package com.eye.plan;
import java.time.*;
import java.util.*;
/** Calendar recurrence policy; uses the manager-owned holiday set. */
final class PlanSchedule {
 private final Set<LocalDate> customHolidays;
 PlanSchedule(Set<LocalDate> holidays){customHolidays=holidays;}
    boolean isActiveOn(PlanItem p, LocalDate day) {
        if(p.template||p.archivedOn!=null)return false;
        switch (p.kind) {
            case TODAY_ONLY:
                return day.equals(p.createdDate);
            case MULTI_DAY:
                return p.selectedDates.contains(day);
            case RECURRING:
                return matchRecurring(p, day);
            default:
                return false;
        }
    }

    boolean matchRecurring(PlanItem p, LocalDate day) {
        if(day.isBefore(p.createdDate)||p.cloudEnd!=null&&day.isAfter(p.cloudEnd))return false;
        DayOfWeek dw = day.getDayOfWeek();
        boolean weekend = (dw == DayOfWeek.SATURDAY || dw == DayOfWeek.SUNDAY);
        boolean holiday = weekend || customHolidays.contains(day);

        switch (p.recurringRule) {
            case WORKDAY:
                return !customHolidays.contains(day)&&com.eye.calendar.DayCategory.WORKDAY.matches(day);
            case NON_WORKDAY:
                return customHolidays.contains(day)||com.eye.calendar.DayCategory.NON_WORKDAY.matches(day);
            case PUBLIC_HOLIDAY:
                return customHolidays.contains(day)||com.eye.calendar.DayCategory.HOLIDAY.matches(day);
            case NORMAL_WORKDAY:
                return !customHolidays.contains(day)&&com.eye.calendar.DayCategory.NORMAL_WORKDAY.matches(day);
            case EVERY_DAY:
                return true;
            case WEEKDAY:
                return !weekend && !customHolidays.contains(day);
            case WEEKEND:
                return weekend;
            case HOLIDAY:
                return holiday;
            case BY_WEEKDAY:
                return p.weekdays.contains(dw.getValue());
            default:
                return false;
        }
    }

    List<LocalDate> collectOccurrences(PlanItem plan, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        if (from.isAfter(to)) return dates;

        if (plan.kind == PlanItem.PlanKind.MULTI_DAY) {
            for (LocalDate d : plan.selectedDates) {
                if ((d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to))) {
                    dates.add(d);
                }
            }
            dates.sort(Comparator.naturalOrder());
            return dates;
        }

        if (plan.kind == PlanItem.PlanKind.TODAY_ONLY) {
            if ((plan.createdDate.isEqual(from) || plan.createdDate.isAfter(from))
                    && (plan.createdDate.isEqual(to) || plan.createdDate.isBefore(to))) {
                dates.add(plan.createdDate);
            }
            return dates;
        }

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            if (matchRecurring(plan, cursor)) {
                dates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }
}
