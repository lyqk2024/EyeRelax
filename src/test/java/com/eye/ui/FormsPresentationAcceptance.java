package com.eye.ui;

import com.eye.core.*;
import com.eye.plan.*;
import com.eye.reminder.*;
import com.eye.ui.theme.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.*;
import java.util.List;

/** Real settings/reminder surfaces, synthetic schedules and an isolated profile. No engine is started. */
public final class FormsPresentationAcceptance {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container cc)layout(cc);}
    private static void invalidateTree(Container c){for(Component child:c.getComponents())if(child instanceof Container cc)invalidateTree(cc);c.invalidate();}
    private static void settle(Container c){for(int n=0;n<8;n++){invalidateTree(c);layout(c);}}
    private static void capture(JComponent c,Path target)throws Exception{settle(c);BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Theme.BG);g.fillRect(0,0,image.getWidth(),image.getHeight());c.paint(g);g.dispose();ImageIO.write(image,"png",target.toFile());}
    @FunctionalInterface private interface EdtAction {void run()throws Exception;}
    private static void edt(EdtAction action)throws Exception{Throwable[] error={null};SwingUtilities.invokeAndWait(()->{try{action.run();}catch(Throwable ex){error[0]=ex;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private static void drain()throws Exception{for(int pass=0;pass<5;pass++)SwingUtilities.invokeAndWait(()->{});}
    private static void render(JComponent component,Path target)throws Exception{
        // Let the same deferred layout/centering work used in a displayed Swing window
        // finish; holding every screenshot inside one EDT event would suppress it.
        edt(()->settle(component));drain();edt(()->settle(component));drain();edt(()->capture(component,target));
    }
    private static void checkWheelCenter(DurationInput input)throws Exception{
        for(String name:new String[]{"hourWheel","minuteWheel","secondWheel"}){JList<?> list=(JList<?>)field(input,name);JViewport viewport=(JViewport)list.getParent();Rectangle cell=list.getCellBounds(list.getSelectedIndex(),list.getSelectedIndex());int center=cell.y+cell.height/2-viewport.getViewPosition().y;check(Math.abs(center-viewport.getExtentSize().height/2)<=2,"wheel centers selected value: "+name);check(list.getWidth()==viewport.getExtentSize().width,"wheel width tracks narrow column: "+name);}
    }
    private static ReminderItem reminder(String title,ReminderItem.ScheduleKind kind,int minutes){ReminderItem i=new ReminderItem();i.title=title;i.kind=kind;i.nextTriggerEpochMs=Instant.now().plusSeconds(minutes*60L).toEpochMilli();return i;}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("EyeRelax-forms-v21-").toString());Path out=Path.of(args[0]);Files.createDirectories(out);Throwable[] failure={null};
        try{
            for(String locale:new String[]{"zh-CN","en"}){
                ApplicationSettingsPanel[] holder={null};ReminderPanel[] reminderHolder={null};
                Config config=new Config();config.timeInputMode=Config.TimeInputMode.WHEEL;config.launchAtLogin=false;config.workSeconds=2700;config.relaxSeconds=300;
                CycleEngine engine=new CycleEngine(config);
                edt(()->{
                com.eye.i18n.I18n.configure(locale);Theme.install();PlanManager plans=new PlanManager(config);plans.loadToday();
                ApplicationSettingsPanel settings=new ApplicationSettingsPanel(config,engine,plans,()->{},b->{},()->{},()->{},()->{},()->{});
                settings.refreshPreferenceFields();settings.refreshSystemFields();holder[0]=settings;});
                ApplicationSettingsPanel settings=holder[0];SettingsUi.View view=(SettingsUi.View)field(settings,"unifiedSettings");
                for(int width:new int[]{700,870,1050}){
                    edt(()->settings.setSize(width,760));
                    for(String section:new String[]{"rhythm","startup","input","tutorial","usage-limits"}){edt(()->view.select(section));render(settings,out.resolve("settings-"+locale+"-"+section+"-"+width+".png"));}
                    edt(()->{for(Object item:(List<?>)field(view,"buttons")){JButton b=(JButton)item;check(b.getWidth()>=b.getPreferredSize().width,"settings nav full label "+locale+" "+width+" "+b.getText());}});
                }
                edt(()->{view.select("rhythm");settings.setSize(870,760);settle(settings);});drain();
                edt(()->{
                DurationInput work=(DurationInput)field(settings,"workDurationInput");JComponent region=(JComponent)work.getClientProperty("tutorial.region");check(region!=null&&region.getHeight()>work.getHeight(),"work tutorial region includes heading");
                checkWheelCenter(work);check(work.getSeconds()==2700,"wheel presentation preserves duration");
                JRadioButton a=(JRadioButton)field(settings,"modeA"),b=(JRadioButton)field(settings,"modeB");a.doClick();check(a.isSelected()&&!b.isSelected(),"mode group selection");b.doClick();check(b.isSelected()&&!a.isSelected(),"mode switching");
                Container option=(Container)a.getClientProperty("tutorial.region");JPanel copy=(JPanel)((BorderLayout)option.getLayout()).getLayoutComponent(BorderLayout.CENTER);Component title=copy.getComponent(0);title.dispatchEvent(new MouseEvent(title,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,2,2,1,false));check(a.isSelected(),"mode title click selects whole option");
                });
                ReminderManager manager=new ReminderManager();
                edt(()->{
                for(ReminderItem prior:manager.list())manager.delete(prior.id);
                ReminderItem first=reminder(locale.equals("en")?"Drink some water and give your eyes a short break":"喝水 · 让眼睛离开屏幕片刻",ReminderItem.ScheduleKind.INTERVAL,12);manager.upsert(first);
                ReminderItem next=reminder(locale.equals("en")?"Read the weekly review and organize next week's tasks":"整理本周记录并安排下周的任务，这是一个非常长的提醒名称",ReminderItem.ScheduleKind.DAILY,35);next.pinned=true;manager.upsert(next);
                ReminderItem disabled=reminder("Disabled reminder",ReminderItem.ScheduleKind.DAILY,55);disabled.enabled=false;manager.upsert(disabled);
                ReminderItem archived=reminder("Archived reminder",ReminderItem.ScheduleKind.ONCE,60);archived.enabled=false;archived.lifecycle=ReminderItem.Lifecycle.COMPLETED;archived.archivedAtEpochMs=System.currentTimeMillis();manager.upsert(archived);
                reminderHolder[0]=new ReminderPanel(manager,config,engine,()->{});});ReminderPanel reminders=reminderHolder[0];
                for(int width:new int[]{700,870,1050}){edt(()->reminders.setSize(width,760));render(reminders,out.resolve("reminders-"+locale+"-"+width+".png"));edt(()->{JToggleButton[] tabs=(JToggleButton[])field(reminders,"filters");for(JToggleButton tab:tabs)check(tab.getWidth()>=tab.getPreferredSize().width,"reminder tab label "+locale+" "+width+" "+tab.getText());});}
                edt(()->{
                JToggleButton[] tabs=(JToggleButton[])field(reminders,"filters");tabs[1].doClick();check(tabs[1].isSelected(),"disabled reminders filter");tabs[2].doClick();check(tabs[2].isSelected(),"archive filter");tabs[0].doClick();
                ((JTextField)field(reminders,"search")).setText("missing search");});render(reminders,out.resolve("reminders-"+locale+"-empty.png"));edt(()->{((JTextField)field(reminders,"search")).setText("");check(((JPanel)field(reminders,"list")).getComponentCount()==2,"search restores active reminders");manager.shutdown();});
            }
            System.out.println("FormsPresentationAcceptance: "+checks+" checks passed; "+out);
        }catch(Throwable ex){failure[0]=ex;}
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
    }
}
