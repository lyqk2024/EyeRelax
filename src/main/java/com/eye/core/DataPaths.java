package com.eye.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Resolves and safely migrates the per-user EyeRelax data directory.
 *
 * The tiny bootstrap file stays under ~/.eye-relax so the application can always
 * find the active directory after the directory containing config.properties is moved.
 * All actual user data (config, plans, history, activity, session state) lives in root().
 */
public final class DataPaths {
    private static final String BOOTSTRAP_NAME = "data-location.properties";
    private static final String POINTER_KEY = "path";
    private static final String PENDING_KEY = "pendingPath";
    private static final String INSTALLER_REQUEST_NAME = "data-location.request";
    private static final Set<String> BOOTSTRAP_ONLY = Set.of(BOOTSTRAP_NAME, INSTALLER_REQUEST_NAME, "instance.lock", "installer-close.request", "strict-local.properties", "strict-local.properties.bak");
    private static volatile Path activeRoot;
    private static volatile String migrationNotice = "";

    private DataPaths() {}

    /** Stable bootstrap location, intentionally not migrated. */
    public static Path bootstrapRoot() {
        return Paths.get(System.getProperty("user.home"), ".eye-relax").toAbsolutePath().normalize();
    }

    public static Path bootstrapFile() { return bootstrapRoot().resolve(BOOTSTRAP_NAME); }
    public static Path instanceLock() { return bootstrapRoot().resolve("instance.lock"); }

    /** These files identify this account or coordinate its running instance across data moves. */
    public static boolean isBootstrapFile(Path file) {
        return bootstrapRoot().equals(file.getParent()) && BOOTSTRAP_ONLY.contains(file.getFileName().toString());
    }

    /** Call before Config/PlanManager/CycleEngine are constructed. Safe to call repeatedly. */
    public static synchronized Path initialize() {
        if (activeRoot != null) return activeRoot;
        Path fallback = bootstrapRoot();
        Properties pointer = readBootstrap();
        Path source = normalizeStored(pointer.getProperty(POINTER_KEY), fallback);
        Path pending = normalizeStored(pointer.getProperty(PENDING_KEY), null);
        Path installerRequest = bootstrapRoot().resolve(INSTALLER_REQUEST_NAME);
        if (pending == null && Files.isRegularFile(installerRequest)) {
            try {
                String requested = Files.readString(installerRequest, StandardCharsets.UTF_8).trim();
                if (!requested.isBlank()) pending = normalizeStored(requested, null);
            } catch (IOException ignored) {}
        }

        // Installed metadata may provide a first-run default without touching another account's files.
        if (pending == null) {
            String installedDefault = System.getProperty("eyerelax.defaultDataDir", "").trim();
            if (!installedDefault.isBlank() && pointer.getProperty(POINTER_KEY) == null) {
                Path candidate = normalizeStored(expandEnvironment(installedDefault), fallback);
                if (!candidate.equals(fallback)) pending = candidate;
            }
        }

        if (pending != null && !pending.equals(source)) {
            try {
                migrate(source, pending);
                writePointer(pending, null);
                cleanupSourceAfterMigration(source, pending);
                source = pending;
                migrationNotice = "数据已迁移到 " + pending;
                try { Files.deleteIfExists(installerRequest); } catch (IOException ignored) {}
            } catch (Exception ex) {
                // Keep the previous active path authoritative. A failed migration must never strand data.
                migrationNotice = "数据迁移未完成，仍使用原目录：" + ex.getMessage();
                try { writePointer(source, null); } catch (Exception ignored) {}
                try { Files.deleteIfExists(installerRequest); } catch (IOException ignored) {}
            }
        }
        activeRoot = source;
        try { Files.createDirectories(activeRoot); } catch (IOException ignored) {}
        return activeRoot;
    }

    public static Path root() { return initialize(); }
    public static Path file(String name) { return root().resolve(name); }
    public static String migrationNotice() { initialize(); return migrationNotice; }

