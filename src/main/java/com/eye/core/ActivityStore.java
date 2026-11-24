package com.eye.core;

import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Local, actual running-time ledger. No synthetic backfill for unrecorded rest/free focus. */
public final class ActivityStore {
    private final Path path;
    private final Properties values;
    private final ZoneId zone;
    private volatile int dayBoundarySeconds;
    private final SessionStore sessions;
    private boolean legacyInitialized;
    private long lastSave;
    private boolean dirty;
    public ActivityStore() { this(DataPaths.file("activity.properties"), ZoneId.systemDefault(), StudyDay.DEFAULT_BOUNDARY_SECONDS); }
    public ActivityStore(int dayBoundarySeconds) { this(DataPaths.file("activity.properties"), ZoneId.systemDefault(), dayBoundarySeconds); }
    public ActivityStore(Path path, ZoneId zone) { this(path, zone, StudyDay.DEFAULT_BOUNDARY_SECONDS); }
    public ActivityStore(Path path, ZoneId zone, int dayBoundarySeconds) {
        this.path=path; this.zone=zone; this.dayBoundarySeconds=StudyDay.sanitizeBoundarySeconds(dayBoundarySeconds);
        Properties loaded;
        try { loaded=AtomicProperties.read(path); }
        catch (Exception ex) { loaded=new Properties(); System.err.println("Activity ledger: "+ex.getMessage()); }
        values=loaded;
        sessions = new SessionStore(path.toAbsolutePath().getParent().resolve("session-ledger"));
        // Legacy versions only had one trackedSince marker. Treat it as work coverage;
        // rest coverage begins only when a real rest interval has actually been recorded.
        if (!values.containsKey("workTrackedSince") && values.containsKey("trackedSince")) {
            values.setProperty("workTrackedSince", values.getProperty("trackedSince")); dirty=true;
        }
        migrateMetricSinceFromKeys(false);
        migrateMetricSinceFromKeys(true);
    }
    public synchronized void setDayBoundarySeconds(int seconds) {
        int value = StudyDay.sanitizeBoundarySeconds(seconds);
        if (value != this.dayBoundarySeconds) {
            ensureLegacy(); sessions.changeRule(System.currentTimeMillis(), value, zone);
            this.dayBoundarySeconds = value; dirty = true;
        }
    }
    public SessionStore sessions() { return sessions; }
    /** Freeze the migration baseline BEFORE any new plan-progress tick can run. */
    public synchronized void prepareRecording() { ensureLegacy(); }
    private void ensureLegacy() { if (!legacyInitialized) { sessions.initializeLegacy(values); legacyInitialized=true; } }

    public synchronized LocalDate trackedSince() { return trackedSince(false); }
    public synchronized LocalDate trackedSince(boolean rest) {
        String key=rest?"restTrackedSince":"workTrackedSince";
        String raw=values.getProperty(key);
        if(raw==null||raw.isBlank()) return LocalDate.MAX;
        try { return LocalDate.parse(raw); } catch(Exception e) { return LocalDate.MAX; }
    }
    private void migrateMetricSinceFromKeys(boolean rest) {
        String marker=rest?"restTrackedSince":"workTrackedSince";
        if(values.containsKey(marker)) return;
        String prefix=rest?"rest.":"work."; LocalDate first=null;
        for(String key:values.stringPropertyNames()) if(key.startsWith(prefix)) {
            try { LocalDate d=LocalDate.parse(key.substring(prefix.length())); if(first==null||d.isBefore(first))first=d; }
            catch(Exception ignored) {}
        }
        if(first!=null){values.setProperty(marker,first.toString());dirty=true;}
    }
    private long aggregateMillis(LocalDate day, boolean rest) {
        try { return Math.max(0, Long.parseLong(values.getProperty((rest?"rest.":"work.")+day,"0"))); }
        catch(Exception e) { return 0; }
    }
    public synchronized long millis(LocalDate day, boolean rest) {
        // Detailed files commit before the compatibility aggregate. If a crash lands between
        // them, recover the committed facts rather than lose the last checkpoint.
        return Math.max(aggregateMillis(day,rest),sessions.legacyMillis(day,rest)+sessions.detailedMillis(day,rest));
    }
    public synchronized void record(long fromMillis, long toMillis, boolean rest) {
        record(fromMillis, toMillis, rest ? SessionStore.Kind.REST : SessionStore.Kind.FOCUS, "", "", "legacy-api");
    }
    public synchronized void record(long fromMillis, long toMillis, SessionStore.Kind kind,
                                    String taskId, String taskName, String sessionId) {
        if (toMillis <= fromMillis) return;
        ensureLegacy();
        boolean rest = kind != SessionStore.Kind.FOCUS;
        Map<LocalDate,Long> deltas=new LinkedHashMap<>();
        for (SessionStore.Delta delta : sessions.record(fromMillis,toMillis,kind,taskId,taskName,sessionId,dayBoundarySeconds,zone))
            deltas.merge(delta.day(),delta.millis(),Long::sum);
        for (var delta : deltas.entrySet()) {
            LocalDate day=delta.getKey();
            long total=Math.max(aggregateMillis(day,rest)+delta.getValue(),
                    sessions.legacyMillis(day,rest)+sessions.detailedMillis(day,rest));
            values.setProperty((rest?"rest.":"work.")+day, ""+total);
            String marker=rest?"restTrackedSince":"workTrackedSince";
            LocalDate tracked=trackedSince(rest);
            if(tracked.equals(LocalDate.MAX)||day.isBefore(tracked)) values.setProperty(marker,day.toString());
            dirty=true;
        }
    }
    public synchronized void flush(boolean force) {
        long now=System.currentTimeMillis();
        if (!dirty || (!force && now-lastSave<1000)) return;
        try { sessions.flush(); AtomicProperties.write(path,values);lastSave=now;dirty=false; }
        catch(Exception ex) { System.err.println("Cannot save activity ledger: "+ex.getMessage()); }
    }
}
