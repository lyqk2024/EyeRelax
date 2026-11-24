package com.eye.ui;

import com.eye.ui.theme.InlineErrors;
import com.eye.ui.theme.SettingsUi;

import com.eye.core.Config;
import com.eye.core.DataPaths;
import com.eye.core.UsageLimitMonitor;
import com.eye.win.StartupRegistration;
import com.eye.core.CycleEngine;
import com.eye.plan.PlanManager;
import com.eye.ui.theme.DurationInput;
import com.eye.ui.theme.Icons;
import com.eye.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/** Owns settings controls, validation and application; window navigation is supplied by callbacks. */
final class ApplicationSettingsPanel extends JPanel {
    private Runnable openApplicationLimits=()->{};
    public void setApplicationLimitsAction(Runnable action){openApplicationLimits=action;}
    // Preferences: time / plan behavior.
    private final DurationInput workDurationInput = new DurationInput();
    private final DurationInput relaxDurationInput = new DurationInput();
    private final JRadioButton modeA = new JRadioButton(com.eye.i18n.I18n.text("A · 温和：休息时关闭屏幕，允许继续使用电脑"));
    private final JRadioButton modeB = new JRadioButton(com.eye.i18n.I18n.text("B · 遮罩：唤醒后遮罩阻止操作，直到休息结束"));
    private final JRadioButton modeC = new JRadioButton(com.eye.i18n.I18n.text("C · 严格：唤醒后再次关闭屏幕，直到休息结束"));
    private final JCheckBox launchPlanChooserChk = new JCheckBox(com.eye.i18n.I18n.text("打开 EyeRelax 时，在主页面出现后弹出计划选择"));
    private final JCheckBox autoStartAfterPlanChk = new JCheckBox(com.eye.i18n.I18n.text("选择计划后自动开始（关闭时由我手动点击“开始”）"));
    private final JCheckBox autoContinueChk = new JCheckBox(com.eye.i18n.I18n.text("实际休息结束（且已解锁）后自动开始下一轮；未完成的当前任务继续沿用"));
    private final JCheckBox taskCompletionNotificationChk = new JCheckBox(com.eye.i18n.I18n.text("任务计划完成时显示通知并播放柔和提示音"));
    private final JLabel settingsStatus = Theme.label("", 11, Theme.SECONDARY);

    // System settings: visual / interaction behavior.
    private final JCheckBox overlayChk = new JCheckBox(com.eye.i18n.I18n.text("显示右上角悬浮计时器"));
    private final JCheckBox overlaySwitchChk = new JCheckBox(com.eye.i18n.I18n.text("悬浮计时器显示“切换”按钮"));
    private final JCheckBox overlayButtonTextChk = new JCheckBox(com.eye.i18n.I18n.text("悬浮计时器显示“暂停 / 继续 / 切换”文字（关闭后仅显示图标）"));
    private final JComboBox<String> languageBox=new JComboBox<>(new String[]{com.eye.i18n.I18n.text("简体中文"),"English"});
    private final JComboBox<String> timeInputModeBox = new JComboBox<>(new String[]{com.eye.i18n.I18n.text("分栏输入 · 小时 / 分钟 / 秒"), com.eye.i18n.I18n.text("滚轮选择 · 仿闹钟拨轮")});
    private final JComboBox<String> closeActionBox = new JComboBox<>(new String[]{com.eye.i18n.I18n.text("每次询问"), com.eye.i18n.I18n.text("关闭窗口时最小化到托盘"), com.eye.i18n.I18n.text("关闭窗口时退出程序")});
    private final JComboBox<String> planSelectionActionBox = new JComboBox<>(new String[]{com.eye.i18n.I18n.text("只设为当前计划 · 手动开始"), com.eye.i18n.I18n.text("设为当前计划并开始")});
    private final JComboBox<String> historyDateInputBox = new JComboBox<>(new String[]{com.eye.i18n.I18n.text("日历选择 · 默认"), com.eye.i18n.I18n.text("手动输入日期 · 也可打开日历")});
    private final JCheckBox launchAtLoginChk = new JCheckBox(com.eye.i18n.I18n.text("登录 Windows 后自动启动 EyeRelax"));
    private UsageLimitMonitor usageMonitor;
    private final JCheckBox dailyUsageLimitChk = new JCheckBox(com.eye.i18n.I18n.text("限制每日电脑使用时长"));
    private final JCheckBox usageWindowChk = new JCheckBox(com.eye.i18n.I18n.text("限制允许使用时段"));
    private final DurationInput usageBudgetInput = DurationInput.forDailyBudget();
    private final DurationInput usageWindowStartInput = DurationInput.forTimeOfDay(false);
    private final DurationInput usageWindowEndInput = DurationInput.forTimeOfDay(false);
    private final JLabel usageSummary = Theme.label(com.eye.i18n.I18n.text("今日使用：—"),12,Theme.GREEN);
    private final JCheckBox usageGuardChk=new JCheckBox();
    private UpdateSettingsPanel updateSettingsPanel;
    private boolean configuringGuard;
    public void startUpdateChecks(){if(updateSettingsPanel!=null)updateSettingsPanel.startAutomaticChecks();}
    public void setUsageMonitor(UsageLimitMonitor monitor) {usageMonitor=monitor;}
    private final JSpinner dayBoundaryHour = new JSpinner(new SpinnerNumberModel(4, 0, 23, 1));
    private final JSpinner dayBoundaryMinute = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
    private final JButton dayBoundaryEditBtn = new JButton();
    private final JCheckBox resetAfterLongPauseChk = new JCheckBox();
    private final DurationInput longPauseResetInput = new DurationInput();
    private final JButton longPauseResetEditBtn = new JButton();
    private final JTextField dataDirectoryField = new JTextField();
    private final JButton dataDirectoryBtn = new JButton(com.eye.i18n.I18n.text("更改位置…"));
    private SettingsUi.View systemSections;
    private SettingsUi.View preferenceSections;
    private final JLabel systemStatus = Theme.label("", 11, Theme.SECONDARY);

    private SettingsUi.View unifiedSettings;
    private Runnable tutorialAction;
    private java.util.function.Consumer<String> topicAction;
    boolean tutorialLimitsSelected(){return "usage-limits".equals(unifiedSettings.selected());}
    boolean tutorialSectionSelected(String id){return id.equals(unifiedSettings.selected());}
    private JButton applicationLimitsButton;
    private JComponent applicationLimitBehavior;
    private LocalStrictPanel localStrictPanel;
    JComponent tutorialSettingTarget(String topic,String key){return "strict".equals(topic)?localStrictPanel.tutorialTarget(key):"manage".equals(key)?applicationLimitsButton:applicationLimitBehavior;}
    void setTopicAction(java.util.function.Consumer<String> action){topicAction=action;}
    void setTutorialSelectionListener(java.util.function.Consumer<String> listener){unifiedSettings.setSelectionListener(listener);}
    JComponent tutorialLimitTarget(String key){return switch(key){case "enable"->dailyUsageLimitChk;case "budget"->usageBudgetInput;case "window"->usageWindowChk;case "times"->usageWindowStartInput;default->tutorialSaveTarget();};}
    void setTutorialAction(Runnable action){tutorialAction=action;}
    private final JButton reminderDefaultEditBtn = new JButton();
    private int stagedReminderDefaultSeconds;

