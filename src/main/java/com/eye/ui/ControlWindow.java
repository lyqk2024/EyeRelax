package com.eye.ui;

import com.eye.ui.theme.SettingsUi;

import com.eye.core.Config;
import com.eye.core.UsageLimitMonitor;
import com.eye.core.CycleEngine;
import com.eye.plan.PlanItem;
import com.eye.plan.PlanManager;
import com.eye.plan.PlanWindow;
import com.eye.reminder.ReminderManager;
import com.eye.reminder.ReminderPanel;
import com.eye.ui.theme.DurationInput;
import com.eye.ui.theme.Icons;
import com.eye.ui.theme.Theme;
import com.eye.ui.theme.TimerDial;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Single application window. Focus, plans, preferences and system settings switch in place. */
public class ControlWindow extends JFrame {
    private static final String PAGE_FOCUS = "focus";
    private static final String PAGE_PLANS = "plans";
    private static final String PAGE_REMINDERS = "reminders";
    private static final String PAGE_PREFERENCES = "preferences";
    private static final String PAGE_SYSTEM = "system";
    private static final String PAGE_SETTINGS = "settings";
    private static final String PAGE_COMPUTER = "computer";
    private final ApplicationSettingsPanel settingsPage;
    private final ComputerUsagePanel computerUsagePage = new ComputerUsagePanel();
    private final JButton computerBtn = new JButton(com.eye.i18n.I18n.text("电脑使用"));
    private UsageLimitMonitor tutorialUsageMonitor;
    private com.eye.tutorial.TutorialController tutorial;
    public void setUsageMonitor(UsageLimitMonitor monitor){tutorialUsageMonitor=monitor;settingsPage.setUsageMonitor(monitor);}
    public void configureTutorial(com.eye.tutorial.TutorialState state){
        if(tutorial!=null)tutorial.close();
        final long[] editorBaseline={0},rhythmBaseline={0},chooserBaseline={0};
        var steps=java.util.List.of(
            new com.eye.tutorial.TutorialController.Step("先为今天安排一项任务。点击计划管理。",()->planBtn,this::showFocusPage,()->PAGE_PLANS.equals(activePage)),
            new com.eye.tutorial.TutorialController.Step("填写名称和时长，创建你的第一个计划。",()->planPage.tutorialTarget("add"),()->{showPlanPage();planPage.showCard("today");editorBaseline[0]=planPage.tutorialPlanEditorClosedCount();},()->planPage.tutorialPlanEditorClosedCount()>editorBaseline[0]),
            new com.eye.tutorial.TutorialController.Step("点击我的节奏，可以调节工作、休息时长与休息模式。",()->rhythmCard,()->{showFocusPage();rhythmBaseline[0]=tutorialRhythmEditorClosed;},()->tutorialRhythmEditorClosed>rhythmBaseline[0]),
            new com.eye.tutorial.TutorialController.Step("在当前计划中选择任务；不选计划也能自由专注。",()->currentPlanCard,()->{showFocusPage();chooserBaseline[0]=tutorialPlanChooserClosed;},()->tutorialPlanChooserClosed>chooserBaseline[0]),
            new com.eye.tutorial.TutorialController.Step("点击开始，进入专注。",()->startBtn,this::showFocusPage,()->engine.isRunning()&&engine.isInWork()),
            new com.eye.tutorial.TutorialController.Step("需要调整节奏时，可以暂停或立即休息。",()->(JComponent)stopBtn.getParent().getParent(),this::showFocusPage,null),
            new com.eye.tutorial.TutorialController.Step("在专注统计中回顾投入；其他功能可在设置的使用教程中了解。",()->planPage.tutorialTarget("statistics"),()->{showPlanPage();planPage.showCard("today");},()->planPage.tutorialStatisticsSelected())
        );
        tutorial=new com.eye.tutorial.TutorialController(this,state,steps,()->engine.isInRelax()||engine.isReminderActive()||engine.isSessionLocked()||tutorialUsageMonitor!=null&&tutorialUsageMonitor.blocked());
        tutorial.configureSettingsShortcut(2,()->openSettings("rhythm"),settingsPage::tutorialSaveTarget,settingsPage::saveTutorialRhythm);
        com.eye.tutorial.FeatureTutorials.register(tutorial,this::topicTarget,this::openTopicPage);
        settingsPage.setTopicAction(id->tutorial.startTopic(id));
        planPage.setTutorialPageListener(card->offerTopicLater("activity".equals(card)?"statistics":"library".equals(card)?"archive":"plans"));
        settingsPage.setTutorialSelectionListener(section->{switch(section){case "usage-limits"->offerTopicLater("limits");case "application-limits"->offerTopicLater("app-limits");case "strict-local"->offerTopicLater("strict");default->{}}});
    }
    private JComponent topicTarget(String id,String key){return switch(id){case "plans","archive","statistics","insights"->planPage.tutorialTarget(key);case "reminders"->reminderPage.tutorialTarget(key);case "computer"->computerUsagePage.tutorialTarget(key);case "app-limits","strict"->settingsPage.tutorialSettingTarget(id,key);default->settingsPage.tutorialLimitTarget(key);};}
    private void openTopicPage(String id){openTopicPage(id,"");}
    private void openTopicPage(String id,String key){switch(id){case "plans"->{showPlanPage();planPage.showCard("scheduled".equals(key)?"scheduled":"today");}case "archive"->{showPlanPage();planPage.showCard("library");planPage.prepareTutorialTarget(key);}case "statistics","insights"->{showPlanPage();planPage.showCard("activity");planPage.prepareTutorialTarget(key);}case "reminders"->showReminderPage();case "computer"->showPage(PAGE_COMPUTER);case "limits"->openSettings("usage-limits");case "app-limits"->openSettings("application-limits");case "strict"->openSettings("strict-local");default->{}}}
    private boolean topicPageMatches(String id){return switch(id){case "plans"->PAGE_PLANS.equals(activePage)&&!java.util.Set.of("activity","library").contains(planPage.tutorialCurrentCard());case "statistics","insights"->PAGE_PLANS.equals(activePage)&&"activity".equals(planPage.tutorialCurrentCard());case "archive"->PAGE_PLANS.equals(activePage)&&"library".equals(planPage.tutorialCurrentCard());case "reminders"->PAGE_REMINDERS.equals(activePage);case "computer"->PAGE_COMPUTER.equals(activePage);case "limits"->PAGE_SETTINGS.equals(activePage)&&settingsPage.tutorialSectionSelected("usage-limits");case "app-limits"->PAGE_SETTINGS.equals(activePage)&&settingsPage.tutorialSectionSelected("application-limits");case "strict"->PAGE_SETTINGS.equals(activePage)&&settingsPage.tutorialSectionSelected("strict-local");default->false;};}
    private void offerTopicLater(String id){if(tutorial!=null&&!tutorial.isActive())SwingUtilities.invokeLater(()->{if(tutorial!=null&&!tutorial.isActive()&&topicPageMatches(id))tutorial.offerTopic(id);});}
    public boolean automaticTutorialPending(){return tutorial!=null&&tutorial.automaticWelcomePending();}
    public void offerTutorialOnLaunch(){if(tutorial!=null)SwingUtilities.invokeLater(tutorial::offerOnLaunch);}
    private void replayTutorial(){
        if(tutorial==null)try{configureTutorial(com.eye.tutorial.TutorialState.capture(com.eye.core.DataPaths.root()));}catch(Exception ex){return;}
        tutorial.startWelcome(true);
    }
    public void startUpdateChecks(){settingsPage.startUpdateChecks();}
    private com.eye.core.ComputerActivity computerActivity;
    public void setComputerActivity(com.eye.core.ComputerActivity activity){computerActivity=activity;computerUsagePage.setActivity(activity);}
    public void setApplicationProtection(com.eye.win.ApplicationProtection protection){
        Runnable open=()->ApplicationLimitsPanel.open(this,config,computerActivity,protection.limits());
        computerUsagePage.setLimitsAction(open);settingsPage.setApplicationLimitsAction(open);
    }

