package com.eye.ui.theme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;

/** Keeps the original design proportions; extra height exposes more page content. */
public final class ProportionalPanel extends JPanel {
    private final JComponent content;
    private final int baseWidth, baseHeight;
    private final Map<Component, Style> styles = new WeakHashMap<>();
    private final Map<LayoutManager, double[]> gaps = new WeakHashMap<>();
    private final Map<Component, Insets> gridInsets = new WeakHashMap<>();
    private double scale = 1;

    public ProportionalPanel(JComponent content, int width, int height) {
        super(null); this.content=content; baseWidth=width; baseHeight=height;
        setBackground(Theme.BG); add(content);
        setPreferredSize(new Dimension(width,height));
    }
    public double getScale() { return scale; }
    /** Set an unscaled style font and apply the current scale immediately. */
    public static void setBaseFont(Component component,Font font){
        for(Component c=component;c!=null;c=c.getParent())if(c instanceof ProportionalPanel panel){
            Style state=panel.styles.computeIfAbsent(component,panel::newStyle);
            state.font=font;state.lastFont=null;state.apply(component);return;
        }
        component.setFont(font);
    }
    private Style newStyle(Component c){return new Style(c);}
    @Override public void doLayout() {
        // Preserve the original relative sizes instead of stretching cards across a wide screen.
        // Limit window-driven magnification so a taller viewport can reveal more content.
        scale=Math.max(1,Math.min(1.15,Math.min(getWidth()/(double)baseWidth,getHeight()/(double)baseHeight)));
        putClientProperty("eyerelax.uiScale",scale);
        apply(content);
        int width=Math.min(getWidth(),px(baseWidth));
        content.setBounds((getWidth()-width)/2,0,width,getHeight());
    }
    private int px(double value) { return (int)Math.round(value*scale); }
    private Dimension size(Dimension d) {
        if(d==null)return null;
        return new Dimension(d.width>=32767?d.width:px(d.width),d.height>=32767?d.height:px(d.height));
    }
    private Insets insets(Insets i) { return new Insets(px(i.top),px(i.left),px(i.bottom),px(i.right)); }
    private void apply(Component c) {
        // Swing cell renderers already inherit the scaled control font at paint time.
        if(c instanceof CellRendererPane)return;
        Style state=styles.computeIfAbsent(c,Style::new);
        state.apply(c);
        if(c instanceof Container parent) {
            LayoutManager layout=parent.getLayout();
            if(layout instanceof BorderLayout b) {
                double[] g=gaps.computeIfAbsent(layout,k->new double[]{b.getHgap(),b.getVgap()});
                b.setHgap(px(g[0]));b.setVgap(px(g[1]));
            } else if(layout instanceof FlowLayout f) {
                double[] g=gaps.computeIfAbsent(layout,k->new double[]{f.getHgap(),f.getVgap()});
                f.setHgap(px(g[0]));f.setVgap(px(g[1]));
            } else if(layout instanceof GridLayout g) {
                double[] original=gaps.computeIfAbsent(layout,k->new double[]{g.getHgap(),g.getVgap()});
                g.setHgap(px(original[0]));g.setVgap(px(original[1]));
            }
            for(Component child:parent.getComponents()) {
                if(layout instanceof GridBagLayout grid) {
                    GridBagConstraints constraints=grid.getConstraints(child);
                    Insets original=gridInsets.computeIfAbsent(child,k->(Insets)constraints.insets.clone());
                    constraints.insets=insets(original);grid.setConstraints(child,constraints);
                }
                apply(child);
            }
        }
    }
    private final class Style {
        Font font,lastFont;
        Dimension preferred,minimum,maximum,lastPreferred,lastMinimum,lastMaximum;
        Border border,lastBorder;
        int iconGap,rowHeight;
        boolean updating;
        Style(Component c) {
            font=c.getFont();
            if(!c.isFontSet()&&c.getParent()!=null){Style parent=styles.get(c.getParent());if(parent!=null)font=parent.font;}
            preferred=c.isPreferredSizeSet()?c.getPreferredSize():null;
            minimum=c.isMinimumSizeSet()?c.getMinimumSize():null;
            maximum=c.isMaximumSizeSet()?c.getMaximumSize():null;
            if(c instanceof JComponent j)border=j.getBorder();
            if(c instanceof AbstractButton b)iconGap=b.getIconTextGap();
            if(c instanceof JLabel l)iconGap=l.getIconTextGap();
            if(c instanceof JTable t)rowHeight=t.getRowHeight();
            if(c instanceof JList<?> l)rowHeight=l.getFixedCellHeight();
            c.addPropertyChangeListener(event->{
                if(updating)return;
                if("preferredSize".equals(event.getPropertyName()))preferred=(Dimension)event.getNewValue();
                if("minimumSize".equals(event.getPropertyName()))minimum=(Dimension)event.getNewValue();
                if("maximumSize".equals(event.getPropertyName()))maximum=(Dimension)event.getNewValue();
            });
            if(c instanceof Container parent)parent.addContainerListener(new java.awt.event.ContainerAdapter(){
                @Override public void componentAdded(java.awt.event.ContainerEvent event){ProportionalPanel.this.apply(event.getChild());}
            });
        }
        void apply(Component c) {
            updating=true;
            try {
            if(lastFont!=null&&!Objects.equals(lastFont,c.getFont()))font=c.getFont();
            if(font!=null){lastFont=font.deriveFont((float)(font.getSize2D()*scale));c.setFont(lastFont);}
            if(preferred!=null){lastPreferred=size(preferred);c.setPreferredSize(lastPreferred);}
            if(minimum!=null){lastMinimum=size(minimum);c.setMinimumSize(lastMinimum);}
            if(maximum!=null){lastMaximum=size(maximum);c.setMaximumSize(lastMaximum);}
            if(c instanceof JComponent j){
                if(lastBorder!=null&&j.getBorder()!=lastBorder)border=j.getBorder();
                if(border instanceof EmptyBorder){
                    Insets target=insets(border.getBorderInsets(c));
                    if(lastBorder==null||!target.equals(lastBorder.getBorderInsets(c))){lastBorder=new EmptyBorder(target);j.setBorder(lastBorder);}
                }
            }
            if(c instanceof AbstractButton b){b.setIcon(scaled(b.getIcon()));b.setIconTextGap(px(iconGap));}
            if(c instanceof JLabel l){l.setIcon(scaled(l.getIcon()));l.setIconTextGap(px(iconGap));}
            if(c instanceof JTable t&&rowHeight>0)t.setRowHeight(px(rowHeight));
            if(c instanceof JList<?> l){
                Object height=l.getClientProperty("eyerelax.listBaseHeight"),width=l.getClientProperty("eyerelax.listBaseWidth");
                if(height instanceof Number n)l.setFixedCellHeight(px(n.intValue()));else if(rowHeight>0)l.setFixedCellHeight(px(rowHeight));
                if(width instanceof Number n)l.setFixedCellWidth(n.intValue()>0?px(n.intValue()):-1);
            }
            } finally { updating=false; }
        }
    }
    private Icon scaled(Icon icon) {
        if(icon==null)return null;
        if(icon instanceof ScaledIcon s&&s.scale==scale)return icon;
        Icon original=icon instanceof ScaledIcon s?s.original:icon;
        return scale==1?original:new ScaledIcon(original,scale);
    }
    private record ScaledIcon(Icon original,double scale) implements Icon {
        public int getIconWidth(){return (int)Math.round(original.getIconWidth()*scale);}
        public int getIconHeight(){return (int)Math.round(original.getIconHeight()*scale);}
        public void paintIcon(Component c,Graphics graphics,int x,int y){
            Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
            g.translate(x,y);g.scale(scale,scale);original.paintIcon(c,g,0,0);g.dispose();
        }
    }
    public static double scaleOf(Component component) {
        for(Component c=component;c!=null;c=c.getParent())if(c instanceof ProportionalPanel p)return p.scale;
        return 1;
    }
}
