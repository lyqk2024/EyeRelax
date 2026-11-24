package com.eye.plan;
import com.eye.plan.PlanDatePickerDialogs.*;

import com.eye.ui.theme.InlineErrors;
import com.eye.ui.theme.SettingsUi;

import com.eye.core.Config;
import com.eye.ui.theme.DurationInput;
import com.eye.ui.theme.Theme;
import com.eye.ui.theme.Icons;
import javax.swing.border.EmptyBorder;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plan management content embedded directly in the main EyeRelax window. */
public class PlanWindow extends JPanel {

    public interface Actions {
        String getCurrentPlanId();
        void onSelectCurrentPlan(String planId);
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_HEADING_FMT = DateTimeFormatter.ofPattern("yyyy.M.d");

    private final PlanManager manager;
    private final Actions actions;
    private final Config config;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    private final JPanel dailyPanel = createListPanel();
    private final JPanel otherPanel = createListPanel();
    private final JPanel scheduledPanel = createListPanel();
    private final JPanel historyPanel = createListPanel();
    private final JScrollPane historyScrollPane = new JScrollPane(historyPanel);

    private JButton todayBtn;
    private JButton scheduledBtn;
    private JButton historyBtn;
    private JButton activityBtn;
    private JButton libraryBtn;
    private JButton tutorialAddBtn;
    private long tutorialPlanEditorClosed;
    public long tutorialPlanEditorClosedCount(){return tutorialPlanEditorClosed;}
    public JComponent tutorialTarget(String key){return switch(key){case "today"->todayBtn;case "scheduled"->scheduledBtn;case "history"->historyBtn;case "library"->libraryBtn;case "archive"->libraryPanel.tutorialTarget(false);case "templates"->libraryPanel.tutorialTarget(true);case "library-actions"->libraryPanel.tutorialActions();case "day","day-export","trend","task","heatmap","insights-summary","insights-range","insights-export"->activityPanel.tutorialTarget(key);case "add"->tutorialAddBtn;case "more"->{JComponent target=findTutorialMarked(this,"tutorial.more");yield target==null?tutorialAddBtn:target;}case "statistics"->activityBtn;case "select"->findTutorialSelection(this);default->null;};}
    public void prepareTutorialTarget(String key){if("archive".equals(key)||"templates".equals(key)||"library-actions".equals(key))libraryPanel.tutorialCategory(!"archive".equals(key));else activityPanel.prepareTutorialTarget(key);}
    private static JComponent findTutorialMarked(Container parent,String property){for(Component child:parent.getComponents()){if(child instanceof JComponent j&&Boolean.TRUE.equals(j.getClientProperty(property))&&j.isShowing())return j;if(child instanceof Container c){JComponent found=findTutorialMarked(c,property);if(found!=null)return found;}}return null;}
    public boolean tutorialStatisticsSelected(){return Boolean.TRUE.equals(activityBtn.getClientProperty("rest.selected"));}
    private static JComponent findTutorialSelection(Container parent){for(Component child:parent.getComponents()){
        if(child instanceof JButton b&&b.isEnabled()&&Boolean.TRUE.equals(b.getClientProperty("tutorial.select")))return b;
        if(child instanceof Container c){JComponent found=findTutorialSelection(c);if(found!=null&&found.isShowing())return found;}
    }return null;}
    private final PlanLibraryPanel libraryPanel;
    private final ActivityPanel activityPanel;
    private final Set<String> collapsedHistory = new LinkedHashSet<>();
    private boolean rebuildingHistory;

    private final JLabel viewTitle = Theme.heading(com.eye.i18n.I18n.text("今日任务"), 25);
    private final JPanel subtitleHost = Theme.panel(new BorderLayout());
    private final JLabel dateLabel = Theme.label("", 12, Theme.SECONDARY);

    private final JTextField historyFromField = new JTextField();
    private final JTextField historyToField = new JTextField();
    private final JButton historyRangePickerBtn = new JButton(com.eye.i18n.I18n.text("选择日期"));
    private final JComboBox<HistoryTaskChoice> historyTaskBox = new JComboBox<>();
    private final JLabel historyScopeLabel = Theme.label("", 11, Theme.SECONDARY);
    private final JLabel historyFilterMessage = Theme.label("", 11, Theme.DANGER);
    private int historyLoadedDays = 7;
    private boolean historyExplicitDateRange = false;
    private LocalDate historyFromDate;
    private LocalDate historyToDate;
    private boolean historyNoMore = false;
    private boolean historyLoadingMore = false;
    private boolean updatingHistoryTaskChoices = false;
    private List<PlanManager.HistoricalRecord> historyCache = List.of();
    private boolean historyCacheReady;
    private LocalDate historyCacheDay;
    private int historyRestorePosition = -1;
    private long historyCacheRevision;
    private HistoryViewState historyViewState;
    private record HistoryViewState(long revision, LocalDate today, String planId,
            boolean explicitRange, LocalDate from, LocalDate to, Set<String> collapsed) {}
    private Component historyEmptyState;
    private SwingWorker<List<PlanManager.HistoricalRecord>, Void> historyReader;
    private final java.util.ArrayDeque<java.util.function.Supplier<Component>> historyRenderQueue = new java.util.ArrayDeque<>();
    private final Set<String> historyRenderedGroups = new LinkedHashSet<>();
    private final Set<String> historyRenderedRecords = new LinkedHashSet<>();
    private javax.swing.Timer historyRenderTimer;
    private String visibleCard = "today";
    public String tutorialCurrentCard(){return visibleCard;}
    private boolean clickAwayFocusListenerInstalled = false;
    private final java.awt.event.AWTEventListener clickAwayFocusListener = event -> {
        if (!(event instanceof java.awt.event.MouseEvent mouse)
                || mouse.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED
                || !isShowing()) return;
        Object source = mouse.getSource();
        if (!(source instanceof Component clicked)
                || (clicked != this && !SwingUtilities.isDescendingFrom(clicked, this))) return;

        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        JComponent input = focusedInputAncestor(focus);
        if (input == null || clicked == input || SwingUtilities.isDescendingFrom(clicked, input)) return;

        // Swing keeps focus on a JComboBox/JTextField when a non-focusable background is clicked.
        // Defer until the click has had a chance to transfer focus naturally; only clear the old
        // input if it still owns focus afterwards. Popup-list clicks are outside this panel and
        // therefore deliberately do not blur the combo immediately after a selection.
        SwingUtilities.invokeLater(() -> {
            Component after = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (after != null && (after == input || SwingUtilities.isDescendingFrom(after, input))) {
                PlanWindow.this.requestFocusInWindow();
                input.repaint();
            }
        });
    };

    public PlanWindow(Window ignoredOwner, PlanManager manager, Actions actions) {
        this(manager, actions, null);
    }

    public PlanWindow(PlanManager manager, Actions actions) {
        this(manager, actions, null);
    }

