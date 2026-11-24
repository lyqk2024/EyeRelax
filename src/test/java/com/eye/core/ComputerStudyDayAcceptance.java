package com.eye.core;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class ComputerStudyDayAcceptance {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static long runtime(ComputerActivity s,LocalDate day,String exe){return s.applicationRuntime(day).getOrDefault(exe,0L);}
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);Files.createDirectories(root);ZoneId zone=ZoneId.of("Asia/Shanghai");AtomicInteger boundary=new AtomicInteger(4*3600+30*60);String exe="c:/apps/chatgpt.exe";
  Path file=root.resolve("study-"+System.nanoTime()+".properties");var s=new ComputerActivity(file,zone,boundary::get);var sample=new ComputerActivity.Sample(true,true,exe);LocalDate day=LocalDate.of(2026,10,13),next=day.plusDays(1);
  Instant midnight=ZonedDateTime.of(day,LocalTime.of(23,59,58),zone).toInstant();
  s.sample(midnight,0,sample);s.sample(midnight.plusSeconds(4),4_000_000_000L,sample);s.sampleRunning(midnight,0,Set.of(exe));s.sampleRunning(midnight.plusSeconds(4),4_000_000_000L,Set.of(exe));
  check(s.day(day).measured()==4000&&s.day(next).measured()==0,"midnight remains in previous configured day");check(runtime(s,day,exe)==4000&&runtime(s,next,exe)==0,"runtime follows configured day");check(s.dateAt(midnight.plusSeconds(3600)).equals(day),"today before boundary");
  Instant edge=ZonedDateTime.of(next,LocalTime.of(4,29,58),zone).toInstant();
  s.sample(edge,10_000_000_000L,sample);s.sample(edge.plusSeconds(4),14_000_000_000L,sample);s.sampleRunning(edge,10_000_000_000L,Set.of(exe));s.sampleRunning(edge.plusSeconds(4),14_000_000_000L,Set.of(exe));
  check(s.day(day).measured()==6000&&s.day(next).measured()==2000,"exact 04:30 split without sleep gap");check(s.day(day).hours()[23]==2000&&s.day(next).hours()[0]==2000,"hour bins anchored to half-hour boundary");check(s.hourLabel(next,boundary.get(),0).equals("04:30"),"hour axis matches boundary");
  boundary.set(5*3600+30*60);s.sample(edge.plusSeconds(6),16_000_000_000L,sample);s.sample(edge.plusSeconds(8),18_000_000_000L,sample);s.sampleRunning(edge.plusSeconds(6),16_000_000_000L,Set.of(exe));s.sampleRunning(edge.plusSeconds(8),18_000_000_000L,Set.of(exe));
  check(s.day(next).boundarySeconds()==4*3600+30*60,"recorded boundary frozen");check(s.day(day).mixedBoundaries(),"changed boundaries explicitly labelled");check(s.applicationUsageTotals().get(exe)==12000,"no duplicated totals");s.close();
  var reload=new ComputerActivity(file,zone,boundary::get);check(reload.day(day).measured()==8000&&reload.day(next).measured()==4000&&runtime(reload,day,exe)==8000,"persisted buckets and runtime retain recorded boundaries");reload.close();
  Path legacy=root.resolve("legacy-"+System.nanoTime()+".properties");Properties old=new Properties();old.setProperty(day+".measured","9000");old.setProperty(day+".hour.1","9000");old.setProperty(day+".app."+Base64.getUrlEncoder().withoutPadding().encodeToString(exe.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"9000");AtomicProperties.write(legacy,old);
  var compatibility=new ComputerActivity(legacy,zone,boundary::get);check(compatibility.day(day).measured()==9000&&compatibility.day(day).apps().get(exe)==9000&&compatibility.day(day).boundarySeconds()==0&&compatibility.hasLegacyDay(day),"legacy totals unchanged and labelled");compatibility.close();
  var estimate=new ComputerActivity(root.resolve("history-"+System.nanoTime()+".properties"),zone,()->4*3600+30*60);Instant one=ZonedDateTime.of(next,LocalTime.of(1,0),zone).toInstant();estimate.importHistory(List.of(new ComputerActivity.Range(one.toEpochMilli(),one.plusSeconds(3600).toEpochMilli())));check(estimate.day(day).estimated()==3_600_000&&estimate.day(next).estimated()==0,"history estimates use configured window");estimate.close();
  var dst=new ComputerActivity(root.resolve("dst-"+System.nanoTime()+".properties"),ZoneId.of("Europe/Berlin"),()->0);check(dst.day(LocalDate.of(2026,3,29)).hours().length==23&&dst.day(LocalDate.of(2026,10,25)).hours().length==25,"DST hour counts");check(dst.hourLabel(LocalDate.of(2026,3,29),0,2).equals("03:00"),"DST labels follow actual clock");dst.close();
  System.out.println("PASS configured midnight/04:30 splits, foreground/runtime/hour axes, today, live changes, frozen history, legacy preservation, estimates, reload and DST");
 }
}
