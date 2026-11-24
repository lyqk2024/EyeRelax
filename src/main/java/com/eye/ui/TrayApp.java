package com.eye.ui;

import com.eye.core.Config;
import com.eye.core.CycleEngine;
import com.eye.core.UsageLimitMonitor;
import com.eye.plan.PlanItem;
import com.eye.plan.PlanManager;
import com.eye.reminder.ReminderItem;
import com.eye.reminder.ReminderManager;
import com.eye.ui.theme.Icons;
import com.eye.ui.theme.DurationInput;
import com.eye.ui.theme.PlanRenderer;
import com.eye.ui.theme.Theme;
import com.eye.win.GlobalMouseClickWatcher;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** System tray integration and the application-level navigation/start flow. */
public class TrayApp {
    private final Config config;
    private final CycleEngine engine;
    private final PlanManager planManager;
    private final ReminderManager reminderManager;
    private final ReminderQueueController reminderQueue;

    private JMenuItem startItem;
    private JMenuItem stopItem;
    private JMenuItem restartItem;
    private JMenuItem restNowItem;
    private JMenuItem overlayToggleItem;

    private ControlWindow controlWindow;
    private UsageLimitMonitor usageMonitor;
    public void setUsageMonitor(UsageLimitMonitor monitor) {usageMonitor=monitor;if(controlWindow!=null)controlWindow.setUsageMonitor(monitor);}
    public void setComputerActivity(com.eye.core.ComputerActivity activity){if(controlWindow!=null)controlWindow.setComputerActivity(activity);}
    public void setApplicationProtection(com.eye.win.ApplicationProtection protection){if(controlWindow!=null)controlWindow.setApplicationProtection(protection);}
    public void startUpdateChecks(){if(controlWindow!=null)controlWindow.startUpdateChecks();}
    public void configureTutorial(com.eye.tutorial.TutorialState state){if(state!=null&&controlWindow!=null)controlWindow.configureTutorial(state);}
    private TrayIcon trayIcon;
    private JPopupMenu trayMenu;
    private JWindow trayMenuAnchor;
    private long lastTrayPopupAt;
    private AWTEventListener trayMenuClickAwayListener;
    private GlobalMouseClickWatcher trayMenuGlobalClickWatcher;

    public TrayApp(Config config, CycleEngine engine, PlanManager planManager) {
        this(config, engine, planManager, new ReminderManager());
    }

    public TrayApp(Config config, CycleEngine engine, PlanManager planManager, ReminderManager reminderManager) {
        this.config = config;
        this.reminderQueue=new ReminderQueueController(config,engine,this::refreshStartStop);
        this.engine = engine;
        this.planManager = planManager;
        this.reminderManager = reminderManager == null ? new ReminderManager() : reminderManager;
    }