    public PlanWindow(PlanManager manager, Actions actions, Config config) {
        this.manager = manager;
        this.actions = actions;
        this.config = config;
        this.activityPanel = new ActivityPanel(manager, config);
        this.libraryPanel=new PlanLibraryPanel(manager,config,this::refresh);
        setOpaque(false);
        setLayout(new BorderLayout());

        JButton addBtn = new JButton(com.eye.i18n.I18n.text("新增计划"));
        tutorialAddBtn=addBtn;
        addBtn.addActionListener(e -> {addPlan();tutorialPlanEditorClosed++;});
        Theme.button(addBtn, "primary", "plus");

        todayBtn = new JButton(com.eye.i18n.I18n.text("今日任务"));
        scheduledBtn = new JButton(com.eye.i18n.I18n.text("预定任务"));
        historyBtn = new JButton(com.eye.i18n.I18n.text("过去计划"));
        activityBtn = new JButton(com.eye.i18n.I18n.text("专注统计"));
        activityBtn.addActionListener(e -> showCard("activity"));
        Theme.navigation(activityBtn, "grid");
        todayBtn.addActionListener(e -> showCard("today"));
        scheduledBtn.addActionListener(e -> showCard("scheduled"));
        historyBtn.addActionListener(e -> showCard("history"));
        Theme.navigation(todayBtn, "sun");
        Theme.navigation(scheduledBtn, "calendar");
        Theme.navigation(historyBtn, "history");

        JPanel header = Theme.panel(new BorderLayout(20, 0));
        JPanel words = Theme.panel(new BorderLayout(0, 7));
        words.add(viewTitle, BorderLayout.CENTER);
        words.add(subtitleHost, BorderLayout.SOUTH);
        header.add(words, BorderLayout.CENTER);

        JPanel right = Theme.column();
        dateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        dateLabel.setIcon(Icons.of("calendar", 15, Theme.SECONDARY));
        dateLabel.setIconTextGap(7);
        addBtn.setAlignmentX(Component.RIGHT_ALIGNMENT);
        libraryBtn=new JButton(com.eye.i18n.I18n.text("归档与模板"));Theme.button(libraryBtn,"secondary","folder");libraryBtn.addActionListener(e->showCard("library"));
        JPanel managementActions=Theme.panel(new FlowLayout(FlowLayout.RIGHT,8,0));managementActions.setAlignmentX(Component.RIGHT_ALIGNMENT);
        managementActions.add(libraryBtn);managementActions.add(addBtn);
        right.add(dateLabel);
        right.add(Box.createVerticalStrut(13));
        right.add(managementActions);
        header.add(right, BorderLayout.EAST);

        JPanel tabs = new Theme.Surface(new FlowLayout(FlowLayout.LEFT, 3, 0), Theme.GROUP, Theme.LINE, 16);
        tabs.setBorder(new EmptyBorder(4, 4, 4, 4));
        tabs.add(todayBtn);
        tabs.add(scheduledBtn);
        tabs.add(historyBtn);
        tabs.add(activityBtn);
        JButton[] tabButtons = {todayBtn, scheduledBtn, historyBtn, activityBtn};
        String[] tabIcons = {"sun", "calendar", "history", "grid"};
        for (int index = 0; index < tabButtons.length; index++) {
            JButton tab = tabButtons[index];
            Theme.smallButton(tab, "segment", tabIcons[index]);
            tab.setBorder(new EmptyBorder(10, 13, 10, 13));
        }
        JPanel tabBar = Theme.panel(new BorderLayout());
        tabBar.add(tabs, BorderLayout.WEST);

        JPanel top = Theme.panel(new BorderLayout(0, 19));
        top.add(header, BorderLayout.NORTH);
        top.add(tabBar, BorderLayout.SOUTH);

        cardPanel.setOpaque(false);
        cardPanel.add(buildTodayView(), "today");
        cardPanel.add(wrapPanel(scheduledPanel), "scheduled");
        cardPanel.add(buildHistoryView(), "history");
        cardPanel.add(activityPanel, "activity");
        cardPanel.add(libraryPanel,"library");

        JPanel body = Theme.panel(new BorderLayout(0, 20));
        body.setBorder(new EmptyBorder(28, 28, 20, 28));
        body.add(top, BorderLayout.NORTH);
        body.add(cardPanel, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        Theme.scroll(historyScrollPane);
        historyScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            maybeLoadMoreHistory();
        });
        historyScrollPane.addMouseWheelListener(e -> {
            if (rebuildingHistory || e.getWheelRotation() <= 0 || !"history".equals(visibleCard)
                    || historyExplicitDateRange || historyNoMore || historyLoadingMore) return;
            JScrollBar bar = historyScrollPane.getVerticalScrollBar();
            // When a short seven-day result fits entirely in the viewport there is no scrollbar
            // movement to observe. A deliberate downward wheel at the end still means
            // “show me older history”, so expand the lazy window in that case too.
            if (bar.getMaximum() <= bar.getVisibleAmount()) expandHistoryWindow();
        });

        setFocusable(true);
        refresh();
        showCard("today");
    }

