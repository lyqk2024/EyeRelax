package com.eye.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** A read-only preview of the configured cycle, not a second timer or statistics source. */
public final class RhythmPreview extends JComponent {
    private int workSeconds = -1, restSeconds = -1;
    private boolean automatic;
    public RhythmPreview() { setMinimumSize(new Dimension(244,160)); }
    public void update(int work,int rest,boolean automatic) {
        work=Math.max(0,work);rest=Math.max(0,rest);
        if(workSeconds==work&&restSeconds==rest&&this.automatic==automatic)return;
        workSeconds=work;restSeconds=rest;this.automatic=automatic;
        setToolTipText(t("工作")+" "+duration(work)+" · "+t("休息")+" "+duration(rest));
        repaint();
    }
    private static String t(String text){return com.eye.i18n.I18n.text(text);}
    private static String duration(int seconds){return seconds>=3600?String.format("%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format("%02d:%02d",seconds/60,seconds%60);}
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
        double scale=ProportionalPanel.scaleOf(this);g.scale(scale,scale);
        int w=(int)(getWidth()/scale),h=(int)(getHeight()/scale);
        g.setColor(Theme.DARK);g.fill(new RoundRectangle2D.Double(0,0,w,h,18,18));
        g.setPaint(new GradientPaint(0,0,Theme.alpha(Theme.GREEN_HOVER,0),w,h,Theme.alpha(Theme.GREEN_HOVER,90)));
        g.fill(new RoundRectangle2D.Double(0,0,w,h,18,18));
        g.setColor(Color.WHITE);g.setFont(Theme.bold(13));g.drawString(t("每轮节奏"),20,29);
        Icons.of("restart",16,new Color(0xB6CCBE)).paintIcon(this,g,w-36,16);
        int barY=48,barW=Math.max(1,w-40);double total=Math.max(1,(double)workSeconds+restSeconds);
        int workW=(int)Math.round(barW*Math.max(0,workSeconds)/total);
        Shape old=g.getClip();g.clip(new RoundRectangle2D.Double(20,barY,barW,8,8,8));
        g.setColor(new Color(0xA9C6B0));g.fillRect(20,barY,barW,8);
        g.setColor(new Color(0xEDF5E9));g.fillRect(20,barY,workW,8);g.setClip(old);
        int middle=w/2;
        g.setFont(Theme.font(10));g.setColor(new Color(0xC2D5C9));
        g.drawString(t("工作"),20,78);g.drawString(t("休息"),middle+4,78);
        g.setFont(Theme.numbers(22));g.setColor(Color.WHITE);
        g.drawString(duration(Math.max(0,workSeconds)),20,105);g.drawString(duration(Math.max(0,restSeconds)),middle+4,105);
        g.setColor(new Color(0x365D4E));g.drawLine(20,h-38,w-20,h-38);
        g.setFont(Theme.font(10));g.setColor(new Color(0xCADDD0));
        String label=t(automatic?"休息后自动开始下一轮":"休息后手动开始下一轮");
        while(g.getFontMetrics().stringWidth(label)>barW&&g.getFont().getSize2D()>8)g.setFont(g.getFont().deriveFont(g.getFont().getSize2D()-.5f));
        g.drawString(label,20,h-17);g.dispose();
    }
}
