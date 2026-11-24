package com.eye.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.*;

/** Serial, coalesced snapshots prevent disk flushes from blocking UI actions. */
final class StatisticsStore implements AutoCloseable {
    private final Path file;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"EyeRelax-statistics-save");thread.setDaemon(true);return thread;});
    private Properties pending;
    private boolean writing;
    private volatile IOException failure;
    StatisticsStore(Path file){this.file=file;}
    synchronized void save(Properties values){
        Properties snapshot=new Properties();snapshot.putAll(values);pending=snapshot;
        if(!writing){writing=true;writer.execute(this::drain);}
    }
    private void drain(){
        while(true){
            Properties snapshot;
            synchronized(this){snapshot=pending;pending=null;if(snapshot==null){writing=false;return;}}
            try{AtomicProperties.write(file,snapshot);failure=null;}catch(IOException ex){failure=ex;}
        }
    }
    String warning(){IOException problem=failure;return problem==null?"":"统计暂未保存："+problem.getMessage();}
    @Override public void close(){
        writer.shutdown();
        try{if(!writer.awaitTermination(10,TimeUnit.SECONDS))System.err.println("Statistics save is still completing.");}
        catch(InterruptedException ex){Thread.currentThread().interrupt();}
    }
}
