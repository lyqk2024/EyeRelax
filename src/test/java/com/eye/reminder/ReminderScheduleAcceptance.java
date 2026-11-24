package com.eye.reminder;
import com.eye.core.*;
import com.eye.ui.theme.*;
import javax.swing.*;
import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public final class ReminderScheduleAcceptance {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void layout(Container c){c.doLayout();for(Component x:c.getComponents())if(x instanceof Container p)layout(p);}
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[0]);Files.createDirectories(out);System.setProperty("user.home",Files.createTempDirectory("EyeRelax-reminder-schedules-").toString());
  if(args.length>1)com.eye.i18n.I18n.configure("en");
  long base=1_000_000L;ReminderItem interval=new ReminderItem();interval.kind=ReminderItem.ScheduleKind.INTERVAL;interval.intervalSeconds=3000;interval.nextTriggerEpochMs=base+3_000_000;
  interval.advanceAfter(interval.nextTriggerEpochMs);check(interval.nextTriggerEpochMs==base+6_000_000&&interval.enabled,"50 minute repeat");
  interval.advanceAfter(base+20_000_000);check(interval.nextTriggerEpochMs==base+21_000_000,"missed intervals skipped without drift");
  ReminderItem sequence=new ReminderItem();sequence.kind=ReminderItem.ScheduleKind.SEQUENCE;sequence.nextTriggerEpochMs=base+300_000;
  long[] expected={base+900_000,base+2_100_000,base+3_900_000};for(long time:expected){sequence.advanceAfter(sequence.nextTriggerEpochMs);check(sequence.nextTriggerEpochMs==time&&sequence.enabled,"cumulative sequence");}
  sequence.advanceAfter(sequence.nextTriggerEpochMs);check(!sequence.enabled&&sequence.sequenceIndex==4,"sequence stops after four");
  ReminderStore store=new ReminderStore(out.resolve("reminders.properties"));store.save(java.util.List.of(interval,sequence));var saved=store.load();check(saved.size()==2,"store count");var restored=saved.stream().filter(i->i.id.equals(sequence.id)).findFirst().orElseThrow();check(restored.sequenceIndex==4&&restored.sequenceSeconds.equals(sequence.sequenceSeconds)&&!restored.enabled,"persisted sequence progress");restored.sequenceSeconds.set(0,1);check(sequence.sequenceSeconds.get(0)==300,"copy independence");
  ReminderManager manager=new ReminderManager(store);manager.setEnabled(sequence.id,true);check(manager.get(sequence.id).sequenceIndex==0&&manager.get(sequence.id).nextTriggerEpochMs>System.currentTimeMillis(),"completed sequence restarts on enable");manager.setEnabled(interval.id,true);check(manager.get(interval.id).nextTriggerEpochMs>System.currentTimeMillis(),"interval enable restarts delay");manager.shutdown();
  ReminderItem missed=new ReminderItem();missed.kind=ReminderItem.ScheduleKind.SEQUENCE;missed.nextTriggerEpochMs=base+300_000;missed.advanceAfter(base+2_000_000);check(missed.sequenceIndex==2&&missed.nextTriggerEpochMs==base+2_100_000,"skip missed sequence occurrences");
  ReminderManager overdueManager=new ReminderManager(new ReminderStore(out.resolve("overdue.properties")));ReminderItem overdue=new ReminderItem();overdue.kind=ReminderItem.ScheduleKind.SEQUENCE;overdue.nextTriggerEpochMs=System.currentTimeMillis()-10_000_000;overdueManager.upsert(overdue);int[] fired={0};overdueManager.setDueHandler(item->{check(item.sequenceIndex==0,"delivered occurrence retains its index");fired[0]++;});var tick=ReminderManager.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(overdueManager);tick.invoke(overdueManager);check(fired[0]==1&&!overdueManager.get(overdue.id).enabled,"no catch-up burst or duplicate after expired sequence");overdueManager.shutdown();
  Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{try{
   Theme.install();Config config=new Config();var reminders=new ReminderManager(new ReminderStore(out.resolve("ui.properties")));ReminderPanel panel=new ReminderPanel(reminders,config,new CycleEngine(config),()->{});
   Class<?> editor=Arrays.stream(ReminderPanel.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Editor")).findFirst().orElseThrow();var constructor=editor.getDeclaredConstructor(ReminderPanel.class,ReminderItem.class);constructor.setAccessible(true);Object instance=constructor.newInstance(panel,new ReminderItem());
   ((JTextField)field(instance,"title")).setText("喝水 · user content");var kind=(JComboBox<?>)field(instance,"kind");var validate=editor.getDeclaredMethod("validate");validate.setAccessible(true);
   kind.setSelectedIndex(4);check((boolean)validate.invoke(instance),"interval form valid");ReminderItem prepared=(ReminderItem)field(instance,"prepared");check(prepared.kind==ReminderItem.ScheduleKind.INTERVAL&&prepared.intervalSeconds==3000,"50 minute default");
   var duration=(DurationInput)field(instance,"interval");duration.setSeconds(0);check(!(boolean)validate.invoke(instance),"invalid interval stays in editor");check(((JTextField)field(instance,"title")).getText().equals("喝水 · user content"),"validation preserves entered content");duration.setSeconds(3000);
   kind.setSelectedIndex(5);check((boolean)validate.invoke(instance),"sequence form valid");prepared=(ReminderItem)field(instance,"prepared");check(prepared.sequenceSeconds.equals(java.util.List.of(300,600,1200,1800)),"four editable delays");
   JComponent root=(JComponent)field(instance,"root");InlineErrors.clear(root);root.setSize(520,1000);layout(root);BufferedImage image=new BufferedImage(520,1000,2);var g=image.createGraphics();g.setColor(Theme.SURFACE);g.fillRect(0,0,520,1000);root.paint(g);g.dispose();ImageIO.write(image,"png",out.resolve(args.length>1?"sequence-editor-english.png":"sequence-editor.png").toFile());
   reminders.shutdown();
  }catch(Throwable ex){failure[0]=ex;}});if(failure[0]!=null)throw new AssertionError(failure[0]);
  System.out.println("PASS recurring intervals, cumulative sequence, skipped overdue occurrences, completion, persistence, re-enable and editor inputs");System.exit(0);
 }
}
