package com.eye.core;

import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Optional local-machine rest handoff, enabled explicitly by the all-users installer.
 * Contains only remaining rest time/mode, never task names or personal plan data.
 * Cooperative coordination, NOT a security boundary or a tamper-resistant service.
 */
public final class SharedRestStore {
    public record Rest(String id, long remainingMs, Config.ForceMode mode) {}
    private final Path directory;
    public SharedRestStore() {
        String path=System.getProperty("eyerelax.sharedRestDir", "");
        directory=path.isBlank()?null:Paths.get(path);
    }
    public SharedRestStore(Path directory) { this.directory=directory; }
    public boolean isEnabled() { return directory!=null; }
    public Rest poll() { return access(0,null); }
    public Rest begin(long remaining, Config.ForceMode mode) { return access(remaining,mode); }
    private synchronized Rest access(long requested, Config.ForceMode mode) {
        if (directory==null) return null;
        try {
            Files.createDirectories(directory);
            try (FileChannel c=FileChannel.open(directory.resolve("rest.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                 FileLock lock=c.lock()) {
                Path file=directory.resolve("rest.properties"); Properties p=AtomicProperties.read(file);
                long now=System.currentTimeMillis();
                long remaining=Long.parseLong(p.getProperty("remainingMs","0"));
                long updated=Long.parseLong(p.getProperty("updatedAt",""+now));
                long gap=now-updated;
                // A stale heartbeat means all clients stopped (shutdown/reboot), not verified rest.
                if(gap>=0 && gap<=5000) remaining=Math.max(0,remaining-gap);
                Config.ForceMode outMode=Config.ForceMode.valueOf(p.getProperty("mode","B_STRONG"));
                String id=p.getProperty("id", "");
                if(requested>0 && mode!=null && mode!=Config.ForceMode.A_SOFT) {
                    if(remaining<=0) { id=UUID.randomUUID().toString(); outMode=mode; }
                    else if(mode==Config.ForceMode.C_ULTRA) outMode=mode;
                    remaining=Math.max(remaining,requested);
                }
                if(!id.isBlank() && (requested > 0 || Long.parseLong(p.getProperty("remainingMs","0")) > 0)) {
                    p.setProperty("id",id);p.setProperty("remainingMs",""+remaining);
                    p.setProperty("updatedAt",""+now);p.setProperty("mode",outMode.name());
                    AtomicProperties.write(file,p);
                }
                return id.isBlank()?null:new Rest(id,remaining,outMode);
            }
        } catch(Exception ex) { System.err.println("Shared rest handoff unavailable: "+ex.getMessage());return null; }
    }
}
