package com.eye.core;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Local interval ledger. Ownership is frozen at capture, NEVER reconstructed with today's
 * boundary. Immutable IDs, device IDs and rule IDs make later sync possible without making
 * an offline client pretend it has an account. Old daily totals are retained separately.
 */
public final class SessionStore {
    public enum Kind { FOCUS, REST, IMMEDIATE_REST, REMINDER_REST }
    public record Interval(String id, String sessionId, String deviceId, String taskId,
                           String taskName, Kind kind, long startAt, long endAt,
                           LocalDate studyDate, int boundarySeconds, String zoneId,
                           String ruleId) {
        public long durationMs() { return Math.max(0, endAt - startAt); }
        public boolean isRest() { return kind != Kind.FOCUS; }
        public String displayName() {
            return switch (kind) {
                case FOCUS -> taskName == null || taskName.isBlank() ? "自由专注" : taskName;
                case REST -> "正常休息";
                case IMMEDIATE_REST -> "立即休息";
                case REMINDER_REST -> "提醒遮罩";
            };
        }
    }
    public record Delta(LocalDate day, long millis) {}
    public record Rule(String id, long effectiveAt, int boundarySeconds, String zoneId) {}

    private final Path root;
    private final Map<LocalDate, LinkedHashMap<String, Interval>> days = new HashMap<>();
    private final Set<LocalDate> dirty = new HashSet<>();
    private final List<Rule> rules = new ArrayList<>();
    private String deviceId;
    private Interval tail;
    private boolean rulesDirty;
    private Properties legacyTotals;

    public SessionStore(Path root) {
        this.root = root;
        Properties meta = read(root.resolve("identity.properties"));
        deviceId = meta.getProperty("deviceId", "");
        Properties history = read(root.resolve("rules.properties"));
        for (String key : history.stringPropertyNames()) {
            if (!key.endsWith(".effectiveAt")) continue;
            String prefix = key.substring(0, key.length() - "effectiveAt".length());
            try { rules.add(new Rule(prefix.substring(0, prefix.length()-1),
                    Long.parseLong(history.getProperty(key)),
                    Integer.parseInt(history.getProperty(prefix+"boundarySeconds")),
                    history.getProperty(prefix+"zoneId"))); }
            catch (RuntimeException ex) { System.err.println("Skipped invalid study-day rule: " + key); }
        }
        rules.sort(Comparator.comparingLong(Rule::effectiveAt));
    }

