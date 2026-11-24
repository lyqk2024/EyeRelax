package com.eye.plan;

import com.eye.calendar.*;
import com.eye.reminder.*;
import com.eye.ui.theme.Theme;
import com.eye.i18n.I18n;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Offline calendar truth, scheduling compatibility, persisted reminders and real picker actions. */
public final class CalendarAcceptance {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static LocalDate date(String s){return LocalDate.parse(s);}
    private static long epoch(String s){return date(s).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private static LocalDate next(ReminderItem item,String after){item.advanceAfter(epoch(after));return Instant.ofEpochMilli(item.nextTriggerEpochMs).atZone(ZoneId.systemDefault()).toLocalDate();}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container p)layout(p);}
    private static <T> T field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(o);}
    private static JButton button(Container c,String name){for(Component child:c.getComponents()){if(child instanceof JButton b&&name.equals(b.getText()))return b;if(child instanceof Container p){JButton b=button(p,name);if(b!=null)return b;}}return null;}
    private static JComboBox<?> combo(Container c){for(Component child:c.getComponents()){if(child instanceof JComboBox<?> box)return box;if(child instanceof Container p){JComboBox<?> box=combo(p);if(box!=null)return box;}}return null;}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);System.setProperty("user.home",Files.createTempDirectory("EyeRelax-calendar-").toString());
        check(ChineseCalendar.festival(date("2026-10-18")).equals("重阳节"),"Chongyang lunar 9/9");
        check(ChineseCalendar.solarTerm(date("2026-10-08")).equals("寒露"),"Cold Dew");
        check(ChineseCalendar.solarTerm(date("2026-10-23")).equals("霜降"),"Frost Descent");
        check(ChineseCalendar.festival(date("2026-02-16")).equals("除夕"),"variable lunar last day");
        check(ChineseCalendar.festival(date("2026-02-17")).equals("春节"),"Spring Festival");
        for(int year=2023;year<=2028;year++){
            int terms=0;for(LocalDate d=LocalDate.of(year,1,1);d.getYear()==year;d=d.plusDays(1)){
                check(!ChineseCalendar.lunarDate(d).isEmpty(),"lunar completeness "+d);if(!ChineseCalendar.solarTerm(d).isEmpty())terms++;
            }check(terms==24,"24 terms "+year);
        }
        LocalDate holiday=date("2026-10-01"),makeup=date("2026-10-10"),weekend=date("2026-10-11"),ordinary=date("2026-10-12");
        check(!DayCategory.WORKDAY.matches(holiday)&&DayCategory.HOLIDAY.matches(holiday),"weekday holiday");
        check(DayCategory.WORKDAY.matches(makeup)&&!DayCategory.NON_WORKDAY.matches(makeup)&&!DayCategory.NORMAL_WORKDAY.matches(makeup),"adjusted Saturday");
        check(DayCategory.NON_WORKDAY.matches(weekend)&&!DayCategory.HOLIDAY.matches(weekend),"weekend is not public holiday break");
        check(DayCategory.NORMAL_WORKDAY.matches(ordinary),"ordinary weekday");
        check(!ChineseCalendar.hasHolidaySchedule(2027)&&!DayCategory.HOLIDAY.matches(date("2027-01-01")),"no fabricated future breaks");
        PlanSchedule schedule=new PlanSchedule(new HashSet<>());PlanItem plan=new PlanItem("calendar-test","Calendar",600,600);
        plan.createdDate = holiday;
        plan.recurringRule=PlanItem.RecurringRule.WORKDAY;check(schedule.matchRecurring(plan,makeup)&&!schedule.matchRecurring(plan,holiday),"new plan rule");
        plan.recurringRule=PlanItem.RecurringRule.WEEKDAY;check(!schedule.matchRecurring(plan,makeup)&&schedule.matchRecurring(plan,holiday),"legacy unchanged");
        ReminderItem reminder=new ReminderItem();reminder.kind=ReminderItem.ScheduleKind.DAILY;reminder.dayCategory=DayCategory.WORKDAY;reminder.time=LocalTime.of(9,0);
        check(next(reminder,"2026-09-30").equals(date("2026-10-08")),"skip entire National Day break");
        check(next(reminder,"2026-10-09").equals(makeup),"remind on adjusted Saturday");
        reminder.dayCategory=DayCategory.HOLIDAY;check(next(reminder,"2026-06-21").equals(date("2026-09-25")),"holiday search beyond one week");
        reminder.advanceAfter(epoch("2026-10-07"));check(!reminder.enabled&&reminder.nextTriggerEpochMs==0,"unknown future holidays stop safely");
        reminder.enabled=true;reminder.kind=ReminderItem.ScheduleKind.WEEKLY;reminder.dayCategory=DayCategory.WORKDAY;reminder.weekdays=EnumSet.of(DayOfWeek.SATURDAY);
        check(next(reminder,"2026-09-30").equals(makeup),"weekly + adjusted-day intersection");
        ReminderStore store=new ReminderStore(out.resolve("reminders.properties"));store.save(java.util.List.of(reminder));check(store.load().get(0).dayCategory==DayCategory.WORKDAY&&reminder.copy().dayCategory==DayCategory.WORKDAY,"persistence and copy");
        Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{try{
            Theme.install();
            for(String language:new String[]{"zh-CN","en"}){
                I18n.configure(language);var ctor=PlanDatePickerDialogs.MultiDatePickerDialog.class.getDeclaredConstructor(Window.class,Set.class,LocalDate.class);ctor.setAccessible(true);
                JDialog picker=ctor.newInstance(null,Set.of(date("2026-11-03")),date("2026-10-06"));
                var monthField=picker.getClass().getDeclaredField("visibleMonth");monthField.setAccessible(true);monthField.set(picker,YearMonth.of(2026,10));
                JComboBox box=combo(picker);box.setSelectedItem(DayCategory.WORKDAY);button(picker,I18n.text("选中本月")).doClick();
                Set<LocalDate> selected=field(picker,"selectedDates");check(selected.contains(makeup)&&!selected.contains(holiday)&&selected.contains(date("2026-11-03")),"month scope preserves other month");
                box.setSelectedItem(DayCategory.HOLIDAY);button(picker,I18n.text("选中本月")).doClick();check(selected.contains(holiday)&&!selected.contains(makeup)&&selected.size()==8,"replace visible month only");
                Container content=picker.getContentPane();content.setSize(picker.getWidth(),picker.getHeight());layout(content);BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();content.printAll(g);g.dispose();ImageIO.write(image,"png",out.resolve("calendar-"+language+".png").toFile());
                button(picker,I18n.text("清空本月")).doClick();check(selected.equals(Set.of(date("2026-11-03"))),"clear month scope");
                monthField.set(picker,YearMonth.of(2027,1));button(picker,I18n.text("选中本月")).doClick();check(selected.equals(Set.of(date("2026-11-03"))),"unknown holiday year preserves input");picker.dispose();
                var config=new com.eye.core.Config();var manager=new ReminderManager(new ReminderStore(out.resolve("editor-"+language+".properties")));
                ReminderPanel panel=new ReminderPanel(manager,config,new com.eye.core.CycleEngine(config),()->{});
                Class<?> editor=Arrays.stream(ReminderPanel.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Editor")).findFirst().orElseThrow();
                var constructor=editor.getDeclaredConstructor(ReminderPanel.class,ReminderItem.class);constructor.setAccessible(true);Object form=constructor.newInstance(panel,new ReminderItem());
                ((JTextField)field(form,"title")).setText("Calendar reminder");((JComboBox<?>)field(form,"kind")).setSelectedIndex(2);
                ((JComboBox<DayCategory>)field(form,"dailyCategory")).setSelectedItem(DayCategory.WORKDAY);
                var validate=editor.getDeclaredMethod("validate");validate.setAccessible(true);check((boolean)validate.invoke(form),"calendar reminder editor valid");
                ReminderItem prepared=field(form,"prepared");check(prepared.dayCategory==DayCategory.WORKDAY&&DayCategory.WORKDAY.matches(Instant.ofEpochMilli(prepared.nextTriggerEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()),"editor uses scheduling policy");
                JComponent body=field(form,"root");body.setSize(600,900);layout(body);BufferedImage reminderImage=new BufferedImage(600,900,BufferedImage.TYPE_INT_RGB);Graphics2D rg=reminderImage.createGraphics();rg.setColor(Theme.BG);rg.fillRect(0,0,600,900);body.printAll(rg);rg.dispose();ImageIO.write(reminderImage,"png",out.resolve("reminder-"+language+".png").toFile());manager.shutdown();
            }
        }catch(Throwable ex){failure[0]=ex;}});
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}
        System.out.println("PASS lunar/terms 2023-2028, official adjustment/holiday distinction, legacy rules, reminder recurrence/store and bilingual month-selection UI");System.exit(0);
    }
}