    public void init() {
        if (!SystemTray.isSupported()) {
            showTopMessage(com.eye.i18n.I18n.text("系统不支持托盘（SystemTray）。"), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Do not attach an AWT PopupMenu to TrayIcon. On Windows that menu is rendered by the
        // old native AWT peer, which can fail to render CJK text depending on the host locale/font.
        // The tray icon itself stays AWT, but the right-click menu is fully Swing-rendered.
        trayMenu = buildTrayMenu();

        Image img = Icons.appImage(64);
        trayIcon = new TrayIcon(img, "EyeRelax");
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybeShowTrayMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowTrayMenu(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    SwingUtilities.invokeLater(() -> {
                        hideTrayMenu();
                        if (controlWindow != null) controlWindow.showFocusPage();
                    });
                }
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException ex) {
            showTopMessage(com.eye.i18n.I18n.text("托盘图标添加失败: ") + ex.getMessage(), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
        }

        controlWindow = new ControlWindow(
                engine,
                config,
                planManager,
                reminderManager,
                this::onExitRequested,
                () -> {
                    onStartRequested();
                    refreshStartStop();
                },
                () -> {
                    onStopRequested();
                    refreshStartStop();
                },
                () -> {
                    onRestartRequested();
                    refreshStartStop();
                },
                this::choosePlanFromFocusCard,
                enabled -> {
                    config.showStatusOverlay = enabled;
                    config.save();
                    engine.onOverlaySettingChanged();
                    if (overlayToggleItem != null) refreshOverlayTrayItem();
                }
        );

        engine.setPlanHooks(new CycleEngine.PlanHooks() {
            @Override public int onConsumeSeconds(String planId, int seconds) {
                return planManager.consumeSeconds(planId, seconds);
            }

            @Override public void onPlanDepleted(String planId) {
                SwingUtilities.invokeLater(() -> onPlanDepletedUi(planId));
            }

            @Override public void onStudyDayChanged(LocalDate studyDate) {
                planManager.refreshStudyDay();
                SwingUtilities.invokeLater(() -> {
                    refreshPlanUi();
                    refreshStartStop();
                });
            }

            @Override public int getPlannedSeconds(String planId) {
                return planManager.getPlannedSeconds(planId);
            }

            @Override public int getRemainingSeconds(String planId) {
                return planManager.getRemainingSeconds(planId);
            }

            @Override public String getPlanName(String planId) {
                PlanItem item = planManager.get(planId);
                return item == null ? null : item.name;
            }
        });

        engine.setQuickActionHandlers(
                () -> SwingUtilities.invokeLater(() -> {
                    onStopRequested();
                    refreshStartStop();
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    onStartRequested();
                    refreshStartStop();
                })
        );

        engine.setOnStateChanged(this::refreshStartStop);
        engine.setOnRelaxFinishedStartRequested(() -> SwingUtilities.invokeLater(() -> {
            startAfterRest();
            refreshStartStop();
        }));
        engine.setReminderHooks(new CycleEngine.ReminderHooks() {
            @Override public long nextDueBetween(long fromEpochMs, long toEpochMs) {
                return reminderManager.nextDueBetween(fromEpochMs, toEpochMs);
            }
            @Override public void onRestOverlap(long reminderEpochMs, long restEndEpochMs) {
                SwingUtilities.invokeLater(() -> showRestReminderConflict(reminderEpochMs, restEndEpochMs));
            }
        });
        reminderQueue.setCompletionHandler(reminderManager::completePresentation);
        reminderManager.setDueHandler(item -> SwingUtilities.invokeLater(() -> reminderQueue.enqueue(item)));

        refreshStartStop();
    }

    private JPopupMenu buildTrayMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setLightWeightPopupEnabled(false);
        menu.setBackground(Theme.SURFACE);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));

        startItem = trayMenuItem(com.eye.i18n.I18n.text("开始"), "play");
        startItem.addActionListener(e -> {
            onStartRequested();
            refreshStartStop();
        });

        stopItem = trayMenuItem(com.eye.i18n.I18n.text("暂停"), "pause");
        stopItem.addActionListener(e -> {
            onStopRequested();
            refreshStartStop();
        });

        restartItem = trayMenuItem(com.eye.i18n.I18n.text("重新开始"), "restart");
        restartItem.addActionListener(e -> {
            onRestartRequested();
            refreshStartStop();
        });

        restNowItem = trayMenuItem(com.eye.i18n.I18n.text("立即休息"), "leaf");
        restNowItem.addActionListener(e -> {
            engine.startRestNow();
            refreshStartStop();
        });

        JMenuItem planItem = trayMenuItem(com.eye.i18n.I18n.text("计划管理…"), "calendar");
        planItem.addActionListener(e -> openPlanWindow());

        JMenuItem reminderItem = trayMenuItem(com.eye.i18n.I18n.text("提醒计划…"), "clock");
        reminderItem.addActionListener(e -> { if (controlWindow != null) controlWindow.showReminderPage(); });

        JMenuItem preferenceItem = trayMenuItem(com.eye.i18n.I18n.text("偏好设置…"), "settings");
        preferenceItem.addActionListener(e -> showSettingsDialog());

        JMenuItem systemItem = trayMenuItem(com.eye.i18n.I18n.text("系统设置…"), "grid");
        systemItem.addActionListener(e -> showSystemSettings());

