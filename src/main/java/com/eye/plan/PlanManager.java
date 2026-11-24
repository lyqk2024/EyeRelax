package com.eye.plan;

import static com.eye.plan.PlanPropertiesCodec.*;

import com.eye.core.DataPaths;
import com.eye.core.Config;
import com.eye.core.StudyDay;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class PlanManager {
    public static final class ScheduledOccurrence {
        public final LocalDate date;
        public final PlanItem plan;

        public ScheduledOccurrence(LocalDate date, PlanItem plan) {
            this.date = date;
            this.plan = plan;
        }
    }

    public static final class HistoricalTask {
        public final String planId;
        public final String name;

        public HistoricalTask(String planId, String name) {
            this.planId = planId;
            this.name = name;
        }

        @Override public String toString() {
            return name;
        }
    }

    public static final class HistoricalRecord {
        public final LocalDate date;
        public final PlanItem plan;
        public final int completedSeconds;
        public final int remainingSeconds;

        public HistoricalRecord(LocalDate date, PlanItem plan, int completedSeconds, int remainingSeconds) {
            this.date = date;
            this.plan = plan;
            this.completedSeconds = completedSeconds;
            this.remainingSeconds = remainingSeconds;
        }

        public int getPlannedSeconds() {
            return plan == null ? 0 : plan.plannedSeconds;
        }

        public int getPercent() {
            int planned = getPlannedSeconds();
            if (planned <= 0) return 0;
            return Math.max(0, Math.min(100, completedSeconds * 100 / planned));
        }
    }

    private final Map<String, PlanItem> allPlans = new LinkedHashMap<>();
    private final Map<String, PlanItem> activeToday = new LinkedHashMap<>();
    private final Set<LocalDate> customHolidays = new LinkedHashSet<>();
    private final PlanSchedule schedule=new PlanSchedule(customHolidays);
    private final Config config;
    private LocalDate date;

    public PlanManager() { this(new Config()); }
    public PlanManager(Config config) {
        this.config = config == null ? new Config() : config;
        this.date = StudyDay.dateNow(this.config);
    }

    private static Path base() { return DataPaths.root(); }
    private static Path allPlansPath() { return base().resolve("all-plans.properties"); }
    private static Path holidayPath() { return base().resolve("holidays.properties"); }
    private static Path historyPath() { return base().resolve("plan-history.log"); }

    private static Path progressPath(LocalDate d) {
        return base().resolve("plan-progress-" + d + ".properties");
    }

    public synchronized void loadToday() {
        date = StudyDay.dateNow(config);
        loadAllPlans();
        archiveExpired(date);
        loadCustomHolidays();
        rebuildActiveForDate(date);
    }

    /** Refresh the logical study day without re-reading the plan definition file. */
    public synchronized boolean refreshStudyDay() {
        LocalDate now = StudyDay.dateNow(config);
        if (now.equals(date)) return false;
        date = now;
        archiveExpired(date);
        loadCustomHolidays();
        rebuildActiveForDate(date);
        return true;
    }

    private void ensureStudyDay() { refreshStudyDay(); }

    public synchronized LocalDate getToday() {
        ensureStudyDay();
        return date;
    }

    public synchronized List<PlanItem> listAll() {
        ensureStudyDay();
        List<PlanItem> out = new ArrayList<>();
        for (PlanItem p : allPlans.values()) {
            PlanItem c = p.copy();
            PlanItem t = activeToday.get(p.id);
            if (t != null) {
                c.remainingSeconds = t.remainingSeconds;
                c.activeToday = true;
            } else {
                c.remainingSeconds = 0;
                c.activeToday = false;
            }
            out.add(c);
        }
        return out;
    }

    public synchronized List<PlanItem> listActiveToday() {
        ensureStudyDay();
        List<PlanItem> out = new ArrayList<>();
        for (PlanItem p : activeToday.values()) out.add(p.copy());
        return out;
    }

    public synchronized List<PlanItem> listTodayDailyPlans() {
        ensureStudyDay();
        List<PlanItem> out = new ArrayList<>();
        for (PlanItem p : activeToday.values()) {
            if (isDailyPlan(p)) out.add(p.copy());
        }
        sortTodayPlans(out);
        return out;
    }

    public synchronized List<PlanItem> listTodayOtherPlans() {
        ensureStudyDay();
        List<PlanItem> out = new ArrayList<>();
        for (PlanItem p : activeToday.values()) {
            if (!isDailyPlan(p)) out.add(p.copy());
        }
        sortTodayPlans(out);
        return out;
    }

    public synchronized List<PlanItem> listRemainingPositive() {
        ensureStudyDay();
        List<PlanItem> out = new ArrayList<>();
        for (PlanItem p : activeToday.values()) {
            if (p.remainingSeconds > 0) out.add(p.copy());
        }
        sortTodayPlans(out);
        return out;
    }

    public synchronized List<ScheduledOccurrence> listUpcomingScheduled(int daysAhead) {
        loadToday();
        LocalDate from = date.plusDays(1);
        LocalDate to = date.plusDays(Math.max(1, daysAhead));
        List<ScheduledOccurrence> out = new ArrayList<>();

        for (PlanItem plan : allPlans.values()) {
            if(plan.template||plan.archivedOn!=null)continue;
            if (isDailyPlan(plan)) continue;
            for (LocalDate occurrence : collectOccurrences(plan, from, to)) {
                out.add(new ScheduledOccurrence(occurrence, plan.copy()));
            }
        }

        out.sort(Comparator.comparing((ScheduledOccurrence o) -> o.date)
                .thenComparing(o -> o.plan.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public synchronized List<HistoricalRecord> listPastRecords() {
        return listPastRecords(null, null, null);
    }

    /** Returns past daily snapshots inside the inclusive date range, optionally for one task id. */
    public synchronized List<HistoricalRecord> listPastRecords(LocalDate fromInclusive,
                                                                LocalDate toInclusive,
                                                                String planId) {
        loadAllPlans();
        Map<String, String> historyNames = loadHistoryNameFallbacks();
        List<HistoricalRecord> out = new ArrayList<>();
        LocalDate today = StudyDay.dateNow(config);
        LocalDate effectiveTo = toInclusive == null || !toInclusive.isBefore(today)
                ? today.minusDays(1) : toInclusive;

        try {
            Files.createDirectories(base());
        } catch (IOException ignored) {
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base(), "plan-progress-*.properties")) {
            for (Path path : stream) {
                LocalDate day = parseProgressDate(path.getFileName().toString());
                if (day == null || !day.isBefore(today)) continue;
                if (fromInclusive != null && day.isBefore(fromInclusive)) continue;
                if (effectiveTo != null && day.isAfter(effectiveTo)) continue;

                Properties progress = loadProgress(day);
                for (String id : extractProgressIds(progress)) {
                    if (planId != null && !planId.equals(id)) continue;
                    PlanItem snapshot = buildPlanSnapshotFromProgress(progress, id, day, historyNames);
                    if (snapshot == null) continue;
                    int remaining = readRemainingSeconds(progress, id, snapshot.plannedSeconds);
                    remaining = Math.max(0, Math.min(snapshot.plannedSeconds, remaining));
                    int done = Math.max(0, snapshot.plannedSeconds - remaining);
                    out.add(new HistoricalRecord(day, snapshot, done, remaining));
                }
            }
        } catch (IOException ignored) {
        }

        out.sort(Comparator.comparing((HistoricalRecord r) -> r.date).reversed()
                .thenComparing(r -> r.plan.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Distinct historical tasks, preserving the snapshot name even after a live task is deleted. */
    public synchronized List<HistoricalTask> listHistoricalTasks() {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        for (HistoricalRecord record : listPastRecords()) {
            names.putIfAbsent(record.plan.id, record.plan.name);
        }
        List<HistoricalTask> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            out.add(new HistoricalTask(entry.getKey(), entry.getValue()));
        }
        out.sort(Comparator.comparing(t -> t.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public synchronized boolean hasPastRecordsBefore(LocalDate dateExclusive, String planId) {
        if (dateExclusive == null) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base(), "plan-progress-*.properties")) {
            for (Path path : stream) {
                LocalDate day = parseProgressDate(path.getFileName().toString());
                if (day == null || !day.isBefore(dateExclusive)) continue;
                Properties progress = loadProgress(day);
                if (planId == null) {
                    if (!extractProgressIds(progress).isEmpty()) return true;
                } else if (extractProgressIds(progress).contains(planId)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    /** Deletes exactly one historical daily task record; other tasks on that date are preserved. */
    public synchronized boolean deleteHistoricalRecord(LocalDate day, String id) {
        if (day == null || id == null) return false;
        Properties progress = loadProgress(day);
        Set<String> ids = extractProgressIds(progress);
        if (!ids.remove(id)) return false;
        progress.remove("remainSeconds." + id);
        progress.remove("remain." + id);
        String prefix = "snapshot." + id + ".";
        List<String> keys = new ArrayList<>(progress.stringPropertyNames());
        for (String key : keys) {
            if (key.startsWith(prefix)) progress.remove(key);
        }
        Path path = progressPath(day);
        if (ids.isEmpty()) {
            try {
                return Files.deleteIfExists(path);
            } catch (IOException e) {
                return false;
            }
        }
        progress.setProperty("ids", joinStringSet(ids));
        return saveProgress(day, progress);
    }

    public synchronized PlanItem addPlan(String name, int plannedSeconds) {
        return addPlan(name, plannedSeconds, PlanItem.PlanKind.TODAY_ONLY,
                PlanItem.RecurringRule.EVERY_DAY, Collections.emptySet(), Collections.emptySet());
    }

    public synchronized PlanItem addPlan(String name,
                                         int plannedSeconds,
                                         PlanItem.PlanKind kind,
                                         PlanItem.RecurringRule recurringRule,
                                         Set<Integer> weekdays,
                                         Set<LocalDate> dates) {
        ensureStudyDay();
        String id = UUID.randomUUID().toString();
        PlanItem item = new PlanItem(id, name, plannedSeconds, plannedSeconds);
        item.kind = kind;
        item.recurringRule = recurringRule;
        item.weekdays = new LinkedHashSet<>(weekdays == null ? Collections.emptySet() : weekdays);
        item.selectedDates = new LinkedHashSet<>(dates == null ? Collections.emptySet() : dates);
        item.createdDate = date;

        allPlans.put(id, item);
        saveAllPlans();
        rebuildActiveForDate(date);
        return get(id);
    }

    /**
     * Updates a live plan definition while preserving work already completed on today's
     * occurrence.  Editing a schedule never rewrites historical snapshots.  If a plan
     * was already active today, that occurrence remains available for the rest of today
     * even when the edited future rule would otherwise exclude today's date.
     */
    public synchronized PlanItem updatePlan(String id,
                                            String name,
                                            int plannedSeconds,
                                            PlanItem.PlanKind kind,
                                            PlanItem.RecurringRule recurringRule,
                                            Set<Integer> weekdays,
                                            Set<LocalDate> dates) {
        ensureStudyDay();
        if (id == null || name == null || name.trim().isEmpty() || plannedSeconds <= 0 || kind == null) {
            return null;
        }
        PlanItem base = allPlans.get(id);
        if (base == null) return null;

        PlanItem activeBefore = activeToday.get(id);
        boolean wasActiveToday = activeBefore != null;
        int completedToday = wasActiveToday
                ? Math.max(0, activeBefore.plannedSeconds - activeBefore.remainingSeconds)
                : 0;
        PlanItem.PlanKind oldKind = base.kind;

        base.name = name.trim();
        base.plannedSeconds = plannedSeconds;
        base.kind = kind;
        base.recurringRule = recurringRule == null ? PlanItem.RecurringRule.EVERY_DAY : recurringRule;
        base.weekdays = new LinkedHashSet<>(weekdays == null ? Collections.emptySet() : weekdays);
        base.selectedDates = new LinkedHashSet<>(dates == null ? Collections.emptySet() : dates);
        if (kind == PlanItem.PlanKind.TODAY_ONLY && oldKind != PlanItem.PlanKind.TODAY_ONLY) {
            base.createdDate = date;
        }

        saveAllPlans();
        rebuildActiveForDate(date);

        if (wasActiveToday) {
            PlanItem today = activeToday.get(id);
            if (today == null) {
                today = base.copy();
                today.activeToday = true;
                activeToday.put(id, today);
            }
            today.name = base.name;
            int extraToday = Math.max(0, activeBefore.temporaryExtraSeconds);
            today.temporaryExtraSeconds = extraToday;
            today.plannedSeconds = Math.max(0, base.plannedSeconds + extraToday);
            today.kind = base.kind;
            today.recurringRule = base.recurringRule;
            today.weekdays = new LinkedHashSet<>(base.weekdays);
            today.selectedDates = new LinkedHashSet<>(base.selectedDates);
            today.createdDate = base.createdDate;
            today.remainingSeconds = Math.max(0, today.plannedSeconds - completedToday);
            if (today.remainingSeconds==0) {
                today.completedAtEpochMs=activeBefore.completedAtEpochMs;
                today.completedZoneId=activeBefore.completedZoneId;
                if(activeBefore.remainingSeconds>0) markCompleted(today);
            } else { today.completedAtEpochMs=0; today.completedZoneId=""; }
            saveDailyProgress();
        }
        return get(id);
    }

    /** Add time only to the current study-day occurrence; the base plan definition is unchanged. */
    public synchronized PlanItem extendToday(String id, int extraSeconds) {
        ensureStudyDay();
        if (id == null || extraSeconds <= 0) return null;
        PlanItem item = activeToday.get(id);
        if (item == null) return null;
        long totalPlanned = (long) item.plannedSeconds + extraSeconds;
        long totalRemaining = (long) item.remainingSeconds + extraSeconds;
        long totalExtra = (long) item.temporaryExtraSeconds + extraSeconds;
        item.plannedSeconds = totalPlanned > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalPlanned;
        item.remainingSeconds = totalRemaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalRemaining;
        item.temporaryExtraSeconds = totalExtra > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalExtra;
        item.completedAtEpochMs=0; item.completedZoneId="";
        saveDailyProgress();
        return item.copy();
    }

    public synchronized void deletePlan(String id) {
        ensureStudyDay();
        allPlans.remove(id);
        activeToday.remove(id);
        saveAllPlans();
        saveDailyProgress();
    }

    public synchronized PlanItem get(String id) {
        ensureStudyDay();
        PlanItem p = activeToday.get(id);
        if (p != null) return p.copy();
        p = allPlans.get(id);
        return p == null ? null : p.copy();
    }

    public synchronized int getPlannedSeconds(String id) {
        ensureStudyDay();
        PlanItem p = activeToday.get(id);
        if (p != null) return p.plannedSeconds;
        p = allPlans.get(id);
        return p == null ? 0 : p.plannedSeconds;
    }

    public synchronized int getRemainingSeconds(String id) {
        ensureStudyDay();
        PlanItem p = activeToday.get(id);
        if (p == null) return 0;
        return Math.max(0, p.remainingSeconds);
    }

    public synchronized int consumeSeconds(String id, int delta) {
        ensureStudyDay();
        PlanItem p = activeToday.get(id);
        if (p == null) return 0;

        int d = Math.max(1, delta);
        int before = p.remainingSeconds;
        p.remainingSeconds = Math.max(0, p.remainingSeconds - d);
        if (before>0 && p.remainingSeconds==0) markCompleted(p);

        saveDailyProgress();
        appendHistory(p, before, p.remainingSeconds, d);
        return p.remainingSeconds;
    }

    private static void markCompleted(PlanItem item) {
        item.completedAtEpochMs=System.currentTimeMillis();
        item.completedZoneId=java.time.ZoneId.systemDefault().getId();
    }

    private void rebuildActiveForDate(LocalDate day) {
        activeToday.clear();
        Properties progress = loadProgress(day);

        for (PlanItem base : allPlans.values()) {
            if (!isActiveOn(base, day)) continue;

            PlanItem now = base.copy();
            now.activeToday = true;
            int extra = Math.max(0, parseInt(progress.getProperty("extraSeconds." + now.id), 0));
            now.temporaryExtraSeconds = extra;
            long total = (long) base.plannedSeconds + extra;
            now.plannedSeconds = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            int remain = readRemainingSeconds(progress, now.id, now.plannedSeconds);
            now.remainingSeconds = Math.max(0, Math.min(now.plannedSeconds, remain));
            if (now.remainingSeconds==0) {
                now.completedAtEpochMs=completionAt(progress,"snapshot."+now.id+".");
                now.completedZoneId=progress.getProperty("snapshot."+now.id+".completedZoneId","");
            }
            activeToday.put(now.id, now);
        }

        saveDailyProgress();
    }

    private boolean isActiveOn(PlanItem plan,LocalDate day){return schedule.isActiveOn(plan,day);}

    private boolean matchRecurring(PlanItem plan,LocalDate day){return schedule.matchRecurring(plan,day);}

    private List<LocalDate> collectOccurrences(PlanItem plan,LocalDate from,LocalDate to){return schedule.collectOccurrences(plan,from,to);}

    private void loadAllPlans() {
        allPlans.clear();
        if (!Files.exists(allPlansPath())) return;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(allPlansPath())) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return;
        }

        String ids = p.getProperty("ids", "").trim();
        if (ids.isEmpty()) return;

        for (String rawId : ids.split(",")) {
            String id = rawId.trim();
            if (id.isEmpty()) continue;

            PlanItem item = buildPlanSnapshotFromProperties(p, "plan." + id + ".", id);
            if (item != null) {
                allPlans.put(item.id, item);
            }
        }
    }

    private void saveAllPlans() {
        try {
            Files.createDirectories(base());
        } catch (IOException ignored) {
            return;
        }

        Properties p = new Properties();
        StringBuilder ids = new StringBuilder();
        for (String id : allPlans.keySet()) {
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
        }
        p.setProperty("ids", ids.toString());

        for (PlanItem item : allPlans.values()) {
            writePlanSnapshot(p, "plan." + item.id + ".", item);
        }

        try {
            com.eye.core.AtomicProperties.write(allPlansPath(),p);
        } catch (IOException ignored) {
        }
    }

    private boolean saveProgress(LocalDate day, Properties p) {
        Path path = progressPath(day);
        try {
            Files.createDirectories(base());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "EyeRelax daily progress " + day);
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private Properties loadProgress(LocalDate day) {
        Properties p = new Properties();
        Path path = progressPath(day);
        if (!Files.exists(path)) return p;

        try (InputStream in = Files.newInputStream(path)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        return p;
    }

    private void saveDailyProgress() {
        Path path = progressPath(date);
        try {
            Files.createDirectories(base());
        } catch (IOException ignored) {
            return;
        }

        Properties p = new Properties();
        StringBuilder ids = new StringBuilder();
        for (PlanItem item : activeToday.values()) {
            if (ids.length() > 0) ids.append(',');
            ids.append(item.id);
            writeRemainingSeconds(p, item.id, item.remainingSeconds);
            if (item.temporaryExtraSeconds > 0) p.setProperty("extraSeconds." + item.id, String.valueOf(item.temporaryExtraSeconds));
            writePlanSnapshot(p, "snapshot." + item.id + ".", item);
        }
        p.setProperty("ids", ids.toString());

        try (OutputStream out = Files.newOutputStream(path)) {
            p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "EyeRelax daily progress " + date);
        } catch (IOException ignored) {
        }
    }

    private void appendHistory(PlanItem p, int before, int after, int consume) {
        try {
            Files.createDirectories(base());
            String line = LocalDateTime.now() + "\t" + date + "\t" + p.id + "\t" + p.name + "\t"
                    + consume + "\t" + before + "\t" + after + System.lineSeparator();
            Files.write(historyPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private void loadCustomHolidays() {
        customHolidays.clear();
        if (!Files.exists(holidayPath())) return;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(holidayPath())) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            customHolidays.addAll(parseDates(p.getProperty("dates", "")));
        } catch (IOException ignored) {
        }
    }

    private PlanItem buildPlanSnapshotFromProgress(Properties progress, String id, LocalDate day, Map<String, String> historyNames) {
        PlanItem item = buildPlanSnapshotFromProperties(progress, "snapshot." + id + ".", id);
        if (item != null) return item;

        PlanItem current = allPlans.get(id);
        if (current != null) return current.copy();

        String name = historyNames.get(day + "|" + id);
        if (name == null || name.trim().isEmpty()) {
            name = id;
        }

        int planned = readPlannedSeconds(progress, "snapshot." + id + ".", 0);
        if (planned <= 0) {
            planned = readRemainingSeconds(progress, id, 0);
        }
        PlanItem fallback = new PlanItem(id, name, planned, readRemainingSeconds(progress, id, planned));
        fallback.createdDate = day;
        return fallback;
    }

    private PlanItem buildPlanSnapshotFromProperties(Properties p,String prefix,String id){
        return PlanPropertiesCodec.readSnapshot(p,prefix,id,date==null?StudyDay.dateNow(config):date);
    }

    private Set<String> extractProgressIds(Properties progress) {
        Set<String> ids = new LinkedHashSet<>();
        String raw = progress.getProperty("ids", "").trim();
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (!id.isEmpty()) ids.add(id);
            }
        }
        for (String key : progress.stringPropertyNames()) {
            if (key.startsWith("remainSeconds.")) {
                ids.add(key.substring("remainSeconds.".length()));
            } else if (key.startsWith("remain.")) {
                ids.add(key.substring("remain.".length()));
            }
        }
        return ids;
    }

    private Map<String, String> loadHistoryNameFallbacks() {
        Map<String, String> names = new LinkedHashMap<>();
        if (!Files.exists(historyPath())) return names;

        try {
            for (String line : Files.readAllLines(historyPath(), StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t");
                if (parts.length < 4) continue;
                names.put(parts[1] + "|" + parts[2], parts[3]);
            }
        } catch (IOException ignored) {
        }
        return names;
    }

    private LocalDate parseProgressDate(String fileName) {
        if (!fileName.startsWith("plan-progress-") || !fileName.endsWith(".properties")) {
            return null;
        }
        String datePart = fileName.substring("plan-progress-".length(), fileName.length() - ".properties".length());
        try {
            return LocalDate.parse(datePart);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDailyPlan(PlanItem p) {
        return p.kind == PlanItem.PlanKind.RECURRING && p.recurringRule == PlanItem.RecurringRule.EVERY_DAY;
    }

    private void sortTodayPlans(List<PlanItem> plans) {
        plans.sort(Comparator.comparing((PlanItem p) -> !p.pinned).thenComparing(p -> p.remainingSeconds)
                .thenComparing(p -> p.name, String.CASE_INSENSITIVE_ORDER));
    }

    /** Archive definitions only; never rewrite the original daily snapshots. */
    synchronized void archiveExpired(LocalDate studyDay){
        boolean changed=false;
        for(PlanItem p:allPlans.values()){
            if(p.template||p.archivedOn!=null||p.kind==PlanItem.PlanKind.RECURRING)continue;
            LocalDate last=p.kind==PlanItem.PlanKind.TODAY_ONLY?p.createdDate:p.selectedDates.stream().max(LocalDate::compareTo).orElse(p.createdDate);
            if(last.isBefore(studyDay)){p.archivedOn=studyDay;changed=true;}
        }
        if(changed)saveAllPlans();
    }
    public synchronized void setPinned(String id,boolean value){
        PlanItem p=allPlans.get(id);if(p==null)return;p.pinned=value;PlanItem active=activeToday.get(id);if(active!=null)active.pinned=value;saveAllPlans();
    }
    public synchronized PlanItem saveTemplate(String id){
        ensureStudyDay();PlanItem p=allPlans.get(id);if(p==null)return null;
        PlanItem copy=definitionCopy(p);copy.template=true;allPlans.put(copy.id,copy);saveAllPlans();return copy.copy();
    }
    public synchronized PlanItem copyForToday(String id){
        ensureStudyDay();PlanItem p=allPlans.get(id);if(p==null)return null;
        return addPlan(p.name,p.plannedSeconds,PlanItem.PlanKind.TODAY_ONLY,PlanItem.RecurringRule.EVERY_DAY,Set.of(),Set.of());
    }
    private PlanItem definitionCopy(PlanItem source){
        PlanItem copy=new PlanItem(UUID.randomUUID().toString(),source.name,source.plannedSeconds,source.plannedSeconds);
        copy.kind=source.kind;copy.recurringRule=source.recurringRule;copy.weekdays=new LinkedHashSet<>(source.weekdays);
        copy.selectedDates=new LinkedHashSet<>(source.selectedDates);copy.createdDate=date;return copy;
    }

    /** Apply one shared definition without resetting elapsed time on the current occurrence. */
    public synchronized void applyCloud(PlanItem incoming,String doneDay) {
        ensureStudyDay();PlanItem previous=activeToday.get(incoming.id);
        allPlans.put(incoming.id,incoming.copy());saveAllPlans();
        if(schedule.isActiveOn(incoming,date)) {
            PlanItem active=incoming.copy();
            active.remainingSeconds=previous==null?incoming.plannedSeconds:Math.max(0,incoming.plannedSeconds-Math.max(0,previous.plannedSeconds-previous.remainingSeconds));
            if(date.toString().equals(doneDay))active.remainingSeconds=0;
            activeToday.put(active.id,active);
        }else activeToday.remove(incoming.id);
        saveDailyProgress();
    }

}
