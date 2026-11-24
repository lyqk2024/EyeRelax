package com.eye.stats;

import com.eye.core.*;
import com.eye.plan.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure analytics over frozen daily snapshots and captured intervals. Never rewrites history. */
public final class Statistics {
    private Statistics() {}
    public static final String FREE = "@free";
    public static String taskKey(SessionStore.Interval i) { return i.taskId().isBlank()?FREE:i.taskId(); }
    public static String key(SessionStore.Interval i) { return i.isRest()?"@"+i.kind().name():taskKey(i); }
    public record Task(String id, String name, long actualMs, long targetMs, long extraMs,
                       long completedAtEpochMs, String completedZoneId) {
        public Task(String id,String name,long actualMs,long targetMs,long extraMs) {this(id,name,actualMs,targetMs,extraMs,0,"");}
        public boolean complete() { return targetMs>0 && actualMs>=targetMs; }
        public double ratio() { return targetMs>0?Math.min(1,actualMs/(double)targetMs):0; }
    }
    public record Day(LocalDate date, long focusMs, long restMs, List<Task> tasks,
                      List<SessionStore.Interval> intervals) {
        public long recordedMs(boolean rest) { return intervals.stream().filter(i->i.isRest()==rest).mapToLong(SessionStore.Interval::durationMs).sum(); }
        public long unplacedMs(boolean rest) { return Math.max(0,(rest?restMs:focusMs)-recordedMs(rest)); }
        public long plannedMs() { return tasks.stream().mapToLong(Task::targetMs).sum(); }
        public long earnedMs() { return tasks.stream().filter(t->t.targetMs>0).mapToLong(t->Math.min(t.actualMs,t.targetMs)).sum(); }
        public int targetCount() { return (int)tasks.stream().filter(t->t.targetMs>0).count(); }
        public int completeCount() { return (int)tasks.stream().filter(Task::complete).count(); }
        public double ratio() { return plannedMs()>0?earnedMs()/(double)plannedMs():0; }
        public Map<String,Long> restKinds() {
            Map<String,Long> values=new LinkedHashMap<>();
            for(SessionStore.Interval i:intervals)if(i.isRest())values.merge(i.displayName(),i.durationMs(),Long::sum);
            if(unplacedMs(true)>0) values.put(com.eye.i18n.I18n.text("历史休息"),unplacedMs(true));return values;
        }
    }
    public record Snapshot(LocalDate from, LocalDate to, Map<LocalDate,Day> days, Map<String,String> taskNames,Set<String> analysisEligibleTaskIds) {
        public Snapshot(LocalDate from,LocalDate to,Map<LocalDate,Day> days,Map<String,String> taskNames){this(from,to,days,taskNames,Set.of());}
        public Snapshot {analysisEligibleTaskIds=Set.copyOf(analysisEligibleTaskIds);}
        public Day day(LocalDate date) { return days.getOrDefault(date,new Day(date,0,0,List.of(),List.of())); }
    }
    public static Map<String,String> taskNamesInRange(Snapshot snapshot,LocalDate from,LocalDate to){
        Map<String,String> names=new LinkedHashMap<>();
        for(Day day:snapshot.days().values())if(!day.date().isBefore(from)&&!day.date().isAfter(to))
            for(Task task:day.tasks())if(task.actualMs()>0||task.complete())names.put(task.id(),task.name());
        return names;
    }
    /** Definition semantics, not the number of recorded days, determine analysis eligibility. */
    public static boolean isAnalysisTask(PlanItem plan){return plan.kind==PlanItem.PlanKind.RECURRING&&!plan.template;}
    public record Window(long start, long end, ZoneId zone, boolean mixedRules) {}
    public record Bucket(String label, long start, long end, Map<String,Long> values, String endLabel) {
        public Bucket(String label,long start,long end,Map<String,Long> values){this(label,start,end,values,"");}
        public long total() { return values.values().stream().mapToLong(Long::longValue).sum(); }
    }
    public record Summary(long focusMs, long restMs, int calendarDays, int activeDays, long minActiveMs,
                          long maxMs, int longestStreak, int completedTasks, int plannedTasks,
                          long earnedMs, long targetMs, long extraMs, int restSessions, long recordedRestMs) {
        public long averageMs() { return calendarDays<=0?0:focusMs/calendarDays; }
        public double completionRatio() { return targetMs<=0?0:earnedMs/(double)targetMs; }
    }

