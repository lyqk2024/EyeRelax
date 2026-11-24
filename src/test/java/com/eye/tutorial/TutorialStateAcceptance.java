package com.eye.tutorial;

import com.eye.core.AtomicProperties;
import java.nio.file.*;
import java.util.*;

/** Isolated install/upgrade scenarios; no actual profile, configuration or OS hooks. */
public final class TutorialStateAcceptance {
    private static int checks;
    private static void check(boolean value,String description){checks++;if(!value)throw new AssertionError(description);}
    private static Path profile(String name)throws Exception{return Files.createTempDirectory("EyeRelax-tutorial-state-"+name+"-");}
    private static TutorialState completed(Path root,String version)throws Exception{
        TutorialState state=TutorialState.capture(root,version);state.finish(false);return state;
    }
    public static void main(String[] args)throws Exception{
        Path freshRoot=profile("fresh");Files.writeString(freshRoot.resolve("instance.lock"),"");
        TutorialState fresh=TutorialState.capture(freshRoot,"1.0.0");
        check(fresh.shouldOfferWelcome()&&!fresh.interrupted(),"empty install with normal lock offers welcome");
        fresh.registerFeatures(Map.of("existing","0.9.0","installed","1.0.0","future","2.0.0"));
        fresh.begin();fresh.progress(3);fresh=TutorialState.capture(freshRoot,"1.0.0");
        check(fresh.interrupted()&&fresh.step()==3,"welcome progress survives restart");
        fresh.finish(true);
        check(!TutorialState.capture(freshRoot,"1.0.0").shouldOfferWelcome(),"welcome skip persists");
        check(!fresh.shouldOfferFeature("installed","1.0.0")&&!fresh.shouldOfferFeature("future","2.0.0"),"fresh installs get no existing or future update lesson");

        Path legacyRoot=profile("legacy");Path config=legacyRoot.resolve("config.properties");Files.writeString(config,"workSeconds=900\n");
        byte[] before=Files.readAllBytes(config);TutorialState legacy=TutorialState.capture(legacyRoot,"1.0.0");
        legacy.registerFeatures(Map.of("existing","1.0.0"));
        check(!legacy.shouldOfferWelcome()&&!legacy.shouldOfferFeature("existing","1.0.0"),"existing profile without tutorial never treated as fresh");
        check(Arrays.equals(before,Files.readAllBytes(config)),"capture preserves actual configuration bytes");

        Path upgradeRoot=profile("upgrade");completed(upgradeRoot,"1.0.0");
        TutorialState update=TutorialState.capture(upgradeRoot,"1.2.0");
        update.registerFeatures(Map.of("prior","1.0.0","first","1.1.0","second","1.2.0","future","1.3.0"));
        check(update.shouldOfferFeature("first","1.1.0")&&update.shouldOfferFeature("second","1.2.0"),"skipped releases queue each actual new feature");
        check(!update.shouldOfferFeature("prior","1.0.0")&&!update.shouldOfferFeature("future","1.3.0"),"past and future features not announced");
        check(!update.shouldOfferWelcome(),"upgrade does not repeat welcome");
        update.progressFeature("first",2);update=TutorialState.capture(upgradeRoot,"1.2.0");
        update.registerFeatures(Map.of("first","1.1.0","second","1.2.0"));
        check(update.featureInterrupted("first")&&update.featureStep("first")==2,"feature progress survives restart");
        check(update.shouldOfferFeature("first","1.1.0"),"unfinished feature stays eligible");
        update.finishFeature("first",true);update.finishFeature("second",false);
        update=TutorialState.capture(upgradeRoot,"1.3.0");
        update.registerFeatures(Map.of("first","1.3.0","second","1.3.0"));
        check(!update.shouldOfferFeature("first","1.3.0")&&!update.shouldOfferFeature("second","1.3.0"),"completed and skipped feature IDs never repeat");
        check(update.featureStep("first")==0&&!update.featureInterrupted("first"),"finished feature discards its cursor");

        Path earlyExit=profile("early-exit");completed(earlyExit,"1.0.0");
        TutorialState.capture(earlyExit,"1.1.0");
        TutorialState recovered=TutorialState.capture(earlyExit,"1.2.0");
        recovered.registerFeatures(Map.of("lost-at-startup","1.1.0","newer","1.2.0"));
        check(recovered.shouldOfferFeature("lost-at-startup","1.1.0")&&recovered.shouldOfferFeature("newer","1.2.0"),"startup interruption before registration retains upgrade range");
        recovered=TutorialState.capture(earlyExit,"1.2.1");recovered.registerFeatures(Map.of());
        check(recovered.shouldOfferFeature("lost-at-startup","1.1.0"),"patch release and empty manifest preserve queued feature");

        Path repairRoot=profile("repair");completed(repairRoot,"1.0.0");
        TutorialState repair=TutorialState.capture(repairRoot,"1.0.1");repair.registerFeatures(Map.of());
        check(!repair.shouldOfferWelcome()&&!repair.shouldOfferFeature("anything","1.0.1"),"repair-only update has no automatic course");
        repair=TutorialState.capture(repairRoot,"1.0.1");repair.registerFeatures(Map.of("late-registration","1.0.1"));
        check(!repair.shouldOfferFeature("late-registration","1.0.1"),"same-version late catalog cannot invent an upgrade");

        Path migrationRoot=profile("migration");Properties old=new Properties();old.setProperty("schema","1");old.setProperty("baselineVersion","0.1.0");
        old.setProperty("welcome","completed");old.setProperty("feature.finished","skipped");old.setProperty("topic.computer","in_progress");old.setProperty("topicStep.computer","3");
        AtomicProperties.write(migrationRoot.resolve("tutorial.properties"),old);
        TutorialState migration=TutorialState.capture(migrationRoot,"1.5.0");migration.registerFeatures(Map.of("old-content","1.4.0","finished","1.5.0"));
        check(!migration.shouldOfferWelcome()&&!migration.shouldOfferFeature("old-content","1.4.0"),"schema1 old baseline migrates silently");
        check(!migration.shouldOfferFeature("finished","1.5.0"),"migration preserves skipped update course");
        check(migration.shouldOfferTopic("computer")&&migration.topicStep("computer")==3,"migration preserves topic progress");
        Properties migrated=AtomicProperties.read(migrationRoot.resolve("tutorial.properties"));
        check("2".equals(migrated.getProperty("schema"))&&"1.5.0".equals(migrated.getProperty("lastSeenVersion")),"migration establishes actual version");
        migration=TutorialState.capture(migrationRoot,"1.6.0");migration.registerFeatures(Map.of("real-after-migration","1.6.0"));
        check(migration.shouldOfferFeature("real-after-migration","1.6.0"),"subsequent real upgrade works after migration");

        Path interruptedOldRoot=profile("old-welcome");Properties interruptedOld=new Properties();
        interruptedOld.setProperty("schema","1");interruptedOld.setProperty("welcome","in_progress");interruptedOld.setProperty("step","4");
        AtomicProperties.write(interruptedOldRoot.resolve("tutorial.properties"),interruptedOld);
        TutorialState migratedWelcome=TutorialState.capture(interruptedOldRoot,"1.5.0");
        check(migratedWelcome.shouldOfferWelcome()&&migratedWelcome.interrupted()&&migratedWelcome.step()==4,"migration retains an interrupted genuine first-run tutorial");

        Path delayedWelcome=profile("pending-welcome");TutorialState.capture(delayedWelcome,"1.0.0");
        TutorialState delayed=TutorialState.capture(delayedWelcome,"1.1.0");delayed.registerFeatures(Map.of("new","1.1.0"));
        check(!delayed.shouldOfferFeature("new","1.1.0"),"pending welcome takes precedence over update tutorial");
        delayed.finish(false);check(delayed.shouldOfferFeature("new","1.1.0"),"update remains available after welcome");

        Path rollbackRoot=profile("rollback");completed(rollbackRoot,"1.0.0");TutorialState rollback=TutorialState.capture(rollbackRoot,"2.0.0");
        rollback.registerFeatures(Map.of("v2","2.0.0"));rollback=TutorialState.capture(rollbackRoot,"1.0.0");rollback.registerFeatures(Map.of());
        check(!rollback.shouldOfferFeature("v2","2.0.0"),"rollback cannot offer absent future feature");
        rollback=TutorialState.capture(rollbackRoot,"2.0.0");rollback.registerFeatures(Map.of());
        check(rollback.shouldOfferFeature("v2","2.0.0"),"returning to current release preserves queued feature");

        System.out.println("TutorialStateAcceptance: "+checks+" checks passed; fresh/legacy/schema migration, repair/new-feature updates, skip/resume, crash recovery and rollback.");
    }
}