    private JPanel sidebar;
    private boolean sidebarCompact, sidebarForcedExpanded;
    private final JButton sidebarToggle = new JButton();
    private final JButton globalSettingsBtn = new JButton(com.eye.i18n.I18n.text("设置"));
    private String returnFromSettings = PAGE_FOCUS;

    private final CycleEngine engine;
    private final Config config;
    private final PlanManager planManager;

    private final JButton startBtn = new JButton(com.eye.i18n.I18n.text("开始"));
    private final JButton stopBtn = new JButton(com.eye.i18n.I18n.text("暂停"));
    private final JButton restartBtn = new JButton(com.eye.i18n.I18n.text("重新开始"));
    private final JButton restNowBtn = new JButton(com.eye.i18n.I18n.text("立即休息"));
    private final JButton focusBtn = new JButton(com.eye.i18n.I18n.text("专注计时"));
    private final JButton preferencesBtn = new JButton(com.eye.i18n.I18n.text("偏好设置"));
    // Compatibility name retained for old QA/reflection callers.
    private final JButton settingsBtn = preferencesBtn;
    private final JButton systemBtn = new JButton(com.eye.i18n.I18n.text("系统设置"));
    private final JButton planBtn = new JButton(com.eye.i18n.I18n.text("计划管理"));
    private final JButton reminderBtn = new JButton(com.eye.i18n.I18n.text("提醒计划"));

    private final Runnable onExit;
    private final Runnable onStartRequested;
    private final Runnable onStopRequested;
    private final Runnable onRestartRequested;
    private final Runnable onChoosePlanRequested;
    private final Consumer<Boolean> onToggleOverlay;

    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pageCards = new JPanel(pageLayout);
    private String activePage = PAGE_FOCUS;

    private final TimerDial dial = new TimerDial();
    private final JLabel stateLabel = new FocusStateLabel();
    private final JLabel workValue = Theme.heading("—", 18);
    private final JLabel relaxValue = Theme.heading("—", 18);
    private final JLabel modeValue = Theme.heading("—", 14);
    private final JLabel planName = Theme.heading(com.eye.i18n.I18n.text("自由专注"), 16);
    private final JLabel planDetail = Theme.label(com.eye.i18n.I18n.text("未关联工作计划"), 12, Theme.SECONDARY);
    private final PlanProgress planProgress = new PlanProgress();
    private final com.eye.ui.theme.RhythmPreview rhythmPreview = new com.eye.ui.theme.RhythmPreview();
    private final JLabel clockDescription = Theme.label(com.eye.i18n.I18n.text("准备好后，开启一段专注时光。"), 12, Theme.SECONDARY);
    private final JLabel dateLabel = Theme.label("", 12, Theme.SECONDARY);
    private final Timer displayTimer = new Timer(500, e -> refreshPresentation());
    private Theme.Surface currentPlanCard;
    private Theme.Surface rhythmCard;

    private final PlanWindow planPage;
    private final ReminderManager reminderManager;
    private final ReminderPanel reminderPage;

