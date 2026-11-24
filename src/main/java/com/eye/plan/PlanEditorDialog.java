package com.eye.plan;
import com.eye.plan.PlanDatePickerDialogs.*;

import com.eye.ui.theme.InlineErrors;
import com.eye.ui.theme.SettingsUi;

import com.eye.core.Config;
import com.eye.ui.theme.DurationInput;
import com.eye.ui.theme.Forms;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/** Plan editing and inline validation; saving reports through the provided refresh callback. */
final class PlanEditorDialog {
    private static final DateTimeFormatter DATE_FMT=DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Component parent;
    private final PlanManager manager;
    private final Config config;
    private final Runnable onChanged;
    PlanEditorDialog(Component parent,PlanManager manager,Config config,Runnable onChanged){this.parent=parent;this.manager=manager;this.config=config;this.onChanged=onChanged;}
    void show(PlanItem existing) {
        show(existing,false);
    }
    void showCopy(PlanItem source){
        PlanItem seed=source.copy();if(!seed.template){seed.kind=PlanItem.PlanKind.TODAY_ONLY;seed.selectedDates.clear();}
        else if(seed.kind==PlanItem.PlanKind.MULTI_DAY)seed.selectedDates.clear();
        show(seed,true);
    }
    private void show(PlanItem existing,boolean copy) {
        boolean editing = existing != null&&!copy;
        boolean seeded=existing!=null;
        JTextField nameField = new JTextField(seeded ? existing.name : "");
        DurationInput durationInput = new DurationInput();
        durationInput.setMode(configuredDurationMode());
        durationInput.setSeconds(seeded ? existing.plannedSeconds : 0);

        JComboBox<String> kindBox = new JComboBox<>(new String[]{com.eye.i18n.I18n.text("每日/周期计划"), com.eye.i18n.I18n.text("多日预定计划"), com.eye.i18n.I18n.text("仅当天计划")});
        JComboBox<String> recurringBox = new JComboBox<>(new String[]{com.eye.i18n.I18n.text("每天"), com.eye.i18n.I18n.text("周一至周五"), com.eye.i18n.I18n.text("周末"), com.eye.i18n.I18n.text("周末及自定义假期"), com.eye.i18n.I18n.text("按星期"), com.eye.calendar.DayCategory.WORKDAY.toString(), com.eye.calendar.DayCategory.NON_WORKDAY.toString(), com.eye.calendar.DayCategory.HOLIDAY.toString(), com.eye.calendar.DayCategory.NORMAL_WORKDAY.toString()});
        JTextField weekdaysField = new JTextField(seeded ? formatWeekdays(existing.weekdays) : "1,2,3,4,5");

        if (seeded) {
            kindBox.setSelectedIndex(switch (existing.kind) {
                case MULTI_DAY -> 1;
                case TODAY_ONLY -> 2;
                default -> 0;
            });
            recurringBox.setSelectedIndex(switch (existing.recurringRule) {
                case WEEKDAY -> 1;
                case WEEKEND -> 2;
                case HOLIDAY -> 3;
                case BY_WEEKDAY -> 4;
                case WORKDAY -> 5;case NON_WORKDAY -> 6;case PUBLIC_HOLIDAY -> 7;case NORMAL_WORKDAY -> 8;
                default -> 0;
            });
        }

        final Set<LocalDate> selectedDates = new LinkedHashSet<>(
                seeded && existing.selectedDates != null ? existing.selectedDates : Set.of());
        JLabel dateSummary = new JLabel(formatDateSummary(selectedDates));
        JButton pickDatesBtn = new JButton(com.eye.i18n.I18n.text("选择日期..."));
        pickDatesBtn.addActionListener(e -> {
            Set<LocalDate> picked = MultiDatePickerDialog.showDialog(SwingUtilities.getWindowAncestor(parent), selectedDates, manager.getToday());
            if (picked != null) {
                selectedDates.clear();selectedDates.addAll(picked);
                dateSummary.setText(formatDateSummary(selectedDates));
            }
        });

        JPanel form = Forms.plan(nameField, durationInput, kindBox, recurringBox, weekdaysField, pickDatesBtn, dateSummary);
        JPanel guidedForm=new JPanel(new BorderLayout(0,8));guidedForm.setOpaque(false);
        guidedForm.add(com.eye.tutorial.TutorialFormHelp.create(parent,java.util.List.of(
            new com.eye.tutorial.TutorialFormHelp.Hint("先填写计划名称，例如阅读。",nameField),
            new com.eye.tutorial.TutorialFormHelp.Hint("再设置计划需要投入的总时长。",durationInput),
            new com.eye.tutorial.TutorialFormHelp.Hint("选择每天重复、指定日期或仅今天，最后点击确定保存。",kindBox))),BorderLayout.NORTH);
        guidedForm.add(form);

        Runnable updateEnabled = () -> {
            int idx = kindBox.getSelectedIndex();
            boolean recurring = idx == 0;
            boolean multi = idx == 1;
            recurringBox.setEnabled(recurring);
            weekdaysField.setEnabled(recurring && recurringBox.getSelectedIndex() == 4);
            pickDatesBtn.setEnabled(multi);
            dateSummary.setEnabled(multi);
        };
        kindBox.addActionListener(e -> updateEnabled.run());
        recurringBox.addActionListener(e -> updateEnabled.run());
        updateEnabled.run();

        boolean accepted=SettingsUi.confirm(parent,editing?com.eye.i18n.I18n.text("修改计划"):com.eye.i18n.I18n.text("新增计划"),com.eye.i18n.I18n.text("安排专注时段。"),guidedForm,com.eye.i18n.I18n.text("确定"),()->{
            boolean valid=true;
            if(nameField.getText().trim().isEmpty()){InlineErrors.show(nameField,com.eye.i18n.I18n.text("计划名称不能为空。"));valid=false;}
            valid=InlineErrors.number(durationInput,durationInput::getSeconds,1,Integer.MAX_VALUE,com.eye.i18n.I18n.text("任务时长必须大于 0 秒。"))&&valid;
            if(kindBox.getSelectedIndex()==0&&recurringBox.getSelectedIndex()==4){
                boolean any=false;for(String part:weekdaysField.getText().split(","))try{int day=Integer.parseInt(part.trim());if(day>=1&&day<=7)any=true;}catch(NumberFormatException ignored){}
                if(!any){InlineErrors.show(weekdaysField,com.eye.i18n.I18n.text("请至少输入一个 1 到 7 的星期数字。"));valid=false;}
            }
            if(kindBox.getSelectedIndex()==1&&selectedDates.isEmpty()){InlineErrors.show(pickDatesBtn,com.eye.i18n.I18n.text("请先选择至少一个日期。"));valid=false;}
            return valid;
        });
        if(!accepted)return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(parent, com.eye.i18n.I18n.text("计划名称不能为空。"), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int durationSeconds;
        try {
            durationSeconds = durationInput.getSeconds();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent, com.eye.i18n.I18n.text("任务时长必须大于 0 秒。"), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        PlanItem.PlanKind kind;
        if (kindBox.getSelectedIndex() == 1) {
            kind = PlanItem.PlanKind.MULTI_DAY;
        } else if (kindBox.getSelectedIndex() == 2) {
            kind = PlanItem.PlanKind.TODAY_ONLY;
        } else {
            kind = PlanItem.PlanKind.RECURRING;
        }

        PlanItem.RecurringRule rule = PlanItem.RecurringRule.EVERY_DAY;
        if (kind == PlanItem.PlanKind.RECURRING) {
            switch (recurringBox.getSelectedIndex()) {
                case 1 -> rule = PlanItem.RecurringRule.WEEKDAY;
                case 2 -> rule = PlanItem.RecurringRule.WEEKEND;
                case 3 -> rule = PlanItem.RecurringRule.HOLIDAY;
                case 4 -> rule = PlanItem.RecurringRule.BY_WEEKDAY;
                case 5 -> rule = PlanItem.RecurringRule.WORKDAY;case 6 -> rule = PlanItem.RecurringRule.NON_WORKDAY;case 7 -> rule = PlanItem.RecurringRule.PUBLIC_HOLIDAY;case 8 -> rule = PlanItem.RecurringRule.NORMAL_WORKDAY;
                default -> rule = PlanItem.RecurringRule.EVERY_DAY;
            }
        }

        Set<Integer> weekdays = new LinkedHashSet<>();
        if (kind == PlanItem.PlanKind.RECURRING && rule == PlanItem.RecurringRule.BY_WEEKDAY) {
            for (String part : weekdaysField.getText().split(",")) {
                try {
                    int value = Integer.parseInt(part.trim());
                    if (value >= 1 && value <= 7) weekdays.add(value);
                } catch (Exception ignored) {
                }
            }
            if (weekdays.isEmpty()) {
                JOptionPane.showMessageDialog(parent, com.eye.i18n.I18n.text("请至少输入一个 1 到 7 的星期数字。"), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        if (kind == PlanItem.PlanKind.MULTI_DAY && selectedDates.isEmpty()) {
            JOptionPane.showMessageDialog(parent, com.eye.i18n.I18n.text("请先在日历中选择至少一个日期。"), com.eye.i18n.I18n.text("提示"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (editing) {
            PlanItem updated = manager.updatePlan(existing.id, name, durationSeconds, kind, rule, weekdays, selectedDates);
            if (updated == null) {
                JOptionPane.showMessageDialog(parent, com.eye.i18n.I18n.text("修改失败，计划可能已经被删除。"), com.eye.i18n.I18n.text("提示"), JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else {
            manager.addPlan(name, durationSeconds, kind, rule, weekdays, selectedDates);
        }
        onChanged.run();
    }
    private static String formatWeekdays(Set<Integer> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer day : weekdays) {
            if (day == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(day);
        }
        return sb.toString();
    }

    private String formatDateSummary(Set<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) return com.eye.i18n.I18n.text("未选择日期");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (LocalDate date : dates) {
            if (count > 0) sb.append(", ");
            sb.append(date.format(DATE_FMT));
            count++;
            if (count >= 4 && dates.size() > 4) {
                sb.append(com.eye.i18n.I18n.text(" ... 共 ")).append(dates.size()).append(com.eye.i18n.I18n.text(" 天"));
                break;
            }
        }
        return sb.toString();
    }

    private DurationInput.Mode configuredDurationMode() {
        return config != null && config.timeInputMode == Config.TimeInputMode.WHEEL
                ? DurationInput.Mode.WHEEL : DurationInput.Mode.SEGMENTED;
    }
}
