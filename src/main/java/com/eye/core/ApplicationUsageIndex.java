package com.eye.core;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** In-memory application aggregates. The properties file remains the storage format. */
final class ApplicationUsageIndex {
    private record Key(LocalDate day,String executable,boolean runtime) {}
    private final Map<String,Key> keys=new HashMap<>();
    private final Map<LocalDate,Map<String,Long>> foreground=new HashMap<>(),runtime=new HashMap<>();
    private final Map<String,Long> totals=new HashMap<>();
    ApplicationUsageIndex(Properties properties){
        for(String key:properties.stringPropertyNames()){
            try{long value=Long.parseLong(properties.getProperty(key));if(value>0)add(key,value);}
            catch(IllegalArgumentException ignored){/* Non-counter settings and malformed legacy entries. */}
        }
    }
    void add(String property,long amount){
        String normalized=property.startsWith("study.")?property.substring(property.indexOf('.',6)+1):property;
        if(normalized.length()<15)return;
        boolean background=normalized.startsWith(".runtime.",10),active=normalized.startsWith(".app.",10);
        if(!background&&!active)return;
        Key key=keys.computeIfAbsent(property,name->new Key(LocalDate.parse(normalized.substring(0,10)),
            new String(Base64.getUrlDecoder().decode(normalized.substring(background?19:15)),StandardCharsets.UTF_8),background));
        (key.runtime?runtime:foreground).computeIfAbsent(key.day,d->new HashMap<>()).merge(key.executable,amount,Long::sum);
        if(!key.runtime)totals.merge(key.executable,amount,Long::sum);
    }
    Map<String,Long> foreground(LocalDate day){return snapshot(foreground.get(day));}
    Map<String,Long> runtime(LocalDate day){return snapshot(runtime.get(day));}
    Map<String,Long> totals(){return new HashMap<>(totals);}
    private static Map<String,Long> snapshot(Map<String,Long> values){return values==null?new HashMap<>():new HashMap<>(values);}
}
