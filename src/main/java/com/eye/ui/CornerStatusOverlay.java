package com.eye.ui;

import com.eye.ui.theme.Icons;
import com.eye.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.function.BiConsumer;

/**
 * Top-right status surface.
 *
 * Collapsed mode keeps the original compact EyeRelax strip and sizes itself to the content,
 * capped at MAX_OVERLAY_WIDTH. Expanded mode has one stable footprint: the mini dial stays
 * on the left, while task/status information is stacked in two lines on the right.
 */
public class CornerStatusOverlay {
    private static final int MAX_OVERLAY_WIDTH = 380;
    private static final int MIN_COMPACT_LABEL_WIDTH = 70;
    private static final int EXPANDED_WIDTH = 290;
    private static final int EXPANDED_HEIGHT = 116;
    private static final int EXPANDED_TEXT_WIDTH = 142;

    private final JWindow window = new JWindow();

    /** Primary expanded line: task name / phase name / paused state. */
    private final JLabel label = new JLabel("", SwingConstants.LEFT);
    /** Secondary expanded line: e.g. 本轮剩余 12:34. */
    private final JLabel secondaryLabel = new JLabel("", SwingConstants.LEFT);
    private final JPanel secondaryRow = Theme.panel(new BorderLayout());
    private final JLabel compactLabel = new JLabel("", SwingConstants.LEFT);
    private final MiniDial dial = new MiniDial();

    private final JButton actionBtn = new JButton(com.eye.i18n.I18n.text("暂停"));
    private final JButton toggleBtn = new JButton(com.eye.i18n.I18n.text("切换"));
    private final JButton compactActionBtn = new JButton(com.eye.i18n.I18n.text("暂停"));
    private final JButton compactToggleBtn = new JButton(com.eye.i18n.I18n.text("切换"));

    private final JPanel compactActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private final JPanel expandedActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JComponent collapsedPanel;
    private final JComponent expandedPanel;

    private Runnable onActionButton;
    private Runnable onToggleAction;
    private BiConsumer<Integer, Integer> onPositionChanged;

    private boolean customPosition = false;
    private int customX = 0;
    private int customY = 0;
    private boolean expanded = false;
    private boolean showSwitchButton = true;
    private boolean showButtonText = true;
    private String actionText = com.eye.i18n.I18n.text("暂停");
    private String fullStatusText = "";
    private String primaryStatusText = "";
    private String secondaryStatusText = "";
    private boolean pausedStatus = false;

    private Point dragStartScreen;
    private Point windowStart;
    private boolean dragged;

    public CornerStatusOverlay() {
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setBackground(new Color(0, 0, 0, 0));

        collapsedPanel = buildCollapsed();
        expandedPanel = buildExpanded();
        window.setContentPane(collapsedPanel);
        refreshContentSizing();
        placeDefaultTopRight();
    }

