package com.eye.ui;

import com.eye.ui.theme.Icons;
import com.eye.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/** Branded bottom-right notification used for task completion. */
public final class NotificationToast {
    private static final int TOAST_WIDTH = 392;
    private static final int TOAST_HEIGHT = 86;
    private static JWindow activeWindow;
    private static Timer activeTimer;

    private NotificationToast() {}

    /** Preferred API for the task-complete flow. */
    public static void showTaskComplete(String taskName) {
        show(com.eye.i18n.I18n.text("任务计划已完成"), taskName == null ? "" : taskName);
    }

    /** Backward-compatible API; old "任务名 · 请选择下一项任务" messages are normalized automatically. */
    public static void show(String title, String message) {
        Runnable r = () -> {
            closeActive();

            String safeTitle = title == null || title.isBlank() ? com.eye.i18n.I18n.text("任务计划已完成") : title.strip();
            String taskName = normalizeTaskName(message);
            if (taskName.isBlank()) taskName = com.eye.i18n.I18n.text("当前任务");

            JWindow window = new JWindow();
            activeWindow = window;
            window.setAlwaysOnTop(true);
            window.setFocusableWindowState(false);
            window.setBackground(new Color(0, 0, 0, 0));

            JPanel shell = new JPanel(new BorderLayout());
            shell.setOpaque(false);
            shell.setBorder(new EmptyBorder(9, 9, 9, 9));

            Color cardFill = new Color(0x173F34);
            Color cardLine = new Color(0x426B5C);
            Color bright = new Color(0xF3F6EF);
            Color accent = new Color(0xBFD6AD);

            Theme.Surface card = new Theme.Surface(new BorderLayout(), cardFill, cardLine, 24);
            card.setPreferredSize(new Dimension(TOAST_WIDTH, TOAST_HEIGHT));
            card.setBorder(new EmptyBorder(14, 16, 14, 14));

            JButton close = new JButton("×");
            Theme.smallButton(close, "dark", null);
            close.setForeground(bright);
            close.setFont(Theme.bold(17));
            close.setBorder(new EmptyBorder(2, 8, 2, 8));
            close.setToolTipText(com.eye.i18n.I18n.text("关闭通知"));
            close.setFocusable(false);


            JPanel main = Theme.panel(new BorderLayout(14, 0));
            JPanel dismiss = Theme.panel(new BorderLayout());
            dismiss.add(close, BorderLayout.NORTH);
            main.add(dismiss, BorderLayout.EAST);

            CompletionBadge badge = new CompletionBadge(accent, bright);
            badge.setPreferredSize(new Dimension(48, 48));
            main.add(badge, BorderLayout.WEST);

            JPanel copy = Theme.column();
            JLabel heading = Theme.label(safeTitle, 16, bright);
            heading.setFont(Theme.bold(16));
            copy.add(heading);
            copy.add(Box.createVerticalStrut(3));

            JLabel task = Theme.label("", 12, accent);
            task.setFont(Theme.bold(12));
            task.setText(elide(taskName, task.getFontMetrics(task.getFont()), 265));
            task.setToolTipText(null);
            task.setPreferredSize(new Dimension(265, 19));
            copy.add(task);
            main.add(copy, BorderLayout.CENTER);
            card.add(main, BorderLayout.CENTER);

            shell.add(card, BorderLayout.CENTER);
            window.setContentPane(shell);
            window.pack();

            close.addActionListener(e -> closeWindow(window));

            Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int x = b.x + b.width - window.getWidth() - 18;
            int y = b.y + b.height - window.getHeight() - 18;
            window.setLocation(x, y);
            window.setVisible(true);

            Timer timer = new Timer(6_500, e -> closeWindow(window));
            timer.setRepeats(false);
            activeTimer = timer;
            timer.start();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }

    private static String normalizeTaskName(String message) {
        String value = message == null ? "" : message.strip();
        int split = value.indexOf(" · ");
        if (split > 0) value = value.substring(0, split).strip();
        return value;
    }

    private static String elide(String value, FontMetrics fm, int maxWidth) {
        if (value == null || value.isEmpty() || fm.stringWidth(value) <= maxWidth) return value == null ? "" : value;
        String ellipsis = "…";
        int room = Math.max(0, maxWidth - fm.stringWidth(ellipsis));
        int low = 0, high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (fm.stringWidth(value.substring(0, mid)) <= room) low = mid;
            else high = mid - 1;
        }
        return value.substring(0, low).stripTrailing() + ellipsis;
    }

    private static void closeWindow(JWindow window) {
        if (window == null) return;
        if (activeTimer != null) {
            activeTimer.stop();
            activeTimer = null;
        }
        window.setVisible(false);
        window.dispose();
        if (activeWindow == window) activeWindow = null;
    }

    private static void closeActive() {
        if (activeWindow != null) closeWindow(activeWindow);
    }

    /** Minimal success emblem; vector-drawn so it stays crisp at any DPI. */
    private static final class CompletionBadge extends JComponent {
        private final Color accent;
        private final Color ink;
        private CompletionBadge(Color accent, Color ink) { this.accent = accent; this.ink = ink; }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.quality(g);
            int s = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 36));
            g.fill(new Ellipse2D.Double(x, y, s, s));
            g.setColor(accent);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(x + .75, y + .75, s - 1.5, s - 1.5));
            Icon check = Icons.of("check", 22, ink);
            check.paintIcon(this, g, (getWidth() - 22) / 2, (getHeight() - 22) / 2);
            g.dispose();
        }
    }
}