    public static Snapshot load(PlanManager manager, Config config, LocalDate from, LocalDate until) {
        List<PlanManager.HistoricalRecord> rows=new ArrayList<>(manager.listPastRecords(from,until,null));
        LocalDate today=manager.getToday();
        if(!today.isBefore(from)&&!today.isAfter(until))for(PlanItem p:manager.listActiveToday())
            rows.add(new PlanManager.HistoricalRecord(today,p,Math.max(0,p.plannedSeconds-p.remainingSeconds),p.remainingSeconds));
        ActivityStore store=new ActivityStore(config.dayBoundarySeconds);
        return build(from,until,rows,store,store.sessions().intervals(from,until));
    }
    public static Snapshot build(LocalDate from, LocalDate until,List<PlanManager.HistoricalRecord> rows,
                                 ActivityStore store,List<SessionStore.Interval> intervals) {
        Map<LocalDate,List<PlanManager.HistoricalRecord>> history=new HashMap<>();
        Map<LocalDate,List<SessionStore.Interval>> captured=new HashMap<>();
        Set<String> analysisEligibleIds=new HashSet<>();
        for(var row:rows){history.computeIfAbsent(row.date,k->new ArrayList<>()).add(row);if(isAnalysisTask(row.plan))analysisEligibleIds.add(row.plan.id);}
        // Persistent interval IDs, not timestamp or task title, determine identity.
        Set<String> seen=new HashSet<>();
        for(var i:intervals)if(seen.add(i.id()))captured.computeIfAbsent(i.studyDate(),k->new ArrayList<>()).add(i);
        Map<LocalDate,Day> days=new LinkedHashMap<>();Map<String,String> names=new LinkedHashMap<>();
        for(LocalDate d=from;!d.isAfter(until);d=d.plusDays(1)){
            List<SessionStore.Interval> recorded=captured.getOrDefault(d,List.of());
            Map<String,Task> taskMap=new LinkedHashMap<>();
            for(var h:history.getOrDefault(d,List.of())){
                String id=h.plan.id;names.put(id,h.plan.name);
                taskMap.put(id,new Task(id,h.plan.name,h.completedSeconds*1000L,h.getPlannedSeconds()*1000L,h.plan.temporaryExtraSeconds*1000L,h.plan.completedAtEpochMs,h.plan.completedZoneId));
            }
            Map<String,Long> exact=new LinkedHashMap<>();Map<String,String> exactNames=new HashMap<>();
            for(var i:recorded)if(!i.isRest()) {String id=taskKey(i);exact.merge(id,i.durationMs(),Long::sum);exactNames.put(id,i.displayName());names.putIfAbsent(id,i.displayName());}
            for(var e:exact.entrySet()){
                Task old=taskMap.get(e.getKey());
                taskMap.put(e.getKey(),new Task(e.getKey(),old==null?exactNames.get(e.getKey()):old.name,
                        Math.max(e.getValue()+store.sessions().legacyTaskMillis(d,e.getKey()),old==null?0:old.actualMs),old==null?0:old.targetMs,old==null?0:old.extraMs,old==null?0:old.completedAtEpochMs,old==null?"":old.completedZoneId));
            }
            long taskSum=taskMap.values().stream().mapToLong(Task::actualMs).sum();
            long focus=Math.max(store.millis(d,false),taskSum);
            long rest=Math.max(store.millis(d,true),recorded.stream().filter(SessionStore.Interval::isRest).mapToLong(SessionStore.Interval::durationMs).sum());
            // An old aggregate can include unassigned focus; retain it, without inventing a timestamp.
            if(focus>taskSum){Task old=taskMap.get(FREE);taskMap.put(FREE,new Task(FREE,com.eye.i18n.I18n.text("自由专注"),(old==null?0:old.actualMs)+focus-taskSum,0,0));names.put(FREE,com.eye.i18n.I18n.text("自由专注"));}
            ArrayList<Task> tasks=new ArrayList<>(taskMap.values());tasks.sort(Comparator.comparingLong(Task::actualMs).reversed().thenComparing(Task::name));
            days.put(d,new Day(d,focus,rest,List.copyOf(tasks),List.copyOf(recorded)));
        }
        return new Snapshot(from,until,Collections.unmodifiableMap(days),Collections.unmodifiableMap(names),analysisEligibleIds);
    }

