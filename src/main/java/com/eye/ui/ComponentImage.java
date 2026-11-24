package com.eye.ui;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
/** Renders a detached export view, including content beyond the on-screen viewport. */
public final class ComponentImage {
    private ComponentImage(){}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container p)layout(p);}
    private static void prepareLabels(Component component,java.awt.font.FontRenderContext context){
        if(component instanceof JLabel label && label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)==null)
            label.setUI(new ExportLabelUI(context));
        if(component instanceof Container parent)for(Component child:parent.getComponents())prepareLabels(child,context);
    }
    public static BufferedImage capture(JComponent card,int width,int scale){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Render on EDT");
        java.awt.font.FontRenderContext context=new java.awt.font.FontRenderContext(
                java.awt.geom.AffineTransform.getScaleInstance(scale,scale),true,true);
        prepareLabels(card,context);
        card.setSize(width,card.getPreferredSize().height);
        for(int i=0;i<4;i++){layout(card);card.setSize(width,card.getPreferredSize().height);}
        layout(card);BufferedImage image=new BufferedImage(width*scale,Math.max(1,card.getHeight())*scale,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();try{g.setColor(com.eye.ui.theme.Theme.SURFACE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.scale(scale,scale);card.printAll(g);}finally{g.dispose();}return image;
    }
}
