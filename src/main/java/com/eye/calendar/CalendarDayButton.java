package com.eye.calendar;

import com.eye.ui.theme.Theme;
import com.eye.ui.theme.ProportionalPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;

/** Two-line calendar cell with separate holiday badge; plain text, no HTML layout overhead. */
public class CalendarDayButton extends JToggleButton {
    private LocalDate date;
    public CalendarDayButton(){Theme.button(this,"calendar",null);setBorder(new EmptyBorder(0,0,0,0));setPreferredSize(new Dimension(70,54));}
    public void setDate(LocalDate date){this.date=date;setText(date==null?"":Integer.toString(date.getDayOfMonth()));getAccessibleContext().setAccessibleName(date==null?"":ChineseCalendar.accessibleDate(date));setToolTipText(null);repaint();}
    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);double scale=ProportionalPanel.scaleOf(this);
        boolean rangeInterior=isSelected()&&Boolean.FALSE.equals(getClientProperty("endpoint"));
        if(isSelected()||getModel().isRollover()){g.setColor(isSelected()&&!rangeInterior?Theme.GREEN:Theme.MINT);g.fillRoundRect(0,0,getWidth(),getHeight(),12,12);}
        if(isFocusOwner()){g.setColor(Theme.SAGE);g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,12,12);}
        if(Boolean.TRUE.equals(getClientProperty("rest.today"))){g.setColor(Theme.SAGE);g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,12,12);}
        if(date==null){g.dispose();return;}
        String text=getText();
        g.setColor(isSelected()&&!rangeInterior?Color.WHITE:isEnabled()?Theme.INK:Theme.MUTED);
        g.setFont(Theme.font(14).deriveFont((float)(14*scale)));FontMetrics fm=g.getFontMetrics();
        g.drawString(text,(getWidth()-fm.stringWidth(text))/2,(int)(getHeight()/2.0-3*scale));
        String annotation=ChineseCalendar.annotation(date);g.setFont(Theme.font(10).deriveFont((float)(10*scale)));fm=g.getFontMetrics();
        if(fm.stringWidth(annotation)>getWidth()-6){g.setFont(g.getFont().deriveFont(g.getFont().getSize2D()*(getWidth()-6)/fm.stringWidth(annotation)));fm=g.getFontMetrics();}
        g.drawString(annotation,(getWidth()-fm.stringWidth(annotation))/2,(int)(getHeight()/2.0+13*scale));
        String badge=ChineseCalendar.badge(date);g.setFont(Theme.font(9).deriveFont((float)(9*scale)));fm=g.getFontMetrics();
        g.drawString(badge,getWidth()-fm.stringWidth(badge)-(int)(3*scale),(int)(11*scale));g.dispose();
    }
}
