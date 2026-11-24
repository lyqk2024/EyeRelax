package com.eye.stats;

import com.eye.core.SessionStore;
import java.time.*;
import java.util.*;

public final class FocusInsightsAcceptance {
    private static int checks;
    private static final long MINUTE = 60_000;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static SessionStore.Interval interval(String id, LocalDate day, int minutes, SessionStore.Kind kind) {
        long start = day.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        return new SessionStore.Interval(id, "same-session", "device", "reading", "Reading", kind,
                start, start + minutes * MINUTE, day, 4 * 3600, "UTC", "frozen-rule");
    }
    public static void main(String[] args) {
        LocalDate monday = LocalDate.of(2026, 11, 2), end = monday.plusDays(7);
        Map<LocalDate, Statistics.Day> days = new HashMap<>();
        var ten = interval("ten", monday, 10, SessionStore.Kind.FOCUS);
        var twenty = interval("twenty", monday, 20, SessionStore.Kind.FOCUS);
        days.put(monday, new Statistics.Day(monday, 60 * MINUTE, 5 * MINUTE,
                List.of(new Statistics.Task("reading", "Reading", 45 * MINUTE, 60 * MINUTE, 0)),
                List.of(ten, twenty, ten, interval("rest", monday, 5, SessionStore.Kind.REST))));
        days.put(monday.plusDays(1), new Statistics.Day(monday.plusDays(1), 40 * MINUTE, 0,
                List.of(new Statistics.Task("reading", "Reading", 40 * MINUTE, 60 * MINUTE, 0)), List.of()));
        days.put(end, new Statistics.Day(end, 10 * MINUTE, 0,
                List.of(new Statistics.Task(Statistics.FREE, "Free focus", 10 * MINUTE, 0, 0)), List.of()));
        LocalDate previous = monday.minusDays(8);
        days.put(previous, new Statistics.Day(previous, 55 * MINUTE, MINUTE, List.of(), List.of()));
        var snapshot = new Statistics.Snapshot(previous, end, days, Map.of(Statistics.FREE, "Free focus"));
        var report = FocusInsights.analyze(snapshot, monday, end);
        check(report.calendarDays() == 8 && report.focusMs() == 110 * MINUTE && report.restMs() == 5 * MINUTE, "inclusive period and totals");
        check(report.activeDays() == 3 && report.longestStreak() == 2, "activity and streak include zero days");
        check(report.averageDailyFocusMs() == 110 * MINUTE / 8 && report.averageActiveFocusMs() == 110 * MINUTE / 3, "daily and active averages differ");
        check(report.recordedFocusMs() == 30 * MINUTE && report.focusIntervalCount() == 2, "deduplicated focus intervals exclude rest");
        check(report.longestFocusIntervalMs() == 20 * MINUTE && report.medianFocusIntervalMs() == 15 * MINUTE, "fragments not merged into session");
        check(report.unplacedFocusMs() == 80 * MINUTE && Math.abs(report.captureRatio() - 3.0 / 11) < 1e-10, "legacy remainder remains unplaced");
        check(report.weekdays().size() == 7 && report.weekdays().get(0).calendarDays() == 2, "Monday-first seven weekdays");
        check(report.bestWeekday().orElseThrow().day() == DayOfWeek.TUESDAY, "best weekday uses averages: Tuesday 40 beats Monday 35");
        check(report.tasks().get(0).focusMs() == 85 * MINUTE && report.tasks().get(1).focusMs() == 25 * MINUTE, "task totals include free aggregate remainder");
        check(Math.abs(report.tasks().stream().mapToDouble(FocusInsights.TaskShare::share).sum() - 1) < 1e-10, "shares sum to one");
        check(report.comparison().from().equals(previous) && report.comparison().to().equals(monday.minusDays(1)), "equal-length previous period");
        check(report.comparison().loaded() && report.comparison().hasRecords() && report.comparison().focusChangeRatio().orElseThrow() == 1, "period comparison is +100 percent");
        var partial = FocusInsights.analyze(new Statistics.Snapshot(monday.minusDays(1), end, days, Map.of()), monday, end);
        check(!partial.comparison().loaded() && partial.comparison().focusChangeRatio().isEmpty(), "partial prior range is not compared as zero");
        var empty = FocusInsights.analyze(new Statistics.Snapshot(previous, end, Map.of(), Map.of()), monday, end);
        check(empty.focusMs() == 0 && empty.tasks().isEmpty() && empty.bestWeekday().isEmpty(), "empty period has no invented leader");
        check(empty.focusIntervalCount() == 0 && empty.medianFocusIntervalMs() == 0 && empty.captureRatio() == 0, "empty capture is zero");
        check(empty.comparison().loaded() && !empty.comparison().hasRecords() && empty.comparison().focusChangeRatio().isEmpty(), "no prior records explicitly distinguished");
        var legacy = FocusInsights.analyze(new Statistics.Snapshot(previous, end,
                Map.of(monday, new Statistics.Day(monday, 90 * MINUTE, 0, List.of(), List.of())), Map.of()), monday, end);
        check(legacy.focusMs() == 90 * MINUTE && legacy.recordedFocusMs() == 0 && legacy.focusIntervalCount() == 0, "legacy total never creates intervals");
        check(legacy.tasks().get(0).id().equals(Statistics.FREE), "legacy unassigned total retained");
        var odd = FocusInsights.analyze(new Statistics.Snapshot(previous, end, Map.of(monday,
                new Statistics.Day(monday, 45 * MINUTE, 0, List.of(), List.of(ten, twenty,
                        interval("fifteen", monday, 15, SessionStore.Kind.FOCUS)))), Map.of()), monday, end);
        check(odd.medianFocusIntervalMs() == 15 * MINUTE && odd.focusIntervalCount() == 3, "odd median uses middle captured interval");
        check(odd.comparison().loaded() && !odd.comparison().hasRecords() && odd.comparison().focusChangeRatio().isEmpty(), "new activity with zero baseline has no invented percentage");
        var decline = FocusInsights.analyze(new Statistics.Snapshot(previous, end,
                Map.of(previous, days.get(previous)), Map.of()), monday, end);
        check(decline.comparison().focusChangeRatio().orElseThrow() == -1, "zero current period is a valid minus-100-percent comparison");
        try { report.tasks().clear(); throw new AssertionError("mutable tasks"); } catch (UnsupportedOperationException expected) { checks++; }
        try { report.weekdays().clear(); throw new AssertionError("mutable weekdays"); } catch (UnsupportedOperationException expected) { checks++; }
        try { FocusInsights.analyze(snapshot, previous.minusDays(1), end); throw new AssertionError("outside range accepted"); } catch (IllegalArgumentException expected) { checks++; }
        check(days.get(monday).intervals().size() == 4 && days.get(monday).tasks().size() == 1, "source snapshot unchanged");
        System.out.println("FocusInsightsAcceptance: " + checks + " checks passed");
    }
}
