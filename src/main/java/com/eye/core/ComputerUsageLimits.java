package com.eye.core;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Computer-session accounting, independent of focus timers and study-day boundaries. */
public final class ComputerUsageLimits {
    public enum Reason { NONE, DAILY_BUDGET, OUTSIDE_WINDOW, STORAGE_ERROR }
    public record Status(LocalDate day, long usedMillis, long remainingMillis, Reason reason,
                         ZonedDateTime nextAllowed, String error) {
        public boolean blocked() { return reason != Reason.NONE; }
    }
    private record Policy(boolean daily, long budget, boolean window, int start, int end) {
        static Policy read(Config c) {
            return new Policy(c.dailyUsageLimitEnabled, Math.max(60, c.dailyUsageLimitSeconds)*1000L,
                    c.usageWindowEnabled, c.usageWindowStartMinute, c.usageWindowEndMinute);
        }
        boolean enabled() { return daily || window; }
        boolean permits(ZonedDateTime time) {
            if (!window) return true;
            int minute = time.getHour()*60+time.getMinute();
            if (start == end) return false;
            return start < end ? minute >= start && minute < end : minute >= start || minute < end;
        }
    }
    private final Config config;
    private final Path file;
    private final Map<LocalDate, Long> days = new TreeMap<>();
    private Instant previous;
    private long previousNanos;
    private boolean active = true;
    private Policy previousPolicy;
    private String storageError = "";

    public ComputerUsageLimits(Config config, Path file, Instant now, long nanos) {
        this.config=config; this.file=file; previous=now; previousNanos=nanos;
        previousPolicy=Policy.read(config);
        try { load(file); }
        catch (IOException | IllegalArgumentException ex) {
            try { load(file.resolveSibling(file.getFileName()+".bak"));
                if (!Files.isRegularFile(file.resolveSibling(file.getFileName()+".bak"))) throw new IOException("no backup");
            } catch (IOException | IllegalArgumentException ignored) { storageError="使用记录无法读取，请检查数据目录。"; }
        }
    }
    private void load(Path path) throws IOException {
        Properties p=AtomicProperties.read(path);
        Map<LocalDate,Long> restored=new TreeMap<>();
        for(String key:p.stringPropertyNames()) if(key.startsWith("day.")) {
            long value=Long.parseLong(p.getProperty(key));
            if(value<0)throw new IllegalArgumentException("negative usage");
            restored.put(LocalDate.parse(key.substring(4)),value);
        }
        if(Files.isRegularFile(path) && !"1".equals(p.getProperty("format"))) throw new IOException("unsupported usage record");
        days.clear(); days.putAll(restored);
    }
    /** Samples only observed active time; suspend/offline gaps and clock jumps are not invented. */
    public synchronized Status sample(Instant now, long nanos, ZoneId zone) {
        long wall=Duration.between(previous,now).toMillis();
        long elapsed=(nanos-previousNanos)/1_000_000L;
        boolean changed=false;
        if(active && previousPolicy.enabled() && elapsed>0 && elapsed<=5000 && wall>0
                && Math.abs(wall-elapsed)<=1500 && storageError.isEmpty()) {
            Instant cursor=previous;
            while(cursor.isBefore(now)) {
                ZonedDateTime local=cursor.atZone(zone);
                // Minute boundaries also split midnight and window transitions.
                Instant end=local.withSecond(0).withNano(0).plusMinutes(1).toInstant();
                if(end.isAfter(now))end=now;
                long delta=Duration.between(cursor,end).toMillis();
                LocalDate day=local.toLocalDate();
                long used=days.getOrDefault(day,0L);
                if(previousPolicy.permits(local) && (!previousPolicy.daily || used<previousPolicy.budget)) {
                    long charge=previousPolicy.daily?Math.min(delta,previousPolicy.budget-used):delta;
                    days.put(day,used+charge); changed|=charge>0;
                }
                cursor=end;
            }
        }
        previous=now; previousNanos=nanos; previousPolicy=Policy.read(config);
        if(changed)persist();
        return status(now,zone);
    }
    /** Charge the old state before toggling session activity. Unlock/resume starts a new anchor. */
    public synchronized Status sessionActive(boolean value, Instant now, long nanos, ZoneId zone) {
        sample(now,nanos,zone); active=value;
        return status(now,zone);
    }
    public synchronized void persist() {
        if(!storageError.isEmpty())return;
        Properties p=new Properties();p.setProperty("format","1");
        days.forEach((day,value)->p.setProperty("day."+day,value.toString()));
        try { AtomicProperties.write(file,p); }
        catch(IOException ex){storageError="使用记录保存失败，限制保持生效；请检查磁盘和数据目录。";}
    }
    public synchronized Status status(Instant now, ZoneId zone) {
        Policy p=Policy.read(config);ZonedDateTime local=now.atZone(zone);LocalDate day=local.toLocalDate();
        long used=days.getOrDefault(day,0L),remaining=Math.max(0,p.budget-used);
        Reason reason=Reason.NONE;
        if(p.enabled()&&!storageError.isEmpty())reason=Reason.STORAGE_ERROR;
        else if(p.daily&&remaining==0)reason=Reason.DAILY_BUDGET;
        else if(!p.permits(local))reason=Reason.OUTSIDE_WINDOW;
        ZonedDateTime next=null;
        if(reason==Reason.DAILY_BUDGET || reason==Reason.OUTSIDE_WINDOW) {
            next=reason==Reason.DAILY_BUDGET?day.plusDays(1).atStartOfDay(zone):local;
            for(int i=0;i<days.size()+2;i++) {
                if(p.daily&&days.getOrDefault(next.toLocalDate(),0L)>=p.budget) {
                    next=next.toLocalDate().plusDays(1).atStartOfDay(zone);continue;
                }
                if(p.permits(next))break;
                ZonedDateTime start=windowOpening(next.toLocalDate(),p.start,zone);
                if(!start.isAfter(next)&&start.withLaterOffsetAtOverlap().isAfter(next))start=start.withLaterOffsetAtOverlap();
                if(!start.isAfter(next))start=windowOpening(next.toLocalDate().plusDays(1),p.start,zone);
                next=start;
            }
            if(!p.permits(next))next=null;
        }
        return new Status(day,used,remaining,reason,next,storageError);
    }
    private static ZonedDateTime windowOpening(LocalDate day,int minute,ZoneId zone) {
        LocalDateTime wall=day.atTime(minute/60,minute%60);
        var transition=zone.getRules().getTransition(wall);
        return transition!=null&&transition.isGap()?transition.getDateTimeAfter().atZone(zone):wall.atZone(zone);
    }
}
