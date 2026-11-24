package com.eye.ui;
import com.eye.core.*;
import com.eye.plan.*;
import com.eye.reminder.*;
import com.eye.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
public final class SettingsScaleAcceptance {
 static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container cc)layout(cc);}
 static JButton button(Container c,String text){for(Component child:c.getComponents()){if(child instanceof JButton b&&text.equals(b.getText()))return b;if(child instanceof Container p){JButton b=button(p,text);if(b!=null)return b;}}return null;}
 public static void main(String[] args)throws Exception{
  System.setProperty("user.home",Files.createTempDirectory("EyeRelax-settings-scale-").toString());Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{ControlWindow w=null;try{
   Theme.install();Config c=new Config();PlanManager plans=new PlanManager(c);plans.loadToday();w=new ControlWindow(new CycleEngine(c),c,plans,new ReminderManager(),()->{},()->{},()->{},()->{},()->{},v->{});w.setSize(1080,740);w.requestSettingsPage();ProportionalPanel root=(ProportionalPanel)w.getContentPane();Object settings=field(w,"settingsPage");SettingsUi.View view=(SettingsUi.View)field(settings,"unifiedSettings");
   for(int width:new int[]{1064,2128,1596,1064,2128}){
    root.setSize(width,width*701/1064);layout(root);double scale=root.getScale();
    for(String id:new String[]{"startup","input","updates","day","storage","rhythm"}){
     view.select(id);layout(root);var buttons=(java.util.List<?>)field(view,"buttons");for(Object item:buttons){JButton b=(JButton)item;check(Math.abs(b.getFont().getSize2D()-12*scale)<0.1,"font scale after switching "+id+" "+b.getText());}
    }
    JButton open=button(view,"打开启动文件夹"),checkStartup=button(view,"检查启动入口");check(open!=null&&checkStartup!=null,"startup actions");check(Math.abs(open.getPreferredSize().height-checkStartup.getPreferredSize().height)<=2,"consistent action heights");check(open.getPreferredSize().height<=40*scale,"startup action bounded height");
    JButton boundary=(JButton)field(settings,"dayBoundaryEditBtn");check(Math.abs(boundary.getPreferredSize().height-34*scale)<=1,"duration size does not grow");
   }
   var label=ActivityPanel.class.getDeclaredMethod("dayNavigationLabel",java.time.LocalDate.class,java.time.LocalDate.class);label.setAccessible(true);var today=java.time.LocalDate.of(2026,10,6);check(label.invoke(null,today,today).equals("今天"),"today");check(label.invoke(null,today.minusDays(1),today).equals("昨天"),"yesterday");check(label.invoke(null,today.minusDays(2),today).equals("前天"),"two days ago");check(label.invoke(null,today.minusDays(3),today).equals("2026-10-03"),"earlier date");
   System.out.println("PASS repeated window resizing, settings switches, retained fonts, bounded button sizes and relative date labels");
  }catch(Throwable ex){failure[0]=ex;}finally{if(w!=null)w.dispose();}});if(failure[0]!=null)throw new AssertionError(failure[0]);System.exit(0);
 }
}
