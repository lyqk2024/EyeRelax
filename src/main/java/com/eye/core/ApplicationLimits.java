package com.eye.core;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Independent of UI statistics: foreground video/reading counts even without keyboard input. */
public final class ApplicationLimits implements AutoCloseable {
    public enum Reason { NONE, BANNED, DAILY, WINDOW, STORAGE }
    public record Status(String path, String name, long used, long budget, Reason reason) {
        public boolean blocked() { return reason != Reason.NONE; }
    }
    private final Path file;
    private final ZoneId zone;
    private final IntSupplier boundary;
    private final Supplier<String> source;
    private Properties usage;
    private String cachedJson, previousApp="";
    private Map<String,ApplicationLimitRule> rules=Map.of();
    private long previousWall,previousNanos,lastSave;
    private boolean previousAllowed,dirty;
    private String error="";
    private Map<String,String> enforcementErrors=Map.of();
    public ApplicationLimits(Path file,ZoneId zone,IntSupplier boundary,Supplier<String> source) {
        this.file=file;this.zone=zone;this.boundary=boundary;this.source=source;
        try {usage=AtomicProperties.read(file);} catch(Exception e){usage=new Properties();error="应用用量记录无法读取。";}
    }
    private void reload() {
        String json=source.get();
        if (!Objects.equals(json,cachedJson)) {rules=ApplicationLimitRule.parse(json);cachedJson=json;}
    }
    public synchronized boolean enabled(){reload();return rules.values().stream().anyMatch(ApplicationLimitRule::enabled);}
    public synchronized Set<String> blockedPaths(Instant now){reload();Set<String> out=new HashSet<>();for(String path:rules.keySet())if(status(path,now).blocked())out.add(path);return out;}
    public synchronized void enforcementErrors(Map<String,String> errors){enforcementErrors=Map.copyOf(errors);}
    public synchronized String enforcementError(String app){return enforcementErrors.getOrDefault(ApplicationLimitRule.key(app),"");}
    private String usageKey(String app,LocalDate day) {
        return day+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(app.getBytes(StandardCharsets.UTF_8));
    }
    public synchronized long used(String app,Instant now) {
        String key=usageKey(ApplicationLimitRule.key(app),StudyDay.dateAt(now.toEpochMilli(),zone,boundary.getAsInt()));
        try{return Math.max(0,Long.parseLong(usage.getProperty(key,"0")));}catch(RuntimeException ex){error="应用用量记录无法读取。";return 0;}
    }
    public synchronized Status status(String app,Instant now) {
        reload();app=ApplicationLimitRule.key(app);ApplicationLimitRule r=rules.get(app);
        long amount=used(app,now);
        if (r==null || !r.enabled())return new Status(app,"",amount,0,Reason.NONE);
        int minute=now.atZone(zone).getHour()*60+now.atZone(zone).getMinute();
        Reason reason=!error.isEmpty()?Reason.STORAGE:r.blocked()?Reason.BANNED:r.daily()>0&&amount>=r.daily()?Reason.DAILY:!r.permits(minute)?Reason.WINDOW:Reason.NONE;
        return new Status(app,r.name(),amount,r.daily(),reason);
    }
    public synchronized Status sample(Instant now,long nanos,String app,boolean unlocked,boolean shield) {
        reload();long wall=now.toEpochMilli(),delta=wall-previousWall,elapsed=(nanos-previousNanos)/1_000_000;
        if(previousAllowed && delta>0 && elapsed>0 && delta<=5000 && Math.abs(delta-elapsed)<=1500) {
            long cursor=wall-Math.min(delta,elapsed);
            while(cursor<wall) {
                LocalDate day=StudyDay.dateAt(cursor,zone,boundary.getAsInt());
                long end=Math.min(wall,StudyDay.nextBoundaryAfter(cursor,zone,boundary.getAsInt()));
                String key=usageKey(previousApp,day);
                long old=Long.parseLong(usage.getProperty(key,"0"));
                usage.setProperty(key,Long.toString(old+end-cursor));dirty=true;cursor=end;
            }
        }
        previousWall=wall;previousNanos=nanos;previousApp=ApplicationLimitRule.key(app);
        Status result=status(app,now);ApplicationLimitRule rule=rules.get(previousApp);
        previousAllowed=unlocked&&!shield&&rule!=null&&rule.enabled()&&!result.blocked();
        if (dirty && (wall-lastSave>=5000 || result.blocked())) {persist();lastSave=wall;result=status(app,now);}
        return result;
    }
    public synchronized String error(){return error;}
    public synchronized void persist() {
        if(!dirty || !error.isEmpty())return;
        try {AtomicProperties.write(file,usage);dirty=false;}catch(Exception ex){error="应用用量保存失败，限制保持生效。";}
    }
    @Override public void close(){persist();}
}
