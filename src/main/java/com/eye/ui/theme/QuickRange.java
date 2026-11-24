package com.eye.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.util.function.IntFunction;

/** Continuous slider plus directly clickable presets; neither constrains the other. */
public final class QuickRange extends JPanel {
    public QuickRange(String title,JSlider slider,IntFunction<String> format,int... stops){
        super(new BorderLayout(0,4));slider.putClientProperty("tutorial.region",this);setOpaque(false);setAlignmentX(LEFT_ALIGNMENT);
        JPanel heading=Theme.panel(new BorderLayout(8,0));heading.add(Theme.label(title,11,Theme.INK));
        JLabel value=Theme.label(format.apply(slider.getValue()),11,Theme.GREEN);heading.add(value,BorderLayout.EAST);add(heading,BorderLayout.NORTH);
        slider.setOpaque(false);slider.setPreferredSize(new Dimension(280,26));slider.getAccessibleContext().setAccessibleName(title);
        slider.setUI(new BasicSliderUI(slider){
            @Override protected Dimension getThumbSize(){int n=(int)Math.round(14*ProportionalPanel.scaleOf(slider));return new Dimension(n,n);}
            @Override public void paintTrack(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);int h=Math.max(3,(int)Math.round(4*ProportionalPanel.scaleOf(slider)));int y=trackRect.y+(trackRect.height-h)/2;g.setColor(Theme.LINE);g.fillRoundRect(trackRect.x,y,trackRect.width,h,h,h);g.setColor(Theme.GREEN);g.fillRoundRect(trackRect.x,y,Math.max(0,thumbRect.x+thumbRect.width/2-trackRect.x),h,h,h);g.dispose();}
            @Override public void paintThumb(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);g.setColor(Theme.GREEN);g.fillOval(thumbRect.x,thumbRect.y,thumbRect.width,thumbRect.height);g.dispose();}
            @Override public void paintFocus(Graphics graphics){if(slider.hasFocus()){graphics.setColor(Theme.SAGE);graphics.drawOval(thumbRect.x-2,thumbRect.y-2,thumbRect.width+4,thumbRect.height+4);}}
            @Override protected void scrollDueToClickInTrack(int direction){slider.setValue(valueForXPosition(slider.getMousePosition()==null?thumbRect.x:slider.getMousePosition().x));}
        });
        add(slider,BorderLayout.CENTER);
        JPanel presets=Theme.panel(new GridLayout(1,stops.length,4,0));
        java.util.List<JButton> buttons=new java.util.ArrayList<>();
        for(int stop:stops){JButton b=new JButton(format.apply(stop));Theme.smallButton(b,"secondary",null);b.setFont(Theme.font(10));b.setBorder(new EmptyBorder(5,3,5,3));b.addActionListener(e->slider.setValue(stop));presets.add(b);buttons.add(b);}
        Runnable update=()->{value.setText(format.apply(slider.getValue()));for(int i=0;i<stops.length;i++){JButton b=buttons.get(i);boolean selected=slider.getValue()==stops[i];b.putClientProperty("rest.variant",selected?"primary":"secondary");b.setForeground(selected?Color.WHITE:Theme.INK);b.repaint();}};
        slider.addChangeListener(e->update.run());update.run();add(presets,BorderLayout.SOUTH);
    }
    @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
}
