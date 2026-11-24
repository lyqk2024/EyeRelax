package com.eye.win;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import com.sun.jna.platform.win32.WinReg;

import java.nio.file.*;
import java.util.*;

/**
 * Current-user login startup using the Windows Startup known folder.
 *
 * The installer creates a normal .lnk template next to the application.  Toggling
 * startup later only copies/removes that shortcut from the current user's Startup
 * folder.  This avoids Java writing a Run persistence registry value, which is a
 * common antivirus heuristic trigger.  Old EyeRelax Run values are removed only
 * when they exactly match this installation.
 */
public final class StartupRegistration {
    private static final String LEGACY_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String LEGACY_VALUE = "EyeRelax";
    private static final String TEMPLATE_RELATIVE = "support/EyeRelax-Startup.lnk";
    private static final String STARTUP_LINK_NAME = "EyeRelax.lnk";

    private StartupRegistration() {}

    private static Path installRoot() {
        try {
            String home = System.getProperty("eyerelax.installDir", "").trim();
            if (home.isBlank()) {
                Path jar = Paths.get(StartupRegistration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                Path dir = jar.getParent();
                if (dir != null && "app".equalsIgnoreCase(dir.getFileName().toString())) {
                    Path root = dir.getParent();
                    if (root != null) home = root.toString();
                }
            }
            if (home.isBlank()) throw new IllegalStateException("便携版请先安装到固定目录，再开启登录自启动。");
            Path root = Paths.get(home).toAbsolutePath().normalize();
            if (!Files.isRegularFile(root.resolve("install.properties"))) {
                throw new IllegalStateException("便携版不能登记登录启动，请使用安装版。");
            }
            return root;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("无法确定安装路径", ex);
        }
    }

    public static Path startupFolder() {
        try {
            String folder = Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Startup);
            if (folder == null || folder.isBlank()) throw new IllegalStateException("Windows 未返回当前账户的启动文件夹。");
            return Paths.get(folder).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("无法读取当前账户的 Windows 启动文件夹。", ex);
        }
    }

    private static Path startupLink() {
        return startupFolder().resolve(STARTUP_LINK_NAME);
    }

    /** Kept only to recognize and remove the old Run-key form during migration. */
    static String legacyCommand() {
        Path root = installRoot();
        Path java = root.resolve("jre/bin/javaw.exe");
        Path jar = root.resolve("app/rest-1.0.0.jar");
        return "\"" + java + "\" -jar \"" + jar + "\" --startup";
    }

    public static boolean isEnabled() {
        if (!Platform.isWindows()) return false;
        try {
            return !matchingLinks(startupFolder(), installRoot()).isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    /** Read shortcut targets, including manually named links, without rewriting them. */
    static List<Path> matchingLinks(Path folder, Path root) throws Exception {
        List<Path> links=new ArrayList<>();
        if(!Files.isDirectory(folder))return links;
        Path expected=root.resolve("EyeRelax.exe").toAbsolutePath().normalize();
        try(var files=Files.list(folder)){
            for(Path file:files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lnk")).toList()){
                WindowsShell.Shortcut shortcut;
                try{shortcut=WindowsShell.readShortcut(file);}catch(RuntimeException broken){continue;}
                String arguments=shortcut.arguments().trim();
                if(shortcut.target()!=null&&shortcut.target().toAbsolutePath().normalize().toString().equalsIgnoreCase(expected.toString())
                        &&(arguments.isEmpty()||arguments.equals("--startup")))links.add(file);
            }
        }
        return links;
    }

    public static void apply(boolean enabled) {
        if (!Platform.isWindows()) throw new IllegalStateException("登录自启动只支持 Windows 安装版。");
        Path root = installRoot();
        applyAtFolder(root, startupFolder(), enabled);
    }

    static void applyAtFolder(Path root, Path folder, boolean enabled) {
        Path template = root.resolve(TEMPLATE_RELATIVE);
        Path link = folder.resolve(STARTUP_LINK_NAME);
        try {
            List<Path> existing = matchingLinks(link.getParent(), root);
            if (enabled && !existing.isEmpty()) return; // Preserve the user's trusted shortcut.
            if (enabled) {
                if (Files.exists(link)) throw new IllegalStateException("EyeRelax.lnk 已存在，但目标不属于当前安装。请打开启动文件夹检查，程序不会覆盖它。");
                if (!Files.isRegularFile(template) || Files.size(template) == 0) {
                    throw new IllegalStateException("安装目录缺少登录启动快捷方式模板，请重新运行最新版安装程序修复安装。");
                }
                Files.createDirectories(link.getParent());
                Files.copy(template, link, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                if (!Files.isRegularFile(link) || Files.size(link) == 0) {
                    throw new IllegalStateException("Windows 启动文件夹没有生成有效的 EyeRelax 快捷方式。");
                }
            } else {
                for (Path owned : existing) Files.deleteIfExists(owned);
            }
            removeLegacyRunBestEffort();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (AccessDeniedException ex) {
            throw new IllegalStateException("无法修改 Windows 启动文件夹。可手动放入当前 EyeRelax.exe 的快捷方式，添加信任后点击“检查启动入口”。", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("登录自启动设置失败：" + ex.getMessage(), ex);
        }
    }

    private static void removeLegacyRunBestEffort() {
        try {
            String expected = legacyCommand();
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, LEGACY_KEY, LEGACY_VALUE)) {
                String current = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, LEGACY_KEY, LEGACY_VALUE);
                if (expected.equals(current)) {
                    Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, LEGACY_KEY, LEGACY_VALUE);
                }
            }
        } catch (RuntimeException ignored) {
            // Registry cleanup is migration hygiene only.  Never fail the new Startup-folder path.
        }
    }
}
