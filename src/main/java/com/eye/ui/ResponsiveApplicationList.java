package com.eye.ui;

import javax.swing.*;
import java.awt.*;
import com.eye.ui.theme.ProportionalPanel;

/** Fills the viewport width with equal cells inside a vertically scrollable viewport. */
public final class ResponsiveApplicationList<T> extends ToggleSelectionList<T> {
    public ResponsiveApplicationList(ListModel<T> model){super(model);}
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override public boolean getScrollableTracksViewportHeight(){return false;}
    @Override public Dimension getPreferredSize(){
        if(getLayoutOrientation()!=HORIZONTAL_WRAP)return super.getPreferredSize();
        int width=getParent() instanceof JViewport viewport&&viewport.getExtentSize().width>0?viewport.getExtentSize().width:Math.max(600,getWidth());
        int columns=Math.max(1,width/Math.max(1,(int)Math.round(140*ProportionalPanel.scaleOf(this))));
        int cellWidth=Math.max(1,width/columns);if(getFixedCellWidth()!=cellWidth)setFixedCellWidth(cellWidth);
        int rows=(getModel().getSize()+columns-1)/columns;
        return new Dimension(width,Math.max(1,rows)*Math.max(1,getFixedCellHeight()));
    }
}
