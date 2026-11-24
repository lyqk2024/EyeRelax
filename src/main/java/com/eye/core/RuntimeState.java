package com.eye.core;

import java.nio.file.*;
import java.util.Properties;

/** Only committed phases are persisted; offline time is never consumed as work or rest. */
public final class RuntimeState {
    public enum Phase { IDLE, WORK, RELAX }
    public Phase phase = Phase.IDLE;
    public long remainingMs;
    public int workSeconds, relaxSeconds;
    /** Pinned rest policy survives a change of future defaults and a process restart. */
    public String restMode = "", restKind = "";
    public String planId = "", signature = "", sharedRestId = "", studyDate = "";
    public boolean awaitingUnlockAfterDeadline;
    /** Original time at which a resumable manual/suspend pause began. */
    public long pausedSinceEpochMs;
    public long savedAt;
    public Properties toProperties() {
        Properties p = new Properties(); p.setProperty("version", "3");
        p.setProperty("phase", phase.name()); p.setProperty("remainingMs", ""+remainingMs);
        p.setProperty("workSeconds", ""+workSeconds); p.setProperty("relaxSeconds", ""+relaxSeconds);
        p.setProperty("restMode", restMode); p.setProperty("restKind", restKind);
        p.setProperty("planId", planId == null ? "" : planId); p.setProperty("signature", signature);
        p.setProperty("sharedRestId", sharedRestId == null ? "" : sharedRestId);
        p.setProperty("awaitingUnlockAfterDeadline", "" + awaitingUnlockAfterDeadline);
        p.setProperty("studyDate", studyDate == null ? "" : studyDate);
        p.setProperty("pausedSinceEpochMs", "" + pausedSinceEpochMs);
        p.setProperty("savedAt", ""+savedAt); return p;
    }
    public static RuntimeState from(Properties p) {
        RuntimeState s = new RuntimeState();
        String version = p.getProperty("version", "1");
        if (!"1".equals(version) && !"2".equals(version) && !"3".equals(version)) throw new IllegalArgumentException("unknown state version");
        s.phase = Phase.valueOf(p.getProperty("phase"));
        s.remainingMs = Long.parseLong(p.getProperty("remainingMs"));
        s.workSeconds = Integer.parseInt(p.getProperty("workSeconds"));
        s.relaxSeconds = Integer.parseInt(p.getProperty("relaxSeconds"));
        if (s.remainingMs < 0 || s.workSeconds <= 0 || s.relaxSeconds <= 0) throw new IllegalArgumentException("invalid state");
        s.restMode = p.getProperty("restMode", ""); s.restKind = p.getProperty("restKind", "");
        s.planId = p.getProperty("planId", ""); s.signature = p.getProperty("signature", "");
        s.sharedRestId = p.getProperty("sharedRestId", "");
        s.awaitingUnlockAfterDeadline = Boolean.parseBoolean(p.getProperty("awaitingUnlockAfterDeadline", "false"));
        s.studyDate = p.getProperty("studyDate", "");
        s.pausedSinceEpochMs = Long.parseLong(p.getProperty("pausedSinceEpochMs", "0"));
        s.savedAt = Long.parseLong(p.getProperty("savedAt", "0")); return s;
    }
    public static final class Store {
        private final Path path;
        public Store() { this(DataPaths.file("session-state.properties")); }
        public Store(Path path) { this.path = path; }
        public synchronized RuntimeState load() {
            for (Path candidate : new Path[]{path, path.resolveSibling(path.getFileName()+".bak")}) {
                try { if (Files.isRegularFile(candidate)) return from(AtomicProperties.read(candidate)); }
                catch (Exception ex) { System.err.println("State recovery: " + candidate + ": " + ex.getMessage()); }
            }
            return null;
        }
        public synchronized void save(RuntimeState state) {
            try { AtomicProperties.write(path, state.toProperties()); }
            catch (Exception ex) { System.err.println("Cannot save session state: " + ex.getMessage()); }
        }
    }
}
