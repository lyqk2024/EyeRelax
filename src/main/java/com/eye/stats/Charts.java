package com.eye.stats;

import com.eye.core.SessionStore;
import com.eye.ui.theme.Theme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Dependency-free, responsive charts. Values are always paired with text, not colour alone. */
public final class Charts {
    private Charts() {}
    private static final Color[] PALETTE={new Color(0x467C65),new Color(0x759D83),new Color(0x558D99),new Color(0x929A68),new Color(0xB59B73),new Color(0x7C849C),new Color(0x739EAA),new Color(0x9B897C)};
    public static Color color(String id){
        if("@completion".equals(id))return Theme.GREEN;
        if(id==null||id.equals(Statistics.FREE))return new Color(0x72A7A2);
        if(id.contains("REMINDER")||id.equals(com.eye.i18n.I18n.text("提醒遮罩")))return new Color(0xBA9C64);
        if(id.contains("IMMEDIATE")||id.equals(com.eye.i18n.I18n.text("立即休息")))return new Color(0xADC399);
        if(id.contains("REST")||id.contains("休息"))return new Color(0xC2D2B7);
        return PALETTE[Math.floorMod(id.hashCode(),PALETTE.length)];
    }
    public static final class Bars extends JComponent {
        private List<Statistics.Bucket> buckets=List.of();private Map<String,String> names=Map.of();
        private boolean hourly,percent;private int hover=-1;private Consumer<Statistics.Bucket> selected;
        private String empty=com.eye.i18n.I18n.text("暂无记录");
        public Bars(){setOpaque(false);setPreferredSize(new Dimension(550,210));setMinimumSize(new Dimension(0,150));setToolTipText("");
            getAccessibleContextSafe();
            addMouseMotionListener(new MouseMotionAdapter(){public void mouseMoved(MouseEvent e){hover=index(e.getPoint());repaint();}});
            addMouseListener(new MouseAdapter(){public void mouseExited(MouseEvent e){hover=-1;repaint();}public void mouseClicked(MouseEvent e){int i=index(e.getPoint());if(i>=0&&selected!=null)selected.accept(buckets.get(i));}});
        }
        private void getAccessibleContextSafe(){if(getAccessibleContext()!=null)getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("时长分布图；悬停查看数值，下方有文字明细"));}
        public void data(List<Statistics.Bucket> data,Map<String,String> labels,boolean hourly,boolean percent){this.buckets=List.copyOf(data);this.names=Map.copyOf(labels);this.hourly=hourly;this.percent=percent;hover=-1;repaint();}
        public void onSelect(Consumer<Statistics.Bucket> action){selected=action;setCursor(Cursor.getPredefinedCursor(action==null?Cursor.DEFAULT_CURSOR:Cursor.HAND_CURSOR));}
        public void emptyMessage(String text){empty=text;}
        private int plotWidth(){return Math.max(1,getWidth()-74);}
        private int index(Point p){if(buckets.isEmpty()||p.x<16||p.x>getWidth()-58||p.y<12||p.y>getHeight()-30)return -1;int i=(int)((p.x-16)/(plotWidth()/(double)buckets.size()));return i>=0&&i<buckets.size()?i:-1;}
        private long maximum(){long raw=buckets.stream().mapToLong(Statistics.Bucket::total).max().orElse(0);
            if(percent)return 10000; if(hourly)return Math.max(3600000,raw);
            long hour=3600000;return Math.max(hour,((raw+hour-1)/hour)*hour);}
        @Override public String getToolTipText(MouseEvent event){int i=index(event.getPoint());if(i<0)return null;var b=buckets.get(i);
            if(b.total()==0)return "<html><b>"+Statistics.escape(b.label())+"</b><br>"+(percent?"0%":Statistics.duration(0))+"<br>"+com.eye.i18n.I18n.text("此时段没有相应记录。")+"</html>";
            StringBuilder s=new StringBuilder("<html><b>").append(Statistics.escape(b.label())).append("</b><br>");
            b.values().entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).filter(e->e.getValue()>0).limit(8)
                    .forEach(e->s.append(Statistics.escape(names.getOrDefault(e.getKey(),e.getKey()))).append("&nbsp;&nbsp;").append(percent?String.format("%.1f%%",e.getValue()/100.0):Statistics.duration(e.getValue())).append("<br>"));
            return s.append("</html>").toString();}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
            int left=16,top=16,w=plotWidth(),h=Math.max(70,getHeight()-48);long max=maximum();
            g.setFont(Theme.font(10));
            for(int n=0;n<=2;n++){int y=top+h-n*h/2;g.setColor(Theme.LINE);g.drawLine(left,y,left+w,y);g.setColor(Theme.SECONDARY);
                String value=percent?(n*50)+"%":hourly?(n*30)+com.eye.i18n.I18n.text(" 分"):(max*n/2/3600000.0==Math.floor(max*n/2/3600000.0)?""+(max*n/2/3600000):String.format("%.1f",max*n/2/3600000.0))+com.eye.i18n.I18n.text(" 时");
                g.drawString(value,left+w+8,y+4);}
            if(buckets.isEmpty()){g.setFont(Theme.font(12));g.setColor(Theme.SECONDARY);g.drawString(empty,left+12,top+h/2);g.dispose();return;}
            double step=w/(double)buckets.size();int bw=Math.max(2,(int)Math.min(40,step*0.58));
            for(int i=0;i<buckets.size();i++){int x=left+(int)(step*i+(step-bw)/2);var bucket=buckets.get(i);
                g.setColor(Theme.alpha(Theme.SAGE,15));g.fillRoundRect(x,top,bw,h,Math.min(7,bw),Math.min(7,bw));
                if(i==hover){g.setColor(Theme.alpha(Theme.SAGE,35));g.fillRoundRect(x-5,top-3,bw+10,h+6,8,8);}
                Shape clip=g.getClip();int totalHeight=(int)Math.round(bucket.total()*h/(double)max);if(totalHeight>0)g.clip(new RoundRectangle2D.Double(x,top+h-totalHeight,bw,totalHeight,Math.min(6,bw),Math.min(6,totalHeight)));
                long sum=0;List<String> keys=new ArrayList<>(bucket.values().keySet());Collections.sort(keys);
                for(String key:keys){long value=bucket.values().get(key);if(value<=0)continue;long previous=sum;sum+=value;
                    int bottom=top+h-(int)Math.round(previous*h/(double)max),y=top+h-(int)Math.round(sum*h/(double)max);
                    Color base=color(key);g.setPaint(new GradientPaint(x,y,Theme.alpha(base,175),x,bottom,base));g.fillRect(x,y,bw,Math.max(1,bottom-y));}
                g.setClip(clip);
            }
            g.setColor(Theme.SECONDARY);g.setFont(Theme.font(10));int jump=Math.max(1,(int)Math.ceil(buckets.size()/6.0));
            if(hourly){
                for(int i=0;i<=buckets.size();i++){
                    if(i%jump!=0&&i!=buckets.size())continue;
                    if(i<buckets.size()&&buckets.size()-i<jump/2)continue;
                    String label=i==buckets.size()?buckets.get(i-1).endLabel():buckets.get(i).label();
                    int x=left+(int)(step*i)-g.getFontMetrics().stringWidth(label)/2;
                    x=Math.max(0,Math.min(x,getWidth()-g.getFontMetrics().stringWidth(label)));
                    g.drawString(label,x,top+h+22);
                }
            } else for(int i=0;i<buckets.size();i++){if(i%jump!=0&&i!=buckets.size()-1)continue;if(i==buckets.size()-1&&i%jump<jump/2&&i%jump!=0)continue;
                String label=buckets.get(i).label();int x=left+(int)(step*(i+.5))-g.getFontMetrics().stringWidth(label)/2;
                x=Math.max(0,Math.min(x,getWidth()-g.getFontMetrics().stringWidth(label)));g.drawString(label,x,top+h+22);}
            g.dispose();}
    }

    public static final class Timeline extends JComponent {
        private List<SessionStore.Interval> intervals=List.of();private Statistics.Window window;
        private final List<Rectangle> hitboxes=new ArrayList<>();private int hover=-1;private Consumer<SessionStore.Interval> selected;
        public Timeline(){setPreferredSize(new Dimension(550,124));setMinimumSize(new Dimension(0,124));setToolTipText("");
            addMouseMotionListener(new MouseMotionAdapter(){public void mouseMoved(MouseEvent e){hover=index(e.getPoint());repaint();}});
            addMouseListener(new MouseAdapter(){public void mouseExited(MouseEvent e){hover=-1;repaint();}public void mouseClicked(MouseEvent e){int i=index(e.getPoint());if(i>=0&&selected!=null)selected.accept(intervals.get(i));}});
        }
        public void data(List<SessionStore.Interval> rows,Statistics.Window window){this.intervals=List.copyOf(rows);this.window=window;hover=-1;repaint();}
        public void onSelect(Consumer<SessionStore.Interval> selected){this.selected=selected;setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
        private int index(Point p){for(int i=0;i<hitboxes.size();i++)if(hitboxes.get(i).contains(p))return i;return -1;}
        public static String description(SessionStore.Interval i){ZoneId z=ZoneId.of(i.zoneId());DateTimeFormatter fmt=DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
            return i.displayName()+"  ·  "+Instant.ofEpochMilli(i.startAt()).atZone(z).format(fmt)+" — "+Instant.ofEpochMilli(i.endAt()).atZone(z).format(fmt)+"  ·  "+Statistics.duration(i.durationMs());}
        @Override public String getToolTipText(MouseEvent e){int i=index(e.getPoint());if(i<0)return null;var row=intervals.get(i);return "<html>"+Statistics.escape(description(row))+"<br>"+Statistics.escape(row.zoneId())+com.eye.i18n.I18n.text(" · 分界 ")+String.format("%02d:%02d",row.boundarySeconds()/3600,row.boundarySeconds()%3600/60)+"</html>";}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);hitboxes.clear();int left=46,top=24,w=Math.max(1,getWidth()-60);
            g.setFont(Theme.font(10));g.setColor(Theme.SECONDARY);g.drawString(com.eye.i18n.I18n.text("专注"),0,top+13);g.drawString(com.eye.i18n.I18n.text("休息"),0,top+45);
            g.setColor(Theme.MINT);g.fillRoundRect(left,top,w,18,8,8);g.fillRoundRect(left,top+32,w,18,8,8);
            if(window==null){g.dispose();return;}
            long span=Math.max(1,window.end()-window.start());
            for(int i=0;i<intervals.size();i++){var r=intervals.get(i);int x=left+(int)((r.startAt()-window.start())*w/(double)span);
                int b=left+(int)Math.ceil((r.endAt()-window.start())*w/(double)span);Rectangle rect=new Rectangle(x,top+(r.isRest()?32:0),Math.max(2,b-x),18);hitboxes.add(rect);
                g.setColor(color(Statistics.key(r)));g.fill(new RoundRectangle2D.Double(rect.x,rect.y,rect.width,rect.height,4,4));
                if(i==hover){g.setColor(Theme.GREEN);g.drawRoundRect(rect.x-1,rect.y-2,rect.width+2,rect.height+4,4,4);}}
            for(int n=0;n<=4;n++){long at=window.start()+span*n/4;String text=Instant.ofEpochMilli(at).atZone(window.zone()).format(DateTimeFormatter.ofPattern("HH:mm"));
                if(n==4)text=com.eye.i18n.I18n.text("次日 ")+text;int x=left+w*n/4-g.getFontMetrics().stringWidth(text)/2;x=Math.max(0,Math.min(x,getWidth()-g.getFontMetrics().stringWidth(text)));
                g.setColor(Theme.SECONDARY);g.drawString(text,x,top+80);}
            if(intervals.isEmpty()){g.setColor(Theme.SECONDARY);g.drawString(com.eye.i18n.I18n.text("暂无时段记录"),left+10,top-8);}g.dispose();}
    }
}
