package com.eye.stats;

import com.eye.ui.theme.*;
import com.eye.i18n.I18n;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;
import static com.eye.i18n.I18n.text;

/** Read-only presentation of period insights; custom charts remain paired with exact tooltips and accessible summaries. */
public final class FocusInsightsPanel extends SettingsUi.Column {
    private final FocusInsights.Report report;
    public FocusInsightsPanel(FocusInsights.Report report) {
        this.report=Objects.requireNonNull(report);
        addSection(new Hero());
        JPanel metrics=new Theme.Surface(new GridLayout(1,3,0,0),Theme.SURFACE,Theme.LINE,16);metrics.setPreferredSize(new Dimension(600,88));metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE,88));
        metrics.add(new Metric(text("日均（含无记录日）"),duration(report.averageDailyFocusMs())));
        metrics.add(new Metric(text("有专注的学习日"),report.activeDays()+" / "+report.calendarDays()));
        metrics.add(new Metric(text("最长连续记录"),report.longestStreak()+text(" 天")));
        addSection(metrics);addSection(new Composition());addSection(new WeekRhythm());addSection(new Evidence());
        getAccessibleContext().setAccessibleName(text("专注洞察")+" · "+report.from()+" — "+report.to());
    }
    private void addSection(JComponent component){component.setAlignmentX(LEFT_ALIGNMENT);add(component);add(Box.createVerticalStrut(14));}
    private static String duration(long ms){return Statistics.duration(ms);}
    private static String percentage(double fraction){return String.format(Locale.ROOT,"%.1f%%",Math.max(0,fraction)*100);}
    private static String weekday(DayOfWeek day){return day.getDisplayName(TextStyle.SHORT,I18n.isEnglish()?Locale.ENGLISH:Locale.CHINA);}
    private static String shortTime(long ms){long minutes=ms/60000;if(minutes==0)return duration(ms);long hours=minutes/60;return I18n.isEnglish()?(hours>0?hours+"h ":"")+(minutes%60)+"m":(hours>0?hours+"时 ":"")+(minutes%60)+"分";}
    private String comparison(){
        var previous=report.comparison();
        if(!previous.loaded())return text("上一周期数据尚未加载");
        if(previous.focusMs()==0)return report.focusMs()>0?text("上一周期没有专注记录"):text("两个周期均没有专注记录");
        double change=previous.focusChangeRatio().orElse(0);
        if(Math.abs(change)<0.0005)return text("与上一周期基本持平");
        return text("较上一周期 ")+(change>0?"+":"−")+percentage(Math.abs(change))+"  ·  "+duration(Math.abs(previous.focusDeltaMs()));
    }
    private record Hit(Rectangle area,String value) {}
    private abstract class Canvas extends JPanel {
        final List<Hit> hits=new ArrayList<>();int hover=-1;
        Canvas(int height,String description){setOpaque(false);setPreferredSize(new Dimension(600,height));setMaximumSize(new Dimension(Integer.MAX_VALUE,height));setMinimumSize(new Dimension(0,height));setToolTipText("");getAccessibleContext().setAccessibleName(description);
            addMouseMotionListener(new MouseMotionAdapter(){public void mouseMoved(MouseEvent e){int next=hit(e.getPoint());if(next!=hover){hover=next;repaint();}}});
            addMouseListener(new MouseAdapter(){public void mouseExited(MouseEvent e){hover=-1;repaint();}});
        }
        int hit(Point point){for(int i=0;i<hits.size();i++)if(hits.get(i).area().contains(point))return i;return -1;}
        @Override public String getToolTipText(MouseEvent e){int index=hit(e.getPoint());return index<0?null:"<html>"+Statistics.escape(hits.get(index).value()).replace("\n","<br>")+"</html>";}
        Graphics2D canvas(Graphics g,Color fill){Graphics2D copy=(Graphics2D)g.create();Theme.quality(copy);copy.setColor(fill);copy.fillRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);copy.setColor(Theme.LINE);copy.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);hits.clear();return copy;}
        void heading(Graphics2D g,String title,String subtitle){line(g,title,24,31,getWidth()-48,17,Theme.INK,true);line(g,subtitle,24,54,getWidth()-48,11,Theme.SECONDARY,false);}
        void tooltip(int x,int y,int width,int height,String value){hits.add(new Hit(new Rectangle(x,y,width,height),value));}
    }
    private static void line(Graphics2D g,String value,int x,int baseline,int width,int size,Color color,boolean bold){
        g.setFont(bold?Theme.bold(size):Theme.font(size));g.setColor(color);FontMetrics metrics=g.getFontMetrics();String shown=value;
        while(shown.length()>1&&metrics.stringWidth(shown)>Math.max(1,width)){shown=shown.substring(0,shown.length()-1);}
        if(!shown.equals(value)){while(shown.length()>1&&metrics.stringWidth(shown+"…")>width)shown=shown.substring(0,shown.length()-1);shown+="…";}
        g.drawString(shown,x,baseline);
    }
    private static void fitValue(Graphics2D g,String value,int x,int baseline,int width,int max,Color color){int size=max;while(size>13&&g.getFontMetrics(Theme.bold(size)).stringWidth(value)>width)size--;line(g,value,x,baseline,width,size,color,true);}
    private final class Hero extends Canvas {
        Hero(){super(192,text("累计专注")+" "+duration(report.focusMs())+"; "+comparison());}
        protected void paintComponent(Graphics graphics){Graphics2D g=canvas(graphics,Theme.SURFACE);
            int right=Math.max(176,getWidth()/3),leftWidth=getWidth()-right-70;
            line(g,text("专注洞察"),26,31,leftWidth,17,Theme.INK,true);
            line(g,report.from()+" — "+report.to(),getWidth()-right,31,right-24,11,Theme.SECONDARY,false);
            g.setColor(Theme.LINE);g.drawLine(24,48,getWidth()-24,48);
            line(g,text("累计专注"),26,75,leftWidth,11,Theme.SECONDARY,false);fitValue(g,duration(report.focusMs()),26,117,leftWidth,35,Theme.INK);
            g.setColor(Theme.GROUP);g.fillRoundRect(24,140,getWidth()-48,34,10,10);
            line(g,comparison(),36,162,getWidth()-72,11,Theme.GREEN,false);
            int x=getWidth()-right;g.setColor(Theme.LINE);g.drawLine(x-22,67,x-22,119);
            line(g,text("累计休息"),x,75,right-24,11,Theme.SECONDARY,false);fitValue(g,duration(report.restMs()),x,111,right-24,25,Theme.INK);
            tooltip(0,0,getWidth(),getHeight(),text("累计专注")+": "+duration(report.focusMs())+"\n"+text("累计休息")+": "+duration(report.restMs())+"\n"+comparison()+"\n"+text("对比范围")+": "+report.comparison().from()+" — "+report.comparison().to());g.dispose();
        }
    }
    private final class Metric extends Canvas {
        final String label,value;
        Metric(String label,String value){super(88,label+": "+value);this.label=label;this.value=value;}
        protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);hits.clear();
            if(getX()>0){g.setColor(Theme.LINE);g.drawLine(0,18,0,getHeight()-18);}
            line(g,label,20,29,getWidth()-40,11,Theme.SECONDARY,false);fitValue(g,value,20,66,getWidth()-40,26,Theme.INK);tooltip(0,0,getWidth(),getHeight(),label+": "+value);g.dispose();}
    }
    private record Slice(String id,String name,long amount,double share,Color color) {}
    private List<Slice> slices(){List<Slice> values=new ArrayList<>();long other=0;double fraction=0;
        Map<String,Long> totals=new LinkedHashMap<>();Map<String,String> names=new LinkedHashMap<>();
        for(var task:report.tasks()){totals.put(task.id(),task.focusMs());names.put(task.id(),task.name());}
        Map<String,Color> colors=HourlyFocusChart.allocateColors(List.of(new Statistics.Bucket("",0,0,totals)),names);
        for(int i=0;i<report.tasks().size();i++){var task=report.tasks().get(i);if(i<5)values.add(new Slice(task.id(),Statistics.FREE.equals(task.id())?text("自由专注"):task.name(),task.focusMs(),task.share(),colors.get(task.id())));else{other+=task.focusMs();fraction+=task.share();}}
        if(other>0)values.add(new Slice("@other",text("其他任务"),other,fraction,Theme.MUTED));return values;
    }
    private final class Composition extends Canvas {
        final List<Slice> slices=slices();
        Composition(){super(354,text("时间去向")+"; "+text("前五项与其他任务，按专注时间排序"));getAccessibleContext().setAccessibleDescription(report.tasks().stream().map(t->t.name()+": "+duration(t.focusMs())+" "+percentage(t.share())).reduce((a,b)->a+"; "+b).orElse(text("还没有专注记录")));}
        protected void paintComponent(Graphics graphics){Graphics2D g=canvas(graphics,Theme.SURFACE);heading(g,text("时间去向"),text("前五项与其他任务，按专注时间排序"));
            int side=Math.max(126,Math.min(190,getWidth()/4)),cx=29+side/2,cy=196,ring=Math.max(16,side/8);double start=90;
            g.setStroke(new BasicStroke(ring));g.setColor(Theme.MINT);g.draw(new Ellipse2D.Double(cx-side/2.0,cy-side/2.0,side,side));
            for(Slice slice:slices){double extent=-slice.share()*360;g.setColor(slice.color());double gap=slices.size()>1?Math.min(2.0,Math.abs(extent)*.2):0;g.draw(new Arc2D.Double(cx-side/2.0,cy-side/2.0,side,side,start-gap/2,extent+gap,Arc2D.OPEN));start+=extent;}
            g.setStroke(new BasicStroke(1));String count=report.tasks().isEmpty()?"—":Integer.toString(report.tasks().size());g.setFont(Theme.bold(30));g.setColor(Theme.INK);g.drawString(count,cx-g.getFontMetrics().stringWidth(count)/2,cy+2);g.setFont(Theme.font(11));g.setColor(Theme.SECONDARY);String legend=text("投入方向");g.drawString(legend,cx-g.getFontMetrics().stringWidth(legend)/2,cy+27);
            int x=side+72,width=getWidth()-x-26;
            if(slices.isEmpty())line(g,text("还没有专注记录"),x,cy,width,17,Theme.INK,true);
            double maximum=slices.isEmpty()?1:slices.get(0).share();
            for(int i=0;i<slices.size();i++){Slice slice=slices.get(i);int y=83+i*42;
                if(hover==i){g.setColor(Theme.MINT);g.fillRoundRect(x-9,y-16,width+18,39,9,9);}
                g.setColor(slice.color());g.fillRoundRect(x,y-10,7,7,3,3);line(g,slice.name(),x+17,y,width-138,12,Theme.INK,i==0);
                String amount=shortTime(slice.amount())+" · "+percentage(slice.share());g.setFont(Theme.font(10));g.setColor(Theme.SECONDARY);g.drawString(amount,x+width-g.getFontMetrics().stringWidth(amount),y);
                g.setColor(Theme.GROUP);g.fillRoundRect(x+17,y+11,width-17,4,4,4);g.setColor(Theme.alpha(slice.color(),195));g.fillRoundRect(x+17,y+11,(int)Math.round((width-17)*slice.share()/Math.max(0.0001,maximum)),4,4,4);
                tooltip(x-9,y-16,width+18,39,slice.name()+"\n"+duration(slice.amount())+" · "+percentage(slice.share()));
            }
            g.dispose();
        }
    }
    private final class WeekRhythm extends Canvas {
        WeekRhythm(){super(292,text("一周的专注节奏")+"; "+text("同一星期几的平均专注，包含无记录日"));getAccessibleContext().setAccessibleDescription(report.weekdays().stream().map(w->weekday(w.day())+": "+(w.calendarDays()==0?"—":duration(w.averageFocusMs()))).reduce((a,b)->a+"; "+b).orElse(""));}
        protected void paintComponent(Graphics graphics){Graphics2D g=canvas(graphics,Theme.SURFACE);heading(g,text("一周的专注节奏"),text("同一星期几的平均专注，包含无记录日"));
            int x=35,plotWidth=getWidth()-70,top=98,bottom=229,height=bottom-top;double step=plotWidth/7.0;long max=report.weekdays().stream().mapToLong(FocusInsights.Weekday::averageFocusMs).max().orElse(0);DayOfWeek best=report.bestWeekday().map(FocusInsights.Weekday::day).orElse(null);
            g.setStroke(new BasicStroke(1,0,0,1,new float[]{3,5},0));g.setColor(Theme.LINE);for(int i=0;i<3;i++)g.drawLine(x,top+i*height/2,x+plotWidth,top+i*height/2);g.setStroke(new BasicStroke(1));
            for(int i=0;i<7;i++){var day=report.weekdays().get(i);int center=x+(int)(step*(i+.5)),bw=(int)Math.min(54,step*.57),h=max==0?0:(int)Math.round(height*day.averageFocusMs()/(double)max);
                if(i==hover){g.setColor(Theme.MINT);g.fillRoundRect(center-(int)step/2+2,top-24,(int)step-4,height+61,10,10);}
                g.setPaint(new GradientPaint(0,top,day.day()==best?Theme.GREEN:new Color(0xC2D4C6),0,bottom,day.day()==best?new Color(0x4A8469):new Color(0xE0E9E0)));if(h>0)g.fillRoundRect(center-bw/2,bottom-h,bw,h,8,8);
                g.setColor(Theme.SECONDARY);g.setFont(Theme.font(10));String amount=day.calendarDays()==0?"—":shortTime(day.averageFocusMs());g.drawString(amount,center-g.getFontMetrics().stringWidth(amount)/2,bottom-h-10);
                g.setFont(day.day()==best?Theme.bold(12):Theme.font(12));g.setColor(day.day()==best?Theme.GREEN:Theme.SECONDARY);String label=weekday(day.day());g.drawString(label,center-g.getFontMetrics().stringWidth(label)/2,253);
                tooltip(center-(int)step/2,top-26,(int)step,height+66,label+"\n"+text("平均专注")+": "+duration(day.averageFocusMs())+"\n"+text("累计专注")+": "+duration(day.focusMs())+"\n"+text("统计天数")+": "+day.calendarDays());
            }
            g.dispose();
        }
    }
    private final class Evidence extends Canvas {
        Evidence(){super(188,text("专注片段")+" · "+text("一轮专注可以包含多个有起止时间的片段。"));}
        protected void paintComponent(Graphics graphics){Graphics2D g=canvas(graphics,Theme.SURFACE);heading(g,text("专注片段"),text("一轮专注可以包含多个有起止时间的片段。"));int w=(getWidth()-48)/3;
            String[] labels={text("专注记录段"),text("记录段中位时长"),text("最长记录段")};String[] values={Integer.toString(report.focusIntervalCount()),report.focusIntervalCount()==0?"—":duration(report.medianFocusIntervalMs()),report.focusIntervalCount()==0?"—":duration(report.longestFocusIntervalMs())};
            for(int i=0;i<3;i++){int x=24+i*w;line(g,labels[i],x,87,w-18,11,Theme.SECONDARY,false);fitValue(g,values[i],x,117,w-18,23,Theme.INK);tooltip(x,69,w-10,56,labels[i]+": "+values[i]);}
            int width=getWidth()-48;g.setColor(Theme.LINE);g.fillRoundRect(24,137,width,5,5,5);g.setColor(Theme.GREEN);g.fillRoundRect(24,137,(int)(width*report.captureRatio()),5,5,5);
            String coverage=report.focusMs()==0?text("暂无可分析的记录"):text("有起止时间的专注占比 ")+percentage(report.captureRatio())+"  ·  "+duration(report.recordedFocusMs());line(g,coverage,24,164,width,11,Theme.GREEN,false);
            tooltip(24,133,width,42,report.unplacedFocusMs()>0?coverage+"\n"+text("时段未知")+": "+duration(report.unplacedFocusMs()):coverage);g.dispose();
        }
    }
}