        // Use the same vector icon lane as every other item instead of Swing's native
        // JCheckBoxMenuItem check glyph (which sits off the icon baseline on Windows HiDPI).
        overlayToggleItem = trayMenuItem(com.eye.i18n.I18n.text("显示状态悬浮条"), config.showStatusOverlay ? "check" : "blank");
        overlayToggleItem.addActionListener(e -> {
            boolean enabled = !config.showStatusOverlay;
            config.showStatusOverlay = enabled;
            refreshOverlayTrayItem();
            config.save();
            engine.onOverlaySettingChanged();
            if (controlWindow != null) controlWindow.setOverlayChecked(enabled);
        });

        JMenuItem exitItem = trayMenuItem(com.eye.i18n.I18n.text("退出 EyeRelax"), "exit");
        exitItem.addActionListener(e -> onExitRequested());

        menu.add(startItem);
        menu.add(stopItem);
        menu.add(restartItem);
        menu.add(restNowItem);
        menu.addSeparator();
        menu.add(planItem);
        menu.add(reminderItem);
        menu.add(preferenceItem);
        menu.add(systemItem);
        menu.add(overlayToggleItem);
        menu.addSeparator();
        menu.add(exitItem);

        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                stopTrayMenuOutsideClickWatch();
                hideTrayMenuAnchor();
            }
            @Override public void popupMenuCanceled(PopupMenuEvent e) {
                stopTrayMenuOutsideClickWatch();
                hideTrayMenuAnchor();
            }
        });
        return menu;
    }

    private JMenuItem trayMenuItem(String text, String iconName) {
        JMenuItem item = new JMenuItem(text);
        if (iconName != null) item.setIcon(Icons.of(iconName, 15, Theme.GREEN));
        styleTrayMenuItem(item);
        return item;
    }

    private void styleTrayMenuItem(JMenuItem item) {
        item.setFont(Theme.font(13));
        item.setForeground(Theme.INK);
        item.setBackground(Theme.SURFACE);
        item.setOpaque(true);
        item.setIconTextGap(9);
        item.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 9));
    }

    private void refreshOverlayTrayItem() {
        if (overlayToggleItem == null) return;
        overlayToggleItem.setIcon(Icons.of(config.showStatusOverlay ? "check" : "blank", 15, Theme.GREEN));
        overlayToggleItem.setToolTipText(config.showStatusOverlay ? com.eye.i18n.I18n.text("点击隐藏状态悬浮条") : com.eye.i18n.I18n.text("点击显示状态悬浮条"));
    }

    private void maybeShowTrayMenu(MouseEvent e) {
        boolean popup = e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3;
        if (!popup || trayMenu == null) return;
        long when = e.getWhen();
        if (when - lastTrayPopupAt < 180) return; // Windows may report both press and release.
        lastTrayPopupAt = when;

        // Use the live pointer position first: this is the exact point the user right-clicked
        // in the system tray. TrayIcon MouseEvent coordinates can be peer-dependent on Windows.
        Point pointer = null;
        try {
            PointerInfo info = MouseInfo.getPointerInfo();
            if (info != null) pointer = info.getLocation();
        } catch (HeadlessException | SecurityException ignored) {}

        int screenX;
        int screenY;
        if (pointer != null) {
            screenX = pointer.x;
            screenY = pointer.y;
        } else {
            try {
                screenX = e.getXOnScreen();
                screenY = e.getYOnScreen();
            } catch (IllegalComponentStateException ex) {
                // TrayIcon mouse coordinates are screen-relative on the Windows peer.
                screenX = e.getX();
                screenY = e.getY();
            }
        }
        final int x = screenX;
        final int y = screenY;
        SwingUtilities.invokeLater(() -> showTrayMenuAt(x, y));
    }

    private void showTrayMenuAt(int x, int y) {
        if (trayMenu == null) return;
        hideTrayMenu();
        refreshStartStop();

        if (trayMenuAnchor == null) {
            trayMenuAnchor = new JWindow();
            trayMenuAnchor.setAlwaysOnTop(true);
            trayMenuAnchor.setFocusableWindowState(true);
            trayMenuAnchor.setType(Window.Type.POPUP);
            trayMenuAnchor.setBackground(new Color(0, 0, 0, 0));
            trayMenuAnchor.setSize(1, 1);
        }

        Dimension size = trayMenu.getPreferredSize();
        Point origin = trayMenuOriginAtClick(x, y, size);

        // The requested anchor is geometric, not screen-edge based: the menu's lower-left
        // corner is exactly the tray right-click point. In particular, do not snap the menu
        // to the usable desktop's bottom-right corner just because the click happened on the taskbar.
        trayMenuAnchor.setLocation(origin.x, origin.y);
        trayMenuAnchor.setVisible(true);
        trayMenu.show(trayMenuAnchor.getContentPane(), 0, 0);
        trayMenu.requestFocusInWindow();
        startTrayMenuOutsideClickWatch();
    }

    private void startTrayMenuOutsideClickWatch() {
        stopTrayMenuOutsideClickWatch();

        // AWT covers every click elsewhere inside EyeRelax itself. Keep this path even on Windows
        // so in-process click-away does not depend on native-hook availability.
        trayMenuClickAwayListener = event -> {
            if (!(event instanceof MouseEvent mouse) || mouse.getID() != MouseEvent.MOUSE_PRESSED
                    || trayMenu == null || !trayMenu.isVisible()) return;
            Object source = mouse.getSource();
            if (source instanceof Component component
                    && (component == trayMenu || SwingUtilities.isDescendingFrom(component, trayMenu))) return;

            // Heavyweight Swing popups can report the event source as the popup's hosting window
            // instead of a JMenuItem descendant. Fall back to Java/AWT screen coordinates before
            // treating the press as an outside click. These coordinates use the same HiDPI space
            // as the Swing popup.
            try {
                Point at = mouse.getLocationOnScreen();
                if (isInsideTrayMenu(at.x, at.y)) return;
            } catch (IllegalComponentStateException ignored) {}
            hideTrayMenu();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(trayMenuClickAwayListener, AWTEvent.MOUSE_EVENT_MASK);

        // Desktop/taskbar/other-process clicks never enter AWT. Use a low-level Windows mouse hook
        // while the menu is visible, so even a very fast click is observed. The previous polling
        // approach could miss a complete press+release between timer ticks. The hook only observes
        // button-down events; it never consumes or modifies them.
        trayMenuGlobalClickWatcher = new GlobalMouseClickWatcher();
        boolean watching = trayMenuGlobalClickWatcher.start(() ->
                // WH_MOUSE_LL reports native desktop pixels while Swing can be DPI-scaled. The
                // watcher deliberately exposes no native coordinates; it is only the event signal.
                // Sample the pointer through AWT on the EDT so point and popup bounds are guaranteed
                // to use the same coordinate space.
                SwingUtilities.invokeLater(this::dismissTrayMenuIfPointerOutside));
        if (!watching) {
            trayMenuGlobalClickWatcher = null;
        }
    }

    private void dismissTrayMenuIfPointerOutside() {
        if (trayMenu == null || !trayMenu.isVisible()) return;
        try {
            PointerInfo info = MouseInfo.getPointerInfo();
            if (info == null) return;
            Point at = info.getLocation();
            if (!isInsideTrayMenu(at.x, at.y)) hideTrayMenu();
        } catch (HeadlessException | SecurityException ignored) {
            // If AWT cannot provide a pointer position, do not pre-emptively hide a live Swing
            // menu. The in-process AWT listener still handles EyeRelax clicks, and another native
            // click will retry the check. This is safer than breaking every JMenuItem action.
        }
    }

    private boolean isInsideTrayMenu(int screenX, int screenY) {
        Rectangle popup = trayMenuScreenBounds();
        if (popup == null) return false;
        popup.grow(3, 3);
        return popup.contains(screenX, screenY);
    }

    private void stopTrayMenuOutsideClickWatch() {
        GlobalMouseClickWatcher watcher = trayMenuGlobalClickWatcher;
        trayMenuGlobalClickWatcher = null;
        if (watcher != null) watcher.stop();
        if (trayMenuClickAwayListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(trayMenuClickAwayListener);
            trayMenuClickAwayListener = null;
        }
    }

    private Rectangle trayMenuScreenBounds() {
        if (trayMenu == null || !trayMenu.isShowing()) return null;
        try {
            Point at = trayMenu.getLocationOnScreen();
            return new Rectangle(at.x, at.y, trayMenu.getWidth(), trayMenu.getHeight());
        } catch (IllegalComponentStateException ignored) {
            return null;
        }
    }

    private static Point trayMenuOriginAtClick(int clickX, int clickY, Dimension menuSize) {
        int height = menuSize == null ? 0 : Math.max(0, menuSize.height);
        return new Point(clickX, clickY - height);
    }

    private void hideTrayMenu() {
        stopTrayMenuOutsideClickWatch();
        if (trayMenu != null) trayMenu.setVisible(false);
        hideTrayMenuAnchor();
    }

    private void hideTrayMenuAnchor() {
        if (trayMenuAnchor != null) trayMenuAnchor.setVisible(false);
    }

    /** Show the Focus page first. The optional chooser is deliberately deferred until after that first paint. */
    public void startOnLaunch() {
        if (controlWindow == null) return;
        boolean restored = engine.restoreSession();
        reminderManager.start();
        controlWindow.showFocusPage();
        controlWindow.showWindow();
        controlWindow.offerTutorialOnLaunch();
        if (config.showPlanChooserOnLaunch && !restored && !controlWindow.automaticTutorialPending()) {
            Timer deferred = new Timer(180, e -> {
                choosePlan(false, config.autoStartAfterPlanSelection, com.eye.i18n.I18n.text("选择今天的计划"),
                        com.eye.i18n.I18n.text("先选择要投入的任务；默认仍由你手动点击“开始”。"), true);
                refreshStartStop();
            });
            deferred.setRepeats(false);
            deferred.start();
        }
    }

    public void refresh() { refreshStartStop(); }

    private void onStartRequested() {
        if(usageMonitor!=null&&usageMonitor.blocked())return;
        if (engine.isRunning()) return;
        if (engine.canResumeFromPause()) {
            engine.start();
            return;
        }

        planManager.loadToday();
        String current = engine.getCurrentPlanId();
        if (current != null && planManager.getRemainingSeconds(current) > 0) {
            engine.start();
            return;
        }

        if (planManager.listRemainingPositive().isEmpty()) {
            Object[] options = {com.eye.i18n.I18n.text("计划管理"), com.eye.i18n.I18n.text("自由专注"), com.eye.i18n.I18n.text("取消")};
            int ch = showTopOption(com.eye.i18n.I18n.text("今天还没有可继续的计划。\n\n可以先建立计划，也可以直接开始自由专注。"),
                    com.eye.i18n.I18n.text("开始专注"), JOptionPane.QUESTION_MESSAGE, options, options[0]);
            if (ch == 0) openPlanWindow();
            else if (ch == 1) {
                engine.setCurrentPlanId(null);
                engine.start();
            }
            return;
        }

        // The user already pressed Start, so selecting here always starts immediately.
        choosePlan(false, true, com.eye.i18n.I18n.text("开始专注"), com.eye.i18n.I18n.text("选择本轮要跟踪的任务，或者使用自由专注。"), true);
    }

    private void onRestartRequested() {
        if(usageMonitor!=null&&usageMonitor.blocked())return;
        // Keep the selected task, but restart the work cycle itself from the configured duration.
        engine.restartFresh();
    }

    private void onStopRequested() {
        if (isStrictRelaxRunning()) {
            showTopMessage(com.eye.i18n.I18n.text("B / C 模式休息中不可以暂停，请先完成休息。"), com.eye.i18n.I18n.text("提示"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        engine.stop();
    }

    private void onExitRequested() {
        if(config.hasUsageLimits() || config.hasApplicationLimits() || com.eye.sync.StrictSettings.locked()) {
            if(usageMonitor==null||!usageMonitor.blocked())showTopMessage(com.eye.i18n.I18n.text("使用时间限制已启用，EyeRelax 需要保持运行。关闭窗口可保留在托盘；请在允许使用时段内从设置中关闭限制后再退出。"),com.eye.i18n.I18n.text("使用时间限制"),JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (reminderQueue.isActive() || engine.isReminderActive()) {
            showTopMessage(com.eye.i18n.I18n.text("提醒遮罩尚未手动关闭，暂时不能退出 EyeRelax。"), com.eye.i18n.I18n.text("提醒进行中"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (isStrictRelaxRunning()) {
            showTopMessage(com.eye.i18n.I18n.text("B / C 模式休息中不可以退出，请先完成休息。"), com.eye.i18n.I18n.text("提示"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        reminderManager.shutdown();
        engine.shutdown();
        hideTrayMenu();
        if (trayIcon != null) {
            try { SystemTray.getSystemTray().remove(trayIcon); } catch (Exception ignored) {}
        }
        System.exit(0);
    }

    private boolean isStrictRelaxRunning() {
        return engine.isRunning() && engine.isForcedRest();
    }

    private void openPlanWindow() {
        if (controlWindow == null) return;
        controlWindow.showPlanPage();
    }

    private void choosePlanFromFocusCard() {
        choosePlan(false, config.autoStartAfterPlanSelection,
                com.eye.i18n.I18n.text("选择当前计划"), com.eye.i18n.I18n.text("选择后会立即成为当前任务。是否自动开始由“偏好设置”决定。"), true);
        refreshStartStop();
    }

    /**
     * Common plan chooser.
     * @param forceFreshStart when true, start a fresh cycle rather than resume a paused one
     * @param startAfterSelection whether choosing a plan/free focus should immediately run the timer
     * @param allowFreeFocus whether to expose a free-focus option
     */
    private boolean choosePlan(boolean forceFreshStart,
                               boolean startAfterSelection,
                               String title,
                               String intro,
                               boolean allowFreeFocus) {
        planManager.loadToday();
        List<PlanItem> candidates = planManager.listRemainingPositive();

        if (candidates.isEmpty()) {
            Object[] options = allowFreeFocus
                    ? new Object[]{com.eye.i18n.I18n.text("打开计划管理"), com.eye.i18n.I18n.text("自由专注"), com.eye.i18n.I18n.text("稍后")}
                    : new Object[]{com.eye.i18n.I18n.text("打开计划管理"), com.eye.i18n.I18n.text("稍后")};
            int ch = showTopOption(com.eye.i18n.I18n.text("今天没有剩余时间大于 0 的计划。"), title,
                    JOptionPane.INFORMATION_MESSAGE, options, options[0]);
            if (ch == 0) openPlanWindow();
            else if (allowFreeFocus && ch == 1) {
                engine.setCurrentPlanId(null);
                if (startAfterSelection) startEngine(forceFreshStart);
                refreshPlanUi();
                return true;
            }
            return false;
        }

        DefaultListModel<PlanItem> model = new DefaultListModel<>();
        for (PlanItem p : candidates) model.addElement(p);
        JList<PlanItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new PlanRenderer());
        list.setBackground(Theme.BG);
        list.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        list.setVisibleRowCount(Math.min(8, candidates.size()));

        String current = engine.getCurrentPlanId();
        if (current != null) {
            for (int i = 0; i < model.size(); i++) {
                if (current.equals(model.get(i).id)) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (list.getSelectedIndex() < 0 && !candidates.isEmpty()) list.setSelectedIndex(0);

        JPanel panel = Theme.panel(new BorderLayout(0, 12));
        JLabel copy = Theme.label("<html>" + escapeHtml(intro) + "</html>", 12, Theme.SECONDARY);
        panel.add(copy, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        Theme.scroll(scroll);
        scroll.setPreferredSize(new Dimension(440, Math.min(360, 80 + candidates.size() * 62)));
        panel.add(scroll, BorderLayout.CENTER);

        Object[] options = allowFreeFocus
                ? new Object[]{com.eye.i18n.I18n.text("使用所选计划"), com.eye.i18n.I18n.text("自由专注"), com.eye.i18n.I18n.text("计划管理"), com.eye.i18n.I18n.text("取消")}
                : new Object[]{com.eye.i18n.I18n.text("使用所选计划"), com.eye.i18n.I18n.text("计划管理"), com.eye.i18n.I18n.text("取消")};
        int ch = showTopOption(panel, title, JOptionPane.PLAIN_MESSAGE, options, options[0]);
        if (ch < 0) return false;

        if (ch == 0) {
            PlanItem selected = list.getSelectedValue();
            if (selected == null) {
                showTopMessage(com.eye.i18n.I18n.text("请先选择一个计划。"), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
                return false;
            }
            engine.setCurrentPlanId(selected.id);
            if (startAfterSelection && !engine.isRunning()) startEngine(forceFreshStart);
            refreshPlanUi();
            return true;
        }

        if (allowFreeFocus && ch == 1) {
            engine.setCurrentPlanId(null);
            if (startAfterSelection && !engine.isRunning()) startEngine(forceFreshStart);
            refreshPlanUi();
            return true;
        }

        int planManagementIndex = allowFreeFocus ? 2 : 1;
        if (ch == planManagementIndex) openPlanWindow();
        return false;
    }

    private void startEngine(boolean forceFreshStart) {
        if (forceFreshStart) engine.restartFresh();
        else engine.start();
    }

    private void startAfterRest() {
        planManager.loadToday();
        String current = engine.getCurrentPlanId();
        if (current != null && planManager.getRemainingSeconds(current) > 0) {
            // The current task survives the rest break and automatically continues when requested.
            engine.start();
            return;
        }
        choosePlan(false, true, com.eye.i18n.I18n.text("下一轮专注"), com.eye.i18n.I18n.text("选择下一项任务；选择后会直接开始下一轮。"), true);
    }

    private void onPlanDepletedUi(String planId) {
        PlanItem item = planManager.get(planId);
        String name = item == null || item.name == null || item.name.isBlank() ? com.eye.i18n.I18n.text("当前任务") : item.name;

        // Stop the work clock at this exact moment, preserving the remaining work-cycle time.
        if (engine.isRunning() && engine.isInWork()) engine.stop();
        if (config.showTaskCompletionNotification) {
            SoftChime.play();
            NotificationToast.showTaskComplete(name);
        }

        Object[] options = {com.eye.i18n.I18n.text("临时加时"), com.eye.i18n.I18n.text("选择下一项"), com.eye.i18n.I18n.text("稍后再说")};
        int ch = showTopOption("“" + name + com.eye.i18n.I18n.text("” 的今日计划时间已完成。\n\n") +
                        com.eye.i18n.I18n.text("可以只给今天临时增加一段时间；明天仍使用原来的计划时长。"),
                com.eye.i18n.I18n.text("任务完成"), JOptionPane.INFORMATION_MESSAGE, options, options[0]);
        if (ch == 0) {
            int extra = promptTodayExtension(name);
            if (extra > 0 && planManager.extendToday(planId, extra) != null) {
                engine.setCurrentPlanId(planId);
                engine.start();
                refreshPlanUi();
            }
        } else if (ch == 1) {
            choosePlan(false, true, com.eye.i18n.I18n.text("任务完成"), com.eye.i18n.I18n.text("选择下一项任务后会直接继续本轮专注。"), true);
        }
        refreshStartStop();
    }

    private int promptTodayExtension(String name) {
        final int[] result = {0};
        runOnEdtAndWait(() -> {
            DurationInput input = new DurationInput();
            input.setMode(config.timeInputMode == Config.TimeInputMode.WHEEL ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED);
            input.setSeconds(15 * 60);
            JPanel panel = Theme.column();
            panel.add(Theme.label(com.eye.i18n.I18n.text("今日临时加时 · ") + name, 12, Theme.SECONDARY));
            panel.add(Box.createVerticalStrut(10));
            panel.add(input);
            boolean ok = com.eye.ui.theme.SettingsUi.confirmForeground(controlWindow,com.eye.i18n.I18n.text("临时加时"),com.eye.i18n.I18n.text("只增加今日计划时长。"),panel,com.eye.i18n.I18n.text("确定"),()->com.eye.ui.theme.InlineErrors.number(input,input::getSeconds,1,Integer.MAX_VALUE,com.eye.i18n.I18n.text("加时时长必须大于 0 秒。")));
            if (ok) {
                try { result[0] = input.getSeconds(); }
                catch (IllegalArgumentException ignored) { result[0] = 0; }
            }
        });
        return result[0];
    }

    private long lastPlanStatsRefresh;
    private void refreshPlanUi() {
        if (controlWindow != null) {
            controlWindow.refreshPlanPage();
            controlWindow.refreshButtons();
        }
    }

    private void refreshStartStop() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshStartStop);
            return;
        }
        boolean running = engine.isRunning();
        boolean pausedSnapshot = !running && engine.hasPausedSnapshot();
        if (startItem != null) {
            startItem.setEnabled(!running);
            startItem.setText(pausedSnapshot ? com.eye.i18n.I18n.text("继续") : com.eye.i18n.I18n.text("开始"));
        }
        if (stopItem != null) stopItem.setEnabled(engine.canPauseNow());
        if (restartItem != null) restartItem.setEnabled(true);
        if (restNowItem != null) restNowItem.setEnabled(true);
        if (overlayToggleItem != null) refreshOverlayTrayItem();
        if (controlWindow != null) {
            controlWindow.setRestartEnabled(pausedSnapshot);
            controlWindow.refreshButtons();
            if ("plans".equals(controlWindow.getActivePage())&&System.nanoTime()-lastPlanStatsRefresh>=60_000_000_000L){lastPlanStatsRefresh=System.nanoTime();controlWindow.refreshPlanPage();}
        }
    }

    private void showRestReminderConflict(long reminderEpochMs, long restEndEpochMs) {
        ReminderItem item = reminderManager.itemDueAt(reminderEpochMs);
        String name = item == null ? com.eye.i18n.I18n.text("定时提醒") : item.title;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        String due = Instant.ofEpochMilli(reminderEpochMs).atZone(ZoneId.systemDefault()).format(fmt);
        Object[] options = {com.eye.i18n.I18n.text("保持原计划"), com.eye.i18n.I18n.text("调整提醒计划"), com.eye.i18n.I18n.text("调整我的节奏")};
        int ch = showTopOption(com.eye.i18n.I18n.text("本次休息与提醒“") + name + com.eye.i18n.I18n.text("”重叠（约 ") + due + "）。\n\n" +
                        com.eye.i18n.I18n.text("若保持原计划，本次休息先不锁屏，只保留休息计时；提醒到点后进入强制遮罩。") +
                        com.eye.i18n.I18n.text("遮罩关闭时若仍在休息时段，再继续执行当前休息模式。"),
                com.eye.i18n.I18n.text("休息与提醒重叠"), JOptionPane.WARNING_MESSAGE, options, options[0]);
        if (ch == 1 && controlWindow != null) controlWindow.showReminderPage();
        else if (ch == 2 && controlWindow != null) controlWindow.openQuickRhythmEditor();
    }

    private void showSettingsDialog() {
        if(usageMonitor!=null&&usageMonitor.blocked())return;
        if (controlWindow == null) return;
        controlWindow.requestSettingsPage();
    }

    private void showSystemSettings() {
        if(usageMonitor!=null&&usageMonitor.blocked())return;
        if (controlWindow == null) return;
        controlWindow.showSystemSettingsPage();
    }

    private void showTopMessage(Object message, String title, int messageType) {
        runOnEdtAndWait(() -> {
            JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION, Icons.appIcon(40));
            JDialog dialog = pane.createDialog(controlWindow, title);
            Theme.window(dialog);
            DialogPresentation.foreground(dialog);
            dialog.setModal(true);
            try{dialog.setVisible(true);}finally{dialog.dispose();}
        });
    }

    private int showTopOption(Object message, String title, int messageType, Object[] options, Object defaultValue) {
        final int[] result = {JOptionPane.CLOSED_OPTION};
        runOnEdtAndWait(() -> {
            JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION,
                    Icons.appIcon(40), options, defaultValue);
            JDialog dialog = pane.createDialog(controlWindow, title);
            Theme.window(dialog);
            DialogPresentation.foreground(dialog);
            dialog.setModal(true);
            try{dialog.setVisible(true);}finally{dialog.dispose();}

            Object value = pane.getValue();
            if (value == null) {
                result[0] = JOptionPane.CLOSED_OPTION;
                return;
            }
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(value)) {
                    result[0] = i;
                    return;
                }
            }
            if (value instanceof Integer) result[0] = (Integer) value;
        });
        return result[0];
    }

    private void runOnEdtAndWait(Runnable action) {
        try {
            if (SwingUtilities.isEventDispatchThread()) action.run();
            else SwingUtilities.invokeAndWait(action);
        } catch (Exception ignored) {}
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
