package com.eye.plan;

import com.eye.core.*;
import com.eye.stats.*;
import com.eye.ui.theme.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Rolling calendar -> day drill-down -> trends/tasks. Historical date ownership is immutable. */
public final class ActivityPanel extends JPanel {
    private final PlanManager manager;
    private final Config config;
    private final JComboBox<String> metricBox=new JComboBox<>(new String[]{com.eye.i18n.I18n.text("专注时长"),com.eye.i18n.I18n.text("休息时长"),com.eye.i18n.I18n.text("任务完成度")});
    private final JSlider heatmapDays=new JSlider(30,300,240);
    private final JComboBox<TaskChoice> taskBox=new JComboBox<>();
    private final JComboBox<TaskChoice> analysisTaskBox=new JComboBox<>();
    private final JTextField taskSearch=new SettingsUi.PlaceholderField(com.eye.i18n.I18n.text("搜索任务名称"));
    private final JCheckBox includeUnrecorded=new JCheckBox(com.eye.i18n.I18n.text("包含无记录的周期计划"));
    private final JPanel taskFilters=Theme.panel(new FlowLayout(FlowLayout.LEFT,8,0));
    private final JComboBox<String> trendRange=new JComboBox<>(new String[]{com.eye.i18n.I18n.text("最近 7 天"),com.eye.i18n.I18n.text("最近 30 天"),com.eye.i18n.I18n.text("最近一年"),com.eye.i18n.I18n.text("自定义…")});
    private final JLabel note=Theme.label(com.eye.i18n.I18n.text("正在读取本地记录…"),10.5f,Theme.SECONDARY);
    private final JLabel rangeLabel=Theme.label("",11,Theme.SECONDARY);
    private final Heatmap heatmap=new Heatmap();
    private final HourlyFocusChart hourlyChart=new HourlyFocusChart();
    private final SettingsUi.Column column=new SettingsUi.Column();
    private final JPanel detailHost=Theme.panel(new BorderLayout());
    private final JLabel[] summaryValues=new JLabel[4];
    private final JLabel[] summaryLabels=new JLabel[4];
    private final List<JButton> analysisTabs=new ArrayList<>();
    private JComponent tutorialDayExport,tutorialInsightsSummary,tutorialInsightsExport;
    JComponent tutorialTarget(String key){if("heatmap".equals(key))return heatmapDays;if("insights-range".equals(key))return trendRange;JComponent target=switch(key){case "day-export"->tutorialDayExport;case "insights-summary"->tutorialInsightsSummary;case "insights-export"->tutorialInsightsExport;default->null;};if(target!=null&&target.isShowing())return target;String tab=key.startsWith("insights-")?"insights":"day-export".equals(key)?"day":key;return analysisTabs.stream().filter(b->tab.equals(b.getActionCommand())).findFirst().orElse(null);}
    void prepareTutorialTarget(String key){String mode=key.startsWith("insights-")?"insights":"day-export".equals(key)?"day":key;if(!Set.of("day","trend","task","insights").contains(mode))return;detailMode=mode;if(data!=null)renderDetail();}
    private Statistics.Snapshot data;
    private LocalDate selectedDay,customFrom,customTo;
    private String detailMode="day";
    private boolean filling;
    private boolean followToday=true, intervalsExpanded;
    private int generation;
    private long lastRefresh;
    private final javax.swing.Timer autoRefresh=new javax.swing.Timer(60000,e->{if(isShowing())refresh();});
    public ActivityPanel(PlanManager manager){this(manager,new Config());}
    public ActivityPanel(PlanManager manager,Config config){
        this.manager=manager;this.config=config==null?new Config():config;
        setOpaque(false);setLayout(new BorderLayout());
        metricBox.setPreferredSize(new Dimension(128,34));
        taskBox.setPreferredSize(new Dimension(170,34));taskBox.addItem(new TaskChoice(null,com.eye.i18n.I18n.text("全部任务")));taskBox.setVisible(false);
        analysisTaskBox.setPreferredSize(new Dimension(210,34));trendRange.setPreferredSize(new Dimension(136,34));trendRange.setSelectedIndex(1);
        Theme.Surface heatCard=surface(new BorderLayout(0,12));
        JPanel top=Theme.panel(new BorderLayout(12,0){
            @Override public void layoutContainer(Container parent){
                super.layoutContainer(parent);Component selector=getLayoutComponent(BorderLayout.EAST);Component metrics=getLayoutComponent(BorderLayout.WEST);
                if(selector==null||metrics==null)return;int right=heatmap.gridGeometry(parent.getWidth()).right();
                int width=Math.min(selector.getPreferredSize().width,Math.max(1,right-metrics.getWidth()-12));
                selector.setBounds(right-width,selector.getY(),width,selector.getHeight());
            }
        });
        JPanel selectors=Theme.panel(new FlowLayout(FlowLayout.LEFT,7,0));selectors.add(metricBox);
        JPanel daySelector=new QuickRange(com.eye.i18n.I18n.text("显示天数"),heatmapDays,n->n+com.eye.i18n.I18n.text("天"),30,60,90,120,150,180,210,240,270,300);
        daySelector.putClientProperty("tutorial.region",heatCard);daySelector.setPreferredSize(new Dimension(410,82));
        selectors.add(taskBox);top.add(daySelector,BorderLayout.EAST);
        top.add(selectors,BorderLayout.WEST);heatCard.add(top,BorderLayout.NORTH);
        JPanel heatContent=Theme.panel(new BorderLayout(0,8));
        JPanel stats=Theme.panel(new GridLayout(1,4,0,0));
        stats.setBorder(new EmptyBorder(6,0,14,0));
        for(int i=0;i<4;i++){
            JPanel p=Theme.panel(new BorderLayout(0,10));
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,0,i<3?1:0,Theme.LINE),new EmptyBorder(4,i==0?0:18,4,14)));
            summaryValues[i]=fitHeading("—",23);summaryLabels[i]=Theme.label("",10.5f,Theme.SECONDARY);
            p.add(summaryLabels[i],BorderLayout.NORTH);p.add(summaryValues[i],BorderLayout.CENTER);stats.add(p);
        }
        heatContent.add(stats,BorderLayout.NORTH);heatContent.add(heatmap,BorderLayout.CENTER);
        JPanel footer=Theme.panel(new BorderLayout());footer.add(rangeLabel,BorderLayout.WEST);
        JPanel colors=Theme.panel(new FlowLayout(FlowLayout.RIGHT,3,0));colors.add(Theme.label(com.eye.i18n.I18n.text("少"),10,Theme.SECONDARY));
        for(Color c:Heatmap.COLORS){JPanel swatch=new JPanel();swatch.setBackground(c);swatch.setPreferredSize(new Dimension(9,9));colors.add(swatch);}
        colors.add(Theme.label(com.eye.i18n.I18n.text("多"),10,Theme.SECONDARY));footer.add(colors,BorderLayout.EAST);heatContent.add(footer,BorderLayout.SOUTH);
        heatCard.add(heatContent,BorderLayout.CENTER);column.add(heatCard);column.add(Box.createVerticalStrut(16));
        Theme.Surface nav=new Theme.Surface(new FlowLayout(FlowLayout.LEFT,4,4),Theme.PAGE,null,14);
        String[][] tabs={{"day",com.eye.i18n.I18n.text("单日复盘")},{"trend",com.eye.i18n.I18n.text("趋势分析")},{"task",com.eye.i18n.I18n.text("任务分析")},{"insights",com.eye.i18n.I18n.text("专注洞察")}};
        String[] analysisIcons={"calendar","chart","target","grid"};int tabIndex=0;
        for(String[] t:tabs){JButton b=new JButton(t[1]);Theme.smallButton(b,"segment",analysisIcons[tabIndex++]);b.setActionCommand(t[0]);
            b.addActionListener(e->{detailMode=t[0];if("insights".equals(detailMode)&&data!=null&&range()[0].minusDays(Math.min(365,ChronoUnit.DAYS.between(range()[0],range()[1])+1)).isBefore(data.from()))refresh();else renderDetail();});nav.add(b);analysisTabs.add(b);}
        nav.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));column.add(nav);column.add(Box.createVerticalStrut(16));
        Theme.field(taskSearch);taskSearch.setFont(Theme.font(12));taskSearch.setPreferredSize(new Dimension(210,34));taskSearch.setToolTipText(null);
        taskFilters.add(taskSearch);taskFilters.add(includeUnrecorded);taskFilters.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));taskFilters.setVisible(false);column.add(taskFilters);
        taskSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){private void changed(){if(data!=null){fillTaskChoices();renderDetail();}}public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}});
        includeUnrecorded.addActionListener(e->{if(data!=null){fillTaskChoices();render();}});
        detailHost.setAlignmentX(LEFT_ALIGNMENT);column.add(detailHost);
        note.setBorder(new EmptyBorder(10,0,0,0));column.add(note);
        JScrollPane scroll=new JScrollPane(column);Theme.scroll(scroll);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);add(scroll);
        heatmap.setSelectionListener(day->{detailMode="day";selectDay(day);});
        metricBox.addActionListener(e->{taskBox.setVisible(metricBox.getSelectedIndex()==2);render();});
        heatmapDays.addChangeListener(e->{if(data!=null)fillTaskChoices();render();top.revalidate();top.doLayout();});taskBox.addActionListener(e->{if(!filling)render();});
        analysisTaskBox.addActionListener(e->{if(!filling)renderDetail();});
        trendRange.addActionListener(e->{if(filling)return;if(trendRange.getSelectedIndex()==3&&!chooseCustomRange())return;refresh();});
        addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0){if(isShowing()){autoRefresh.start();if(System.currentTimeMillis()-lastRefresh>1000)refresh();}else autoRefresh.stop();}});
    }
    private record TaskChoice(String id,String name){public String toString(){return name;}}
    static String dayNavigationLabel(LocalDate selected,LocalDate today){
        long age=ChronoUnit.DAYS.between(selected,today);
        return switch((int)age){case 0->com.eye.i18n.I18n.text("今天");case 1->com.eye.i18n.I18n.text("昨天");case 2->com.eye.i18n.I18n.text("前天");default->selected.format(DateTimeFormatter.ISO_LOCAL_DATE);};
    }
    private Theme.Surface surface(LayoutManager layout){Theme.Surface c=new Theme.Surface(layout,Theme.SURFACE,Theme.LINE,16){
        @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};
        c.setBorder(new EmptyBorder(18,20,18,20));c.setAlignmentX(LEFT_ALIGNMENT);return c;}
    private JComponent card(String title,String subtitle,JComponent body){Theme.Surface c=surface(new BorderLayout(0,12));
        JPanel head=new SettingsUi.Column();head.add(Theme.heading(title,15));if(subtitle!=null&&!subtitle.isBlank()){head.add(Box.createVerticalStrut(5));head.add(new SettingsUi.Text(subtitle,10.5f,Theme.SECONDARY));}
        c.add(head,BorderLayout.NORTH);c.add(body,BorderLayout.CENTER);return c;}
    private static void addSection(JPanel list,JComponent item){item.setAlignmentX(LEFT_ALIGNMENT);list.add(item);list.add(Box.createVerticalStrut(14));}
    public void refresh(){
        int ticket=++generation;lastRefresh=System.currentTimeMillis();LocalDate until=manager.getToday();
        if(followToday||selectedDay==null||selectedDay.isAfter(until))selectedDay=until;
        LocalDate start=until.minusDays("insights".equals(detailMode)&&trendRange.getSelectedIndex()==2?739:371);if(customFrom!=null){LocalDate compareFrom=customFrom.minusDays(Math.min(365,ChronoUnit.DAYS.between(customFrom,customTo)+1));if(compareFrom.isBefore(start))start=compareFrom;}
        if(selectedDay.minusDays(10).isBefore(start))start=selectedDay.minusDays(10);
        final LocalDate from=start;
        new SwingWorker<Statistics.Snapshot,Void>(){
            protected Statistics.Snapshot doInBackground(){return Statistics.load(manager,config,from,until);}
            protected void done(){if(ticket!=generation)return;try{data=get();fillTaskChoices();render();note.setVisible(false);}catch(Exception e){note.setVisible(true);note.setForeground(Theme.DANGER);note.setText(com.eye.i18n.I18n.text("读取统计失败：")+e.getMessage());}}
        }.execute();
    }
    private void fillTaskChoices(){
        filling=true;TaskChoice old=(TaskChoice)taskBox.getSelectedItem(),analysisOld=(TaskChoice)analysisTaskBox.getSelectedItem();
        taskBox.removeAllItems();taskBox.addItem(new TaskChoice(null,com.eye.i18n.I18n.text("全部任务")));analysisTaskBox.removeAllItems();
        Map<String,String> heatNames=new TreeMap<>(Statistics.taskNamesInRange(data,data.to().minusDays(heatmapDays.getValue()-1),data.to()));
        LocalDate[] dates=range();Map<String,String> names=new TreeMap<>(Statistics.taskNamesInRange(data,dates[0],dates[1]));
        Set<String> pinned=new HashSet<>(),analysisEligibleIds=new HashSet<>(data.analysisEligibleTaskIds());
        for(PlanItem plan:manager.listAll()){if(Statistics.isAnalysisTask(plan))analysisEligibleIds.add(plan.id);else analysisEligibleIds.remove(plan.id);if(plan.pinned)pinned.add(plan.id);if(includeUnrecorded.isSelected()&&!plan.template){names.putIfAbsent(plan.id,plan.name);heatNames.putIfAbsent(plan.id,plan.name);}}
        if(includeUnrecorded.isSelected()){data.taskNames().forEach(names::putIfAbsent);data.taskNames().forEach(heatNames::putIfAbsent);}
        names.keySet().retainAll(analysisEligibleIds);
        Comparator<Map.Entry<String,String>> order=Comparator.comparing((Map.Entry<String,String> e)->!pinned.contains(e.getKey())).thenComparing(Map.Entry::getValue,String.CASE_INSENSITIVE_ORDER);
        for(var e:heatNames.entrySet().stream().sorted(order).toList()){TaskChoice t=new TaskChoice(e.getKey(),e.getValue());taskBox.addItem(t);if(old!=null&&Objects.equals(old.id,t.id))taskBox.setSelectedItem(t);}
        String query=taskSearch.getText().strip().toLowerCase(Locale.ROOT);
        for(var e:names.entrySet().stream().filter(e->e.getValue().toLowerCase(Locale.ROOT).contains(query)).sorted(order).toList()){TaskChoice t=new TaskChoice(e.getKey(),e.getValue());analysisTaskBox.addItem(t);if(analysisOld!=null&&Objects.equals(analysisOld.id,t.id))analysisTaskBox.setSelectedItem(t);}
        filling=false;
    }
    private void render(){if(data==null)return;
        int metric=metricBox.getSelectedIndex(),n=heatmapDays.getValue();
        LocalDate end=data.to(),start=end.minusDays(n-1);Map<LocalDate,Heatmap.Day> values=new LinkedHashMap<>();
        TaskChoice selected=(TaskChoice)taskBox.getSelectedItem();String id=selected==null?null:selected.id;
        long sum=0,target=0;int active=0,streak=0,longest=0;double currentValue=0;
        for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1)){
            Statistics.Day day=data.day(d);long raw=metric==0?day.focusMs():day.restMs();double fraction;
            if(metric==2){long[] totals=completion(day,id);raw=totals[0];target+=totals[1];fraction=totals[1]>0?raw/(double)totals[1]:0;}
            else fraction=Math.min(1,raw/(metric==0?4*3600000.0:3600000.0));
            sum+=raw;if(raw>0){active++;longest=Math.max(longest,++streak);}else streak=0;
            values.put(d,new Heatmap.Day(true,fraction,tooltip(day,metric,id),""));
            if(d.equals(end))currentValue=metric==2?fraction:raw;
        }
        summaryValues[0].setText(metric==2?(target>0?Math.round(sum*100.0/target):0)+"%":Statistics.duration(sum));
        summaryLabels[0].setText(metric==2?com.eye.i18n.I18n.text("投入达成率"):metric==1?com.eye.i18n.I18n.text("累计休息"):com.eye.i18n.I18n.text("累计专注"));
        summaryValues[1].setText(active+com.eye.i18n.I18n.text(" 天"));summaryLabels[1].setText(com.eye.i18n.I18n.text("有记录的学习日"));
        summaryValues[2].setText(longest+com.eye.i18n.I18n.text(" 天"));summaryLabels[2].setText(com.eye.i18n.I18n.text("最长连续记录"));
        summaryValues[3].setText(metric==2?Math.round(currentValue*100)+"%":Statistics.duration((long)currentValue));summaryLabels[3].setText(com.eye.i18n.I18n.text("当前学习日"));
        heatmap.setData(start,end,values);heatmap.setSelected(selectedDay);
        rangeLabel.setText(start.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))+" — "+end.format(DateTimeFormatter.ofPattern("MM/dd"))+com.eye.i18n.I18n.text(" · 悬停查看构成，点击查看详情"));
        if(!heatmapDays.getValueIsAdjusting())renderDetail();
    }
    private static long[] completion(Statistics.Day day,String taskId){long earned=0,target=0;
        for(var t:day.tasks())if(taskId==null||taskId.equals(t.id())){target+=t.targetMs();earned+=Math.min(t.actualMs(),t.targetMs());}return new long[]{earned,target};}
    private static String tooltip(Statistics.Day day,int metric,String taskId){
        StringBuilder b=new StringBuilder("<html><div style='padding:5px;width:300px'><b>").append(day.date().format(DateTimeFormatter.ofPattern(com.eye.i18n.I18n.text("yyyy年M月d日"),com.eye.i18n.I18n.isEnglish()?java.util.Locale.ENGLISH:java.util.Locale.CHINA))).append("</b><br>");
        if(metric==0)b.append(com.eye.i18n.I18n.text("专注 ")).append(Statistics.duration(day.focusMs()));
        else if(metric==1)b.append(com.eye.i18n.I18n.text("休息 ")).append(Statistics.duration(day.restMs()));
        else{long[] c=completion(day,taskId);long complete=day.tasks().stream().filter(t->taskId==null||taskId.equals(t.id())).filter(Statistics.Task::complete).count();long count=day.tasks().stream().filter(t->taskId==null||taskId.equals(t.id())).filter(t->t.targetMs()>0).count();
            b.append(com.eye.i18n.I18n.text("完成 ")).append(complete).append(" / ").append(count).append(com.eye.i18n.I18n.text(" 项 · 投入达成 ")).append(c[1]>0?Math.round(c[0]*100.0/c[1]):0).append("%");}
        String emptyMessage=metric==0&&day.focusMs()==0?"当天没有有效专注时间。":metric==1&&day.restMs()==0?"当天没有记录到休息时间。":metric==2&&completion(day,taskId)[1]==0?"当天没有可统计的任务目标。":null;
        if(emptyMessage!=null)b.append("<br><font color='#687970'>").append(com.eye.i18n.I18n.text(emptyMessage)).append("</font>");
        b.append("<br><table cellpadding='3' cellspacing='0' width='100%'>");List<String[]> rows=new ArrayList<>();
        if(metric==1)day.restKinds().forEach((k,v)->rows.add(new String[]{k,Statistics.duration(v)}));
        else for(var t:day.tasks()){
            if(metric==2&&(t.targetMs()==0||taskId!=null&&!taskId.equals(t.id())))continue;if(metric==0&&t.actualMs()==0)continue;
            rows.add(new String[]{t.name(),Statistics.duration(t.actualMs())+(metric==2?" / "+Statistics.duration(t.targetMs())+(t.complete()?" ✓":""):"")});}
        for(int i=0;i<Math.min(5,rows.size());i++){String name=rows.get(i)[0];if(name.length()>22)name=name.substring(0,21)+"…";
            b.append("<tr><td>").append(Statistics.escape(name)).append("</td><td align='right'>").append(rows.get(i)[1]).append("</td></tr>");}
        if(rows.size()>5)b.append(com.eye.i18n.I18n.text("<tr><td colspan='2'>另有 ")).append(rows.size()-5).append(com.eye.i18n.I18n.text(" 项 · 点击查看全部</td></tr>"));
        return b.append("</table></div></html>").toString();
    }
    private void renderDetail(){if(data==null)return;taskFilters.setVisible(detailMode.equals("task"));
        for(JButton b:analysisTabs){b.putClientProperty("rest.selected",detailMode.equals(b.getActionCommand()));b.repaint();}
        JPanel view=new SettingsUi.Column();
        if(detailMode.equals("day"))renderDay(view);else if(detailMode.equals("trend"))renderTrend(view);else if(detailMode.equals("insights"))renderInsights(view);else renderTask(view);
        detailHost.removeAll();detailHost.add(view,BorderLayout.CENTER);
        detailHost.setMaximumSize(new Dimension(Integer.MAX_VALUE,view.getPreferredSize().height));
        // The column must follow dynamic wrapped heights after its actual width is assigned.
        view.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){detailHost.setMaximumSize(new Dimension(Integer.MAX_VALUE,view.getPreferredSize().height));column.revalidate();}});
        detailHost.revalidate();column.revalidate();column.repaint();
    }
    private void renderDay(JPanel view){Statistics.Day day=data.day(selectedDay);boolean rest=metricBox.getSelectedIndex()==1;
        Statistics.Window window=Statistics.window(day,config.dayBoundarySeconds,ZoneId.systemDefault());
        addSection(view,dayOverview(day,rest,window,false));
        Charts.Timeline timeline=new Charts.Timeline();timeline.data(day.intervals(),window);
        JLabel segment=Theme.label(com.eye.i18n.I18n.text("悬停查看时段，点击查看精确记录。"),10.5f,Theme.SECONDARY);segment.setMinimumSize(new Dimension(0,24));
        timeline.onSelect(i->{segment.setText(Charts.Timeline.description(i));segment.setToolTipText(null);});
        JPanel timelineBody=new SettingsUi.Column();timelineBody.add(timeline);timelineBody.add(segment);
        JPanel tableHost=Theme.panel(new BorderLayout());JButton expand=new JButton(intervalsExpanded?com.eye.i18n.I18n.text("收起逐段记录"):com.eye.i18n.I18n.text("查看逐段记录"));Theme.smallButton(expand,"ghost","down");timelineBody.add(Box.createVerticalStrut(6));timelineBody.add(expand);timelineBody.add(tableHost);
        if(intervalsExpanded)tableHost.add(intervalTable(day),BorderLayout.CENTER);
        expand.addActionListener(e->{intervalsExpanded=!intervalsExpanded;renderDetail();});
        addSection(view,card(com.eye.i18n.I18n.text("一天的节奏"),com.eye.i18n.I18n.text("空白时段没有活动记录。"),timelineBody));
        LocalDate[] near=Statistics.nearby(selectedDay,data.to());Statistics.Summary week=Statistics.summarize(data,near[0],near[1]);Statistics.Summary previous=Statistics.summarize(data,near[0].minusDays(7),near[0].minusDays(1));
        Charts.Bars seven=new Charts.Bars();seven.data(metricBox.getSelectedIndex()==2?Statistics.completionSeries(data,near[0],near[1],null):Statistics.dailySeries(data,near[0],near[1],rest,null),chartNames(),false,metricBox.getSelectedIndex()==2);seven.onSelect(b->selectDay(LocalDate.ofEpochDay(b.start())));
        JPanel weekBody=new SettingsUi.Column();weekBody.add(seven);long amount=rest?week.restMs():week.focusMs(),before=rest?previous.restMs():previous.focusMs();
        weekBody.add(new SettingsUi.Text(com.eye.i18n.I18n.text("总计 ")+Statistics.duration(amount)+com.eye.i18n.I18n.text("  ·  日均 ")+Statistics.duration(amount/7)+com.eye.i18n.I18n.text("  ·  比前 7 天")+(amount>=before?com.eye.i18n.I18n.text("增加 "):com.eye.i18n.I18n.text("减少 "))+Statistics.duration(Math.abs(amount-before))+com.eye.i18n.I18n.text("  ·  完成 ")+week.completedTasks()+" / "+week.plannedTasks()+com.eye.i18n.I18n.text(" 项"),11,Theme.SECONDARY));
        addSection(view,card(com.eye.i18n.I18n.text("附近 7 天"),near[0]+" — "+near[1]+com.eye.i18n.I18n.text(" · 点击柱子切换日期"),weekBody));
    }
    private Theme.Surface dayOverview(Statistics.Day day,boolean rest,Statistics.Window window,boolean exporting){
        Theme.Surface overview=surface(new BorderLayout(0,16));JPanel head=new SettingsUi.Column();JPanel titles=new SettingsUi.Column();
        JLabel date=Theme.label(selectedDay.format(DateTimeFormatter.ofPattern(com.eye.i18n.I18n.text("yyyy年M月d日 EEEE"),(com.eye.i18n.I18n.isEnglish()?Locale.ENGLISH:Locale.CHINA))),12,Theme.SECONDARY);
        date.setIcon(Icons.of("calendar",16,Theme.SECONDARY));date.setIconTextGap(8);
        String big=metricBox.getSelectedIndex()==2?com.eye.i18n.I18n.text("完成 ")+day.completeCount()+" / "+day.targetCount()+com.eye.i18n.I18n.text(" 项"):Statistics.duration(rest?day.restMs():day.focusMs());
        titles.add(Theme.label(com.eye.i18n.I18n.text(metricBox.getSelectedIndex()==2?"学习日内达标":rest?"累计休息":"累计专注"),11,Theme.SECONDARY));titles.add(Box.createVerticalStrut(7));
        JLabel amount=fitHeading(big,34);titles.add(amount);titles.add(Box.createVerticalStrut(8));long yesterday=rest?data.day(selectedDay.minusDays(1)).restMs():data.day(selectedDay.minusDays(1)).focusMs();
        long current=rest?day.restMs():day.focusMs();String compare=selectedDay.equals(data.to())?com.eye.i18n.I18n.text("今天仍在进行 · 上一学习日合计 ")+Statistics.duration(yesterday):com.eye.i18n.I18n.text("比前一学习日")+(current>=yesterday?com.eye.i18n.I18n.text("增加 "):com.eye.i18n.I18n.text("减少 "))+Statistics.duration(Math.abs(current-yesterday));
        titles.add(new SettingsUi.Text(compare,10.5f,Theme.SECONDARY));
        JPanel arrows=Theme.panel(new FlowLayout(FlowLayout.RIGHT,4,0));JButton prev=new JButton("‹"),now=new JButton(dayNavigationLabel(selectedDay,manager.getToday())),next=new JButton("›"),today=new JButton("»");
        for(JButton b:new JButton[]{prev,now,next,today}){Theme.smallButton(b,"secondary",null);if(!exporting)arrows.add(b);}
        now.setToolTipText(com.eye.i18n.I18n.text("选择日期"));now.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("选择日期"));
        today.setToolTipText(com.eye.i18n.I18n.text("回到今天"));today.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("回到今天"));
        prev.addActionListener(e->selectDay(selectedDay.minusDays(1)));next.setEnabled(selectedDay.isBefore(manager.getToday()));next.addActionListener(e->selectDay(selectedDay.plusDays(1)));
        today.setEnabled(!selectedDay.equals(manager.getToday()));today.addActionListener(e->selectDay(manager.getToday()));
        now.addActionListener(e->{LocalDate chosen=PlanDatePickerDialogs.SingleDatePickerDialog.showDialog(SwingUtilities.getWindowAncestor(this),selectedDay,manager.getToday());if(chosen!=null)selectDay(chosen);});
        if(!exporting){JButton export=new JButton(com.eye.i18n.I18n.text("导出图片"));Theme.smallButton(export,"secondary","download");export.addActionListener(e->exportDayImage());arrows.add(export);AdaptiveToolbar toolbar=new AdaptiveToolbar(date,arrows,10);export.putClientProperty("tutorial.region",toolbar);tutorialDayExport=export;head.add(toolbar);}
        else head.add(date);
        head.add(Box.createVerticalStrut(14));
        JPanel hero=Theme.panel(new BorderLayout(22,0));
        hero.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.LINE),new EmptyBorder(8,0,22,0)));
        hero.add(titles,BorderLayout.CENTER);
        JPanel companion=new SettingsUi.Column();companion.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,1,0,0,Theme.LINE),new EmptyBorder(6,20,6,0)));
        JLabel companionLabel=Theme.label(com.eye.i18n.I18n.text(rest?"累计专注":"累计休息"),11,Theme.SECONDARY);companionLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,24));
        companion.add(companionLabel);companion.add(Box.createVerticalStrut(10));
        companion.add(fitHeading(Statistics.duration(rest?day.focusMs():day.restMs()),22));
        companion.setPreferredSize(new Dimension(165,76));hero.add(companion,BorderLayout.EAST);head.add(hero);
        overview.add(head,BorderLayout.NORTH);
        JPanel graph=new SettingsUi.Column();HourlyFocusChart hours=exporting?new HourlyFocusChart():hourlyChart;if(exporting)hours.copyPresentationFrom(hourlyChart);
        hours.data(Statistics.hourly(day,window,rest,null),chartNames(),rest?day.restMs():day.focusMs(),rest);graph.add(hours);
        // Keep the accepted new hourly visualization above, but restore the compact
        // task list below it: colour dot + task name on the left, duration and
        // optional completion timestamp right-aligned, matching the established UI.
        graph.add(Box.createVerticalStrut(8));graph.add(taskSummary(day,rest,exporting));
        if(window.mixedRules()){graph.add(Box.createVerticalStrut(6));graph.add(new SettingsUi.Text(com.eye.i18n.I18n.text("当天的学习日分界或时区有变动。"),10.5f,Theme.SECONDARY));}
        if(day.unplacedMs(rest)>0){graph.add(Box.createVerticalStrut(6));graph.add(new SettingsUi.Text(com.eye.i18n.I18n.text("另有 ")+Statistics.duration(day.unplacedMs(rest))+com.eye.i18n.I18n.text(" 已计入总时长，具体时段未知。"),10.5f,Theme.AMBER));}
        overview.add(graph,BorderLayout.CENTER);return overview;
    }
    private void exportDayImage(){
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(com.eye.i18n.I18n.text("导出图片"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG image","png"));
        chooser.setSelectedFile(new java.io.File("EyeRelax-"+selectedDay+".png"));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        java.io.File chosen=chooser.getSelectedFile();if(!chosen.getName().toLowerCase(Locale.ROOT).endsWith(".png"))chosen=new java.io.File(chosen.getParentFile(),chosen.getName()+".png");
        if(chosen.exists()&&JOptionPane.showConfirmDialog(this,com.eye.i18n.I18n.text("文件已存在，是否覆盖？"),com.eye.i18n.I18n.text("导出图片"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        Statistics.Day day=data.day(selectedDay);boolean rest=metricBox.getSelectedIndex()==1;
        JComponent card=dayOverview(day,rest,Statistics.window(day,config.dayBoundarySeconds,ZoneId.systemDefault()),true);
        java.awt.image.BufferedImage image=com.eye.ui.ComponentImage.capture(card,1000,2);
        java.io.File target=chosen;
        new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{if(!javax.imageio.ImageIO.write(image,"png",target))throw new java.io.IOException("PNG encoder unavailable");return null;}
            protected void done(){try{get();JOptionPane.showMessageDialog(ActivityPanel.this,com.eye.i18n.I18n.text("图片已保存：")+target);}catch(Exception error){JOptionPane.showMessageDialog(ActivityPanel.this,com.eye.i18n.I18n.text("导出失败：")+error.getMessage(),com.eye.i18n.I18n.text("导出图片"),JOptionPane.ERROR_MESSAGE);}}}.execute();
    }
    private void selectDay(LocalDate day){if(day.isAfter(manager.getToday()))return;selectedDay=day;followToday=day.equals(manager.getToday());heatmap.setSelected(day);
        if(data==null||day.minusDays(10).isBefore(data.from()))refresh();else renderDetail();}
    private String completionSummary(Statistics.Day day){
        List<String> completed=new ArrayList<>();
        for(var task:day.tasks())if(task.complete()){String when=Statistics.completionTime(task,day.date());if(!when.isBlank())completed.add(task.name()+" "+when);}
        return completed.isEmpty()?"":com.eye.i18n.I18n.text("完成记录 · ")+String.join("   ·   ",completed);
    }
    private JComponent taskSummary(Statistics.Day day,boolean rest,boolean exporting){
        List<String[]> rows=new ArrayList<>();
        if(rest){
            day.restKinds().entrySet().stream().filter(e->e.getValue()>0)
                    .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                    .forEach(e->rows.add(new String[]{e.getKey(),e.getKey(),Statistics.duration(e.getValue())}));
        } else {
            day.tasks().stream().filter(t->t.actualMs()>0)
                    .sorted(Comparator.comparingLong(Statistics.Task::actualMs).reversed().thenComparing(Statistics.Task::name,String.CASE_INSENSITIVE_ORDER))
                    .forEach(t->rows.add(new String[]{t.id(),t.name(),Statistics.duration(t.actualMs())+(metricBox.getSelectedIndex()==2?" / "+Statistics.duration(t.targetMs())+(t.complete()?"  ✓":""):"")}));
        }
        JPanel items=new SettingsUi.Column();for(String[] r:rows){
            JPanel row=Theme.panel(new BorderLayout(12,0));row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.LINE),new EmptyBorder(11,2,11,2)));
            Color dot=hourlyChart.displayColor(r[0],r[1]);
            JLabel name=Theme.label(r[1],12,Theme.INK);name.setIcon(new DotIcon(dot));name.setIconTextGap(8);name.setToolTipText(null);name.setMinimumSize(new Dimension(0,22));
            row.add(name,BorderLayout.CENTER);
            JPanel values=Theme.panel(new GridLayout(0,1,0,2));JLabel amount=Theme.label(r[2],12,Theme.INK);amount.setHorizontalAlignment(SwingConstants.RIGHT);values.add(amount);
            if(!rest){day.tasks().stream().filter(t->t.id().equals(r[0])&&t.complete()).findFirst().ifPresent(t->{
                String when=Statistics.completionTime(t,day.date());if(!when.isBlank()){JLabel done=Theme.label(com.eye.i18n.I18n.text("完成于 ")+when,10,Theme.GREEN);done.setHorizontalAlignment(SwingConstants.RIGHT);values.add(done);}});}
            row.add(values,BorderLayout.EAST);items.add(row);}
        if(rows.isEmpty()){JLabel empty=Theme.label(com.eye.i18n.I18n.text(rest?"当天没有记录到休息时间。":"当天没有有效专注时间。"),12,Theme.SECONDARY);empty.setBorder(new EmptyBorder(12,12,12,12));items.add(empty);}
        if(exporting||rows.size()<=6)return items;JScrollPane scroll=new JScrollPane(items);Theme.scroll(scroll);ScrollChaining.install(scroll);scroll.setPreferredSize(new Dimension(500,210));scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,210));return scroll;
    }
    private JScrollPane intervalTable(Statistics.Day day){DefaultTableModel model=new DefaultTableModel(new String[]{com.eye.i18n.I18n.text("时间段"),com.eye.i18n.I18n.text("内容"),com.eye.i18n.I18n.text("时长")},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var i:day.intervals())model.addRow(new Object[]{time(i.startAt(),ZoneId.of(i.zoneId()))+" — "+time(i.endAt(),ZoneId.of(i.zoneId())),i.displayName(),Statistics.duration(i.durationMs())});
        JTable table=new JTable(model);table.setFont(Theme.font(11));table.setRowHeight(32);table.setShowGrid(false);table.setBackground(Theme.SURFACE);table.setForeground(Theme.INK);table.setSelectionBackground(Theme.MINT);table.getTableHeader().setFont(Theme.font(11));
        JScrollPane scroll=new JScrollPane(table);Theme.scroll(scroll);ScrollChaining.install(scroll);scroll.setPreferredSize(new Dimension(500,Math.min(240,Math.max(80,32*day.intervals().size()+30))));return scroll;}
    private Map<String,String> chartNames(){Map<String,String> names=new HashMap<>(data.taskNames());names.put(Statistics.FREE,com.eye.i18n.I18n.text("自由专注"));names.put("@completion",com.eye.i18n.I18n.text("投入达成率"));for(var kind:SessionStore.Kind.values())names.put("@"+kind.name(),switch(kind){case FOCUS->com.eye.i18n.I18n.text("专注");case REST->com.eye.i18n.I18n.text("正常休息");case IMMEDIATE_REST->com.eye.i18n.I18n.text("立即休息");case REMINDER_REST->com.eye.i18n.I18n.text("提醒遮罩");});return names;}
    private static String time(long instant,ZoneId zone){return Instant.ofEpochMilli(instant).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"));}
    private LocalDate[] range(){LocalDate end=data.to();if(trendRange.getSelectedIndex()==3&&customFrom!=null)return new LocalDate[]{customFrom,customTo.isAfter(end)?end:customTo};int count=trendRange.getSelectedIndex()==0?7:trendRange.getSelectedIndex()==1?30:365;return new LocalDate[]{end.minusDays(count-1),end};}
    private JComponent rangeHeader(boolean task){JPanel p=Theme.panel(new BorderLayout(10,0));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));
        if(task)p.add(analysisTaskBox,BorderLayout.WEST);
        else p.add(Theme.label(com.eye.i18n.I18n.text("按学习日回顾投入与休息"),11,Theme.SECONDARY),BorderLayout.WEST);
        p.add(trendRange,BorderLayout.EAST);trendRange.putClientProperty("tutorial.region",p);return p;}
    private void renderInsights(JPanel view){
        LocalDate[] dates=range();
        JPanel header=Theme.panel(new BorderLayout(12,0));
        JPanel actions=Theme.panel(new FlowLayout(FlowLayout.LEFT,6,0));
        JButton image=new JButton(com.eye.i18n.I18n.text("导出洞察图片")),csv=new JButton(com.eye.i18n.I18n.text("导出每日明细"));
        Theme.smallButton(image,"secondary",null);Theme.smallButton(csv,"ghost",null);actions.add(image);actions.add(csv);
        image.addActionListener(e->exportInsights(false));csv.addActionListener(e->exportInsights(true));header.add(actions);header.add(trendRange,BorderLayout.EAST);addSection(view,header);tutorialInsightsExport=actions;trendRange.putClientProperty("tutorial.region",header);
        FocusInsightsPanel insights=new FocusInsightsPanel(FocusInsights.analyze(data,dates[0],dates[1]));tutorialInsightsSummary=(JComponent)insights.getComponent(0);addSection(view,insights);
        if(dates[1].equals(manager.getToday()))addSection(view,new SettingsUi.Text(com.eye.i18n.I18n.text("今天的专注时间仍在累计。"),11,Theme.SECONDARY));
    }
    private void exportInsights(boolean csv){
        LocalDate[] dates=range();Statistics.Snapshot snapshot=data;
        JFileChooser chooser=new JFileChooser();String suffix=csv?"csv":"png";
        chooser.setDialogTitle(com.eye.i18n.I18n.text(csv?"导出每日明细":"导出洞察图片"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(csv?"CSV":"PNG",suffix));
        chooser.setSelectedFile(new java.io.File("EyeRelax-insights-"+dates[0]+"-"+dates[1]+"."+suffix));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        java.io.File chosen=chooser.getSelectedFile();if(!chosen.getName().toLowerCase(Locale.ROOT).endsWith("."+suffix))chosen=new java.io.File(chosen.getParentFile(),chosen.getName()+"."+suffix);
        if(chosen.exists()&&JOptionPane.showConfirmDialog(this,com.eye.i18n.I18n.text("文件已存在，是否覆盖？"),com.eye.i18n.I18n.text("导出"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        java.io.File target=chosen;
        java.awt.image.BufferedImage image=csv?null:com.eye.ui.ComponentImage.capture(new FocusInsightsPanel(FocusInsights.analyze(snapshot,dates[0],dates[1])),1000,2);
        new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{if(csv)StatisticsExport.writeDailyCsv(snapshot,dates[0],dates[1],target.toPath());else if(!javax.imageio.ImageIO.write(image,"png",target))throw new java.io.IOException("PNG encoder unavailable");return null;}
            protected void done(){try{get();JOptionPane.showMessageDialog(ActivityPanel.this,com.eye.i18n.I18n.text("已导出：")+target);}catch(Exception ex){JOptionPane.showMessageDialog(ActivityPanel.this,com.eye.i18n.I18n.text("导出失败：")+ex.getMessage(),com.eye.i18n.I18n.text("导出"),JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }
    private void renderTrend(JPanel view){LocalDate[] range=range();Statistics.Summary s=Statistics.summarize(data,range[0],range[1]);addSection(view,rangeHeader(false));
        addSection(view,metricGrid(new String[][]{{com.eye.i18n.I18n.text("累计专注"),Statistics.duration(s.focusMs())},{com.eye.i18n.I18n.text("日均（含无记录日）"),Statistics.duration(s.averageMs())},{com.eye.i18n.I18n.text("单日最大"),Statistics.duration(s.maxMs())},{com.eye.i18n.I18n.text("最小有记录日"),s.activeDays()>0?Statistics.duration(s.minActiveMs()):"—"},{com.eye.i18n.I18n.text("有记录 / 无记录"),s.activeDays()+" / "+(s.calendarDays()-s.activeDays())+com.eye.i18n.I18n.text(" 天")},{com.eye.i18n.I18n.text("最长连续"),s.longestStreak()+com.eye.i18n.I18n.text(" 天")}}));
        boolean rest=metricBox.getSelectedIndex()==1;Charts.Bars bars=new Charts.Bars();bars.data(metricBox.getSelectedIndex()==2?Statistics.completionSeries(data,range[0],range[1],null):Statistics.dailySeries(data,range[0],range[1],rest,null),chartNames(),false,metricBox.getSelectedIndex()==2);
        addSection(view,card(metricBox.getSelectedIndex()==2?com.eye.i18n.I18n.text("计划达成趋势"):rest?com.eye.i18n.I18n.text("休息趋势"):com.eye.i18n.I18n.text("专注趋势"),range[0]+" — "+range[1]+" · "+com.eye.i18n.I18n.text("短期按天，较长时按周或月汇总"),bars));
        addSection(view,metricGrid(new String[][]{{com.eye.i18n.I18n.text("累计休息"),Statistics.duration(s.restMs())},{com.eye.i18n.I18n.text("平均每段休息"),s.restSessions()>0?Statistics.duration(s.recordedRestMs()/s.restSessions()):"—"},{com.eye.i18n.I18n.text("专注 / 休息"),s.restMs()>0?String.format("%.1f : 1",s.focusMs()/(double)s.restMs()):"—"},{com.eye.i18n.I18n.text("学习日内达标"),s.completedTasks()+" / "+s.plannedTasks()+com.eye.i18n.I18n.text(" 项")},{com.eye.i18n.I18n.text("投入达成率"),s.targetMs()>0?Math.round(s.completionRatio()*100)+"%":"—"},{com.eye.i18n.I18n.text("今日加时累计"),Statistics.duration(s.extraMs())}}));
        addSection(view,new SettingsUi.Text(com.eye.i18n.I18n.text("每项计划按当天的目标判断是否达标，临时加时也计入目标。"),10.5f,Theme.SECONDARY));
        if(s.restMs()>s.recordedRestMs())addSection(view,new SettingsUi.Text(com.eye.i18n.I18n.text("平均休息时长仅含有起止时间的记录。"),10.5f,Theme.SECONDARY));
    }
    private void renderTask(JPanel view){
        addSection(view,new SettingsUi.Text(com.eye.i18n.I18n.text("选择一个每日或周期计划，查看这段时间的投入和达标情况。"),11,Theme.SECONDARY));
        addSection(view,rangeHeader(true));TaskChoice task=(TaskChoice)analysisTaskBox.getSelectedItem();if(task==null){addSection(view,Theme.label(com.eye.i18n.I18n.text("暂无任务记录"),12,Theme.SECONDARY));return;}
        LocalDate[] range=range();long sum=0,max=0,target=0,earned=0,last=0;int days=0,complete=0,plannedDays=0;Set<String> runs=new HashSet<>();long recorded=0;
        for(LocalDate d=range[0];!d.isAfter(range[1]);d=d.plusDays(1)){
            for(var t:data.day(d).tasks())if(t.id().equals(task.id)){sum+=t.actualMs();max=Math.max(max,t.actualMs());target+=t.targetMs();earned+=Math.min(t.actualMs(),t.targetMs());if(t.actualMs()>0)days++;if(t.targetMs()>0)plannedDays++;if(t.complete())complete++;}
            for(var i:data.day(d).intervals())if(!i.isRest()&&Statistics.taskKey(i).equals(task.id)){runs.add(i.sessionId());recorded+=i.durationMs();last=Math.max(last,i.endAt());}}
        int count=(int)ChronoUnit.DAYS.between(range[0],range[1])+1;
        addSection(view,metricGrid(new String[][]{{com.eye.i18n.I18n.text("总投入"),Statistics.duration(sum)},{com.eye.i18n.I18n.text("日均（含无记录日）"),Statistics.duration(sum/Math.max(1,count))},{com.eye.i18n.I18n.text("最大单日"),Statistics.duration(max)},{com.eye.i18n.I18n.text("投入天数 / 达标"),days+" / "+complete+com.eye.i18n.I18n.text(" 天")},{com.eye.i18n.I18n.text("平均每段"),runs.isEmpty()?"—":Statistics.duration(recorded/runs.size())},{com.eye.i18n.I18n.text("投入达成率"),target>0?Math.round(earned*100.0/target)+"%":"—"}}));
        Charts.Bars bars=new Charts.Bars();bars.data(Statistics.dailySeries(data,range[0],range[1],false,task.id),chartNames(),false,false);addSection(view,card(task.name+com.eye.i18n.I18n.text(" · 投入趋势"),range[0]+" — "+range[1]+" · "+com.eye.i18n.I18n.text("短期按天，较长时按周或月汇总"),bars));
        Charts.Bars hours=new Charts.Bars();List<Statistics.Bucket> buckets=Statistics.hoursOfTask(data,range[0],range[1],task.id);hours.data(buckets,chartNames(),false,false);
        Statistics.Bucket peak=buckets.stream().max(Comparator.comparingLong(Statistics.Bucket::total)).orElse(null);
        String subtitle=recorded>0?com.eye.i18n.I18n.text("最多投入时段 ")+peak.label()+" — "+String.format("%02d:00",(peak.start()+1)%24):com.eye.i18n.I18n.text("暂无时段记录");
        addSection(view,card(com.eye.i18n.I18n.text("常见执行时段"),subtitle,hours));
        List<String> details=new ArrayList<>();
        if(sum>recorded)details.add(com.eye.i18n.I18n.text("另有 ")+Statistics.duration(sum-recorded)+com.eye.i18n.I18n.text(" 已计入总时长，具体时段未知。"));
        if(last>0)details.add(com.eye.i18n.I18n.text("最近执行于 ")+Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        if(!details.isEmpty())addSection(view,new SettingsUi.Text(String.join("  ·  ",details),10.5f,Theme.SECONDARY));
    }
    private JComponent metricGrid(String[][] values){Theme.Surface grid=surface(new GridLayout(0,3,0,0));grid.setBorder(new EmptyBorder(4,4,4,4));int index=0;
        for(String[] v:values){JPanel c=Theme.panel(new BorderLayout(0,10));int cell=index++;
            c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(cell>=3?1:0,0,0,cell%3<2?1:0,Theme.LINE),new EmptyBorder(17,17,17,17)));
            c.add(Theme.label(v[0],10.5f,Theme.SECONDARY),BorderLayout.NORTH);c.add(fitHeading(v[1],23),BorderLayout.CENTER);grid.add(c);}return grid;}
    /** Fit only the painted value; never shrink preferred size and trigger a layout feedback loop. */
    private static JLabel fitHeading(String text,int maximum){
        JLabel label=new JLabel(text){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
                Insets in=getInsets();int available=Math.max(1,getWidth()-in.left-in.right),size=maximum;
                Font font=Theme.font(size).deriveFont(Font.BOLD);
                while(size>12 && g.getFontMetrics(font).stringWidth(getText())>available)font=Theme.font(--size).deriveFont(Font.BOLD);
                g.setFont(font);g.setColor(getForeground());FontMetrics fm=g.getFontMetrics();
                int y=in.top+(getHeight()-in.top-in.bottom-fm.getHeight())/2+fm.getAscent();
                g.drawString(getText(),in.left,y);g.dispose();
            }
            @Override public String getToolTipText(MouseEvent event){return getText();}
        };
        label.setFont(Theme.font(maximum).deriveFont(Font.BOLD));label.setForeground(Theme.INK);label.setToolTipText(null);return label;
    }
    private boolean chooseCustomRange(){JTextField from=new JTextField(customFrom==null?manager.getToday().minusDays(29).toString():customFrom.toString()),to=new JTextField(customTo==null?manager.getToday().toString():customTo.toString());
        Theme.field(from);Theme.field(to);JPanel form=new SettingsUi.Column();form.add(SettingsUi.row(com.eye.i18n.I18n.text("开始学习日"),"yyyy-MM-dd",from));form.add(SettingsUi.row(com.eye.i18n.I18n.text("结束学习日"),"yyyy-MM-dd",to));from.setPreferredSize(new Dimension(180,34));to.setPreferredSize(new Dimension(180,34));
        if(!SettingsUi.confirm(this,com.eye.i18n.I18n.text("自定义统计范围"),com.eye.i18n.I18n.text("包含起止学习日，不包含未来日期。"),form,com.eye.i18n.I18n.text("应用"),()->{
            boolean valid=true;LocalDate a=null,b=null;
            try{a=LocalDate.parse(from.getText().trim());}catch(RuntimeException ex){InlineErrors.show(from,com.eye.i18n.I18n.text("请输入有效日期，例如 2026-10-05。"));valid=false;}
            try{b=LocalDate.parse(to.getText().trim());}catch(RuntimeException ex){InlineErrors.show(to,com.eye.i18n.I18n.text("请输入有效日期，例如 2026-10-05。"));valid=false;}
            if(a!=null&&a.isBefore(LocalDate.of(1970,1,1))){InlineErrors.show(from,com.eye.i18n.I18n.text("开始日期不能早于 1970-01-01。"));valid=false;}
            if(b!=null&&b.isAfter(manager.getToday())){InlineErrors.show(to,com.eye.i18n.I18n.text("结束日期不能晚于当前学习日。"));valid=false;}
            if(a!=null&&b!=null&&a.isAfter(b)){InlineErrors.show(to,com.eye.i18n.I18n.text("结束日期不能早于开始日期。"));valid=false;}
            return valid;
        })){filling=true;trendRange.setSelectedIndex(customFrom==null?1:3);filling=false;return false;}
        try{LocalDate a=LocalDate.parse(from.getText().trim()),b=LocalDate.parse(to.getText().trim());if(a.isAfter(b)||b.isAfter(manager.getToday())||a.isBefore(LocalDate.of(1970,1,1)))throw new IllegalArgumentException();customFrom=a;customTo=b;return true;}
        catch(RuntimeException ex){JOptionPane.showMessageDialog(this,com.eye.i18n.I18n.text("请填写有效的日期范围（开始不晚于结束，结束不晚于当前学习日）。"));filling=true;trendRange.setSelectedIndex(1);filling=false;return false;}}
    public static String duration(long seconds){long s=Math.max(0,seconds);if(s>=3600)return s/3600+com.eye.i18n.I18n.text("时 ")+s%3600/60+com.eye.i18n.I18n.text("分")+(s%60>0?" "+s%60+com.eye.i18n.I18n.text("秒"):"");if(s>=60)return s/60+com.eye.i18n.I18n.text("分")+(s%60>0?" "+s%60+com.eye.i18n.I18n.text("秒"):"");return s+com.eye.i18n.I18n.text("秒");}
    private record DotIcon(Color color) implements Icon{public int getIconWidth(){return 9;}public int getIconHeight(){return 9;}public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(color);g.fillRoundRect(x,y,9,9,4,4);}}

    public static final class Heatmap extends JComponent {
        static final Color[] COLORS={new Color(0xE7EDE2),new Color(0xCFE0C7),new Color(0xA6C49B),new Color(0x719977),Theme.GREEN};
        public record Day(boolean known,double fraction,String tooltip,String detailHtml){}
        private LocalDate first=LocalDate.now(),last=first,start=first,selected;
        private Map<LocalDate,Day> data=Map.of();private Consumer<LocalDate> selectionListener,hoverListener;
        private int cell=15,gap=4,left=30,top=9,hover=-1;
        public Heatmap(){setPreferredSize(new Dimension(550,184));setMinimumSize(new Dimension(0,120));setMaximumSize(new Dimension(Integer.MAX_VALUE,184));setToolTipText("");setFocusable(true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            ToolTipManager.sharedInstance().setDismissDelay(15000);
            addMouseMotionListener(new MouseMotionAdapter(){public void mouseMoved(MouseEvent e){hover=index(e.getPoint());if(hover>=0&&hoverListener!=null)hoverListener.accept(start.plusDays(hover));repaint();}});
            addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){requestFocusInWindow();}public void mouseExited(MouseEvent e){hover=-1;repaint();}public void mouseClicked(MouseEvent e){int i=index(e.getPoint());if(i>=0){requestFocusInWindow();select(start.plusDays(i));}}});
            addKeyListener(new KeyAdapter(){public void keyPressed(KeyEvent e){LocalDate d=selected==null?last:selected;
                switch(e.getKeyCode()){case KeyEvent.VK_LEFT->select(d.minusDays(7));case KeyEvent.VK_RIGHT->select(d.plusDays(7));case KeyEvent.VK_UP->select(d.minusDays(1));case KeyEvent.VK_DOWN->select(d.plusDays(1));case KeyEvent.VK_ENTER->{if(selectionListener!=null)selectionListener.accept(d);}default->{}}}});
        }
        private void select(LocalDate day){if(day.isBefore(first)||day.isAfter(last))return;selected=day;repaint();if(selectionListener!=null)selectionListener.accept(day);}
        public void setSelectionListener(Consumer<LocalDate> listener){selectionListener=listener;}
        void setHoverListener(Consumer<LocalDate> listener){hoverListener=listener;}
        void setData(LocalDate first,LocalDate last,Map<LocalDate,Day> data){this.first=first;this.last=last;this.data=Map.copyOf(data);start=first.minusDays(first.getDayOfWeek().getValue()-1);repaint();}
        void setSelected(LocalDate date){selected=date;repaint();}
        private int scaled(double value){return (int)Math.round(value*ProportionalPanel.scaleOf(this));}
        private record GridGeometry(int cell,int gap,int left,int top,int columns){int right(){return left+columns*(cell+gap)-gap;}}
        private GridGeometry gridGeometry(int width){int gap=scaled(4),columns=(int)(ChronoUnit.DAYS.between(start,last)/7)+1;
            int cell=Math.max(1,Math.min(scaled(18),(width-scaled(36))/Math.max(1,columns)-gap));
            int left=scaled(26)+Math.max(0,(width-scaled(36)-columns*(cell+gap))/2);
            return new GridGeometry(cell,gap,left,scaled(9),columns);
        }
        private void geometry(){GridGeometry geometry=gridGeometry(getWidth());cell=geometry.cell;gap=geometry.gap;left=geometry.left;top=geometry.top;}
        private int index(Point p){geometry();if(p.x<left||p.y<top)return -1;int col=(p.x-left)/(cell+gap),row=(p.y-top)/(cell+gap);if(row>6||((p.x-left)%(cell+gap)>=cell)||((p.y-top)%(cell+gap)>=cell))return -1;int i=col*7+row;LocalDate d=start.plusDays(i);return d.isBefore(first)||d.isAfter(last)?-1:i;}
        @Override public Point getToolTipLocation(MouseEvent event){
            String text=getToolTipText(event);if(text==null)return null;
            JToolTip tip=createToolTip();tip.setTipText(text);Dimension size=tip.getPreferredSize();
            Window window=SwingUtilities.getWindowAncestor(this);if(window==null)return null;
            Rectangle bounds=SwingUtilities.convertRectangle(window,new Rectangle(12,12,Math.max(1,window.getWidth()-24),Math.max(1,window.getHeight()-24)),this);
            int x=Math.max(bounds.x,Math.min(event.getX()+12,bounds.x+bounds.width-size.width));
            int y=event.getY()+cell+10;if(y+size.height>bounds.y+bounds.height)y=event.getY()-size.height-8;
            return new Point(x,Math.max(bounds.y,y));
        }
        @Override public JToolTip createToolTip(){JToolTip tip=new JToolTip();tip.setComponent(this);tip.setBackground(Theme.SURFACE);tip.setForeground(Theme.INK);tip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE),new EmptyBorder(9,11,9,11)));tip.setFont(Theme.font(12).deriveFont((float)(12*ProportionalPanel.scaleOf(this))));return tip;}
        @Override public String getToolTipText(MouseEvent e){int i=index(e.getPoint());if(i<0)return null;LocalDate date=start.plusDays(i);Day value=data.get(date);return value==null?ActivityPanel.tooltip(new Statistics.Day(date,0,0,List.of(),List.of()),0,null):value.tooltip;}
        @Override protected void paintComponent(Graphics graphics){geometry();Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);int columns=(int)(ChronoUnit.DAYS.between(start,last)/7)+1;
            g.setFont(Theme.font(10).deriveFont((float)(10*ProportionalPanel.scaleOf(this))));g.setColor(Theme.SECONDARY);for(int row:new int[]{0,2,4,6})g.drawString(new String[]{com.eye.i18n.I18n.text("一"),com.eye.i18n.I18n.text("二"),com.eye.i18n.I18n.text("三"),com.eye.i18n.I18n.text("四"),com.eye.i18n.I18n.text("五"),com.eye.i18n.I18n.text("六"),com.eye.i18n.I18n.text("日")}[row],Math.max(0,left-scaled(24)),top+row*(cell+gap)+cell-scaled(2));
            int lastMonth=-1;int previousLabelX=-100;
            for(int c=0;c<columns;c++){LocalDate column=start.plusWeeks(c);
                for(int row=0;row<7;row++){LocalDate d=column.plusDays(row);if(d.isBefore(first)||d.isAfter(last))continue;Day value=data.get(d);int level=value==null||value.fraction<=0?0:Math.min(4,1+(int)Math.floor(value.fraction*3.999));int x=left+c*(cell+gap),y=top+row*(cell+gap);
                    g.setColor(COLORS[level]);g.fillRoundRect(x,y,cell,cell,scaled(5),scaled(5));
                    if(hover==c*7+row||d.equals(selected)){
                        float stroke=(float)Math.min(cell,(d.equals(selected)?1.6:1)*ProportionalPanel.scaleOf(this));
                        double inset=stroke/2.0,arc=Math.max(0,scaled(5)-stroke);
                        g.setColor(Theme.GREEN);g.setStroke(new BasicStroke(stroke));
                        // Keep the outline's outer edge on the cell boundary at every display scale.
                        g.draw(new java.awt.geom.RoundRectangle2D.Double(x+inset,y+inset,
                                cell-stroke,cell-stroke,arc,arc));
                    }}
                LocalDate label=column.isBefore(first)?first:column;int x=left+c*(cell+gap);if(label.getMonthValue()!=lastMonth&&x-previousLabelX>=scaled(30)){g.setColor(Theme.SECONDARY);g.drawString(com.eye.i18n.I18n.month(label.getMonth()),x,top+7*(cell+gap)+scaled(16));lastMonth=label.getMonthValue();previousLabelX=x;}}
            g.dispose();}
    }
}
