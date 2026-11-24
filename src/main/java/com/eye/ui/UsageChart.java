package com.eye.ui;

import com.eye.ui.theme.Theme;
import com.eye.ui.theme.ProportionalPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongFunction;

/** Render-only usage chart; receives prepared values and icon/label providers. */
final class UsageChart extends JComponent {
    static final Color MEASURED = new Color(0x77839E);
    static final Color ESTIMATED = new Color(0xE3B887);
    private final boolean horizontal;
    private final Function<String, Icon> icons;
    private final LongFunction<String> durations;
    private List<String> paths = List.of(), labels = List.of();
    private List<Long> values = List.of(), extra = List.of();
    private static final int APPLICATION_ROW_HEIGHT=52;
    private int hovered=-1;

    UsageChart(boolean horizontal, Function<String, Icon> icons, LongFunction<String> durations) {
        this.horizontal=horizontal;this.icons=icons;this.durations=durations;setOpaque(false);
        setToolTipText("");setFocusable(true);getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text(horizontal?"应用使用时长":"每日使用趋势"));
        for(String key:new String[]{"LEFT","UP","RIGHT","DOWN"}){
            int delta=key.equals("LEFT")||key.equals("UP")?-1:1;getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),key);
            getActionMap().put(key,new AbstractAction(){@Override public void actionPerformed(ActionEvent e){if(values.isEmpty())return;hovered=hovered<0?(delta<0?values.size()-1:0):Math.floorMod(hovered+delta,values.size());getAccessibleContext().setAccessibleDescription(labels.get(hovered)+" · "+durations.apply(values.get(hovered)));scrollRectToVisible(new Rectangle(0,horizontal?px(6+hovered*APPLICATION_ROW_HEIGHT):0,getWidth(),horizontal?px(APPLICATION_ROW_HEIGHT):getHeight()));repaint();}});
        }
        addFocusListener(new FocusAdapter(){@Override public void focusGained(FocusEvent e){if(hovered<0&&!values.isEmpty())hovered=0;repaint();}@Override public void focusLost(FocusEvent e){hovered=-1;repaint();}});
        MouseAdapter hover=new MouseAdapter(){
            @Override public void mouseMoved(MouseEvent e){int next=indexAt(e.getX(),e.getY());if(next!=hovered){hovered=next;repaint();}}
            @Override public void mouseExited(MouseEvent e){hovered=-1;repaint();}
        };addMouseListener(hover);addMouseMotionListener(hover);
    }
    void setPaths(List<String> paths){this.paths=List.copyOf(paths);}
    void setValues(List<String> labels,List<Long> values,List<Long> extra){
        this.labels=List.copyOf(labels);this.values=List.copyOf(values);this.extra=List.copyOf(extra);hovered=-1;
        StringBuilder description=new StringBuilder();for(int i=0;i<labels.size();i++){if(i>0)description.append("; ");description.append(labels.get(i)).append(" · ").append(durations.apply(values.get(i)));if(extraAt(i)>0)description.append(" + ").append(durations.apply(extraAt(i))).append(' ').append(com.eye.i18n.I18n.text("系统补充估算"));}getAccessibleContext().setAccessibleDescription(description.toString());revalidate();repaint();
    }
    @Override public javax.accessibility.AccessibleContext getAccessibleContext(){if(accessibleContext==null)accessibleContext=new AccessibleJComponent(){};return accessibleContext;}
    @Override public Dimension getPreferredSize(){double s=ProportionalPanel.scaleOf(this);return new Dimension(600,(int)Math.round((horizontal?Math.max(88,labels.size()*APPLICATION_ROW_HEIGHT+12):194)*s));}
    @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
    private int px(double x){return (int)Math.round(x*ProportionalPanel.scaleOf(this));}
    private long extraAt(int i){return i<extra.size()?extra.get(i):0;}
    private int indexAt(int x,int y){
        if(values.isEmpty())return -1;int i;
        if(horizontal){i=(y-px(6))/Math.max(1,px(APPLICATION_ROW_HEIGHT));if(y<px(6))return -1;}
        else{int left=px(58),right=getWidth()-px(12);if(x<left||x>right||y<px(24)||y>getHeight()-px(30))return -1;i=(int)((x-left)/(double)Math.max(1,right-left)*values.size());}
        return i>=0&&i<values.size()?i:-1;
    }
    @Override public String getToolTipText(MouseEvent e){
        int i=indexAt(e.getX(),e.getY());if(i<0)return null;
        String title=Theme.escape(labels.get(i));String amount=durations.apply(values.get(i));
        return horizontal?"<html>"+title+"<br>"+amount+"</html>":"<html>"+title+"<br>"+com.eye.i18n.I18n.text("软件实测")+" · "+amount+(extraAt(i)>0?"<br>"+com.eye.i18n.I18n.text("系统补充估算")+" · "+durations.apply(extraAt(i)):"")+"</html>";
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();try{
            Theme.quality(g);g.setFont(Theme.font((float)(10*ProportionalPanel.scaleOf(this))));
            if(values.isEmpty()){
                g.setColor(Theme.GROUP);g.fillRoundRect(0,px(12),getWidth(),getHeight()-px(24),px(12),px(12));
                g.setColor(Theme.SECONDARY);String empty=com.eye.i18n.I18n.text(horizontal?"没有符合筛选条件的应用记录":"当天暂无使用记录");
                g.drawString(empty,Math.max(px(12),(getWidth()-g.getFontMetrics().stringWidth(empty))/2),getHeight()/2);return;
            }
            long max=1;for(int i=0;i<values.size();i++)max=Math.max(max,values.get(i)+extraAt(i));
            if(horizontal)paintApplications(g,max);else paintTimeline(g,max);
        }finally{g.dispose();}
    }
    private void paintApplications(Graphics2D g,long maximum){
        double scale=ProportionalPanel.scaleOf(this);int rowHeight=px(APPLICATION_ROW_HEIGHT),iconSize=px(26),left=px(66),right=getWidth()-px(12);
        for(int i=0;i<values.size();i++){
            int y=px(6)+i*rowHeight;if(hovered==i){g.setColor(Theme.MINT);g.fillRoundRect(0,y,getWidth(),rowHeight-px(1),px(10),px(10));}
            else if(i<values.size()-1){g.setColor(Theme.LINE);g.drawLine(left,y+rowHeight-1,right,y+rowHeight-1);}
            g.setFont(Theme.numbers((float)(10*scale)));g.setColor(Theme.MUTED);String rank=String.format("%02d",i+1);g.drawString(rank,px(9),y+px(29));
            if(i<paths.size()){Icon icon=icons.apply(paths.get(i));if(icon!=null)ApplicationSelectionList.fitIcon(icon,iconSize).paintIcon(this,g,px(30),y+px(11));}
            g.setFont(Theme.font((float)(12*scale)));String amount=durations.apply(values.get(i));int valueWidth=g.getFontMetrics().stringWidth(amount);
            g.setColor(Theme.INK);g.drawString(ellipsize(g,labels.get(i),Math.max(px(30),right-left-valueWidth-px(16))),left,y+px(23));
            g.setColor(Theme.INK);g.drawString(amount,right-valueWidth,y+px(23));
            int barWidth=Math.max(1,right-left),barY=y+px(34);g.setColor(Theme.GROUP);g.fillRoundRect(left,barY,barWidth,px(4),px(4),px(4));
            int width=(int)Math.round(barWidth*values.get(i)/(double)maximum);if(width>0){g.setPaint(new GradientPaint(left,barY,MEASURED,left+barWidth,barY,new Color(0xA3AEC2)));g.fillRoundRect(left,barY,width,px(4),px(4),px(4));}
        }
    }
    private void paintTimeline(Graphics2D g,long maximum){
        int left=px(58),top=px(24),bottom=getHeight()-px(30),right=getWidth()-px(12),height=Math.max(1,bottom-top);double step=Math.max(1,right-left)/(double)values.size();
        long axisMax=maximum<=60_000?60_000:maximum<=3_600_000?((maximum+15*60_000-1)/(15*60_000))*(15*60_000):((maximum+3_600_000-1)/3_600_000)*3_600_000;
        for(int line=0;line<=2;line++){
            int y=top+height*line/2;g.setColor(Theme.LINE);g.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10,new float[]{4,5},0));g.drawLine(left,y,right,y);g.setStroke(new BasicStroke(1));
            String value=durations.apply(axisMax*(2-line)/2);g.setColor(Theme.SECONDARY);g.drawString(value,Math.max(0,left-px(9)-g.getFontMetrics().stringWidth(value)),y+px(4));
        }
        for(int i=0;i<values.size();i++){
            int bar=Math.max(2,Math.min(px(36),(int)(step*.56))),x=left+(int)(i*step+(step-bar)/2);
            if(i==hovered){g.setColor(Theme.MINT);g.fillRoundRect(left+(int)(i*step),top-px(5),Math.max(1,(int)step),height+px(10),px(7),px(7));}
            int measured=(int)(height*values.get(i)/(double)axisMax),estimated=(int)(height*extraAt(i)/(double)axisMax),total=measured+estimated;
            if(total>0){
                Shape clip=g.getClip();g.clip(new RoundRectangle2D.Double(x,bottom-total,bar,total,Math.min(px(7),bar),Math.min(px(7),total)));
                g.setPaint(new GradientPaint(0,bottom-total,MEASURED,0,bottom,new Color(0xABB4C7)));g.fillRect(x,bottom-measured,bar,measured);
                g.setColor(ESTIMATED);if(estimated>0)g.fillRect(x,bottom-total,bar,estimated);g.setClip(clip);
            }else{g.setColor(Theme.MUTED);g.fillOval(x+bar/2-px(1),bottom-px(1),px(3),px(3));}
            if(values.size()<=7&&total>0){String label=durations.apply(values.get(i)+extraAt(i));g.setColor(Theme.INK);int textWidth=g.getFontMetrics().stringWidth(label);g.drawString(label,Math.max(left,Math.min(right-textWidth,x+bar/2-textWidth/2)),bottom-total-px(8));}
            int stride=Math.max(1,(int)Math.ceil(values.size()/7.0));
            if(i%stride==0||i==values.size()-1){String label=labels.get(i);g.setColor(i==hovered?Theme.INK:Theme.SECONDARY);int textWidth=g.getFontMetrics().stringWidth(label);g.drawString(label,Math.max(left,Math.min(right-textWidth,x+bar/2-textWidth/2)),bottom+px(22));}
        }
    }
    private static String ellipsize(Graphics2D g,String text,int width){if(g.getFontMetrics().stringWidth(text)<=width)return text;int end=text.length();while(end>0&&g.getFontMetrics().stringWidth(text.substring(0,end)+"…")>width)end--;return text.substring(0,end)+"…";}
}