    private final Config config;
    private final CycleEngine engine;
    private final PlanManager planManager;
    private final Runnable onExit,onBack,refreshFocus,refreshPlans,refreshReminders;
    private final Consumer<Boolean> onToggleOverlay;
    ApplicationSettingsPanel(Config config,CycleEngine engine,PlanManager planManager,Runnable onExit,Consumer<Boolean> onToggleOverlay,
            Runnable onBack,Runnable refreshFocus,Runnable refreshPlans,Runnable refreshReminders){
        super(new BorderLayout());setOpaque(false);this.config=config;this.engine=engine;this.planManager=planManager;
        this.onExit=onExit;this.onToggleOverlay=onToggleOverlay;this.onBack=onBack;this.refreshFocus=refreshFocus;
        this.refreshPlans=refreshPlans;this.refreshReminders=refreshReminders;
        Theme.smallButton(dayBoundaryEditBtn, "secondary", "clock");
        Theme.smallButton(longPauseResetEditBtn, "secondary", "clock");
        dayBoundaryEditBtn.setPreferredSize(new Dimension(124, 34));
        longPauseResetEditBtn.setPreferredSize(new Dimension(124, 34));
        resetAfterLongPauseChk.setToolTipText(null);
        dayBoundaryEditBtn.addActionListener(e -> editDayBoundary());
        longPauseResetEditBtn.addActionListener(e -> editLongPauseResetThreshold());

        add(buildSettingsPage(),BorderLayout.CENTER);
    }
    void select(String section){unifiedSettings.select(section);}
    private JButton tutorialSaveButton;
    JComponent tutorialSaveTarget(){return tutorialSaveButton;}
    JComponent tutorialRhythmTarget(){return unifiedSettings.selectedContent();}
    java.util.List<com.eye.tutorial.TutorialController.Step> tutorialRhythmHints(){return java.util.List.of(
        new com.eye.tutorial.TutorialController.Step("先设置每轮工作时长。倒计时结束后进入休息；是否自动开始下一轮，可在设置中选择。",()->workDurationInput,()->{},null),
        new com.eye.tutorial.TutorialController.Step("设置每轮休息时长。它与计划的总时长是两回事。",()->relaxDurationInput,()->{},null),
        new com.eye.tutorial.TutorialController.Step("A 可继续使用；B 遮罩阻止操作；C 唤醒后再次关屏。选择适合你的模式，再保存并返回教程。",()->(JComponent)modeA.getParent().getParent(),()->{},null));}
    boolean saveTutorialRhythm(){
        boolean valid=com.eye.ui.theme.InlineErrors.number(workDurationInput,workDurationInput::getSeconds,1,Integer.MAX_VALUE,com.eye.i18n.I18n.text("工作时长和休息时长都必须大于 0 秒。"));
        valid=com.eye.ui.theme.InlineErrors.number(relaxDurationInput,relaxDurationInput::getSeconds,1,Integer.MAX_VALUE,com.eye.i18n.I18n.text("工作时长和休息时长都必须大于 0 秒。"))&&valid;
        if(!valid)return false;
        applyPreferences();return Theme.GREEN.equals(settingsStatus.getForeground());
    }
    void setOverlayChecked(boolean checked){overlayChk.setSelected(checked);}
    private int showThemedOption(Object message,String title,int type,Object[] options,Object initial){return ThemedDialogs.option(this,message,title,type,options,initial);}
    private void showThemedMessage(Object message,String title,int type){ThemedDialogs.message(this,message,title,type);}
    private JComponent buildSettingsPage() {
        JButton save = new JButton(com.eye.i18n.I18n.text("保存设置")); Theme.button(save,"primary","check");
        tutorialSaveButton=save;save.addActionListener(e -> applyAllSettings());
        unifiedSettings = new SettingsUi.View(com.eye.i18n.I18n.text("设置"), com.eye.i18n.I18n.text("让 EyeRelax 更适合你的节奏。"), systemStatus, save);
        unifiedSettings.backAction(() -> onBack.run());
        unifiedSettings.divider(com.eye.i18n.I18n.text("个人偏好"));
        buildPreferencesPage();
        reminderDefaultEditBtn.setText(com.eye.i18n.I18n.text("1 分钟  ›")); SettingsUi.valueButton(reminderDefaultEditBtn,"clock");
        reminderDefaultEditBtn.addActionListener(e -> {
            DurationInput input = new DurationInput();
            input.setMode(timeInputModeBox.getSelectedIndex()==1 ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED);
            input.setSeconds(stagedReminderDefaultSeconds);
            if (SettingsUi.confirm(this,com.eye.i18n.I18n.text("默认提醒遮罩时长"),com.eye.i18n.I18n.text("至少 5 秒；提醒也可以单独设置。"),input,com.eye.i18n.I18n.text("确定"),()->InlineErrors.number(input,input::getSeconds,5,Integer.MAX_VALUE,com.eye.i18n.I18n.text("遮罩时长至少 5 秒。")))) {
                try { int v=input.getSeconds(); if(v<5)throw new IllegalArgumentException();
                    stagedReminderDefaultSeconds=v;reminderDefaultEditBtn.setText(formatFriendlyDuration(v)+"  ›");
                } catch(IllegalArgumentException ex) { showThemedMessage(com.eye.i18n.I18n.text("遮罩时长至少 5 秒。"),com.eye.i18n.I18n.text("提醒设置"),JOptionPane.WARNING_MESSAGE); }
            }
        });
        unifiedSettings.section("reminder-settings",com.eye.i18n.I18n.text("提醒"),"bell",com.eye.i18n.I18n.text("提醒到时暂停工作；手动关闭后才继续。"),
                SettingsUi.group(com.eye.i18n.I18n.text("提醒遮罩"),SettingsUi.row(com.eye.i18n.I18n.text("默认遮罩时长"),com.eye.i18n.I18n.text("单个提醒的独立时长优先；已触发的提醒不受更改影响。"),reminderDefaultEditBtn)));
        unifiedSettings.divider(com.eye.i18n.I18n.text("设备与系统"));
        buildSystemPage();
        unifiedSettings.divider(com.eye.i18n.I18n.text("账户与帮助"));
        JButton tutorialButton=new JButton(com.eye.i18n.I18n.text("查看使用教程"));Theme.smallButton(tutorialButton,"secondary","play");tutorialButton.addActionListener(e->{if(tutorialAction!=null)tutorialAction.run();});
        JPanel topicButtons=Theme.panel(new java.awt.GridLayout(0,2,10,10));
        for(var topic:com.eye.tutorial.FeatureTutorials.topics()){
            String id=topic.id();JButton b=new JButton(com.eye.i18n.I18n.text(topic.name()));Theme.button(b,"secondary",topic.icon());
            b.setHorizontalAlignment(SwingConstants.LEFT);b.setIconTextGap(12);b.setBorder(new EmptyBorder(15,16,15,12));
            b.addActionListener(e->{if(topicAction!=null)topicAction.accept(id);});topicButtons.add(b);
        }
        unifiedSettings.section("tutorial",com.eye.i18n.I18n.text("使用教程"),"book",com.eye.i18n.I18n.text("选择想了解的功能，随时可以跳过。"),SettingsUi.group(com.eye.i18n.I18n.text("新手教程"),SettingsUi.row(com.eye.i18n.I18n.text("重新查看"),"",tutorialButton)),SettingsUi.group(com.eye.i18n.I18n.text("功能教程"),topicButtons));
        unifiedSettings.section("updates",com.eye.i18n.I18n.text("版本与更新"),"download",com.eye.i18n.I18n.text("检查发布版本，下载并通过安装向导更新。"),updateSettingsPanel);
        unifiedSettings.section("sync",com.eye.i18n.I18n.text("账户与同步"),"swap",com.eye.i18n.I18n.text("计划和提醒可跨设备同步。用量和应用限制按设备区分。"),new CloudSettingsPanel());
        unifiedSettings.section("about",com.eye.i18n.I18n.text("关于 EyeRelax"),"info",com.eye.i18n.I18n.text("好好专注，也好好休息。"),
                SettingsUi.group("EyeRelax "+com.eye.app.AppVersion.CURRENT,new SettingsUi.Text(com.eye.i18n.I18n.text("Windows 桌面版"),12,Theme.SECONDARY)));
        return unifiedSettings;
    }

