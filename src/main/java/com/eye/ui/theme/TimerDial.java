package com.eye.ui.theme;

import javax.swing.*;
import javax.accessibility.*;
import java.awt.*;
import java.awt.geom.*;

/** Read-only clock face. Timing is supplied by the existing engine, never calculated here. */
public final class TimerDial extends JComponent {
    private String value = "--:--";
    private String caption = com.eye.i18n.I18n.text("准备好，再开始");
    private String state = "READY TO FOCUS";
    private double remaining = 1;
    private boolean resting;

    public TimerDial() {
        setPreferredSize(new Dimension(310, 300));
        setMinimumSize(new Dimension(250,250));
    }
    @Override public AccessibleContext getAccessibleContext() {
        if(accessibleContext==null) accessibleContext=new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
        };
        return accessibleContext;
    }
    public void display(String value, String caption, String state, double remaining, boolean resting) {
        double bounded=Math.max(0,Math.min(1,remaining));
        if(java.util.Objects.equals(this.value,value)&&java.util.Objects.equals(this.caption,caption)
                &&java.util.Objects.equals(this.state,state)&&this.remaining==bounded&&this.resting==resting)return;
        this.value=value;this.caption=caption;this.state=state;
        this.remaining=bounded;this.resting=resting;
        getAccessibleContext().setAccessibleName(caption+"："+value);
        getAccessibleContext().setAccessibleDescription(state);
        repaint();
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
        double uiScale=ProportionalPanel.scaleOf(this);
        g.scale(uiScale,uiScale);
        double size=Math.max(1, Math.min(getWidth(),getHeight())/uiScale-22);
        double cx=getWidth()/uiScale/2.0,cy=getHeight()/uiScale/2.0;
        double x=cx-size/2,y=cy-size/2;
        Color accent=resting?Theme.AMBER:Theme.GREEN;
        g.setColor(resting?new Color(0xF7F2E8):new Color(0xF2F6EF));
        g.fill(new Ellipse2D.Double(x,y,size,size));
        g.setColor(Color.WHITE);g.fill(new Ellipse2D.Double(x+25,y+25,size-50,size-50));
        g.setColor(Theme.LINE);g.setStroke(new BasicStroke(1));g.draw(new Ellipse2D.Double(x+.5,y+.5,size-1,size-1));
        for(int i=0;i<60;i++) {
            double a=i*Math.PI/30-Math.PI/2;
            double r=size/2-10;
            g.setColor(i%5==0?new Color(0xA9BDA7):new Color(0xD4DFD0));
            g.setStroke(new BasicStroke(i%5==0?1.5f:1f));
            double length=i%5==0?7:3;
            g.draw(new Line2D.Double(cx+Math.cos(a)*r,cy+Math.sin(a)*r,cx+Math.cos(a)*(r-length),cy+Math.sin(a)*(r-length)));
        }
        double inset=25;
        double ring=size-inset*2;
        g.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(Theme.MINT);g.draw(new Ellipse2D.Double(x+inset,y+inset,ring,ring));
        g.setColor(accent);
        if(remaining>0) g.draw(new Arc2D.Double(x+inset,y+inset,ring,ring,90,-360*remaining,Arc2D.OPEN));
        double angle=-Math.PI/2+2*Math.PI*remaining;
        double dx=cx+Math.cos(angle)*ring/2,dy=cy+Math.sin(angle)*ring/2;
        g.setColor(Theme.SURFACE);g.fill(new Ellipse2D.Double(dx-6,dy-6,12,12));
        g.setColor(accent);g.fill(new Ellipse2D.Double(dx-4,dy-4,8,8));
        Font display=value.contains(":")?Theme.numbers((float)size*.205f):Theme.font((float)size*.13f);
        g.setFont(display);
        while(g.getFontMetrics().stringWidth(value)>ring-34&&g.getFont().getSize()>16) g.setFont(g.getFont().deriveFont(g.getFont().getSize2D()-1));
        display=g.getFont();
        // The dial can grow after collapsing the sidebar. Keep labels outside the real
        // glyph bounds instead of retaining a fixed baseline that intersects big digits.
        double valueBaseline=cy+29;
        double stateBaseline=Math.min(cy-30,valueBaseline-g.getFontMetrics().getAscent()-12);
        double captionBaseline=Math.max(cy+61,valueBaseline+g.getFontMetrics().getDescent()+18);
        Icons.of(resting?"leaf":"eye",25,accent).paintIcon(this,g,(int)cx-12,(int)stateBaseline-45);
        g.setFont(Theme.numbers(9));g.setColor(Theme.SECONDARY);center(g,state,cx,stateBaseline);
        g.setFont(display);g.setColor(Theme.INK);center(g,value,cx,valueBaseline);
        g.setFont(Theme.font(11));
        while (g.getFontMetrics().stringWidth(caption)>ring-32 && g.getFont().getSize2D()>8) {
            g.setFont(g.getFont().deriveFont(g.getFont().getSize2D()-.5f));
        }
        g.setColor(Theme.SECONDARY);center(g,caption,cx,captionBaseline);
        g.dispose();
    }
    private void center(Graphics2D g,String text,double cx,double baseline) {
        g.drawString(text,(float)(cx-g.getFontMetrics().stringWidth(text)/2.0),(float)baseline);
    }
}
