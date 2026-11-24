package com.eye.tutorial;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.*;
import java.util.function.*;

/** Temporarily lifts a spotlight while its real Swing popup is open. Owns no selection. */
final class TutorialPopupGate implements AutoCloseable {
    private final java.util.List<Binding> bindings=new ArrayList<>();
    private final Consumer<JComponent> opened;
    private final BiConsumer<JComponent,Boolean> closed;
    private int generation,openCount,closingCount;
    TutorialPopupGate(Consumer<JComponent> opened,BiConsumer<JComponent,Boolean> closed){this.opened=opened;this.closed=closed;}
    boolean isOpen(){return openCount>0||closingCount>0;}
    void bind(JComponent region){
        close();if(region==null)return;
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        collect(region,seen);
    }
    private void collect(Component component,Set<Object> seen){
        if(component instanceof JComboBox<?> combo&&seen.add(combo))add(new Binding(combo,combo,null));
        if(component instanceof JComponent control&&control.getClientProperty("tutorial.popup") instanceof JPopupMenu popup&&seen.add(popup))add(new Binding(control,null,popup));
        if(component instanceof Container parent)for(Component child:parent.getComponents())collect(child,seen);
    }
    private void add(Binding binding){bindings.add(binding);if(binding.combo!=null)binding.combo.addPopupMenuListener(binding);else binding.menu.addPopupMenuListener(binding);}
    @Override public void close(){
        generation++;
        for(Binding b:bindings){if(b.combo!=null)b.combo.removePopupMenuListener(b);else b.menu.removePopupMenuListener(b);}
        bindings.clear();openCount=closingCount=0;
    }
    private final class Binding implements PopupMenuListener {
        final JComponent control;final JComboBox<?> combo;final JPopupMenu menu;
        boolean showing,cancelled;Object selection;
        Binding(JComponent control,JComboBox<?> combo,JPopupMenu menu){this.control=control;this.combo=combo;this.menu=menu;}
        @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e){
            if(showing)return;showing=true;cancelled=false;selection=combo==null?null:combo.getSelectedItem();openCount++;opened.accept(control);
        }
        @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e){dismiss();}
        @Override public void popupMenuCanceled(PopupMenuEvent e){cancelled=true;dismiss();}
        private void dismiss(){
            if(!showing)return;showing=false;openCount=Math.max(0,openCount-1);closingCount++;int ticket=generation;
            // Swing can publish its selection after the invisible event. Read it after
            // that event finishes, and never revive a course that was closed meanwhile.
            SwingUtilities.invokeLater(()->{if(ticket!=generation)return;closingCount=Math.max(0,closingCount-1);
                closed.accept(control,!cancelled&&combo!=null&&!Objects.equals(selection,combo.getSelectedItem()));});
        }
    }
}