    /** Written once, before the first detailed interval, never replaced on upgrade. */
    public synchronized void initializeLegacy(Properties totals) {
        Path baseline = root.resolve("legacy-totals.properties");
        try {
            if (deviceId.isBlank()) {
                deviceId = UUID.randomUUID().toString();
                Properties id = new Properties(); id.setProperty("schemaVersion", "1");
                id.setProperty("deviceId", deviceId); AtomicProperties.write(root.resolve("identity.properties"), id);
            }
            if (!Files.exists(baseline) && !Files.exists(baseline.resolveSibling(baseline.getFileName()+".bak"))) {
                Properties copy = new Properties(); copy.putAll(totals);
                copy.setProperty("schemaVersion", "1"); copy.setProperty("capturedAt", ""+System.currentTimeMillis());
                // Snapshot existing per-task totals too. Without this, a same-day upgrade can
                // wrongly label a task's new sub-second tail as free focus.
                Path profile = root.getParent();
                if (profile != null && Files.isDirectory(profile)) {
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(profile, "plan-progress-*.properties")) {
                        for (Path path : files) {
                            String name = path.getFileName().toString();
                            String day = name.substring("plan-progress-".length(),name.length()-".properties".length());
                            try { LocalDate.parse(day); } catch (RuntimeException invalid) { continue; }
                            Properties daily = read(path);
                            for (String key : daily.stringPropertyNames()) if (key.startsWith("remainSeconds.")) {
                                String id = key.substring("remainSeconds.".length());
                                String planned = daily.getProperty("snapshot."+id+".plannedSeconds");
                                if (planned == null) continue; // do not infer missing legacy goals
                                try {
                                    long target = Long.parseLong(planned), remaining = Long.parseLong(daily.getProperty(key));
                                    long spent = Math.max(0,target-Math.max(0,remaining));
                                    copy.setProperty("task."+day+"."+id, ""+(spent*1000L));
                                } catch (RuntimeException ignored) { /* preserve original file without guessing */ }
                            }
                        }
                    }
                }
                AtomicProperties.write(baseline, copy);
            }
            legacyTotals = read(baseline);
        } catch (IOException ex) { throw new IllegalStateException("Cannot initialize session ledger", ex); }
    }

    public synchronized Rule changeRule(long at, int seconds, ZoneId zone) {
        seconds = StudyDay.sanitizeBoundarySeconds(seconds);
        Rule previous = ruleAt(at);
        if (previous != null && previous.boundarySeconds == seconds && previous.zoneId.equals(zone.getId())) return previous;
        Rule rule = new Rule(UUID.randomUUID().toString(), at, seconds, zone.getId());
        rules.add(rule); rules.sort(Comparator.comparingLong(Rule::effectiveAt)); rulesDirty = true;
        tail = null; return rule;
    }
    private Rule ruleAt(long instant) {
        Rule result = null;
        for (Rule rule : rules) { if (rule.effectiveAt > instant) break; result = rule; }
        return result;
    }
    public synchronized List<Rule> rules() { return List.copyOf(rules); }

    public synchronized List<Delta> record(long from, long to, Kind kind, String taskId, String name,
                                           String sessionId, int boundary, ZoneId zone) {
        if (to <= from) return List.of();
        Rule startRule = ruleAt(from);
        // An explicit setting change has already added a rule. Unannounced external changes
        // (e.g. startup configuration) take effect at the first new captured instant only.
        if (startRule == null || (rules.get(rules.size()-1).effectiveAt <= from &&
                (startRule.boundarySeconds != boundary || !startRule.zoneId.equals(zone.getId())))) {
            changeRule(from, boundary, zone);
        }
        String task = taskId == null ? "" : taskId;
        String label = name == null ? "" : name;
        ArrayList<Delta> result = new ArrayList<>();
        long cursor = from;
        while (cursor < to) {
            Rule rule = ruleAt(cursor);
            if (rule == null) rule = changeRule(cursor, boundary, zone);
            ZoneId capturedZone = ZoneId.of(rule.zoneId);
            LocalDate day = StudyDay.dateAt(cursor, capturedZone, rule.boundarySeconds);
            long end = Math.min(to, StudyDay.nextBoundaryAfter(cursor, capturedZone, rule.boundarySeconds));
            for (Rule next : rules) if (next.effectiveAt > cursor) { end = Math.min(end, next.effectiveAt); break; }
            if (end <= cursor) throw new IllegalStateException("Non-increasing session boundary");
            LinkedHashMap<String, Interval> map = loadDay(day);
            boolean merge = tail != null && tail.endAt == cursor && tail.studyDate.equals(day)
                    && tail.kind == kind && tail.taskId.equals(task) && tail.taskName.equals(label)
                    && tail.sessionId.equals(sessionId) && tail.ruleId.equals(rule.id);
            Interval interval = new Interval(merge ? tail.id : UUID.randomUUID().toString(), sessionId,
                    deviceId, task, label, kind, merge ? tail.startAt : cursor, end, day,
                    rule.boundarySeconds, rule.zoneId, rule.id);
            map.put(interval.id, interval); tail = interval; dirty.add(day);
            result.add(new Delta(day, end - cursor)); cursor = end;
        }
        return result;
    }

    public synchronized List<Interval> intervals(LocalDate from, LocalDate until) {
        ArrayList<Interval> result = new ArrayList<>();
        if (until.isBefore(from)) return result;
        for (LocalDate d = from; !d.isAfter(until); d = d.plusDays(1)) result.addAll(loadDay(d).values());
        result.sort(Comparator.comparingLong(Interval::startAt).thenComparing(Interval::id));
        return List.copyOf(result);
    }
    public synchronized long legacyMillis(LocalDate date, boolean rest) {
        Properties p = legacy();
        try { return Math.max(0, Long.parseLong(p.getProperty((rest?"rest.":"work.")+date,"0"))); }
        catch (RuntimeException e) { return 0; }
    }
    private Properties legacy() {
        if (legacyTotals == null) legacyTotals = read(root.resolve("legacy-totals.properties"));
        return legacyTotals;
    }
    public synchronized long legacyTaskMillis(LocalDate date, String taskId) {
        try { return Math.max(0, Long.parseLong(legacy().getProperty("task."+date+"."+taskId,"0"))); }
        catch (RuntimeException invalid) { return 0; }
    }
    public synchronized long detailedMillis(LocalDate date, boolean rest) {
        return loadDay(date).values().stream().filter(i -> i.isRest()==rest).mapToLong(Interval::durationMs).sum();
    }
    private LinkedHashMap<String, Interval> loadDay(LocalDate day) {
        return days.computeIfAbsent(day, d -> {
            LinkedHashMap<String, Interval> result = new LinkedHashMap<>();
            Properties p = read(root.resolve(d+".properties"));
            List<Interval> loaded = new ArrayList<>();
            for (String key : p.stringPropertyNames()) {
                if (!key.endsWith(".startAt")) continue;
                String id = key.substring(0, key.length()-8), pre = id+".";
                try {
                    Interval interval = new Interval(id, p.getProperty(pre+"sessionId",id),
                            p.getProperty(pre+"deviceId",""), p.getProperty(pre+"taskId",""),
                            p.getProperty(pre+"taskName",""), Kind.valueOf(p.getProperty(pre+"kind")),
                            Long.parseLong(p.getProperty(key)), Long.parseLong(p.getProperty(pre+"endAt")),
                            LocalDate.parse(p.getProperty(pre+"studyDate")),
                            Integer.parseInt(p.getProperty(pre+"boundarySeconds")), p.getProperty(pre+"zoneId"),
                            p.getProperty(pre+"ruleId",""));
                    if (!interval.studyDate.equals(d) || interval.durationMs() <= 0) continue;
                    ZoneId.of(interval.zoneId); loaded.add(interval);
                } catch (RuntimeException ex) { System.err.println("Skipped damaged interval " + id); }
            }
            loaded.sort(Comparator.comparingLong(Interval::startAt));
            for (Interval interval : loaded) result.put(interval.id, interval);
            return result;
        });
    }
    public synchronized void flush() throws IOException {
        if (rulesDirty) {
            Properties p = new Properties(); p.setProperty("schemaVersion", "1");
            for (Rule r : rules) { String key=r.id+"."; p.setProperty(key+"effectiveAt", ""+r.effectiveAt);
                p.setProperty(key+"boundarySeconds", ""+r.boundarySeconds); p.setProperty(key+"zoneId", r.zoneId); }
            AtomicProperties.write(root.resolve("rules.properties"), p); rulesDirty = false;
        }
        for (LocalDate day : new ArrayList<>(dirty)) {
            Properties p = new Properties(); p.setProperty("schemaVersion", "1");
            for (Interval r : loadDay(day).values()) {
                String key=r.id+".";
                p.setProperty(key+"sessionId",r.sessionId); p.setProperty(key+"deviceId",r.deviceId);
                p.setProperty(key+"taskId",r.taskId); p.setProperty(key+"taskName",r.taskName);
                p.setProperty(key+"kind",r.kind.name()); p.setProperty(key+"startAt",""+r.startAt);
                p.setProperty(key+"endAt",""+r.endAt); p.setProperty(key+"studyDate",r.studyDate.toString());
                p.setProperty(key+"boundarySeconds",""+r.boundarySeconds); p.setProperty(key+"zoneId",r.zoneId);
                p.setProperty(key+"ruleId",r.ruleId);
            }
            AtomicProperties.write(root.resolve(day+".properties"),p); dirty.remove(day);
        }
    }
    private static Properties read(Path path) {
        for (Path p : new Path[]{path, path.resolveSibling(path.getFileName()+".bak")}) {
            try { if (Files.isRegularFile(p)) return AtomicProperties.read(p); }
            catch (IOException | IllegalArgumentException ex) { System.err.println("Session ledger read: " + ex.getMessage()); }
        }
        return new Properties();
    }
}
