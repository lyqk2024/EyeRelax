package com.eye.stats;

import com.eye.core.SessionStore;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Read-only insights over frozen study days. Aggregate history never acquires invented intervals. */
public final class FocusInsights {
    private FocusInsights() {}

    public record Weekday(DayOfWeek day, long focusMs, long restMs, int calendarDays, int activeDays) {
        public long averageFocusMs() { return calendarDays == 0 ? 0 : focusMs / calendarDays; }
    }

    public record TaskShare(String id, String name, long focusMs, double share) {}

    /** loaded=false means the snapshot does not contain the entire comparison period. */
    public record Comparison(LocalDate from, LocalDate to, boolean loaded, boolean hasRecords,
                             long focusMs, long restMs, int activeDays, long currentFocusMs) {
        public OptionalDouble focusChangeRatio() {
            return loaded && focusMs > 0
                    ? OptionalDouble.of((currentFocusMs - (double) focusMs) / focusMs)
                    : OptionalDouble.empty();
        }
        public long focusDeltaMs() { return currentFocusMs - focusMs; }
    }

    /** Interval figures describe captured fragments, which may split at a study-day or rule boundary. */
    public record Report(LocalDate from, LocalDate to, int calendarDays, long focusMs, long restMs,
                         int activeDays, int longestStreak, long recordedFocusMs, int focusIntervalCount,
                         long longestFocusIntervalMs, long medianFocusIntervalMs,
                         List<Weekday> weekdays, List<TaskShare> tasks, Comparison comparison) {
        public Report { weekdays = List.copyOf(weekdays); tasks = List.copyOf(tasks); }
        public long averageDailyFocusMs() { return calendarDays == 0 ? 0 : focusMs / calendarDays; }
        public long averageActiveFocusMs() { return activeDays == 0 ? 0 : focusMs / activeDays; }
        public double captureRatio() { return focusMs == 0 ? 0 : Math.min(1, recordedFocusMs / (double) focusMs); }
        public long unplacedFocusMs() { return Math.max(0, focusMs - recordedFocusMs); }
        public Optional<Weekday> bestWeekday() {
            // Compare averages rather than totals so partial weeks do not favour extra weekdays.
            return weekdays.stream().filter(w -> w.focusMs() > 0).max(
                    Comparator.comparingDouble((Weekday w) -> w.focusMs() / (double) w.calendarDays())
                            .thenComparingInt(w -> -w.day().getValue()));
        }
    }

    public static Report analyze(Statistics.Snapshot snapshot, LocalDate from, LocalDate to) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(from); Objects.requireNonNull(to);
        if (to.isBefore(from) || from.isBefore(snapshot.from()) || to.isAfter(snapshot.to()))
            throw new IllegalArgumentException("Insight range must be inside the loaded snapshot");
        int calendarDays = Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);
        long focus = 0, rest = 0, recorded = 0;
        int active = 0, streak = 0, longestStreak = 0;
        long[] weekdayFocus = new long[7], weekdayRest = new long[7];
        int[] weekdayDays = new int[7], weekdayActive = new int[7];
        Map<String, Long> taskTotals = new HashMap<>();
        Map<String, String> names = new HashMap<>(snapshot.taskNames());
        List<Long> intervals = new ArrayList<>(); Set<String> seenIntervals = new HashSet<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Statistics.Day day = snapshot.day(date); int weekday = date.getDayOfWeek().getValue() - 1;
            focus += day.focusMs(); rest += day.restMs();
            weekdayFocus[weekday] += day.focusMs(); weekdayRest[weekday] += day.restMs(); weekdayDays[weekday]++;
            if (day.focusMs() > 0) { active++; weekdayActive[weekday]++; longestStreak = Math.max(longestStreak, ++streak); }
            else streak = 0;
            long assigned = 0;
            for (Statistics.Task task : day.tasks()) {
                if (task.actualMs() <= 0) continue;
                taskTotals.merge(task.id(), task.actualMs(), Long::sum); names.putIfAbsent(task.id(), task.name());
                assigned += task.actualMs();
            }
            // Mirrors Statistics.build's unassigned aggregate handling; no timestamp is inferred.
            if (day.focusMs() > assigned) taskTotals.merge(Statistics.FREE, day.focusMs() - assigned, Long::sum);
            for (SessionStore.Interval interval : day.intervals()) {
                if (interval.isRest() || interval.durationMs() == 0 || !seenIntervals.add(interval.id())) continue;
                intervals.add(interval.durationMs()); recorded += interval.durationMs();
            }
        }
        List<Weekday> weekdays = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) { int i = day.getValue() - 1;
            weekdays.add(new Weekday(day, weekdayFocus[i], weekdayRest[i], weekdayDays[i], weekdayActive[i])); }
        List<TaskShare> tasks = new ArrayList<>();
        for (var entry : taskTotals.entrySet()) {
            String id = entry.getKey(); String name = names.getOrDefault(id, Statistics.FREE.equals(id) ? "自由专注" : id);
            tasks.add(new TaskShare(id, name, entry.getValue(), focus == 0 ? 0 : entry.getValue() / (double) focus));
        }
        tasks.sort(Comparator.comparingLong(TaskShare::focusMs).reversed().thenComparing(TaskShare::name).thenComparing(TaskShare::id));
        intervals.sort(Long::compare);
        int size = intervals.size(); long longest = size == 0 ? 0 : intervals.get(size - 1), median = 0;
        if (size > 0) { median = intervals.get(size / 2); if (size % 2 == 0) {
            long lower = intervals.get(size / 2 - 1); median = lower + (median - lower) / 2; } }
        LocalDate previousTo = from.minusDays(1), previousFrom = from.minusDays(calendarDays);
        boolean loaded = !previousFrom.isBefore(snapshot.from()) && !previousTo.isAfter(snapshot.to());
        long previousFocus = 0, previousRest = 0; int previousActive = 0;
        if (loaded) for (LocalDate date = previousFrom; !date.isAfter(previousTo); date = date.plusDays(1)) {
            Statistics.Day day = snapshot.day(date); previousFocus += day.focusMs(); previousRest += day.restMs();
            if (day.focusMs() > 0) previousActive++;
        }
        Comparison comparison = new Comparison(previousFrom, previousTo, loaded, previousFocus > 0 || previousRest > 0,
                previousFocus, previousRest, previousActive, focus);
        return new Report(from, to, calendarDays, focus, rest, active, longestStreak, recorded, size,
                longest, median, weekdays, tasks, comparison);
    }
}
