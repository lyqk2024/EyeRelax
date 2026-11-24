package com.eye.ui;
import javax.swing.*;
import java.awt.*;
import java.util.function.LongSupplier;
/** Native input hooks may only enforce a shield with a recent completed UI paint. */
public final class ShieldPresentation {
 private static final long LEASE_NANOS=3_000_000_000L;
 private final LongSupplier clock;
 private volatile boolean painted;
 private volatile long lastPaint;
 public ShieldPresentation(){this(System::nanoTime);}
 ShieldPresentation(LongSupplier clock){this.clock=clock;}
 public boolean ready(){return painted&&clock.getAsLong()-lastPaint<LEASE_NANOS;}
 public void clear(){painted=false;}
 public void afterPaint(Runnable action){SwingUtilities.invokeLater(()->{if(ready())action.run();});}
 void painted(){lastPaint=clock.getAsLong();painted=true;}
 public boolean present(Window window){
  if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Shield presentation requires EDT");
  boolean stale=painted&&!ready();
  clear();
  try{
   // Recreate the visible layered surface after a sleep or stalled UI interval.
   if(stale&&window.isVisible())window.setVisible(false);
   if(!window.isVisible())window.setVisible(true);
   window.validate();
   if(!window.isShowing()||window.getWidth()<=0||window.getHeight()<=0)return false;
   JRootPane root=((RootPaneContainer)window).getRootPane();
   root.paintImmediately(0,0,root.getWidth(),root.getHeight());Toolkit.getDefaultToolkit().sync();
   painted();return true;
  }catch(RuntimeException ex){clear();window.setVisible(false);System.err.println("Shield presentation: "+ex.getMessage());return false;}
 }
}
