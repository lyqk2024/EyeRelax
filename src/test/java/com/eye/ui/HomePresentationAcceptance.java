package com.eye.ui;

import com.eye.core.Config;
import com.eye.core.CycleEngine;
import com.eye.i18n.I18n;
import com.eye.plan.PlanItem;
import com.eye.plan.PlanManager;
import com.eye.ui.theme.Theme;
import com.eye.ui.theme.TimerDial;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Renders the real dashboard with an isolated profile and a stationary clock. */
public final class HomePresentationAcceptance {
    private enum Phase { IDLE, WORK, PAUSED, REST, REMINDER }

    private static final class ClockFixture extends CycleEngine {
        Phase phase = Phase.IDLE;
        ClockFixture(Config config) { super(config); }
        @Override public boolean isRunning() { return isInWork() || isInRelax(); }
        @Override public boolean isInWork() { return phase == Phase.WORK; }
        @Override public boolean isInRelax() { return phase == Phase.REST; }
        @Override public boolean hasPausedSnapshot() { return phase == Phase.PAUSED; }
        @Override public boolean canResumeFromPause() { return hasPausedSnapshot(); }
        @Override public boolean isReminderActive() { return phase == Phase.REMINDER; }
        @Override public boolean canPauseNow() { return isInWork(); }
        @Override public long getWorkRemainingMs() { return 404_000; }
    }

    private static String translated(String chinese) {
        String text = I18n.text(chinese);
        if (I18n.isEnglish()) check(!text.equals(chinese), "missing English translation: " + chinese);
        return text;
    }

    private static boolean hasLabel(Container parent, String text) {
        for (Component c : parent.getComponents()) {
            if (!c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) continue;
            if (c instanceof JLabel label && text.equals(label.getText())) return true;
            if (c instanceof Container child && hasLabel(child, text)) return true;
        }
        return false;
    }