    private boolean strictPreflight(boolean system) {
        if (!com.eye.sync.StrictSettings.locked()) return true;
        try {
            Config next=com.eye.sync.StrictSettings.copy(config);
            next.workSeconds=workDurationInput.getSeconds();next.relaxSeconds=relaxDurationInput.getSeconds();
            next.mode=modeA.isSelected()?Config.ForceMode.A_SOFT:modeB.isSelected()?Config.ForceMode.B_STRONG:Config.ForceMode.C_ULTRA;
            next.showPlanChooserOnLaunch=launchPlanChooserChk.isSelected();next.autoStartAfterPlanSelection=autoStartAfterPlanChk.isSelected();next.autoContinueCycles=autoContinueChk.isSelected();next.showTaskCompletionNotification=taskCompletionNotificationChk.isSelected();
            if(system){
                next.dailyUsageLimitSeconds=usageBudgetSeconds();next.usageWindowStartMinute=usageWindowStartInput.getTimeOfDaySeconds()/60;next.usageWindowEndMinute=usageWindowEndInput.getTimeOfDaySeconds()/60;
                next.dailyUsageLimitEnabled=dailyUsageLimitChk.isSelected();next.usageWindowEnabled=usageWindowChk.isSelected();next.launchAtLogin=next.hasUsageLimits()||next.hasApplicationLimits()||com.eye.sync.StrictSettings.locked()||launchAtLoginChk.isSelected();next.usageGuardEnabled=usageGuardChk.isSelected();
                next.showStatusOverlay=overlayChk.isSelected();next.overlayShowSwitchButton=overlaySwitchChk.isSelected();next.overlayShowButtonText=overlayButtonTextChk.isSelected();next.language=languageBox.getSelectedIndex()==1?"en":"zh-CN";
                next.timeInputMode=timeInputModeBox.getSelectedIndex()==1?Config.TimeInputMode.WHEEL:Config.TimeInputMode.SEGMENTED;
                next.closeAction=switch(closeActionBox.getSelectedIndex()){case 1->Config.CloseAction.MINIMIZE_TO_TRAY;case 2->Config.CloseAction.EXIT;default->Config.CloseAction.ASK;};
                next.planManagementSelectionAction=planSelectionActionBox.getSelectedIndex()==1?Config.PlanSelectionAction.SELECT_AND_START:Config.PlanSelectionAction.SELECT_ONLY;
                next.historyDateInput=historyDateInputBox.getSelectedIndex()==1?Config.HistoryDateInput.MANUAL:Config.HistoryDateInput.CALENDAR;
                next.dayBoundarySeconds=(((Integer)dayBoundaryHour.getValue())*60+(Integer)dayBoundaryMinute.getValue())*60;next.resetAfterLongPause=resetAfterLongPauseChk.isSelected();next.longPauseResetSeconds=Math.max(60,longPauseResetInput.getSeconds());next.reminderDefaultMaskSeconds=stagedReminderDefaultSeconds;
                updateSettingsPanel.applyTo(next);
            }
            if(com.eye.sync.StrictSettings.allow(next))return true;
        }catch(RuntimeException invalid){ }
        systemStatus.setForeground(Theme.DANGER);settingsStatus.setForeground(Theme.DANGER);systemStatus.setText(com.eye.sync.StrictSettings.MESSAGE);settingsStatus.setText(com.eye.sync.StrictSettings.MESSAGE);return false;
    }

    private void applyAllSettings() {
        if (!strictPreflight(true)) return;
        if (!validateUsageFields()) return;
        try {updateSettingsPanel.validateSettings();}catch(RuntimeException ex){systemStatus.setForeground(Theme.DANGER);systemStatus.setText(ex.getMessage());return;}
        boolean desiredGuard=usageGuardChk.isSelected();
        if(desiredGuard&&!dailyUsageLimitChk.isSelected()&&!usageWindowChk.isSelected()){systemStatus.setForeground(Theme.DANGER);systemStatus.setText(com.eye.i18n.I18n.text("运行保护需同时启用每日额度或允许时段限制。"));return;}
        try {workDurationInput.getSeconds();relaxDurationInput.getSeconds();longPauseResetInput.getSeconds();}
        catch(IllegalArgumentException ex){systemStatus.setForeground(Theme.DANGER);systemStatus.setText(com.eye.i18n.I18n.text("请检查时长，均须大于 0 秒。"));return;}
        // Confirm the required login entry before creating any administrator-owned watchdog.
        if(desiredGuard!=config.usageGuardEnabled && desiredGuard && !StartupRegistration.isEnabled()){
            try{StartupRegistration.apply(true);}catch(RuntimeException ex){systemStatus.setForeground(Theme.DANGER);systemStatus.setText(com.eye.i18n.I18n.text("未能确认登录启动入口，运行保护尚未启用：")+ex.getMessage());return;}
        }
        if(desiredGuard!=config.usageGuardEnabled){
            if(configuringGuard)return;configuringGuard=true;systemStatus.setText(com.eye.i18n.I18n.text("正在配置 Windows 守护，需要管理员授权…"));usageGuardChk.setEnabled(false);
            new SwingWorker<Void,Void>(){
                protected Void doInBackground(){com.eye.win.UsageGuard.apply(desiredGuard);return null;}
                protected void done(){configuringGuard=false;usageGuardChk.setEnabled(true);try{get();config.usageGuardEnabled=desiredGuard;config.usageGuardAccountSid=desiredGuard?com.eye.win.UsageGuard.currentSid():"";applyAllSettings();}catch(Exception ex){usageGuardChk.setSelected(config.usageGuardEnabled);systemStatus.setForeground(Theme.DANGER);systemStatus.setText(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage());}}
            }.execute();return;
        }
        try { workDurationInput.getSeconds(); relaxDurationInput.getSeconds(); longPauseResetInput.getSeconds(); }
        catch(IllegalArgumentException ex) { systemStatus.setForeground(Theme.DANGER);systemStatus.setText(com.eye.i18n.I18n.text("请检查时长，均须大于 0 秒。"));return; }
        applyPreferences();
        config.reminderDefaultMaskSeconds = stagedReminderDefaultSeconds;
        applySystemSettings();
        if (settingsStatus.getForeground().equals(Theme.DANGER)) {
            systemStatus.setForeground(Theme.DANGER);systemStatus.setText(settingsStatus.getText());
        }
        refreshReminders.run();
    }

