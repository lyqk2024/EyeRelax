package com.eye.ui;

import com.eye.core.ComputerActivity;
import com.eye.win.WindowsUsageSources;
import com.eye.ui.theme.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.eye.win.WindowsShell;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** Local, plan-independent usage overview. Windows estimates always remain distinguishable. */
public final class ComputerUsagePanel extends JPanel {
    private Runnable openLimits=()->{};
    public void setLimitsAction(Runnable action){openLimits=action;}
    private ComputerActivity activity;
    private List<ApplicationSelectionList.Entry> scanCache;
    private long scannedAt;
    private static final long SCAN_CACHE_MILLIS=10*60*1000L;
    private final Map<String,Icon> applicationIcons=new HashMap<>();
    private final Set<String> pendingIcons=new HashSet<>();
    private boolean followToday=true;
    private final JLabel periodLabel=Theme.label("",11,Theme.SECONDARY);
    private LocalDate selected=LocalDate.now();
    private final JTextField dateField=new JTextField(selected.toString(),10);
    private final JTextArea message=new JTextArea();
    private final JLabel[] summaries=new JLabel[4];
    private final JLabel rankingSummary=Theme.label("",11,Theme.SECONDARY);
    private final JLabel watchedSummary=Theme.label("",11,Theme.SECONDARY);
    private final UsageChart dailyPlot=new UsageChart(false,this::applicationIcon,ComputerUsagePanel::duration),
            hourlyPlot=new UsageChart(false,this::applicationIcon,ComputerUsagePanel::duration),
            appPlot=new UsageChart(true,this::applicationIcon,ComputerUsagePanel::duration);
    private final JComboBox<String> range=new JComboBox<>(new String[]{com.eye.i18n.I18n.text("最近 7 天"),com.eye.i18n.I18n.text("最近 30 天")});
    private final JComboBox<String> appMetric=new JComboBox<>(new String[]{com.eye.i18n.I18n.text("前台使用"),com.eye.i18n.I18n.text("总运行时间（含后台）")});
    private final JSlider appMinimum=new JSlider(0,120,1);
    private final JSlider appPercent=new JSlider(1,100,100);
    private final JComboBox<String> appOrder=new JComboBox<>(new String[]{com.eye.i18n.I18n.text("倒序 · 时长从多到少"),com.eye.i18n.I18n.text("正序 · 时长从少到多")});
    private boolean restoringPreferences;
    private int watchedViewIndex;
    private final JToggleButton watchedTiles=new JToggleButton(),watchedList=new JToggleButton();
    private JButton tutorialAdd;
    public JComponent tutorialTarget(String key){return switch(key){case "metric"->appMetric;case "minimum"->appMinimum;case "percent"->appPercent;case "search"->appSearch;case "watched"->watchedSearch;case "add"->tutorialAdd;default->onlyWatched;};}
    private final JTextField appSearch=new SettingsUi.PlaceholderField(com.eye.i18n.I18n.text("搜索应用名称或文件名"));
    private final JTextField watchedSearch=new SettingsUi.PlaceholderField(com.eye.i18n.I18n.text("搜索关注的应用"));
    private final JCheckBox onlyWatched=new JCheckBox(com.eye.i18n.I18n.text("只看关注的应用"));
    private final DefaultListModel<Application> watchedModel=new DefaultListModel<>();
    private final JList<Application> watched=new ResponsiveApplicationList<>(watchedModel);
    private final javax.swing.Timer timer=new javax.swing.Timer(60000,e->refresh());
    private record Application(Path path,String name){@Override public String toString(){return name;}}
    public ComputerUsagePanel() {
        super(new BorderLayout());setOpaque(false);
        hourlyPlot.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("当天每小时使用"));
        message.setEditable(false);message.setLineWrap(true);message.setWrapStyleWord(true);message.setOpaque(false);
        message.setFont(Theme.font(11));message.setForeground(Theme.SECONDARY);message.setBorder(new EmptyBorder(12,0,0,0));message.setVisible(false);
        JPanel content=new SettingsUi.Column();content.setBorder(new EmptyBorder(22,26,24,26));
        JPanel pageHeading=Theme.panel(new BorderLayout(12,0));pageHeading.add(Theme.heading(com.eye.i18n.I18n.text("电脑使用"),26));
        JButton limitsButton=button(com.eye.i18n.I18n.text("应用限制"));Theme.smallButton(limitsButton,"primary","shield");limitsButton.addActionListener(e->openLimits.run());JPanel pageActions=Theme.panel(new FlowLayout(FlowLayout.RIGHT,0,0));pageActions.add(limitsButton);pageHeading.add(pageActions,BorderLayout.EAST);
        content.add(pageHeading);content.add(Box.createVerticalStrut(5));
        content.add(new SettingsUi.Text(com.eye.i18n.I18n.text("查看每天的电脑使用时长和常用应用。没有开启专注计时也会记录。"),12,Theme.SECONDARY));
        content.add(Box.createVerticalStrut(18));
        JPanel controls=Theme.panel(new FlowLayout(FlowLayout.RIGHT,4,0));
        JButton previous=SettingsUi.iconButton("prev",com.eye.i18n.I18n.text("前一天"),"ghost"),next=SettingsUi.iconButton("next",com.eye.i18n.I18n.text("后一天"),"ghost"),today=button(com.eye.i18n.I18n.text("今天")),select=SettingsUi.iconButton("check",com.eye.i18n.I18n.text("查看"),"ghost");
        previous.addActionListener(e->{followToday=false;selected=selected.minusDays(1);dateField.setText(selected.toString());refresh();});
        next.addActionListener(e->{followToday=false;selected=selected.plusDays(1);dateField.setText(selected.toString());refresh();});
        today.addActionListener(e->{followToday=true;selected=activity==null?LocalDate.now():activity.today();dateField.setText(selected.toString());refresh();});
        select.addActionListener(e->{try{selected=LocalDate.parse(dateField.getText().trim());followToday=false;refresh();}catch(Exception ex){showMessage(com.eye.i18n.I18n.text("请输入日期，例如 2026-10-04。"));}});
        Theme.field(dateField);dateField.setColumns(0);dateField.setPreferredSize(new Dimension(122,34));dateField.setHorizontalAlignment(SwingConstants.CENTER);dateField.setFont(Theme.numbers(12));dateField.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("日期"));dateField.addActionListener(e->select.doClick());
        controls.add(previous);controls.add(dateField);controls.add(select);controls.add(next);controls.add(Box.createHorizontalStrut(4));controls.add(today);
        content.add(new AdaptiveToolbar(Theme.heading(com.eye.i18n.I18n.text("使用概览"),16),controls,12));content.add(Box.createVerticalStrut(12));
        JPanel cards=new Theme.Surface(new GridLayout(1,4,0,0),Theme.SURFACE,Theme.LINE,16);cards.setBorder(new EmptyBorder(16,0,16,0));String[] labels={com.eye.i18n.I18n.text("总使用时间"),com.eye.i18n.I18n.text("软件实测"),com.eye.i18n.I18n.text("活跃使用"),com.eye.i18n.I18n.text("系统补充估算")};
        for(int i=0;i<4;i++){
            boolean primary=i==0;JPanel card=Theme.panel(new BorderLayout(0,12));card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,0,i==3?0:1,Theme.LINE),new EmptyBorder(0,16,0,16)));
            JPanel title=Theme.panel(new BorderLayout(6,0));JLabel label=Theme.label(labels[i],10.5f,Theme.SECONDARY);title.add(label);card.add(title,BorderLayout.NORTH);
            summaries[i]=Theme.heading("—",24);summaries[i].setForeground(primary?Theme.GREEN:Theme.INK);card.add(summaries[i]);cards.add(card);
        }
        content.add(cards);content.add(Box.createVerticalStrut(6));
        content.add(disclosure("统计口径",com.eye.i18n.I18n.text("EyeRelax 运行且桌面解锁时计入实测。活跃使用扣除连续 5 分钟无操作的时间。")));content.add(Box.createVerticalStrut(14));
        JPanel trend=surface();JPanel heading=sectionHeading("每日使用趋势","history");SettingsUi.combo(range,128);heading.add(range,BorderLayout.EAST);trend.add(heading);trend.add(Box.createVerticalStrut(10));
        JPanel legend=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0));legend.add(legendDot("软件实测",UsageChart.MEASURED));legend.add(Box.createHorizontalStrut(18));legend.add(legendDot("系统补充估算",UsageChart.ESTIMATED));trend.add(legend);trend.add(dailyPlot);
        JSeparator chartsDivider=new JSeparator();chartsDivider.setForeground(Theme.LINE);chartsDivider.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));trend.add(Box.createVerticalStrut(12));trend.add(chartsDivider);trend.add(Box.createVerticalStrut(18));
        trend.add(sectionHeading("当天每小时使用","clock"));trend.add(Box.createVerticalStrut(7));trend.add(periodLabel);hourlyPlot.setToolTipText(com.eye.i18n.I18n.text("仅软件实测；锁屏、睡眠与关机期间不累计。"));trend.add(hourlyPlot);content.add(trend);content.add(Box.createVerticalStrut(16));
        JPanel apps=surface();JPanel appHeading=sectionHeading("应用使用时长","monitor");appHeading.add(onlyWatched,BorderLayout.EAST);onlyWatched.setOpaque(false);JPanel usageIntro=new SettingsUi.Column();usageIntro.add(appHeading);usageIntro.add(Box.createVerticalStrut(14));apps.add(usageIntro);appMetric.putClientProperty("tutorial.region",usageIntro);appSearch.putClientProperty("tutorial.region",usageIntro);
        JPanel appFilters=Theme.panel(new BorderLayout(12,0));SettingsUi.combo(appMetric,220);SettingsUi.combo(appOrder,220);appFilters.add(appMetric,BorderLayout.WEST);appFilters.add(appOrder,BorderLayout.EAST);usageIntro.add(appFilters);
        Theme.field(appSearch);appSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));usageIntro.add(Box.createVerticalStrut(10));usageIntro.add(searchField(appSearch));usageIntro.add(Box.createVerticalStrut(14));
        JPanel filterSurface=Theme.panel(new BorderLayout());filterSurface.setBorder(new EmptyBorder(4,0,0,0));
        JPanel filterRanges=Theme.panel(new GridLayout(1,2,20,0));
        filterRanges.add(new QuickRange(com.eye.i18n.I18n.text("最短使用时长"),appMinimum,ComputerUsagePanel::minimumLabel,0,1,5,10,30,60,120));
        filterRanges.add(new QuickRange(com.eye.i18n.I18n.text("时长排名 · 应用数量比例"),appPercent,value->value==100?unrestrictedLabel():value+"%",10,20,25,50,75,100));
        filterSurface.add(filterRanges);apps.add(filterSurface);apps.add(Box.createVerticalStrut(10));
        apps.add(disclosure("筛选与统计说明",com.eye.i18n.I18n.text("先按时长筛选，再按排名比例保留应用。最短时长选 0、比例选 100% 可查看全部。")+"\n"+com.eye.i18n.I18n.text("前台使用扣除连续 5 分钟无操作的时间。总运行时间包含后台运行。")));
        apps.add(Box.createVerticalStrut(10));apps.add(rankingSummary);apps.add(Box.createVerticalStrut(4));apps.add(appPlot);content.add(apps);content.add(Box.createVerticalStrut(16));
        JPanel manage=surface();watchedSearch.putClientProperty("tutorial.region",manage);JPanel managedHeading=sectionHeading("关注的应用","grid");managedHeading.add(watchedSummary,BorderLayout.EAST);manage.add(managedHeading);
        manage.add(Box.createVerticalStrut(5));manage.add(new SettingsUi.Text(com.eye.i18n.I18n.text("可以从开始菜单选择应用，也可以手动添加程序，再用“只看关注的应用”查看用量。"),11,Theme.SECONDARY));
        manage.add(Box.createVerticalStrut(12));
        JPanel actions=Theme.panel(new BorderLayout(8,0));JButton scan=tutorialAdd=button(com.eye.i18n.I18n.text("添加关注…")),add=button(com.eye.i18n.I18n.text("手动添加…")),remove=button(com.eye.i18n.I18n.text("移除关注"));
        Theme.smallButton(scan,"primary","plus");Theme.smallButton(add,"secondary","folder");Theme.smallButton(remove,"ghost","trash");
        JPanel addActions=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0));addActions.add(scan);addActions.add(Box.createHorizontalStrut(8));addActions.add(add);actions.add(addActions,BorderLayout.WEST);JPanel viewActions=Theme.panel(new FlowLayout(FlowLayout.RIGHT,0,0));JPanel viewSwitch=new Theme.Surface(new GridLayout(1,2,0,0),Theme.PAGE,Theme.LINE,10);viewSwitch.setBorder(new EmptyBorder(2,2,2,2));ButtonGroup viewGroup=new ButtonGroup();viewGroup.add(watchedTiles);viewGroup.add(watchedList);Theme.smallButton(watchedTiles,"segment","grid");Theme.smallButton(watchedList,"segment","list");watchedTiles.setIcon(Icons.of("grid",18,null));watchedList.setIcon(Icons.of("list",18,null));watchedTiles.setPreferredSize(new Dimension(38,30));watchedList.setPreferredSize(new Dimension(38,30));watchedTiles.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("平铺视图"));watchedList.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("列表视图"));watchedTiles.setSelected(true);viewSwitch.add(watchedList);viewSwitch.add(watchedTiles);viewActions.add(viewSwitch);viewActions.add(Box.createHorizontalStrut(8));viewActions.add(remove);actions.add(viewActions,BorderLayout.EAST);
        remove.setEnabled(false);watched.addListSelectionListener(e->remove.setEnabled(!watched.isSelectionEmpty()));
        scan.putClientProperty("tutorial.region",manage);scan.addActionListener(e->scanApplications(scan));add.addActionListener(e->addApplication());remove.addActionListener(e->{if(activity!=null&&!watched.isSelectionEmpty()){for(Application a:watched.getSelectedValuesList())activity.removeApplication(a.path.toString());refreshCatalog();refresh();}});
        manage.add(new AdaptiveToolbar(addActions,viewActions,10));manage.add(Box.createVerticalStrut(10));Theme.field(watchedSearch);watchedSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));manage.add(searchField(watchedSearch));manage.add(Box.createVerticalStrut(12));
        watched.setBackground(Theme.SURFACE);watched.setSelectionBackground(Theme.MINT);watched.setSelectionForeground(Theme.GREEN);
        watched.setCellRenderer((list,app,index,isSelected,focus)->{
            boolean tiles=watchedViewIndex==0;double scale=ProportionalPanel.scaleOf(list);
            JPanel row=new Theme.Surface(new BorderLayout(0,(int)Math.round(3*scale)),isSelected?Theme.MINT:Theme.SURFACE,isSelected?Theme.SAGE:Theme.LINE,12){
                @Override public void doLayout(){
                    if(!tiles){super.doLayout();return;}Insets in=getInsets();
                    for(Component child:getComponents())if(child instanceof JCheckBox){Dimension size=child.getPreferredSize();child.setBounds(getWidth()-in.right-size.width,in.top,size.width,size.height);}
                    else child.setBounds(in.left,in.top,getWidth()-in.left-in.right,getHeight()-in.top-in.bottom);
                }
                @Override public void paint(Graphics graphics){
                    doLayout();for(Component child:getComponents())if(child instanceof Container container)container.doLayout();
                    super.paint(graphics);
                }
            };
            row.setBorder(new EmptyBorder((int)Math.round(5*scale),(int)Math.round(8*scale),(int)Math.round(8*scale),(int)Math.round(8*scale)));
            JLabel label=new JLabel(app.name,ApplicationSelectionList.fitIcon(applicationIcon(app.path.toString()),(int)Math.round((tiles?30:24)*scale)),tiles?SwingConstants.CENTER:SwingConstants.LEFT);
            label.setFont(list.getFont());label.setForeground(isSelected?Theme.GREEN:Theme.INK);label.setIconTextGap((int)Math.round((tiles?6:12)*scale));
            label.setHorizontalTextPosition(tiles?SwingConstants.CENTER:SwingConstants.RIGHT);label.setVerticalTextPosition(tiles?SwingConstants.BOTTOM:SwingConstants.CENTER);
            JCheckBox check=new JCheckBox();check.setSelected(isSelected);check.setFocusable(false);check.setOpaque(false);
            Icon source=UIManager.getIcon("CheckBox.icon");if(source!=null)check.setIcon(ApplicationSelectionList.fitIcon(source,(int)Math.round(16*scale)));
            row.add(check,BorderLayout.EAST);
            row.add(label,BorderLayout.CENTER);JPanel cell=Theme.panel(new BorderLayout());cell.setBorder(new EmptyBorder(3,3,3,3));cell.add(row);cell.setSize(Math.max(1,list.getFixedCellWidth()>0?list.getFixedCellWidth():list.getWidth()),list.getFixedCellHeight());cell.doLayout();row.doLayout();for(Component child:row.getComponents())if(child instanceof Container c)c.doLayout();return cell;
        });
        watched.setFont(Theme.font(12));setWatchedLayout();
        JScrollPane appList=new JScrollPane(watched){
            @Override public Dimension getPreferredSize(){
                int rowHeight=Math.max(1,watched.getFixedCellHeight());int visibleRows=watchedViewIndex==0?2:4;
                int contentHeight=Math.max(rowHeight,watched.getPreferredSize().height);
                Insets insets=getInsets();return new Dimension(600,Math.min(contentHeight,visibleRows*rowHeight)+insets.top+insets.bottom);
            }
            @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        };Theme.scroll(appList);appList.getViewport().setBackground(Theme.SURFACE);
        appList.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        appList.setAlignmentX(LEFT_ALIGNMENT);
        ScrollChaining.install(appList);manage.add(appList);
        watchedTiles.addActionListener(e->selectWatchedView(0));watchedList.addActionListener(e->selectWatchedView(1));
        content.add(manage);content.add(Box.createVerticalStrut(16));
        JPanel history=surface();history.add(Theme.heading(com.eye.i18n.I18n.text("软件未运行期间"),16));
        history.add(new SettingsUi.Text(com.eye.i18n.I18n.text("用最近 30 天的 Windows 解锁和锁屏记录补充使用时长。已实测的时间不会重复计算，估算不含各应用明细。"),11,Theme.SECONDARY));
        JButton importHistory=button(com.eye.i18n.I18n.text("读取系统记录"));importHistory.addActionListener(e->readHistory(importHistory));history.add(importHistory);content.add(history);content.add(message);
        JScrollPane scroll=new JScrollPane(content);Theme.scroll(scroll);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);add(scroll);
        onSearch(appSearch,this::refreshRanking);onSearch(watchedSearch,this::refreshCatalog);
        range.addActionListener(e->refresh());onlyWatched.addActionListener(e->refreshRanking());
        appMetric.addActionListener(e->{saveFilterPreferences();refreshRanking();});appOrder.addActionListener(e->{saveFilterPreferences();refreshRanking();});
        appMinimum.addChangeListener(e->{if(!appMinimum.getValueIsAdjusting())saveFilterPreferences();refreshRanking();});
        appPercent.addChangeListener(e->{if(!appPercent.getValueIsAdjusting())saveFilterPreferences();refreshRanking();});
        addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0){if(isShowing()){timer.start();refresh();}else timer.stop();}});
    }
    private void showMessage(String value){
        message.setText(value);
        message.setVisible(!value.isBlank());
    }
    private void selectWatchedView(int index){
        watchedViewIndex=index;watchedTiles.setSelected(index==0);watchedList.setSelected(index==1);setWatchedLayout();
        if(activity!=null&&!restoringPreferences)activity.setPreference("watchedView",Integer.toString(index));
    }
    private void setWatchedLayout(){
        boolean tiles=watchedViewIndex==0;
        watched.setLayoutOrientation(tiles?JList.HORIZONTAL_WRAP:JList.VERTICAL);
        double scale=ProportionalPanel.scaleOf(this);
        watched.putClientProperty("eyerelax.listBaseWidth",null);watched.putClientProperty("eyerelax.listBaseHeight",tiles?88:44);
        watched.setVisibleRowCount(tiles?0:4);watched.setFixedCellWidth(tiles?(int)Math.round(140*scale):-1);watched.setFixedCellHeight((int)Math.round((tiles?88:44)*scale));
        watched.revalidate();watched.repaint();
    }
    private static int preferenceIndex(ComputerActivity activity,String name,int fallback,int count){
        try{return Math.max(0,Math.min(count-1,Integer.parseInt(activity.preference(name,Integer.toString(fallback)))));}catch(NumberFormatException ex){return fallback;}
    }
    private static String unrestrictedLabel(){return com.eye.i18n.I18n.isEnglish()?"Any":com.eye.i18n.I18n.text("不限");}
    private static String minimumLabel(int minutes){return minutes==0?unrestrictedLabel():minutes==60?com.eye.i18n.I18n.text("1小时"):minutes==120?com.eye.i18n.I18n.text("2小时"):minutes+com.eye.i18n.I18n.text("分");}
    private void saveFilterPreferences(){
        if(activity==null||restoringPreferences)return;
        activity.setPreferences(Map.of("appMetric",Integer.toString(appMetric.getSelectedIndex()),"appMinimumMinutes",Integer.toString(appMinimum.getValue()),"appPercent",Integer.toString(appPercent.getValue()),"appOrder",Integer.toString(appOrder.getSelectedIndex())));
    }
    public void setActivity(ComputerActivity value){
        activity=value;if(value!=null&&followToday){selected=value.today();dateField.setText(selected.toString());}restoringPreferences=true;
        try{if(value!=null){
            selectWatchedView(preferenceIndex(value,"watchedView",0,2));
            int[] legacy={0,1,10,30,60};int old=legacy[preferenceIndex(value,"appMinimum",1,legacy.length)];
            appMinimum.setValue(preferenceIndex(value,"appMinimumMinutes",old,121));appPercent.setValue(preferenceIndex(value,"appPercent",100,101));
            appOrder.setSelectedIndex(preferenceIndex(value,"appOrder",0,2));appMetric.setSelectedIndex(preferenceIndex(value,"appMetric",0,2));
        }}finally{restoringPreferences=false;}
        refreshCatalog();refresh();
    }
    static List<Map.Entry<String,Long>> filteredRanking(Map<String,Long> usage,Set<String> watched,boolean onlyWatched,long minimum,int percent,boolean ascending){
        return com.eye.stats.ApplicationRanking.filter(usage,watched,onlyWatched,minimum,percent,ascending);
    }
    private void refreshCatalog(){
        watchedModel.clear();if(activity==null)return;
        Map<String,Long> usage=activity.applicationUsageTotals();
        activity.applications().entrySet().stream().filter(e->matches(e.getValue(),e.getKey(),watchedSearch.getText())).sorted(Comparator.<Map.Entry<String,String>>comparingLong(e->usage.getOrDefault(e.getKey(),0L)).reversed().thenComparing(Map.Entry::getValue,String.CASE_INSENSITIVE_ORDER))
                .forEach(e->watchedModel.addElement(new Application(Path.of(e.getKey()),e.getValue())));
        watchedSummary.setText(watchedModel.size()+com.eye.i18n.I18n.text(" 个应用"));
    }
    private Icon applicationIcon(String path){
        String key=path.toLowerCase(Locale.ROOT);Icon ready=applicationIcons.get(key);if(ready!=null)return ApplicationSelectionList.fitIcon(ready,24);
        if(pendingIcons.add(key))new SwingWorker<Icon,Void>(){
            protected Icon doInBackground(){return ApplicationSelectionList.entry(Path.of(path),"",64).icon();}
            protected void done(){pendingIcons.remove(key);try{Icon icon=get();if(icon!=null)applicationIcons.put(key,icon);}catch(Exception ignored){}watched.repaint();appPlot.repaint();}
        }.execute();
        return UIManager.getIcon("FileView.fileIcon");
    }
    private void refresh(){
        if(activity==null)return;if(followToday){selected=activity.today();dateField.setText(selected.toString());}ComputerActivity.Day day=activity.day(selected);
        long[] values={day.measured()+day.estimated(),day.measured(),day.active(),day.estimated()};for(int i=0;i<4;i++)summaries[i].setText(duration(values[i]));
        int n=range.getSelectedIndex()==0?7:30;List<String> names=new ArrayList<>();List<Long> measured=new ArrayList<>(),estimated=new ArrayList<>();
        for(int i=n-1;i>=0;i--){LocalDate date=selected.minusDays(i);ComputerActivity.Day d=activity.day(date);names.add(date.getMonthValue()+"/"+date.getDayOfMonth());measured.add(d.measured());estimated.add(d.estimated());}
        dailyPlot.setValues(names,measured,estimated);
        names=new ArrayList<>();measured=new ArrayList<>();int boundary=day.boundarySeconds();
        periodLabel.setText(day.mixedBoundaries()?com.eye.i18n.I18n.text("当天的统计日分界有变动。"):
            (activity.hasLegacyDay(selected)?com.eye.i18n.I18n.text("旧版自然日记录 · "):"")+selected+" · "+String.format("%02d:%02d",boundary/3600,boundary%3600/60)+" — "+selected.plusDays(1)+" · "+String.format("%02d:%02d",boundary/3600,boundary%3600/60));
        for(int i=0;i<day.hours().length;i++){names.add(day.mixedBoundaries()?Integer.toString(i+1):activity.hasLegacyDay(selected)?String.format("%02d:00",i):activity.hourLabel(selected,boundary,i));measured.add(day.hours()[i]);}hourlyPlot.setValues(names,measured,List.of());
        refreshRanking();
        if(!activity.warning().isBlank())showMessage(activity.warning());
    }
    private void refreshRanking(){
        if(activity==null)return;if(followToday){selected=activity.today();dateField.setText(selected.toString());}ComputerActivity.Day day=activity.day(selected);
        Map<String,Long> searched=new HashMap<>(appMetric.getSelectedIndex()==0?day.apps():activity.applicationRuntime(selected));searched.keySet().removeIf(path->!matches(activity.displayName(path),path,appSearch.getText()));
        List<Map.Entry<String,Long>> ranking=filteredRanking(searched,activity.applications().keySet(),onlyWatched.isSelected(),appMinimum.getValue()*60_000L,appPercent.getValue(),appOrder.getSelectedIndex()==1);
        List<String> names=new ArrayList<>();List<Long> measured=new ArrayList<>();for(var entry:ranking){names.add(activity.displayName(entry.getKey()));measured.add(entry.getValue());}appPlot.setPaths(ranking.stream().map(Map.Entry::getKey).toList());appPlot.setValues(names,measured,List.of());
        rankingSummary.setText(com.eye.i18n.I18n.text("显示 ")+ranking.size()+com.eye.i18n.I18n.text(" 个应用")+"  ·  "+appMetric.getSelectedItem());
    }
    private void addApplication(){
        if(activity==null)return;
        try {
            Path p=WindowsShell.choose(this,com.eye.i18n.I18n.text("选择要关注的应用"),false);
            if(p==null)return;
            if(!Files.isRegularFile(p)||!p.toString().toLowerCase(Locale.ROOT).endsWith(".exe")){showMessage(com.eye.i18n.I18n.text("请选择现有的 EXE 应用程序。"));return;}
            if(activity.applications().containsKey(p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT))){showMessage(com.eye.i18n.I18n.text("这个应用已经在关注列表中。"));return;}
            activity.addApplication(p,p.getFileName().toString());refreshCatalog();refresh();
        }catch(Exception ex){showMessage(com.eye.i18n.I18n.text("无法打开应用选择窗口：")+ex.getMessage());}
    }
    private void scanApplications(JButton button){scanApplications(button,false);}
    private void scanApplications(JButton button,boolean force){
        if(activity==null)return;
        double scale=ProportionalPanel.scaleOf(this);
        if(reuseScan(scanCache,scannedAt,System.currentTimeMillis(),force)){showApplicationChoices(button,scale);return;}
        button.setEnabled(false);showMessage(com.eye.i18n.I18n.text("正在扫描开始菜单快捷方式…"));
        new SwingWorker<List<ApplicationSelectionList.Entry>,Void>(){
            protected List<ApplicationSelectionList.Entry> doInBackground()throws Exception{
                List<ApplicationSelectionList.Entry> apps=new ArrayList<>();
                Map<Path,String> detected=new TreeMap<>(WindowsUsageSources.commonApplications());
                Set<String> observed=new HashSet<>(activity.applicationUsageTotals().keySet());observed.addAll(activity.applicationRuntime(activity.today()).keySet());
                for(String executable:observed){Path path=Path.of(executable);if(Files.isRegularFile(path)){String name=path.getFileName().toString();detected.putIfAbsent(path,activity.displayName(executable));}}
                for(var app:detected.entrySet())apps.add(ApplicationSelectionList.entry(app.getKey(),app.getValue(),(int)Math.round(28*scale)));
                return apps;
            }
            protected void done(){button.setEnabled(true);try{scanCache=List.copyOf(get());scannedAt=System.currentTimeMillis();showApplicationChoices(button,scale);}catch(Exception ex){showMessage(com.eye.i18n.I18n.text("扫描失败：")+cause(ex));}}
        }.execute();
    }
    static boolean reuseScan(List<ApplicationSelectionList.Entry> cached,long scanned,long now,boolean force){return !force&&cached!=null&&now-scanned<SCAN_CACHE_MILLIS;}
    static List<ApplicationSelectionList.Entry> candidates(List<ApplicationSelectionList.Entry> scanned,Set<String> watched,Map<String,Long> usage){
        Set<String> excluded=new HashSet<>();for(String path:watched)excluded.add(path.toLowerCase(Locale.ROOT));
        return scanned.stream().filter(e->!excluded.contains(e.path().toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.<ApplicationSelectionList.Entry>comparingLong(e->usage.getOrDefault(e.path().toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT),0L)).reversed()
                        .thenComparing(ApplicationSelectionList.Entry::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private void showApplicationChoices(JButton button,double scale){
        showMessage("");
        var apps=candidates(scanCache,activity.applications().keySet(),activity.applicationUsageTotals());
        var result=ApplicationChooserDialog.choose(this,apps,scale);
        if(result.rescan()){SwingUtilities.invokeLater(()->scanApplications(button,true));return;}
        if(result.accepted()){
            for(var app:result.selected())activity.addApplication(app.path(),app.name());refreshCatalog();refresh();
        }
    }
    private void readHistory(JButton button){if(activity==null)return;button.setEnabled(false);showMessage(com.eye.i18n.I18n.text("正在读取系统会话记录…"));new SwingWorker<WindowsUsageSources.History,Void>(){
        protected WindowsUsageSources.History doInBackground()throws Exception{return WindowsUsageSources.history();}
        protected void done(){button.setEnabled(true);try{var result=get();if(!result.ranges().isEmpty())activity.importHistory(result.ranges());showMessage(result.message());refresh();}catch(Exception ex){showMessage(com.eye.i18n.I18n.text("未能读取系统记录：")+cause(ex)+com.eye.i18n.I18n.text("。软件实测仍正常累计。"));}}
    }.execute();}
    static boolean matches(String name,String path,String query){return ApplicationSearch.matches(name,path,query);}
    private static void onSearch(JTextField field,Runnable update){field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){update.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){update.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){update.run();}});}
    private static String cause(Exception ex){return ex.getCause()==null?ex.getMessage():ex.getCause().getMessage();}
    private static JButton button(String text){JButton b=new JButton(text);Theme.smallButton(b,"secondary",null);return b;}
    private static JComponent disclosure(String title,String explanation){
        JPanel box=new SettingsUi.Column();
        JToggleButton toggle=new JToggleButton(com.eye.i18n.I18n.text(title));
        Theme.smallButton(toggle,"ghost","info");toggle.setFont(Theme.font(10));toggle.setBorder(new EmptyBorder(5,0,5,6));
        toggle.getAccessibleContext().setAccessibleDescription(explanation);
        SettingsUi.Text detail=new SettingsUi.Text(explanation,10.5f,Theme.SECONDARY);
        detail.setBorder(new EmptyBorder(4,0,8,0));detail.setVisible(false);
        toggle.addActionListener(e->{detail.setVisible(toggle.isSelected());box.revalidate();box.repaint();});
        box.add(toggle);box.add(detail);return box;
    }
    private static JPanel sectionHeading(String title,String icon){JPanel heading=Theme.panel(new BorderLayout(8,0));JLabel label=Theme.heading(com.eye.i18n.I18n.text(title),16);label.setIcon(Icons.of(icon,18,Theme.GREEN));label.setIconTextGap(8);heading.add(label);return heading;}
    private static JComponent legendDot(String title,Color color){JLabel label=Theme.label(com.eye.i18n.I18n.text(title),10,Theme.SECONDARY);label.setIcon(new Icon(){public int getIconWidth(){return 9;}public int getIconHeight(){return 9;}public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);g.setColor(color);g.fillRoundRect(x,y,9,9,4,4);g.dispose();}});label.setIconTextGap(6);return label;}
    private static JPanel searchField(JTextField field){JPanel row=Theme.panel(new BorderLayout(8,0));JLabel icon=new JLabel(Icons.of("search",17,Theme.SECONDARY));row.add(icon,BorderLayout.WEST);row.add(field);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));return row;}
    private static JPanel surface(){Theme.Surface p=new Theme.Surface(new BorderLayout(),Theme.SURFACE,Theme.LINE,18);JPanel inner=new SettingsUi.Column();inner.setBorder(new EmptyBorder(20,20,20,20));p.add(inner);return new JPanelProxy(p,inner);}
    /** Box layout card keeps natural height inside the page's scroll column. */
    private static final class JPanelProxy extends JPanel {
        private final JPanel inner;
        JPanelProxy(JPanel shell,JPanel inner){super(new BorderLayout());this.inner=inner;setOpaque(false);super.add(shell);setAlignmentX(LEFT_ALIGNMENT);}
        @Override public Component add(Component component){return inner==null?super.add(component):inner.add(component);}
        @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
    }
    public static String duration(long ms){long s=ms/1000;return s>=3600?s/3600+com.eye.i18n.I18n.text("时 ")+s%3600/60+com.eye.i18n.I18n.text("分"):s>=60?s/60+com.eye.i18n.I18n.text("分 ")+s%60+com.eye.i18n.I18n.text("秒"):s+com.eye.i18n.I18n.text("秒");}

}
