package com.eye.sync;

import com.eye.core.*;
import com.eyerelax.cloud.StrictRules;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Local seven-day commitment survives restarts and data-directory migration; no account required. */
public final class LocalStrictSettings {
    public static final long WEEK=7L*24*60*60*1000;
    private static LocalStrictSettings singleton;
    private final Path file;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Properties state;
    private long lastNanos,effective,lastWrite;
    public LocalStrictSettings(Path file,Clock clock,LongSupplier nanos) throws IOException {
        this.file=file;this.clock=clock;this.nanos=nanos;state=AtomicProperties.read(file);
        effective=Math.max(clock.millis(),Long.parseLong(state.getProperty("seen","0")));lastNanos=nanos.getAsLong();lastWrite=effective;
    }
    public static synchronized LocalStrictSettings get() {
        Path path=DataPaths.bootstrapRoot().resolve("strict-local.properties");
        if(singleton==null||!singleton.file.equals(path))try{singleton=new LocalStrictSettings(path,Clock.systemUTC(),System::nanoTime);}
        catch(IOException e){throw new IllegalStateException("严格设置状态无法读取。",e);}
        return singleton;
    }
    private long now() {
        long tick=nanos.getAsLong();effective=Math.max(clock.millis(),effective+Math.max(0,(tick-lastNanos)/1_000_000));lastNanos=tick;
        if(until()>effective && effective-lastWrite>=60_000)try{save();}catch(IOException e){throw new IllegalStateException("严格设置状态无法保存。",e);}
        return effective;
    }
    public synchronized long until(){return Long.parseLong(state.getProperty("until","0"));}
    public synchronized boolean active(){return now()<until();}
    public synchronized void activate(Properties settings) throws IOException {
        if(active())throw new IllegalStateException("严格期已开启，不能重新计时或提前结束。");
        state.clear();state.setProperty("until",Long.toString(now()+WEEK));setBaseline(settings);save();
    }
    private Properties baseline(){Properties p=new Properties();for(String k:state.stringPropertyNames())if(k.startsWith("config."))p.setProperty(k.substring(7),state.getProperty(k));return p;}
    private void setBaseline(Properties p){state.keySet().removeIf(k->k.toString().startsWith("config."));p.forEach((k,v)->state.setProperty("config."+k,v.toString()));}
    public synchronized boolean allows(Properties next){return !active()||StrictRules.stricter(StrictSettings.normalized(baseline()),StrictSettings.normalized(next));}
    public synchronized void accepted(Properties next) throws IOException {
        if(!active())return;
        if(!allows(next))throw new IOException(StrictSettings.MESSAGE);
        setBaseline(next);save();
    }
    public synchronized Properties recover(Properties p) {
        // Restoring an older config through backup/migration cannot relax the current baseline.
        return allows(p)?p:baseline();
    }
    private void save() throws IOException {state.setProperty("seen",Long.toString(effective));AtomicProperties.write(file,state);lastWrite=effective;}
}
