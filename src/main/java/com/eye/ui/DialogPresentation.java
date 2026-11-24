package com.eye.ui;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
/** Center foreground prompts on the owner's monitor, independent of owner position/minimization. */
public final class DialogPresentation {
 private DialogPresentation(){}
 public static void foreground(JDialog dialog){
  dialog.setAlwaysOnTop(true);
  GraphicsConfiguration config=dialog.getOwner()==null?dialog.getGraphicsConfiguration():dialog.getOwner().getGraphicsConfiguration();
  Rectangle screen=config.getBounds();
  dialog.setLocation(screen.x+(screen.width-dialog.getWidth())/2,screen.y+(screen.height-dialog.getHeight())/2);
  dialog.addWindowListener(new WindowAdapter(){@Override public void windowOpened(WindowEvent e){SwingUtilities.invokeLater(()->{if(dialog.isShowing()){dialog.toFront();dialog.requestFocus();dialog.getRootPane().requestFocusInWindow();}});}});
 }
}
