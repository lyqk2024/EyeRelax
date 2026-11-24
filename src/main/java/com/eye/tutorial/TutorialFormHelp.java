package com.eye.tutorial;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Dialog-local spotlight guide. Skipping restores ordinary form interaction; values are never changed. */
public final class TutorialFormHelp {
 public record Hint(String message,JComponent input){}
 private TutorialFormHelp(){}
 public static JComponent create(Component owner,List<Hint> hints){
  JPanel empty=new JPanel();empty.setVisible(false);
  JRootPane parent=SwingUtilities.getRootPane(owner);
  if(parent==null||!Boolean.TRUE.equals(parent.getClientProperty("tutorial.formHelp"))||hints.isEmpty())return empty;
  BooleanSupplier blocked=parent.getClientProperty("tutorial.blocked") instanceof BooleanSupplier guard?guard:()->false;
  JComponent anchor=hints.get(0).input();
  HierarchyListener[] listener={null};
  listener[0]=e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)==0||!anchor.isShowing())return;
   Window window=SwingUtilities.getWindowAncestor(anchor);if(!(window instanceof JDialog dialog))return;
   anchor.removeHierarchyListener(listener[0]);
   SwingUtilities.invokeLater(()->{if(!dialog.isVisible())return;
    var steps=new java.util.ArrayList<TutorialController.Step>();
    for(int i=0;i<hints.size();i++){
     Hint hint=hints.get(i);
     var interaction=i==hints.size()-1&&hint.input() instanceof JComboBox<?>?
         TutorialController.Interaction.FINISH_ON_POPUP_OPEN:TutorialController.Interaction.DEFAULT;
     steps.add(new TutorialController.Step(hint.message(),hint::input,()->{},null,interaction));
    }
    TutorialController guide=new TutorialController(dialog,TutorialState.ephemeral(),steps,blocked);
    dialog.getRootPane().putClientProperty("tutorial.formGuide",guide);guide.startWelcome(true);
   });
  };
  anchor.addHierarchyListener(listener[0]);return empty;
 }
}
