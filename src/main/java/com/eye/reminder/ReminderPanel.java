package com.eye.reminder;

import com.eye.ui.theme.InlineErrors;
import com.eye.ui.theme.SettingsUi;

import com.eye.core.Config;
import com.eye.core.CycleEngine;
import com.eye.ui.theme.DurationInput;
import com.eye.ui.theme.Icons;
import com.eye.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;

/** Reminder presentation. Scheduler, overlap decisions and persistence remain in their existing owners. */
public final class ReminderPanel extends JPanel {
    public interface Actions { void onAdjustRhythm(); }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm[:ss]");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ReminderManager manager;
    private final Config config;
    private final CycleEngine engine;
    private final Actions actions;
    private final SettingsUi.Column list = new SettingsUi.Column();
    private final JLabel defaultMaskLabel = Theme.label("", 11, Theme.SECONDARY);
    private final JButton defaultMaskButton = new JButton();
    private final JLabel countLabel = Theme.label("", 11, Theme.SECONDARY);
    private final JLabel nextTimeLabel = Theme.label("—", 26, Theme.INK);
    private final JLabel nextNameLabel = Theme.label("", 11, Theme.SECONDARY);
    private final JLabel activeCountLabel = Theme.label("0", 26, Theme.INK);
    private final JLabel recurringCountLabel = Theme.label("", 11, Theme.SECONDARY);
    private final JTextField search = new SettingsUi.PlaceholderField(com.eye.i18n.I18n.text("搜索提醒内容"));
    private final JToggleButton[] filters = new JToggleButton[4];
    private int filterIndex;
    private JButton tutorialAdd;
    public JComponent tutorialTarget(String key){return switch(key){case "add"->tutorialAdd;case "search"->search;case "mask"->defaultMaskButton;case "enabled"->filters[0];default->filters[2];};}
    private com.eye.ui.theme.CollectionBrowser<ReminderItem> library;
    private final CardLayout listCards=new CardLayout();
    private final JPanel listHost=new JPanel(listCards);

    public ReminderPanel(ReminderManager manager, Config config, CycleEngine engine, Actions actions) {
        this.manager=manager; this.config=config; this.engine=engine; this.actions=actions;
        setOpaque(false); setLayout(new BorderLayout());
        add(build(), BorderLayout.CENTER);
        manager.setChangedHandler(() -> { refresh(); engine.onReminderScheduleChanged(); });
        refresh();
    }

