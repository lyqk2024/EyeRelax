package com.eye.ui;
import com.eye.i18n.I18n;
import com.eye.core.*;
import com.eye.plan.*;
import com.eye.reminder.*;
import com.eye.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public final class LanguageAcceptance {
 static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container cc)layout(cc);}
 static void capture(JComponent c,Path p)throws Exception{layout(c);var b=new BufferedImage(c.getWidth(),c.getHeight(),2);var g=b.createGraphics();c.paint(g);g.dispose();ImageIO.write(b,"png",p.toFile());}
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[0]);Files.createDirectories(out);System.setProperty("user.home",Files.createTempDirectory("EyeRelax-language-").toString());
  Config config=new Config();config.language="en";config.save();Config loaded=new Config();loaded.loadIfExists();check(loaded.language.equals("en"),"language persistence");
  I18n.configure(loaded.language);check(I18n.text("专注计时").equals("Focus timer"),"English resource");check(I18n.text("用户自己的计划").equals("用户自己的计划"),"user content unchanged");check(I18n.text("日").equals("Sun"),"Sunday label");
  Throwable[] error={null};SwingUtilities.invokeAndWait(()->{ControlWindow w=null;try{
   Theme.install();PlanManager manager=new PlanManager(config);manager.loadToday();w=new ControlWindow(new CycleEngine(config),config,manager,new ReminderManager(),()->{},()->{},()->{},()->{},()->{},v->{});
   w.setSize(1080,740);w.getContentPane().setSize(1064,701);capture((JComponent)w.getContentPane(),out.resolve("english-focus.png"));w.requestSettingsPage();capture((JComponent)w.getContentPane(),out.resolve("english-settings.png"));w.showPlanPage();w.showReminderPage();
   ActivityPanel activity=new ActivityPanel(manager,config);activity.setSize(1150,740);layout(activity);
   Object heat=field(activity,"heatmap");var set=heat.getClass().getDeclaredMethod("setData",java.time.LocalDate.class,java.time.LocalDate.class,java.util.Map.class);set.setAccessible(true);
   var geometry=heat.getClass().getDeclaredMethod("gridGeometry",int.class);geometry.setAccessible(true);
   JPanel column=(JPanel)field(activity,"column");Container card=(Container)column.getComponent(0);JPanel top=(JPanel)((BorderLayout)card.getLayout()).getLayoutComponent(BorderLayout.NORTH);
   for(int days:new int[]{30,240,300}){var end=java.time.LocalDate.of(2026,10,6);set.invoke(heat,end.minusDays(days-1),end,java.util.Map.of());layout(activity);top.doLayout();Object grid=geometry.invoke(heat,top.getWidth());var right=grid.getClass().getDeclaredMethod("right");right.setAccessible(true);Component slider=((BorderLayout)top.getLayout()).getLayoutComponent(BorderLayout.EAST);check(slider.getX()+slider.getWidth()==(int)right.invoke(grid),"slider aligned at "+days);}
   capture(activity,out.resolve("english-heatmap.png"));I18n.configure("zh-CN");check(I18n.text("日").equals("日"),"Chinese Sunday");
   System.out.println("PASS language persistence, English resources, user content, navigation, Sunday and slider/grid alignment at 30/240/300 days");
  }catch(Throwable ex){error[0]=ex;}finally{if(w!=null)w.dispose();}});if(error[0]!=null){error[0].printStackTrace();System.exit(1);}System.exit(0);
 }
}
