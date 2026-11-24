package com.eye.plan;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
/** Plan properties serialization and legacy minute/second compatibility; no UI or file I/O. */
final class PlanPropertiesCodec {
 private PlanPropertiesCodec(){}
    static long completionAt(Properties p,String prefix) {
        try { return Math.max(0,Long.parseLong(p.getProperty(prefix+"completedAtEpochMs","0"))); }
        catch(RuntimeException invalid) { return 0; }
    }

    static void writePlanSnapshot(Properties p, String prefix, PlanItem item) {
        p.setProperty(prefix + "name", item.name);
        p.setProperty(prefix + "plannedSeconds", String.valueOf(item.plannedSeconds));
        p.setProperty(prefix + "planned", String.valueOf(Math.max(1, (item.plannedSeconds + 59) / 60)));
        p.setProperty(prefix + "kind", item.kind.name());
        p.setProperty(prefix + "rule", item.recurringRule.name());
        p.setProperty(prefix + "weekdays", joinIntSet(item.weekdays));
        p.setProperty(prefix + "dates", joinDateSet(item.selectedDates));
        p.setProperty(prefix + "createdDate", item.createdDate.toString());p.setProperty(prefix+"cloudEnd",item.cloudEnd==null?"":item.cloudEnd.toString());
        if(prefix.startsWith("plan.")){
            p.setProperty(prefix+"archivedOn",item.archivedOn==null?"":item.archivedOn.toString());
            p.setProperty(prefix+"pinned",Boolean.toString(item.pinned));p.setProperty(prefix+"template",Boolean.toString(item.template));
        }
        p.setProperty(prefix + "temporaryExtraSeconds", String.valueOf(Math.max(0, item.temporaryExtraSeconds)));
        if(prefix.startsWith("snapshot.") && item.completedAtEpochMs>0) {
            p.setProperty(prefix+"completedAtEpochMs",""+item.completedAtEpochMs);
            p.setProperty(prefix+"completedZoneId",item.completedZoneId);
        }
    }

    static int readPlannedSeconds(Properties p, String prefix, int fallback) {
        String exact = p.getProperty(prefix + "plannedSeconds");
        if (exact != null) return parseInt(exact, fallback);
        int legacyMinutes = parseInt(p.getProperty(prefix + "planned"), -1);
        if (legacyMinutes >= 0) {
            long seconds = legacyMinutes * 60L;
            return seconds > Integer.MAX_VALUE ? fallback : (int) seconds;
        }
        return fallback;
    }

    static int readRemainingSeconds(Properties p, String id, int fallback) {
        String exact = p.getProperty("remainSeconds." + id);
        if (exact != null) return parseInt(exact, fallback);
        int legacyMinutes = parseInt(p.getProperty("remain." + id), -1);
        if (legacyMinutes >= 0) {
            long seconds = legacyMinutes * 60L;
            return seconds > Integer.MAX_VALUE ? fallback : (int) seconds;
        }
        return fallback;
    }

    static void writeRemainingSeconds(Properties p, String id, int seconds) {
        int safe = Math.max(0, seconds);
        p.setProperty("remainSeconds." + id, String.valueOf(safe));
        // Preserve a coarse legacy value for older EyeRelax builds while v2 uses the exact seconds key.
        p.setProperty("remain." + id, String.valueOf((safe + 59) / 60));
    }

    static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    static LocalDate parseDate(String s, LocalDate fallback) {
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    static Set<Integer> parseWeekdays(String s) {
        Set<Integer> out = new LinkedHashSet<>();
        if (s == null || s.trim().isEmpty()) return out;
        for (String part : s.split(",")) {
            try {
                int v = Integer.parseInt(part.trim());
                if (v >= 1 && v <= 7) out.add(v);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    static Set<LocalDate> parseDates(String s) {
        Set<LocalDate> out = new LinkedHashSet<>();
        if (s == null || s.trim().isEmpty()) return out;
        for (String part : s.split(",")) {
            try {
                out.add(LocalDate.parse(part.trim()));
            } catch (DateTimeParseException ignored) {
            }
        }
        return out;
    }

    static String joinStringSet(Set<String> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String value : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(value);
        }
        return sb.toString();
    }

    static String joinIntSet(Set<Integer> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer i : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(i);
        }
        return sb.toString();
    }

    static String joinDateSet(Set<LocalDate> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (LocalDate d : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(d);
        }
        return sb.toString();
    }

    static <E extends Enum<E>> E parseEnum(String raw, E fallback, Class<E> cls) {
        try {
            return Enum.valueOf(cls, raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    static PlanItem readSnapshot(Properties p, String prefix, String id,LocalDate fallbackDate) {
        String name = p.getProperty(prefix + "name", "").trim();
        int planned = readPlannedSeconds(p, prefix, 0);
        if (name.isEmpty() || planned <= 0) return null;

        PlanItem item = new PlanItem(id, name, planned, planned);
        item.kind = parseEnum(p.getProperty(prefix + "kind", PlanItem.PlanKind.TODAY_ONLY.name()),
                PlanItem.PlanKind.TODAY_ONLY, PlanItem.PlanKind.class);
        item.recurringRule = parseEnum(p.getProperty(prefix + "rule", PlanItem.RecurringRule.EVERY_DAY.name()),
                PlanItem.RecurringRule.EVERY_DAY, PlanItem.RecurringRule.class);
        item.weekdays = parseWeekdays(p.getProperty(prefix + "weekdays", ""));
        item.selectedDates = parseDates(p.getProperty(prefix + "dates", ""));
        item.createdDate = parseDate(p.getProperty(prefix + "createdDate", ""), fallbackDate);item.cloudEnd=parseDate(p.getProperty(prefix+"cloudEnd",""),null);
        if(prefix.startsWith("plan.")){
            item.archivedOn=parseDate(p.getProperty(prefix+"archivedOn",""),null);
            item.pinned=Boolean.parseBoolean(p.getProperty(prefix+"pinned","false"));item.template=Boolean.parseBoolean(p.getProperty(prefix+"template","false"));
        }
        item.temporaryExtraSeconds = Math.max(0, parseInt(p.getProperty(prefix + "temporaryExtraSeconds"), 0));
        item.completedAtEpochMs=completionAt(p,prefix);
        item.completedZoneId=p.getProperty(prefix+"completedZoneId","");
        return item;
    }
}
