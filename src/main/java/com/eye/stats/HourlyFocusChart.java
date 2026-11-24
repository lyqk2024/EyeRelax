package com.eye.stats;

import com.eye.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;

/**
 * Rich hourly composition chart used by the single-day review.
 *
 * Presentation intentionally mirrors the accepted HTML prototype while the
 * underlying buckets remain the same frozen/statistically-correct data that
 * preview.6 already used. It never changes aggregation semantics.
 */
public final class HourlyFocusChart extends JComponent {
    private enum Mode { COMPOSITION, TOTAL }
    public enum ChartStyle { BARS, CURVE, AREA }

    private static final Color TITLE = Theme.INK;
    private static final Color SUB = Theme.SECONDARY;
    private static final Color AXIS = Theme.SECONDARY;
    private static final Color GRID = Theme.LINE;
    private static final Color TRACK_TOP = new Color(0xF2F3F7);
    private static final Color TRACK_BOTTOM = new Color(0xF7F7FA);
    private static final Color TOOLTIP = new Color(0x3C3F49);
    private static final Color TOOLTIP_TEXT = Color.WHITE;
    private static final Color SELECT_CORAL = new Color(0xFF6B50);
    private static final Color TOTAL_GREEN = new Color(0x2FA25F);
    private static final Color TOTAL_GREEN_TOP = new Color(0xC8EBD4);
    private static final Color TOTAL_GREEN_BOTTOM = new Color(0x8CD3A4);
    private static final Color TAB_FILL = new Color(0xEEF4EE);
    private static final Color TAB_LINE = new Color(0xDCE7DC);
    private static final Color TAB_ACCENT = new Color(0x3A7D5D);
    private static final Color TAB_TEXT = new Color(0x243F39);
    private static final Color LEGEND_FILL = new Color(0xFBFBFE);
    private static final Color LEGEND_LINE = new Color(0xE1E5FB);

    private static final Color[] FALLBACK = {
            new Color(0x13B975), new Color(0x075FF4), new Color(0x8D24EC),
            new Color(0xFF8400), new Color(0xE71D79), new Color(0x09BDA0),
            new Color(0x09A9CF), new Color(0x7D8F0C), new Color(0x3F3ED1),
            new Color(0xEF5B48),
            // Complementary hue/lightness families; original ten swatches remain unchanged.
            new Color(0xA45B12), new Color(0xBE487C), new Color(0x267A99),
            new Color(0x6F55A5), new Color(0x44864A), new Color(0xD2A323),
            new Color(0xC7663E), new Color(0x5478B8), new Color(0x884653),
            new Color(0x637477)
    };
    private static final int ORIGINAL_COLOR_COUNT=10;
    private Map<String,Color> categoryColors=Map.of();

    private List<Statistics.Bucket> buckets = List.of();
    private Map<String,String> names = Map.of();
    private long dailyTotalMs;
    private boolean rest;
    private Mode mode = Mode.COMPOSITION;
    private ChartStyle chartStyle=ChartStyle.BARS;
    private final Rectangle barsTab=new Rectangle(),curveTab=new Rectangle(),areaTab=new Rectangle();
    private int hover = -1;
    private Rectangle compositionTab = new Rectangle();
    private Rectangle totalTab = new Rectangle();
    private String empty = com.eye.i18n.I18n.text("暂无记录");

