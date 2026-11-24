package com.eye.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.BitSet;

/** Click/drag multiselection without requiring modifier keys; standard keyboard selection stays available. */
public class ToggleSelectionList<T> extends JList<T> {
    public ToggleSelectionList(T[] values){super(values);setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);}
    public ToggleSelectionList(ListModel<T> model){super(model);setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);}
    private int dragStart=-1;
    private boolean dragSelect;
    private BitSet dragBaseline;
    private int rowAt(Point point){int index=locationToIndex(point);Rectangle bounds=index<0?null:getCellBounds(index,index);return bounds!=null&&bounds.contains(point)?index:-1;}
    @Override protected void processMouseEvent(MouseEvent event){
        if(SwingUtilities.isLeftMouseButton(event)&&event.getID()==MouseEvent.MOUSE_PRESSED){
            int index=rowAt(event.getPoint());if(index<0)return;
            requestFocusInWindow();dragStart=index;dragBaseline=new BitSet();for(int selected:getSelectedIndices())dragBaseline.set(selected);
            if(event.isShiftDown()){
                int anchor=getAnchorSelectionIndex();if(anchor<0)anchor=index;
                if(!event.isControlDown())clearSelection();addSelectionInterval(Math.min(anchor,index),Math.max(anchor,index));
                getSelectionModel().setAnchorSelectionIndex(anchor);
                ((DefaultListSelectionModel)getSelectionModel()).moveLeadSelectionIndex(index);
                dragStart=-1;
            }else{
                dragSelect=!isSelectedIndex(index);applyDrag(index);
            }
            event.consume();return;
        }
        if(event.getID()==MouseEvent.MOUSE_RELEASED&&dragStart>=0){dragStart=-1;dragBaseline=null;getSelectionModel().setValueIsAdjusting(false);event.consume();return;}
        if(event.getID()==MouseEvent.MOUSE_CLICKED&&SwingUtilities.isLeftMouseButton(event)){event.consume();return;}
        super.processMouseEvent(event);
    }
    @Override protected void processMouseMotionEvent(MouseEvent event){
        if(event.getID()==MouseEvent.MOUSE_DRAGGED&&dragStart>=0){
            int index=locationToIndex(event.getPoint());if(index>=0){applyDrag(index);ensureIndexIsVisible(index);}event.consume();return;
        }
        super.processMouseMotionEvent(event);
    }
    private void applyDrag(int index){
        BitSet selected=(BitSet)dragBaseline.clone();int from=Math.min(dragStart,index),to=Math.max(dragStart,index)+1;
        if(dragSelect)selected.set(from,to);else selected.clear(from,to);
        getSelectionModel().setValueIsAdjusting(true);clearSelection();
        for(int row=selected.nextSetBit(0);row>=0;row=selected.nextSetBit(row+1))addSelectionInterval(row,row);
        getSelectionModel().setAnchorSelectionIndex(dragStart);((DefaultListSelectionModel)getSelectionModel()).moveLeadSelectionIndex(index);
    }
}
