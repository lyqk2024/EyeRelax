package com.eye.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** Keeps a heading and its actions aligned, wrapping actions instead of clipping the heading. */
public final class AdaptiveToolbar extends JPanel {
    private final JComponent leading, actions;
    private final int gap;
    public AdaptiveToolbar(JComponent leading,JComponent actions,int gap) {
        this.leading=leading;this.actions=actions;this.gap=gap;
        setOpaque(false);setLayout(null);add(leading);add(actions);
        addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){revalidate();}});
    }
    private boolean wraps(){return getWidth()>0&&leading.getPreferredSize().width+gap+actions.getPreferredSize().width>getWidth();}
    @Override public Dimension getPreferredSize(){
        Dimension a=leading.getPreferredSize(),b=actions.getPreferredSize();
        return new Dimension(a.width+gap+b.width,wraps()?a.height+gap+b.height:Math.max(a.height,b.height));
    }
    @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
    @Override public void doLayout(){
        Dimension a=leading.getPreferredSize(),b=actions.getPreferredSize();
        if(wraps()){
            leading.setBounds(0,0,getWidth(),a.height);
            actions.setBounds(Math.max(0,getWidth()-b.width),a.height+gap,Math.min(getWidth(),b.width),b.height);
        }else{
            leading.setBounds(0,Math.max(0,(getHeight()-a.height)/2),Math.max(0,getWidth()-b.width-gap),a.height);
            actions.setBounds(Math.max(0,getWidth()-b.width),Math.max(0,(getHeight()-b.height)/2),Math.min(getWidth(),b.width),b.height);
        }
    }
}
