package com.eye.ui;

import com.eye.ui.theme.Theme;
import com.eye.ui.theme.Icons;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Full-screen rest presentation. Visibility, text and timing still come from CycleEngine. */
public class Overlay {
    private final JWindow window = new JWindow();
    private final ShieldPresentation presentation=new ShieldPresentation();
    private final JLabel label = new JLabel("", SwingConstants.CENTER);

    public Overlay() {
        window.setModalExclusionType(Dialog.ModalExclusionType.TOOLKIT_EXCLUDE);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(true);
        window.setBackground(Color.BLACK);
        // Preserve the existing translucent appearance; input enforcement waits for painting.
        if(window.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT))window.setOpacity(0.85f);

        label.setForeground(Color.WHITE);
        label.setFont(Theme.font(40));

        JPanel canvas=new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
                g.setPaint(new GradientPaint(0,0,new Color(0x173F34),getWidth(),getHeight(),new Color(0x0B2722)));
                g.fillRect(0,0,getWidth(),getHeight());
                double cx=getWidth()/2.0,cy=getHeight()/2.0-32;
                for(int i=0;i<6;i++) {
                    double r=225+i*63;
                    g.setColor(new Color(187,209,168,Math.max(6,21-i*3)));
                    g.setStroke(new BasicStroke(1));g.draw(new Ellipse2D.Double(cx-r,cy-r,r*2,r*2));
                }
                g.setColor(new Color(187,209,168,90));
                g.setFont(Theme.numbers(11));
                g.drawString("E Y E R E L A X   /   A MOMENT FOR YOURSELF",42,48);
                g.setFont(Theme.font(11));
                String footer=com.eye.i18n.I18n.text("专注有时，休息有时。");
                g.drawString(footer,(getWidth()-g.getFontMetrics().stringWidth(footer))/2,getHeight()-44);
                g.dispose();
            }
        };
        JPanel content=Theme.column();
        JLabel eye=new JLabel(Icons.appIcon(64));eye.setAlignmentX(Component.CENTER_ALIGNMENT);content.add(eye);
        content.add(Box.createVerticalStrut(25));
        JLabel eyebrow=Theme.label("T I M E   T O   R E S T",11,Theme.SAGE);eyebrow.setAlignmentX(Component.CENTER_ALIGNMENT);content.add(eyebrow);
        content.add(Box.createVerticalStrut(23));
        JLabel title=Theme.label(com.eye.i18n.I18n.text("让目光，去远一点的地方。"),37,new Color(0xF3F3DF));title.setAlignmentX(Component.CENTER_ALIGNMENT);content.add(title);
        content.add(Box.createVerticalStrut(13));
        JLabel subtitle=Theme.label(com.eye.i18n.I18n.text("暂时离开屏幕，给自己一点留白。"),15,new Color(0xAFC3B1));subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);content.add(subtitle);
        content.add(Box.createVerticalStrut(38));label.setAlignmentX(Component.CENTER_ALIGNMENT);content.add(label);
        content.add(Box.createVerticalStrut(22));
        JLabel hint=Theme.label(com.eye.i18n.I18n.text("松一松肩膀，慢慢呼吸。"),12,new Color(0x94AC9D));hint.setAlignmentX(Component.CENTER_ALIGNMENT);content.add(hint);
        canvas.add(content);
        // The overlay itself never exposes an action. Mouse events are intentionally swallowed here;
        // B-mode additionally installs native low-level hooks so input cannot reach other apps.
        java.awt.event.MouseAdapter swallowMouse = new java.awt.event.MouseAdapter() {};
        canvas.addMouseListener(swallowMouse);
        canvas.addMouseMotionListener(swallowMouse);
        window.setContentPane(canvas);
    }

    public boolean isPresented(){return presentation.ready();}
    public void afterPaint(Runnable action){presentation.afterPaint(action);}
    public void showOverlay(String text) {
        presentation.clear();
        window.setVisible(false);
        label.setText(text);
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        window.setBounds(bounds);
        if(!presentation.present(window))return;
        window.toFront();
        window.requestFocus();
    }

    public void updateText(String text) {
        label.setText(text);
        presentation.present(window);
    }

    public void hideOverlay() {
        presentation.clear();
        window.setVisible(false);
    }
}