    /** A frozen day can have multiple policies after a mid-day setting change. The union of
     * captured windows is shown, labelled as mixed; no old interval is moved or overwritten. */
    public static Window window(Day day, int fallbackBoundary, ZoneId fallbackZone) {
        long start=Long.MAX_VALUE,end=Long.MIN_VALUE;ZoneId zone=fallbackZone;Set<String> policies=new HashSet<>();
        for(var i:day.intervals){ZoneId z=ZoneId.of(i.zoneId());zone=z;
            long a=StudyDay.boundaryAtStartOf(i.studyDate(),z,i.boundarySeconds());
            long b=StudyDay.boundaryAtStartOf(i.studyDate().plusDays(1),z,i.boundarySeconds());
            start=Math.min(start,Math.min(a,i.startAt()));end=Math.max(end,Math.max(b,i.endAt()));
            policies.add(i.boundarySeconds()+"|"+i.zoneId());
        }
        if(start==Long.MAX_VALUE){start=StudyDay.boundaryAtStartOf(day.date,fallbackZone,fallbackBoundary);end=StudyDay.boundaryAtStartOf(day.date.plusDays(1),fallbackZone,fallbackBoundary);}
        return new Window(start,end,zone,policies.size()>1);
    }
    public static List<Bucket> hourly(Day day,Window window,boolean rest,String taskId) {
        ArrayList<Bucket> result=new ArrayList<>();
        for(long a=window.start;a<window.end;){long b=Math.min(window.end,a+3600000L);Map<String,Long> values=new LinkedHashMap<>();
            for(var i:day.intervals){if(i.isRest()!=rest||(!rest&&taskId!=null&&!taskId.equals(taskKey(i))))continue;
                long ms=Math.max(0,Math.min(b,i.endAt())-Math.max(a,i.startAt()));if(ms>0)values.merge(key(i),ms,Long::sum);}
            ZonedDateTime t=Instant.ofEpochMilli(a).atZone(window.zone);
            String label=t.format(DateTimeFormatter.ofPattern("HH:mm"));
            if(t.toLocalDate().isAfter(day.date))label=com.eye.i18n.I18n.text("次日 ")+label;
            ZonedDateTime edge=Instant.ofEpochMilli(b).atZone(window.zone);
            String endLabel=(edge.toLocalDate().isAfter(day.date)?com.eye.i18n.I18n.text("次日 "):"")+edge.format(DateTimeFormatter.ofPattern("HH:mm"));
            result.add(new Bucket(label,a,b,values,endLabel));a=b;
        }return List.copyOf(result);
    }
    public static LocalDate[] nearby(LocalDate selected,LocalDate today){
        LocalDate end=selected.plusDays(3);if(end.isAfter(today))end=today;return new LocalDate[]{end.minusDays(6),end};
    }
    public static Summary summarize(Snapshot data,LocalDate from,LocalDate until){
        long focus=0,rest=0,min=Long.MAX_VALUE,max=0,earned=0,target=0,extra=0,exactRest=0;
        int active=0,longest=0,streak=0,complete=0,plans=0;Set<String> restRuns=new HashSet<>();
        for(LocalDate d=from;!d.isAfter(until);d=d.plusDays(1)){Day day=data.day(d);focus+=day.focusMs;rest+=day.restMs;earned+=day.earnedMs();target+=day.plannedMs();
            complete+=day.completeCount();plans+=day.targetCount();extra+=day.tasks.stream().mapToLong(Task::extraMs).sum();
            max=Math.max(max,day.focusMs);if(day.focusMs>0){active++;min=Math.min(min,day.focusMs);longest=Math.max(longest,++streak);}else streak=0;
            for(var i:day.intervals)if(i.isRest()){restRuns.add(i.sessionId());exactRest+=i.durationMs();}
        }
        return new Summary(focus,rest,(int)ChronoUnit.DAYS.between(from,until)+1,active,min==Long.MAX_VALUE?0:min,max,longest,complete,plans,earned,target,extra,restRuns.size(),exactRest);
    }
    /** Short ranges use days, medium use seven-day buckets, long ranges use calendar months. */
    public static List<Bucket> dailySeries(Snapshot data,LocalDate from,LocalDate until,boolean rest,String taskId){
        ArrayList<Bucket> result=new ArrayList<>();long count=ChronoUnit.DAYS.between(from,until)+1;
        LocalDate cursor=from;
        while(!cursor.isAfter(until)){
            LocalDate end=count<=45?cursor:count<=180?cursor.plusDays(6):cursor.withDayOfMonth(cursor.lengthOfMonth());
            if(end.isAfter(until))end=until;
            Map<String,Long> values=new LinkedHashMap<>();
            for(LocalDate d=cursor;!d.isAfter(end);d=d.plusDays(1)){
                Day day=data.day(d);
                if(rest)for(var e:day.restKinds().entrySet())values.merge(e.getKey(),e.getValue(),Long::sum);
                else for(var t:day.tasks)if(taskId==null||taskId.equals(t.id))values.merge(t.id,t.actualMs,Long::sum);
            }
            String label=count>180?cursor.format(DateTimeFormatter.ofPattern("yy/MM")):cursor.format(DateTimeFormatter.ofPattern("M/d"));
            result.add(new Bucket(label,cursor.toEpochDay(),end.toEpochDay(),values));cursor=end.plusDays(1);
        }return result;
    }
    public static List<Bucket> completionSeries(Snapshot data,LocalDate from,LocalDate until,String taskId){
        List<Bucket> result=new ArrayList<>();
        for(Bucket base:dailySeries(data,from,until,false,taskId)){
            long earned=0,target=0;
            for(LocalDate d=LocalDate.ofEpochDay(base.start);!d.isAfter(LocalDate.ofEpochDay(base.end));d=d.plusDays(1))
                for(Task t:data.day(d).tasks)if(taskId==null||taskId.equals(t.id)){target+=t.targetMs;earned+=Math.min(t.actualMs,t.targetMs);}
            result.add(new Bucket(base.label,base.start,base.end,Map.of("@completion",target>0?Math.round(10000.0*earned/target):0L)));
        }return result;
    }
    public static String completionTime(Task task,LocalDate day){
        if(task.completedAtEpochMs<=0 || task.completedZoneId==null || task.completedZoneId.isBlank())return "";
        try{ZonedDateTime local=Instant.ofEpochMilli(task.completedAtEpochMs).atZone(ZoneId.of(task.completedZoneId));
            return (local.toLocalDate().isAfter(day)?com.eye.i18n.I18n.text("次日 "):"")+local.format(DateTimeFormatter.ofPattern("HH:mm:ss"));}
        catch(RuntimeException invalid){return "";}
    }
    public static List<Bucket> hoursOfTask(Snapshot data,LocalDate from,LocalDate until,String taskId){
        long[] total=new long[24];
        for(LocalDate d=from;!d.isAfter(until);d=d.plusDays(1))for(var i:data.day(d).intervals){
            if(i.isRest()||!taskKey(i).equals(taskId))continue;
            long cursor=i.startAt();ZoneId zone=ZoneId.of(i.zoneId());
            while(cursor<i.endAt()){
                ZonedDateTime t=Instant.ofEpochMilli(cursor).atZone(zone);
                long next=t.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli();
                long end=Math.min(i.endAt(),Math.max(cursor+1,next));total[t.getHour()]+=end-cursor;cursor=end;
            }
        }
        List<Bucket> result=new ArrayList<>();for(int h=0;h<24;h++)result.add(new Bucket(String.format("%02d:00",h),h,h+1,Map.of(taskId,total[h])));return result;
    }
    public static String duration(long millis){return ActivityPanel.duration(Math.max(0,millis)/1000);}
    public static String escape(String text){return text==null?"":text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