    /** Original compact strip: logo + one-line status + action controls. Width is natural, up to a cap. */
    private JComponent buildCollapsed() {
        Theme.Surface panel = new Theme.Surface(new BorderLayout(10, 0), Theme.DARK, new Color(0x527363), 20);
        panel.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 10));

        compactLabel.setForeground(new Color(0xF2F5EB));
        compactLabel.setFont(Theme.font(13));
        compactLabel.setIcon(Icons.appIcon(21));
        compactLabel.setIconTextGap(10);
        compactLabel.setToolTipText(null);
        panel.add(compactLabel, BorderLayout.CENTER);

        Theme.smallButton(compactActionBtn, "dark", "pause");
        compactActionBtn.setFocusable(false);
        compactActionBtn.addActionListener(e -> { if (onActionButton != null) onActionButton.run(); });

        Theme.smallButton(compactToggleBtn, "dark", "swap");
        compactToggleBtn.setFocusable(false);
        compactToggleBtn.setToolTipText(com.eye.i18n.I18n.text("切换显示：本轮剩余 / 已用 / 任务剩余 / 已用"));
        compactToggleBtn.addActionListener(e -> { if (onToggleAction != null) onToggleAction.run(); });

        compactActions.setOpaque(false);
        compactActions.add(compactActionBtn);
        compactActions.add(compactToggleBtn);
        panel.add(compactActions, BorderLayout.EAST);

        installDragAndToggle(panel, true);
        installDragAndToggle(compactLabel, true);
        return panel;
    }

    /** Stable expanded appearance: same footprint whether paused or actively counting down. */
    private JComponent buildExpanded() {
        Theme.Surface panel = new Theme.Surface(new BorderLayout(10, 0), Theme.DARK, new Color(0x527363), 20);
        panel.setBorder(BorderFactory.createEmptyBorder(11, 12, 11, 12));
        panel.setPreferredSize(new Dimension(EXPANDED_WIDTH, EXPANDED_HEIGHT));
        panel.setMinimumSize(new Dimension(EXPANDED_WIDTH, EXPANDED_HEIGHT));
        panel.setMaximumSize(new Dimension(EXPANDED_WIDTH, EXPANDED_HEIGHT));

        JPanel left = Theme.panel(new BorderLayout());
        dial.setPreferredSize(new Dimension(94, 94));
        left.setPreferredSize(new Dimension(94, 94));
        left.add(dial, BorderLayout.CENTER);
        panel.add(left, BorderLayout.WEST);

        JPanel center = Theme.column();
        center.setPreferredSize(new Dimension(EXPANDED_TEXT_WIDTH, 94));
        // The content stack is slightly shorter than the dial. Center the whole right-hand
        // block against the 94px dial instead of letting BoxLayout pin it to the top edge.
        center.add(Box.createVerticalStrut(6));

        JPanel heading = Theme.panel(new BorderLayout(7, 0));
        JLabel logo = new JLabel(Icons.appIcon(18));
        logo.setPreferredSize(new Dimension(18, 22));
        heading.add(logo, BorderLayout.WEST);
        label.setForeground(new Color(0xF4F7F0));
        label.setFont(Theme.bold(12));
        label.setPreferredSize(new Dimension(EXPANDED_TEXT_WIDTH - 25, 22));
        heading.add(label, BorderLayout.CENTER);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        center.add(heading);

        secondaryLabel.setForeground(new Color(0xBFD0C5));
        secondaryLabel.setFont(Theme.font(11));
        // The title text starts after the 18px logo + 7px gap. Mirror that 25px start exactly.
        secondaryRow.add(Box.createHorizontalStrut(25), BorderLayout.WEST);
        secondaryRow.add(secondaryLabel, BorderLayout.CENTER);
        secondaryRow.setPreferredSize(new Dimension(EXPANDED_TEXT_WIDTH, 18));
        secondaryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        secondaryRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(secondaryRow);
        // Use some of the spare lower space so the action row sits a little lower and feels balanced.
        center.add(Box.createVerticalStrut(10));

        expandedActions.setOpaque(false);
        Theme.smallButton(actionBtn, "dark", "pause");
        actionBtn.setFocusable(false);
        actionBtn.addActionListener(e -> { if (onActionButton != null) onActionButton.run(); });
        Theme.smallButton(toggleBtn, "dark", "swap");
        toggleBtn.setFocusable(false);
        toggleBtn.setToolTipText(com.eye.i18n.I18n.text("切换显示：本轮剩余 / 已用 / 任务剩余 / 已用"));
        toggleBtn.addActionListener(e -> { if (onToggleAction != null) onToggleAction.run(); });
        expandedActions.add(actionBtn);
        expandedActions.add(toggleBtn);
        expandedActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        center.add(expandedActions);
        panel.add(center, BorderLayout.CENTER);

        // Any non-button area collapses the expanded card.
        installDragAndToggle(panel, false);
        installDragAndToggle(left, false);
        installDragAndToggle(dial, false);
        installDragAndToggle(center, false);
        installDragAndToggle(heading, false);
        installDragAndToggle(logo, false);
        installDragAndToggle(label, false);
        installDragAndToggle(secondaryRow, false);
        installDragAndToggle(secondaryLabel, false);
        return panel;
    }

    private void installDragAndToggle(Component component, boolean expandOnClick) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragStartScreen = e.getLocationOnScreen();
                windowStart = window.getLocation();
                dragged = false;
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (dragStartScreen == null || windowStart == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - dragStartScreen.x;
                int dy = now.y - dragStartScreen.y;
                if (Math.abs(dx) + Math.abs(dy) > 4) dragged = true;
                customPosition = true;
                customX = windowStart.x + dx;
                customY = windowStart.y + dy;
                window.setLocation(customX, customY);
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (customPosition && onPositionChanged != null) onPositionChanged.accept(customX, customY);
                if (!dragged) applyExpandedState(expandOnClick);
                dragStartScreen = null;
                windowStart = null;
            }
        };
        component.addMouseListener(drag);
        component.addMouseMotionListener(drag);
    }

    public void setToggleAction(Runnable onToggleAction) { this.onToggleAction = onToggleAction; }

    public void setActionButton(String text, boolean enabled, Runnable action) {
        actionText = text == null ? "" : text;
        String visibleText = showButtonText ? actionText : "";
        Icon icon = Icons.of(com.eye.i18n.I18n.text("暂停").equals(actionText) ? "pause" : "play", 14, null);
        for (JButton button : new JButton[]{actionBtn, compactActionBtn}) {
            button.setText(visibleText);
            button.setIcon(icon);
            button.setToolTipText(actionText.isEmpty() ? null : actionText);
            button.setEnabled(enabled);
        }
        onActionButton = action;
        refreshContentSizing();
    }

    public void setPreferences(boolean showSwitchButton, boolean showButtonText) {
        this.showSwitchButton = showSwitchButton;
        this.showButtonText = showButtonText;
        for (JButton button : new JButton[]{toggleBtn, compactToggleBtn}) {
            button.setVisible(showSwitchButton);
            button.setText(showButtonText ? com.eye.i18n.I18n.text("切换") : "");
            button.setIcon(Icons.of("swap", 14, null));
        }
        for (JButton button : new JButton[]{actionBtn, compactActionBtn}) {
            button.setText(showButtonText ? actionText : "");
        }
        refreshContentSizing();
    }

    public void setPositionChangedListener(BiConsumer<Integer, Integer> listener) { this.onPositionChanged = listener; }

    public void setCustomPosition(int x, int y) {
        this.customPosition = true;
        this.customX = x;
        this.customY = y;
        window.setLocation(x, y);
    }

    public void clearCustomPosition() { this.customPosition = false; }

    /** Backward-compatible one-line status; split the first middle dot into expanded primary/secondary lines. */
    public void showStatus(String text, String clock, double remainingRatio, boolean resting) {
        String safe = text == null ? "" : text;
        int split = safe.indexOf(" · ");
        if (split > 0) {
            showStatus(safe.substring(0, split), safe.substring(split + 3), clock, remainingRatio, resting);
        } else {
            showStatus(safe, "", clock, remainingRatio, resting);
        }
    }

    /** Two-line expanded status while the collapsed strip remains a compact single-line summary. */
    public void showStatus(String primary, String secondary, String clock, double remainingRatio, boolean resting) {
        String safePrimary = primary == null ? "" : primary.strip();
        String safeSecondary = secondary == null ? "" : secondary.strip();
        String safeClock = clock == null || clock.isBlank() ? "--:--" : clock;
        pausedStatus = safePrimary.contains("暂停") || safeSecondary.contains("暂停");
        primaryStatusText = pausedStatus ? com.eye.i18n.I18n.text("已暂停") : safePrimary;
        secondaryStatusText = pausedStatus ? "" : safeSecondary;
        fullStatusText = pausedStatus ? com.eye.i18n.I18n.text("已暂停")
                : secondaryStatusText.isBlank() ? primaryStatusText : primaryStatusText + " · " + secondaryStatusText;

        compactLabel.setToolTipText(null);
        label.setToolTipText(null);
        secondaryLabel.setToolTipText(null);
        dial.display(pausedStatus ? com.eye.i18n.I18n.text("暂停") : safeClock, remainingRatio, resting, pausedStatus);
        refreshContentSizing();
        if (!window.isVisible()) window.setVisible(true);
    }

    public void showText(String text) {
        showStatus(text, text != null && text.contains("休息") ? "REST" : "PAUSE", 0,
                text != null && text.contains("休息"));
    }

    public void hide() { window.setVisible(false); }

    public boolean isExpanded() { return expanded; }

    private void applyExpandedState(boolean expanded) {
        if (this.expanded == expanded && window.getContentPane() == (expanded ? expandedPanel : collapsedPanel)) {
            refreshContentSizing();
            return;
        }
        Point old = window.isShowing() ? window.getLocation() : null;
        this.expanded = expanded;
        window.setContentPane(expanded ? expandedPanel : collapsedPanel);
        refreshContentSizing();
        if (customPosition) {
            window.setLocation(customX, customY);
        } else if (old != null) {
            Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            window.setLocation(b.x + b.width - window.getWidth() - 20, Math.max(b.y + 20, old.y));
        } else {
            placeDefaultTopRight();
        }
        window.revalidate();
        window.repaint();
    }

    /** Recompute natural compact width while keeping expanded geometry stable. */
    private void refreshContentSizing() {
        refreshCompactTextAndWidth();
        refreshExpandedText();

        Point old = window.isShowing() ? window.getLocation() : null;
        window.pack();
        if (customPosition) {
            window.setLocation(customX, customY);
        } else if (old != null) {
            Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            window.setLocation(b.x + b.width - window.getWidth() - 20, Math.max(b.y + 20, old.y));
        } else {
            placeDefaultTopRight();
        }
        window.revalidate();
        window.repaint();
    }

    private void refreshCompactTextAndWidth() {
        FontMetrics fm = compactLabel.getFontMetrics(compactLabel.getFont());
        int actionsWidth = compactActions.getPreferredSize().width;
        int iconWidth = compactLabel.getIcon() == null ? 0 : compactLabel.getIcon().getIconWidth() + compactLabel.getIconTextGap();
        int chrome = 32;
        // Reserve the complete active metric before giving the task name any room.
        int maxLabelWidth = Math.max(Math.max(MIN_COMPACT_LABEL_WIDTH, MAX_OVERLAY_WIDTH - actionsWidth - chrome),iconWidth + (secondaryStatusText.isBlank()?0:fm.stringWidth(" · "+secondaryStatusText)+fm.stringWidth("…")+8) + 4);
        int naturalLabelWidth = iconWidth + fm.stringWidth(fullStatusText) + 4;
        int labelWidth = Math.max(MIN_COMPACT_LABEL_WIDTH, Math.min(naturalLabelWidth, maxLabelWidth));
        int textWidth = Math.max(28, labelWidth - iconWidth - 4);
        if (secondaryStatusText.isBlank()) compactLabel.setText(elide(primaryStatusText, fm, textWidth));
        else {
            String separator = " · ";
            int nameWidth = textWidth - fm.stringWidth(separator + secondaryStatusText);
            String name = nameWidth >= fm.stringWidth("…") ? elide(primaryStatusText, fm, nameWidth) : "";
            compactLabel.setText(name.isBlank() ? secondaryStatusText : name + separator + secondaryStatusText);
        }
        compactLabel.setPreferredSize(new Dimension(labelWidth, 28));
    }

    private void refreshExpandedText() {
        FontMetrics primaryMetrics = label.getFontMetrics(label.getFont());
        FontMetrics secondaryMetrics = secondaryLabel.getFontMetrics(secondaryLabel.getFont());
        int textWidth = Math.max(EXPANDED_TEXT_WIDTH, Math.max(secondaryMetrics.stringWidth(secondaryStatusText) + 29, expandedActions.getPreferredSize().width));
        int width = Math.max(EXPANDED_WIDTH, 24 + 94 + 10 + textWidth);
        Dimension size = new Dimension(width, EXPANDED_HEIGHT);
        expandedPanel.setPreferredSize(size);
        expandedPanel.setMinimumSize(size);
        expandedPanel.setMaximumSize(size);
        label.setText(elide(primaryStatusText, primaryMetrics, width - 24 - 94 - 10 - 25));
        secondaryLabel.setText(secondaryStatusText);
    }

    private static String elide(String value, FontMetrics fm, int maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (fm.stringWidth(value) <= maxWidth) return value;
        String ellipsis = "…";
        int room = Math.max(0, maxWidth - fm.stringWidth(ellipsis));
        int low = 0, high = value.codePointCount(0,value.length());
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (fm.stringWidth(value.substring(0, value.offsetByCodePoints(0,mid))) <= room) low = mid;
            else high = mid - 1;
        }
        return value.substring(0, value.offsetByCodePoints(0,Math.max(0, low))).stripTrailing() + ellipsis;
    }

    private void placeDefaultTopRight() {
        Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = b.x + b.width - window.getWidth() - 20;
        int y = b.y + 20;
        window.setLocation(x, y);
    }

    private static final class MiniDial extends JComponent {
        private String clock = "--:--";
        private double remaining = 1;
        private boolean resting;
        private boolean paused;

        void display(String clock, double remaining, boolean resting, boolean paused) {
            this.clock = clock;
            this.remaining = Math.max(0, Math.min(1, remaining));
            this.resting = resting;
            this.paused = paused;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.quality(g);
            int size = Math.min(getWidth(), getHeight()) - 8;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            Color accent = resting ? Theme.SAGE : new Color(0xC4D7C0);
            g.setColor(new Color(255, 255, 255, 18));
            g.fill(new Ellipse2D.Double(x, y, size, size));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 35));
            g.draw(new Ellipse2D.Double(x + 4, y + 4, size - 8, size - 8));
            g.setColor(accent);
            if (remaining > 0) {
                g.draw(new Arc2D.Double(x + 4, y + 4, size - 8, size - 8, 90, -360 * remaining, Arc2D.OPEN));
            }

            String text = paused ? com.eye.i18n.I18n.text("暂停") : clock;
            g.setFont(paused ? Theme.bold(16) : Theme.numbers(clock.length() > 5 ? 12 : 14).deriveFont(Font.BOLD));
            g.setColor(new Color(0xF2F5EB));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2f,
                    getHeight() / 2f + fm.getAscent() / 3f);
            g.dispose();
        }
    }
}
