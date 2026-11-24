package com.eye.calendar;

import com.eye.i18n.I18n;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Offline mainland-China holiday policy and lunar/solar-term annotations. */
public final class ChineseCalendar {
    private ChineseCalendar(){}
    private record Lunar(String month,String day,String term){}
    private static final Map<Integer,Map<LocalDate,Lunar>> YEARS=new ConcurrentHashMap<>();
    private static final Set<LocalDate> HOLIDAYS=new HashSet<>(), MAKEUP=new HashSet<>();
    static {
        holidays(2025,"01-01","01-01");holidays(2025,"01-28","02-04");holidays(2025,"04-04","04-06");
        holidays(2025,"05-01","05-05");holidays(2025,"05-31","06-02");holidays(2025,"10-01","10-08");
        makeup(2025,"01-26","02-08","04-27","09-28","10-11");
        holidays(2026,"01-01","01-03");holidays(2026,"02-15","02-23");holidays(2026,"04-04","04-06");
        holidays(2026,"05-01","05-05");holidays(2026,"06-19","06-21");holidays(2026,"09-25","09-27");holidays(2026,"10-01","10-07");
        makeup(2026,"01-04","02-14","02-28","05-09","09-20","10-10");
    }
    private static void holidays(int year,String first,String last){for(LocalDate d=LocalDate.parse(year+"-"+first),end=LocalDate.parse(year+"-"+last);!d.isAfter(end);d=d.plusDays(1))HOLIDAYS.add(d);}
    private static void makeup(int year,String...dates){for(String date:dates)MAKEUP.add(LocalDate.parse(year+"-"+date));}
    public static boolean hasHolidaySchedule(int year){return year==2025||year==2026;}
    public static boolean isHoliday(LocalDate date){return HOLIDAYS.contains(date);}
    public static boolean isMakeupWorkday(LocalDate date){return MAKEUP.contains(date);}
    public static boolean isWorkday(LocalDate date){return isMakeupWorkday(date)||date.getDayOfWeek().getValue()<=5&&!isHoliday(date);}
    public static String policyNote(){return I18n.text("中国大陆调休已收录 2025–2026 年；其它年份工作日按周一至周五，假期不作推测。");}
    private static Map<LocalDate,Lunar> load(int year){
        Map<LocalDate,Lunar> result=new HashMap<>();
        InputStream stream=ChineseCalendar.class.getResourceAsStream("lunar-"+year+".txt");
        if(stream==null)return Map.of();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){if(line.startsWith("#")||line.isBlank())continue;String[] parts=line.split("\\|",-1);result.put(LocalDate.parse(parts[0]),new Lunar(parts[1],parts[2],parts[3]));}
        }catch(IOException ex){throw new UncheckedIOException(ex);}
        return Map.copyOf(result);
    }
    private static Lunar lunar(LocalDate date){return YEARS.computeIfAbsent(date.getYear(),ChineseCalendar::load).get(date);}
    public static String lunarDate(LocalDate date){Lunar l=lunar(date);return l==null?"":l.month+l.day;}
    public static String solarTerm(LocalDate date){Lunar l=lunar(date);return l==null?"":I18n.text(l.term);}
    public static String festival(LocalDate date){
        String fixed=switch(date.getMonthValue()*100+date.getDayOfMonth()){
            case 101 -> "元旦";case 308 -> "妇女节";case 501 -> "劳动节";case 504 -> "青年节";
            case 601 -> "儿童节";case 910 -> "教师节";case 1001 -> "国庆节";case 1225 -> "圣诞节";default -> "";
        };
        if(!fixed.isEmpty())return I18n.text(fixed);
        Lunar l=lunar(date);if(l==null)return "";
        if(!l.month.startsWith("闰")){
            String key=l.month+l.day;
            String value=switch(key){case "正月初一"->"春节";case "正月十五"->"元宵节";case "五月初五"->"端午节";
                case "七月初七"->"七夕";case "八月十五"->"中秋节";case "九月初九"->"重阳节";case "十二月初八"->"腊八节";default->"";};
            if(!value.isEmpty())return I18n.text(value);
            Lunar next=lunar(date.plusDays(1));if(next!=null&&next.month.equals("正月")&&next.day.equals("初一"))return I18n.text("除夕");
        }
        return "";
    }
    /** Festival > solar term > lunar date; holiday/workday badges remain separate. */
    public static String annotation(LocalDate date){
        String festival=festival(date);if(!festival.isEmpty())return festival;
        String term=solarTerm(date);if(!term.isEmpty())return term;
        Lunar l=lunar(date);if(l==null||I18n.isEnglish())return "";
        return l.day.equals("初一")?l.month:l.day;
    }
    public static String badge(LocalDate date){return isMakeupWorkday(date)?I18n.text("班"):isHoliday(date)?I18n.text("休"):"";}
    public static String accessibleDate(LocalDate date){return date+" "+lunarDate(date)+" "+festival(date)+" "+solarTerm(date)+" "+badge(date);}
}
