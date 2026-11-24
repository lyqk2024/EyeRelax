package com.eye.core;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Independent of plans: measured unlocked time, active foreground apps and labelled history. */
public final class ComputerActivity implements AutoCloseable {
    public record Sample(boolean unlocked, boolean active, String executable) {}
    public record Range(long from, long to) {}
    public record Day(long measured, long active, long estimated, long[] hours, Map<String,Long> apps,int boundarySeconds,boolean mixedBoundaries) {}
    private final Path file;
    private final ZoneId zone;
    private final java.util.function.IntSupplier boundary;
    private final Map<LocalDate,Set<Integer>> periods=new HashMap<>();
    private final Set<LocalDate> legacyDays=new HashSet<>();
    private int sampleBoundary,runtimeBoundary;
    private final Properties data;
    private final ApplicationUsageIndex applicationIndex;
    private final StatisticsStore store;
    private Instant previous;
    private long previousNanos, lastSaved;
    private Instant runtimePrevious;
    private long runtimeNanos;
    private Set<String> running=Set.of();
    private Sample last = new Sample(false,false,"");
    private final List<Range> coverage = new ArrayList<>();
    private final List<Range> history = new ArrayList<>();
    private final Map<String,String> catalog = new TreeMap<>();
    private boolean sessionAvailable = true;
    private String warning = "";

