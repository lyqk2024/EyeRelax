package com.eye.plan;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class PlanItem {
    public enum PlanKind { RECURRING, MULTI_DAY, TODAY_ONLY }
    public enum RecurringRule { EVERY_DAY, WEEKDAY, WEEKEND, HOLIDAY, BY_WEEKDAY, WORKDAY, NON_WORKDAY, PUBLIC_HOLIDAY, NORMAL_WORKDAY }

    public final String id;
    public String name;
    public int plannedSeconds;

    public PlanKind kind = PlanKind.TODAY_ONLY;
    public RecurringRule recurringRule = RecurringRule.EVERY_DAY;
    public Set<Integer> weekdays = new LinkedHashSet<>();
    public Set<LocalDate> selectedDates = new LinkedHashSet<>();
    public LocalDate createdDate = LocalDate.now();
    public LocalDate archivedOn;
    public LocalDate cloudEnd;
    public boolean pinned, template;

    public int remainingSeconds;
    /** Extra time added to this study-day occurrence only; never written into the base plan definition. */
    public int temporaryExtraSeconds;
    public boolean activeToday;
    /** Actual completion event for this daily occurrence; unknown legacy events stay zero. */
    public long completedAtEpochMs;
    public String completedZoneId = "";

    public PlanItem(String id, String name, int plannedSeconds, int remainingSeconds) {
        this.id = id;
        this.name = name;
        this.plannedSeconds = Math.max(0, plannedSeconds);
        this.remainingSeconds = Math.max(0, remainingSeconds);
    }

    public PlanItem copy() {
        PlanItem c = new PlanItem(id, name, plannedSeconds, remainingSeconds);
        c.kind = kind;
        c.recurringRule = recurringRule;
        c.weekdays = new LinkedHashSet<>(weekdays);
        c.selectedDates = new LinkedHashSet<>(selectedDates);
        c.createdDate = createdDate;c.cloudEnd=cloudEnd;
        c.archivedOn=archivedOn;c.pinned=pinned;c.template=template;
        c.temporaryExtraSeconds = temporaryExtraSeconds;
        c.activeToday = activeToday;
        c.completedAtEpochMs=completedAtEpochMs; c.completedZoneId=completedZoneId;
        return c;
    }

    @Override
    public String toString() {
        return name + "（剩余 " + fmtHms(remainingSeconds) + "）";
    }

    public static String fmtHms(int totalSeconds) {
        int s = Math.max(0, totalSeconds);
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        return String.format("%02d:%02d:%02d", h, m, sec);
    }

    /**
     * Duration entry parser used by settings and plan forms.
     * Plain positive integers keep the old UX and mean minutes; clock notation supports seconds.
     * Examples: 30 -> 30m, 02:30 -> 2m30s, 01:02:03 -> 1h2m3s.
     */
    public static int parseDurationSeconds(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty duration");
        String text = raw.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("empty duration");
        try {
            if (!text.contains(":")) {
                long minutes = Long.parseLong(text);
                long seconds = Math.multiplyExact(minutes, 60L);
                if (seconds <= 0 || seconds > Integer.MAX_VALUE) throw new IllegalArgumentException("duration out of range");
                return (int) seconds;
            }
            String[] parts = text.split(":", -1);
            if (parts.length != 2 && parts.length != 3) throw new IllegalArgumentException("invalid duration");
            long h = 0;
            long m;
            long s;
            if (parts.length == 2) {
                m = Long.parseLong(parts[0]);
                s = Long.parseLong(parts[1]);
            } else {
                h = Long.parseLong(parts[0]);
                m = Long.parseLong(parts[1]);
                s = Long.parseLong(parts[2]);
                if (m >= 60) throw new IllegalArgumentException("minute component out of range");
            }
            if (h < 0 || m < 0 || s < 0 || s >= 60) throw new IllegalArgumentException("duration component out of range");
            long total = Math.addExact(Math.addExact(Math.multiplyExact(h, 3600L), Math.multiplyExact(m, 60L)), s);
            if (total <= 0 || total > Integer.MAX_VALUE) throw new IllegalArgumentException("duration out of range");
            return (int) total;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("invalid duration", ex);
        }
    }

    public static String durationInput(int seconds) {
        return fmtHms(seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanItem)) return false;
        PlanItem planItem = (PlanItem) o;
        return Objects.equals(id, planItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
