package com.eye.app;

import com.eye.core.DataPaths;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.jar.JarFile;

/** Non-UI installer operations. Uses the supplied interactive-user home, never scans other profiles. */
public final class InstallerSupport {
    public static void main(String[] args) {
        try {
            switch(args[0]) {
                case "refresh-guard" -> {
                    com.eye.core.Config config=new com.eye.core.Config();config.loadIfExists();
                    if(config.usageGuardEnabled)com.eye.win.UsageGuard.refreshInstalled(Path.of(args[1]),config.usageGuardAccountSid);
                }
                case "remove-guard" -> {
                    com.eye.core.Config config=new com.eye.core.Config();config.loadIfExists();
                    System.out.println(com.eye.win.UsageGuard.removeInstalled(Path.of(args[1]),config.usageGuardAccountSid));
                }
                case "compare-version" -> {
                    int compare=AppVersion.compare(args[2],args[1]);
                    Files.writeString(Path.of(args[3]),"\ufeff[Version]\r\nCompare="+compare+"\r\n",StandardCharsets.UTF_16LE);
                }
                case "resolve" -> {
                    Path root=DataPaths.resolvePointerWithoutInitialization();
                    Files.writeString(Path.of(args[1]),"\ufeff[Data]\r\nPath="+root+"\r\n",StandardCharsets.UTF_16LE);
                }
                case "migrate" -> {
                    Path old=DataPaths.resolvePointerWithoutInitialization(),target=Path.of(args[1]).toAbsolutePath().normalize();
                    DataPaths.requestMigrationFromInstaller(target);
                    if(!DataPaths.initialize().equals(target))throw new Exception(DataPaths.migrationNotice());
                    if(!old.equals(target)&&Files.isDirectory(old))try(var paths=Files.walk(old)) {
                        if(paths.filter(Files::isRegularFile).anyMatch(p -> !DataPaths.isBootstrapFile(p)))
                            throw new Exception("新目录已生效，但旧目录仍有未能删除的文件，请检查权限："+old);
                    }
                }
                case "close", "force-close" -> {
                    Path bootstrap=DataPaths.bootstrapRoot();
                    boolean force=args[0].equals("force-close");
                    boolean registered=args.length>1&&!args[1].isBlank();
                    if(!registered){
                        if(!instanceLocked(bootstrap.resolve("instance.lock")))return;
                        if(force)throw new IOException("无法确认占用进程所属的安装目录，请从托盘退出 EyeRelax。");
                    }
                    String user=ProcessHandle.current().info().user().orElseGet(()->{
                        String domain=System.getenv("USERDOMAIN");return domain==null||domain.isBlank()?System.getProperty("user.name"):domain+"\\"+System.getProperty("user.name");
                    });
                    List<ProcessHandle> processes=ProcessHandle.allProcesses().filter(p->p.pid()!=ProcessHandle.current().pid()).toList();
                    Path install=registered?Path.of(args[1]).toAbsolutePath().normalize():discoverInstallation(processes,user);
                    List<ManagedProcess> targets=processes.stream()
                            .filter(p->p.info().command().map(c->matchesInstallation(Path.of(c),p.info().user().orElse(""),install,user)).orElse(false))
                            .map(p->(ManagedProcess)new ManagedProcess(){
                                public long pid(){return p.pid();}
                                public boolean isAlive(){return p.isAlive();}
                                public boolean forceClose(){return p.destroyForcibly();}
                            }).toList();
                    closeProcesses(install,bootstrap.resolve("installer-close.request"),targets,force,System::nanoTime,Thread::sleep);
                }
                default -> throw new IllegalArgumentException("Unknown installer operation");
            }
        }catch(Exception ex){System.err.println(ex.getMessage());System.exit(2);}
    }
    interface ManagedProcess { long pid(); boolean isAlive(); boolean forceClose(); }
    interface Sleeper { void sleep(long milliseconds)throws Exception; }
    private static Path discoverInstallation(List<ProcessHandle> processes,String user)throws IOException {
        Set<Path> roots=new HashSet<>();
        for(ProcessHandle process:processes){
            ProcessHandle.Info info=process.info();
            if(!user.isBlank()&&info.user().filter(user::equalsIgnoreCase).isPresent())
                info.command().map(Path::of).map(InstallerSupport::verifiedRoot).ifPresent(roots::add);
        }
        if(roots.size()!=1)throw new IOException("无法确认正在运行的 EyeRelax 安装目录，请从托盘退出后重试。");
        return roots.iterator().next();
    }
    static Path verifiedRoot(Path command) {
        Path exe=command.toAbsolutePath().normalize(),root=exe.getParent();
        if(exe.endsWith(Path.of("jre","bin","javaw.exe")))root=root.getParent().getParent();
        else if(!exe.endsWith("EyeRelax.exe"))return null;
        if(!Files.isRegularFile(root.resolve("install.properties"))||!Files.isRegularFile(root.resolve("EyeRelax.exe")))return null;
        try(JarFile jar=new JarFile(root.resolve("app/rest-1.0.0.jar").toFile())){
            return jar.getEntry("com/eye/app/Main.class")!=null?root:null;
        }catch(IOException unavailable){return null;}
    }
    static boolean matchesInstallation(Path command,String processUser,Path install,String currentUser){
        Path root=install.toAbsolutePath().normalize(),executable=command.toAbsolutePath().normalize();
        return !currentUser.isBlank()&&processUser.equalsIgnoreCase(currentUser)
                &&(executable.equals(root.resolve("EyeRelax.exe"))||executable.equals(root.resolve("jre/bin/javaw.exe")));
    }
    /** Explicitly separate cooperative shutdown from the installer's second, destructive choice. */
    static void closeProcesses(Path install,Path request,List<ManagedProcess> targets,boolean force,LongSupplier now,Sleeper sleeper)throws Exception {
        Path instanceLock=request.resolveSibling("instance.lock");
        // Some Windows tokens may read the user's bootstrap directory but not the
        // installed process's executable path. The app still accepts its normal
        // path-scoped request in this case, including already-installed versions.
        if(targets.isEmpty()){
            if(!instanceLocked(instanceLock))return;
            if(force)throw new IOException("无法确认占用进程所属的安装目录，请从托盘退出 EyeRelax。");
        }
        String owner=install.toAbsolutePath().normalize().toString();
        if(force){
            for(ManagedProcess process:targets)if(process.isAlive()&&!process.forceClose()&&process.isAlive())
                throw new Exception("无法强制关闭 EyeRelax 进程 "+process.pid()+"，请检查账户权限后重试。");
        }else{
            Files.createDirectories(request.getParent());Files.writeString(request,owner,StandardCharsets.UTF_8);
        }
        try{
            long until=now.getAsLong()+(force?3_000_000_000L:4_000_000_000L);
            while(targets.stream().anyMatch(ManagedProcess::isAlive)||instanceLocked(instanceLock)){
                if(now.getAsLong()>=until)throw new IOException(force?
                        "EyeRelax 进程尚未退出，请关闭占用后重试。":"EyeRelax 尚未正常退出。请先保存编辑内容并从托盘退出，或明确选择强制关闭后继续。");
                sleeper.sleep(100);
            }
        }finally{
            if(!force)try{
                if(Files.readString(request,StandardCharsets.UTF_8).equals(owner))Files.deleteIfExists(request);
            }catch(NoSuchFileException ignored){ /* The running application consumed the request. */ }
        }
    }
    private static boolean instanceLocked(Path path)throws IOException {
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE);
                FileLock lock=channel.tryLock(0,1,false)){
            return lock==null;
        }catch(NoSuchFileException absent){return false;}
        catch(OverlappingFileLockException heldInThisJvm){return true;}
    }
}