    @Override public void addNotify() {
        super.addNotify();
        if (!clickAwayFocusListenerInstalled) {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                    clickAwayFocusListener, AWTEvent.MOUSE_EVENT_MASK);
            clickAwayFocusListenerInstalled = true;
        }
    }

    @Override public void removeNotify() {
        if (historyRenderTimer != null) historyRenderTimer.stop();
        if (clickAwayFocusListenerInstalled) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(clickAwayFocusListener);
            clickAwayFocusListenerInstalled = false;
        }
        super.removeNotify();
    }

    private JComponent focusedInputAncestor(Component component) {
        for (Component cursor = component; cursor != null; cursor = cursor.getParent()) {
            if (cursor instanceof JComboBox<?> || cursor instanceof JTextField) return (JComponent) cursor;
            if (cursor == this) break;
        }
        return null;
    }

    public void refresh() {
        applyHistoryInputMode();
        manager.loadToday();
        dateLabel.setText(manager.getToday().format(DATE_FMT));
        refreshTodayView();
        refreshScheduledView();
        refreshHistoryTaskChoices();
        if ("history".equals(visibleCard)) refreshHistoryView();
        if ("activity".equals(visibleCard)) activityPanel.refresh();
        if("library".equals(visibleCard))libraryPanel.refresh();
        revalidate();
        repaint();
    }

    private java.util.function.Consumer<String> tutorialPageListener=card->{};
    public void setTutorialPageListener(java.util.function.Consumer<String> listener){tutorialPageListener=listener;}
    public void showCard(String card) {
        tutorialPageListener.accept(card);
        visibleCard = card;
        manager.loadToday();
        if ("today".equals(card)) refreshTodayView();
        if ("scheduled".equals(card)) refreshScheduledView();
        if ("history".equals(card)) {
            refreshHistoryTaskChoices();
            refreshHistoryView();
        }
        applyHistoryInputMode();
        if ("activity".equals(card)) activityPanel.refresh();
        if("library".equals(card))libraryPanel.refresh();libraryBtn.putClientProperty("rest.selected","library".equals(card));libraryBtn.repaint();
        cardLayout.show(cardPanel, card);
        todayBtn.putClientProperty("rest.selected", "today".equals(card));
        scheduledBtn.putClientProperty("rest.selected", "scheduled".equals(card));
        historyBtn.putClientProperty("rest.selected", "history".equals(card));
        activityBtn.putClientProperty("rest.selected", "activity".equals(card));
        activityBtn.repaint();
        viewTitle.setText(switch(card) { case "library"->com.eye.i18n.I18n.text("归档与模板");case "scheduled" -> com.eye.i18n.I18n.text("预定任务"); case "history" -> com.eye.i18n.I18n.text("过去计划"); case "activity" -> com.eye.i18n.I18n.text("专注统计"); default -> com.eye.i18n.I18n.text("今日任务"); });
        String subtitle = switch(card) {
            case "library" -> com.eye.i18n.I18n.text("使用模板可新建计划，归档计划保留历史记录。");
            case "scheduled" -> com.eye.i18n.I18n.text("按日期查看未来一个月的预定任务。");
            case "activity" -> com.eye.i18n.I18n.text("回顾每日专注与休息，查看长期计划的投入趋势。");
            case "history" -> com.eye.i18n.I18n.text("按日期和任务查找过去的计划及完成情况。");
            default -> com.eye.i18n.I18n.text("查看今天要做的任务、已投入的时间和剩余时长。");
        };
        subtitleHost.removeAll();
        subtitleHost.add(new SettingsUi.Text(subtitle, 12, Theme.SECONDARY));
        subtitleHost.revalidate();
        subtitleHost.repaint();
        todayBtn.repaint();
        scheduledBtn.repaint();
        historyBtn.repaint();
    }

    private JComponent buildTodayView() {
        JPanel root = createListPanel();
        root.add(createSection(com.eye.i18n.I18n.text("每日任务"), dailyPanel));
        root.add(Box.createVerticalStrut(22));
        root.add(createSection(com.eye.i18n.I18n.text("其它当天任务"), otherPanel));
        return wrapPanel(root);
    }

    private JComponent buildHistoryView() {
        JPanel root = Theme.panel(new BorderLayout(0, 14));
        root.add(buildHistoryFilters(), BorderLayout.NORTH);
        root.add(historyScrollPane, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildHistoryFilters() {
        Theme.field(historyFromField); Theme.field(historyToField);
        for (JTextField field : new JTextField[]{historyFromField, historyToField}) {
            field.setFont(Theme.font(13)); field.setColumns(0);
            field.setPreferredSize(new Dimension(118, 38)); field.setMinimumSize(new Dimension(114, 38));
            field.setMaximumSize(new Dimension(118, 38));
            field.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (config == null || config.historyDateInput == Config.HistoryDateInput.CALENDAR) pickHistoryDateRange();
                }
            });
        }
        Theme.smallButton(historyRangePickerBtn, "secondary", "calendar");
        historyRangePickerBtn.addActionListener(e -> pickHistoryDateRange());
        historyTaskBox.setPreferredSize(new Dimension(150, 36));
        historyTaskBox.setMaximumSize(new Dimension(158, 36));
        historyTaskBox.addActionListener(e -> {
            if (!updatingHistoryTaskChoices && "history".equals(visibleCard)) refreshHistoryView();
        });
        JButton applyBtn = new JButton(com.eye.i18n.I18n.text("筛选"));
        Theme.smallButton(applyBtn, "primary", "search");
        applyBtn.addActionListener(e -> applyHistoryFilters());
        Theme.Surface filters = Theme.card(new BorderLayout(0, 11));
        filters.setBorder(new EmptyBorder(15, 16, 13, 16));
        // Keep every filter on one compact line. The default view is already the latest seven days,
        // so a separate “最近 7 天” control only duplicated the default state.
        JPanel controls = Theme.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(Theme.label(com.eye.i18n.I18n.text("从"), 11, Theme.SECONDARY)); controls.add(historyFromField);
        controls.add(Theme.label(com.eye.i18n.I18n.text("到"), 11, Theme.SECONDARY)); controls.add(historyToField);
        controls.add(historyRangePickerBtn);
        controls.add(Box.createHorizontalStrut(4));
        controls.add(Theme.label(com.eye.i18n.I18n.text("任务"), 11, Theme.SECONDARY)); controls.add(historyTaskBox); controls.add(applyBtn);
        filters.add(controls, BorderLayout.CENTER);
        JPanel status = Theme.panel(new BorderLayout(12, 0));
        // FlowLayout reserves one hgap before its first control. Match that inset so the
        // status text starts exactly under the “从” label above instead of drifting left.
        status.setBorder(new EmptyBorder(0, 8, 0, 0));
        status.add(historyScopeLabel, BorderLayout.WEST); historyFilterMessage.setHorizontalAlignment(SwingConstants.RIGHT);
        status.add(historyFilterMessage, BorderLayout.EAST); filters.add(status, BorderLayout.SOUTH);
        historyFromField.setText(manager.getToday().minusDays(7).format(DATE_FMT));
        historyToField.setText(manager.getToday().minusDays(1).format(DATE_FMT));
        applyHistoryInputMode(); return filters;
    }

    private void applyHistoryInputMode() {
        boolean manual = config != null && config.historyDateInput == Config.HistoryDateInput.MANUAL;
        for (JTextField field : new JTextField[]{historyFromField, historyToField}) {
            field.setEditable(manual);
            field.setCursor(Cursor.getPredefinedCursor(manual ? Cursor.TEXT_CURSOR : Cursor.HAND_CURSOR));
            field.setToolTipText(manual ? com.eye.i18n.I18n.text("输入完整日期 yyyy-MM-dd；也可打开日历") : com.eye.i18n.I18n.text("点击打开日历选择日期范围"));
            field.setFont(Theme.font(13));
            // JTextField may recompute its preferred width after font/editable changes; pin the compact date width last.
            field.setPreferredSize(new Dimension(118, 38)); field.setMinimumSize(new Dimension(114, 38)); field.setMaximumSize(new Dimension(118, 38));
            field.setCaretPosition(0);
        }
    }

    private JComponent createSection(String title, JPanel listPanel) {
        JPanel section = new JPanel(new BorderLayout(0, 10)) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel heading = Theme.panel(new BorderLayout(12, 0));
        heading.setBorder(new EmptyBorder(0, 2, 1, 2));
        JLabel label = Theme.heading(title, 14);
        label.setIcon(Icons.of(listPanel == dailyPanel ? "restart" : "grid", 17, Theme.GREEN));
        label.setIconTextGap(8);
        heading.add(label, BorderLayout.WEST);
        JLabel count = Theme.badge(com.eye.i18n.I18n.text("0 项"), Theme.SECONDARY, Theme.MINT);
        heading.add(count, BorderLayout.EAST);
        listPanel.putClientProperty("rest.count", count);
        section.add(heading, BorderLayout.NORTH);
        section.add(listPanel, BorderLayout.CENTER);
        return section;
    }

    private JScrollPane wrapPanel(JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel);
        Theme.scroll(scrollPane);
        return scrollPane;
    }

    private JPanel createListPanel() {
        JPanel panel = new VerticalList();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(0, 0, 3, 5));
        panel.setOpaque(false);
        return panel;
    }

    private static final class VerticalList extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int axis, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int axis, int direction) { return Math.max(18, r.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private void refreshTodayView() {
        refillListPanel(dailyPanel, buildTodayRows(manager.listTodayDailyPlans()));
        refillListPanel(otherPanel, buildTodayRows(manager.listTodayOtherPlans()));
    }

    private void refreshScheduledView() {
        List<JComponent> rows = new ArrayList<>();
        for (PlanManager.ScheduledOccurrence occurrence : manager.listUpcomingScheduled(31)) {
            rows.add(createScheduledRow(occurrence));
        }
        refillListPanel(scheduledPanel, rows, com.eye.i18n.I18n.text("未来一个月内没有非每日的预定计划。"));
    }

    private void refreshHistoryTaskChoices() {
        if (historyReader != null) return;
        if (historyCacheReady && manager.getToday().equals(historyCacheDay)) return;
        // Independent reader: historical disk I/O must not hold the live manager's monitor.
        historyReader = new SwingWorker<>() {
            @Override protected List<PlanManager.HistoricalRecord> doInBackground() {
                return new PlanManager(config).listPastRecords();
            }
            @Override protected void done() {
                historyReader = null;
                if (isCancelled()) return;
                try {
                    historyCache = List.copyOf(get()); historyCacheRevision++; historyCacheReady = true; historyCacheDay=manager.getToday();
                    String selected = selectedHistoryPlanId();
                    updatingHistoryTaskChoices = true;
                    historyTaskBox.removeAllItems();
                    historyTaskBox.addItem(new HistoryTaskChoice(null, com.eye.i18n.I18n.text("全部任务")));
                    Map<String,String> tasks = new LinkedHashMap<>();
                    for (var record : historyCache) tasks.putIfAbsent(record.plan.id, record.plan.name);
                    tasks.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                            .forEach(task -> historyTaskBox.addItem(new HistoryTaskChoice(task.getKey(), task.getValue())));
                    for (int i=0; i<historyTaskBox.getItemCount(); i++) {
                        if (java.util.Objects.equals(selected,historyTaskBox.getItemAt(i).planId)) { historyTaskBox.setSelectedIndex(i); break; }
                    }
                    updatingHistoryTaskChoices = false;
                    if ("history".equals(visibleCard)) refreshHistoryView();
                } catch (Exception ex) {
                    historyFilterMessage.setText(com.eye.i18n.I18n.text("读取历史记录失败，请重新打开过去计划。"));
                } finally { updatingHistoryTaskChoices = false; }
            }
        };
        historyReader.execute();
    }

    private List<PlanManager.HistoricalRecord> filteredHistory() {
        String id = selectedHistoryPlanId();
        return historyCache.stream().filter(r -> id == null || id.equals(r.plan.id))
                .filter(r -> !historyExplicitDateRange || ((historyFromDate==null || !r.date.isBefore(historyFromDate))
                        && (historyToDate==null || !r.date.isAfter(historyToDate)))).toList();
    }

    private void refreshHistoryView() {
        if (!historyCacheReady) return; // Prefetch starts while the user is still on today's plans.
        HistoryViewState state = new HistoryViewState(historyCacheRevision, manager.getToday(),
                selectedHistoryPlanId(), historyExplicitDateRange, historyFromDate, historyToDate,
                Set.copyOf(collapsedHistory));
        // Live timer updates must not clear the history or interrupt an append already in progress.
        if (state.equals(historyViewState)) return;
        historyViewState = state;
        if (historyRenderTimer != null) historyRenderTimer.stop();
        historyRestorePosition=historyScrollPane.getVerticalScrollBar().getValue();
        historyRenderQueue.clear(); historyRenderedGroups.clear(); historyRenderedRecords.clear();
        historyPanel.removeAll(); historyEmptyState=null;
        queueHistoryRows();
    }

    private void queueHistoryRows() {
        rebuildingHistory = true; historyLoadingMore = true;
        LocalDate from = manager.getToday().minusDays(historyLoadedDays);
        List<PlanManager.HistoricalRecord> all = filteredHistory();
        List<PlanManager.HistoricalRecord> records = all.stream()
                .filter(r -> historyExplicitDateRange || !r.date.isBefore(from)).toList();
        if (!records.isEmpty() && historyEmptyState!=null) {historyPanel.remove(historyEmptyState);historyEmptyState=null;}
        historyNoMore = historyExplicitDateRange || all.stream().noneMatch(r -> r.date.isBefore(from));
        Map<LocalDate,List<PlanManager.HistoricalRecord>> days = new LinkedHashMap<>();
        for (var record : records) days.computeIfAbsent(record.date,k -> new ArrayList<>()).add(record);
        Map<Integer,Integer> yearCounts = new LinkedHashMap<>();
        Map<YearMonth,Integer> monthCounts = new LinkedHashMap<>();
        for (var record : all) {
            yearCounts.merge(record.date.getYear(),1,Integer::sum);
            monthCounts.merge(YearMonth.from(record.date),1,Integer::sum);
        }
        for (var entry : days.entrySet()) {
            LocalDate day = entry.getKey(); YearMonth month = YearMonth.from(day);
            String yearKey = "year:"+day.getYear(), monthKey = "month:"+month, dayKey = "day:"+day;
            if (historyRenderedGroups.add(yearKey)) historyRenderQueue.add(() -> createHistoryGroupHeader(yearKey,day.getYear()+com.eye.i18n.I18n.text(" 年"),yearCounts.get(day.getYear()),16));
            if (collapsedHistory.contains(yearKey)) continue;
            if (historyRenderedGroups.add(monthKey)) historyRenderQueue.add(() -> createHistoryGroupHeader(monthKey,month.getMonthValue()+com.eye.i18n.I18n.text(" 月"),monthCounts.get(month),13));
            if (collapsedHistory.contains(monthKey)) continue;
            if (historyRenderedGroups.add(dayKey)) {
                historyRenderQueue.add(() -> { JComponent heading=createHistoryDateSeparator(day,entry.getValue().size());attachCollapse(heading,dayKey);return heading; });
                if (!collapsedHistory.contains(dayKey)) historyRenderQueue.add(() -> Box.createVerticalStrut(9));
            }
            if (collapsedHistory.contains(dayKey)) continue;
            for (var record : entry.getValue()) {
                if (!historyRenderedRecords.add(record.date+"|"+record.plan.id)) continue;
                historyRenderQueue.add(() -> createHistoryRow(record));
                historyRenderQueue.add(() -> Box.createVerticalStrut(10));
            }
        }
        if (records.isEmpty() && historyPanel.getComponentCount()==0) historyRenderQueue.add(() -> {
            JLabel empty=Theme.label(com.eye.i18n.I18n.text("这个范围里还没有历史计划记录。"),13,Theme.SECONDARY);
            empty.setBorder(new EmptyBorder(28,24,28,24));historyEmptyState=empty;return empty;
        });
        historyScopeLabel.setText((historyExplicitDateRange ? com.eye.i18n.I18n.text("筛选范围") : com.eye.i18n.I18n.text("历史记录"))+"  ·  "+all.size()+com.eye.i18n.I18n.text(" 条"));
        // Build only a small number of real Swing rows per frame. Never replace existing rows on scroll.
        historyRenderTimer = new javax.swing.Timer(16,e -> {
            int position = historyScrollPane.getViewport().getViewPosition().y;
            long until=System.nanoTime()+6_000_000L; int count=0;
            do {
                var next=historyRenderQueue.poll(); if(next==null)break;
                historyPanel.add(next.get()); count++;
            } while(count<12 && System.nanoTime()<until);
            historyPanel.revalidate();
            // Layout before anchoring: deferred scrollbar extent changes must not move the viewport.
            historyScrollPane.validate();
            historyScrollPane.getViewport().doLayout();
            if (historyRestorePosition < 0) {
                Point anchor = historyScrollPane.getViewport().getViewPosition();
                anchor.y = Math.min(position, Math.max(0, historyPanel.getHeight()
                        - historyScrollPane.getViewport().getExtentSize().height));
                historyScrollPane.getViewport().setViewPosition(anchor);
            }
            historyPanel.repaint();
            if(historyRenderQueue.isEmpty()) {
                ((javax.swing.Timer)e.getSource()).stop();
                rebuildingHistory=false; historyLoadingMore=false;
                if(historyRestorePosition>=0){historyScrollPane.getVerticalScrollBar().setValue(historyRestorePosition);historyRestorePosition=-1;}
                SwingUtilities.invokeLater(this::maybeLoadMoreHistory);
            }
        });
        historyRenderTimer.start();
    }

    private JComponent createHistoryGroupHeader(String key, String title, int count, int size) {
        JPanel row = Theme.panel(new BorderLayout(12, 0));
        row.setBorder(new EmptyBorder(10, 2, 8, 4));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(500, 43)); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        JLabel name = Theme.heading(title, size);
        // Draw the disclosure arrow instead of relying on optional Unicode glyphs.
        name.setIcon(Icons.of(collapsedHistory.contains(key) ? "next" : "down", 13, Theme.GREEN));
        name.setIconTextGap(9);
        row.add(name, BorderLayout.WEST); row.add(Theme.label(count + com.eye.i18n.I18n.text(" 条记录"), 11, Theme.SECONDARY), BorderLayout.EAST);
        attachCollapse(row, key); return row;
    }

    private void attachCollapse(JComponent component, String key) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.setToolTipText(collapsedHistory.contains(key) ? com.eye.i18n.I18n.text("展开记录") : com.eye.i18n.I18n.text("收起记录"));
        component.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                int position = historyScrollPane.getVerticalScrollBar().getValue();
                if (!collapsedHistory.add(key)) collapsedHistory.remove(key);
                refreshHistoryView();
                SwingUtilities.invokeLater(() -> historyScrollPane.getVerticalScrollBar().setValue(position));
            }
        });
        for (Component child : component.getComponents()) if (child instanceof JComponent c) attachCollapse(c, key);
    }

    private void maybeLoadMoreHistory() {
        if (rebuildingHistory || !"history".equals(visibleCard) || historyExplicitDateRange || historyNoMore || historyLoadingMore || !historyCacheReady) return;
        JScrollBar bar=historyScrollPane.getVerticalScrollBar();
        int reserve=Math.max(1200,bar.getVisibleAmount()*4);
        if(bar.getMaximum()-bar.getValue()-bar.getVisibleAmount()>reserve)return;
        expandHistoryWindow();
    }

    private void expandHistoryWindow() {
        if(historyExplicitDateRange || historyNoMore || historyLoadingMore || !historyCacheReady)return;
        int days=historyLoadedDays<30?30:historyLoadedDays+30;
        LocalDate from=manager.getToday().minusDays(historyLoadedDays);
        // Skip empty calendar gaps instead of making the user scroll through repeated empty pages.
        LocalDate next=filteredHistory().stream().map(r->r.date).filter(d->d.isBefore(from)).findFirst().orElse(null);
        if(next==null){historyNoMore=true;return;}
        historyLoadedDays=Math.max(days,(int)java.time.temporal.ChronoUnit.DAYS.between(next,manager.getToday()));
        queueHistoryRows();
    }

    private void pickHistoryDateRange() {
        LocalDate initialFrom = historyFromDate;
        LocalDate initialTo = historyToDate;
        try {
            String fromText = historyFromField.getText().trim();
            String toText = historyToField.getText().trim();
            if (!fromText.isEmpty()) initialFrom = LocalDate.parse(fromText, DATE_FMT);
            if (!toText.isEmpty()) initialTo = LocalDate.parse(toText, DATE_FMT);
            if (initialFrom == null && initialTo != null) initialFrom = initialTo;
            if (initialTo == null && initialFrom != null) initialTo = initialFrom;
        } catch (DateTimeParseException ignored) {
            // A visual picker is also the recovery path for malformed manual input.
        }

        DateRange picked = RangeDatePickerDialog.showDialog(
                SwingUtilities.getWindowAncestor(this), initialFrom, initialTo, manager.getToday());
        if (picked == null) return;

        historyFromField.setText(picked.from.format(DATE_FMT));
        historyToField.setText(picked.to.format(DATE_FMT));
        historyFilterMessage.setText("");
        historyExplicitDateRange = true;
        historyFromDate = picked.from;
        historyToDate = picked.to;
        refreshHistoryView();
        SwingUtilities.invokeLater(() -> {historyRestorePosition=0;historyScrollPane.getVerticalScrollBar().setValue(0);});
    }

    private void applyHistoryFilters() {
        String fromText = historyFromField.getText().trim();
        String toText = historyToField.getText().trim();
        historyFilterMessage.setText("");
        if (fromText.isEmpty() && toText.isEmpty()) {
            historyExplicitDateRange = false;
            historyFromDate = null;
            historyToDate = null;
            historyLoadedDays = 7;
            refreshHistoryView();
            return;
        }
        try {
            LocalDate from = fromText.isEmpty() ? LocalDate.parse(toText, DATE_FMT) : LocalDate.parse(fromText, DATE_FMT);
            LocalDate to = toText.isEmpty() ? from : LocalDate.parse(toText, DATE_FMT);
            if (from.isAfter(to)) {
                historyFilterMessage.setText(com.eye.i18n.I18n.text("开始日期不能晚于结束日期"));
                return;
            }
            historyExplicitDateRange = true;
            historyFromDate = from;
            historyToDate = to;
            historyFromField.setText(from.format(DATE_FMT));
            historyToField.setText(to.format(DATE_FMT));
            refreshHistoryView();
        } catch (DateTimeParseException ex) {
            historyFilterMessage.setText(com.eye.i18n.I18n.text("日期格式请使用 yyyy-MM-dd"));
        }
    }

    private void resetHistoryFilters() {
        historyFromField.setText(manager.getToday().minusDays(7).format(DATE_FMT));
        historyToField.setText(manager.getToday().minusDays(1).format(DATE_FMT));
        historyFilterMessage.setText("");
        historyExplicitDateRange = false;
        historyFromDate = null;
        historyToDate = null;
        historyLoadedDays = 7;
        if (historyTaskBox.getItemCount() > 0) historyTaskBox.setSelectedIndex(0);
        refreshHistoryView();
        SwingUtilities.invokeLater(() -> {historyRestorePosition=0;historyScrollPane.getVerticalScrollBar().setValue(0);});
    }

    private String selectedHistoryPlanId() {
        Object selected = historyTaskBox.getSelectedItem();
        return selected instanceof HistoryTaskChoice choice ? choice.planId : null;
    }

    private static final class HistoryTaskChoice {
        final String planId;
        final String name;
        HistoryTaskChoice(String planId, String name) { this.planId = planId; this.name = name; }
        @Override public String toString() { return name; }
    }

    private JComponent createHistoryDateSeparator(LocalDate date, int count) {
        JPanel row = Theme.panel(new GridBagLayout());
        row.setBorder(new EmptyBorder(5, 2, 3, 2));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setPreferredSize(new Dimension(500, 34));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;

        JLabel text = Theme.label(date.format(DATE_HEADING_FMT), 12, Theme.GREEN);
        text.setFont(Theme.bold(12));
        text.setIcon(Icons.of(collapsedHistory.contains("day:" + date) ? "next" : "down", 13, Theme.GREEN));
        text.setIconTextGap(7);
        gc.gridx = 0;
        gc.insets = new Insets(0, 0, 0, 11);
        row.add(text, gc);

        JComponent line = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                Theme.quality(g);
                g.setColor(Theme.LINE);
                int y = getHeight() / 2;
                g.drawLine(0, y, getWidth(), y);
                g.dispose();
            }
        };
        line.setPreferredSize(new Dimension(160, 20));
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 11);
        row.add(line, gc);

        JLabel badge = Theme.badge(count + com.eye.i18n.I18n.text(" 项"), Theme.SECONDARY, Theme.MINT);
        gc.gridx = 2;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.insets = new Insets(0, 0, 0, 0);
        row.add(badge, gc);
        return row;
    }

    private List<JComponent> buildTodayRows(List<PlanItem> plans) {
        List<JComponent> rows = new ArrayList<>();
        for (PlanItem plan : plans) rows.add(createTodayRow(plan));
        return rows;
    }

    private void refillListPanel(JPanel panel, List<JComponent> rows) {
        refillListPanel(panel, rows, com.eye.i18n.I18n.text("当前没有计划。"));
    }

    private void refillListPanel(JPanel panel, List<JComponent> rows, String emptyText) {
        panel.removeAll();
        Object counter = panel.getClientProperty("rest.count");
        if (counter instanceof JLabel count) count.setText(rows.size() + com.eye.i18n.I18n.text(" 项"));
        if (rows.isEmpty()) {
            Theme.Surface empty = Theme.card(new BorderLayout(18, 0));
            empty.setBorder(new EmptyBorder(24, 24, 24, 24));
            empty.setPreferredSize(new Dimension(500, 108));
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel icon = new JLabel(Icons.of("calendar", 26, Theme.GREEN), SwingConstants.CENTER);
            Theme.Surface iconTile = new Theme.Surface(new BorderLayout(), Theme.MINT, null, 16);
            iconTile.setPreferredSize(new Dimension(56, 56)); iconTile.add(icon);
            empty.add(iconTile, BorderLayout.WEST);
            empty.add(Theme.heading(emptyText, 13), BorderLayout.CENTER);
            panel.add(empty);
        } else {
            for (JComponent row : rows) {
                panel.add(row);
                panel.add(Box.createVerticalStrut(8));
            }
        }
        panel.revalidate();
        panel.repaint();
    }

    private JComponent createTodayRow(PlanItem plan) {
        boolean current = actions != null && plan.id.equals(actions.getCurrentPlanId());
        JPanel row = createRowPanel(current);
        JPanel top = Theme.panel(new BorderLayout(12, 0));
        JLabel title = Theme.heading(plan.name, 15);
        title.setToolTipText(plan.name);
        if(plan.pinned){title.setIcon(Icons.of("pin",14,Theme.GREEN));title.setIconTextGap(6);}
        JPanel titleWords = Theme.panel(new BorderLayout(0, 2));
        titleWords.add(title, BorderLayout.NORTH);
        JLabel rule = Theme.label(ruleText(plan), 11, Theme.SECONDARY);
        rule.setToolTipText(rule.getText());
        titleWords.add(rule, BorderLayout.SOUTH);
        top.add(titleWords, BorderLayout.CENTER);
        JPanel status = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        String state = current ? com.eye.i18n.I18n.text("当前工作计划")
                : plan.remainingSeconds <= 0 ? com.eye.i18n.I18n.text("已完成") : kindText(plan);
        JLabel badge = Theme.badge(state, current ? Color.WHITE : Theme.GREEN, current ? Theme.GREEN : Theme.MINT);
        if (current || plan.remainingSeconds <= 0) {
            badge.setIcon(Icons.of(current ? "play" : "check", 12, current ? Color.WHITE : Theme.GREEN));
            badge.setIconTextGap(5);
        }
        status.add(badge); top.add(status, BorderLayout.EAST);
        row.add(top, BorderLayout.NORTH);

        JPanel metrics = metricStrip();
        metrics.add(metric(com.eye.i18n.I18n.text("计划时长"), PlanItem.fmtHms(plan.plannedSeconds)));
        metrics.add(metric(com.eye.i18n.I18n.text("今日剩余"), PlanItem.fmtHms(plan.remainingSeconds)));
        metrics.add(metric(com.eye.i18n.I18n.text("今日已完成"), PlanItem.fmtHms(Math.max(0, plan.plannedSeconds - plan.remainingSeconds))));
        row.add(metrics, BorderLayout.CENTER);

        JPanel buttons = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (plan.remainingSeconds <= 0) {
            JButton extendBtn = new JButton(com.eye.i18n.I18n.text("今日加时"));
            Theme.smallButton(extendBtn, "secondary", "plus");
            extendBtn.addActionListener(e -> extendToday(plan));
            buttons.add(extendBtn);
        }
        JButton selectBtn = new JButton(current ? com.eye.i18n.I18n.text("当前计划") : config != null && config.planManagementSelectionAction == Config.PlanSelectionAction.SELECT_AND_START ? com.eye.i18n.I18n.text("选择并开始") : com.eye.i18n.I18n.text("设为当前"));
        Theme.smallButton(selectBtn, current ? "secondary" : "primary", current ? "check" : "play");
        boolean canSelect = actions != null && plan.remainingSeconds > 0;
        selectBtn.setEnabled(canSelect && !current);
        selectBtn.putClientProperty("tutorial.select",true);
        selectBtn.addActionListener(e -> {
            if (actions != null) {
                actions.onSelectCurrentPlan(plan.id);
                refresh();
            }
        });
        buttons.add(selectBtn);
        JButton editBtn = utilityButton("edit", com.eye.i18n.I18n.text("修改"), "ghost");
        editBtn.addActionListener(e -> editPlan(plan));
        buttons.add(editBtn);
        JButton more=new JButton("⋯");Theme.smallButton(more,"ghost",null);more.setFont(Theme.bold(18));more.setPreferredSize(new Dimension(32,32));
        more.setToolTipText(com.eye.i18n.I18n.text("更多操作"));more.getAccessibleContext().setAccessibleName(more.getToolTipText());
        JPopupMenu menu=new JPopupMenu();
        more.putClientProperty("tutorial.more",true);more.putClientProperty("tutorial.popup",menu);more.putClientProperty("tutorial.region",row);
        addPlanMenuItem(menu,plan.pinned?"取消置顶":"切换置顶","pin",()->{manager.setPinned(plan.id,!plan.pinned);refresh();});
        addPlanMenuItem(menu,"保存为模板","grid",()->{manager.saveTemplate(plan.id);showCard("library");});
        menu.addSeparator();addPlanMenuItem(menu,"删除","trash",()->deletePlan(plan));
        more.addActionListener(e->menu.show(more,Math.min(0,more.getWidth()-menu.getPreferredSize().width),more.getHeight()));buttons.add(more);
        JPanel footer = Theme.panel(new BorderLayout(18, 0));
        double done = plan.plannedSeconds <= 0 ? 0 : Math.max(0, plan.plannedSeconds - plan.remainingSeconds) / (double) plan.plannedSeconds;
        footer.add(progressSummary(done), BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        row.add(footer, BorderLayout.SOUTH);
        return row;
    }

    private JComponent createScheduledRow(PlanManager.ScheduledOccurrence occurrence) {
        PlanItem plan = occurrence.plan;
        JPanel row = createRowPanel();
        row.setPreferredSize(new Dimension(500, 118));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        JPanel titleRow = Theme.panel(new BorderLayout(12, 0));
        JLabel title = Theme.heading(plan.name, 15);
        title.setToolTipText(plan.name);
        titleRow.add(title, BorderLayout.CENTER);
        titleRow.add(Theme.badge(occurrence.date.format(DATE_FMT), Theme.GREEN, Theme.MINT), BorderLayout.EAST);
        row.add(titleRow, BorderLayout.NORTH);

        String info = com.eye.i18n.I18n.text("类型：") + kindText(plan)
                + com.eye.i18n.I18n.text("    规则：") + ruleText(plan)
                + com.eye.i18n.I18n.text("    计划时长：") + PlanItem.fmtHms(plan.plannedSeconds);
        JLabel detail = Theme.label(info, 12, Theme.SECONDARY);
        detail.setToolTipText(null);
        row.add(detail, BorderLayout.CENTER);

        JPanel buttons = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton editBtn = new JButton(com.eye.i18n.I18n.text("修改计划"));
        Theme.smallButton(editBtn, "secondary", "edit");
        editBtn.addActionListener(e -> editPlan(plan));
        buttons.add(editBtn);
        JButton deleteBtn = new JButton(com.eye.i18n.I18n.text("删除该计划"));
        Theme.smallButton(deleteBtn, "danger", "trash");
        deleteBtn.addActionListener(e -> deletePlan(plan));
        buttons.add(deleteBtn);
        row.add(buttons, BorderLayout.SOUTH);
        return row;
    }

    private JComponent createHistoryRow(PlanManager.HistoricalRecord record) {
        JPanel row = createRowPanel();
        row.setPreferredSize(new Dimension(500, 144));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 144));
        JPanel header = Theme.panel(new BorderLayout(12, 0));
        JLabel title = Theme.heading(record.plan.name, 15);
        title.setToolTipText(record.plan.name);
        header.add(title, BorderLayout.CENTER);
        header.add(Theme.badge(record.getPercent() + com.eye.i18n.I18n.text("% 完成"), Theme.GREEN, Theme.MINT), BorderLayout.EAST);
        row.add(header, BorderLayout.NORTH);

        JPanel body = Theme.panel(new BorderLayout(0, 4));
        String info = com.eye.i18n.I18n.text("类型：") + kindText(record.plan) + com.eye.i18n.I18n.text("    规则：") + ruleText(record.plan);
        JLabel meta = Theme.label(info, 11, Theme.SECONDARY);
        meta.setToolTipText(null);
        body.add(meta, BorderLayout.NORTH);
        JPanel metrics = metricStrip();
        metrics.add(metric(com.eye.i18n.I18n.text("计划时长"), PlanItem.fmtHms(record.getPlannedSeconds())));
        metrics.add(metric(com.eye.i18n.I18n.text("已完成"), PlanItem.fmtHms(record.completedSeconds)));
        metrics.add(metric(com.eye.i18n.I18n.text("剩余"), PlanItem.fmtHms(record.remainingSeconds)));
        body.add(metrics, BorderLayout.CENTER);
        row.add(body, BorderLayout.CENTER);

        JComponent progress = progressSummary(record.getPercent() / 100.0);
        JPanel actions = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton delete = new JButton(com.eye.i18n.I18n.text("删除"));
        Theme.smallButton(delete, "danger", "trash");
        delete.addActionListener(e -> deleteHistoricalRecord(record));
        actions.add(delete);
        JPanel footer = Theme.panel(new BorderLayout(14, 0));
        footer.add(progress, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.EAST);
        row.add(footer, BorderLayout.SOUTH);
        return row;
    }

    private void deleteHistoricalRecord(PlanManager.HistoricalRecord record) {
        int ok = JOptionPane.showConfirmDialog(this,
                com.eye.i18n.I18n.text("确定删除这一条历史记录？\n\n") + record.date.format(DATE_FMT) + "  ·  " + record.plan.name
                        + com.eye.i18n.I18n.text("\n\n只删除这一天的这一条记录，不会删除当前计划定义。"),
                com.eye.i18n.I18n.text("删除历史记录"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        if (!manager.deleteHistoricalRecord(record.date, record.plan.id)) {
            JOptionPane.showMessageDialog(this, com.eye.i18n.I18n.text("删除失败，记录可能已经不存在。"), com.eye.i18n.I18n.text("提示"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        historyCacheReady=false;
        refreshHistoryTaskChoices();
        refreshHistoryView();
    }

    private JPanel createRowPanel() { return createRowPanel(false); }

    private JPanel createRowPanel(boolean current) {
        Theme.Surface row = new Theme.Surface(new BorderLayout(8, 6), Theme.SURFACE,
                current ? Theme.SAGE : Theme.LINE, 16) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (current) {
                    Graphics2D g = (Graphics2D) graphics.create(); Theme.quality(g);
                    double scale = com.eye.ui.theme.ProportionalPanel.scaleOf(this);
                    g.setColor(Theme.GREEN);
                    g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 16 * scale, 3 * scale,
                            Math.max(0, getHeight() - 32 * scale), 3 * scale, 3 * scale));
                    g.dispose();
                }
            }
        };
        row.setBorder(new EmptyBorder(12, 18, 10, 18));
        row.setPreferredSize(new Dimension(500, 144));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 144));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JPanel metricStrip() {
        JPanel strip = Theme.panel(new GridLayout(1, 3, 12, 0));
        strip.setBorder(new EmptyBorder(2, 0, 1, 0));
        return strip;
    }

    private JPanel metric(String label, String value) {
        JPanel p = Theme.panel(new BorderLayout(0, 2));
        p.add(Theme.label(label, 10, Theme.SECONDARY), BorderLayout.NORTH);
        JLabel number = Theme.label(value, 18, Theme.INK);
        number.setFont(Theme.numbers(18));
        p.add(number, BorderLayout.CENTER);
        return p;
    }

    private static JButton utilityButton(String icon, String text, String variant) {
        JButton button = SettingsUi.iconButton(icon, text, variant);
        button.setToolTipText(text);
        return button;
    }
    private static void addPlanMenuItem(JPopupMenu menu,String label,String icon,Runnable action){
        JMenuItem item=new JMenuItem(com.eye.i18n.I18n.text(label),Icons.of(icon,15,"trash".equals(icon)?Theme.DANGER:Theme.GREEN));
        item.setFont(Theme.font(12));item.setBorder(new EmptyBorder(9,12,9,18));
        if("trash".equals(icon))item.setForeground(Theme.DANGER);
        item.addActionListener(e->action.run());menu.add(item);
    }

    private JComponent progressSummary(double value) {
        JPanel summary = Theme.panel(new BorderLayout(9, 0));
        JLabel percent = Theme.label(Math.round(Math.max(0, Math.min(1, value)) * 100) + "%", 11, Theme.SECONDARY);
        percent.setFont(Theme.numbers(11));
        summary.add(new ProgressLine(value), BorderLayout.CENTER);
        summary.add(percent, BorderLayout.EAST);
        return summary;
    }

    private static final class ProgressLine extends JComponent {
        private final double value;
        ProgressLine(double value) {
            this.value = Math.max(0, Math.min(1, value));
            setPreferredSize(new Dimension(100, 8));
            setToolTipText(com.eye.i18n.I18n.text("已完成 ") + Math.round(this.value * 100) + "%");
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.quality(g);
            int y = getHeight() / 2 - 2;
            g.setColor(Theme.LINE);
            g.fillRoundRect(0, y, getWidth(), 4, 4, 4);
            g.setPaint(new GradientPaint(0, y, Theme.GREEN, Math.max(1, getWidth()), y, Theme.GREEN_HOVER));
            g.fillRoundRect(0, y, (int) Math.round(getWidth() * value), 4, 4, 4);
            g.dispose();
        }
    }

    private void extendToday(PlanItem plan) {
        if (plan == null) return;
        DurationInput input = new DurationInput();
        input.setMode(configuredDurationMode());
        input.setSeconds(15 * 60);
        if(!SettingsUi.confirm(this,com.eye.i18n.I18n.text("今日临时加时 · ")+plan.name,com.eye.i18n.I18n.text("只增加当前学习日的时长。"),input,com.eye.i18n.I18n.text("确定"),()->InlineErrors.number(input,input::getSeconds,1,Integer.MAX_VALUE,com.eye.i18n.I18n.text("加时时长必须大于 0 秒。"))))return;
        final int seconds;
        try { seconds = input.getSeconds(); }
        catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, com.eye.i18n.I18n.text("加时时长必须大于 0 秒。"), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        PlanItem updated = manager.extendToday(plan.id, seconds);
        if (updated == null) {
            JOptionPane.showMessageDialog(this, com.eye.i18n.I18n.text("加时失败，该任务今天可能已不再有效。"), com.eye.i18n.I18n.text("提示"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        refresh();
    }

    private void deletePlan(PlanItem plan) {
        int ok = JOptionPane.showConfirmDialog(
                this,
                com.eye.i18n.I18n.text("确定删除计划？\n") + plan.name + com.eye.i18n.I18n.text("\n\n删除后将从当前和预定视图中移除。"),
                com.eye.i18n.I18n.text("确认删除"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (ok == JOptionPane.OK_OPTION) {
            manager.deletePlan(plan.id);
            refresh();
        }
    }

    private DurationInput.Mode configuredDurationMode() {
        return config != null && config.timeInputMode == Config.TimeInputMode.WHEEL
                ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED;
    }

    private void addPlan() {
        showPlanEditor(null);
    }

    private void editPlan(PlanItem plan) {
        if (plan == null) return;
        PlanItem fresh = manager.get(plan.id);
        showPlanEditor(fresh == null ? plan : fresh);
    }

    private void showPlanEditor(PlanItem existing){new PlanEditorDialog(this,manager,config,this::refresh).show(existing);}

    private static String kindText(PlanItem p) {
        if (p.kind == PlanItem.PlanKind.RECURRING) return com.eye.i18n.I18n.text("周期");
        if (p.kind == PlanItem.PlanKind.MULTI_DAY) return com.eye.i18n.I18n.text("多日预定");
        return com.eye.i18n.I18n.text("仅当天");
    }

    private static String ruleText(PlanItem p) {
        if (p.kind == PlanItem.PlanKind.TODAY_ONLY) return com.eye.i18n.I18n.text("仅当天");
        if (p.kind == PlanItem.PlanKind.MULTI_DAY) {
            if (p.selectedDates.isEmpty()) return com.eye.i18n.I18n.text("未设置日期");
            return p.selectedDates.size() + " dates";
        }
        switch (p.recurringRule) {
            case WORKDAY: return com.eye.calendar.DayCategory.WORKDAY.toString();
            case NON_WORKDAY: return com.eye.calendar.DayCategory.NON_WORKDAY.toString();
            case PUBLIC_HOLIDAY: return com.eye.calendar.DayCategory.HOLIDAY.toString();
            case NORMAL_WORKDAY: return com.eye.calendar.DayCategory.NORMAL_WORKDAY.toString();
            case EVERY_DAY:
                return com.eye.i18n.I18n.text("每天");
            case WEEKDAY:
                return com.eye.i18n.I18n.text("周一至周五");
            case WEEKEND:
                return com.eye.i18n.I18n.text("周末");
            case HOLIDAY:
                return com.eye.i18n.I18n.text("周末及自定义假期");
            case BY_WEEKDAY:
                return p.weekdays.isEmpty() ? com.eye.i18n.I18n.text("按星期（未设置）") : com.eye.i18n.I18n.text("按星期 ") + p.weekdays;
            default:
                return com.eye.i18n.I18n.text("未知");
        }
    }

}