    public ComputerActivity(Path file, ZoneId zone) {this(file,zone,()->0);}
    public ComputerActivity(Path file,ZoneId zone,java.util.function.IntSupplier boundary) {
        this.boundary=boundary;
        this.file=file;this.zone=zone;store=new StatisticsStore(file);
        Properties loaded;
        try {loaded=AtomicProperties.read(file);}
        catch(Exception ex){
            try {loaded=AtomicProperties.read(file.resolveSibling(file.getFileName()+".bak"));warning="统计主文件无法读取，已尝试恢复备份。";}
            catch(Exception backup){loaded=new Properties();warning="历史统计无法读取；新记录将另行保存。";}
        }
        data=loaded;applicationIndex=new ApplicationUsageIndex(data);
        for(String key:data.stringPropertyNames()) {
            try{if(key.startsWith("study.")){int end=key.indexOf('.',6);int seconds=Integer.parseInt(key.substring(6,end));LocalDate date=LocalDate.parse(key.substring(end+1,end+11));periods.computeIfAbsent(date,d->new TreeSet<>()).add(seconds);}
                else if(key.length()>11&&key.charAt(10)=='.')legacyDays.add(LocalDate.parse(key.substring(0,10)));
            }catch(RuntimeException ignored){}
            try {
                if(key.startsWith("coverage."))coverage.add(new Range(Long.parseLong(key.substring(9)),Long.parseLong(data.getProperty(key))));
                if(key.startsWith("history."))history.add(new Range(Long.parseLong(key.substring(8)),Long.parseLong(data.getProperty(key))));
                if(key.startsWith("catalog."))catalog.put(decode(key.substring(8)),data.getProperty(key));
            }catch(RuntimeException ignored){}
        }
        coverage.sort(Comparator.comparingLong(Range::from));history.sort(Comparator.comparingLong(Range::from));
    }
    public synchronized void sample(Instant now, long nanos, Sample next) {
        if(previous!=null) {
            long wall=Duration.between(previous,now).toMillis(), elapsed=(nanos-previousNanos)/1_000_000;
            // Missed heartbeats, sleep and clock jumps never become invented usage.
            if(wall>0&&elapsed>0&&wall<=10_000&&Math.abs(wall-elapsed)<=2_000&&last.unlocked&&sessionAvailable) {
                long end=now.toEpochMilli(),start=end-Math.min(wall,elapsed);
                addCoverage(start,end);
                String exe=last.executable==null?"":normalize(last.executable);
                split(start,end,sampleBoundary,(day,hour,ms)->{
                    increment(day+".measured",ms);increment(day+".hour."+hour,ms);
                    if(last.active){increment(day+".active",ms);if(!exe.isBlank())increment(day+".app."+encode(exe),ms);}
                });
            }
        }
        previous=now;previousNanos=nanos;last=next;sampleBoundary=currentBoundary();sessionAvailable=next.unlocked;
        if(now.toEpochMilli()-lastSaved>=30_000){persist();lastSaved=now.toEpochMilli();}
    }
    /** Each executable is counted once per interval, even with several running processes. */
    public synchronized void sampleRunning(Instant now,long nanos,Set<String> executables){
        if(runtimePrevious!=null){
            long wall=Duration.between(runtimePrevious,now).toMillis(),elapsed=(nanos-runtimeNanos)/1_000_000;
            if(wall>0&&elapsed>0&&wall<=10_000&&Math.abs(wall-elapsed)<=2_000){
                long end=now.toEpochMilli(),start=end-Math.min(wall,elapsed);
                split(start,end,runtimeBoundary,(day,hour,ms)->{String prefix=day+".runtime.";for(String exe:running)increment(prefix+encode(exe),ms);});
            }
        }
        Set<String> normalized=new HashSet<>();for(String exe:executables)if(exe!=null&&!exe.isBlank())normalized.add(normalize(exe));
        running=Set.copyOf(normalized);runtimePrevious=now;runtimeNanos=nanos;runtimeBoundary=currentBoundary();
    }
    public synchronized Map<String,Long> applicationRuntime(LocalDate date){
        return applicationIndex.runtime(date);
    }
    public synchronized String preference(String name,String fallback){return data.getProperty("ui."+name,fallback);}
    public synchronized void setPreferences(Map<String,String> values){values.forEach((name,value)->data.setProperty("ui."+name,value));persist();}
    public synchronized void setPreference(String name,String value){data.setProperty("ui."+name,value);persist();}
    public synchronized void sessionAvailable(boolean available) { sessionAvailable=available;previous=null; }
    public synchronized void addApplication(Path exe, String name) {
        String path=normalize(exe.toAbsolutePath().normalize().toString());
        catalog.put(path,name==null||name.isBlank()?exe.getFileName().toString():name.trim());persist();
    }
    public synchronized void removeApplication(String exe) {catalog.remove(normalize(exe));persist();}
    public synchronized Map<String,String> applications() {return new TreeMap<>(catalog);}
    public synchronized Map<String,Long> applicationUsageTotals() {
        return applicationIndex.totals();
    }
    public synchronized String displayName(String exe) {return catalog.getOrDefault(exe,Path.of(exe).getFileName().toString());}
    public synchronized String warning() {String saveWarning=store.warning();return saveWarning.isBlank()?warning:saveWarning;}
    public synchronized void importHistory(List<Range> ranges) {
        // Replace the queried history range; repeated import is idempotent.
        history.clear();
        for(Range r:ranges)if(r.to>r.from)history.add(r);
        history.sort(Comparator.comparingLong(Range::from));
        List<Range> merged=new ArrayList<>();
        for(Range r:history){if(!merged.isEmpty()&&r.from<=merged.get(merged.size()-1).to){Range p=merged.remove(merged.size()-1);merged.add(new Range(p.from,Math.max(p.to,r.to)));}else merged.add(r);}
        history.clear();history.addAll(merged);persist();
    }
    public int currentBoundary(){return StudyDay.sanitizeBoundarySeconds(boundary.getAsInt());}
    public LocalDate today(){return dateAt(Instant.now());}
    public LocalDate dateAt(Instant instant){return StudyDay.dateAt(instant.toEpochMilli(),zone,currentBoundary());}
    public synchronized boolean hasLegacyDay(LocalDate date){return legacyDays.contains(date);}
    public String hourLabel(LocalDate date,int seconds,int index){long start=StudyDay.boundaryAtStartOf(date,zone,seconds);return Instant.ofEpochMilli(start+index*3_600_000L).atZone(zone).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern(seconds%60==0?"HH:mm":"HH:mm:ss"));}
    public synchronized Day day(LocalDate date) {
        Set<Integer> recorded=new TreeSet<>(periods.getOrDefault(date,Set.of()));if(legacyDays.contains(date))recorded.add(0);
        int displayBoundary=recorded.size()==1?recorded.iterator().next():currentBoundary();boolean mixed=recorded.size()>1;
        long start=StudyDay.boundaryAtStartOf(date,zone,displayBoundary),end=StudyDay.boundaryAtStartOf(date.plusDays(1),zone,displayBoundary);
        int count=(int)Math.max(1,(end-start+3_599_999L)/3_600_000L);if(legacyDays.contains(date)||mixed)count=Math.max(24,count);
        for(int seconds:recorded){long span=StudyDay.boundaryAtStartOf(date.plusDays(1),zone,seconds)-StudyDay.boundaryAtStartOf(date,zone,seconds);count=Math.max(count,(int)((span+3_599_999L)/3_600_000L));}
        long[] hours=new long[count];Map<String,Long> apps=applicationIndex.foreground(date);long measured=value(date+".measured"),active=value(date+".active");
        for(int i=0;i<Math.min(24,hours.length);i++)hours[i]=value(date+".hour."+i);
        for(int seconds:periods.getOrDefault(date,Set.of())){String key="study."+seconds+"."+date;measured+=value(key+".measured");active+=value(key+".active");for(int i=0;i<hours.length;i++)hours[i]+=value(key+".hour."+i);}

        long estimated=0;
        for(Range r:history){long a=Math.max(start,r.from),b=Math.min(end,r.to);if(b>a){long ms=b-a;for(Range c:coverage)ms-=Math.max(0,Math.min(b,c.to)-Math.max(a,c.from));estimated+=Math.max(0,ms);}}
        return new Day(measured,active,estimated,hours,Collections.unmodifiableMap(apps),displayBoundary,mixed);
    }
    public synchronized void persist() {
        data.keySet().removeIf(k->k.toString().startsWith("coverage.")||k.toString().startsWith("history.")||k.toString().startsWith("catalog."));
        for(Range r:coverage)data.setProperty("coverage."+r.from,Long.toString(r.to));
        for(Range r:history)data.setProperty("history."+r.from,Long.toString(r.to));
        catalog.forEach((k,v)->data.setProperty("catalog."+encode(k),v));
        store.save(data);
    }
    private void addCoverage(long from,long to){
        if(!coverage.isEmpty()){Range r=coverage.get(coverage.size()-1);if(from<=r.to+2){coverage.set(coverage.size()-1,new Range(r.from,Math.max(to,r.to)));return;}}
        coverage.add(new Range(from,to));
    }
    private interface Slice {void accept(String day,int hour,long ms);}
    private void split(long from,long to,int seconds,Slice consumer){
        while(from<to){LocalDate date=StudyDay.dateAt(from,zone,seconds);long begin=StudyDay.boundaryAtStartOf(date,zone,seconds);
            int hour=(int)((from-begin)/3_600_000L);long next=Math.min(begin+(hour+1L)*3_600_000L,StudyDay.nextBoundaryAfter(from,zone,seconds));
            long end=Math.min(next,to);String prefix="study."+seconds+"."+date;
            periods.computeIfAbsent(date,d->new TreeSet<>()).add(seconds);consumer.accept(prefix,hour,end-from);from=end;
        }
    }
    private long value(String key){try{return Math.max(0,Long.parseLong(data.getProperty(key,"0")));}catch(Exception ex){return 0;}}
    private void increment(String key,long ms){data.setProperty(key,Long.toString(value(key)+ms));applicationIndex.add(key,ms);}
    private static String normalize(String s){return s.toLowerCase(Locale.ROOT);}
    private static String encode(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private static String decode(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
    @Override public void close(){persist();store.close();}
}
