package com.eye.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Layout-only helpers. Callers own values, validation and persistence. */
public final class Forms {
    private Forms() {}

    public static JPanel preferences(DurationInput work, DurationInput relax,
                                     JRadioButton a, JRadioButton b, JRadioButton c,
                                     JCheckBox launchChooser, JCheckBox autoStartAfterSelection,
                                     JCheckBox autoContinue, JCheckBox taskCompletionNotification) {
        JPanel form = Theme.column();
        form.setPreferredSize(new Dimension(640, 700));
        addHeader(form, "PREFERENCES", com.eye.i18n.I18n.text("找到适合自己的节奏。"), com.eye.i18n.I18n.text("调整每轮工作和休息时长，选择休息方式。"));

        JPanel durations = Theme.panel(new GridLayout(1, 2, 16, 0));
        durations.add(durationField(com.eye.i18n.I18n.text("工作时长"), "timer", work));
        durations.add(durationField(com.eye.i18n.I18n.text("休息时长"), "leaf", relax));
        durations.setAlignmentX(Component.LEFT_ALIGNMENT);
        durations.setMaximumSize(new Dimension(Integer.MAX_VALUE, 188));
        form.add(durations);
        form.add(Box.createVerticalStrut(22));

        JLabel mode = Theme.heading(com.eye.i18n.I18n.text("休息模式"), 13);
        mode.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(mode);
        form.add(Box.createVerticalStrut(9));
        for (JRadioButton radio : new JRadioButton[]{a, b, c}) {
            Theme.Surface row = new Theme.Surface(new BorderLayout(), Theme.SURFACE, radio.isSelected() ? Theme.SAGE : Theme.LINE, 13);
            row.setBorder(new EmptyBorder(10, 12, 10, 12));
            row.add(radio, BorderLayout.CENTER);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            radio.setFont(Theme.font(12));
            radio.setIconTextGap(8);
            radio.addItemListener(e -> row.setOutline(radio.isSelected() ? Theme.SAGE : Theme.LINE));
            form.add(row);
            form.add(Box.createVerticalStrut(7));
        }

        form.add(Box.createVerticalStrut(13));
        JLabel planFlow = Theme.heading(com.eye.i18n.I18n.text("启动与计划衔接"), 13);
        planFlow.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(planFlow);
        form.add(Box.createVerticalStrut(8));
        for (JCheckBox box : new JCheckBox[]{launchChooser, autoStartAfterSelection, autoContinue}) {
            box.setFont(Theme.font(12));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(box);
            form.add(Box.createVerticalStrut(8));
        }

        form.add(Box.createVerticalStrut(8));
        JLabel reminder = Theme.heading(com.eye.i18n.I18n.text("计划完成提醒"), 13);
        reminder.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(reminder);
        form.add(Box.createVerticalStrut(8));
        taskCompletionNotification.setFont(Theme.font(12));
        taskCompletionNotification.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(taskCompletionNotification);

        return adaptive(form);
    }

    private static JPanel durationField(String title, String icon, DurationInput inputControl) {
        Theme.Surface card = Theme.card(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel name = Theme.label(title, 12, Theme.SECONDARY);
        name.setIcon(Icons.of(icon, 16, Theme.GREEN));
        name.setIconTextGap(7);
        name.setLabelFor(inputControl);
        card.add(name, BorderLayout.NORTH);
        card.add(inputControl, BorderLayout.CENTER);
        return card;
    }

    public static JPanel plan(JTextField name, DurationInput duration, JComboBox<String> kind,
                              JComboBox<String> recurring, JTextField weekdays,
                              JButton pickDates, JLabel summary) {
        JPanel form = Theme.column();
        addHeader(form, "MAKE ROOM FOR WHAT MATTERS", com.eye.i18n.I18n.text("给重要的事，留一段时间。"), com.eye.i18n.I18n.text("填写计划名称和目标时长，再选择重复规则或执行日期。"));

        Theme.field(name); Theme.field(weekdays);
        name.setToolTipText(null);
        duration.setToolTipText(null);
        weekdays.setToolTipText(null);
        Theme.smallButton(pickDates, "secondary", "calendar");
        JPanel grid = Theme.panel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(5, 0, 5, 0);
        fieldRow(grid, gc, 0, com.eye.i18n.I18n.text("计划名称"), name);
        fieldRow(grid, gc, 1, com.eye.i18n.I18n.text("任务时长"), duration);
        fieldRow(grid, gc, 2, com.eye.i18n.I18n.text("计划类型"), kind);
        fieldRow(grid, gc, 3, com.eye.i18n.I18n.text("周期规则"), recurring);
        fieldRow(grid, gc, 4, com.eye.i18n.I18n.text("星期 · 1–7"), weekdays);
        JPanel dates = Theme.panel(new BorderLayout(9, 0));
        dates.add(pickDates, BorderLayout.WEST);
        summary.setFont(Theme.font(11));
        summary.setForeground(Theme.SECONDARY);
        summary.setPreferredSize(new Dimension(265, 38));
        dates.add(summary, BorderLayout.CENTER);
        fieldRow(grid, gc, 5, com.eye.i18n.I18n.text("预定日期"), dates);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 380));
        form.add(grid);
        JLabel calendarPolicy=Theme.label("<html><div style='width:430px'>"+com.eye.calendar.ChineseCalendar.policyNote()+"</div></html>",11,Theme.SECONDARY);
        calendarPolicy.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(calendarPolicy);
        Runnable showPolicy = () -> calendarPolicy.setVisible(kind.getSelectedIndex() == 0 && recurring.getSelectedIndex() >= 5);
        kind.addActionListener(e -> showPolicy.run());
        recurring.addActionListener(e -> showPolicy.run());
        form.setPreferredSize(new Dimension(620, form.getPreferredSize().height));
        showPolicy.run();
        return adaptive(form);
    }

    private static JPanel adaptive(JPanel form) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension desired = form.getPreferredSize();
        int height = Math.min(desired.height, Math.max(220, screen.height - 210));
        int width = Math.min(desired.width, Math.max(300, screen.width - 100));
        if (height == desired.height && width == desired.width) return form;
        JPanel wrapper = Theme.panel(new BorderLayout());
        JScrollPane scroll = new JScrollPane(form);
        Theme.scroll(scroll);
        wrapper.add(scroll, BorderLayout.CENTER);
        wrapper.setPreferredSize(new Dimension(width, height));
        return wrapper;
    }

    private static void fieldRow(JPanel p, GridBagConstraints gc, int row, String text, JComponent field) {
        gc.gridy = row; gc.gridx = 0; gc.weightx = 0; gc.insets = new Insets(5, 0, 5, 20);
        JLabel label = Theme.label(text, 12, Theme.SECONDARY);
        label.setLabelFor(field);field.putClientProperty("tutorial.label",label);
        p.add(label, gc);
        gc.gridx = 1; gc.weightx = 1; gc.insets = new Insets(5, 0, 5, 0);
        p.add(field, gc);
    }

    private static void addHeader(JPanel panel, String eyebrow, String title, String subtitle) {
        JLabel a = Theme.eyebrow(eyebrow), b = Theme.heading(title, 23), c = Theme.label(subtitle, 12, Theme.SECONDARY);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(a);
        panel.add(Box.createVerticalStrut(8));
        panel.add(b);
        panel.add(Box.createVerticalStrut(7));
        panel.add(c);
        panel.add(Box.createVerticalStrut(22));
    }
}
