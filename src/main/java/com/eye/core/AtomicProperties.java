package com.eye.core;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Crash-safe UTF-8 properties, with a last-known-good copy. */
public final class AtomicProperties {
    private AtomicProperties() {}
    public static Properties read(Path path) throws IOException {
        Properties p = new Properties();
        if (!Files.isRegularFile(path)) return p;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(r); }
        return p;
    }
    public static void write(Path path, Properties p) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName()+".", ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { p.store(w, "EyeRelax"); }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            if (Files.isRegularFile(path)) Files.copy(path, path.resolveSibling(path.getFileName()+".bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
