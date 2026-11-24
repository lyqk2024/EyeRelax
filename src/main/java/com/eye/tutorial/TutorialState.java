package com.eye.tutorial;

import com.eye.core.AtomicProperties;
import com.eye.app.AppVersion;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Capture install eligibility before normal startup creates configuration/history. */
public final class TutorialState {
    private static final Set<String> INSTALL_MARKERS=Set.of("instance.lock","data-location.properties","data-location.request","installer-close.request");
    private static final Set<String> FINISHED=Set.of("completed","skipped");
    private static final Set<String> PENDING=Set.of("pending","in_progress");
    private final Path path;
    private final Properties values;
    private final String currentVersion;

    private TutorialState(Path path,Properties values,String currentVersion){
        this.path=path;this.values=values;this.currentVersion=currentVersion;
    }
    public static TutorialState ephemeral(){return new TutorialState(null,new Properties(),AppVersion.CURRENT);}
    public static TutorialState capture(Path root)throws IOException{return capture(root,AppVersion.CURRENT);}

    /** Explicit version seam for isolated upgrade tests; production uses the packaged version. */
    static TutorialState capture(Path root,String version)throws IOException{
        AppVersion.compare(version,version);
        Path file=root.resolve("tutorial.properties");
        if(Files.exists(file)){
            Properties values=AtomicProperties.read(file);
            TutorialState state=new TutorialState(file,values,version);
            boolean changed=!"2".equals(values.getProperty("schema"));
            String previous=validVersion(values.getProperty("lastSeenVersion"));
            if(previous==null){
                // Schema 1 recorded the first version, not the last version the person used.
                // Using that baseline would announce years-old features on a repair update.
                values.setProperty("lastSeenVersion",version);
                values.remove("upgradeFromVersion");values.remove("upgradeToVersion");changed=true;
            }else if(AppVersion.compare(version,previous)>0){
                // Keep an unprocessed window if startup ended before course registration.
                String from=validVersion(values.getProperty("upgradeFromVersion"));
                values.setProperty("upgradeFromVersion",from==null?previous:from);
                values.setProperty("upgradeToVersion",version);
                values.setProperty("lastSeenVersion",version);changed=true;
            }
            values.setProperty("schema","2");if(changed)state.save();return state;
        }
        boolean legacy=false;
        if(Files.isDirectory(root))try(var files=Files.list(root)){
            legacy=files.anyMatch(p->!INSTALL_MARKERS.contains(p.getFileName().toString()));
        }
        Properties values=new Properties();values.setProperty("schema","2");
        values.setProperty("welcome",legacy?"legacy":"pending");
        values.setProperty("baselineVersion",version);values.setProperty("lastSeenVersion",version);
        TutorialState state=new TutorialState(file,values,version);state.save();return state;
    }

    /**
     * Queue only features introduced by this upgrade, then consume its version window.
     * Call once with the complete release manifest, even if it is empty. Previously
     * queued courses remain available until completed or skipped.
     */
    public void registerFeatures(Map<String,String> introducedVersions){
        String from=validVersion(values.getProperty("upgradeFromVersion"));
        String to=validVersion(values.getProperty("upgradeToVersion"));
        if(from==null||to==null||AppVersion.compare(to,currentVersion)>0)return;
        for(var entry:introducedVersions.entrySet()){
            String introduced=validVersion(entry.getValue());
            if(introduced!=null&&!values.containsKey("feature."+entry.getKey())
                    &&AppVersion.compare(introduced,from)>0&&AppVersion.compare(introduced,to)<=0){
                values.setProperty("feature."+entry.getKey(),"pending");
                values.setProperty("featureVersion."+entry.getKey(),introduced);
            }
        }
        values.remove("upgradeFromVersion");values.remove("upgradeToVersion");save();
    }
    public boolean shouldOfferWelcome(){return PENDING.contains(values.getProperty("welcome","legacy"));}
    public boolean interrupted(){return "in_progress".equals(values.getProperty("welcome"));}
    public int step(){return position("step");}
    public void begin(){values.setProperty("welcome","in_progress");save();}
    public void progress(int step){values.setProperty("step",Integer.toString(Math.max(0,step)));save();}
    public void finish(boolean skipped){values.setProperty("welcome",skipped?"skipped":"completed");values.setProperty("step","0");save();}

    public boolean shouldOfferFeature(String id,String introducedVersion){
        String introduced=validVersion(introducedVersion);
        return !shouldOfferWelcome()&&introduced!=null&&AppVersion.compare(introduced,currentVersion)<=0
                &&PENDING.contains(values.getProperty("feature."+id,""));
    }
    public boolean featureInterrupted(String id){return "in_progress".equals(values.getProperty("feature."+id));}
    public int featureStep(String id){return position("featureStep."+id);}
    public void progressFeature(String id,int step){
        if(!PENDING.contains(values.getProperty("feature."+id,"")))return;
        values.setProperty("feature."+id,"in_progress");
        values.setProperty("featureStep."+id,Integer.toString(Math.max(0,step)));save();
    }
    public void finishFeature(String id,boolean skipped){
        values.setProperty("feature."+id,skipped?"skipped":"completed");values.remove("featureStep."+id);save();
    }
    public boolean shouldOfferTopic(String id){return !FINISHED.contains(values.getProperty("topic."+id,"pending"));}
    public int topicStep(String id){return position("topicStep."+id);}
    public void progressTopic(String id,int step){values.setProperty("topic."+id,"in_progress");values.setProperty("topicStep."+id,Integer.toString(Math.max(0,step)));save();}
    public void finishTopic(String id,boolean skipped){values.setProperty("topic."+id,skipped?"skipped":"completed");save();}

    private int position(String key){try{return Math.max(0,Integer.parseInt(values.getProperty(key,"0")));}catch(NumberFormatException ex){return 0;}}
    private static String validVersion(String value){
        if(value==null)return null;
        try{AppVersion.compare(value,value);return value;}catch(IllegalArgumentException ex){return null;}
    }
    private void save(){
        if(path==null)return;
        try{AtomicProperties.write(path,values);}catch(Exception ex){System.err.println("Tutorial state: "+ex.getMessage());}
    }
}
