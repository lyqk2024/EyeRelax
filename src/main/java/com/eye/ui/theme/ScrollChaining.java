package com.eye.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;

/** Scroll the inner view first, passing remaining wheel motion to its enclosing page. */
public final class ScrollChaining {
    private ScrollChaining() {}
    public static void install(JScrollPane pane) {
        if(Boolean.TRUE.equals(pane.getClientProperty("eyerelax.scrollChaining")))return;
        pane.putClientProperty("eyerelax.scrollChaining",true);
        pane.setWheelScrollingEnabled(false);
        pane.addMouseWheelListener(event -> scroll(pane,event));
    }
    private static void scroll(JScrollPane pane,MouseWheelEvent event) {
        double rotation=event.getPreciseWheelRotation();if(rotation==0)return;
        JScrollBar bar=event.isShiftDown()?pane.getHorizontalScrollBar():pane.getVerticalScrollBar();
        int direction=rotation>0?1:-1;
        int increment=event.getScrollType()==MouseWheelEvent.WHEEL_BLOCK_SCROLL
                ?bar.getBlockIncrement(direction):Math.max(1,event.getScrollAmount())*bar.getUnitIncrement(direction);
        int delta=(int)Math.round(rotation*Math.max(1,increment));if(delta==0)delta=direction;
        int before=bar.getValue();bar.setValue(before+delta);int applied=bar.getValue()-before;
        if(applied!=delta){
            JScrollPane parent=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,pane.getParent());
            if(parent!=null){
                install(parent); // Preserve precise residual movement through every nesting level.
                Point point=SwingUtilities.convertPoint(pane,event.getPoint(),parent);
                double remainder=rotation*(delta-applied)/(double)delta;
                parent.dispatchEvent(new MouseWheelEvent(parent,event.getID(),event.getWhen(),event.getModifiersEx(),
                        point.x,point.y,event.getXOnScreen(),event.getYOnScreen(),event.getClickCount(),event.isPopupTrigger(),
                        event.getScrollType(),event.getScrollAmount(),remainder>0?(int)Math.ceil(remainder):(int)Math.floor(remainder),remainder));
            }
        }
        event.consume();
    }
}