    /**
     * Schedules a migration for the next launch. This is deliberately two-phase:
     * the running process continues using its current path until it exits, and the
     * next process performs/validates the copy before switching the pointer.
     */
    public static synchronized void requestMigration(Path target) throws IOException {
        Path current = root();
        Path normalized = validateTarget(target, current);
        if (normalized.equals(current)) return;
        Properties pointer = readBootstrap();
        pointer.setProperty(POINTER_KEY, current.toString());
        pointer.setProperty(PENDING_KEY, normalized.toString());
        saveBootstrap(pointer);
    }

    public static synchronized boolean hasPendingMigration() {
        return readBootstrap().getProperty(PENDING_KEY) != null;
    }

    /** For installer/bootstrap code that wants first launch to migrate existing default data safely. */
    public static synchronized void requestMigrationFromInstaller(Path target) throws IOException {
        Path source = resolvePointerWithoutInitialization();
        Path normalized = validateTarget(target, source);
        if (normalized.equals(source)) { writePointer(source, null); Files.deleteIfExists(bootstrapRoot().resolve(INSTALLER_REQUEST_NAME)); return; }
        Properties pointer = readBootstrap();
        pointer.setProperty(POINTER_KEY, source.toString());
        pointer.setProperty(PENDING_KEY, normalized.toString());
        saveBootstrap(pointer);
    }

    public static Path resolvePointerWithoutInitialization() {
        Path fallback = bootstrapRoot();
        Properties pointer = readBootstrap();
        return normalizeStored(pointer.getProperty(POINTER_KEY), fallback);
    }

