package com.eye.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseWheelEvent;

/**
 * Seconds-backed duration editor with two presentation modes:
 *  - segmented numeric fields (hour / minute / second)
 *  - compact alarm-clock-style wheel columns
 *
 * The component never asks the user to type ':' separators. Callers still
 * persist and consume one exact total-second value.
 */
public final class DurationInput extends JPanel {
    public enum Mode { SEGMENTED, WHEEL }

    private static final int MAX_HOURS = 9_999;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);

    private final int maximumHours;
    private final boolean showSeconds;
    private final boolean timeOfDay;
    private final JSpinner hourSpinner;
    private final JSpinner minuteSpinner;
    private final JSpinner secondSpinner;
    private final JList<Integer> hourWheel;
    private final JList<Integer> minuteWheel;
    private final JList<Integer> secondWheel;

    private Mode mode = Mode.SEGMENTED;
    private boolean syncing;

    public DurationInput() { this(MAX_HOURS, true, false); }

    public static DurationInput forDailyBudget() { return new DurationInput(24, false, false); }

    /** Uses the exact same segmented/wheel presentation for a 24-hour clock. */
    public static DurationInput forTimeOfDay(boolean includeSeconds) {
        return new DurationInput(23, includeSeconds, true);
    }

    private DurationInput(int maxHours, boolean includeSeconds, boolean clock) {
        super(new BorderLayout());
        maximumHours = maxHours; showSeconds = includeSeconds; timeOfDay = clock;
        hourSpinner = spinner(0, 0, maxHours, 1, clock ? 2 : 4);
        minuteSpinner = spinner(0, 0, 59, 1, 2);
        secondSpinner = spinner(0, 0, 59, 1, 2);
        hourWheel = wheelList(0, maxHours);
        minuteWheel = wheelList(0, 59);
        secondWheel = wheelList(0, 59);
        setOpaque(false);
        cardHost.setOpaque(false);
        cardHost.add(buildSegmented(), Mode.SEGMENTED.name());
        cardHost.add(buildWheel(), Mode.WHEEL.name());
        add(cardHost, BorderLayout.CENTER);

        ChangeListener segmentedChange = e -> syncWheelFromSegmented();
        hourSpinner.addChangeListener(segmentedChange);
        minuteSpinner.addChangeListener(segmentedChange);
        secondSpinner.addChangeListener(segmentedChange);

        hourWheel.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) syncSegmentedFromWheel(); });
        minuteWheel.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) syncSegmentedFromWheel(); });
        secondWheel.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) syncSegmentedFromWheel(); });

        setSeconds(60 * 60);

        // A wheel can be configured while the Preferences card is still hidden.
        // Re-center once Swing has actually made this editor visible, when the
        // viewport extent is finally known.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing() && mode == Mode.WHEEL) {
                queueCenterWheels();
            }
        });
    }

    public void setMode(Mode next) {
        if (next == null) next = Mode.SEGMENTED;
        int value = getSecondsAllowZero();
        mode = next;
        setAll(value);
        cards.show(cardHost, mode.name());
        revalidate();
        repaint();
        if (mode == Mode.WHEEL) queueCenterWheels();
    }

    public Mode getMode() { return mode; }

    public void setSeconds(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long max = (long) maximumHours * 3600L + 59L * 60L + 59L;
        if (totalSeconds > max) totalSeconds = (int) Math.min(Integer.MAX_VALUE, max);
        setAll(totalSeconds);
    }

    /** Midnight is valid for a clock, while a zero duration remains invalid. */
    public int getTimeOfDaySeconds() {
        if (!timeOfDay) throw new IllegalStateException("Not a time-of-day editor");
        return getSecondsAllowZero();
    }

    /** Returns an exact second count and rejects a zero duration. */
    public int getSeconds() {
        int total = getSecondsAllowZero();
        if (total <= 0) throw new IllegalArgumentException("duration must be positive");
        return total;
    }

    private int getSecondsAllowZero() {
        int h, m, s;
        if (mode == Mode.WHEEL) {
            h = selected(hourWheel);
            m = selected(minuteWheel);
            s = selected(secondWheel);
        } else {
            try { hourSpinner.commitEdit(); minuteSpinner.commitEdit(); if (showSeconds) secondSpinner.commitEdit(); }
            catch (java.text.ParseException ex) { throw new IllegalArgumentException(com.eye.i18n.I18n.text("请检查时间输入。"), ex); }
            h = ((Number) hourSpinner.getValue()).intValue();
            m = ((Number) minuteSpinner.getValue()).intValue();
            s = ((Number) secondSpinner.getValue()).intValue();
        }
        long total = (long) h * 3600L + (long) m * 60L + s;
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("duration too large");
        return (int) total;
    }

    private void setAll(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = showSeconds ? totalSeconds % 60 : 0;
        h = Math.max(0, Math.min(maximumHours, h));
        syncing = true;
        try {
            hourSpinner.setValue(h);
            minuteSpinner.setValue(m);
            secondSpinner.setValue(s);
            select(hourWheel, h);
            select(minuteWheel, m);
            select(secondWheel, s);
        } finally {
            syncing = false;
        }
        if (mode == Mode.WHEEL) queueCenterWheels();
    }

    private void syncWheelFromSegmented() {
        if (syncing) return;
        syncing = true;
        try {
            select(hourWheel, ((Number) hourSpinner.getValue()).intValue());
            select(minuteWheel, ((Number) minuteSpinner.getValue()).intValue());
            select(secondWheel, ((Number) secondSpinner.getValue()).intValue());
        } finally {
            syncing = false;
        }
    }

    private void syncSegmentedFromWheel() {
        if (syncing) return;
        syncing = true;
        try {
            hourSpinner.setValue(selected(hourWheel));
            minuteSpinner.setValue(selected(minuteWheel));
            secondSpinner.setValue(selected(secondWheel));
        } finally {
            syncing = false;
        }
    }

    private JPanel buildSegmented() {
        JPanel row = Theme.panel(new GridLayout(1, showSeconds ? 3 : 2, 10, 0));
        row.add(segment(hourSpinner, timeOfDay ? com.eye.i18n.I18n.text("时") : com.eye.i18n.I18n.text("小时")));
        row.add(segment(minuteSpinner, timeOfDay ? com.eye.i18n.I18n.text("分") : com.eye.i18n.I18n.text("分钟")));
        if (showSeconds) row.add(segment(secondSpinner, com.eye.i18n.I18n.text("秒")));
        row.setBorder(new EmptyBorder(2, 0, 0, 0));
        return row;
    }

    private JPanel segment(JSpinner spinner, String unit) {
        JPanel p = Theme.panel(new BorderLayout(0, 5));
        p.add(spinner, BorderLayout.NORTH);
        JLabel label = Theme.label(unit, 10, Theme.SECONDARY);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        p.add(label, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildWheel() {
        JPanel row = Theme.panel(new GridLayout(1, showSeconds ? 3 : 2, 10, 0));
        row.add(wheelColumn(hourWheel, timeOfDay ? com.eye.i18n.I18n.text("时") : com.eye.i18n.I18n.text("小时")));
        row.add(wheelColumn(minuteWheel, timeOfDay ? com.eye.i18n.I18n.text("分") : com.eye.i18n.I18n.text("分钟")));
        if (showSeconds) row.add(wheelColumn(secondWheel, com.eye.i18n.I18n.text("秒")));
        return row;
    }

    private JPanel wheelColumn(JList<Integer> list, String unit) {
        Theme.Surface shell = new Theme.Surface(new BorderLayout(), Theme.SURFACE, Theme.LINE, 12);
        shell.setBorder(new EmptyBorder(3, 4, 3, 4));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setWheelScrollingEnabled(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().addChangeListener(e -> {
            JViewport viewport=scroll.getViewport();
            int height=viewport.getExtentSize().height;
            Object previous=viewport.getClientProperty("EyeRelax.wheelViewportHeight");
            // Layout/zoom can change the viewport after initial selection has been
            // centered. React only to its extent, never to ordinary scrolling.
            if(height>0&&(!(previous instanceof Integer)||((Integer)previous)!=height)){
                viewport.putClientProperty("EyeRelax.wheelViewportHeight",height);
                SwingUtilities.invokeLater(()->centerSelection(list));
            }
        });
        scroll.setOpaque(false);
        shell.add(scroll, BorderLayout.CENTER);

        JPanel out = Theme.panel(new BorderLayout(0, 5));
        out.add(shell, BorderLayout.CENTER);
        JLabel label = Theme.label(unit, 10, Theme.SECONDARY);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        out.add(label, BorderLayout.SOUTH);
        return out;
    }

    private static JSpinner spinner(int value, int min, int max, int step, int columns) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setFont(Theme.numbers(17));
        spinner.setBorder(BorderFactory.createLineBorder(Theme.LINE, 1, true));
        Dimension size = new Dimension(columns >= 4 ? 78 : 66, 42);
        spinner.setPreferredSize(size);
        spinner.setMinimumSize(new Dimension(54, 42));
        spinner.setMaximumSize(size);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        JFormattedTextField text = editor.getTextField();
        text.setColumns(columns);
        text.setFont(Theme.numbers(17));
        text.setHorizontalAlignment(SwingConstants.CENTER);
        text.setForeground(Theme.INK);
        text.setBackground(Theme.SURFACE);
        text.setBorder(new EmptyBorder(6, 5, 6, 5));
        text.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
        spinner.setEditor(editor);
        return spinner;
    }

    private static JList<Integer> wheelList(int min, int max) {
        int count = max - min + 1;
        JList<Integer> list = new JList<Integer>(new AbstractListModel<Integer>() {
            @Override public int getSize() { return count; }
            @Override public Integer getElementAt(int index) { return min + index; }
        }) {
            @Override public boolean getScrollableTracksViewportWidth(){return true;}
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(3);
        list.setFixedCellHeight(27);
        list.setFixedCellWidth(max > 99 ? 58 : 48);
        list.setOpaque(false);
        list.setFocusable(true);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBorder(new EmptyBorder(0, 2, 0, 2));
                label.setFont(Theme.numbers(selected ? 16 : 12).deriveFont((float)((selected?16:12)*ProportionalPanel.scaleOf(l))));
                int available=l.getWidth()-8;
                while(available>0&&label.getFontMetrics(label.getFont()).stringWidth(label.getText())>available&&label.getFont().getSize2D()>10)
                    label.setFont(label.getFont().deriveFont(label.getFont().getSize2D()-.5f));
                label.setForeground(selected ? Theme.GREEN : Theme.MUTED);
                label.setBackground(selected ? Theme.MINT : Theme.SURFACE);
                label.setOpaque(true);
                return label;
            }
        });
        list.addMouseWheelListener(e -> moveWheelSelection(list, e));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) SwingUtilities.invokeLater(() -> centerSelection(list));
        });
        list.setSelectedIndex(0);
        return list;
    }

    private static void moveWheelSelection(JList<Integer> list, MouseWheelEvent e) {
        if (!list.isEnabled()) return;
        int delta = e.getWheelRotation();
        if (delta == 0) return;
        int next = Math.max(0, Math.min(list.getModel().getSize() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(next);
        centerSelection(list);
        e.consume();
    }

    private void queueCenterWheels() {
        // Two EDT passes are intentional: CardLayout / JScrollPane often learns its
        // final viewport size one layout pass after the card becomes visible.
        SwingUtilities.invokeLater(() -> {
            centerSelection(hourWheel);
            centerSelection(minuteWheel);
            centerSelection(secondWheel);
            SwingUtilities.invokeLater(() -> {
                centerSelection(hourWheel);
                centerSelection(minuteWheel);
                centerSelection(secondWheel);
            });
        });
    }

    private static void centerSelection(JList<?> list) {
        int index = list.getSelectedIndex();
        if (index < 0 || !(list.getParent() instanceof JViewport viewport)) return;

        int viewportHeight = viewport.getExtentSize().height;
        int cellHeight = list.getFixedCellHeight();
        if (viewportHeight <= 0 || cellHeight <= 0) return;

        // Give the first and last values enough virtual breathing room to occupy
        // the visual center too. Without this padding, value 0 is forced to the
        // top edge and 59 to the bottom edge even when the selected row is valid.
        int padding = Math.max(0, (viewportHeight - cellHeight) / 2);
        Object oldPadding = list.getClientProperty("EyeRelax.wheelCenterPadding");
        if (!(oldPadding instanceof Integer) || ((Integer) oldPadding) != padding) {
            list.putClientProperty("EyeRelax.wheelCenterPadding", padding);
            list.setBorder(new EmptyBorder(padding, 0, padding, 0));
            list.revalidate();
            SwingUtilities.invokeLater(() -> centerSelection(list));
            return;
        }

        Rectangle cell = list.getCellBounds(index, index);
        if (cell == null) return;
        int targetY = cell.y + cell.height / 2 - viewportHeight / 2;
        int contentHeight = Math.max(list.getHeight(), list.getPreferredSize().height);
        int maxY = Math.max(0, contentHeight - viewportHeight);
        targetY = Math.max(0, Math.min(maxY, targetY));
        viewport.setViewPosition(new Point(0, targetY));
        list.repaint();
    }

    private static int selected(JList<Integer> list) {
        Integer value = list.getSelectedValue();
        return value == null ? 0 : value;
    }

    private static void select(JList<Integer> list, int value) {
        int idx = Math.max(0, Math.min(list.getModel().getSize() - 1, value));
        list.setSelectedIndex(idx);
        list.ensureIndexIsVisible(idx);
        SwingUtilities.invokeLater(() -> centerSelection(list));
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setChildrenEnabled(this, enabled);
    }

    private static void setChildrenEnabled(Container parent, boolean enabled) {
        for (Component c : parent.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof Container child) setChildrenEnabled(child, enabled);
        }
    }

    @Override public Dimension getPreferredSize() {
        int width = showSeconds ? 260 : 190;
        return mode == Mode.WHEEL ? new Dimension(width, 108) : new Dimension(width, 70);
    }
}