    private JComponent buildPreferencesPage() {
        ButtonGroup modes = new ButtonGroup(); modes.add(modeA); modes.add(modeB); modes.add(modeC);
        JButton apply = new JButton(com.eye.i18n.I18n.text("保存并应用")); Theme.button(apply, "primary", "check");
        apply.addActionListener(e -> applyPreferences());
        preferenceSections = unifiedSettings != null ? unifiedSettings : new SettingsUi.View(com.eye.i18n.I18n.text("设置"), "", settingsStatus, apply);

        JPanel durations = Theme.panel(new GridLayout(1, 2, 12, 0));
        durations.add(preferenceDuration(com.eye.i18n.I18n.text("工作时长"), "timer", workDurationInput));
        durations.add(preferenceDuration(com.eye.i18n.I18n.text("休息时长"), "leaf", relaxDurationInput));
        SettingsUi.Column modeChoices=new SettingsUi.Column();
        JLabel modeTitle=Theme.heading(com.eye.i18n.I18n.text("休息模式"),12);modeChoices.add(modeTitle);modeChoices.add(Box.createVerticalStrut(10));
        modeChoices.add(modeRow(modeA,com.eye.i18n.I18n.text("A · 温和模式"),com.eye.i18n.I18n.text("休息时关闭屏幕；你仍可以唤醒屏幕继续使用电脑。")));
        modeChoices.add(Box.createVerticalStrut(8));
        modeChoices.add(modeRow(modeB,com.eye.i18n.I18n.text("B · 遮罩模式"),com.eye.i18n.I18n.text("休息时关闭屏幕；唤醒后用全屏遮罩阻止操作，直到休息结束。")));
        modeChoices.add(Box.createVerticalStrut(8));
        modeChoices.add(modeRow(modeC,com.eye.i18n.I18n.text("C · 严格模式"),com.eye.i18n.I18n.text("休息时关闭屏幕；每次唤醒都会再次关闭，直到休息结束。")));
        preferenceSections.section("rhythm", com.eye.i18n.I18n.text("专注节奏"), "timer", com.eye.i18n.I18n.text("运行中也能调整。B / C 模式的当前休息不变，新时长从下一轮生效。"),
                durations,modeChoices);

        SettingsUi.toggle(launchPlanChooserChk, com.eye.i18n.I18n.text("启动时选择计划"));
        SettingsUi.toggle(autoStartAfterPlanChk, com.eye.i18n.I18n.text("选择计划后自动开始"));
        SettingsUi.toggle(autoContinueChk, com.eye.i18n.I18n.text("休息后自动继续"));
        preferenceSections.section("plans", com.eye.i18n.I18n.text("计划衔接"), "calendar", com.eye.i18n.I18n.text("选择打开应用、选好计划和休息结束时如何开始专注。"),
                SettingsUi.group(com.eye.i18n.I18n.text("启动与衔接"),
                        SettingsUi.row(com.eye.i18n.I18n.text("启动时选择计划"), "", launchPlanChooserChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("选好计划自动开始"), com.eye.i18n.I18n.text("关闭后，由你手动开始计时。"), autoStartAfterPlanChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("休息后自动继续"), com.eye.i18n.I18n.text("实际休息结束且已解锁后，继续未完成的任务。"), autoContinueChk)));
        SettingsUi.toggle(taskCompletionNotificationChk, com.eye.i18n.I18n.text("任务完成提醒"));
        preferenceSections.section("notifications", com.eye.i18n.I18n.text("通知提示"), "message", "",
                SettingsUi.group(com.eye.i18n.I18n.text("计划通知"), SettingsUi.row(com.eye.i18n.I18n.text("任务完成提醒"), com.eye.i18n.I18n.text("弹出通知并播放柔和提示音。"), taskCompletionNotificationChk)));
        return preferenceSections;
    }

    private JComponent preferenceDuration(String title, String icon, DurationInput input) {
        Theme.Surface p = new Theme.Surface(new BorderLayout(0, 16),"leaf".equals(icon)?Theme.MINT:Theme.SURFACE,Theme.LINE,14);
        p.setBorder(new EmptyBorder(16,16,16,16));
        JLabel label = Theme.heading(title, 12);
        label.setIcon(Icons.of(icon, 18, Theme.GREEN)); label.setIconTextGap(8); label.setLabelFor(input);
        input.putClientProperty("tutorial.region",p);
        p.add(label, BorderLayout.NORTH); p.add(input, BorderLayout.CENTER); return p;
    }

