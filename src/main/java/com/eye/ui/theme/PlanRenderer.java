package com.eye.ui.theme;

import com.eye.plan.PlanItem;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** A renderer only: JList retains its original selection model and keyboard behavior. */
public final class PlanRenderer extends JPanel implements ListCellRenderer<PlanItem> {
    private PlanItem item;
    private boolean selected;
    private boolean focused;
    private float scale = 1;
    public PlanRenderer() { setPreferredSize(new Dimension(320,82));setOpaque(false); }
    @Override public Component getListCellRendererComponent(JList<? extends PlanItem> list,PlanItem value,int index,boolean selected,boolean focused) {
        this.item=value;this.selected=selected;this.focused=focused;
        scale = (float)ProportionalPanel.scaleOf(list);
        getAccessibleContext().setAccessibleName(value==null?"":value.toString());return this;
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
        g.scale(scale, scale);
        int width = Math.round(getWidth()/scale), height = Math.round(getHeight()/scale);
        g.setColor(selected?Theme.MINT:Theme.SURFACE);
        g.fill(new RoundRectangle2D.Double(3,3,width-6,height-6,16,16));
        g.setColor(selected||focused?Theme.SAGE:Theme.LINE);
        if(selected||focused)g.draw(new RoundRectangle2D.Double(3,3,width-6,height-6,16,16));
        else g.drawLine(68,height-1,width-20,height-1);
        int cy = height / 2;
        g.setColor(selected?Theme.GREEN:Theme.GROUP);
        g.fill(new RoundRectangle2D.Double(17,cy-19,38,38,12,12));
        Icons.of(selected?"check":"calendar",19,selected?Color.WHITE:Theme.GREEN).paintIcon(this,g,27,cy-9);
        if(item!=null) {
            String remaining = PlanItem.fmtHms(item.remainingSeconds);
            g.setFont(Theme.numbers(15));
            int timeWidth = Math.max(74, g.getFontMetrics().stringWidth(remaining));
            int right = width - 20;
            g.setColor(Theme.INK);g.setFont(Theme.bold(14));
            g.drawString(fit(g,item.name,width-107-timeWidth),68,cy-6);
            g.setColor(selected?Theme.GREEN:Theme.INK);g.setFont(Theme.numbers(15));
            g.drawString(remaining,right-g.getFontMetrics().stringWidth(remaining),cy-6);
            g.setColor(Theme.SECONDARY);g.setFont(Theme.font(10));
            String detail = com.eye.i18n.I18n.text("计划时长")+" "+PlanItem.fmtHms(item.plannedSeconds);
            g.drawString(fit(g,detail,width-107-timeWidth),68,cy+14);
            String label = com.eye.i18n.I18n.text("剩余");
            g.drawString(label,right-g.getFontMetrics().stringWidth(label),cy+14);
            double done = item.plannedSeconds <= 0 ? 0 : Math.max(0, Math.min(1,
                    (item.plannedSeconds-item.remainingSeconds)/(double)item.plannedSeconds));
            g.setColor(Theme.LINE); g.fillRoundRect(68,height-13,Math.max(0,width-88),3,3,3);
            g.setColor(Theme.GREEN); g.fillRoundRect(68,height-13,(int)Math.round(Math.max(0,width-88)*done),3,3,3);
        }
        g.dispose();
    }
    private static String fit(Graphics2D g,String text,int width) {
        if(text==null)return "";if(g.getFontMetrics().stringWidth(text)<=width)return text;
        int end=text.length();while(end>0&&g.getFontMetrics().stringWidth(text.substring(0,end)+"…")>width)end--;
        return text.substring(0,end)+"…";
    }
}
