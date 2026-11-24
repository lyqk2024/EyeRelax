package com.eye.core;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Owns polling lifecycle; native sampling and statistics persistence stay off Swing's EDT. */
public final class ComputerActivityRecorder implements AutoCloseable {
    private final ComputerActivity activity;
    private final Clock clock;
    private final Supplier<ComputerActivity.Sample> foreground;
    private final Supplier<Set<String>> running;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(task->{
        Thread thread=new Thread(task,"EyeRelax-computer-statistics");thread.setDaemon(true);return thread;
    });
    public ComputerActivityRecorder(ComputerActivity activity,Clock clock,Supplier<ComputerActivity.Sample> foreground,Supplier<Set<String>> running){
        this.activity=activity;this.clock=clock;this.foreground=foreground;this.running=running;
    }
    public void start(){
        executor.scheduleWithFixedDelay(()->{
            try{activity.sample(clock.instant(),System.nanoTime(),foreground.get());}
            catch(RuntimeException ex){activity.sessionAvailable(false);System.err.println("Computer statistics: "+ex.getMessage());}
        },0,1,TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(()->{
            try{activity.sampleRunning(clock.instant(),System.nanoTime(),running.get());}
            catch(RuntimeException ex){System.err.println("Application runtime: "+ex.getMessage());}
        },0,2,TimeUnit.SECONDS);
    }
    @Override public void close(){
        executor.shutdownNow();
        try{executor.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
        activity.close();
    }
}
