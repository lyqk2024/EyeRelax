package com.eye.ui;

import com.eye.core.ComputerUsageLimits;
import com.eye.core.UsageLimitMonitor;
import com.eye.ui.theme.Theme;
import com.eye.win.KeyboardBlocker;
import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** All-monitor usage-limit shield. Only shutdown is available while blocked. */
public final class UsageLimitOverlay implements UsageLimitMonitor.Actions {
    private final List<JFrame> screens=new ArrayList<>();
    private final List<JLabel> messages=new ArrayList<>();
    private final List<JLabel> nextTimes=new ArrayList<>();
    private final KeyboardBlocker keyboard=new KeyboardBlocker();
    private final ShieldPresentation presentation=new ShieldPresentation();
    private Runnable onShutdown=()->{};
    private final Runnable onBlock;
    public UsageLimitOverlay(Runnable onBlock) {this.onBlock=onBlock;keyboard.setPresentationGuard(presentation::ready);}
    public void setShutdownAction(Runnable action){onShutdown=action;}
    @Override public void show(ComputerUsageLimits.Status status) {
        if(screens.isEmpty()) {
            onBlock.run();
            for(GraphicsDevice device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                JFrame frame=new JFrame(com.eye.i18n.I18n.text("EyeRelax · 使用时间限制"),device.getDefaultConfiguration());
                frame.setModalExclusionType(Dialog.ModalExclusionType.TOOLKIT_EXCLUDE);frame.setUndecorated(true);frame.setAlwaysOnTop(true);
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                JPanel body=new JPanel(new GridBagLayout());body.setBackground(Theme.DARK);
                JPanel copy=new JPanel();copy.setOpaque(false);copy.setLayout(new BoxLayout(copy,BoxLayout.Y_AXIS));
                JLabel title=Theme.heading(com.eye.i18n.I18n.text("电脑使用限制"),30);title.setForeground(Color.WHITE);
                JLabel reason=Theme.label("",18,Color.WHITE);JLabel next=Theme.label("",14,Theme.SAGE);
                JButton power=new JButton(com.eye.i18n.I18n.text("关机"));Theme.button(power,"primary","play");
                power.addActionListener(e->{
                    if(JOptionPane.showConfirmDialog(frame,com.eye.i18n.I18n.text("确认关闭电脑？请先保存其他程序中的工作。"),com.eye.i18n.I18n.text("关机"),
                            JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE)==JOptionPane.YES_OPTION)onShutdown.run();
                });
                for(JComponent c:new JComponent[]{title,reason,next,power}){c.setAlignmentX(Component.CENTER_ALIGNMENT);copy.add(c);copy.add(Box.createVerticalStrut(24));}
                body.add(copy);frame.setContentPane(body);frame.setBounds(device.getDefaultConfiguration().getBounds());
                screens.add(frame);messages.add(reason);nextTimes.add(next);frame.setVisible(true);
            }
            // Install hooks only after every monitor shield has been painted.
        }
        String reason=switch(status.reason()) {
            case DAILY_BUDGET -> com.eye.i18n.I18n.text("今日电脑使用额度已用完，重启不会重置。");
            case OUTSIDE_WINDOW -> com.eye.i18n.I18n.text("现在不在允许使用电脑的时段内。");
            case STORAGE_ERROR -> status.error();
            default -> "";
        };
        String next=status.nextAllowed()==null?com.eye.i18n.I18n.text("限制将持续保留。"):
                com.eye.i18n.I18n.text("下次可用：")+status.nextAllowed().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        boolean presented=true;
        for(int i=0;i<screens.size();i++){
            messages.get(i).setText(reason);nextTimes.get(i).setText(next);
            JFrame frame=screens.get(i);if(!frame.isVisible())frame.setVisible(true);
            // Never steal focus from our shutdown confirmation dialog.
            boolean dialog=false;for(Window owned:frame.getOwnedWindows())dialog|=owned.isShowing();
            if(!presentation.present(frame))presented=false;
            if(!dialog){frame.toFront();frame.requestFocus();}
        }
        if(presented)presentation.afterPaint(keyboard::start);else {presentation.clear();keyboard.stop();}
    }
    @Override public void hide(){presentation.clear();keyboard.stop();for(JFrame frame:screens)frame.dispose();screens.clear();messages.clear();nextTimes.clear();}
    @Override public void shutdown(){
        try {
            String system=System.getenv("SystemRoot");if(system==null)throw new IllegalStateException(com.eye.i18n.I18n.text("无法定位 Windows 系统目录。"));
            new ProcessBuilder(system+"\\System32\\shutdown.exe","/s","/t","0").start();
        } catch(Exception ex){JOptionPane.showMessageDialog(screens.isEmpty()?null:screens.get(0),com.eye.i18n.I18n.text("关机未能启动：")+ex.getMessage(),com.eye.i18n.I18n.text("关机"),JOptionPane.ERROR_MESSAGE);}
    }
}
