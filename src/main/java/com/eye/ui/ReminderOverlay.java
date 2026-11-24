package com.eye.ui;

import com.eye.ui.theme.Icons;
import com.eye.ui.theme.Theme;
import com.eye.win.KeyboardBlocker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/** Full-screen reminder overlay that can only be dismissed manually after the configured hold time. */
public final class ReminderOverlay {
    private final JWindow window = new JWindow();
    private final ShieldPresentation presentation=new ShieldPresentation();
    private final JLabel title = Theme.heading(com.eye.i18n.I18n.text("提醒"), 30);
    private final JLabel countdown = Theme.label("", 15, new Color(0xC7D7CE));
    private final JButton close = new JButton(com.eye.i18n.I18n.text("完成提醒"));
    private final Timer timer;
    private final KeyboardBlocker keyboardBlocker = new KeyboardBlocker();
    private long unlockAtMs;
    private Runnable onClose;
    private boolean active;

    public ReminderOverlay() {
        keyboardBlocker.setPresentationGuard(presentation::ready);
        window.setModalExclusionType(Dialog.ModalExclusionType.TOOLKIT_EXCLUDE);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(true);
        window.setBackground(Color.BLACK);
        // Preserve the existing translucent appearance; input enforcement waits for painting.
        if(window.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT))window.setOpacity(0.94f);

        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { /* forced overlay: only the button may close it */ }
        });

        JPanel canvas = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create(); Theme.quality(g);
                g.setPaint(new GradientPaint(0,0,new Color(0x102F29),getWidth(),getHeight(),new Color(0x071D1A)));
                g.fillRect(0,0,getWidth(),getHeight());
                double cx=getWidth()/2.0, cy=getHeight()/2.0-18;
                for(int i=0;i<6;i++) {
                    double r=235+i*66;
                    g.setColor(new Color(187,209,168,Math.max(5,20-i*3)));
                    g.setStroke(new BasicStroke(1)); g.draw(new Ellipse2D.Double(cx-r,cy-r,r*2,r*2));
                }
                g.setColor(new Color(187,209,168,90));
                g.setFont(Theme.numbers(11));
                g.drawString("E Y E R E L A X   /   REMINDER",42,48);
                g.dispose();
            }
        };

        JPanel content=Theme.column();
        JLabel icon=new JLabel(Icons.appIcon(68)); icon.setAlignmentX(Component.CENTER_ALIGNMENT); content.add(icon);
        content.add(Box.createVerticalStrut(24));
        JLabel eyebrow=Theme.label("R E M I N D E R",11,Theme.SAGE); eyebrow.setAlignmentX(Component.CENTER_ALIGNMENT); content.add(eyebrow);
        content.add(Box.createVerticalStrut(22));
        title.setForeground(new Color(0xF4F4E6)); title.setAlignmentX(Component.CENTER_ALIGNMENT); content.add(title);
        content.add(Box.createVerticalStrut(14));
        countdown.setAlignmentX(Component.CENTER_ALIGNMENT); content.add(countdown);
        content.add(Box.createVerticalStrut(28));
        Theme.button(close,"primary","check");
        close.setAlignmentX(Component.CENTER_ALIGNMENT);
        close.setMaximumSize(new Dimension(230,46));
        close.addActionListener(e -> attemptClose());
        content.add(close);
        content.setBorder(new EmptyBorder(30,40,30,40));
        canvas.add(content);
        window.setContentPane(canvas);

        // Swallow Escape. JWindow has no frame decoration, so there is no close button to bypass the timer.
        JRootPane root = window.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"ignoreEscape");
        root.getActionMap().put("ignoreEscape",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){}});
        timer = new Timer(250,e -> refresh());
    }

    public boolean isActive() { return active; }

    public void show(String text, int holdSeconds, Runnable afterClose) {
        Runnable r = () -> {
            if (active) return;
            active = true;
            onClose = afterClose;
            title.setText(text == null || text.isBlank() ? com.eye.i18n.I18n.text("该停一下了。") : text.strip());
            unlockAtMs = System.currentTimeMillis() + Math.max(5, holdSeconds) * 1000L;
            Rectangle bounds = null;
            for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle b = device.getDefaultConfiguration().getBounds();
                bounds = bounds == null ? new Rectangle(b) : bounds.union(b);
            }
            if (bounds == null) bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            window.setBounds(bounds);
            refresh();
            boolean presented=presentation.present(window);
            window.toFront();
            window.requestFocus();
            if(presented)presentation.afterPaint(()->{if(active)keyboardBlocker.start();});
            timer.start();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }

    private void refresh() {
        if (!active) return;
        if(!presentation.present(window))keyboardBlocker.stop();
        else if(!keyboardBlocker.isActive())presentation.afterPaint(()->{if(active)keyboardBlocker.start();});
        long remain = Math.max(0, unlockAtMs - System.currentTimeMillis());
        boolean unlocked = remain <= 0;
        close.setEnabled(unlocked);
        if (unlocked) {
            close.setText(com.eye.i18n.I18n.text("完成提醒 · 关闭遮罩"));
            countdown.setText(com.eye.i18n.I18n.text("最低遮罩时间已满足，需要你手动关闭后才能继续。"));
        } else {
            close.setText(com.eye.i18n.I18n.text("暂时不能关闭"));
            countdown.setText(com.eye.i18n.I18n.text("还需 ") + fmt(remain) + com.eye.i18n.I18n.text(" · 到时后仍需手动关闭"));
        }
    }

    private void attemptClose() {
        if (!active || System.currentTimeMillis() < unlockAtMs) return;
        presentation.clear();
        active = false;
        timer.stop();
        keyboardBlocker.stop();
        window.setVisible(false);
        Runnable r = onClose; onClose = null;
        if (r != null) r.run();
    }

    private static String fmt(long ms) {
        long s=(ms+999)/1000, m=s/60; s%=60;
        return m>0?String.format("%d:%02d",m,s):String.format(com.eye.i18n.I18n.text("%d 秒"),s);
    }
}
