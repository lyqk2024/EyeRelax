package com.eye.stats;
import java.util.*;
/** Stateless app ranking policy: duration, watched scope, count percentage, presentation order. */
public final class ApplicationRanking {
 private ApplicationRanking(){}
    public static List<Map.Entry<String,Long>> filter(Map<String,Long> usage,Set<String> watched,boolean onlyWatched,long minimum,int percent,boolean ascending){
        List<Map.Entry<String,Long>> ranked=new ArrayList<>(usage.entrySet());
        Comparator<Map.Entry<String,Long>> descending=Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey);
        ranked.removeIf(e->e.getValue()<=0||e.getValue()<minimum||(onlyWatched&&!watched.contains(e.getKey())));ranked.sort(descending);
        int count=(int)Math.ceil(ranked.size()*Math.max(1,Math.min(100,percent))/100.0);
        ranked=new ArrayList<>(ranked.subList(0,count));
        if(ascending)ranked.sort(Map.Entry.<String,Long>comparingByValue().thenComparing(Map.Entry::getKey));
        return ranked;
    }
}