    public ControlWindow(CycleEngine engine,
                         Config config,
                         PlanManager planManager,
                         ReminderManager reminderManager,
                         Runnable onExit,
                         Runnable onStartRequested,
                         Runnable onStopRequested,
                         Runnable onRestartRequested,
                         Runnable onChoosePlanRequested,
                         Consumer<Boolean> onToggleOverlay) {
        super("EyeRelax");
        this.engine = engine;
        this.config = config;
        this.planManager = planManager;
        this.reminderManager = reminderManager == null ? new ReminderManager() : reminderManager;
        this.onExit = onExit;
        this.onStartRequested = onStartRequested;
        this.onStopRequested = onStopRequested;
        this.onRestartRequested = onRestartRequested;
        this.onChoosePlanRequested = onChoosePlanRequested;
        this.onToggleOverlay = onToggleOverlay;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        Theme.fitInitialWindow(this, 1080, 740);
        setMinimumSize(new Dimension(Math.min(900, getWidth()), Math.min(620, getHeight())));
        setResizable(true);
        Theme.window(this);
        setLocationRelativeTo(null);

        Theme.button(startBtn, "primary", "play");
        Theme.button(stopBtn, "secondary", "pause");
        Theme.button(restartBtn, "ghost", "restart");
        Theme.button(restNowBtn, "ghost", "leaf");
        Theme.navigation(focusBtn, "timer");
        Theme.navigation(planBtn, "calendar");
        Theme.navigation(reminderBtn, "bell");
        Theme.navigation(preferencesBtn, "settings");
        Theme.navigation(systemBtn, "grid");
        startBtn.setToolTipText(null);
        stopBtn.setToolTipText(null);
        restartBtn.setToolTipText(null);
        restNowBtn.setToolTipText(null);
        planPage = new PlanWindow(planManager, new PlanWindow.Actions() {
            @Override public String getCurrentPlanId() { return engine.getCurrentPlanId(); }
            @Override public void onSelectCurrentPlan(String planId) {
                engine.setCurrentPlanId(planId);
                if ((config.autoStartAfterPlanSelection || config.planManagementSelectionAction == Config.PlanSelectionAction.SELECT_AND_START) && !engine.isRunning() && onStartRequested != null) {
                    onStartRequested.run();
                }
                refreshPresentation();
                refreshButtons();
            }
        }, config);
        reminderPage = new ReminderPanel(this.reminderManager, config, engine, this::openQuickRhythmEditor);

        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(Theme.BG);
        sidebar = buildSidebar();
        shell.add(sidebar, BorderLayout.WEST);
        pageCards.setOpaque(false);
        pageCards.add(buildFocusPage(), PAGE_FOCUS);
        pageCards.add(planPage, PAGE_PLANS);
        pageCards.add(reminderPage, PAGE_REMINDERS);
        settingsPage = new ApplicationSettingsPanel(config,engine,planManager,onExit,onToggleOverlay,
                ()->showPage(returnFromSettings),this::refreshButtons,planPage::refresh,reminderPage::refresh);
        settingsPage.setTutorialAction(this::replayTutorial);
        pageCards.add(settingsPage, PAGE_SETTINGS);
        pageCards.add(computerUsagePage, PAGE_COMPUTER);
        Theme.navigation(computerBtn, "monitor");
        computerBtn.addActionListener(e -> showPage(PAGE_COMPUTER));
        shell.add(pageCards, BorderLayout.CENTER);
        setContentPane(new com.eye.ui.theme.ProportionalPanel(shell, 1064, 701));

        startBtn.addActionListener(e -> { if (onStartRequested != null) onStartRequested.run(); });
        stopBtn.addActionListener(e -> { if (onStopRequested != null) onStopRequested.run(); });
        restartBtn.addActionListener(e -> { if (onRestartRequested != null) onRestartRequested.run(); });
        restNowBtn.addActionListener(e -> { if (engine.startRestNow()) refreshButtons(); });
        focusBtn.addActionListener(e -> showFocusPage());
        planBtn.addActionListener(e -> showPlanPage());
        reminderBtn.addActionListener(e -> showReminderPage());
        preferencesBtn.addActionListener(e -> requestSettingsPage());
        systemBtn.addActionListener(e -> showSystemSettingsPage());

        dial.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dial.setToolTipText(null);
        dial.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (engine.isRunning() && engine.canPauseNow()) {
                    if (onStopRequested != null) onStopRequested.run();
                } else if (!engine.isRunning() && engine.hasPausedSnapshot()) {
                    if (onStartRequested != null) onStartRequested.run();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { handleCloseRequest(); }
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                refreshPresentation();
                displayTimer.start();
            }
            @Override public void componentHidden(ComponentEvent e) { displayTimer.stop(); }
            @Override public void componentResized(ComponentEvent e) { adaptSidebar(); }
        });

        setRestartEnabled(false);
        settingsPage.refreshPreferenceFields();
        settingsPage.refreshSystemFields();
        showPage(PAGE_FOCUS);
        refreshButtons();
    }

    // Backward-compatible constructor used by existing QA/screenshots and callers.
    public ControlWindow(CycleEngine engine, Config config, PlanManager planManager,
                         Runnable onExit, Runnable onStartRequested, Runnable onStopRequested,
                         Runnable onRestartRequested, Runnable onChoosePlanRequested,
                         Consumer<Boolean> onToggleOverlay) {
        this(engine, config, planManager, null, onExit, onStartRequested, onStopRequested,
                onRestartRequested, onChoosePlanRequested, onToggleOverlay);
    }

    // Compatibility constructor used by older isolated QA tools.
    public ControlWindow(CycleEngine engine, Config config, PlanManager planManager,
                         Runnable onExit, Runnable onStartRequested, Runnable onStopRequested,
                         Runnable onRestartRequested, Consumer<Boolean> onToggleOverlay,
                         boolean ignoredOverlayInitial) {
        this(engine, config, planManager, null, onExit, onStartRequested, onStopRequested,
                onRestartRequested, () -> {}, onToggleOverlay);
    }

    private JPanel buildSidebar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.SIDEBAR);
        Theme.navigation(globalSettingsBtn, "settings");
        globalSettingsBtn.addActionListener(e -> openSettings(null));
        Theme.navigation(sidebarToggle, null);
        sidebarToggle.addActionListener(e -> {
            config.sidebarCollapsed = !sidebarCompact;
            sidebarForcedExpanded = !config.sidebarCollapsed;
            config.save(); rebuildSidebar(p, config.sidebarCollapsed);
        });
        rebuildSidebar(p, config.sidebarCollapsed);
        return p;
    }
    private void adaptSidebar() {
        if (sidebar == null) return;
        boolean compact = config.sidebarCollapsed || (!sidebarForcedExpanded && getWidth() < 1000);
        if (compact != sidebarCompact) rebuildSidebar(sidebar, compact);
    }
    private void rebuildSidebar(JPanel p, boolean compact) {
        sidebarCompact = compact;
        p.removeAll(); p.setPreferredSize(new Dimension(compact ? 70 : 192, 0));
        p.setBorder(new EmptyBorder(26, compact ? 10 : 18, 18, compact ? 10 : 18));
        if (compact) p.add(new JLabel(Icons.appIcon(34)), BorderLayout.NORTH);
        else p.add(Theme.brand(), BorderLayout.NORTH);
        JPanel nav = Theme.column(); nav.setBorder(new EmptyBorder(compact ? 38 : 44, 0, 0, 0));
        if (!compact) { nav.add(Theme.eyebrow("WORKSPACE")); nav.add(Box.createVerticalStrut(12)); }
        JButton[] main = {focusBtn, planBtn, reminderBtn, computerBtn};
        String[] titles = {com.eye.i18n.I18n.text("专注计时"), com.eye.i18n.I18n.text("计划管理"), com.eye.i18n.I18n.text("提醒计划"), com.eye.i18n.I18n.text("电脑使用")};
        for (int i=0;i<main.length;i++) { styleSideButton(main[i],titles[i],compact); nav.add(main[i]); nav.add(Box.createVerticalStrut(8)); }
        nav.add(Box.createVerticalGlue()); p.add(nav,BorderLayout.CENTER);
        JPanel footer = Theme.column();
        styleSideButton(globalSettingsBtn,com.eye.i18n.I18n.text("设置"),compact); footer.add(globalSettingsBtn); footer.add(Box.createVerticalStrut(8));
        sidebarToggle.setText(compact ? "›" : com.eye.i18n.I18n.text("‹  收起侧栏"));
        sidebarToggle.setHorizontalAlignment(compact ? SwingConstants.CENTER : SwingConstants.LEFT);
        sidebarToggle.setFont(Theme.font(compact ? 24 : 12));
        sidebarToggle.setToolTipText(compact ? com.eye.i18n.I18n.text("展开侧栏") : com.eye.i18n.I18n.text("收起侧栏"));
        sidebarToggle.setBorder(new EmptyBorder(8, compact ? 4 : 15, 8, compact ? 4 : 15));
        sidebarToggle.getAccessibleContext().setAccessibleName(sidebarToggle.getToolTipText());
        footer.add(sidebarToggle);
        p.add(footer,BorderLayout.SOUTH); p.revalidate(); p.repaint();
    }
    private void styleSideButton(JButton button, String text, boolean compact) {
        button.setText(compact ? "" : text); button.setToolTipText(compact ? text : null);
        button.getAccessibleContext().setAccessibleName(text);
        button.setHorizontalAlignment(compact ? SwingConstants.CENTER : SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(13,compact?4:15,13,compact?4:15));
        // Icon-only buttons do not use the gap. Keeping the expanded gap here also
        // prevents a first layout in compact mode from caching a zero gap on resize.
        button.setIconTextGap(9);
    }

    private JComponent buildFocusPage() {
        JPanel content = Theme.panel(new BorderLayout(0, 22));
        content.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));

        JPanel header = Theme.panel(new BorderLayout());
        JPanel headings = Theme.column();
        headings.add(Theme.eyebrow("YOUR DAILY RHYTHM"));
        headings.add(Box.createVerticalStrut(6));
        headings.add(Theme.heading(com.eye.i18n.I18n.text("专注当下，也关照自己。"), 25));
        headings.add(Box.createVerticalStrut(7));
        headings.add(Theme.label(com.eye.i18n.I18n.text("让工作有节奏，让眼睛有片刻留白。"), 12, Theme.SECONDARY));
        header.add(headings, BorderLayout.CENTER);
        dateLabel.setIcon(Icons.of("calendar", 16, Theme.SECONDARY));
        dateLabel.setIconTextGap(7);
        dateLabel.setVerticalAlignment(SwingConstants.TOP);
        JPanel headerActions = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JButton gear = SettingsUi.iconButton("settings",com.eye.i18n.I18n.text("设置"),"ghost"); gear.addActionListener(e->openSettings(null));
        headerActions.add(dateLabel); headerActions.add(gear); header.add(headerActions,BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH);

        JPanel workspace = Theme.panel(new BorderLayout(20, 0));
        FocusTimerSurface timerCard = new FocusTimerSurface();
        timerCard.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        JPanel timerTop = Theme.panel(new BorderLayout());
        JLabel timerTitle=Theme.heading(com.eye.i18n.I18n.text("专注计时"),14);
        timerTitle.setIcon(Icons.of("timer",17,Theme.GREEN));timerTitle.setIconTextGap(8);
        timerTop.add(timerTitle, BorderLayout.WEST);
        timerTop.add(stateLabel, BorderLayout.EAST);
        timerCard.add(timerTop, BorderLayout.NORTH);

        JPanel clockCenter = Theme.panel(new BorderLayout());
        clockCenter.add(dial, BorderLayout.CENTER);
        clockDescription.setHorizontalAlignment(SwingConstants.CENTER);
        clockDescription.setFont(Theme.font(11));
        clockCenter.add(clockDescription, BorderLayout.SOUTH);
        timerCard.add(clockCenter, BorderLayout.CENTER);

        JPanel controls = Theme.column();
        JPanel primaryActions = Theme.panel(new GridLayout(1, 2, 10, 0));
        primaryActions.add(startBtn);
        primaryActions.add(stopBtn);
        primaryActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        controls.add(Box.createVerticalStrut(14));
        controls.add(primaryActions);
        JPanel restartRow = Theme.panel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        restartRow.add(restartBtn);
        restartRow.add(restNowBtn);
        controls.add(Box.createVerticalStrut(3));
        controls.add(restartRow);
        timerCard.add(controls, BorderLayout.SOUTH);
        timerCard.setRegions(timerTop,controls);

        workspace.add(timerCard, BorderLayout.CENTER);
        workspace.add(buildDetails(), BorderLayout.EAST);
        content.add(workspace, BorderLayout.CENTER);

        // Preserve the exact focus-workspace height from the original UI.  The old footer
        // occupied this vertical footprint; its setting now lives under “系统设置”, but the
        // workspace must not grow into that space or the dial / right rail become oversized.
        // Keeping the footprint here restores the original proportions without duplicating UI.
        Component legacyFooterFootprint = Box.createVerticalStrut(48);
        content.add(legacyFooterFootprint, BorderLayout.SOUTH);

        // The dashboard has a fixed composition, unlike the scrollable list/statistics pages.
        // A tall window must not stretch the dial and the decorative card indefinitely.
        Theme.ResponsiveCanvas canvas = new Theme.ResponsiveCanvas(new BorderLayout() {
            @Override public void layoutContainer(Container parent) {
                super.layoutContainer(parent);
                int designHeight = (int)Math.round(701 * com.eye.ui.theme.ProportionalPanel.scaleOf(parent));
                content.setBounds(0, 0, parent.getWidth(), Math.min(parent.getHeight(), designHeight));
            }
        }, 690, 701);
        canvas.setBackground(Theme.BG);
        canvas.add(content, BorderLayout.CENTER);
        JScrollPane viewport = new JScrollPane(canvas);
        Theme.scroll(viewport);
        viewport.setBackground(Theme.BG);
        return viewport;
    }

    private JPanel buildDetails() {
        // Fill the entire right rail deliberately instead of pinning the first two cards to the
        // top and the quote card to the bottom.  The old BorderLayout approach created a large
        // dead band in the middle on 1080x740 windows.  These weights keep all three cards
        // visually connected while still aligning the rail exactly with the timer card.
        JPanel details = Theme.panel(new GridBagLayout());
        details.setPreferredSize(new Dimension(244, 0));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.BOTH;

        rhythmCard = Theme.card(new BorderLayout(0, 12));
        rhythmCard.setBorder(BorderFactory.createEmptyBorder(17, 20, 16, 20));
        JPanel rhythmHeader = Theme.panel(new BorderLayout());
        rhythmHeader.add(Theme.heading(com.eye.i18n.I18n.text("我的节奏"), 14), BorderLayout.WEST);
        JLabel rhythmHint = Theme.label(com.eye.i18n.I18n.text("点击调整"), 10, Theme.SECONDARY);
        rhythmHint.setIcon(Icons.of("settings", 12, Theme.SECONDARY));
        rhythmHint.setIconTextGap(4);
        rhythmHeader.add(rhythmHint, BorderLayout.EAST);
        rhythmCard.add(rhythmHeader, BorderLayout.NORTH);
        JPanel values = Theme.panel(new GridLayout(1, 2, 10, 0));
        values.add(metric(com.eye.i18n.I18n.text("工作 · 分钟"), workValue, "timer"));
        values.add(metric(com.eye.i18n.I18n.text("休息 · 分钟"), relaxValue, "leaf"));
        rhythmCard.add(values, BorderLayout.CENTER);
        modeValue.setIcon(Icons.of("shield", 15, Theme.GREEN));
        modeValue.setIconTextGap(8);
        modeValue.setFont(Theme.font(12).deriveFont(Font.BOLD));
        rhythmCard.add(modeValue, BorderLayout.SOUTH);
        rhythmCard.setPreferredSize(new Dimension(244, 150));
        rhythmCard.setMinimumSize(new Dimension(244, 150));
        rhythmCard.setToolTipText(null);
        makeClickable(rhythmCard, this::openQuickRhythmEditor);
        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 12, 0);
        details.add(rhythmCard, gc);

        currentPlanCard = Theme.card(new BorderLayout(0, 7));
        currentPlanCard.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        currentPlanCard.add(Theme.heading(com.eye.i18n.I18n.text("当前计划"), 14), BorderLayout.NORTH);
        JPanel plan = Theme.column();
        planName.setMaximumSize(new Dimension(204, 46));
        plan.add(planName);
        plan.add(Box.createVerticalStrut(4));
        plan.add(planDetail);
        plan.add(Box.createVerticalStrut(7));
        planProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
        plan.add(planProgress);
        currentPlanCard.add(plan, BorderLayout.CENTER);
        JLabel changeHint = Theme.label(com.eye.i18n.I18n.text("点击这里更换或选择任务"), 10, Theme.GREEN);
        changeHint.setIcon(Icons.of("swap", 13, Theme.GREEN));
        changeHint.setIconTextGap(6);
        currentPlanCard.add(changeHint, BorderLayout.SOUTH);
        currentPlanCard.setPreferredSize(new Dimension(244, 138));
        currentPlanCard.setMinimumSize(new Dimension(244, 138));
        makeClickable(currentPlanCard, () -> { if (onChoosePlanRequested != null) onChoosePlanRequested.run();tutorialPlanChooserClosed++; });
        gc.gridy = 1;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 12, 0);
        details.add(currentPlanCard, gc);

        gc.gridy = 2;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        details.add(rhythmPreview, gc);
        return details;
    }

    private void makeClickable(Theme.Surface surface, Runnable action) {
        surface.setFocusable(true);
        surface.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"open");
        surface.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"open");
        surface.getActionMap().put("open",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){action.run();}});
        surface.addFocusListener(new FocusAdapter(){
            @Override public void focusGained(FocusEvent e){surface.setOutline(Theme.GREEN);}
            @Override public void focusLost(FocusEvent e){surface.setOutline(Theme.LINE);}
        });
        attachClickable(surface, surface, action);
    }

    private void attachClickable(Component component, Theme.Surface surface, Runnable action) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if(SwingUtilities.isLeftMouseButton(e))action.run(); }
            @Override public void mouseEntered(MouseEvent e) { surface.setOutline(Theme.SAGE); }
            @Override public void mouseExited(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(component, e.getPoint(), surface);
                if (!surface.contains(p)) surface.setOutline(surface.hasFocus()?Theme.GREEN:Theme.LINE);
            }
        });
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) attachClickable(child, surface, action);
        }
    }

    private JPanel metric(String title, JLabel value, String icon) {
        JPanel p = Theme.panel(new BorderLayout(0,3));
        p.setBorder(new EmptyBorder(5,0,5,0));
        JLabel name = Theme.label(title, 10, Theme.SECONDARY);
        name.setIcon(Icons.of(icon, 12, Theme.SECONDARY));
        name.setIconTextGap(4);
        p.add(name,BorderLayout.NORTH);
        value.setFont(Theme.numbers(28));
        p.add(value,BorderLayout.CENTER);
        return p;
    }

    /** A steady, compact state chip. It follows the engine without introducing an animation timer. */
    private static final class FocusStateLabel extends JLabel {
        FocusStateLabel(){
            super(com.eye.i18n.I18n.text("尚未开始"));setFont(Theme.font(10));setForeground(Theme.GREEN);
            setBorder(new EmptyBorder(5,10,5,10));setIcon(Icons.of("dot",10,Theme.GREEN));setIconTextGap(5);
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);g.setColor(Theme.SURFACE);
            g.fillRoundRect(0,0,getWidth(),getHeight(),getHeight(),getHeight());
            g.setColor(Theme.alpha(getForeground(),9));g.fillRoundRect(0,0,getWidth(),getHeight(),getHeight(),getHeight());
            g.setColor(Theme.alpha(getForeground(),22));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,getHeight(),getHeight());
            g.dispose();super.paintComponent(graphics);
        }
    }

    /** Tonal focus surface. The existing header, clock and actions keep their exact layout. */
    private static final class FocusTimerSurface extends Theme.Surface {
        private JComponent header,footer;
        FocusTimerSurface(){super(new BorderLayout(0,8),Theme.SURFACE,Theme.LINE,18);}
        void setRegions(JComponent header,JComponent footer){this.header=header;this.footer=footer;}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);
            double scale=com.eye.ui.theme.ProportionalPanel.scaleOf(this),arc=18*scale;
            g.clip(new java.awt.geom.RoundRectangle2D.Double(1.5,1.5,getWidth()-3,getHeight()-3,arc,arc));
            g.setPaint(new GradientPaint(0,0,Theme.SURFACE,getWidth(),getHeight(),new Color(0xFAFCF9)));
            g.fillRect(0,0,getWidth(),getHeight());
            if(header!=null){
                int y=header.getY()+header.getHeight()+(int)Math.round(10*scale);
                g.setColor(Theme.LINE);g.drawLine((int)(24*scale),y,getWidth()-(int)(24*scale),y);
            }
            if(footer!=null){
                int y=footer.getY()-(int)Math.round(8*scale);
                g.setColor(Theme.LINE);g.drawLine((int)(24*scale),y,getWidth()-(int)(24*scale),y);
            }
            g.dispose();
        }
    }

    /** Actual task completion, separate from the countdown of the current work interval. */
    private static final class PlanProgress extends JComponent {
        private double fraction;
        PlanProgress(){setPreferredSize(new Dimension(100,4));setMaximumSize(new Dimension(Integer.MAX_VALUE,4));}
        void update(PlanItem plan){
            setVisible(plan!=null);
            // The active day's plannedSeconds already includes temporary extensions.
            int total=plan==null?0:Math.max(0,plan.plannedSeconds);
            double next=total==0?0:Math.max(0,Math.min(1,1-plan.remainingSeconds/(double)total));
            if(fraction!=next){fraction=next;repaint();}
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);int h=getHeight();
            g.setColor(Theme.MINT);g.fillRoundRect(0,0,getWidth(),h,h,h);
            g.setColor(Theme.GREEN);g.fillRoundRect(0,0,(int)Math.round(getWidth()*fraction),h,h,h);g.dispose();
        }
    }

    private JComponent viewport(JComponent content) {
        return viewport(content, 690);
    }

    private JComponent viewport(JComponent content, int canvasHeight) {
        Theme.ResponsiveCanvas canvas = new Theme.ResponsiveCanvas(new BorderLayout(), 788, canvasHeight);
        canvas.setBackground(Theme.BG);
        canvas.add(content, BorderLayout.CENTER);
        JScrollPane viewport = new JScrollPane(canvas);
        Theme.scroll(viewport);
        return viewport;
    }

    private long tutorialRhythmEditorClosed;
    private long tutorialPlanChooserClosed;
    public void openQuickRhythmEditor() {
        DurationInput quickWork = new DurationInput();
        DurationInput quickRelax = new DurationInput();
        DurationInput.Mode mode = config.timeInputMode == Config.TimeInputMode.WHEEL
                ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED;
        quickWork.setMode(mode);
        quickRelax.setMode(mode);
        quickWork.setSeconds(config.workSeconds);
        quickRelax.setSeconds(config.relaxSeconds);

        JPanel body = Theme.column();
        body.setBorder(new EmptyBorder(4, 4, 4, 4));
        JLabel note = Theme.label(com.eye.i18n.I18n.text("修改当前轮与之后的节奏；B / C 模式当前休息不变。"), 11, Theme.SECONDARY);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(note);
        body.add(Box.createVerticalStrut(14));

        Theme.Surface workCard = Theme.card(new BorderLayout(0, 8));
        workCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        workCard.add(Theme.heading(com.eye.i18n.I18n.text("工作时长"), 12), BorderLayout.NORTH);
        workCard.add(quickWork, BorderLayout.CENTER);
        workCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(workCard);
        body.add(Box.createVerticalStrut(10));

        Theme.Surface relaxCard = Theme.card(new BorderLayout(0, 8));
        relaxCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        relaxCard.add(Theme.heading(com.eye.i18n.I18n.text("休息时长"), 12), BorderLayout.NORTH);
        relaxCard.add(quickRelax, BorderLayout.CENTER);
        relaxCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(relaxCard);

        JRadioButton[] quickModes={new JRadioButton(com.eye.i18n.I18n.text("A · 温和模式")),new JRadioButton(com.eye.i18n.I18n.text("B · 遮罩模式")),new JRadioButton(com.eye.i18n.I18n.text("C · 严格模式"))};
        ButtonGroup modeGroup=new ButtonGroup();for(JRadioButton radio:quickModes){modeGroup.add(radio);radio.setOpaque(false);}
        quickModes[config.mode.ordinal()].setSelected(true);
        String[] modeHelp={"休息时关闭屏幕；你仍可以唤醒屏幕继续使用电脑。","休息时关闭屏幕；唤醒后用全屏遮罩阻止操作，直到休息结束。","休息时关闭屏幕；每次唤醒都会再次关闭，直到休息结束。"};
        JComponent[] modeRows=new JComponent[3];for(int i=0;i<3;i++){modeRows[i]=com.eye.ui.theme.SettingsUi.row(quickModes[i].getText(),com.eye.i18n.I18n.text(modeHelp[i]),quickModes[i]);quickModes[i].setText("");quickModes[i].getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text(new String[]{"A · 温和模式","B · 遮罩模式","C · 严格模式"}[i]));}
        JPanel modeCard=com.eye.ui.theme.SettingsUi.group(com.eye.i18n.I18n.text("休息模式"),modeRows);modeCard.setAlignmentX(Component.LEFT_ALIGNMENT);body.add(Box.createVerticalStrut(10));body.add(modeCard);
        quickWork.putClientProperty("tutorial.region",workCard);quickRelax.putClientProperty("tutorial.region",relaxCard);
        body.add(com.eye.tutorial.TutorialFormHelp.create(this,java.util.List.of(
            new com.eye.tutorial.TutorialFormHelp.Hint("先设置每轮工作时长。倒计时结束后进入休息；是否自动开始下一轮，可在设置中选择。",quickWork),
            new com.eye.tutorial.TutorialFormHelp.Hint("设置每轮休息时长。它与计划的总时长是两回事。",quickRelax),
            new com.eye.tutorial.TutorialFormHelp.Hint("选择 A、B 或 C，查看各模式说明，再保存。",modeCard))));

        Object[] options = {com.eye.i18n.I18n.text("保存"), com.eye.i18n.I18n.text("更多设置"), com.eye.i18n.I18n.text("取消")};
        int choice = showThemedOption(body, com.eye.i18n.I18n.text("调整我的节奏"), JOptionPane.PLAIN_MESSAGE, options, options[0]);
        tutorialRhythmEditorClosed++;
        if (choice == 1) {
            requestSettingsPage();
            return;
        }
        if (choice != 0) return;

        final int work, relax;
        try {
            work = quickWork.getSeconds();
            relax = quickRelax.getSeconds();
        } catch (IllegalArgumentException ex) {
            showThemedMessage(com.eye.i18n.I18n.text("工作时长和休息时长都必须大于 0 秒。"), com.eye.i18n.I18n.text("调整我的节奏"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        Config.ForceMode chosenMode=quickModes[0].isSelected()?Config.ForceMode.A_SOFT:quickModes[1].isSelected()?Config.ForceMode.B_STRONG:Config.ForceMode.C_ULTRA;
        boolean changed = work != config.workSeconds || relax != config.relaxSeconds || config.mode!=chosenMode;
        config.mode=chosenMode;
        engine.updateDurations(work, relax);
        boolean saved = config.save();
        settingsPage.refreshPreferenceFields();
        refreshPresentation();
        refreshButtons();
        if (!saved) {
            showThemedMessage(com.eye.i18n.I18n.text("本次时长已在当前运行中生效，但写入配置文件失败。"), com.eye.i18n.I18n.text("调整我的节奏"), JOptionPane.WARNING_MESSAGE);
        } else if (changed && engine.hasPausedSnapshot() && !engine.canResumeFromPause()) {
            showThemedMessage(com.eye.i18n.I18n.text("时长已保存。由于计时设置发生变化，下一次点击“开始”会按新时长开启新一轮。"), com.eye.i18n.I18n.text("调整我的节奏"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openSettings(String section) {
        if (!PAGE_SETTINGS.equals(activePage)) {
            returnFromSettings = activePage;
            settingsPage.refreshPreferenceFields(); settingsPage.refreshSystemFields();
        }
        showPage(PAGE_SETTINGS);
        if(section!=null) settingsPage.select(section);
        showWindow();
    }
    public void requestSettingsPage() { if(tutorial!=null&&tutorial.openModeSettings())return;openSettings("rhythm"); }
    public void showSystemSettingsPage() { openSettings("input"); }

    public void showPlanPage() {
        planPage.refresh();
        showPage(PAGE_PLANS);
        showWindow();
    }

    public void showReminderPage() {
        reminderPage.refresh();
        showPage(PAGE_REMINDERS);
        showWindow();
    }

    public void showFocusPage() {
        showPage(PAGE_FOCUS);
        showWindow();
    }

    public void refreshPlanPage() { planPage.refresh(); }

    private void showPage(String page) {
        if (PAGE_PREFERENCES.equals(page)) { page=PAGE_SETTINGS; settingsPage.select("rhythm"); }
        if (PAGE_SYSTEM.equals(page)) { page=PAGE_SETTINGS; settingsPage.select("input"); }
        if(tutorial!=null)tutorial.dismissTopicOffer();
        activePage = page;
        if(PAGE_REMINDERS.equals(page))offerTopicLater("reminders");else if(PAGE_COMPUTER.equals(page))offerTopicLater("computer");else if(PAGE_PLANS.equals(page))offerTopicLater("plans");
        globalSettingsBtn.putClientProperty("rest.selected",PAGE_SETTINGS.equals(page));globalSettingsBtn.repaint();
        pageLayout.show(pageCards, page);
        focusBtn.putClientProperty("rest.selected", PAGE_FOCUS.equals(page));
        planBtn.putClientProperty("rest.selected", PAGE_PLANS.equals(page));
        reminderBtn.putClientProperty("rest.selected", PAGE_REMINDERS.equals(page));
        computerBtn.putClientProperty("rest.selected", PAGE_COMPUTER.equals(page));computerBtn.repaint();
        preferencesBtn.putClientProperty("rest.selected", PAGE_PREFERENCES.equals(page));
        systemBtn.putClientProperty("rest.selected", PAGE_SYSTEM.equals(page));
        focusBtn.repaint(); planBtn.repaint(); reminderBtn.repaint(); preferencesBtn.repaint(); systemBtn.repaint();
    }

    public String getActivePage() { return activePage; }

    public void setOverlayChecked(boolean checked) { settingsPage.setOverlayChecked(checked); }

    public void setRestartEnabled(boolean enabled) {
        restartBtn.setVisible(true);
        restartBtn.setEnabled(true);
        restNowBtn.setVisible(true);
        restNowBtn.setEnabled(true);
    }

    public void showWindow() {
        if (!isVisible()) setVisible(true);
        int state = getExtendedState();
        if ((state & JFrame.ICONIFIED) != 0) setExtendedState(state & ~JFrame.ICONIFIED);
        toFront();
        requestFocus();
    }

    public void hideWindow() { setVisible(false); }

    public void refreshButtons() {
        boolean running = engine.isRunning();
        boolean reminderActive = engine.isReminderActive();
        boolean paused = !running && engine.hasPausedSnapshot();
        startBtn.setEnabled(!running && !reminderActive);
        startBtn.setText(paused ? com.eye.i18n.I18n.text("继续") : com.eye.i18n.I18n.text("开始"));
        startBtn.setIcon(Icons.of(paused ? "play" : "play", 17, null));
        stopBtn.setEnabled(engine.canPauseNow());
        setRestartEnabled(paused);
        refreshPresentation();
    }

    private void refreshPresentation() {
        settingsPage.updateUsageControls();
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MM / dd")));
        workValue.setText(formatMinutesSummary(config.workSeconds));
        relaxValue.setText(formatMinutesSummary(config.relaxSeconds));
        rhythmPreview.update(config.workSeconds,config.relaxSeconds,config.autoContinueCycles);
        modeValue.setText(switch (config.mode) {
            case A_SOFT -> com.eye.i18n.I18n.text("A · 温和模式");
            case B_STRONG -> com.eye.i18n.I18n.text("B · 遮罩模式");
            case C_ULTRA -> com.eye.i18n.I18n.text("C · 严格模式");
        });

        long total = config.workSeconds * 1_000L;
        if (engine.isReminderActive()) {
            dial.display(com.eye.i18n.I18n.text("提醒中"), com.eye.i18n.I18n.text("遮罩期间计入休息"), "REMINDER", 0, true);
            stateLabel.setText(com.eye.i18n.I18n.text("提醒中"));
            clockDescription.setText(com.eye.i18n.I18n.text("完成最低遮罩时间后，需要手动关闭提醒才能继续。"));
        } else if (engine.isRunning() && engine.isInWork()) {
            long ms = engine.getWorkRemainingMs();
            dial.display(formatClock(ms), com.eye.i18n.I18n.text("本轮工作剩余"), "FOCUS IN PROGRESS", total > 0 ? (double) ms / total : 1, false);
            stateLabel.setText(com.eye.i18n.I18n.text("工作中"));
            clockDescription.setText(com.eye.i18n.I18n.text("把注意力，留给眼前这一件事。"));
        } else if (engine.isRunning() && engine.isInRelax()) {
            // CycleEngine tracks rest time while the dial shows the rest state.
            dial.display(com.eye.i18n.I18n.text("休息中"), com.eye.i18n.I18n.text("把这一刻，留给自己"), "TIME TO REST", 1, true);
            stateLabel.setText(com.eye.i18n.I18n.text("休息中"));
            clockDescription.setText(com.eye.i18n.I18n.text("剩余休息时间可查看悬浮条或休息遮罩。"));
        } else if (engine.hasPausedSnapshot()) {
            boolean resumable = engine.canResumeFromPause();
            // The reference clock renders a paused dial without a progress arc.
            // Click-to-resume behavior remains wired to the dial mouse listener.
            dial.display(com.eye.i18n.I18n.text("已暂停"), resumable ? com.eye.i18n.I18n.text("点击开始，继续这一段") : com.eye.i18n.I18n.text("设置已变更，将开始新计时"), "TAKE YOUR TIME", 0, false);
            stateLabel.setText(com.eye.i18n.I18n.text("已暂停"));
            clockDescription.setText(resumable ? com.eye.i18n.I18n.text("暂停进度已保留，也可以重新开始。") : com.eye.i18n.I18n.text("设置已变更，点击开始将按新设置计时。"));
        } else {
            dial.display(total > 0 ? formatClock(total) : "--:--", com.eye.i18n.I18n.text("准备好，再开始"), "READY TO FOCUS", 1, false);
            stateLabel.setText(com.eye.i18n.I18n.text("尚未开始"));
            clockDescription.setText(com.eye.i18n.I18n.text("准备好后，开启一段专注时光。"));
        }
        Color stateColor=engine.isReminderActive()||engine.isInRelax()?Theme.AMBER:
                engine.hasPausedSnapshot()&&!engine.isRunning()?Theme.SECONDARY:Theme.GREEN;
        if(!stateColor.equals(stateLabel.getForeground())){
            stateLabel.setForeground(stateColor);stateLabel.setIcon(Icons.of("dot",10,stateColor));
        }

        PlanItem selected = planManager.get(engine.getCurrentPlanId());
        planProgress.update(selected);
        if (selected == null) {
            planName.setText(com.eye.i18n.I18n.text("自由专注"));
            planName.setToolTipText(null);
            planDetail.setText(com.eye.i18n.I18n.text("未关联工作计划"));
        } else {
            planName.setText(selected.name);
            planName.setToolTipText(selected.name);
            planDetail.setText(com.eye.i18n.I18n.text("剩余 ") + formatCompactDuration(selected.remainingSeconds));
        }
    }

    private static String formatClock(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * The summary card deliberately keeps the original two-digit whole-minute visual.
     * Engine/config precision remains seconds; only this presentation is minute-only.
     */
    private static String formatMinutesSummary(int seconds) {
        return String.format("%02d", Math.max(0, seconds) / 60);
    }

    private static String formatCompactDuration(int seconds) {
        int safe = Math.max(0, seconds);
        int h = safe / 3600;
        int m = (safe % 3600) / 60;
        int s = safe % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    private void handleCloseRequest() {
        if (config.closeAction == Config.CloseAction.MINIMIZE_TO_TRAY) {
            hideWindow();
            return;
        }
        if (config.closeAction == Config.CloseAction.EXIT) {
            if (onExit != null) onExit.run();
            return;
        }

        JCheckBox remember = new JCheckBox(com.eye.i18n.I18n.text("记住此选项（之后可在“系统设置”中重新调整）"));
        remember.setFont(Theme.font(12));
        JPanel message = Theme.column();
        JLabel text = Theme.label(com.eye.i18n.I18n.text("关闭主窗口时，你希望 EyeRelax 怎么做？"), 13, Theme.INK);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        remember.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.add(text);
        message.add(Box.createVerticalStrut(13));
        message.add(remember);

        Object[] options = {com.eye.i18n.I18n.text("最小化到托盘"), com.eye.i18n.I18n.text("退出程序"), com.eye.i18n.I18n.text("取消")};
        int choice = showThemedOption(message, com.eye.i18n.I18n.text("关闭 EyeRelax"), JOptionPane.PLAIN_MESSAGE, options, options[0]);
        if (choice == 0) {
            if (remember.isSelected()) {
                config.closeAction = Config.CloseAction.MINIMIZE_TO_TRAY;
                config.save();
                settingsPage.refreshSystemFields();
            }
            hideWindow();
        } else if (choice == 1) {
            if (remember.isSelected()) {
                config.closeAction = Config.CloseAction.EXIT;
                config.save();
                settingsPage.refreshSystemFields();
            }
            if (onExit != null) onExit.run();
        }
    }

    private int showThemedOption(Object message,String title,int type,Object[] options,Object initial){return ThemedDialogs.option(this,message,title,type,options,initial);}

    private void showThemedMessage(Object message,String title,int type){ThemedDialogs.message(this,message,title,type);}

    @Override public void dispose() {
        displayTimer.stop();
        super.dispose();
    }
}
