package com.eye.reminder;

import java.time.*;
import java.util.*;

/** One persisted reminder schedule. Times use the current system zone, matching the desktop clock. */
public final class ReminderItem {
    public enum ScheduleKind { ONCE, RELATIVE, DAILY, WEEKLY, INTERVAL, SEQUENCE }
    public enum Lifecycle { CURRENT, COMPLETED, EXPIRED, TEMPLATE, ENDED }
    public Lifecycle lifecycle=Lifecycle.CURRENT;
    public boolean pinned, awaitingCompletion;
    public long archivedAtEpochMs;
    public long completionTriggerEpochMs;
    public int relativeDelaySeconds=3600;

    public String id = UUID.randomUUID().toString();
    public String title = "";
    public String cloudId = "";
    public long cloudStart;
    public int cloudMaxTimes,cloudFired;
    public ScheduleKind kind = ScheduleKind.ONCE;
    public com.eye.calendar.DayCategory dayCategory=com.eye.calendar.DayCategory.EVERY_DAY;
    /** Next trigger in epoch milliseconds. Persisted for deterministic recovery after restart. */
    public long nextTriggerEpochMs;
    /** Local wall-clock time used to compute DAILY/WEEKLY recurrences. */
    public LocalTime time = LocalTime.of(9, 0);
    public EnumSet<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
    /** 0 means use Config.reminderDefaultMaskSeconds. */
    public int maskSeconds;
    public boolean resetWorkTimer;
    public boolean enabled = true;
    public long lastTriggeredEpochMs;
    public int intervalSeconds = 50 * 60;
    /** Each delay is measured from the preceding occurrence; index is the next occurrence. */
    public List<Integer> sequenceSeconds = new ArrayList<>(List.of(300, 600, 1200, 1800));
    public int sequenceIndex;

    public ReminderItem copy() {
        ReminderItem out = new ReminderItem();
        out.id = id; out.title = title; out.kind = kind;out.dayCategory=dayCategory;
        out.cloudId=cloudId;out.cloudStart=cloudStart;out.cloudMaxTimes=cloudMaxTimes;out.cloudFired=cloudFired;
        out.nextTriggerEpochMs = nextTriggerEpochMs; out.time = time;
        out.weekdays = weekdays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(weekdays);
        out.maskSeconds = maskSeconds; out.resetWorkTimer = resetWorkTimer;
        out.enabled = enabled; out.lastTriggeredEpochMs = lastTriggeredEpochMs;
        out.intervalSeconds = intervalSeconds;
        out.sequenceSeconds = new ArrayList<>(sequenceSeconds); out.sequenceIndex = sequenceIndex;
        out.lifecycle=lifecycle;out.pinned=pinned;out.awaitingCompletion=awaitingCompletion;out.archivedAtEpochMs=archivedAtEpochMs;out.relativeDelaySeconds=relativeDelaySeconds;out.completionTriggerEpochMs=completionTriggerEpochMs;
        return out;
    }

    public int effectiveMaskSeconds(int defaultSeconds) {
        return Math.max(5, maskSeconds > 0 ? maskSeconds : defaultSeconds);
    }

    /** Recompute nextTriggerEpochMs strictly after the supplied instant for a recurring schedule. */
    public void advanceAfter(long afterEpochMs) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime after = Instant.ofEpochMilli(afterEpochMs).atZone(zone);
        switch (kind) {
            case ONCE, RELATIVE -> enabled = false;
            case INTERVAL -> {
                if(cloudMaxTimes>0&&++cloudFired>=cloudMaxTimes){enabled=false;return;}
                if (intervalSeconds <= 0) { enabled = false; return; }
                long step = intervalSeconds * 1000L;
                long steps = Math.max(1, (afterEpochMs - nextTriggerEpochMs) / step + 1);
                nextTriggerEpochMs += steps * step;
            }
            case SEQUENCE -> {
                do {
                    sequenceIndex++;
                    if (sequenceIndex >= sequenceSeconds.size()) { enabled = false; return; }
                    int delay = sequenceSeconds.get(sequenceIndex);
                    if (delay <= 0) { enabled = false; return; }
                    nextTriggerEpochMs += delay * 1000L;
                } while (nextTriggerEpochMs <= afterEpochMs);
            }
            case DAILY, WEEKLY -> {
                if(kind==ScheduleKind.WEEKLY&&weekdays.isEmpty()){enabled=false;return;}
                // Search through the end of the packaged schedule for holiday-only recurrences.
                LocalDate base=after.toLocalDate();
                for(int i=0;i<732;i++){
                    LocalDate d=base.plusDays(i);
                    if(!dayCategory.matches(d)||kind==ScheduleKind.WEEKLY&&!weekdays.contains(d.getDayOfWeek()))continue;
                    ZonedDateTime candidate=ZonedDateTime.of(d,time,zone);
                    if(candidate.toInstant().toEpochMilli()<=afterEpochMs)continue;
                    nextTriggerEpochMs=candidate.toInstant().toEpochMilli();return;
                }
                // No published holiday occurrence: retain the rule and stop until data is available.
                enabled=false;nextTriggerEpochMs=0;
            }
        }
    }
}
