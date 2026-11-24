package com.eye.stats;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.time.LocalDate;
import static com.eye.i18n.I18n.text;

/** One row per frozen study day, including zero days; durations remain numeric seconds. */
public final class StatisticsExport {
    private StatisticsExport() {}
    public static void writeDailyCsv(Statistics.Snapshot snapshot,LocalDate from,LocalDate to,Path target)throws IOException{
        if(from.isAfter(to)||from.isBefore(snapshot.from())||to.isAfter(snapshot.to()))throw new IllegalArgumentException("Unloaded export range");
        StringBuilder csv=new StringBuilder("\uFEFF");
        csv.append(text("学习日,专注秒数,休息秒数,已记录专注秒数,计划目标数,完成目标数")).append("\r\n");
        for(LocalDate date=from;!date.isAfter(to);date=date.plusDays(1)){
            Statistics.Day day=snapshot.day(date);
            csv.append(date).append(',').append(seconds(day.focusMs())).append(',').append(seconds(day.restMs())).append(',')
                .append(seconds(day.recordedMs(false))).append(',').append(day.targetCount()).append(',').append(day.completeCount()).append("\r\n");
        }
        Files.writeString(target,csv,StandardCharsets.UTF_8);
    }
    private static String seconds(long millis){return java.math.BigDecimal.valueOf(millis,3).stripTrailingZeros().toPlainString();}
}
