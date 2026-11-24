package com.eye.plan;
import com.eye.core.*;
import com.eye.stats.Statistics;
import com.eye.ui.theme.Theme;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Task analysis accepts recurring definitions only; other statistics retain every task. */
public final class AnalysisScopeAcceptance {
 static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String n,Object value)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,value);}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container p)layout(p);}
 public static void main(String[] args)throws Exception{
  System.setProperty("user.home",Files.createTempDirectory("EyeRelax-analysis-scope-").toString());Path out=Path.of(args[0]);Files.createDirectories(out);
  Config config=new Config();PlanManager manager=new PlanManager(config);manager.loadToday();var today=manager.getToday();
  var recurring=manager.addPlan("长期阅读",600,PlanItem.PlanKind.RECURRING,PlanItem.RecurringRule.EVERY_DAY,Set.of(),Set.of());
  var single=manager.addPlan("一次性任务",600);var finite=manager.addPlan("两日任务",600,PlanItem.PlanKind.MULTI_DAY,PlanItem.RecurringRule.EVERY_DAY,Set.of(),Set.of(today,today.plusDays(1)));
  var unused=manager.addPlan("长期未使用",600,PlanItem.PlanKind.RECURRING,PlanItem.RecurringRule.EVERY_DAY,Set.of(),Set.of());manager.saveTemplate(recurring.id);
  for(var p:java.util.List.of(recurring,single,finite))manager.consumeSeconds(p.id,30);
  var snapshot=Statistics.load(manager,config,today,today);check(snapshot.analysisEligibleTaskIds().contains(recurring.id)&&!snapshot.analysisEligibleTaskIds().contains(single.id)&&!snapshot.analysisEligibleTaskIds().contains(finite.id),"snapshot uses recurring kind, not one recorded day");
  check(Statistics.taskNamesInRange(snapshot,today,today).keySet().containsAll(Set.of(recurring.id,single.id,finite.id)),"daily and heatmap history retain all kinds");
  Throwable[] error={null};SwingUtilities.invokeAndWait(()->{try{
   Theme.install();ActivityPanel panel=new ActivityPanel(manager,config);set(panel,"data",snapshot);set(panel,"selectedDay",today);set(panel,"detailMode","task");
   var fill=ActivityPanel.class.getDeclaredMethod("fillTaskChoices");fill.setAccessible(true);fill.invoke(panel);var analysis=(JComboBox<?>)field(panel,"analysisTaskBox");
   check(analysis.getItemCount()==1&&analysis.getItemAt(0).toString().contains("长期阅读"),"analysis excludes single/multi-day despite records");
   ((JCheckBox)field(panel,"includeUnrecorded")).doClick();check(analysis.getItemCount()==2,"opt-in adds only unused recurring plan, never finite or template");
   ((JTextField)field(panel,"taskSearch")).setText("两日");check(analysis.getItemCount()==0,"search cannot reintroduce finite plans");
   PlanWindow window=new PlanWindow(manager,null,config);window.setSize(1100,850);window.showCard("library");layout(window);
   JButton library=(JButton)field(window,"libraryBtn"),todayButton=(JButton)field(window,"todayBtn"),activity=(JButton)field(window,"activityBtn");
   check(library.getParent()!=todayButton.getParent()&&todayButton.getParent().getComponentCount()==4,"library is separate from four navigation pages");
   check(library.getParent().getComponentCount()==2,"archive action grouped with new plan");
   BufferedImage image=new BufferedImage(1100,850,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setColor(Theme.BG);graphics.fillRect(0,0,1100,850);window.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",out.resolve("management-header.png").toFile());
   library.doClick();check(!Boolean.TRUE.equals(activity.getClientProperty("rest.selected")),"library does not highlight statistics tab");
  }catch(Throwable ex){error[0]=ex;}});if(error[0]!=null){error[0].printStackTrace();System.exit(1);}
  System.out.println("PASS recurring-only analysis, recorded one/multi-day exclusions, search/opt-in, retained daily data and separate management actions");System.exit(0);
 }
}