    private JComponent build() {
        JPanel root = Theme.panel(new BorderLayout(0, 18)); root.setBorder(new EmptyBorder(24, 28, 24, 28));
        JPanel header = Theme.panel(new BorderLayout(16, 0));
        JPanel copy = Theme.column(); copy.add(Theme.heading(com.eye.i18n.I18n.text("提醒计划"), 24)); copy.add(Box.createVerticalStrut(5));
        copy.add(new SettingsUi.Text(com.eye.i18n.I18n.text("把重要的事，安排在合适的时间。"), 12, Theme.SECONDARY));
        header.add(copy);
        JButton add = tutorialAdd = new JButton(com.eye.i18n.I18n.text("新增提醒")); Theme.button(add, "primary", "plus"); add.addActionListener(e -> edit(null));
        JPanel addWrap = Theme.panel(new GridBagLayout()); addWrap.add(add); header.add(addWrap, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = Theme.panel(new BorderLayout(0, 18));
        JPanel overview = Theme.panel(new GridLayout(1,3,12,0));
        nextTimeLabel.setFont(Theme.numbers(26));activeCountLabel.setFont(Theme.numbers(26));
        overview.add(summaryCard(com.eye.i18n.I18n.text("下一次提醒"),"clock",nextTimeLabel,nextNameLabel,false));
        overview.add(summaryCard(com.eye.i18n.I18n.text("生效中的提醒"),"bell",activeCountLabel,recurringCountLabel,false));
        SettingsUi.valueButton(defaultMaskButton, "clock");defaultMaskButton.addActionListener(e -> editDefaultMask());
        defaultMaskLabel.setText(com.eye.i18n.I18n.text("单个提醒可单独设置"));
        JComponent defaults=summaryCard(com.eye.i18n.I18n.text("统一遮罩时长"),"shield",defaultMaskButton,defaultMaskLabel,true);
        defaultMaskButton.putClientProperty("tutorial.region",defaults);overview.add(defaults);center.add(overview,BorderLayout.NORTH);

        Theme.Surface panel = Theme.card(new BorderLayout(0, 14)); panel.setBorder(new EmptyBorder(16, 16, 12, 16));
        JPanel toolbar = Theme.panel(new BorderLayout(0, 14));
        Theme.Surface tabs = new Theme.Surface(new GridLayout(1,4,4,0),Theme.GROUP,null,10);
        tabs.setBorder(new EmptyBorder(4,4,4,4));ButtonGroup group = new ButtonGroup();
        String[] names={com.eye.i18n.I18n.text("已启用"), com.eye.i18n.I18n.text("已停用"),com.eye.i18n.I18n.text("已完成／过期"),com.eye.i18n.I18n.text("提醒模板")};
        for (int i=0;i<filters.length;i++) {
            final int idx=i; JToggleButton button=new JToggleButton(names[i]); Theme.smallButton(button,"segment",null);
            button.setBorder(new EmptyBorder(8, 8, 8, 8)); group.add(button); filters[i]=button;
            button.addActionListener(e -> {filterIndex=idx;refresh();}); tabs.add(button);
        }
        filters[0].setSelected(true); toolbar.add(tabs,BorderLayout.NORTH);
        Theme.field(search); search.setFont(Theme.font(12)); search.setToolTipText(null);
        search.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("搜索提醒内容"));
        JPanel searchWrap=Theme.panel(new BorderLayout(7,0)); searchWrap.add(new JLabel(Icons.of("search",16,Theme.SECONDARY)),BorderLayout.WEST);
        searchWrap.add(search);searchWrap.setPreferredSize(new Dimension(240,34));toolbar.add(searchWrap,BorderLayout.CENTER);
        search.getDocument().addDocumentListener(changes(this::refresh)); panel.add(toolbar,BorderLayout.NORTH);
        list.setBorder(new EmptyBorder(0,0,2,4));
        JScrollPane scroll=new JScrollPane(list); Theme.scroll(scroll); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listHost.setOpaque(false);listHost.add(scroll,"active");
        library=new com.eye.ui.theme.CollectionBrowser<>(manager::list,i->(i.pinned?"★ ":"")+i.title,i->lifecycleLabel(i)+" · "+scheduleDescription(i),i->Instant.ofEpochMilli(i.archivedAtEpochMs>0?i.archivedAtEpochMs:i.nextTriggerEpochMs).atZone(ZoneId.systemDefault()).toLocalDate());
        library.identity(i->i.id);
        library.action(com.eye.i18n.I18n.text("再提醒一次"),"plus",i->{ReminderItem fresh=manager.freshCopy(i.id);if(fresh!=null)edit(fresh);});
        library.action(com.eye.i18n.I18n.text("启用提醒"),"play",i->category(i)==1,i->manager.setEnabled(i.id,true));
        library.action(com.eye.i18n.I18n.text("保存为模板"),"grid",i->manager.saveTemplate(i.id));
        library.action(com.eye.i18n.I18n.text("切换置顶"),"pin",i->manager.setPinned(i.id,!i.pinned));
        library.action(com.eye.i18n.I18n.text("删除"),"trash",i->{if(JOptionPane.showConfirmDialog(this,com.eye.i18n.I18n.text(category(i)==3 ? "删除这个提醒模板？" : "删除这个提醒？历史记录将保留。"),com.eye.i18n.I18n.text("确认删除"),JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)manager.delete(i.id);});
        listHost.add(library,"library");panel.add(listHost,BorderLayout.CENTER);
        JPanel foot = Theme.panel(new BorderLayout()); foot.setBorder(new EmptyBorder(4,2,0,2)); foot.add(countLabel,BorderLayout.WEST);
        JLabel note = Theme.label(com.eye.i18n.I18n.text("遮罩期间暂停工作，并计入休息"),10,Theme.SECONDARY); foot.add(note,BorderLayout.EAST);
        panel.add(foot,BorderLayout.SOUTH); center.add(panel,BorderLayout.CENTER); root.add(center,BorderLayout.CENTER);
        return root;
    }

    private JComponent summaryCard(String title,String icon,JComponent value,JLabel detail,boolean tinted){
        Theme.Surface card=new Theme.Surface(new BorderLayout(0,10),tinted?Theme.MINT:Theme.SURFACE,tinted?null:Theme.LINE,14);
        card.setBorder(new EmptyBorder(16,16,15,16));
        JLabel caption=Theme.label(title,11,Theme.SECONDARY);caption.setIcon(Icons.of(icon,15,Theme.GREEN));caption.setIconTextGap(7);card.add(caption,BorderLayout.NORTH);
        JPanel content=Theme.panel(new BorderLayout(0,6));JPanel valueWrap=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0));valueWrap.add(value);content.add(valueWrap,BorderLayout.NORTH);
        detail.setMinimumSize(new Dimension(0,18));content.add(detail,BorderLayout.SOUTH);card.add(content);return card;
    }

    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
        defaultMaskButton.setText(fmtDuration(config.reminderDefaultMaskSeconds) + "  ›");
        list.removeAll();
        List<ReminderItem> items = manager.list();
        String[] names={com.eye.i18n.I18n.text("已启用"),com.eye.i18n.I18n.text("已停用"),com.eye.i18n.I18n.text("已完成／过期"),com.eye.i18n.I18n.text("提醒模板")};
        long[] counts=new long[4];for(ReminderItem item:items)counts[category(item)]++;
        ReminderItem upcoming=items.stream().filter(i->category(i)==0&&i.enabled&&!i.awaitingCompletion&&i.nextTriggerEpochMs>0).min(Comparator.comparingLong(i->i.nextTriggerEpochMs)).orElse(null);
        nextTimeLabel.setText(upcoming==null?"—":Instant.ofEpochMilli(upcoming.nextTriggerEpochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")));
        nextNameLabel.setText(upcoming==null?com.eye.i18n.I18n.text("暂无待触发的提醒"):upcoming.title);
        nextNameLabel.setToolTipText(upcoming==null?null:upcoming.title);
        activeCountLabel.setText(Long.toString(counts[0]));
        long recurring=items.stream().filter(i->category(i)==0&&isRecurring(i)).count();
        recurringCountLabel.setText(com.eye.i18n.I18n.text("其中重复提醒 ")+recurring);
        for (int i=0;i<filters.length;i++) filters[i].setText(names[i] + " " + counts[i]);
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        List<ReminderItem> shown = items.stream().filter(i -> category(i)==filterIndex)
                .filter(i -> query.isEmpty() || (i.title==null?"":i.title).toLowerCase(Locale.ROOT).contains(query)).toList();
        countLabel.setText(com.eye.i18n.I18n.text("显示 ") + shown.size() + " / " + items.size() + com.eye.i18n.I18n.text(" 项提醒"));
        if(search.getParent()!=null)search.getParent().setVisible(filterIndex==0);
        listCards.show(listHost,filterIndex==0?"active":"library");
        if(filterIndex!=0){library.predicate(i->category(i)==filterIndex);library.refresh();return;}
        if (shown.isEmpty()) list.add(emptyState(query.isEmpty()));
        else for (ReminderItem item:shown) { list.add(row(item)); }
        list.revalidate(); list.repaint();
    }

    private JComponent emptyState(boolean noItems) {
        JPanel empty=Theme.panel(new GridBagLayout()); empty.setPreferredSize(new Dimension(400,280));
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE,320));
        JPanel copy=Theme.column();
        Theme.Surface halo=new Theme.Surface(new GridBagLayout(),Theme.MINT,null,28);halo.setPreferredSize(new Dimension(64,64));halo.setMaximumSize(new Dimension(64,64));halo.add(new JLabel(Icons.of("bell",28,Theme.GREEN)));halo.setAlignmentX(CENTER_ALIGNMENT);copy.add(halo);
        copy.add(Box.createVerticalStrut(14));
        JLabel title=Theme.heading(noItems?com.eye.i18n.I18n.text("暂无提醒"):com.eye.i18n.I18n.text("没有匹配的提醒"),16);title.setAlignmentX(CENTER_ALIGNMENT);copy.add(title);
        copy.add(Box.createVerticalStrut(8));
        JLabel tip=Theme.label(noItems?com.eye.i18n.I18n.text("比如两小时后喝水，或每周五回顾本周。"):com.eye.i18n.I18n.text("试试其他关键词或分类。"),11,Theme.SECONDARY);tip.setAlignmentX(CENTER_ALIGNMENT);copy.add(tip);
        copy.add(Box.createVerticalStrut(18));JButton action=new JButton(com.eye.i18n.I18n.text(noItems?(manager.list().isEmpty()?"创建第一个提醒":"新增提醒"):"清除搜索"));Theme.smallButton(action,"primary",noItems?"plus":"search");action.setAlignmentX(CENTER_ALIGNMENT);action.addActionListener(e->{if(noItems)edit(null);else search.setText("");});copy.add(action);
        empty.add(copy); return empty;
    }

    private JComponent row(ReminderItem item) {
        JPanel card=Theme.panel(new BorderLayout(16,0));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.LINE),new EmptyBorder(17,4,17,2)));
        card.setPreferredSize(new Dimension(560,98));card.setMaximumSize(new Dimension(Integer.MAX_VALUE,98));card.setMinimumSize(new Dimension(240,98));
        card.putClientProperty("EyeRelax.reminderId",item.id);
        ZonedDateTime next=item.nextTriggerEpochMs>0?Instant.ofEpochMilli(item.nextTriggerEpochMs).atZone(ZoneId.systemDefault()):null;
        JPanel schedule=new SettingsUi.Column();schedule.setPreferredSize(new Dimension(78,54));
        JLabel clock=Theme.label(item.awaitingCompletion?com.eye.i18n.I18n.text("待关闭"):item.enabled&&next!=null?next.format(DateTimeFormatter.ofPattern("HH:mm")):"—",21,Theme.INK);
        clock.setFont(item.awaitingCompletion?Theme.font(12):Theme.numbers(21));schedule.add(clock);schedule.add(Box.createVerticalStrut(5));
        String day=next==null?com.eye.i18n.I18n.text("单次提醒"):next.toLocalDate().equals(LocalDate.now())?com.eye.i18n.I18n.text("今天"):next.format(DateTimeFormatter.ofPattern("MM/dd"));
        schedule.add(Theme.label(day,10,Theme.SECONDARY));JPanel scheduleWrap=Theme.panel(new GridBagLayout());scheduleWrap.add(schedule);card.add(scheduleWrap,BorderLayout.WEST);
        JPanel words=Theme.panel(new GridBagLayout());
        String title=item.title==null||item.title.isBlank()?com.eye.i18n.I18n.text("提醒"):item.title;
        JLabel name=Theme.heading(title,14);name.setToolTipText(title);name.setMinimumSize(new Dimension(0,20));if(!item.enabled)name.setForeground(Theme.MUTED);
        if(item.pinned){name.setIcon(Icons.of("pin",13,Theme.GREEN));name.setIconTextGap(5);}
        String metadata=scheduleDescription(item);
        JLabel detail=Theme.label(metadata,11,Theme.SECONDARY);detail.setToolTipText(metadata);detail.setMinimumSize(new Dimension(0,18));
        GridBagConstraints textGbc=new GridBagConstraints();textGbc.gridx=0;textGbc.gridy=0;textGbc.weightx=1;textGbc.fill=GridBagConstraints.HORIZONTAL;textGbc.anchor=GridBagConstraints.WEST;
        words.add(name,textGbc);textGbc.gridy=1;textGbc.insets=new Insets(5,0,0,0);words.add(detail,textGbc);
        JLabel behavior=Theme.label(com.eye.i18n.I18n.text("遮罩 ")+fmtDuration(item.effectiveMaskSeconds(config.reminderDefaultMaskSeconds))+" · "+(item.resetWorkTimer?com.eye.i18n.I18n.text("重置工作轮"):com.eye.i18n.I18n.text("继续本轮")),10,Theme.MUTED);
        behavior.setMinimumSize(new Dimension(0,16));textGbc.gridy=2;textGbc.insets=new Insets(3,0,0,0);words.add(behavior,textGbc);card.add(words,BorderLayout.CENTER);
        JPanel buttons=Theme.panel(new FlowLayout(FlowLayout.RIGHT,4,0));
        JCheckBox toggle=new JCheckBox();SettingsUi.toggle(toggle,item.enabled?com.eye.i18n.I18n.text("停用提醒"):com.eye.i18n.I18n.text("启用提醒"));toggle.setSelected(item.enabled);toggle.setToolTipText(null);toggle.addActionListener(e->manager.setEnabled(item.id,!item.enabled));
        JButton edit=SettingsUi.iconButton("edit",com.eye.i18n.I18n.text("修改提醒"),"ghost");edit.addActionListener(e->edit(item));
        JButton more=new JButton("⋯");Theme.smallButton(more,"ghost",null);more.setFont(Theme.bold(18));more.setPreferredSize(new Dimension(32,32));more.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("更多操作"));
        more.addActionListener(e->showReminderActions(more,item));
        buttons.add(toggle);buttons.add(edit);buttons.add(more);JPanel buttonWrap=Theme.panel(new GridBagLayout());buttonWrap.add(buttons);card.add(buttonWrap,BorderLayout.EAST);return card;
    }

    private void showReminderActions(JButton anchor,ReminderItem item){
        JPopupMenu menu=new JPopupMenu();
        menuItem(menu,item.pinned?"取消置顶":"置顶提醒","pin",()->manager.setPinned(item.id,!item.pinned));
        menuItem(menu,"保存为模板","grid",()->manager.saveTemplate(item.id));menu.addSeparator();
        JMenuItem remove=menuItem(menu,"删除提醒","trash",()->{
            if(JOptionPane.showConfirmDialog(this,com.eye.i18n.I18n.text("删除提醒“")+item.title+"”？",com.eye.i18n.I18n.text("删除提醒"),JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)manager.delete(item.id);
        });remove.setForeground(Theme.DANGER);menu.show(anchor,anchor.getWidth()-menu.getPreferredSize().width,anchor.getHeight()+4);
    }
    private JMenuItem menuItem(JPopupMenu menu,String text,String icon,Runnable action){
        JMenuItem item=new JMenuItem(com.eye.i18n.I18n.text(text),Icons.of(icon,15,Theme.SECONDARY));item.setFont(Theme.font(12));item.setBorder(new EmptyBorder(9,12,9,16));item.addActionListener(e->action.run());menu.add(item);return item;
    }
    private static boolean isRecurring(ReminderItem item){return item.kind==ReminderItem.ScheduleKind.DAILY||item.kind==ReminderItem.ScheduleKind.WEEKLY||item.kind==ReminderItem.ScheduleKind.INTERVAL||item.kind==ReminderItem.ScheduleKind.SEQUENCE;}

    static int category(ReminderItem item){
        return switch(item.lifecycle){case TEMPLATE->3;case COMPLETED,EXPIRED,ENDED->2;case CURRENT->item.enabled||item.awaitingCompletion?0:1;};
    }
    private static String lifecycleLabel(ReminderItem item){return switch(item.lifecycle){case TEMPLATE->com.eye.i18n.I18n.text("模板");case COMPLETED->com.eye.i18n.I18n.text("已完成");case EXPIRED->com.eye.i18n.I18n.text("已过期");case ENDED->com.eye.i18n.I18n.text("已结束（旧记录）");case CURRENT->item.enabled?com.eye.i18n.I18n.text("已启用"):com.eye.i18n.I18n.text("已停用");};}
    private DurationInput.Mode inputMode(){return config.timeInputMode==Config.TimeInputMode.WHEEL?DurationInput.Mode.WHEEL:DurationInput.Mode.SEGMENTED;}
    private void editDefaultMask() {
        DurationInput input=new DurationInput(); input.setMode(inputMode()); input.setSeconds(config.reminderDefaultMaskSeconds);
        JPanel body=Theme.panel(new BorderLayout());body.add(input,BorderLayout.NORTH);
        if(!SettingsUi.confirm(this,com.eye.i18n.I18n.text("默认提醒遮罩时间"),com.eye.i18n.I18n.text("至少 5 秒；到时后仍需手动关闭遮罩。"),body,com.eye.i18n.I18n.text("保存"),()->InlineErrors.number(input,input::getSeconds,5,Integer.MAX_VALUE,com.eye.i18n.I18n.text("遮罩时间不能少于 5 秒。"))))return;
        try {int seconds=input.getSeconds();if(seconds<5)throw new IllegalArgumentException();config.reminderDefaultMaskSeconds=seconds;config.save();refresh();}
        catch(Exception ex){JOptionPane.showMessageDialog(this,com.eye.i18n.I18n.text("遮罩时间不能少于 5 秒。"),com.eye.i18n.I18n.text("提醒计划"),JOptionPane.WARNING_MESSAGE);}
    }

    private void edit(ReminderItem existing) {
        ReminderItem base=existing==null?new ReminderItem():existing.copy();
        while(true) {
            Editor e=new Editor(base);
            int result=SettingsUi.confirm(this,existing==null||manager.get(existing.id)==null?com.eye.i18n.I18n.text("新增提醒"):com.eye.i18n.I18n.text("修改提醒"),com.eye.i18n.I18n.text("为重要的事留一个提醒。"),e.root,com.eye.i18n.I18n.text("保存提醒"),e::validate) ? JOptionPane.OK_OPTION : JOptionPane.CANCEL_OPTION;
            if(result!=JOptionPane.OK_OPTION)return;
            ReminderItem item=e.prepared;
            long[] rest=engine.projectedRelaxWindow();
            long maskEnd=item.nextTriggerEpochMs + item.effectiveMaskSeconds(config.reminderDefaultMaskSeconds)*1000L;
            if(rest!=null && item.nextTriggerEpochMs<=rest[1] && maskEnd>=rest[0]) {
                Object[] opts={com.eye.i18n.I18n.text("保持重叠并保存"),com.eye.i18n.I18n.text("返回修改提醒"),com.eye.i18n.I18n.text("调整我的节奏")};
                String msg=com.eye.i18n.I18n.text("这个提醒与当前预计休息时段重叠：\n")+formatEpoch(rest[0])+" ～ "+formatEpoch(rest[1])+"。\n\n"+
                        com.eye.i18n.I18n.text("若保持重叠，本次休息不会先锁屏，只提示冲突；到提醒时再进入强制遮罩。遮罩关闭后若仍在休息时段，再执行正常休息模式。");
                int ch=JOptionPane.showOptionDialog(this,msg,com.eye.i18n.I18n.text("提醒与休息重叠"),JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,opts,opts[0]);
                if(ch==1||ch==JOptionPane.CLOSED_OPTION){base=item;continue;}
                if(ch==2){if(actions!=null)actions.onAdjustRhythm();return;}
            }
            manager.upsert(item); return;
        }
    }


    private final class Editor {
        final SettingsUi.Column root=new SettingsUi.Column(){
            @Override public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();return new Dimension(520,d.height);}
        };
        final JTextField title=new SettingsUi.PlaceholderField(com.eye.i18n.I18n.text("例如：喝水、站起来活动"));
        final JComboBox<String> kind=new JComboBox<>(new String[]{com.eye.i18n.I18n.text("指定日期和时间"),com.eye.i18n.I18n.text("距离现在一段时间"),com.eye.i18n.I18n.text("每天固定时间"),com.eye.i18n.I18n.text("每周固定时间"),com.eye.i18n.I18n.text("固定间隔循环"),com.eye.i18n.I18n.text("自定义间隔序列")});
        final CardLayout scheduleCards=new CardLayout();final JPanel scheduleHost=new JPanel(scheduleCards){
            @Override public Dimension getPreferredSize(){for(Component c:getComponents())if(c.isVisible())return c.getPreferredSize();return super.getPreferredSize();}
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        };
        final JTextField date=new JTextField(),time=new JTextField();final DurationInput relative=new DurationInput();
        final JTextField dailyTime=new JTextField(),weeklyTime=new JTextField();final JCheckBox[] days=new JCheckBox[7];
        final JComboBox<com.eye.calendar.DayCategory> dailyCategory=new JComboBox<>(com.eye.calendar.DayCategory.values()),weeklyCategory=new JComboBox<>(com.eye.calendar.DayCategory.values());
        final JCheckBox useDefault=new JCheckBox(com.eye.i18n.I18n.text("使用统一默认遮罩时间"));final DurationInput mask=new DurationInput();
        final JCheckBox resetWork=new JCheckBox(com.eye.i18n.I18n.text("关闭提醒遮罩后，从完整工作时长重新开始（未勾选则继续原本剩余时间）"));
        final JButton relativeButton=new JButton(),maskButton=new JButton();
        final JToggleButton[] scheduleTabs=new JToggleButton[6];
        final DurationInput interval=new DurationInput();
        final JButton intervalButton=new JButton();
        final ReminderSequenceInput sequence=new ReminderSequenceInput(inputMode());
        final String reminderId;
        Editor(ReminderItem item){
            reminderId=item.id;
            JLabel titleLabel=Theme.label(com.eye.i18n.I18n.text("提醒内容"),12,Theme.INK);title.putClientProperty("tutorial.label",titleLabel);root.add(titleLabel);root.add(Box.createVerticalStrut(7));
            Theme.field(title);title.setFont(Theme.font(14));title.setText(item.title);title.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));title.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("提醒内容"));root.add(title);root.add(Box.createVerticalStrut(18));
            JLabel scheduleLabel=Theme.label(com.eye.i18n.I18n.text("何时提醒"),12,Theme.INK);root.add(scheduleLabel);root.add(Box.createVerticalStrut(8));
            JPanel tabs=Theme.panel(new GridLayout(2,3,6,6));tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE,78));ButtonGroup group=new ButtonGroup();
            String[] captions={com.eye.i18n.I18n.text("指定时间"),com.eye.i18n.I18n.text("多久以后"),com.eye.i18n.I18n.text("每天"),com.eye.i18n.I18n.text("每周"),com.eye.i18n.I18n.text("间隔循环"),com.eye.i18n.I18n.text("间隔序列")};
            for(int i=0;i<6;i++){final int idx=i;JToggleButton b=new JToggleButton(captions[i]);Theme.smallButton(b,"secondary",null);b.setBorder(new EmptyBorder(8,8,8,8));group.add(b);scheduleTabs[i]=b;b.addActionListener(e->kind.setSelectedIndex(idx));tabs.add(b);}
            tabs.putClientProperty("tutorial.label",scheduleLabel);root.add(tabs);root.add(Box.createVerticalStrut(12));
            scheduleHost.putClientProperty("tutorial.label",scheduleLabel);scheduleHost.setOpaque(false);scheduleHost.add(absolutePanel(),"0");scheduleHost.add(relativePanel(),"1");scheduleHost.add(dailyPanel(),"2");scheduleHost.add(weeklyPanel(),"3");scheduleHost.add(intervalPanel(),"4");scheduleHost.add(sequence,"5");root.add(scheduleHost);root.add(Box.createVerticalStrut(18));
            mask.setMode(inputMode());SettingsUi.toggle(useDefault,com.eye.i18n.I18n.text("使用统一默认遮罩时间"));SettingsUi.toggle(resetWork,com.eye.i18n.I18n.text("关闭后重置工作轮"));
            SettingsUi.valueButton(maskButton,"clock");maskButton.setPreferredSize(new Dimension(164,34));maskButton.addActionListener(e->{
                if(useDefault.isSelected()){
                    useDefault.setSelected(false);
                    mask.setSeconds(config.reminderDefaultMaskSeconds);
                    mask.setEnabled(true);
                }
                editDuration(mask,maskButton,com.eye.i18n.I18n.text("单个提醒遮罩时间"),com.eye.i18n.I18n.text("至少 5 秒；只对本提醒生效。"));
                syncMaskSummary();
            });
            root.add(SettingsUi.group(com.eye.i18n.I18n.text("遮罩与计时"),
                    SettingsUi.row(com.eye.i18n.I18n.text("使用统一默认"), com.eye.i18n.I18n.text("默认 ")+fmtDuration(config.reminderDefaultMaskSeconds)+com.eye.i18n.I18n.text("，最低 5 秒。"),useDefault),
                    SettingsUi.row(com.eye.i18n.I18n.text("本提醒遮罩时长"), com.eye.i18n.I18n.text("到时后仍需手动关闭。"),maskButton),
                    SettingsUi.row(com.eye.i18n.I18n.text("关闭后重置工作轮"), com.eye.i18n.I18n.text("关闭此开关时，继续原本剩余的工作时间。"),resetWork)));
            root.add(com.eye.tutorial.TutorialFormHelp.create(ReminderPanel.this,java.util.List.of(
                new com.eye.tutorial.TutorialFormHelp.Hint("填写提醒内容，例如喝水。",title),
                new com.eye.tutorial.TutorialFormHelp.Hint("选择提醒方式：一次、每天、每周、间隔循环或间隔序列。",tabs),
                new com.eye.tutorial.TutorialFormHelp.Hint("在这里设置提醒的时间或间隔。",scheduleHost),
                new com.eye.tutorial.TutorialFormHelp.Hint("设置提醒遮罩时长，完成后保存提醒。",maskButton))));
            init(item);syncMaskSummary();scheduleTabs[kind.getSelectedIndex()].setSelected(true);
            kind.addActionListener(ev->{scheduleCards.show(scheduleHost,""+kind.getSelectedIndex());scheduleTabs[kind.getSelectedIndex()].setSelected(true);root.revalidate();root.repaint();SwingUtilities.invokeLater(()->SettingsUi.resizeDialogToContent(root));});
            useDefault.addActionListener(ev->{mask.setEnabled(!useDefault.isSelected());syncMaskSummary();});
            relativeButton.setText(fmtDuration(relative.getSeconds())+com.eye.i18n.I18n.text("后  ›"));
        }
        private void syncMaskSummary(){
            maskButton.setEnabled(true);
            maskButton.setText(fmtDuration(useDefault.isSelected()?config.reminderDefaultMaskSeconds:mask.getSeconds())+(useDefault.isSelected()?com.eye.i18n.I18n.text("（默认）  ›"):"  ›"));
        }
        private JPanel absolutePanel(){
            JPanel p=Theme.panel(new GridLayout(1,2,14,0));Theme.field(date);date.setFont(Theme.font(12));date.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("提醒日期"));
            JPanel dateRow=Theme.panel(new BorderLayout(6,0));dateRow.add(date);JButton pick=SettingsUi.iconButton("calendar",com.eye.i18n.I18n.text("选择提醒日期"),"secondary");pick.addActionListener(e->showCalendar(pick,date));dateRow.add(pick,BorderLayout.EAST);
            p.add(labeled(com.eye.i18n.I18n.text("日期"),dateRow));p.add(labeled(com.eye.i18n.I18n.text("时刻"),timeButton(time)));return p;
        }
        private JPanel relativePanel(){relative.setMode(inputMode());SettingsUi.valueButton(relativeButton,"timer");relativeButton.addActionListener(e->{editDuration(relative,relativeButton,com.eye.i18n.I18n.text("多久以后提醒"),com.eye.i18n.I18n.text("从保存提醒的时刻开始计算。") );relativeButton.setText(fmtDuration(relative.getSeconds())+com.eye.i18n.I18n.text("后  ›"));});return SettingsUi.row(com.eye.i18n.I18n.text("从现在起"), com.eye.i18n.I18n.text("例如 2 小时后喝水。"),relativeButton);}
        private JPanel intervalPanel(){
            interval.setMode(inputMode());SettingsUi.valueButton(intervalButton,"timer");
            intervalButton.addActionListener(e->{editDuration(interval,intervalButton,com.eye.i18n.I18n.text("提醒间隔"),com.eye.i18n.I18n.text("首次从保存时开始计时，之后按固定间隔循环。"));intervalButton.setText(fmtDuration(interval.getSeconds())+"  ›");});
            return SettingsUi.row(com.eye.i18n.I18n.text("每隔一段时间"),com.eye.i18n.I18n.text("例如每隔 50 分钟喝水一次；停用后停止循环。"),intervalButton);
        }
        private JComponent dateRuleRow(JComboBox<com.eye.calendar.DayCategory> rule) {
            JPanel row = Theme.panel(new BorderLayout(0, 4));
            row.add(SettingsUi.row(com.eye.i18n.I18n.text("适用日期"), "", rule), BorderLayout.NORTH);
            SettingsUi.Text note = new SettingsUi.Text(com.eye.calendar.ChineseCalendar.policyNote(), 10.5f, Theme.SECONDARY);
            row.add(note, BorderLayout.SOUTH);
            Runnable update = () -> note.setVisible(rule.getSelectedItem() != com.eye.calendar.DayCategory.EVERY_DAY);
            rule.addActionListener(e -> update.run());
            update.run();
            return row;
        }
        private JPanel dailyPanel(){return SettingsUi.group(com.eye.i18n.I18n.text("日期与时刻"),
            dateRuleRow(dailyCategory),
            SettingsUi.row(com.eye.i18n.I18n.text("提醒时刻"),"",timeButton(dailyTime)));}
        private JPanel weeklyPanel(){
            JPanel p=Theme.panel(new BorderLayout(0,10));JPanel weekday=Theme.panel(new GridLayout(1,7,6,0));String[] names={com.eye.i18n.I18n.text("周一"),com.eye.i18n.I18n.text("周二"),com.eye.i18n.I18n.text("周三"),com.eye.i18n.I18n.text("周四"),com.eye.i18n.I18n.text("周五"),com.eye.i18n.I18n.text("周六"),com.eye.i18n.I18n.text("周日")};
            for(int i=0;i<7;i++){days[i]=new JCheckBox(names[i]);JToggleButton b=new JToggleButton(names[i]);b.setModel(days[i].getModel());Theme.smallButton(b,"secondary",null);b.setBorder(new EmptyBorder(7,3,7,3));b.setToolTipText(com.eye.i18n.I18n.text("每")+names[i]);weekday.add(b);}
            days[0].putClientProperty("eyerelax.validationAnchor",weekday);
            p.add(weekday,BorderLayout.NORTH);p.add(dateRuleRow(weeklyCategory),BorderLayout.SOUTH);p.add(SettingsUi.row(com.eye.i18n.I18n.text("提醒时刻"),"",timeButton(weeklyTime)));return p;
        }
        private JPanel labeled(String label,JComponent input){JPanel p=Theme.panel(new BorderLayout(0,6));JLabel l=Theme.label(label,11,Theme.SECONDARY);l.setLabelFor(input);p.add(l,BorderLayout.NORTH);p.add(input);return p;}
        private JButton timeButton(JTextField model){
            JButton b=new JButton();model.putClientProperty("eyerelax.validationAnchor",b);SettingsUi.valueButton(b,"clock");model.getDocument().addDocumentListener(changes(()->b.setText(model.getText()+"  ›")));
            b.addActionListener(e->{
                LocalTime current;try{current=parseTime(model.getText());}catch(IllegalArgumentException ex){current=LocalTime.of(9,0);}
                DurationInput input=DurationInput.forTimeOfDay(true);input.setMode(inputMode());input.setSeconds(current.toSecondOfDay());
                JPanel body=Theme.panel(new BorderLayout());body.add(input,BorderLayout.NORTH);
                if(SettingsUi.confirm(b,com.eye.i18n.I18n.text("提醒时刻"),com.eye.i18n.I18n.text("24 小时制"),body,com.eye.i18n.I18n.text("确定"),()->InlineErrors.number(input,input::getTimeOfDaySeconds,0,86399,com.eye.i18n.I18n.text("请填写有效的时间。"))))model.setText(LocalTime.ofSecondOfDay(input.getTimeOfDaySeconds()).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            });return b;
        }
        private void editDuration(DurationInput model,JButton button,String title,String hint){
            DurationInput input=new DurationInput();input.setMode(inputMode());input.setSeconds(model.getSeconds());JPanel body=Theme.panel(new BorderLayout());body.add(input,BorderLayout.NORTH);
            if(SettingsUi.confirm(button,title,hint,body,com.eye.i18n.I18n.text("确定"),()->InlineErrors.number(input,input::getSeconds,model==mask?5:1,Integer.MAX_VALUE,model==mask?com.eye.i18n.I18n.text("遮罩时间不能少于 5 秒。"):com.eye.i18n.I18n.text("时长必须大于 0 秒。")))){
                try{model.setSeconds(input.getSeconds());button.setText(fmtDuration(model.getSeconds())+"  ›");}
                catch(IllegalArgumentException ex){JOptionPane.showMessageDialog(ReminderPanel.this,com.eye.i18n.I18n.text("时长必须大于 0 秒。"),title,JOptionPane.WARNING_MESSAGE);}
            }
        }
        private void init(ReminderItem item){
            ZonedDateTime now=ZonedDateTime.now(); ZonedDateTime next=item.nextTriggerEpochMs>0?Instant.ofEpochMilli(item.nextTriggerEpochMs).atZone(ZoneId.systemDefault()):now.plusHours(1);
            date.setText(next.toLocalDate().format(DATE));time.setText(next.toLocalTime().withSecond(0).withNano(0).toString());dailyTime.setText((item.time==null?next.toLocalTime():item.time).withSecond(0).withNano(0).toString());weeklyTime.setText(dailyTime.getText());
            interval.setSeconds(item.intervalSeconds);intervalButton.setText(fmtDuration(interval.getSeconds())+"  ›");sequence.setDelays(item.sequenceSeconds);
            relative.setSeconds((int)Math.max(60,Math.min(Integer.MAX_VALUE,(next.toInstant().toEpochMilli()-System.currentTimeMillis())/1000)));
            int idx=switch(item.kind){case ONCE->0;case RELATIVE->1;case DAILY->2;case WEEKLY->3;case INTERVAL->4;case SEQUENCE->5;};kind.setSelectedIndex(idx);scheduleCards.show(scheduleHost,""+idx);
            dailyCategory.setSelectedItem(item.dayCategory);weeklyCategory.setSelectedItem(item.dayCategory);
            if(item.weekdays!=null)for(int i=0;i<7;i++)days[i].setSelected(item.weekdays.contains(DayOfWeek.of(i+1)));
            useDefault.setSelected(item.maskSeconds<=0); mask.setSeconds(item.maskSeconds>0?item.maskSeconds:config.reminderDefaultMaskSeconds); mask.setEnabled(!useDefault.isSelected()); resetWork.setSelected(item.resetWorkTimer);
        }
        ReminderItem prepared;
        boolean validate(){
            boolean valid=true;if(title.getText().strip().isBlank()){InlineErrors.show(title,com.eye.i18n.I18n.text("请填写提醒内容。"));valid=false;}
            if(!useDefault.isSelected())valid=InlineErrors.number(maskButton,mask::getSeconds,5,Integer.MAX_VALUE,com.eye.i18n.I18n.text("遮罩时长不能少于 5 秒。"))&&valid;
            int k=kind.getSelectedIndex();
            if(k==0){
                try{parseDate(date.getText());}catch(IllegalArgumentException ex){InlineErrors.show(date,ex.getMessage());valid=false;}
                try{parseTime(time.getText());}catch(IllegalArgumentException ex){InlineErrors.show(time,ex.getMessage());valid=false;}
                if(valid&&ZonedDateTime.of(parseDate(date.getText()),parseTime(time.getText()),ZoneId.systemDefault()).toInstant().toEpochMilli()<=System.currentTimeMillis()){InlineErrors.show(time,com.eye.i18n.I18n.text("指定时间必须晚于现在。"));valid=false;}
            }else if(k==1)valid=InlineErrors.number(relativeButton,relative::getSeconds,1,Integer.MAX_VALUE,com.eye.i18n.I18n.text("请填写大于 0 秒的时长。"))&&valid;
            else if(k==4)valid=InlineErrors.number(intervalButton,interval::getSeconds,1,Integer.MAX_VALUE,com.eye.i18n.I18n.text("请填写大于 0 秒的时长。"))&&valid;
            else if(k==5){if(sequence.delays().isEmpty()||sequence.delays().stream().anyMatch(x->x<=0)){InlineErrors.show(sequence,com.eye.i18n.I18n.text("请至少添加一个有效提醒间隔。"));valid=false;}}
            else{JTextField field=k==2?dailyTime:weeklyTime;try{parseTime(field.getText());}catch(IllegalArgumentException ex){InlineErrors.show(field,ex.getMessage());valid=false;}
                if(k==3&&java.util.Arrays.stream(days).noneMatch(JCheckBox::isSelected)){InlineErrors.show(days[0],com.eye.i18n.I18n.text("每周提醒至少选择一天。"));valid=false;}}
            if(valid){try{prepared=read(reminderId);}catch(IllegalArgumentException ex){InlineErrors.show(k==2?dailyCategory:k==3?weeklyCategory:time,ex.getMessage());valid=false;}}
            return valid;
        }
        ReminderItem read(String id){
            String text=title.getText().strip();if(text.isBlank())throw new IllegalArgumentException(com.eye.i18n.I18n.text("请填写提醒内容。"));
            ReminderItem out=new ReminderItem();out.id=id;out.title=text;ReminderItem previous=manager.get(id);out.pinned=previous!=null&&previous.pinned;out.enabled=true;out.resetWorkTimer=resetWork.isSelected();out.maskSeconds=useDefault.isSelected()?0:mask.getSeconds();if(out.maskSeconds>0&&out.maskSeconds<5)throw new IllegalArgumentException(com.eye.i18n.I18n.text("单个提醒的遮罩时间不能少于 5 秒。"));
            long now=System.currentTimeMillis();ZoneId zone=ZoneId.systemDefault();int k=kind.getSelectedIndex();
            if(k==0){LocalDate d=parseDate(date.getText());LocalTime t=parseTime(time.getText());out.kind=ReminderItem.ScheduleKind.ONCE;out.time=t;out.nextTriggerEpochMs=ZonedDateTime.of(d,t,zone).toInstant().toEpochMilli();if(out.nextTriggerEpochMs<=now)throw new IllegalArgumentException(com.eye.i18n.I18n.text("指定时间必须是现在或未来的时间。"));}
            else if(k==1){int sec=relative.getSeconds();out.kind=ReminderItem.ScheduleKind.RELATIVE;out.relativeDelaySeconds=sec;out.nextTriggerEpochMs=now+sec*1000L;out.time=Instant.ofEpochMilli(out.nextTriggerEpochMs).atZone(zone).toLocalTime();}
            else if(k==2){out.kind=ReminderItem.ScheduleKind.DAILY;out.dayCategory=(com.eye.calendar.DayCategory)dailyCategory.getSelectedItem();out.time=parseTime(dailyTime.getText());out.nextTriggerEpochMs=now;out.advanceAfter(now);if(!out.enabled)throw new IllegalArgumentException(com.eye.i18n.I18n.text("没有匹配的已公布日期，请更换日期规则。"));}
            else if(k==4){out.kind=ReminderItem.ScheduleKind.INTERVAL;out.intervalSeconds=interval.getSeconds();out.nextTriggerEpochMs=now+out.intervalSeconds*1000L;}
            else if(k==5){out.kind=ReminderItem.ScheduleKind.SEQUENCE;out.sequenceSeconds=sequence.delays();out.sequenceIndex=0;out.nextTriggerEpochMs=now+out.sequenceSeconds.get(0)*1000L;}
            else {out.kind=ReminderItem.ScheduleKind.WEEKLY;out.dayCategory=(com.eye.calendar.DayCategory)weeklyCategory.getSelectedItem();out.time=parseTime(weeklyTime.getText());for(int i=0;i<7;i++)if(days[i].isSelected())out.weekdays.add(DayOfWeek.of(i+1));if(out.weekdays.isEmpty())throw new IllegalArgumentException(com.eye.i18n.I18n.text("每周提醒至少选择一天。"));out.nextTriggerEpochMs=now;out.advanceAfter(now);if(!out.enabled)throw new IllegalArgumentException(com.eye.i18n.I18n.text("没有匹配的已公布日期，请更换日期规则。"));}
            if(previous!=null){out.cloudId=previous.cloudId;out.cloudMaxTimes=previous.cloudMaxTimes;}out.cloudStart=out.nextTriggerEpochMs;
            return out;
        }
    }


    private static DocumentListener changes(Runnable action){return new DocumentListener(){public void insertUpdate(DocumentEvent e){action.run();}public void removeUpdate(DocumentEvent e){action.run();}public void changedUpdate(DocumentEvent e){action.run();}};}

    /** Date-only popover. The existing yyyy-MM-dd model and validation are unchanged. */
    private void showCalendar(JButton anchor,JTextField field){
        LocalDate selected;try{selected=parseDate(field.getText());}catch(IllegalArgumentException ex){selected=LocalDate.now();}
        LocalDate initial=selected;YearMonth[] month={YearMonth.from(selected)};
        JPopupMenu popup=new JPopupMenu();popup.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE),new EmptyBorder(12,12,12,12)));
        JPanel content=Theme.panel(new BorderLayout(0,12));JPanel header=Theme.panel(new BorderLayout());
        JButton prev=SettingsUi.iconButton("prev",com.eye.i18n.I18n.text("上个月"),"ghost"),next=SettingsUi.iconButton("next",com.eye.i18n.I18n.text("下个月"),"ghost");JLabel caption=Theme.heading("",13);caption.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(prev,BorderLayout.WEST);header.add(caption);header.add(next,BorderLayout.EAST);content.add(header,BorderLayout.NORTH);
        JPanel dates=Theme.panel(new GridLayout(7,7,3,3));dates.setPreferredSize(new Dimension(420,352));content.add(dates);
        Runnable rebuild=()->{
            caption.setText(month[0].getYear()+com.eye.i18n.I18n.text(" 年 ")+month[0].getMonthValue()+com.eye.i18n.I18n.text(" 月"));dates.removeAll();
            for(String name:new String[]{com.eye.i18n.I18n.text("一"),com.eye.i18n.I18n.text("二"),com.eye.i18n.I18n.text("三"),com.eye.i18n.I18n.text("四"),com.eye.i18n.I18n.text("五"),com.eye.i18n.I18n.text("六"),com.eye.i18n.I18n.text("日")}){JLabel label=Theme.label(name,10,Theme.SECONDARY);label.setHorizontalAlignment(SwingConstants.CENTER);dates.add(label);}
            LocalDate first=month[0].atDay(1);int offset=first.getDayOfWeek().getValue()-1;
            for(int i=0;i<42;i++){int day=i-offset+1;if(day<1||day>month[0].lengthOfMonth()){dates.add(new JLabel());continue;}
                LocalDate value=month[0].atDay(day);com.eye.calendar.CalendarDayButton b=new com.eye.calendar.CalendarDayButton();b.setDate(value);b.getModel().setSelected(value.equals(initial));b.putClientProperty("rest.today",value.equals(LocalDate.now()));
                b.addActionListener(e->{field.setText(value.format(DATE));popup.setVisible(false);});dates.add(b);
            }dates.revalidate();dates.repaint();
        };
        prev.addActionListener(e->{month[0]=month[0].minusMonths(1);rebuild.run();});next.addActionListener(e->{month[0]=month[0].plusMonths(1);rebuild.run();});rebuild.run();popup.add(content);popup.show(anchor,0,anchor.getHeight()+4);
    }

    private static LocalDate parseDate(String v){try{return LocalDate.parse(v.strip(),DATE);}catch(Exception e){throw new IllegalArgumentException(com.eye.i18n.I18n.text("日期格式应为 yyyy-MM-dd。"));}}
    private static LocalTime parseTime(String v){try{return LocalTime.parse(v.strip(),TIME);}catch(Exception e){throw new IllegalArgumentException(com.eye.i18n.I18n.text("时间格式应为 HH:mm 或 HH:mm:ss。"));}}
    private static String formatEpoch(long ms){return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(WHEN);}
    private static String fmtDuration(int sec){if(com.eye.i18n.I18n.isEnglish())return com.eye.stats.Statistics.duration(Math.max(0,sec)*1000L);int s=Math.max(0,sec);if(s%3600==0)return(s/3600)+com.eye.i18n.I18n.text(" 小时");if(s%60==0)return(s/60)+com.eye.i18n.I18n.text(" 分钟");if(s>=60)return(s/60)+com.eye.i18n.I18n.text(" 分 ")+(s%60)+com.eye.i18n.I18n.text(" 秒");return s+com.eye.i18n.I18n.text(" 秒");}
    private static String scheduleDescription(ReminderItem item){return switch(item.kind){case ONCE->com.eye.i18n.I18n.text("单次");case RELATIVE->com.eye.i18n.I18n.text("相对时间 · 单次");case DAILY->item.dayCategory+" "+item.time.withSecond(0);case WEEKLY->com.eye.i18n.I18n.text("每周 ")+formatDays(item.weekdays)+" · "+item.dayCategory+" "+item.time.withSecond(0);case INTERVAL->com.eye.i18n.I18n.text("每隔 ")+fmtDuration(item.intervalSeconds);case SEQUENCE->com.eye.i18n.I18n.text("序列 ")+Math.min(item.sequenceIndex+1,item.sequenceSeconds.size())+" / "+item.sequenceSeconds.size();};}
    private static String formatDays(Set<DayOfWeek> days){String[] n={com.eye.i18n.I18n.text("一"),com.eye.i18n.I18n.text("二"),com.eye.i18n.I18n.text("三"),com.eye.i18n.I18n.text("四"),com.eye.i18n.I18n.text("五"),com.eye.i18n.I18n.text("六"),com.eye.i18n.I18n.text("日")};StringJoiner j=new StringJoiner("/");for(int i=0;i<7;i++)if(days.contains(DayOfWeek.of(i+1)))j.add(n[i]);return j.toString();}
}
