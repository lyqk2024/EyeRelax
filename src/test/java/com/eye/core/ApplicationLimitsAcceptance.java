package com.eye.core;
import com.eye.sync.*;
import com.eyerelax.cloud.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class ApplicationLimitsAcceptance {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final String APP="c:\\apps\\sample.exe",OTHER="c:\\apps\\other.exe";
    static String json(ApplicationLimitRule r){return ApplicationLimitRule.encode(Map.of(r.path(),r));}
    static ApplicationLimitRule rule(boolean enabled,boolean blocked,long daily,boolean window,int start,int end){return new ApplicationLimitRule(APP,"Sample",enabled,blocked,daily,window,start,end);}
    static final class Time extends Clock {
        long wall=Instant.parse("2026-11-07T04:00:00Z").toEpochMilli(),nano=1;
        public Instant instant(){return Instant.ofEpochMilli(wall);}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}
        void advance(long ms){wall+=ms;nano+=ms*1_000_000;}
    }
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("EyeRelax-app-rules-");System.setProperty("user.home",dir.toString());
        Time t=new Time();String[] current={json(rule(true,false,60_000,false,480,1380))};
        ApplicationLimits engine=new ApplicationLimits(dir.resolve("usage.properties"),ZoneOffset.UTC,()->4*3600,()->current[0]);
        check(ApplicationLimitRule.parse(current[0]).get(APP).daily()==60_000,"rule roundtrip");
        check(ApplicationLimitRule.key("C:/APPS/SAMPLE.EXE").equals(APP),"Windows path identity");
        engine.sample(t.instant(),t.nano,APP,true,false);
        for(int i=0;i<59;i++){t.advance(1000);engine.sample(t.instant(),t.nano,APP,true,false);}
        check(engine.used(APP,t.instant())==59_000,"foreground reading counted without input condition");
        check(!engine.status(APP,t.instant()).blocked(),"before limit");t.advance(1000);check(engine.sample(t.instant(),t.nano,APP,true,false).reason()==ApplicationLimits.Reason.DAILY,"exact quota reached");
        t.advance(1000);engine.sample(t.instant(),t.nano,APP,true,true);check(engine.used(APP,t.instant())==60_000,"shield not charged");
        check(!engine.status(OTHER,t.instant()).blocked(),"other app unaffected");engine.close();
        engine=new ApplicationLimits(dir.resolve("usage.properties"),ZoneOffset.UTC,()->4*3600,()->current[0]);check(engine.status(APP,t.instant()).blocked(),"restart retains quota");
        current[0]=json(rule(false,false,60_000,false,480,1380));check(!engine.status(APP,t.instant()).blocked(),"pause outside strict");current[0]=json(rule(true,false,60_000,false,480,1380));check(engine.status(APP,t.instant()).blocked(),"re-enable does not reset quota");
        t.wall=Instant.parse("2026-11-08T03:59:59Z").toEpochMilli();check(engine.status(APP,t.instant()).blocked(),"midnight does not reset 4am day");t.advance(1000);check(!engine.status(APP,t.instant()).blocked(),"4am resets");
        current[0]=json(rule(true,true,0,true,0,1439));check(engine.status(APP,t.instant()).reason()==ApplicationLimits.Reason.BANNED,"ban wins even inside window");
        current[0]=json(rule(true,false,0,true,22*60,6*60));
        for(int hour:new int[]{22,23,0,5}){t.wall=LocalDate.of(2026,11,8).atTime(hour,0).toInstant(ZoneOffset.UTC).toEpochMilli();check(!engine.status(APP,t.instant()).blocked(),"overnight allowed "+hour);}
        for(int hour:new int[]{6,12,21}){t.wall=LocalDate.of(2026,11,8).atTime(hour,0).toInstant(ZoneOffset.UTC).toEpochMilli();check(engine.status(APP,t.instant()).reason()==ApplicationLimits.Reason.WINDOW,"outside window "+hour);}
        current[0]=json(rule(true,false,60_000,false,480,1380));t=new Time();final Time usageTime=t;
        ApplicationLimits sleep=new ApplicationLimits(dir.resolve("sleep.properties"),ZoneOffset.UTC,()->4*3600,()->current[0]);
        sleep.sample(t.instant(),t.nano,APP,true,false);t.advance(1000);sleep.sample(t.instant(),t.nano,APP,false,false);long before=sleep.used(APP,t.instant());t.advance(3000);sleep.sample(t.instant(),t.nano,APP,false,false);check(sleep.used(APP,t.instant())==before,"locked not charged");
        sleep.sample(t.instant(),t.nano,APP,true,false);t.advance(60_000);sleep.sample(t.instant(),t.nano,APP,true,false);check(sleep.used(APP,t.instant())==before,"sleep gap not invented");
        t.advance(1000);sleep.sample(t.instant(),t.nano,OTHER,true,false);t.advance(1000);sleep.sample(t.instant(),t.nano,OTHER,true,false);check(sleep.used(APP,t.instant())==before+1000,"switch charges old foreground only");sleep.close();
        Config config=new Config();config.applicationLimits=json(rule(true,false,3_600_000,true,480,1380));Properties initial=StrictSettings.properties(config);
        Time clock=new Time();Path strictFile=dir.resolve("strict.properties");LocalStrictSettings strict=new LocalStrictSettings(strictFile,clock,()->clock.nano);strict.activate(initial);long until=strict.until();
        check(strict.active(),"local strict active");check(until==clock.millis()+LocalStrictSettings.WEEK,"exact seven days");
        Config next=StrictSettings.copy(config);next.applicationLimits=json(rule(true,false,1_800_000,true,540,1320));check(strict.allows(StrictSettings.properties(next)),"lower budget and window subset");strict.accepted(StrictSettings.properties(next));
        check(!strict.allows(initial),"cannot relax back to initial baseline");
        for(ApplicationLimitRule r:List.of(rule(false,false,1_800_000,true,540,1320),rule(true,false,3_600_000,true,540,1320),rule(true,false,1_800_000,true,500,1320),rule(true,false,0,false,0,0))){Config bad=StrictSettings.copy(next);bad.applicationLimits=json(r);check(!strict.allows(StrictSettings.properties(bad)),"relaxation rejected "+r);}
        Config deleted=StrictSettings.copy(next);deleted.applicationLimits="{}";check(!strict.allows(StrictSettings.properties(deleted)),"delete rejected");
        Config banned=StrictSettings.copy(next);banned.applicationLimits=json(rule(true,true,0,false,0,0));check(strict.allows(StrictSettings.properties(banned)),"full ban tighter");strict.accepted(StrictSettings.properties(banned));check(!strict.allows(StrictSettings.properties(next)),"unban rejected");
        next=StrictSettings.copy(banned);next.dayBoundarySeconds=0;check(!strict.allows(StrictSettings.properties(next)),"boundary frozen");
        next=StrictSettings.copy(banned);next.workSeconds+=1;check(!strict.allows(StrictSettings.properties(next)),"longer work rejected");next=StrictSettings.copy(banned);next.relaxSeconds--;check(!strict.allows(StrictSettings.properties(next)),"shorter break rejected");
        next=StrictSettings.copy(banned);next.mode=Config.ForceMode.A_SOFT;check(!strict.allows(StrictSettings.properties(next)),"weaker rest rejected");
        next=StrictSettings.copy(banned);next.workSeconds--;next.relaxSeconds++;next.mode=Config.ForceMode.C_ULTRA;check(strict.allows(StrictSettings.properties(next)),"stronger rhythm accepted");
        LocalStrictSettings reloaded=new LocalStrictSettings(strictFile,clock,()->clock.nano);check(reloaded.active()&&!reloaded.allows(initial),"strict restart persists latest baseline");check(reloaded.recover(initial).getProperty("applicationLimits").equals(banned.applicationLimits),"older backup recovered to accepted restrictions");
        clock.advance(LocalStrictSettings.WEEK-1);check(reloaded.active(),"last millisecond locked");clock.advance(1);check(!reloaded.active()&&reloaded.allows(initial),"seven days expires without account");
        Time rollback=new Time();LocalStrictSettings rollbackState=new LocalStrictSettings(dir.resolve("rollback.properties"),rollback,()->rollback.nano);rollbackState.activate(initial);rollback.advance(120_000);check(rollbackState.active(),"clock checkpoint");rollback.wall-=86_400_000;check(rollbackState.active(),"clock rollback cannot unlock");
        check(config.save(),"config saved in isolated home");LocalStrictSettings.get().activate(StrictSettings.properties(config));config.applicationLimits="{}";check(!config.save(),"Config.save guards deletion");check(!config.applicationLimits.equals("{}"),"failed save restored memory");
        System.out.println("ApplicationLimitsAcceptance: "+checks+" checks passed");
    }
}
