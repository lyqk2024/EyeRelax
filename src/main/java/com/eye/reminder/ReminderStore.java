package com.eye.reminder;

import com.eye.core.AtomicProperties;
import com.eye.core.DataPaths;

import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Atomic local persistence for reminder schedules. */
public final class ReminderStore {
    private final Path path;
    public ReminderStore() { this(DataPaths.file("reminders.properties")); }
    public ReminderStore(Path path) { this.path = path; }

    public synchronized List<ReminderItem> load() {
        Properties p;
        try { p = AtomicProperties.read(path); }
        catch (Exception ex) { p = new Properties(); }
        List<ReminderItem> out = new ArrayList<>();
        String rawIds = p.getProperty("ids", "");
        for (String raw : rawIds.split(",")) {
            String id = raw.trim();
            if (id.isEmpty()) continue;
            String prefix = "r." + id + ".";
            try {
                ReminderItem item = new ReminderItem();
                item.id = id;
                item.title = p.getProperty(prefix + "title", "提醒");
                item.kind = ReminderItem.ScheduleKind.valueOf(p.getProperty(prefix + "kind", "ONCE"));
                item.dayCategory=com.eye.calendar.DayCategory.valueOf(p.getProperty(prefix+"dayCategory","EVERY_DAY"));
                item.nextTriggerEpochMs = Long.parseLong(p.getProperty(prefix + "nextEpochMs", "0"));
                item.time = LocalTime.parse(p.getProperty(prefix + "time", "09:00"));
                item.weekdays = parseWeekdays(p.getProperty(prefix + "weekdays", ""));
                item.maskSeconds = Math.max(0, Integer.parseInt(p.getProperty(prefix + "maskSeconds", "0")));
                item.resetWorkTimer = Boolean.parseBoolean(p.getProperty(prefix + "resetWorkTimer", "false"));
                item.enabled = Boolean.parseBoolean(p.getProperty(prefix + "enabled", "true"));
                item.cloudMaxTimes=Integer.parseInt(p.getProperty(prefix+"cloudMaxTimes","0"));item.cloudFired=Integer.parseInt(p.getProperty(prefix+"cloudFired","0"));
                item.cloudId=p.getProperty(prefix+"cloudId","");item.cloudStart=Long.parseLong(p.getProperty(prefix+"cloudStart","0"));
                item.lastTriggeredEpochMs = Long.parseLong(p.getProperty(prefix + "lastTriggeredEpochMs", "0"));
                item.intervalSeconds = Integer.parseInt(p.getProperty(prefix + "intervalSeconds", "3000"));
                String sequence = p.getProperty(prefix + "sequenceSeconds", "300,600,1200,1800");
                item.sequenceSeconds = new ArrayList<>();
                for (String delay : sequence.split(",")) item.sequenceSeconds.add(Integer.parseInt(delay.trim()));
                item.sequenceIndex = Integer.parseInt(p.getProperty(prefix + "sequenceIndex", "0"));
                item.lifecycle=ReminderItem.Lifecycle.valueOf(p.getProperty(prefix+"lifecycle","CURRENT"));
                item.pinned=Boolean.parseBoolean(p.getProperty(prefix+"pinned","false"));
                item.awaitingCompletion=Boolean.parseBoolean(p.getProperty(prefix+"awaitingCompletion","false"));
                item.completionTriggerEpochMs=Long.parseLong(p.getProperty(prefix+"completionTriggerEpochMs",Long.toString(item.awaitingCompletion?item.nextTriggerEpochMs:0)));
                item.archivedAtEpochMs=Long.parseLong(p.getProperty(prefix+"archivedAtEpochMs","0"));
                item.relativeDelaySeconds=Integer.parseInt(p.getProperty(prefix+"relativeDelaySeconds","3600"));
                if (item.nextTriggerEpochMs > 0 || !item.enabled) out.add(item);
            } catch (Exception ex) {
                System.err.println("Reminder record skipped: " + id + ": " + ex.getMessage());
            }
        }
        out.sort(Comparator.comparingLong(x -> x.enabled ? x.nextTriggerEpochMs : Long.MAX_VALUE));
        return out;
    }

    public synchronized void save(Collection<ReminderItem> items) {
        Properties p = new Properties();
        p.setProperty("version", "2");
        StringJoiner ids = new StringJoiner(",");
        for (ReminderItem item : items) {
            if (item == null || item.id == null || item.id.isBlank()) continue;
            ids.add(item.id);
            String prefix = "r." + item.id + ".";
            p.setProperty(prefix + "title", item.title == null ? "" : item.title);
            p.setProperty(prefix + "kind", item.kind.name());
            p.setProperty(prefix+"dayCategory",item.dayCategory.name());
            p.setProperty(prefix + "nextEpochMs", Long.toString(item.nextTriggerEpochMs));
            p.setProperty(prefix + "time", item.time == null ? "09:00" : item.time.toString());
            p.setProperty(prefix + "weekdays", formatWeekdays(item.weekdays));
            p.setProperty(prefix + "maskSeconds", Integer.toString(Math.max(0, item.maskSeconds)));
            p.setProperty(prefix + "resetWorkTimer", Boolean.toString(item.resetWorkTimer));
            p.setProperty(prefix + "enabled", Boolean.toString(item.enabled));
            p.setProperty(prefix+"cloudMaxTimes",""+item.cloudMaxTimes);p.setProperty(prefix+"cloudFired",""+item.cloudFired);
            p.setProperty(prefix+"cloudId",item.cloudId);p.setProperty(prefix+"cloudStart",Long.toString(item.cloudStart));
            p.setProperty(prefix + "lastTriggeredEpochMs", Long.toString(item.lastTriggeredEpochMs));
            p.setProperty(prefix + "intervalSeconds", Integer.toString(item.intervalSeconds));
            p.setProperty(prefix + "sequenceSeconds", item.sequenceSeconds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
            p.setProperty(prefix + "sequenceIndex", Integer.toString(item.sequenceIndex));
            p.setProperty(prefix+"lifecycle",item.lifecycle.name());p.setProperty(prefix+"pinned",Boolean.toString(item.pinned));
            p.setProperty(prefix+"awaitingCompletion",Boolean.toString(item.awaitingCompletion));
            p.setProperty(prefix+"completionTriggerEpochMs",Long.toString(item.completionTriggerEpochMs));
            p.setProperty(prefix+"archivedAtEpochMs",Long.toString(item.archivedAtEpochMs));p.setProperty(prefix+"relativeDelaySeconds",Integer.toString(item.relativeDelaySeconds));
        }
        p.setProperty("ids", ids.toString());
        try { AtomicProperties.write(path, p); }
        catch (Exception ex) { System.err.println("Cannot save reminders: " + ex.getMessage()); }
    }

    private static EnumSet<DayOfWeek> parseWeekdays(String raw) {
        EnumSet<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        if (raw == null || raw.isBlank()) return out;
        for (String token : raw.split("\\|")) {
            try { out.add(DayOfWeek.valueOf(token)); } catch (Exception ignored) {}
        }
        return out;
    }

    private static String formatWeekdays(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return "";
        StringJoiner join = new StringJoiner("|");
        for (DayOfWeek day : DayOfWeek.values()) if (days.contains(day)) join.add(day.name());
        return join.toString();
    }
}