    private static void checkState(ControlWindow window, String state, String dialValue,
                                   String caption, String phase, String hint) throws Exception {
        window.refreshButtons();
        settle(window.getContentPane());
        TimerDial dial = (TimerDial)field(window,"dial");
        JLabel status = (JLabel)field(window,"stateLabel");
        JLabel description = (JLabel)field(window,"clockDescription");
        check(translated(state).equals(status.getText()), "state badge: " + state);
        String spoken = dial.getAccessibleContext().getAccessibleName();
        check(spoken.contains(dialValue) && spoken.contains(translated(caption)), "dial value and caption: " + phase);
        check(phase.equals(dial.getAccessibleContext().getAccessibleDescription()), "dial phase: " + phase);
        check(description.isVisible() && description.getHeight() > 0 && translated(hint).equals(description.getText()),
                "visible guidance: " + phase);
    }

    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object field(Object object,String name)throws Exception{
        var f=ControlWindow.class.getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container p)layout(p);}
    private static void settle(Container parent){for(int n=0;n<6;n++)layout(parent);}
    private static void capture(JComponent root,Path file)throws Exception{
        BufferedImage image=new BufferedImage(root.getWidth(),root.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();root.paint(g);g.dispose();ImageIO.write(image,"png",file.toFile());
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("EyeRelax-home-v20-").toString());
        if(args.length>1)com.eye.i18n.I18n.configure(args[1]);
        Path out=Path.of(args[0]);Files.createDirectories(out);Throwable[] failure={null};
        SwingUtilities.invokeAndWait(()->{
            ControlWindow window=null;
            try{
                Theme.install();Config config=new Config();config.workSeconds=2700;config.relaxSeconds=300;
                PlanManager plans=new PlanManager(config);plans.loadToday();
                PlanItem plan=plans.addPlan("阅读 · 一段不被打扰的时间",5400);
                plans.consumeSeconds(plan.id,1800);
                ClockFixture engine=new ClockFixture(config);engine.setCurrentPlanId(plan.id);
                int[] clicks={0};
                window=new ControlWindow(engine,config,plans,()->{},()->clicks[0]++,()->{},()->{},()->clicks[0]++,b->{});
                JComponent root=(JComponent)window.getContentPane();
                int[] widths={900,1064,1800},heights={620,701,1050};
                for(int i=0;i<widths.length;i++){
                    window.setSize(widths[i]+16,heights[i]+39);var adapt=ControlWindow.class.getDeclaredMethod("adaptSidebar");adapt.setAccessible(true);adapt.invoke(window);root.setSize(widths[i],heights[i]);settle(root);
                    TimerDial dial=(TimerDial)field(window,"dial");
                    check(hasLabel(root,translated("专注当下，也关照自己。")), "homepage heading at " + widths[i]);
                    check(hasLabel(root,translated("让工作有节奏，让眼睛有片刻留白。")), "homepage introduction at " + widths[i]);
                    JLabel state=(JLabel)field(window,"stateLabel");
                    check(hasLabel(state.getParent(),translated("专注计时")), "title inside the timer card at " + widths[i]);
                    checkState(window, "尚未开始", "45:00", "准备好，再开始", "READY TO FOCUS", "准备好后，开启一段专注时光。");
                    check(dial.getWidth()>200&&dial.getHeight()>200,"dial remains readable at "+widths[i]);
                    JButton start=(JButton)field(window,"startBtn"),rest=(JButton)field(window,"restNowBtn");
                    check(start.getHeight()>=30&&rest.getHeight()>=24,"actions retain usable targets at "+widths[i]);
                    JLabel mode=(JLabel)field(window,"modeValue");
                    check(mode.getWidth()>=mode.getPreferredSize().width,"complete mode label at "+widths[i]);
                    capture(root,out.resolve("focus-"+widths[i]+".png"));
                }
                ((JButton)field(window,"startBtn")).doClick();check(clicks[0]==1,"start callback preserved");
                JComponent planCard=(JComponent)field(window,"currentPlanCard");
                check(hasLabel(planCard,translated("点击这里更换或选择任务")), "plan chooser guidance");
                check("open".equals(planCard.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,0))),"current plan keyboard entry available");
                planCard.getActionMap().get("open").actionPerformed(new java.awt.event.ActionEvent(planCard,0,"open"));
                check(clicks[0]==2,"keyboard plan choice uses existing callback");
                Object progress=field(window,"planProgress");var update=progress.getClass().getDeclaredMethod("update",PlanItem.class);update.setAccessible(true);
                var fraction=progress.getClass().getDeclaredField("fraction");fraction.setAccessible(true);
                PlanItem extended=plans.extendToday(plan.id,1800);update.invoke(progress,extended);
                check(Math.abs(fraction.getDouble(progress)-.25)<.00001,"temporary extension counted once in task progress");
                window.setSize(1080,740);root.setSize(1064,701);settle(root);
                engine.phase = Phase.WORK;
                checkState(window, "工作中", "06:44", "本轮工作剩余", "FOCUS IN PROGRESS", "把注意力，留给眼前这一件事。");
                capture(root,out.resolve("focus-progress.png"));
                engine.phase = Phase.PAUSED;
                checkState(window, "已暂停", translated("已暂停"), "点击开始，继续这一段", "TAKE YOUR TIME", "暂停进度已保留，也可以重新开始。");
                capture(root,out.resolve("focus-paused.png"));
                engine.phase = Phase.REST;
                checkState(window, "休息中", translated("休息中"), "把这一刻，留给自己", "TIME TO REST", "剩余休息时间可查看悬浮条或休息遮罩。");
                capture(root,out.resolve("focus-rest.png"));
                engine.phase = Phase.REMINDER;
                checkState(window, "提醒中", translated("提醒中"), "遮罩期间计入休息", "REMINDER", "完成最低遮罩时间后，需要手动关闭提醒才能继续。");
                engine.setCurrentPlanId(null);
                window.refreshButtons();
                settle(root);
                check(hasLabel(planCard,translated("未关联工作计划")), "free focus shows that there is no linked plan");
                System.out.println("HomePresentationAcceptance: "+checks+" checks passed; screenshots: "+out);
            }catch(Throwable problem){failure[0]=problem;}finally{if(window!=null)window.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
    }
}
