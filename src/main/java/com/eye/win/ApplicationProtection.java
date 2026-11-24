package com.eye.win;

import com.eye.core.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import java.time.*;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;
import com.eye.ui.ApplicationLimitOverlay;
import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Foreground restrictions show a window-sized explanation; only an explicit return action closes processes. */
public final class ApplicationProtection implements AutoCloseable {
    private final ApplicationLimits limits;
    private final BooleanSupplier suspended;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"EyeRelax-app-limits");t.setDaemon(true);return t;});
    private volatile boolean closed;
    private record Target(WinDef.HWND window,int pid,String path) {}
    private record Display(Target target,ApplicationLimits.Status status,Rectangle bounds) {}
    private interface WindowState extends StdCallLibrary { WindowState INSTANCE=Native.load("user32",WindowState.class);boolean IsIconic(WinDef.HWND window);boolean ShowWindowAsync(WinDef.HWND window,int command); }
    private final ApplicationLimitOverlay overlay=new ApplicationLimitOverlay();
    private final java.util.concurrent.atomic.AtomicBoolean uiQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile Display display;
    private Target target;
    private final char[] pathBuffer=new char[32768];
    public ApplicationProtection(Config config,ApplicationLimits limits,BooleanSupplier suspended){this.limits=limits;this.suspended=suspended;}
    public ApplicationLimits limits(){return limits;}
    public void start(){worker.scheduleWithFixedDelay(this::tick,0,250,TimeUnit.MILLISECONDS);}
    public static boolean eligible(String exe) {
        String key=ApplicationLimitRule.key(exe);String name=key.substring(key.lastIndexOf('\\')+1);
        if(!key.endsWith(".exe")||Set.of("explorer.exe","taskmgr.exe","winlogon.exe","logonui.exe","lockapp.exe","systemsettings.exe","shellexperiencehost.exe","startmenuexperiencehost.exe").contains(name))return false;
        String self=ProcessHandle.current().info().command().orElse("");
        return !key.equals(ApplicationLimitRule.key(self))&&!name.equals("eyerelax.exe");
    }
    private String image(WinNT.HANDLE process){
        IntByReference length=new IntByReference(pathBuffer.length);
        return Kernel32.INSTANCE.QueryFullProcessImageName(process,0,pathBuffer,length)?new String(pathBuffer,0,length.getValue()):"";
    }
    private void tick() {
        if(closed)return;
        try {
            boolean available=limits.enabled()&&!suspended.getAsBoolean()&&DesktopAvailability.isAvailable();
            WinDef.HWND foreground=available?User32.INSTANCE.GetForegroundWindow():null;
            boolean covered=overlay.owns(foreground);
            Target current=covered?target:identify(foreground);
            if(current!=null&&(!User32.INSTANCE.IsWindowVisible(current.window())||WindowState.INSTANCE.IsIconic(current.window())))current=null;
            var status=limits.sample(Instant.now(),System.nanoTime(),current==null?"":current.path(),available,covered);
            WinDef.RECT rect=new WinDef.RECT();
            if(available&&current!=null&&status.blocked()&&User32.INSTANCE.GetWindowRect(current.window(),rect)&&rect.right>rect.left&&rect.bottom>rect.top){
                target=current;display=new Display(current,status,new Rectangle(rect.left,rect.top,rect.right-rect.left,rect.bottom-rect.top));
            }else{target=null;display=null;}
            updateUi();
        }catch(RuntimeException ex){target=null;display=null;updateUi();System.err.println("Application protection: "+ex.getMessage());}
    }
    private Target identify(WinDef.HWND window){
        if(window==null)return null;IntByReference id=new IntByReference();User32.INSTANCE.GetWindowThreadProcessId(window,id);int pid=id.getValue();
        if(pid==(int)ProcessHandle.current().pid())return null;
        WinNT.HANDLE process=Kernel32.INSTANCE.OpenProcess(0x1000,false,pid);if(process==null)return null;
        try{String app=ApplicationLimitRule.key(image(process));return eligible(app)?new Target(window,pid,app):null;}finally{Kernel32.INSTANCE.CloseHandle(process);}
    }
    private void updateUi(){
        if(!uiQueued.compareAndSet(false,true))return;
        SwingUtilities.invokeLater(()->{
            uiQueued.set(false);Display view=display;
            if(closed||view==null){overlay.hide();return;}
            WinDef.HWND foreground=User32.INSTANCE.GetForegroundWindow();
            if((!view.target().window().equals(foreground)&&!overlay.owns(foreground))||!limits.status(view.target().path(),Instant.now()).blocked()){overlay.hide();return;}
            overlay.show(view.status(),view.bounds(),()->returnDesktop(view.target()));
        });
    }
    /** Invoked only by the explanation's Return to desktop button, on the UI thread. */
    private void returnDesktop(Target expected){
        Display current=display;
        if(closed||current==null||!current.target().equals(expected)||!limits.status(expected.path(),Instant.now()).blocked())return;
        // Remove the restricted window from the desktop before ending its processes.
        WindowState.INSTANCE.ShowWindowAsync(expected.window(),WinUser.SW_MINIMIZE);display=null;overlay.hide();
        worker.execute(()->{
            target=null;Map<String,String> failures=new HashMap<>();
            try(var processes=ProcessHandle.allProcesses()){
                var matches=processes.filter(p->ApplicationLimitRule.key(p.info().command().orElse("")).equals(expected.path())).toList();
                Set<Long> ids=new HashSet<>();for(var process:matches)ids.add(process.pid());
                User32.INSTANCE.EnumWindows((window,data)->{IntByReference id=new IntByReference();User32.INSTANCE.GetWindowThreadProcessId(window,id);if(ids.contains(Integer.toUnsignedLong(id.getValue())))WindowState.INSTANCE.ShowWindowAsync(window,WinUser.SW_MINIMIZE);return true;},null);
                for(var process:matches){int error=terminate(Math.toIntExact(process.pid()),expected.path());if(error!=0)failures.put(expected.path(),"无法结束应用，请检查权限（Windows "+error+"）。");}
                limits.enforcementErrors(failures);
            }
        });
    }
    /** One opened handle is used for both identity verification and termination, avoiding PID reuse. */
    int terminate(int pid,String expected){
        if(pid<=0||pid==(int)ProcessHandle.current().pid()||!eligible(expected)||!limits.status(expected,Instant.now()).blocked())return 0;
        WinNT.HANDLE process=Kernel32.INSTANCE.OpenProcess(0x1000|0x0001,false,pid);
        if(process==null){int error=Kernel32.INSTANCE.GetLastError();return error==87?0:error;}
        try {
            String actual=image(process);
            if(actual.isEmpty())return Kernel32.INSTANCE.GetLastError();
            if(!ApplicationLimitRule.key(actual).equals(ApplicationLimitRule.key(expected)))return 0;
            if(Kernel32.INSTANCE.TerminateProcess(process,0))return 0;
            return Kernel32.INSTANCE.GetLastError();
        }finally{Kernel32.INSTANCE.CloseHandle(process);}
    }
    @Override public void close(){closed=true;worker.shutdownNow();try{worker.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}limits.close();SwingUtilities.invokeLater(overlay::dispose);}
}
