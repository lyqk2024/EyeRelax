package com.eye.app;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Simulated processes and temporary request files only; never enumerates or closes real processes. */
public final class InstallerSupportAcceptance {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static final class FakeProcess implements InstallerSupport.ManagedProcess {
        boolean alive=true,canClose=true;int forceCalls;
        public long pid(){return 123;}
        public boolean isAlive(){return alive;}
        public boolean forceClose(){forceCalls++;if(canClose)alive=false;return canClose;}
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("EyeRelax-installer-helper-qa-");Path install=root.resolve("中文 安装");Path request=root.resolve("profile/installer-close.request");
        check(InstallerSupport.matchesInstallation(install.resolve("EyeRelax.exe"),"DOMAIN\\QA",install,"domain\\qa"),"same installation and same account");
        check(InstallerSupport.matchesInstallation(install.resolve("jre/bin/javaw.exe"),"DOMAIN\\QA",install,"DOMAIN\\QA"),"legacy private runtime is recognized");
        check(!InstallerSupport.matchesInstallation(install.resolve("jre/bin/java.exe"),"DOMAIN\\QA",install,"DOMAIN\\QA"),"helper JVM is never a close target");
        check(!InstallerSupport.matchesInstallation(root.resolve("another/EyeRelax.exe"),"DOMAIN\\QA",install,"DOMAIN\\QA"),"other installation excluded");
        check(!InstallerSupport.matchesInstallation(install.resolve("EyeRelax.exe"),"OTHER\\QA",install,"DOMAIN\\QA"),"same username in another domain excluded");
        Files.createDirectories(install.resolve("app"));
        Path launcher=Files.createFile(install.resolve("EyeRelax.exe"));
        check(InstallerSupport.verifiedRoot(launcher)==null,"executable name alone cannot identify an installation");
        Files.createFile(install.resolve("install.properties"));
        try(var jar=new java.util.jar.JarOutputStream(Files.newOutputStream(install.resolve("app/rest-1.0.0.jar")))){
            jar.putNextEntry(new java.util.jar.JarEntry("com/eye/app/Main.class"));jar.closeEntry();
        }
        check(install.equals(InstallerSupport.verifiedRoot(launcher)),"unregistered native installation can be identified");
        check(install.equals(InstallerSupport.verifiedRoot(install.resolve("jre/bin/javaw.exe"))),"unregistered private javaw can be identified");
        check(InstallerSupport.verifiedRoot(install.resolve("jre/bin/java.exe"))==null,"helper executable cannot identify a target");
        AtomicLong clock=new AtomicLong();FakeProcess normal=new FakeProcess();
        InstallerSupport.closeProcesses(install,request,List.of(normal),false,clock::get,ms->{
            check(Files.isRegularFile(request),"cooperative request exists while waiting");
            check(Files.readString(request).equals(install.toAbsolutePath().normalize().toString()),"request names only selected installation");
            clock.addAndGet(ms*1_000_000L);normal.alive=false;
        });
        check(normal.forceCalls==0&&!Files.exists(request),"normal exit never forces and removes its request");
        FakeProcess timeout=new FakeProcess();clock.set(0);
        try{InstallerSupport.closeProcesses(install,request,List.of(timeout),false,clock::get,ms->clock.addAndGet(ms*1_000_000L));throw new AssertionError("expected timeout");}
        catch(IllegalStateException ex){throw ex;}
        catch(Exception ex){check(ex.getMessage().contains("正常退出"),"timeout gives explicit normal-exit guidance");}
        check(timeout.alive&&timeout.forceCalls==0&&!Files.exists(request),"timeout leaves process alive and no stale request");
        FakeProcess forced=new FakeProcess();clock.set(0);InstallerSupport.closeProcesses(install,request,List.of(forced),true,clock::get,ms->clock.addAndGet(ms*1_000_000L));
        check(forced.forceCalls==1&&!forced.alive&&!Files.exists(request),"force is a separate explicit path");
        FakeProcess denied=new FakeProcess();denied.canClose=false;
        try{InstallerSupport.closeProcesses(install,request,List.of(denied),true,clock::get,ms->{});throw new AssertionError("expected refusal");}
        catch(Exception ex){check(ex.getMessage().contains("权限"),"force failure remains actionable");}
        InstallerSupport.closeProcesses(install,request,List.of(),false,clock::get,ms->{});
        check(!Files.exists(request),"already-exited process is a no-op");
        // An elevated installed process can hold the real lock while the
        // non-elevated helper sees no matching executable path.
        Path instanceLock=request.resolveSibling("instance.lock");
        try(FileChannel channel=FileChannel.open(instanceLock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                FileLock lock=channel.lock()){
            try{InstallerSupport.closeProcesses(install,request,List.of(),true,clock::get,ms->{});throw new AssertionError("unknown force target");}
            catch(java.io.IOException expected){check(lock.isValid()&&!Files.exists(request),"no unverified process is forced");}
            InstallerSupport.closeProcesses(install,request,List.of(),false,clock::get,ms->{
                check(Files.readString(request).equals(install.toAbsolutePath().normalize().toString()),"locked uninspectable process receives the existing close protocol");
                Files.delete(request);
                lock.release();
            });
            check(!lock.isValid()&&!Files.exists(request),"normal close waits for the app to consume the request and release the lock");
        }
        // NSIS's helper classpath contains only its JAR, not JNA. Script generation must work there too.
        var classes = InstallerSupport.class.getProtectionDomain().getCodeSource().getLocation();
        var scriptResource = java.util.Objects.requireNonNull(InstallerSupport.class.getResource("/guard-uninstall.ps1"));
        var resources = new java.net.URL(scriptResource, ".");
        try (var isolated = new java.net.URLClassLoader(new java.net.URL[]{classes, resources}, ClassLoader.getPlatformClassLoader())) {
            var guard=Class.forName("com.eye.win.UsageGuard",true,isolated);
            String script=(String)guard.getMethod("removalScript",Path.class,String.class).invoke(null,install,"");
            check(!script.contains("__SID__")&&!script.contains("__LAUNCHER__"),"removal helper expands all data placeholders without JNA");
            check(script.contains(java.util.Base64.getEncoder().encodeToString(install.resolve("EyeRelax.exe").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))),"launcher path is encoded as data, including spaces and Unicode");
        }
        System.out.println("InstallerSupportAcceptance: "+checks+" checks passed; no real processes touched");
    }
}
