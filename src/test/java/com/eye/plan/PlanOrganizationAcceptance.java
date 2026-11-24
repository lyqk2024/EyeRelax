package com.eye.plan;
import com.eye.core.*;
import com.eye.stats.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class PlanOrganizationAcceptance {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void write(Path path,Properties p)throws Exception{try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){p.store(out,"fixture");}}
 public static void main(String[] args)throws Exception{
  System.setProperty("user.home",Files.createTempDirectory("EyeRelax-organization-").toString());Config config=new Config();LocalDate today=StudyDay.dateNow(config),old=today.minusDays(7);Path root=DataPaths.root();Files.createDirectories(root);
  Properties definitions=new Properties();List<String> ids=new ArrayList<>();
  for(int i=0;i<300;i++){PlanItem p=new PlanItem("old"+i,"一次性 "+i,120,120);p.createdDate=old;ids.add(p.id);PlanPropertiesCodec.writePlanSnapshot(definitions,"plan."+p.id+".",p);}
  for(int i=0;i<4;i++){PlanItem p=new PlanItem("regular"+i,"常用 "+i,600,600);p.createdDate=old;p.kind=PlanItem.PlanKind.RECURRING;ids.add(p.id);PlanPropertiesCodec.writePlanSnapshot(definitions,"plan."+p.id+".",p);}
  PlanItem once=new PlanItem("today","今天已完成",60,0);once.createdDate=today;ids.add(once.id);PlanPropertiesCodec.writePlanSnapshot(definitions,"plan.today.",once);
  PlanItem multi=new PlanItem("multi","多日",60,60);multi.createdDate=old;multi.kind=PlanItem.PlanKind.MULTI_DAY;multi.selectedDates=new LinkedHashSet<>(Set.of(today,today.plusDays(2)));ids.add(multi.id);PlanPropertiesCodec.writePlanSnapshot(definitions,"plan.multi.",multi);
  definitions.setProperty("ids",String.join(",",ids));write(root.resolve("all-plans.properties"),definitions);
  Properties history=new Properties();history.setProperty("ids","old0");PlanItem snapshot=new PlanItem("old0","当时的名称",120,30);snapshot.createdDate=old;PlanPropertiesCodec.writePlanSnapshot(history,"snapshot.old0.",snapshot);history.setProperty("remainSeconds.old0","30");Path historyFile=root.resolve("plan-progress-"+old+".properties");write(historyFile,history);byte[] original=Files.readAllBytes(historyFile);
  PlanManager manager=new PlanManager(config);manager.loadToday();check(manager.listAll().stream().filter(p->p.archivedOn!=null).count()==300,"old one-time definitions archived");check(manager.listActiveToday().size()==6,"current list stays small");check(manager.get("today").archivedOn==null&&manager.get("multi").archivedOn==null,"today and future dates retained");
  manager.consumeSeconds("today",60);check(manager.get("today").remainingSeconds==0&&manager.get("today").archivedOn==null,"completed today remains usable");
  var records=manager.listPastRecords(old,old,null);check(records.size()==1&&records.get(0).completedSeconds==90&&records.get(0).plan.name.equals("当时的名称"),"snapshot name and totals unchanged");check(Arrays.equals(original,Files.readAllBytes(historyFile)),"archive never rewrites past file");
  manager.setPinned("regular3",true);check(manager.listRemainingPositive().get(0).id.equals("regular3"),"pinned first");PlanItem template=manager.saveTemplate("old0");check(template.template&&template.archivedOn==null&&!template.id.equals("old0"),"independent template");check(manager.listActiveToday().size()==6,"template not scheduled");PlanItem fresh=manager.copyForToday(template.id);check(fresh.activeToday&&fresh.remainingSeconds==120&&!fresh.id.equals(template.id),"new identity and fresh progress");
  manager.deletePlan("old0");check(Arrays.equals(original,Files.readAllBytes(historyFile))&&manager.listPastRecords(old,old,null).get(0).completedSeconds==90,"archive deletion retains history");
  PlanManager reload=new PlanManager(config);reload.loadToday();check(reload.get("regular3").pinned&&reload.get(template.id).template,"organization persisted");
  var schedule=new PlanSchedule(Set.of());PlanItem midnight=new PlanItem("midnight","夜间",60,60);midnight.createdDate=today;ZoneId zone=ZoneId.of("Asia/Shanghai");long at=ZonedDateTime.of(today.plusDays(1),LocalTime.of(0,35),zone).toInstant().toEpochMilli();LocalDate logical=StudyDay.dateAt(at,zone,4*3600);check(logical.equals(today)&&schedule.isActiveOn(midnight,logical),"midnight retains previous study day");
  var day=new Statistics.Day(today,10,0,List.of(new Statistics.Task("used","已使用",10,100,0),new Statistics.Task("idle","无记录",0,100,0)),List.of());var oldDay=new Statistics.Day(old,20,0,List.of(new Statistics.Task("previous","旧任务",20,100,0)),List.of());var data=new Statistics.Snapshot(old,today,Map.of(old,oldDay,today,day),Map.of());check(Statistics.taskNamesInRange(data,today,today).keySet().equals(Set.of("used")),"range excludes old and idle tasks");check(Statistics.taskNamesInRange(data,old,old).keySet().equals(Set.of("previous")),"old range still shows historical task");
  System.out.println("PASS 300 archived definitions, bounded active list, learning-day boundaries, immutable history, pins, templates, fresh identity, reload and statistics range filtering");
 }
}
