package com.eye.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntSupplier;

/** Field-local feedback without replacing controls or discarding their values. */
public final class InlineErrors {
    private InlineErrors() {}
    public static void clear(Container root){if(root instanceof JComponent j&&j.getClientProperty("eyerelax.errorLabel") instanceof JLabel label)label.setVisible(false);for(Component c:root.getComponents())if(c instanceof Container nested)clear(nested);root.revalidate();root.repaint();}
    public static boolean show(JComponent original,String message){
        JComponent field=original.getClientProperty("eyerelax.validationAnchor") instanceof JComponent anchor?anchor:original;
        JLabel label=(JLabel)field.getClientProperty("eyerelax.errorLabel");
        if(label==null){
            Container parent=field.getParent();if(parent==null)throw new IllegalStateException("Validation field must be attached to its form");
            int index=java.util.Arrays.asList(parent.getComponents()).indexOf(field);Object constraints=null;LayoutManager layout=parent.getLayout();
            if(layout instanceof BorderLayout border)constraints=border.getConstraints(field);
            if(layout instanceof GridBagLayout grid)constraints=grid.getConstraints(field);
            label=Theme.label("",10,Theme.DANGER);label.setVisible(false);
            JPanel wrapper=new JPanel(new BorderLayout(0,4)){
                @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
            };wrapper.setOpaque(false);wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            parent.remove(field);wrapper.add(field,BorderLayout.CENTER);wrapper.add(label,BorderLayout.SOUTH);parent.add(wrapper,constraints,index);
            field.putClientProperty("eyerelax.errorLabel",label);
        }
        label.setText(message);label.setVisible(true);field.getParent().revalidate();field.getParent().repaint();
        return false;
    }
    public static boolean number(JComponent field,IntSupplier value,int minimum,int maximum,String message){
        try{int n=value.getAsInt();if(n>=minimum&&n<=maximum)return true;}catch(IllegalArgumentException ignored){}
        return show(field,message);
    }
}