    private static Path validateTarget(Path target, Path current) throws IOException {
        if (target == null) throw new IOException("未选择数据目录");
        Path normalized = target.toAbsolutePath().normalize();
        if (normalized.equals(bootstrapFile()) || normalized.equals(instanceLock())) throw new IOException("请选择文件夹，而不是文件");
        if (normalized.equals(current)) return normalized;
        if (normalized.startsWith(current) || current.startsWith(normalized)) {
            throw new IOException("新旧数据目录不能互相包含，请选择独立文件夹");
        }
        Path parent = normalized.getParent();
        if (parent == null) throw new IOException("不能直接使用磁盘根目录");
        Files.createDirectories(parent);
        if (Files.exists(normalized)) {
            if (!Files.isDirectory(normalized)) throw new IOException("目标不是文件夹");
            // The stable ~/.eye-relax bootstrap can contain only its pointer/request/lock files.
            // Those files are not user data and must remain there even when the actual data root moves away.
            boolean bootstrapTarget = normalized.equals(bootstrapRoot());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(normalized)) {
                for (Path child : stream) {
                    if (bootstrapTarget && BOOTSTRAP_ONLY.contains(child.getFileName().toString())) continue;
                    throw new IOException("目标文件夹必须为空，避免覆盖已有文件");
                }
            }
        }
        return normalized;
    }

    private static void migrate(Path source, Path target) throws Exception {
        target = validateTarget(target, source);
        Path parent = target.getParent();
        Path stage = parent.resolve(".EyeRelax-migrate-" + UUID.randomUUID());
        Files.createDirectories(stage);
        boolean success = false;
        boolean bootstrapTarget = target.equals(bootstrapRoot()) && !source.equals(target);
        try {
            if (Files.isDirectory(source)) copyTree(source, stage);
            verifyTree(source, stage);
            if (bootstrapTarget) {
                // ~/.eye-relax must keep the stable pointer/request files, so it cannot be replaced as one
                // directory. Clear only non-bootstrap leftovers, copy the verified stage, then verify again.
                // The old source remains authoritative until the caller switches the pointer.
                clearNonBootstrap(target);
                copyTree(stage, target);
                verifyTree(source, target);
                deleteTreeQuietly(stage);
            } else {
                if (Files.exists(target)) Files.delete(target); // validated empty above
                try { Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(stage, target); }
            }
            success = true;
        } finally {
            if (!success) {
                deleteTreeQuietly(stage);
                if (bootstrapTarget) clearNonBootstrap(target);
            }
        }
    }

    private static void clearNonBootstrap(Path root) {
        if (!Files.isDirectory(root)) return;
        try (var walk = Files.walk(root)) {
            List<Path> paths = new ArrayList<>(walk.toList()); Collections.reverse(paths);
            for (Path path : paths) {
                if (path.equals(root)) continue;
                Path rel = root.relativize(path);
                if (rel.getNameCount() == 1 && BOOTSTRAP_ONLY.contains(rel.toString())) continue;
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static void copyTree(Path source, Path stage) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path rel = source.relativize(path);
                if (rel.getNameCount() == 1 && BOOTSTRAP_ONLY.contains(rel.toString())) continue;
                if (rel.toString().isEmpty()) continue;
                Path out = stage.resolve(rel.toString());
                if (Files.isDirectory(path)) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(path, out, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void verifyTree(Path source, Path stage) throws Exception {
        if (!Files.isDirectory(source)) return;
        try (var walk = Files.walk(source)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                Path rel = source.relativize(path);
                if (rel.getNameCount() == 1 && BOOTSTRAP_ONLY.contains(rel.toString())) continue;
                Path copy = stage.resolve(rel.toString());
                if (!Files.isRegularFile(copy)) throw new IOException("迁移校验缺少文件：" + rel);
                if (Files.size(path) != Files.size(copy)) throw new IOException("迁移校验大小不一致：" + rel);
                if (!Arrays.equals(sha256(path), sha256(copy))) throw new IOException("迁移校验失败：" + rel);
            }
        }
    }

    private static byte[] sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) >= 0;) if (n > 0) digest.update(buf, 0, n);
        }
        return digest.digest();
    }

    private static void cleanupSourceAfterMigration(Path source, Path target) {
        if (source.equals(target) || !Files.isDirectory(source)) return;
        try (var walk = Files.walk(source)) {
            List<Path> paths = new ArrayList<>(walk.toList());
            Collections.reverse(paths);
            for (Path path : paths) {
                if (path.equals(source)) continue;
                Path rel = source.relativize(path);
                if (rel.getNameCount() == 1 && BOOTSTRAP_ONLY.contains(rel.toString())) continue;
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            }
            if (!source.equals(bootstrapRoot())) {
                try { Files.deleteIfExists(source); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            List<Path> paths = new ArrayList<>(walk.toList()); Collections.reverse(paths);
            for (Path p : paths) try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        } catch (IOException ignored) {}
    }

    private static Properties readBootstrap() {
        Properties p = new Properties(); Path file = bootstrapFile();
        if (!Files.isRegularFile(file)) return p;
        try (var in = Files.newInputStream(file); var r = new InputStreamReader(in, StandardCharsets.UTF_8)) { p.load(r); }
        catch (IOException ignored) {}
        return p;
    }

    private static void writePointer(Path path, Path pending) throws IOException {
        Properties p = readBootstrap(); p.setProperty(POINTER_KEY, path.toString());
        if (pending == null) p.remove(PENDING_KEY); else p.setProperty(PENDING_KEY, pending.toString());
        saveBootstrap(p);
    }

    private static void saveBootstrap(Properties p) throws IOException {
        Files.createDirectories(bootstrapRoot());
        Path file = bootstrapFile(), tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var out = Files.newOutputStream(tmp); var w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            p.store(w, "EyeRelax data location bootstrap");
        }
        try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static Path normalizeStored(String raw, Path fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Paths.get(expandEnvironment(raw)).toAbsolutePath().normalize(); }
        catch (Exception ex) { return fallback; }
    }

    private static String expandEnvironment(String value) {
        String out = value;
        for (Map.Entry<String,String> e : System.getenv().entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue());
        }
        return out.replace("${user.home}", System.getProperty("user.home"));
    }

    /** Test hook; production code never needs it. */
    public static synchronized void resetForTests() { activeRoot = null; migrationNotice = ""; }
}
