package com.eye.win;

import com.eye.core.DataPaths;
import com.sun.jna.platform.win32.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Visible administrator-managed Task Scheduler watchdog; never claims protected-process immunity. */
public final class UsageGuard {
    private static String encode(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    static Path powershellExecutable(Path windowsDirectory){
        if(!windowsDirectory.isAbsolute())throw new IllegalStateException("Windows 系统目录无效，无法启动运行保护维护。请检查系统环境后重试。");
        // Sysnative is visible only to a 32-bit process on 64-bit Windows. Keep
        // that alias intact; canonicalizing it would reintroduce WOW64 redirection.
        for(String system:new String[]{"Sysnative","System32"}){
            Path host=windowsDirectory.resolve(system).resolve("WindowsPowerShell/v1.0/powershell.exe").normalize();
            if(Files.isRegularFile(host))return host;
        }
        throw new IllegalStateException("找不到 Windows PowerShell 系统组件，无法维护运行保护。请检查 Windows 系统组件后重试。");
    }
    static ProcessBuilder powershell(String script){
        String systemRoot=System.getenv("SystemRoot");
        if(systemRoot==null||systemRoot.isBlank())systemRoot=System.getenv("WINDIR");
        if(systemRoot==null||systemRoot.isBlank())throw new IllegalStateException("无法读取 Windows 系统目录，不能启动运行保护维护。请检查 SystemRoot 或 WINDIR 环境配置。");
        String textOutput="$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false);$OutputEncoding=[Console]::OutputEncoding;";
        String encoded=Base64.getEncoder().encodeToString((textOutput+script).getBytes(StandardCharsets.UTF_16LE));
        // Windows PowerShell serializes redirected errors for -EncodedCommand even
        // with -OutputFormat Text. Decode our base64 data inside a fixed command
        // instead, so standard output and errors both remain readable UTF-8 text.
        String command="& ([ScriptBlock]::Create([Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('"+encoded+"'))))";
        return new ProcessBuilder(powershellExecutable(Path.of(systemRoot)).toString(),"-NoProfile","-NonInteractive","-WindowStyle","Hidden","-InputFormat","Text","-OutputFormat","Text","-Command",command);
    }
    public static String currentSid(){
        WinNT.HANDLEByReference token=new WinNT.HANDLEByReference();
        if(!Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(),8,token))throw new IllegalStateException("无法读取当前账户标识。");
        try{return Advapi32Util.getTokenAccount(token.getValue()).sidString;}finally{Kernel32.INSTANCE.CloseHandle(token.getValue());}
    }
    public static String configurationScript(boolean enabled,Path launcher,Path lock,String sid)throws Exception {
        if(!sid.matches("S-1-[0-9-]+"))throw new IllegalArgumentException("Invalid account SID");
        String template,watch;
        try(var in=UsageGuard.class.getResourceAsStream("/guard-configure.ps1")){template=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}
        try(var in=UsageGuard.class.getResourceAsStream("/guard-watch.ps1")){watch=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}
        return template.replace("__SID__",sid).replace("__LAUNCHER__",encode(launcher.toString())).replace("__LOCK__",encode(lock.toString())).replace("__ENABLED__",enabled?"$true":"$false").replace("__WATCH_SCRIPT__",encode(watch));
    }
    public static void apply(boolean enabled){
        Path request=null;
        try {
            String root=System.getProperty("eyerelax.installDir","");
            if(enabled&&(root.isBlank()||!Files.isRegularFile(Path.of(root,"install.properties"))))throw new IllegalStateException("运行保护需要安装版 EyeRelax。");
            String script=configurationScript(enabled,Path.of(root.isBlank()?".":root).resolve("EyeRelax.exe"),DataPaths.instanceLock(),currentSid());
            request=Files.createTempFile("EyeRelax-guard-configure-",".ps1");Files.writeString(request,script,StandardCharsets.UTF_8);
            // Fixed command text; paths/user-supplied strings are only base64 data inside the configurator.
            String wrapper="$file=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('"+encode(request.toString())+"'));try{$p=Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -ArgumentList ('-NoProfile -NonInteractive -WindowStyle Hidden -File \"'+$file+'\"') -Verb RunAs -WindowStyle Hidden -Wait -PassThru;exit $p.ExitCode}catch{exit 2}";
            Process p=powershell(wrapper).start();
            if(!p.waitFor(120,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("守护配置授权超时。");}
            if(p.exitValue()!=0)throw new IllegalStateException("守护配置未完成，可能取消了管理员授权；原设置保持不变。");
        }catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("无法配置 Windows 运行保护："+ex.getMessage(),ex);}
        finally {if(request!=null)try{Files.deleteIfExists(request);}catch(Exception ignored){}}
    }
    public static void refreshInstalled(Path root,String sid)throws Exception {
        String script=configurationScript(true,root.resolve("EyeRelax.exe"),DataPaths.instanceLock(),sid);
        Process p=powershell(script).start();
        if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("更新守护配置超时。");}
        if(p.exitValue()!=0)throw new IllegalStateException("更新守护配置失败，请在新安装的设置中重新应用运行保护。");
    }
    public static String removalScript(Path root,String sid)throws Exception {
        String account=sid==null?"":sid.trim();
        if(!account.isEmpty()&&!account.matches("S-1-[0-9-]+"))throw new IllegalArgumentException("运行保护账户标识无效，请在设置中检查运行保护后重试。");
        String template;
        try(var in=UsageGuard.class.getResourceAsStream("/guard-uninstall.ps1")){template=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}
        return template.replace("__SID__",account).replace("__LAUNCHER__",encode(root.toAbsolutePath().normalize().resolve("EyeRelax.exe").toString()));
    }
    /** Called before deleting this installation. The script verifies both task actions before changing either. */
    public static String removeInstalled(Path root,String sid)throws Exception {
        String script=removalScript(root,sid);
        Process p=powershell(script).redirectErrorStream(true).start();
        if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("移除运行保护超时，请稍后重新运行卸载。");}
        String result=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
        if(p.exitValue()!=0)throw new IllegalStateException(result.isBlank()?"运行保护尚未移除，请以管理员身份重新运行卸载。":result);
        return result;
    }
}