    private JComponent modeRow(JRadioButton radio, String title, String help) {
        radio.setToolTipText(null); radio.setText(""); radio.setOpaque(false);
        radio.setPreferredSize(new Dimension(30, 28));
        radio.getAccessibleContext().setAccessibleName(title);
        SettingsUi.Text description=new SettingsUi.Text(help,11,Theme.SECONDARY);
        JPanel row=new JPanel(new BorderLayout(12,0)){
            {setOpaque(false);}
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);double arc=14*com.eye.ui.theme.ProportionalPanel.scaleOf(this);
                g.setColor(radio.isSelected()?Theme.MINT:Theme.SURFACE);g.fill(new java.awt.geom.RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,arc,arc));
                g.setColor(radio.isSelected()?Theme.SAGE:Theme.LINE);g.draw(new java.awt.geom.RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,arc,arc));g.dispose();super.paintComponent(graphics);
            }
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        };
        row.setBorder(new EmptyBorder(14,16,14,12));
        JLabel letter=Theme.heading(title.substring(0,1),18);letter.setForeground(Theme.GREEN);letter.setPreferredSize(new Dimension(28,24));
        JPanel letterWrap=Theme.panel(new GridBagLayout());letterWrap.add(letter);row.add(letterWrap,BorderLayout.WEST);
        SettingsUi.Column copy=new SettingsUi.Column();
        String name=title.indexOf('·')>=0?title.substring(title.indexOf('·')+1).strip():title;
        copy.add(Theme.heading(name,12));copy.add(Box.createVerticalStrut(5));copy.add(description);row.add(copy);
        JPanel control=Theme.panel(new GridBagLayout());control.add(radio);row.add(control,BorderLayout.EAST);radio.putClientProperty("tutorial.region",row);
        radio.addItemListener(e->row.repaint());
        java.awt.event.MouseAdapter choose=new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){if(radio.isEnabled())radio.doClick();}};
        attachModeChoice(row,radio,choose);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return row;
    }
    private static void attachModeChoice(Component component,JRadioButton radio,MouseAdapter choose){
        // Every visible part of the option selects it; the native radio retains its own
        // keyboard and mouse handling so a click never toggles the model twice.
        if(component==radio)return;
        component.addMouseListener(choose);
        if(component instanceof Container parent)for(Component child:parent.getComponents())attachModeChoice(child,radio,choose);
    }

    private JComponent buildSystemPage() {
        JButton apply = new JButton(com.eye.i18n.I18n.text("保存系统设置")); Theme.button(apply, "primary", "check");
        apply.addActionListener(e -> applySystemSettings());
        systemSections = unifiedSettings != null ? unifiedSettings : new SettingsUi.View(com.eye.i18n.I18n.text("设置"), "", systemStatus, apply);
        timeInputModeBox.setModel(new DefaultComboBoxModel<>(new String[]{com.eye.i18n.I18n.text("分栏输入 · 时 / 分 / 秒"), com.eye.i18n.I18n.text("滚轮选择 · 闹钟拨轮")}));
        planSelectionActionBox.setModel(new DefaultComboBoxModel<>(new String[]{com.eye.i18n.I18n.text("设为当前计划，手动开始"), com.eye.i18n.I18n.text("设为当前计划并开始")}));
        historyDateInputBox.setModel(new DefaultComboBoxModel<>(new String[]{com.eye.i18n.I18n.text("日历选择"), com.eye.i18n.I18n.text("手动输入，也可打开日历")}));
        closeActionBox.setModel(new DefaultComboBoxModel<>(new String[]{com.eye.i18n.I18n.text("每次询问"), com.eye.i18n.I18n.text("最小化到托盘"), com.eye.i18n.I18n.text("退出程序")}));
        SettingsUi.combo(timeInputModeBox, 224); SettingsUi.combo(planSelectionActionBox, 224);
        SettingsUi.combo(historyDateInputBox, 224); SettingsUi.combo(closeActionBox, 224);
        SettingsUi.combo(languageBox,224);
        systemSections.section("language",com.eye.i18n.I18n.text("语言"),"language",com.eye.i18n.I18n.text("更改后需重新启动 EyeRelax。"),
                SettingsUi.group(com.eye.i18n.I18n.text("显示语言"),SettingsUi.row(com.eye.i18n.I18n.text("界面语言"),"",languageBox)));
        systemSections.section("input", com.eye.i18n.I18n.text("输入与操作"), "settings", com.eye.i18n.I18n.text("选择习惯的时间和日期输入方式。"),
                SettingsUi.group(com.eye.i18n.I18n.text("输入习惯"),
                        SettingsUi.row(com.eye.i18n.I18n.text("时间输入方式"), com.eye.i18n.I18n.text("工作、休息、学习日与提醒共用。"), timeInputModeBox),
                        SettingsUi.row(com.eye.i18n.I18n.text("历史日期输入"), com.eye.i18n.I18n.text("过去计划中的起止日期。"), historyDateInputBox)),
                SettingsUi.group(com.eye.i18n.I18n.text("计划操作"), SettingsUi.row(com.eye.i18n.I18n.text("选择当前计划"), com.eye.i18n.I18n.text("全局“选好计划自动开始”优先；关闭后按这里的选择。"), planSelectionActionBox)));

        SettingsUi.toggle(launchAtLoginChk, com.eye.i18n.I18n.text("登录 Windows 后自动启动"));
        JButton openStartup = new JButton(com.eye.i18n.I18n.text("打开启动文件夹"));
        JButton checkStartup = new JButton(com.eye.i18n.I18n.text("检查启动入口"));
        Theme.smallButton(openStartup,"secondary","folder");
        Theme.smallButton(checkStartup,"secondary","check");
        openStartup.addActionListener(e -> {
            try { java.awt.Desktop.getDesktop().open(StartupRegistration.startupFolder().toFile()); }
            catch (Exception ex) { systemStatus.setForeground(Theme.DANGER); systemStatus.setText(com.eye.i18n.I18n.text("无法打开启动文件夹：") + ex.getMessage()); }
        });
        checkStartup.addActionListener(e -> {
            boolean found = StartupRegistration.isEnabled();
            launchAtLoginChk.setSelected(found);
            systemStatus.setForeground(found ? Theme.GREEN : Theme.DANGER);
            systemStatus.setText(found ? com.eye.i18n.I18n.text("已启用登录启动。") : com.eye.i18n.I18n.text("请将 EyeRelax.exe 的快捷方式放入启动文件夹，然后重新检查。"));
        });
        JPanel startupActions = new JPanel(new GridLayout(1,2,8,0));
        startupActions.setOpaque(false); startupActions.add(openStartup); startupActions.add(checkStartup);
        systemSections.section("startup", com.eye.i18n.I18n.text("启动与窗口"), "play", com.eye.i18n.I18n.text("管理登录启动与主窗口的关闭方式。"),
                SettingsUi.group(com.eye.i18n.I18n.text("启动与退出"),
                        SettingsUi.row(com.eye.i18n.I18n.text("登录后自动启动"), "", launchAtLoginChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("手动管理启动入口"), com.eye.i18n.I18n.text("使用指向 EyeRelax.exe 的快捷方式。"), startupActions),
                        SettingsUi.row(com.eye.i18n.I18n.text("关闭主窗口时"), com.eye.i18n.I18n.text("选择保留在托盘，或退出程序。"), closeActionBox)),
                SettingsUi.group(com.eye.i18n.I18n.text("状态恢复"), new SettingsUi.Text(com.eye.i18n.I18n.text("工作进度恢复后保持暂停；未完成的休息按原设置执行。关机期间不扣除剩余休息。"), 11, Theme.SECONDARY)));

        SettingsUi.toggle(dailyUsageLimitChk,com.eye.i18n.I18n.text("限制每日用量"));
        SettingsUi.toggle(usageWindowChk,com.eye.i18n.I18n.text("限制允许使用时段"));
        SettingsUi.toggle(usageGuardChk,com.eye.i18n.I18n.text("异常退出自动恢复"));
        JPanel budget=Theme.panel(new FlowLayout(FlowLayout.RIGHT,6,0));
        budget.add(usageBudgetInput);
        JPanel interval=Theme.panel(new FlowLayout(FlowLayout.RIGHT,4,0));
        interval.add(usageWindowStartInput); interval.add(Theme.label(com.eye.i18n.I18n.text("至"),12,Theme.SECONDARY)); interval.add(usageWindowEndInput);
        usageWindowStartInput.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("允许使用开始时刻"));
        usageWindowEndInput.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("允许使用结束时刻"));
        timeInputModeBox.addActionListener(e -> applyUsageInputMode(timeInputModeBox.getSelectedIndex()==1 ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED));
        dailyUsageLimitChk.addActionListener(e->updateUsageControls());
        usageWindowChk.addActionListener(e->updateUsageControls());
        systemSections.section("usage-limits",com.eye.i18n.I18n.text("使用时间限制"),"shield",com.eye.i18n.I18n.text("每日额度与允许时段同时生效；超限后显示遮罩，仅可关机；重启仍会继续限制。"),
                SettingsUi.group(com.eye.i18n.I18n.text("每日额度"),
                        SettingsUi.row(com.eye.i18n.I18n.text("限制每日用量"),com.eye.i18n.I18n.text("按自然日零点重置，锁屏与睡眠不计入。"),dailyUsageLimitChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("每天最多使用"),com.eye.i18n.I18n.text("1 分钟至 24 小时；独立于专注计时。"),budget),usageSummary),
                SettingsUi.group(com.eye.i18n.I18n.text("允许使用时段"),
                        SettingsUi.row(com.eye.i18n.I18n.text("限制使用时段"),com.eye.i18n.I18n.text("开始时刻包含在内，结束时刻起显示遮罩。"),usageWindowChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("允许时间范围"),com.eye.i18n.I18n.text("支持跨午夜，例如 22:00 至 06:00。"),interval)),
                SettingsUi.group(com.eye.i18n.I18n.text("运行保护"),SettingsUi.row(com.eye.i18n.I18n.text("异常退出自动恢复"),com.eye.i18n.I18n.text("需要 Windows 管理员授权。"),usageGuardChk)),
                SettingsUi.group(com.eye.i18n.I18n.text("限制与恢复"),new SettingsUi.Text(com.eye.i18n.I18n.text("启用后需要随 Windows 启动。仅统计 EyeRelax 运行且桌面已解锁的时间。"),11,Theme.SECONDARY)));
        applicationLimitsButton=new JButton(com.eye.i18n.I18n.text("管理应用限制"));Theme.smallButton(applicationLimitsButton,"primary","shield");
        applicationLimitsButton.addActionListener(e->openApplicationLimits.run());
        applicationLimitBehavior=new SettingsUi.Text(com.eye.i18n.I18n.text("受限时先遮挡应用窗口并说明原因；点击返回桌面后，再结束该应用。应用额度按学习日分界重置。"),12,Theme.SECONDARY);
        systemSections.section("application-limits",com.eye.i18n.I18n.text("应用限制"),"monitor",com.eye.i18n.I18n.text("每个应用单独设置完全禁用、每日额度和允许使用时段。"),
                SettingsUi.group(com.eye.i18n.I18n.text("分应用管理"),SettingsUi.row(com.eye.i18n.I18n.text("管理应用限制"),"",applicationLimitsButton)),SettingsUi.group(com.eye.i18n.I18n.text("拦截与恢复"),applicationLimitBehavior));
        localStrictPanel=new LocalStrictPanel(config);
        systemSections.section("strict-local",com.eye.i18n.I18n.text("一周严格设置"),"lock",com.eye.i18n.I18n.text("无需账户，本机离线生效。"),localStrictPanel);
        updateSettingsPanel=new UpdateSettingsPanel(config);

        SettingsUi.valueButton(dayBoundaryEditBtn, "clock"); SettingsUi.valueButton(longPauseResetEditBtn, "clock");
        SettingsUi.toggle(resetAfterLongPauseChk, com.eye.i18n.I18n.text("长时间暂停后重新开始"));
        resetAfterLongPauseChk.addActionListener(e -> {
            longPauseResetInput.setEnabled(resetAfterLongPauseChk.isSelected());
            longPauseResetEditBtn.setEnabled(resetAfterLongPauseChk.isSelected());
        });
        systemSections.section("day", com.eye.i18n.I18n.text("学习日与恢复"), "sun", com.eye.i18n.I18n.text("设置新一天的起点，以及长时间暂停后如何恢复计时。"),
                SettingsUi.group(com.eye.i18n.I18n.text("学习日与恢复"),
                        SettingsUi.row(com.eye.i18n.I18n.text("学习日分界"), com.eye.i18n.I18n.text("分界前的时间归前一天。修改仅影响之后的记录。"), dayBoundaryEditBtn),
                        SettingsUi.row(com.eye.i18n.I18n.text("长暂停后重新开始"), com.eye.i18n.I18n.text("关机、待机或暂停过久，不恢复上一轮。"), resetAfterLongPauseChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("重置阈值"), com.eye.i18n.I18n.text("超过此时长后，下一次开启新一轮。"), longPauseResetEditBtn)));

        SettingsUi.toggle(overlayChk, com.eye.i18n.I18n.text("显示悬浮计时器"));
        SettingsUi.toggle(overlaySwitchChk, com.eye.i18n.I18n.text("显示切换按钮"));
        SettingsUi.toggle(overlayButtonTextChk, com.eye.i18n.I18n.text("显示按钮文字"));
        systemSections.section("overlay", com.eye.i18n.I18n.text("悬浮计时器"), "grid", com.eye.i18n.I18n.text("在使用其他应用时查看倒计时、暂停或切换计划。"),
                SettingsUi.group(com.eye.i18n.I18n.text("悬浮条显示"),
                        SettingsUi.row(com.eye.i18n.I18n.text("显示悬浮计时器"), com.eye.i18n.I18n.text("默认收起，点击展开圆环倒计时。"), overlayChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("显示切换按钮"), com.eye.i18n.I18n.text("在悬浮条上快速选择其他计划。"), overlaySwitchChk),
                        SettingsUi.row(com.eye.i18n.I18n.text("显示按钮文字"), com.eye.i18n.I18n.text("关闭后只显示暂停、继续和切换图标。"), overlayButtonTextChk)));

        dataDirectoryField.setEditable(false); Theme.field(dataDirectoryField);
        dataDirectoryField.setFont(Theme.font(12)); dataDirectoryField.setPreferredSize(new Dimension(230, 36));
        dataDirectoryField.setMinimumSize(new Dimension(60, 36));
        Theme.smallButton(dataDirectoryBtn, "secondary", "folder"); dataDirectoryBtn.addActionListener(e -> chooseDataDirectory());
        dataDirectoryBtn.setPreferredSize(new Dimension(118, 36));
        JPanel dataRow = new JPanel(new BorderLayout(9, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        dataRow.setOpaque(false); dataRow.setBorder(new EmptyBorder(6, 0, 7, 0));
        dataRow.add(dataDirectoryField, BorderLayout.CENTER); dataRow.add(dataDirectoryBtn, BorderLayout.EAST);
        systemSections.section("storage", com.eye.i18n.I18n.text("数据存储"), "folder", "",
                SettingsUi.group(com.eye.i18n.I18n.text("本地数据存储位置"), dataRow,
                        new SettingsUi.Text(com.eye.i18n.I18n.text("更改后，现有数据将在下次启动时迁移。"), 11, Theme.SECONDARY)));
        return systemSections;
    }

    private void editDayBoundary() {
        DurationInput editor = DurationInput.forTimeOfDay(false);
        editor.setMode(timeInputModeBox.getSelectedIndex() == 1 ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED);
        editor.setSeconds(((Number) dayBoundaryHour.getValue()).intValue() * 3600
                + ((Number) dayBoundaryMinute.getValue()).intValue() * 60);
        JPanel body = Theme.panel(new BorderLayout()); body.add(editor, BorderLayout.NORTH);
        if (!SettingsUi.confirm(this, com.eye.i18n.I18n.text("学习日分界"), com.eye.i18n.I18n.text("例如 04:00：此前的凌晨时段仍归前一天。"), body, com.eye.i18n.I18n.text("确定"),()->InlineErrors.number(editor,editor::getTimeOfDaySeconds,0,86399,com.eye.i18n.I18n.text("请填写有效的时间。")))) return;
        int value = editor.getTimeOfDaySeconds();
        dayBoundaryHour.setValue(value / 3600); dayBoundaryMinute.setValue(value % 3600 / 60);
        updateDayPolicySummaries();
    }

    private void editLongPauseResetThreshold() {
        DurationInput editor = new DurationInput();
        editor.setMode(timeInputModeBox.getSelectedIndex() == 1 ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED);
        int current;
        try { current = Math.max(60, longPauseResetInput.getSeconds()); }
        catch (IllegalArgumentException ex) { current = Math.max(60, config.longPauseResetSeconds); }
        editor.setSeconds(current);
        JPanel body = Theme.panel(new BorderLayout()); body.add(editor, BorderLayout.NORTH);
        if (!SettingsUi.confirm(this, com.eye.i18n.I18n.text("长时间暂停阈值"), com.eye.i18n.I18n.text("超过此时长后，下一次启动或恢复不继续旧计时。"), body, com.eye.i18n.I18n.text("确定"),()->InlineErrors.number(editor,editor::getSeconds,60,Integer.MAX_VALUE,com.eye.i18n.I18n.text("重置阈值至少需要 1 分钟。")))) return;
        try { longPauseResetInput.setSeconds(Math.max(60, editor.getSeconds())); }
        catch (IllegalArgumentException ex) {
            showThemedMessage(com.eye.i18n.I18n.text("重置阈值至少需要 1 分钟。"), com.eye.i18n.I18n.text("长时间暂停阈值"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        updateDayPolicySummaries();
    }

    private void updateDayPolicySummaries() {
        int hour = ((Number) dayBoundaryHour.getValue()).intValue();
        int minute = ((Number) dayBoundaryMinute.getValue()).intValue();
        dayBoundaryEditBtn.setText(String.format("%02d:%02d  ›", hour, minute));
        int seconds;
        try { seconds = Math.max(60, longPauseResetInput.getSeconds()); }
        catch (IllegalArgumentException ex) { seconds = Math.max(60, config.longPauseResetSeconds); }
        longPauseResetEditBtn.setText(formatFriendlyDuration(seconds) + "  ›");
    }

    private static String formatFriendlyDuration(int seconds) {
        int safe = Math.max(0, seconds);
        int hours = safe / 3600;
        int minutes = (safe % 3600) / 60;
        int secs = safe % 60;
        if (minutes == 0 && secs == 0 && hours > 0) return hours + com.eye.i18n.I18n.text(" 小时");
        if (hours > 0 && secs == 0) return hours + com.eye.i18n.I18n.text("小时 ") + minutes + com.eye.i18n.I18n.text("分钟");
        if (hours == 0 && secs == 0) return minutes + com.eye.i18n.I18n.text(" 分钟");
        if (hours == 0 && minutes == 0) return secs + com.eye.i18n.I18n.text(" 秒");
        if (hours == 0) return minutes + com.eye.i18n.I18n.text("分 ") + secs + com.eye.i18n.I18n.text("秒");
        return hours + com.eye.i18n.I18n.text("时 ") + minutes + com.eye.i18n.I18n.text("分 ") + secs + com.eye.i18n.I18n.text("秒");
    }

    private void chooseDataDirectory() {
        java.nio.file.Path target;
        try {
            target = com.eye.win.WindowsShell.choose(this,com.eye.i18n.I18n.text("选择 EyeRelax 数据文件夹"),true);
            if (target == null) return;
            target = target.toAbsolutePath().normalize();
        } catch (Exception ex) {
            systemStatus.setText(com.eye.i18n.I18n.text("无法打开文件夹选择窗口：") + ex.getMessage());
            return;
        }
        if (target.equals(DataPaths.root())) return;
        Object[] options = {com.eye.i18n.I18n.text("确认并退出"), com.eye.i18n.I18n.text("取消")};
        int answer = showThemedOption(com.eye.i18n.I18n.text("EyeRelax 将退出。下次打开时，计划、记录和设置会迁移到以下文件夹。")
                        + "\n\n" + target,
                com.eye.i18n.I18n.text("迁移本地数据"), JOptionPane.QUESTION_MESSAGE, options, options[0]);
        if (answer != 0) return;
        try {
            DataPaths.requestMigration(target);
            systemStatus.setForeground(Theme.GREEN);
            systemStatus.setText(com.eye.i18n.I18n.text("正在退出。下次打开时将迁移数据。"));
            if (onExit != null) SwingUtilities.invokeLater(onExit);
        } catch (Exception ex) {
            systemStatus.setForeground(Theme.DANGER);
            systemStatus.setText(com.eye.i18n.I18n.text("无法安排迁移：") + ex.getMessage());
        }
    }

    private void applyPreferences() {
        if (!strictPreflight(false)) return;
        final int work, relax;
        try {
            work = workDurationInput.getSeconds();
            relax = relaxDurationInput.getSeconds();
        } catch (IllegalArgumentException ex) {
            settingsStatus.setForeground(Theme.DANGER);
            settingsStatus.setText(com.eye.i18n.I18n.text("工作时长和休息时长都必须大于 0 秒。"));
            return;
        }

        Config.ForceMode mode = modeA.isSelected() ? Config.ForceMode.A_SOFT
                : modeB.isSelected() ? Config.ForceMode.B_STRONG : Config.ForceMode.C_ULTRA;
        boolean changed = work != config.workSeconds || relax != config.relaxSeconds || mode != config.mode
                || launchPlanChooserChk.isSelected() != config.showPlanChooserOnLaunch
                || autoStartAfterPlanChk.isSelected() != config.autoStartAfterPlanSelection
                || autoContinueChk.isSelected() != config.autoContinueCycles
                || taskCompletionNotificationChk.isSelected() != config.showTaskCompletionNotification;

        config.mode = mode;
        config.showPlanChooserOnLaunch = launchPlanChooserChk.isSelected();
        config.autoStartAfterPlanSelection = autoStartAfterPlanChk.isSelected();
        config.autoContinueCycles = autoContinueChk.isSelected();
        config.showTaskCompletionNotification = taskCompletionNotificationChk.isSelected();
        engine.updateDurations(work, relax);

        if (!config.save()) {
            settingsStatus.setForeground(Theme.DANGER);
            settingsStatus.setText(com.eye.i18n.I18n.text("本次设置已生效，但写入配置文件失败。"));
        } else {
            settingsStatus.setForeground(Theme.GREEN);
            settingsStatus.setText(changed ? com.eye.i18n.I18n.text("已保存；当前强制休息不受影响。") : com.eye.i18n.I18n.text("设置没有变化。"));
        }
        refreshFocus.run();
    }

    private void applySystemSettings() {
        if (!strictPreflight(true)) return;
        if (!validateUsageFields()) return;
        if (usageMonitor != null && usageMonitor.blocked()) return;
        // Validate the entire policy before mutating live configuration.
        final int threshold;
        try { threshold=Math.max(60,longPauseResetInput.getSeconds()); }
        catch(IllegalArgumentException ex){systemStatus.setForeground(Theme.DANGER);systemStatus.setText(com.eye.i18n.I18n.text("重置阈值至少 1 分钟。"));return;}
        boolean requestedLimits = dailyUsageLimitChk.isSelected() || usageWindowChk.isSelected() || config.hasApplicationLimits() || com.eye.sync.StrictSettings.locked();
        boolean startupTarget = requestedLimits || launchAtLoginChk.isSelected();
        boolean startupRegistered = StartupRegistration.isEnabled();
        String startupWarning = null;
        if (startupTarget != startupRegistered) {
            try {
                StartupRegistration.apply(startupTarget);
                startupRegistered = startupTarget;
            } catch (RuntimeException ex) {
                if(requestedLimits)startupRegistered=false;
                // Startup registration is one setting, not a transaction boundary for every other
                // preference on this page. Preserve the real registration state and still save the
                // remaining system settings; surface the failure after the save.
                startupWarning = ex.getMessage();
            }
        }
        if (requestedLimits && !startupRegistered) {
            systemStatus.setForeground(Theme.DANGER);
            systemStatus.setText(com.eye.i18n.I18n.text("使用限制尚未启用：未确认有效的登录启动入口。") + (startupWarning==null?com.eye.i18n.I18n.text("请手动添加快捷方式后检查启动入口。"):startupWarning));
            return;
        }
        if(usageMonitor!=null){usageMonitor.tick();if(usageMonitor.blocked())return;}
        boolean oldDaily=config.dailyUsageLimitEnabled,oldWindow=config.usageWindowEnabled;
        int oldBudget=config.dailyUsageLimitSeconds,oldStart=config.usageWindowStartMinute,oldEnd=config.usageWindowEndMinute;
        config.dailyUsageLimitSeconds=usageBudgetSeconds();
        config.usageWindowStartMinute=usageWindowStartInput.getTimeOfDaySeconds()/60;
        config.usageWindowEndMinute=usageWindowEndInput.getTimeOfDaySeconds()/60;
        config.dailyUsageLimitEnabled=dailyUsageLimitChk.isSelected();config.usageWindowEnabled=usageWindowChk.isSelected();
        config.launchAtLogin = startupRegistered;
        updateSettingsPanel.applyToConfig();
        config.planManagementSelectionAction = planSelectionActionBox.getSelectedIndex() == 1
                ? Config.PlanSelectionAction.SELECT_AND_START : Config.PlanSelectionAction.SELECT_ONLY;
        config.historyDateInput = historyDateInputBox.getSelectedIndex() == 1
                ? Config.HistoryDateInput.MANUAL : Config.HistoryDateInput.CALENDAR;
        config.showStatusOverlay = overlayChk.isSelected();
        config.overlayShowSwitchButton = overlaySwitchChk.isSelected();
        config.overlayShowButtonText = overlayButtonTextChk.isSelected();
        config.language=languageBox.getSelectedIndex()==1?"en":"zh-CN";
        config.timeInputMode = timeInputModeBox.getSelectedIndex() == 1
                ? Config.TimeInputMode.WHEEL : Config.TimeInputMode.SEGMENTED;
        config.closeAction = switch (closeActionBox.getSelectedIndex()) {
            case 1 -> Config.CloseAction.MINIMIZE_TO_TRAY;
            case 2 -> Config.CloseAction.EXIT;
            default -> Config.CloseAction.ASK;
        };
        engine.persistRuntimeState(); // commit the tail while the previous boundary is still active
        config.dayBoundarySeconds = (((Integer) dayBoundaryHour.getValue()) * 60 + (Integer) dayBoundaryMinute.getValue()) * 60;
        config.resetAfterLongPause = resetAfterLongPauseChk.isSelected();
        try { config.longPauseResetSeconds = Math.max(60, longPauseResetInput.getSeconds()); }
        catch (IllegalArgumentException ex) {
            systemStatus.setForeground(Theme.DANGER);
            systemStatus.setText(com.eye.i18n.I18n.text("长时间暂停阈值必须大于 0 秒。"));
            return;
        }
        if (!config.save()) {
            config.dailyUsageLimitEnabled=oldDaily;config.usageWindowEnabled=oldWindow;
            config.dailyUsageLimitSeconds=oldBudget;config.usageWindowStartMinute=oldStart;config.usageWindowEndMinute=oldEnd;
            systemStatus.setForeground(Theme.DANGER);
            systemStatus.setText(com.eye.i18n.I18n.text("设置已应用，但写入配置文件失败。"));
        } else if (startupWarning != null) {
            systemStatus.setForeground(Theme.DANGER);
            systemStatus.setText(com.eye.i18n.I18n.text("其他系统设置已保存；登录启动未更改：") + startupWarning);
        } else {
            systemStatus.setForeground(Theme.GREEN);
            systemStatus.setText(com.eye.i18n.I18n.text((com.eye.i18n.I18n.isEnglish()?"en":"zh-CN").equals(config.language)?"系统设置已保存。":"系统设置已保存，语言将在重新打开应用后生效。"));
        }
        launchAtLoginChk.setSelected(config.launchAtLogin);
        updateUsageControls();
        if(usageMonitor!=null)usageMonitor.tick();
        applyTimeInputPresentation();
        engine.onDayPolicyChanged();
        planManager.refreshStudyDay();
        refreshPlans.run();
        engine.onOverlaySettingChanged();
        if (onToggleOverlay != null) onToggleOverlay.accept(config.showStatusOverlay);
    }

    void refreshPreferenceFields() {
        applyTimeInputPresentation();
        workDurationInput.setSeconds(config.workSeconds);
        relaxDurationInput.setSeconds(config.relaxSeconds);
        modeA.setSelected(config.mode == Config.ForceMode.A_SOFT);
        modeB.setSelected(config.mode == Config.ForceMode.B_STRONG);
        modeC.setSelected(config.mode == Config.ForceMode.C_ULTRA);
        launchPlanChooserChk.setSelected(config.showPlanChooserOnLaunch);
        autoStartAfterPlanChk.setSelected(config.autoStartAfterPlanSelection);
        autoContinueChk.setSelected(config.autoContinueCycles);
        taskCompletionNotificationChk.setSelected(config.showTaskCompletionNotification);
        settingsStatus.setText("");
        settingsStatus.setForeground(Theme.SECONDARY);
    }

    void refreshSystemFields() {
        languageBox.setSelectedIndex("en".equals(config.language)?1:0);
        dailyUsageLimitChk.setSelected(config.dailyUsageLimitEnabled);
        usageWindowChk.setSelected(config.usageWindowEnabled);
        usageBudgetInput.setSeconds(config.dailyUsageLimitSeconds);
        usageWindowStartInput.setSeconds(config.usageWindowStartMinute*60);
        usageWindowEndInput.setSeconds(config.usageWindowEndMinute*60);
        stagedReminderDefaultSeconds=Math.max(5,config.reminderDefaultMaskSeconds);
        reminderDefaultEditBtn.setText(formatFriendlyDuration(stagedReminderDefaultSeconds)+"  ›");
        launchAtLoginChk.setSelected(config.launchAtLogin);
        updateUsageControls();
        int boundary = Math.max(0, Math.min(86399, config.dayBoundarySeconds));
        dayBoundaryHour.setValue(boundary / 3600);
        dayBoundaryMinute.setValue((boundary % 3600) / 60);
        resetAfterLongPauseChk.setSelected(config.resetAfterLongPause);
        longPauseResetInput.setSeconds(Math.max(60, config.longPauseResetSeconds));
        longPauseResetInput.setEnabled(config.resetAfterLongPause);
        longPauseResetEditBtn.setEnabled(config.resetAfterLongPause);
        updateDayPolicySummaries();
        boolean startupRegistered = StartupRegistration.isEnabled();
        usageGuardChk.setSelected(config.usageGuardEnabled);
        dataDirectoryField.setText(DataPaths.root().toString());
        dataDirectoryField.setCaretPosition(0);
        dataDirectoryField.setToolTipText(null);
        planSelectionActionBox.setSelectedIndex(config.planManagementSelectionAction == Config.PlanSelectionAction.SELECT_AND_START ? 1 : 0);
        historyDateInputBox.setSelectedIndex(config.historyDateInput == Config.HistoryDateInput.MANUAL ? 1 : 0);
        timeInputModeBox.setSelectedIndex(config.timeInputMode == Config.TimeInputMode.WHEEL ? 1 : 0);
        overlayChk.setSelected(config.showStatusOverlay);
        overlaySwitchChk.setSelected(config.overlayShowSwitchButton);
        overlayButtonTextChk.setSelected(config.overlayShowButtonText);
        closeActionBox.setSelectedIndex(switch (config.closeAction) {
            case MINIMIZE_TO_TRAY -> 1;
            case EXIT -> 2;
            default -> 0;
        });
        if (config.launchAtLogin && !startupRegistered) {
            systemStatus.setText(com.eye.i18n.I18n.text("检测到登录启动入口缺失；保持勾选后点击“保存系统设置”可重新创建。"));
            systemStatus.setForeground(Theme.DANGER);
        } else {
            systemStatus.setText("");
            systemStatus.setForeground(Theme.SECONDARY);
        }
    }

    private void applyTimeInputPresentation() {
        DurationInput.Mode mode = config.timeInputMode == Config.TimeInputMode.WHEEL
                ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED;
        workDurationInput.setMode(mode);
        relaxDurationInput.setMode(mode);
        longPauseResetInput.setMode(mode);
        applyUsageInputMode(mode);
    }

    private void applyUsageInputMode(DurationInput.Mode mode){usageBudgetInput.setMode(mode);usageWindowStartInput.setMode(mode);usageWindowEndInput.setMode(mode);}

    private int usageBudgetSeconds(){return usageBudgetInput.getSeconds();}

    private boolean validateUsageFields(){
        try {
            if(dailyUsageLimitChk.isSelected()&&(usageBudgetSeconds()<60||usageBudgetSeconds()>86400))throw new IllegalArgumentException(com.eye.i18n.I18n.text("每日额度应在 1 分钟至 24 小时之间。"));
            if(usageWindowChk.isSelected()&&usageWindowStartInput.getTimeOfDaySeconds()==usageWindowEndInput.getTimeOfDaySeconds())throw new IllegalArgumentException(com.eye.i18n.I18n.text("允许时段的开始与结束不能相同；全天允许请关闭时段限制。"));
            return true;
        }catch(Exception ex){systemStatus.setForeground(Theme.DANGER);systemStatus.setText(ex instanceof IllegalArgumentException?ex.getMessage():com.eye.i18n.I18n.text("请检查使用时间限制的输入。"));return false;}
    }

    void updateUsageControls(){
        usageBudgetInput.setEnabled(dailyUsageLimitChk.isSelected());
        usageWindowStartInput.setEnabled(usageWindowChk.isSelected());usageWindowEndInput.setEnabled(usageWindowChk.isSelected());
        boolean limits=dailyUsageLimitChk.isSelected()||usageWindowChk.isSelected()||config.hasApplicationLimits()||com.eye.sync.StrictSettings.locked();
        if(limits)launchAtLoginChk.setSelected(true);launchAtLoginChk.setEnabled(!limits);
        if(usageMonitor!=null){var status=usageMonitor.status();usageSummary.setText(com.eye.i18n.I18n.text("今日已用 ")+formatFriendlyDuration((int)(status.usedMillis()/1000))+(config.dailyUsageLimitEnabled?com.eye.i18n.I18n.text(" · 剩余 ")+formatFriendlyDuration((int)(status.remainingMillis()/1000)):""));}
    }
}
