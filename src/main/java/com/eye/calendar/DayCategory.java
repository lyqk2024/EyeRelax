package com.eye.calendar;

import java.time.LocalDate;
import com.eye.i18n.I18n;

/** Explicit scheduling semantics, independent of festival display labels. */
public enum DayCategory {
    EVERY_DAY("每天"), WORKDAY("工作日（含调休）"), NON_WORKDAY("非工作日"),
    HOLIDAY("法定假期"), NORMAL_WORKDAY("非假期工作日（周一至周五）");
    private final String label;
    DayCategory(String label){this.label=label;}
    @Override public String toString(){return I18n.text(label);}
    public boolean matches(LocalDate date){return switch(this){
        case EVERY_DAY -> true;
        case WORKDAY -> ChineseCalendar.isWorkday(date);
        case NON_WORKDAY -> !ChineseCalendar.isWorkday(date);
        case HOLIDAY -> ChineseCalendar.isHoliday(date);
        case NORMAL_WORKDAY -> date.getDayOfWeek().getValue()<=5&&!ChineseCalendar.isHoliday(date);
    };}
}