    public HourlyFocusChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(780, 430));
        setMinimumSize(new Dimension(0, 390));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 480));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        if (getAccessibleContext() != null) {
            getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("每小时专注时间分布图；可切换任务构成与合计专注时间，悬停查看每小时明细；可切换柱形、曲线与面积，上下方向键切换图形"));
        }
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int next = indexAt(e.getPoint());
                if (next != hover) { hover = next; repaint(); }
                boolean overTab = compositionTab.contains(e.getPoint()) || totalTab.contains(e.getPoint()) || barsTab.contains(e.getPoint()) || curveTab.contains(e.getPoint()) || areaTab.contains(e.getPoint());
                setCursor(Cursor.getPredefinedCursor(overTab ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if(barsTab.contains(e.getPoint()) || curveTab.contains(e.getPoint()) || areaTab.contains(e.getPoint())) {
                    setChartStyle(areaTab.contains(e.getPoint())?ChartStyle.AREA:curveTab.contains(e.getPoint())?ChartStyle.CURVE:ChartStyle.BARS);requestFocusInWindow();return;
                }
                if (compositionTab.contains(e.getPoint())) {
                    mode = Mode.COMPOSITION; repaint(); requestFocusInWindow(); return;
                }
                if (totalTab.contains(e.getPoint())) {
                    mode = Mode.TOTAL; repaint(); requestFocusInWindow(); return;
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if(e.getKeyCode()==KeyEvent.VK_UP || e.getKeyCode()==KeyEvent.VK_DOWN) {
                    int delta=e.getKeyCode()==KeyEvent.VK_DOWN?1:-1;setChartStyle(ChartStyle.values()[Math.floorMod(chartStyle.ordinal()+delta,ChartStyle.values().length)]);e.consume();return;
                }
                if (e.getKeyCode()==KeyEvent.VK_LEFT || e.getKeyCode()==KeyEvent.VK_RIGHT || e.getKeyCode()==KeyEvent.VK_SPACE || e.getKeyCode()==KeyEvent.VK_ENTER) {
                    mode = mode==Mode.COMPOSITION ? Mode.TOTAL : Mode.COMPOSITION;
                    repaint(); e.consume();
                }
            }
        });
    }

    public void data(List<Statistics.Bucket> data, Map<String,String> labels, long dailyTotalMs, boolean rest) {
        this.buckets = List.copyOf(data);
        this.names = Map.copyOf(labels);
        this.categoryColors=allocateColors(data,labels);
        this.dailyTotalMs = Math.max(0, dailyTotalMs);
        this.rest = rest;
        this.hover = -1;
        repaint();
    }

    public void emptyMessage(String text) { empty = text == null ? com.eye.i18n.I18n.text("暂无记录") : text; }
    public boolean isCurveMode() { return chartStyle==ChartStyle.CURVE; }
    public ChartStyle chartStyle(){return chartStyle;}
    public void setChartStyle(ChartStyle style){chartStyle=Objects.requireNonNull(style);hover=-1;repaint();}
    public boolean isTotalMode() { return mode == Mode.TOTAL; }
    public void copyPresentationFrom(HourlyFocusChart source){mode=source.mode;chartStyle=source.chartStyle;}

    private static Color mix(Color a, Color b, double t) {
        t=Math.max(0,Math.min(1,t));
        return new Color(
                (int)Math.round(a.getRed()*(1-t)+b.getRed()*t),
                (int)Math.round(a.getGreen()*(1-t)+b.getGreen()*t),
                (int)Math.round(a.getBlue()*(1-t)+b.getBlue()*t));
    }

    public static Color colorFor(String id, String label) {
        String s = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (Statistics.FREE.equals(id) || s.contains("自由专注")) return new Color(0x09BDA0);
        if (s.contains("eyerelax")) return new Color(0xFF8400);
        if (s.contains("背单词") || s.contains("听力")) return new Color(0xE71D79);
        if (s.contains("科研") || s.contains("论文")) return new Color(0x8D24EC);
        if (s.contains("os") || s.contains("cpp") || s.contains("c++")) return new Color(0x075FF4);
        if (s.contains("英语口语") || s.contains("口语")) return new Color(0x09A9CF);
        if (s.contains("数学") || s.contains("算法")) return new Color(0x7D8F0C);
        if (s.contains("写作") || s.contains("整理")) return new Color(0x3F3ED1);
        if (s.contains("阅读") || s.contains("开发")) return new Color(0x13B975);
        if (s.contains("其它") || s.contains("其他")) return new Color(0xEF5B48);
        if (id != null && id.startsWith("@")) return Charts.color(id);
        if (s.contains("休息") || s.contains("提醒遮罩")) return Charts.color(label);
        return FALLBACK[Math.floorMod(Objects.hashCode(id),ORIGINAL_COLOR_COUNT)];
    }

    /** Prefer each existing category color, then allocate unused swatches within this chart. */
    static Map<String,Color> allocateColors(List<Statistics.Bucket> data,Map<String,String> labels){
        java.util.Set<String> ids=new java.util.TreeSet<>();
        for(var bucket:data)ids.addAll(bucket.values().keySet());
        Map<String,Color> result=new java.util.LinkedHashMap<>();java.util.Set<Color> used=new java.util.HashSet<>();
        // Preserve distinct legacy preferences before using any additional colors.
        java.util.List<String> collisions=new java.util.ArrayList<>();
        for(String id:ids){Color preferred=colorFor(id,labels.getOrDefault(id,id));
            if(id.startsWith("@")){result.put(id,preferred);used.add(preferred);}
            else if(used.add(preferred))result.put(id,preferred);else collisions.add(id);
        }
        for(String id:collisions){Color selected=null;for(Color color:FALLBACK)if(used.add(color)){selected=color;break;}
            result.put(id,selected==null?colorFor(id,labels.getOrDefault(id,id)):selected);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private Color categoryColor(String id) {
        return categoryColors.getOrDefault(id,colorFor(id,names.getOrDefault(id,id==null?"":id)));
    }
    /** The summary outside this canvas uses the very same collision-resolved swatch. */
    public Color displayColor(String id,String label){return categoryColors.getOrDefault(id,colorFor(id,label));}

    private Map<String,Long> totalsByKey() {
        Map<String,Long> totals = new LinkedHashMap<>();
        for (var b : buckets) for (var e : b.values().entrySet()) if (e.getValue()>0) totals.merge(e.getKey(),e.getValue(),Long::sum);
        return totals;
    }

    private List<String> legendKeys() {
        Map<String,Long> totals=totalsByKey();
        List<String> keys=new ArrayList<>(totals.keySet());
        keys.sort(Comparator.<String>comparingLong(k->totals.getOrDefault(k,0L)).reversed()
                .thenComparing(k->names.getOrDefault(k,k),Comparator.nullsLast(String::compareToIgnoreCase)));
        return keys;
    }

    private long maximum() {
        long raw=buckets.stream().mapToLong(Statistics.Bucket::total).max().orElse(0);
        long halfHour=30*60*1000L;
        long min=60*60*1000L;
        return Math.max(min,((raw+halfHour-1)/halfHour)*halfHour);
    }

    private int plotLeft() { return 12; }
    private int plotRight() { return Math.max(plotLeft()+1,getWidth()-88); }
    private boolean wrappedStyleTabs(){
        FontMetrics fm=getFontMetrics(Theme.font(12));
        int a=Math.max(88,fm.stringWidth(rest?com.eye.i18n.I18n.text("休息构成"):com.eye.i18n.I18n.text("任务构成"))+38);
        int b=Math.max(118,fm.stringWidth(rest?com.eye.i18n.I18n.text("合计休息时间"):com.eye.i18n.I18n.text("合计专注时间"))+38);
        return getWidth()<12+a+14+b+20+232+12;
    }
    private int headerOffset(){return wrappedStyleTabs()?48:0;}
    private boolean wrappedSummary(){return getFontMetrics(Theme.font(14)).stringWidth(com.eye.i18n.I18n.text("总计 ")+longDuration(dailyTotalMs))+getFontMetrics(Theme.font(13)).stringWidth(rangeText())+48>getWidth();}
    private int plotTop() { return 154+headerOffset()+(wrappedSummary()?24:0); }
    private int plotBottom() {
        // The task summary now lives below this chart as a normal Swing list,
        // so the plot can use the space that previously belonged to the legend card.
        return Math.max(plotTop()+150,getHeight()-54);
    }

    private double step() { return buckets.isEmpty()?1:(plotRight()-plotLeft())/(double)buckets.size(); }
    private int barWidth() { return Math.max(8,(int)Math.min(34,step()*0.78)); }
    private int barX(int i) { return plotLeft()+(int)Math.round(step()*i+(step()-barWidth())/2); }

    private int indexAt(Point p) {
        if (buckets.isEmpty()) return -1;
        int top=plotTop(),bottom=plotBottom();
        if (p.x<plotLeft() || p.x>plotRight() || p.y<top || p.y>bottom) return -1;
        int i=(int)((p.x-plotLeft())/Math.max(.0001,step()));
        return i>=0&&i<buckets.size()?i:-1;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        Theme.quality(g);
        // Use the desktop's native text rasterization on Windows/HiDPI screens.
        Object desktopHints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if(desktopHints instanceof Map<?,?> hints) g.addRenderingHints(hints);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        try {
            paintTabs(g);
            paintHeader(g);
            paintPlot(g);
            if (hover>=0 && hover<buckets.size()) paintHover(g,hover);
        } finally { g.dispose(); }
    }

    private void paintTabs(Graphics2D g) {
        int y=10,h=38,x=12,gap=14;
        String a=rest?com.eye.i18n.I18n.text("休息构成"):com.eye.i18n.I18n.text("任务构成");
        String b=rest?com.eye.i18n.I18n.text("合计休息时间"):com.eye.i18n.I18n.text("合计专注时间");
        g.setFont(Theme.font(12));
        int aw=Math.max(88,g.getFontMetrics().stringWidth(a)+38);
        int bw=Math.max(118,g.getFontMetrics().stringWidth(b)+38);
        compositionTab.setBounds(x,y,aw,h); totalTab.setBounds(x+aw+gap,y,bw,h);
        g.setColor(Theme.MINT);g.fill(new RoundRectangle2D.Double(x-4,y-4,aw+bw+gap+8,h+8,14,14));
        paintTab(g,compositionTab,a,mode==Mode.COMPOSITION);
        paintTab(g,totalTab,b,mode==Mode.TOTAL);
        int right=Math.max(12,getWidth()-244),styleY=wrappedStyleTabs()?y+48:y;
        barsTab.setBounds(right,styleY,72,h);curveTab.setBounds(right+80,styleY,72,h);areaTab.setBounds(right+160,styleY,72,h);
        g.setColor(Theme.MINT);g.fill(new RoundRectangle2D.Double(right-4,styleY-4,240,h+8,14,14));
        paintTab(g,barsTab,com.eye.i18n.I18n.text("柱形"),chartStyle==ChartStyle.BARS);paintTab(g,curveTab,com.eye.i18n.I18n.text("曲线"),chartStyle==ChartStyle.CURVE);paintTab(g,areaTab,com.eye.i18n.I18n.text("面积"),chartStyle==ChartStyle.AREA);
    }

    private void paintTab(Graphics2D g,Rectangle r,String text,boolean selected) {
        if (selected) {
            g.setColor(Theme.SURFACE); g.fill(new RoundRectangle2D.Double(r.x,r.y,r.width,r.height,10,10));
            g.setColor(Theme.SAGE); g.draw(new RoundRectangle2D.Double(r.x+.5,r.y+.5,r.width-1,r.height-1,10,10));
        }
        g.setColor(TAB_TEXT); g.setFont(Theme.font(12));
        FontMetrics fm=g.getFontMetrics();
        int tx=r.x+(r.width-fm.stringWidth(text))/2;
        g.drawString(text,tx,r.y+(r.height-fm.getHeight())/2+fm.getAscent());
    }

    private void paintHeader(Graphics2D g) {
        g.setColor(TITLE); g.setFont(Theme.bold(19));
        String title=rest?com.eye.i18n.I18n.text("休息时间分布 · 每小时休息构成"):com.eye.i18n.I18n.text("专注时间分布 · 每小时任务构成");
        if(chartStyle==ChartStyle.CURVE||mode==Mode.TOTAL)title=rest?com.eye.i18n.I18n.text("休息时间分布 · 每小时合计"):com.eye.i18n.I18n.text("专注时间分布 · 每小时合计");
        if(g.getFontMetrics().stringWidth(title)>getWidth()-24)g.setFont(Theme.bold(16));
        g.drawString(ellipsize(g,title,Math.max(1,getWidth()-24)),12,82+headerOffset());
        g.setColor(SUB); g.setFont(Theme.font(13));
        g.drawString(rangeText(),12,112+headerOffset());
        String total=com.eye.i18n.I18n.text("总计 ")+longDuration(dailyTotalMs);
        g.setFont(Theme.font(14)); FontMetrics fm=g.getFontMetrics();
        g.drawString(total,Math.max(12,getWidth()-12-fm.stringWidth(total)),112+headerOffset()+(wrappedSummary()?24:0));
    }

    private String rangeText() {
        if (buckets.isEmpty()) return "";
        String start=normalizeTick(buckets.get(0).label(),false);
        String end=normalizeTick(buckets.get(buckets.size()-1).endLabel(),true);
        if (end==null||end.isBlank()) end="";
        return start+" - "+end;
    }

    private void paintPlot(Graphics2D g) {
        int left=plotLeft(),right=plotRight(),top=plotTop(),bottom=plotBottom();
        int h=bottom-top; long max=maximum();
        float[] dash={5f,5f};
        Stroke original=g.getStroke();
        for(int n=0;n<=2;n++) {
            int y=bottom-(int)Math.round(h*n/2.0);
            if(n>0) g.setStroke(new BasicStroke(1f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10,dash,0));
            else g.setStroke(new BasicStroke(1f));
            g.setColor(GRID); g.drawLine(left,y,right,y);
            g.setStroke(original);
            g.setColor(AXIS); g.setFont(Theme.font(11));
            String value=n==0?com.eye.i18n.I18n.text("0 分钟"):formatAxis(max*n/2);
            g.drawString(value,right+12,y+4);
        }
        if (buckets.isEmpty()) {
            g.setColor(SUB); g.setFont(Theme.font(12)); g.drawString(empty,left+16,top+h/2); return;
        }

        List<String> order=legendKeys();
        int bw=barWidth();
        if(chartStyle==ChartStyle.BARS)for(int i=0;i<buckets.size();i++) {
            int x=barX(i);
            Paint old=g.getPaint();
            g.setPaint(new GradientPaint(x,top,TRACK_TOP,x,bottom,TRACK_BOTTOM));
            g.fill(new RoundRectangle2D.Double(x+(bw- Math.max(8,bw-2))/2.0,top,Math.max(8,bw-2),h,12,12));
            g.setPaint(old);
            var bucket=buckets.get(i);
            if(mode==Mode.TOTAL) paintTotalBar(g,x,bw,top,bottom,max,bucket.total());
            else paintCompositionBar(g,x,bw,top,bottom,max,bucket,order);
        }
        if(chartStyle==ChartStyle.CURVE)paintTrend(g,top,bottom,max);
        if(chartStyle==ChartStyle.AREA)paintArea(g,top,bottom,max,order);
        paintXAxis(g,left,right,bottom);
    }

    /** Original smooth interpolation through hourly totals, without overshoot. */
    private Path2D trendPath(int top,int bottom,long max) {
        int count=buckets.size();Path2D path=new Path2D.Double();if(count==0)return path;
        double[] xs=new double[count],ys=new double[count],slopes=new double[count];
        for(int i=0;i<count;i++) {
            xs[i]=barX(i)+barWidth()/2.0;
            ys[i]=bottom-Math.round(buckets.get(i).total()*(bottom-top)/(double)max);
        }
        if(count>1) {
            double[] secants=new double[count-1];
            for(int i=0;i<count-1;i++)secants[i]=(ys[i+1]-ys[i])/(xs[i+1]-xs[i]);
            slopes[0]=secants[0];slopes[count-1]=secants[count-2];
            for(int i=1;i<count-1;i++) {
                double a=secants[i-1],b=secants[i];
                if(a*b>0){double left=xs[i]-xs[i-1],right=xs[i+1]-xs[i],w1=2*right+left,w2=right+2*left;slopes[i]=(w1+w2)/(w1/a+w2/b);}
            }
        }
        path.moveTo(xs[0],ys[0]);
        for(int i=0;i<count-1;i++) {
            double third=(xs[i+1]-xs[i])/3;
            path.curveTo(xs[i]+third,ys[i]+slopes[i]*third,xs[i+1]-third,ys[i+1]-slopes[i+1]*third,xs[i+1],ys[i+1]);
        }
        return path;
    }

    private void paintTrend(Graphics2D g,int top,int bottom,long max) {
        if(buckets.size()<2 || buckets.stream().noneMatch(b->b.total()>0))return;
        Stroke old=g.getStroke();
        g.setColor(new Color(0x77839E));
        g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(trendPath(top,bottom,max));g.setStroke(old);
    }

    /** Straight segments join hourly observations; no smoothing or invented intermediate peaks. */
    private void paintArea(Graphics2D g,int top,int bottom,long max,List<String> order){
        if(buckets.isEmpty())return;
        int count=buckets.size();double[] base=new double[count];
        List<String> layers=mode==Mode.TOTAL?List.of("@total"):order;
        for(String key:layers){
            double[] upper=base.clone();boolean nonzero=false;
            for(int i=0;i<count;i++){long value=mode==Mode.TOTAL?buckets.get(i).total():buckets.get(i).values().getOrDefault(key,0L);upper[i]+=value;nonzero|=value>0;}
            if(!nonzero)continue;
            Path2D band=new Path2D.Double(),line=new Path2D.Double();
            double firstX=plotLeft(),lastX=plotRight();
            band.moveTo(firstX,bottom-upper[0]*(bottom-top)/max);line.moveTo(firstX,bottom-upper[0]*(bottom-top)/max);
            for(int i=0;i<count;i++){double x=plotLeft()+step()*(i+.5),y=bottom-upper[i]*(bottom-top)/max;band.lineTo(x,y);line.lineTo(x,y);}
            band.lineTo(lastX,bottom-upper[count-1]*(bottom-top)/max);line.lineTo(lastX,bottom-upper[count-1]*(bottom-top)/max);
            band.lineTo(lastX,bottom-base[count-1]*(bottom-top)/max);
            for(int i=count-1;i>=0;i--)band.lineTo(plotLeft()+step()*(i+.5),bottom-base[i]*(bottom-top)/max);
            band.lineTo(firstX,bottom-base[0]*(bottom-top)/max);band.closePath();
            Color color=mode==Mode.TOTAL?TOTAL_GREEN:categoryColor(key);Paint oldPaint=g.getPaint();Stroke oldStroke=g.getStroke();
            g.setPaint(new GradientPaint(0,top,new Color(color.getRed(),color.getGreen(),color.getBlue(),126),0,bottom,new Color(color.getRed(),color.getGreen(),color.getBlue(),45)));g.fill(band);
            g.setPaint(oldPaint);g.setColor(color);g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(line);g.setStroke(oldStroke);base=upper;
        }
    }

    private void paintCompositionBar(Graphics2D g,int x,int bw,int top,int bottom,long max,Statistics.Bucket bucket,List<String> order) {
        if(bucket.total()<=0)return;
        long sum=0;
        int stackTop=bottom-(int)Math.round(bucket.total()*(bottom-top)/(double)max);
        Shape outline=new RoundRectangle2D.Double(x,stackTop,bw,Math.max(2,bottom-stackTop),10,10);
        Shape oldClip=g.getClip();g.clip(outline);
        for(String key:order) {
            long value=bucket.values().getOrDefault(key,0L); if(value<=0)continue;
            long previous=sum; sum+=value;
            int yBottom=bottom-(int)Math.round(previous*(bottom-top)/(double)max);
            int y=bottom-(int)Math.round(sum*(bottom-top)/(double)max);
            int height=Math.max(2,yBottom-y);
            Color base=categoryColor(key);
            Shape shape=new Rectangle2D.Double(x,y,bw,height);
            Paint old=g.getPaint();
            g.setPaint(new GradientPaint(x,y,mix(base,Color.WHITE,.68),x,y+height,mix(base,Color.WHITE,.45)));
            g.fill(shape); g.setPaint(old); g.setColor(base); g.setStroke(new BasicStroke(1.45f));
            if(previous>0)g.draw(new Line2D.Double(x,yBottom,x+bw,yBottom));
            // Stroke the rounded outer silhouette without clipping off its outer half.
            // Each vertical slice keeps the outline colour of its own task segment.
            g.setClip(oldClip);
            double borderTop=sum==bucket.total()?y-2:y;
            double borderBottom=previous==0?bottom+2:yBottom;
            g.clip(new Rectangle2D.Double(x-2,borderTop,bw+4,borderBottom-borderTop));
            g.draw(outline);
            g.setClip(oldClip);g.clip(outline);
        }
        g.setClip(oldClip);
    }

    private void paintTotalBar(Graphics2D g,int x,int bw,int top,int bottom,long max,long value) {
        if(value<=0)return;
        int y=bottom-(int)Math.round(value*(bottom-top)/(double)max);
        int height=Math.max(2,bottom-y);
        Shape shape=new RoundRectangle2D.Double(x,y,bw,height,Math.min(10,height),Math.min(10,height));
        Paint old=g.getPaint(); g.setPaint(new GradientPaint(x,y,TOTAL_GREEN_TOP,x,bottom,TOTAL_GREEN_BOTTOM));
        g.fill(shape); g.setPaint(old); g.setColor(TOTAL_GREEN); g.setStroke(new BasicStroke(1.55f)); g.draw(shape);
    }

    private void paintXAxis(Graphics2D g,int left,int right,int bottom) {
        g.setColor(AXIS); g.setFont(Theme.font(11));
        int count=buckets.size();
        if(count==0)return;
        LinkedHashSet<Integer> indices=new LinkedHashSet<>();
        for(int i=0;i<=count;i+=4)indices.add(i);
        indices.add(count);
        FontMetrics fm=g.getFontMetrics();
        String endLabel=normalizeTick(buckets.get(count-1).endLabel(),true);
        int endWidth=fm.stringWidth(endLabel),endLeft=Math.max(0,Math.min(right-endWidth/2,getWidth()-endWidth));
        int previousRight=-12;
        for(int idx:indices) {
            if(idx<0||idx>count)continue;
            String label;
            if(idx==count) label=normalizeTick(buckets.get(count-1).endLabel(),true);
            else label=normalizeTick(buckets.get(idx).label(),false);
            int x=left+(int)Math.round((right-left)*idx/(double)count);
            int tx=x-fm.stringWidth(label)/2;
            tx=Math.max(0,Math.min(tx,getWidth()-fm.stringWidth(label)));
            // The beginning and end define the learning-day window. Suppress only
            // intermediate ticks when their translated text would collide.
            if(idx!=0&&idx!=count&&(tx<previousRight+10||tx+fm.stringWidth(label)>endLeft-10))continue;
            g.drawString(label,tx,bottom+28);
            previousRight=tx+fm.stringWidth(label);
        }
    }

    private String normalizeTick(String raw, boolean ending) {
        if(raw==null)return "";
        String s=raw.trim(),prefix=com.eye.i18n.I18n.text("次日");
        boolean next=s.startsWith(prefix);
        String time=next?s.substring(prefix.length()).strip():s;
        if(next&&time.equals("00:00"))return "24:00";
        if(next)return prefix+(com.eye.i18n.I18n.isEnglish()?" ":"")+time;
        if (ending && s.matches("\\d{2}:\\d{2}") && !buckets.isEmpty()) {
            // End of the study window is the following learning-day boundary.
            return prefix+(com.eye.i18n.I18n.isEnglish()?" ":"")+s;
        }
        return s;
    }

    private void paintLegend(Graphics2D g) {
        int x=12,y=plotBottom()+48,w=Math.max(1,getWidth()-24),h=Math.max(80,getHeight()-y-10);
        g.setColor(LEGEND_FILL); g.fill(new RoundRectangle2D.Double(x,y,w,h,20,20));
        g.setColor(LEGEND_LINE); g.draw(new RoundRectangle2D.Double(x+.5,y+.5,w-1,h-1,20,20));
        if(mode==Mode.TOTAL) {
            int sy=y+24; paintSwatch(g,x+28,sy,TOTAL_GREEN,TOTAL_GREEN_TOP,TOTAL_GREEN_BOTTOM);
            g.setFont(Theme.font(12));g.setColor(new Color(0x59647F));g.drawString(rest?com.eye.i18n.I18n.text("合计休息时间"):com.eye.i18n.I18n.text("合计专注时间"),x+80,sy+22);
            g.setFont(Theme.font(10));g.setColor(new Color(0x8390AD));
            g.drawString(com.eye.i18n.I18n.text("每列表示对应小时的总时长；切回“")+(rest?com.eye.i18n.I18n.text("休息构成"):com.eye.i18n.I18n.text("任务构成"))+com.eye.i18n.I18n.text("”可查看分类明细。"),x+28,sy+54);
            return;
        }
        List<String> keys=legendKeys();
        if(keys.isEmpty()) {
            g.setFont(Theme.font(11));g.setColor(SUB);g.drawString(com.eye.i18n.I18n.text("暂无分类记录"),x+28,y+45);return;
        }
        int cols=w>=820?5:3;
        int shown=Math.min(10,keys.size());
        int colW=(w-36)/cols;
        for(int i=0;i<shown;i++) {
            int col=i%cols,row=i/cols;
            int sx=x+22+col*colW, sy=y+20+row*42;
            String key=keys.get(i); Color base=categoryColor(key);
            paintSwatch(g,sx,sy,base,mix(base,Color.WHITE,.68),mix(base,Color.WHITE,.45));
            String label=names.getOrDefault(key,key);
            if(label==null||label.isBlank())label=com.eye.i18n.I18n.text("未命名任务");
            int available=Math.max(24,colW-54);
            g.setFont(Theme.font(11));g.setColor(new Color(0x59647F));
            g.drawString(ellipsize(g,label,available),sx+50,sy+22);
        }
        if(keys.size()>shown) {
            g.setFont(Theme.font(9));g.setColor(new Color(0x8390AD));
            g.drawString(com.eye.i18n.I18n.text("另有 ")+(keys.size()-shown)+com.eye.i18n.I18n.text(" 个分类可在悬停明细中查看"),x+w-220,y+h-12);
        }
    }

    private void paintSwatch(Graphics2D g,int x,int y,Color stroke,Color top,Color bottom) {
        Shape s=new RoundRectangle2D.Double(x,y,34,34,12,12); Paint old=g.getPaint();
        g.setPaint(new GradientPaint(x,y,top,x,y+34,bottom));g.fill(s);g.setPaint(old);
        g.setColor(stroke);g.setStroke(new BasicStroke(1.7f));g.draw(s);
    }

    private String ellipsize(Graphics2D g,String text,int width) {
        if(g.getFontMetrics().stringWidth(text)<=width)return text;
        String ell="…"; int ew=g.getFontMetrics().stringWidth(ell);
        StringBuilder b=new StringBuilder(text);
        while(b.length()>1&&g.getFontMetrics().stringWidth(b.toString())+ew>width)b.setLength(b.length()-1);
        return b+ell;
    }

    private void paintHover(Graphics2D g,int i) {
        Statistics.Bucket bucket=buckets.get(i);
        int left=plotLeft(),right=plotRight(),top=plotTop(),bottom=plotBottom();
        long max=maximum(),total=bucket.total();
        int bw=barWidth(),x=barX(i),cx=x+bw/2;
        int barTop=bottom-(int)Math.round(Math.min(total,max)*(bottom-top)/(double)max);

        Stroke oldStroke=g.getStroke();
        g.setColor(new Color(0x687491));
        g.setStroke(new BasicStroke(1.4f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10,new float[]{4,5},0));
        g.drawLine(cx,top-8,cx,Math.max(top,barTop-10));g.setStroke(oldStroke);

        int markerY=Math.max(top+3,barTop-2);
        g.setStroke(new BasicStroke(1.8f));
        g.setColor(Color.WHITE);g.fill(new Ellipse2D.Double(cx-10,markerY-10,20,20));
        g.setColor(mode==Mode.TOTAL?new Color(0x75C493):new Color(0xF0A291));g.draw(new Ellipse2D.Double(cx-10,markerY-10,20,20));
        g.setColor(new Color(0x535A70));g.fill(new Ellipse2D.Double(cx-5,markerY-5,10,10));

        List<Map.Entry<String,Long>> rows=new ArrayList<>(bucket.values().entrySet());
        rows.removeIf(e->e.getValue()<=0);
        rows.sort(Map.Entry.<String,Long>comparingByValue().reversed());
        if(mode==Mode.TOTAL && total>0) rows=List.of(Map.entry("@total",total));

        int visible=Math.min(8,rows.size());
        String header=bucketLabel(bucket)+(total<=0?com.eye.i18n.I18n.text("  无"):"");
        g.setFont(Theme.font(13));
        int tw=g.getFontMetrics().stringWidth(header)+36;
        g.setFont(Theme.font(12));
        for(int n=0;n<visible;n++) {
            var row=rows.get(n);
            String label=mode==Mode.TOTAL?com.eye.i18n.I18n.text("总")+(rest?com.eye.i18n.I18n.text("休息"):com.eye.i18n.I18n.text("专注"))+com.eye.i18n.I18n.text("时间"):names.getOrDefault(row.getKey(),row.getKey());
            tw=Math.max(tw,84+g.getFontMetrics().stringWidth(label)+g.getFontMetrics().stringWidth(tooltipDuration(row.getValue())));
        }
        tw=Math.min(Math.max(120,tw),Math.max(120,getWidth()-16));
        int th=total<=0?44:42+visible*30+(rows.size()>visible?22:0);
        int tx=cx+22,ty=Math.max(8,Math.min(markerY-th/2,getHeight()-th-8));
        if(tx+tw>getWidth()-8)tx=cx-tw-22;
        if(tx<8)tx=8;
        Shape box=new RoundRectangle2D.Double(tx,ty,tw,th,13,13);
        g.setColor(TOOLTIP);g.fill(box);
        Path2D pointer=new Path2D.Double();
        int pointerY=Math.max(ty+12,Math.min(markerY,ty+th-12));
        if(tx>cx){pointer.moveTo(tx,pointerY-7);pointer.lineTo(cx+11,markerY);pointer.lineTo(tx,pointerY+7);}
        else{pointer.moveTo(tx+tw,pointerY-7);pointer.lineTo(cx-11,markerY);pointer.lineTo(tx+tw,pointerY+7);}
        pointer.closePath();g.fill(pointer);

        g.setColor(TOOLTIP_TEXT);g.setFont(Theme.font(13));
        g.drawString(header,tx+18,ty+30);
        if(total<=0)return;
        if(mode==Mode.TOTAL) {
            paintTooltipRow(g,tx,ty+56,TOTAL_GREEN,com.eye.i18n.I18n.text("总")+(rest?com.eye.i18n.I18n.text("休息"):com.eye.i18n.I18n.text("专注"))+com.eye.i18n.I18n.text("时间"),total,tw);
        } else {
            int yy=ty+56;
            for(int n=0;n<visible;n++,yy+=30) {
                var row=rows.get(n); paintTooltipRow(g,tx,yy,categoryColor(row.getKey()),names.getOrDefault(row.getKey(),row.getKey()),row.getValue(),tw);
            }
            if(rows.size()>visible){g.setFont(Theme.font(9));g.setColor(new Color(0xD9DDE8));g.drawString(com.eye.i18n.I18n.text("另有 ")+(rows.size()-visible)+com.eye.i18n.I18n.text(" 个分类"),tx+18,ty+th-10);}
        }
    }

    private void paintTooltipRow(Graphics2D g,int tx,int y,Color color,String label,long value,int tw) {
        g.setColor(mix(color,Color.WHITE,.50));g.fill(new RoundRectangle2D.Double(tx+18,y-13,16,16,6,6));
        g.setColor(color);g.setStroke(new BasicStroke(1.2f));g.draw(new RoundRectangle2D.Double(tx+18,y-13,16,16,6,6));
        g.setColor(TOOLTIP_TEXT);g.setFont(Theme.font(12));
        String name=label==null||label.isBlank()?com.eye.i18n.I18n.text("未命名任务"):label;
        g.drawString(ellipsize(g,name,Math.max(20,tw-84-g.getFontMetrics().stringWidth(tooltipDuration(value)))),tx+48,y);
        String duration=tooltipDuration(value);FontMetrics fm=g.getFontMetrics();g.drawString(duration,tx+tw-18-fm.stringWidth(duration),y);
    }

    private String bucketLabel(Statistics.Bucket b) {
        String start=b.label()==null?"":b.label().replace(com.eye.i18n.I18n.text("次日 "),com.eye.i18n.I18n.text("次日"));
        String end=b.endLabel()==null?"":b.endLabel().replace(com.eye.i18n.I18n.text("次日 "),com.eye.i18n.I18n.text("次日"));
        if(end.equals(com.eye.i18n.I18n.text("次日00:00")))end="24:00";
        return start+" – "+end;
    }

    private static String formatAxis(long ms) {
        long minutes=Math.round(ms/60000.0);
        return minutes+com.eye.i18n.I18n.text(" 分钟");
    }

    private static String tooltipDuration(long ms) {
        long sec=Math.max(0,Math.round(ms/1000.0));
        if(sec>=3600){long h=sec/3600,m=sec%3600/60;return h+com.eye.i18n.I18n.text(" 小时")+(m>0?" "+m+com.eye.i18n.I18n.text(" 分钟"):"");}
        if(sec>=60){long m=sec/60,s=sec%60;return m+com.eye.i18n.I18n.text(" 分钟")+(s>0?" "+s+com.eye.i18n.I18n.text(" 秒"):"");}
        return sec+com.eye.i18n.I18n.text(" 秒");
    }

    private static String longDuration(long ms) {
        long sec=Math.max(0,Math.round(ms/1000.0)); long h=sec/3600,m=sec%3600/60;
        if(h>0)return h+com.eye.i18n.I18n.text(" 小时 ")+m+com.eye.i18n.I18n.text(" 分钟");
        if(m>0)return m+com.eye.i18n.I18n.text(" 分钟");
        return sec+com.eye.i18n.I18n.text(" 秒");
    }
}
