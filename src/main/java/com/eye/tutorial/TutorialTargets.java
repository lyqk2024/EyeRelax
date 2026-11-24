package com.eye.tutorial;
import javax.swing.JComponent;
import java.util.HashSet;
/** Explicit semantic regions include titles and related controls without selecting a whole page. */
public final class TutorialTargets {
 private TutorialTargets(){}
 public static JComponent region(JComponent target){
  var seen=new HashSet<JComponent>();
  while(target!=null&&seen.add(target)&&target.getClientProperty("tutorial.region") instanceof JComponent parent)target=parent;
  return target;
 }
}
