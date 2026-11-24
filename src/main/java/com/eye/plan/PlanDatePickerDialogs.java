package com.eye.plan;

import com.eye.ui.theme.Theme;
import javax.swing.border.EmptyBorder;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Calendar dialogs shared by plan editing and historical range selection. */
final class PlanDatePickerDialogs {
 private PlanDatePickerDialogs(){}
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Single historical day; opening or cancelling never changes the selected day. */
    static final class SingleDatePickerDialog extends JDialog {
        private YearMonth month;
        private final LocalDate latest;
        private LocalDate selected;
        private boolean confirmed;
        private final JLabel heading=Theme.heading("",18);
        private final JButton next=new JButton("›");
        private final List<com.eye.calendar.CalendarDayButton> days=new ArrayList<>();
        private SingleDatePickerDialog(Window owner,LocalDate initial,LocalDate latest){
            super(owner,com.eye.i18n.I18n.text("选择日期"),ModalityType.APPLICATION_MODAL);
            this.latest=latest;selected=initial;month=YearMonth.from(initial);Theme.window(this);
            JPanel root=Theme.panel(new BorderLayout(0,16));root.setOpaque(true);root.setBackground(Theme.BG);root.setBorder(new EmptyBorder(20,20,18,20));
            JPanel nav=Theme.panel(new BorderLayout(12,0));JButton prev=new JButton("‹");heading.setHorizontalAlignment(SwingConstants.CENTER);
            Theme.smallButton(prev,"secondary",null);Theme.smallButton(next,"secondary",null);
            prev.setToolTipText(com.eye.i18n.I18n.text("上个月"));next.setToolTipText(com.eye.i18n.I18n.text("下个月"));
            prev.addActionListener(e->{month=month.minusMonths(1);refresh();});next.addActionListener(e->{month=month.plusMonths(1);refresh();});
            nav.add(prev,BorderLayout.WEST);nav.add(heading);nav.add(next,BorderLayout.EAST);root.add(nav,BorderLayout.NORTH);
            JPanel grid=Theme.panel(new GridLayout(7,7,4,4));
            for(String name:new String[]{"一","二","三","四","五","六","日"}){JLabel label=Theme.label(com.eye.i18n.I18n.text(name),12,Theme.SECONDARY);label.setHorizontalAlignment(SwingConstants.CENTER);grid.add(label);}
            for(int i=0;i<42;i++){
                var button=new com.eye.calendar.CalendarDayButton();days.add(button);grid.add(button);
                button.addActionListener(e->{selected=(LocalDate)button.getClientProperty("date");refresh();});
            }
            root.add(grid);JButton cancel=new JButton(com.eye.i18n.I18n.text("取消")),ok=new JButton(com.eye.i18n.I18n.text("确定"));
            Theme.smallButton(cancel,"secondary",null);Theme.smallButton(ok,"primary","check");
            cancel.addActionListener(e->dispose());ok.addActionListener(e->{confirmed=true;dispose();});
            JPanel footer=Theme.panel(new FlowLayout(FlowLayout.RIGHT,8,0));footer.add(cancel);footer.add(ok);root.add(footer,BorderLayout.SOUTH);
            setContentPane(root);getRootPane().setDefaultButton(ok);root.registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);refresh();pack();setResizable(false);setLocationRelativeTo(owner);
        }
        private void refresh(){
            heading.setText(month.format(DateTimeFormatter.ofPattern(com.eye.i18n.I18n.isEnglish()?"MMMM yyyy":"yyyy年M月",com.eye.i18n.I18n.isEnglish()?java.util.Locale.ENGLISH:java.util.Locale.CHINA)));
            next.setEnabled(month.isBefore(YearMonth.from(latest)));LocalDate first=month.atDay(1);LocalDate start=first.minusDays(first.getDayOfWeek().getValue()-1);
            for(int i=0;i<days.size();i++){
                LocalDate date=start.plusDays(i);var button=days.get(i);boolean inMonth=YearMonth.from(date).equals(month);
                button.setDate(inMonth?date:null);button.putClientProperty("date",date);button.putClientProperty("rest.today",inMonth&&date.equals(latest));
                button.setEnabled(inMonth&&!date.isAfter(latest));button.setSelected(inMonth&&date.equals(selected));
            }
        }
        static LocalDate showDialog(Window owner,LocalDate initial,LocalDate latest){
            SingleDatePickerDialog dialog=new SingleDatePickerDialog(owner,initial,latest);dialog.setVisible(true);return dialog.confirmed?dialog.selected:null;
        }
    }
    static final class DateRange {
        final LocalDate from;
        final LocalDate to;
        DateRange(LocalDate from, LocalDate to) { this.from = from; this.to = to; }
    }

    /** Only the historical range picker changes; the plan scheduling calendar is untouched. */
    static final class RangeDatePickerDialog extends JDialog {
        private YearMonth visibleMonth;
        private LocalDate start, end;
        private final LocalDate latestAllowed;
        private final JLabel summaryLabel = Theme.label("", 12, Theme.SECONDARY);
        private final JLabel[] monthLabels = {Theme.heading("",15), Theme.heading("",15)};
        private final List<JToggleButton> dayButtons = new ArrayList<>();
        private final JButton okBtn = new JButton(com.eye.i18n.I18n.text("应用范围"));
        private boolean confirmed;
        private RangeDatePickerDialog(Window owner, LocalDate initialFrom, LocalDate initialTo, LocalDate today) {
            super(owner,com.eye.i18n.I18n.text("选择日期范围"),ModalityType.APPLICATION_MODAL);
            this.latestAllowed = (today == null ? LocalDate.now() : today).minusDays(1);
            if(initialFrom!=null && initialTo!=null && initialFrom.isAfter(initialTo)) {
                LocalDate swap=initialFrom;initialFrom=initialTo;initialTo=swap;
            }
            start=initialFrom;end=initialTo;
            visibleMonth=YearMonth.from(initialTo==null?latestAllowed:initialTo).minusMonths(1);
            Theme.window(this);
            JPanel root=new JPanel(new BorderLayout(0,18));root.setBackground(Theme.BG);root.setBorder(new EmptyBorder(24,24,20,24));
            JPanel intro=Theme.column();
            intro.add(Theme.heading(com.eye.i18n.I18n.text("选择日期范围"),18));intro.add(Box.createVerticalStrut(5));
            intro.add(Theme.label(com.eye.i18n.I18n.text("先选开始，再选结束。跨月也可以连续选择。"),12,Theme.SECONDARY));
            JPanel presets=Theme.panel(new FlowLayout(FlowLayout.LEFT,6,0));
            for(int days:new int[]{7,30,90}) {
                JButton button=new JButton(com.eye.i18n.I18n.text("最近 ")+days+com.eye.i18n.I18n.text(" 天"));Theme.smallButton(button,"secondary",null);
                button.addActionListener(e->{start=latestAllowed.minusDays(Math.max(0, days-1));end=latestAllowed;visibleMonth=YearMonth.from(end).minusMonths(1);refreshCalendar();});presets.add(button);
            }
            intro.add(Box.createVerticalStrut(16));intro.add(presets);root.add(intro,BorderLayout.NORTH);
            JPanel calendars=Theme.panel(new GridLayout(1,2,16,0));
            for(int month=0;month<2;month++) {
                final int position=month;
                Theme.Surface card=Theme.card(new BorderLayout(0,12));card.setBorder(new EmptyBorder(15,14,16,14));
                JPanel nav=Theme.panel(new BorderLayout());monthLabels[month].setHorizontalAlignment(SwingConstants.CENTER);
                JButton move=new JButton();Theme.smallButton(move,"ghost",month==0?"prev":"next");
                move.setToolTipText(month==0?com.eye.i18n.I18n.text("向前一个月"):com.eye.i18n.I18n.text("向后一个月"));move.setPreferredSize(new Dimension(30,30));
                move.addActionListener(e->{visibleMonth=visibleMonth.plusMonths(position==0?-1:1);refreshCalendar();});
                nav.add(monthLabels[month],BorderLayout.CENTER);nav.add(move,month==0?BorderLayout.WEST:BorderLayout.EAST);
                JPanel days=Theme.panel(new GridLayout(7,7,3,3));
                for(String label:new String[]{com.eye.i18n.I18n.text("一"),com.eye.i18n.I18n.text("二"),com.eye.i18n.I18n.text("三"),com.eye.i18n.I18n.text("四"),com.eye.i18n.I18n.text("五"),com.eye.i18n.I18n.text("六"),com.eye.i18n.I18n.text("日")}) {
                    JLabel day=Theme.label(label,11,Theme.MUTED);day.setHorizontalAlignment(SwingConstants.CENTER);days.add(day);
                }
                for(int i=0;i<42;i++){JToggleButton button=new RangeDayButton();dayButtons.add(button);days.add(button);}
                card.add(nav,BorderLayout.NORTH);card.add(days,BorderLayout.CENTER);card.setPreferredSize(new Dimension(390,370));calendars.add(card);
            }
            root.add(calendars,BorderLayout.CENTER);
            JPanel bottom=Theme.panel(new BorderLayout(12,0));
            summaryLabel.setBorder(new EmptyBorder(0,0,0,5));bottom.add(summaryLabel,BorderLayout.CENTER);
            JPanel buttons=Theme.panel(new FlowLayout(FlowLayout.RIGHT,8,0));JButton cancel=new JButton(com.eye.i18n.I18n.text("取消"));
            Theme.button(cancel,"secondary",null);Theme.button(okBtn,"primary","check");
            cancel.addActionListener(e->setVisible(false));okBtn.addActionListener(e->{if(start!=null){if(end==null)end=start;confirmed=true;setVisible(false);}});
            buttons.add(cancel);buttons.add(okBtn);bottom.add(buttons,BorderLayout.EAST);root.add(bottom,BorderLayout.SOUTH);
            setContentPane(root);setResizable(false);pack();setLocationRelativeTo(owner);refreshCalendar();
        }
        private void choose(LocalDate date) {
            if(date.isAfter(latestAllowed))return;
            if(start==null||end!=null){start=date;end=null;}
            else if(date.isBefore(start)){end=start;start=date;}else end=date;
            refreshCalendar();
        }
        private void refreshCalendar() {
            for(int month=0;month<2;month++) {
                YearMonth m=visibleMonth.plusMonths(month);monthLabels[month].setText(m.getYear()+com.eye.i18n.I18n.text(" 年 ")+m.getMonthValue()+com.eye.i18n.I18n.text(" 月"));
                LocalDate first=m.atDay(1);LocalDate gridStart=first.minusDays(first.getDayOfWeek().getValue()-1);
                for(int i=0;i<42;i++) {
                    LocalDate d=gridStart.plusDays(i);JToggleButton b=dayButtons.get(month*42+i);
                    for(var listener:b.getActionListeners())b.removeActionListener(listener);
                    boolean inMonth=YearMonth.from(d).equals(m);((com.eye.calendar.CalendarDayButton)b).setDate(inMonth?d:null);
                    b.setEnabled(inMonth&&!d.isAfter(latestAllowed));
                    boolean selected=inMonth&&start!=null&&!d.isBefore(start)&&(end==null?d.equals(start):!d.isAfter(end));
                    b.setSelected(selected);b.putClientProperty("endpoint",selected&&(d.equals(start)||d.equals(end)));
                    b.setToolTipText(null);
                    if(b.isEnabled())b.addActionListener(e->choose(d));b.repaint();
                }
            }
            okBtn.setEnabled(start!=null);
            summaryLabel.setText(start==null?com.eye.i18n.I18n.text("选择开始日期"):end==null?start.format(DATE_FMT)+com.eye.i18n.I18n.text("  ·  请选择结束日期"):
                    "<html>"+start.format(DATE_FMT)+" — "+end.format(DATE_FMT)+com.eye.i18n.I18n.text("<br><font color='#687970'>共 ")+
                    (java.time.temporal.ChronoUnit.DAYS.between(start,end)+1)+com.eye.i18n.I18n.text(" 天</font></html>"));
        }
        static DateRange showDialog(Window owner,LocalDate from,LocalDate to, LocalDate today) {
            RangeDatePickerDialog dialog=new RangeDatePickerDialog(owner,from,to,today);dialog.setVisible(true);
            DateRange result=dialog.confirmed?new DateRange(dialog.start,dialog.end==null?dialog.start:dialog.end):null;
            dialog.dispose();return result;
        }
        private static final class RangeDayButton extends com.eye.calendar.CalendarDayButton {}
    }

    static final class MultiDatePickerDialog extends JDialog {
        private YearMonth visibleMonth;
        private final Set<LocalDate> selectedDates = new LinkedHashSet<>();
        private final LocalDate today;
        private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel summaryLabel = new JLabel();
        private final List<JToggleButton> dayButtons = new ArrayList<>();
        private boolean confirmed;

        private MultiDatePickerDialog(Window owner, Set<LocalDate> initialDates, LocalDate today) {
            super(owner, com.eye.i18n.I18n.text("选择日期"), ModalityType.APPLICATION_MODAL);
            this.today = today == null ? LocalDate.now() : today;
            this.visibleMonth = YearMonth.from(this.today);
            if (initialDates != null) {
                this.selectedDates.addAll(initialDates);
                if (!initialDates.isEmpty()) {
                    this.visibleMonth = YearMonth.from(initialDates.iterator().next());
                }
            }

            Theme.window(this);
            JPanel header = Theme.panel(new BorderLayout(8, 0));
            monthLabel.setFont(Theme.numbers(25));monthLabel.setForeground(Theme.INK);
            summaryLabel.setFont(Theme.font(11));summaryLabel.setForeground(Theme.SECONDARY);
            summaryLabel.setPreferredSize(new Dimension(300,44));
            JButton prevBtn = new JButton("<");
            JButton nextBtn = new JButton(">");
            Theme.button(prevBtn,"secondary","prev");prevBtn.setText("");
            Theme.button(nextBtn,"secondary","next");nextBtn.setText("");
            prevBtn.setToolTipText(com.eye.i18n.I18n.text("上个月"));nextBtn.setToolTipText(com.eye.i18n.I18n.text("下个月"));
            prevBtn.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("上个月"));
            nextBtn.getAccessibleContext().setAccessibleName(com.eye.i18n.I18n.text("下个月"));
            prevBtn.addActionListener(e -> {
                visibleMonth = visibleMonth.minusMonths(1);
                refreshCalendar();
            });
            nextBtn.addActionListener(e -> {
                visibleMonth = visibleMonth.plusMonths(1);
                refreshCalendar();
            });
            header.add(prevBtn, BorderLayout.WEST);
            header.add(monthLabel, BorderLayout.CENTER);
            header.add(nextBtn, BorderLayout.EAST);

            JPanel weekHeader = Theme.panel(new GridLayout(1, 7, 6, 6));
            for (String text : new String[]{com.eye.i18n.I18n.text("一"), com.eye.i18n.I18n.text("二"), com.eye.i18n.I18n.text("三"), com.eye.i18n.I18n.text("四"), com.eye.i18n.I18n.text("五"), com.eye.i18n.I18n.text("六"), com.eye.i18n.I18n.text("日")}) {
                JLabel day=Theme.label(text,12,Theme.SECONDARY);day.setHorizontalAlignment(SwingConstants.CENTER);
                weekHeader.add(day);
            }

            JPanel grid = Theme.panel(new GridLayout(6, 7, 6, 6));
            grid.setPreferredSize(new Dimension(546,342));
            for (int i = 0; i < 42; i++) {
                JToggleButton btn = new com.eye.calendar.CalendarDayButton();
                btn.setMargin(new Insets(4, 4, 4, 4));
                Theme.button(btn,"calendar",null);btn.setFont(Theme.numbers(14));
                btn.setBorder(new EmptyBorder(8,8,8,8));
                dayButtons.add(btn);
                grid.add(btn);
            }

            JButton okBtn = new JButton(com.eye.i18n.I18n.text("确定"));
            okBtn.addActionListener(e -> {
                confirmed = true;
                setVisible(false);
            });
            JButton cancelBtn = new JButton(com.eye.i18n.I18n.text("取消"));
            Theme.button(okBtn,"primary","check");Theme.button(cancelBtn,"secondary",null);
            cancelBtn.addActionListener(e -> setVisible(false));

            JPanel bottom = Theme.panel(new BorderLayout(8, 8));
            bottom.add(summaryLabel, BorderLayout.CENTER);
            JPanel bottomButtons = Theme.panel(new FlowLayout(FlowLayout.RIGHT,6,0));
            bottomButtons.add(okBtn);
            bottomButtons.add(cancelBtn);
            bottom.add(bottomButtons, BorderLayout.EAST);

            JPanel center = Theme.panel(new BorderLayout(0, 12));
            JPanel calendarTop=Theme.column();calendarTop.add(weekHeader);
            JPanel shortcuts=Theme.panel(new FlowLayout(FlowLayout.LEFT,5,5));
            JComboBox<com.eye.calendar.DayCategory> category=new JComboBox<>(com.eye.calendar.DayCategory.values());
            JButton select=new JButton(com.eye.i18n.I18n.text("选中本月")),clear=new JButton(com.eye.i18n.I18n.text("清空本月"));
            Theme.smallButton(select,"secondary",null);Theme.smallButton(clear,"ghost",null);
            select.addActionListener(e->{
                if(category.getSelectedItem()==com.eye.calendar.DayCategory.HOLIDAY&&!com.eye.calendar.ChineseCalendar.hasHolidaySchedule(visibleMonth.getYear())){
                    summaryLabel.setForeground(new Color(0xB7493E));summaryLabel.setText(com.eye.i18n.I18n.text("此年份尚未收录假期安排，已保留原选择。"));return;
                }
                selectedDates.removeIf(d->YearMonth.from(d).equals(visibleMonth));
                for(int day=1;day<=visibleMonth.lengthOfMonth();day++){LocalDate d=visibleMonth.atDay(day);if(((com.eye.calendar.DayCategory)category.getSelectedItem()).matches(d))selectedDates.add(d);}refreshCalendar();});
            clear.addActionListener(e->{selectedDates.removeIf(d->YearMonth.from(d).equals(visibleMonth));refreshCalendar();});
            shortcuts.add(category);shortcuts.add(select);shortcuts.add(clear);
            JPanel calendarBody=Theme.panel(new BorderLayout(0,8));calendarBody.add(calendarTop,BorderLayout.NORTH);calendarBody.add(grid);
            center.add(shortcuts,BorderLayout.NORTH);center.add(calendarBody);
            center.add(Theme.label("<html><div style='width:420px'>"+com.eye.calendar.ChineseCalendar.policyNote()+"</div></html>",11,Theme.SECONDARY),BorderLayout.SOUTH);
            

            JPanel root = new JPanel(new BorderLayout(8, 20));
            root.setBackground(Theme.BG);
            root.setBorder(new EmptyBorder(24,24,20,24));
            JPanel heading=Theme.panel(new BorderLayout(0,18));
            JPanel instructions=Theme.column();
            instructions.add(Theme.heading(com.eye.i18n.I18n.text("选择预定日期"),18));instructions.add(Box.createVerticalStrut(5));
            instructions.add(Theme.label(com.eye.i18n.I18n.text("点击日期可多选，再次点击取消；支持跨月选择。"),11,Theme.SECONDARY));
            heading.add(instructions,BorderLayout.NORTH);heading.add(header,BorderLayout.SOUTH);
            root.add(heading, BorderLayout.NORTH);
            root.add(center, BorderLayout.CENTER);
            root.add(bottom, BorderLayout.SOUTH);
            JScrollPane viewport = new JScrollPane(root);
            Theme.scroll(viewport);
            setContentPane(viewport);
            pack();
            Theme.fitInitialWindow(this, getWidth(), getHeight());
            setLocationRelativeTo(owner);
            refreshCalendar();
        }

        private void refreshCalendar() {
            monthLabel.setText(visibleMonth.getYear() + "-" + String.format("%02d", visibleMonth.getMonthValue()));
            LocalDate first = visibleMonth.atDay(1);
            int startIndex = first.getDayOfWeek().getValue() - 1;
            int length = visibleMonth.lengthOfMonth();

            for (int i = 0; i < dayButtons.size(); i++) {
                JToggleButton btn = dayButtons.get(i);
                ((com.eye.calendar.CalendarDayButton)btn).setDate(null);
                btn.setEnabled(false);
                for (java.awt.event.ActionListener listener : btn.getActionListeners()) {
                    btn.removeActionListener(listener);
                }
                btn.setSelected(false);
                btn.putClientProperty("rest.today", false);
            }

            for (int day = 1; day <= length; day++) {
                int index = startIndex + day - 1;
                LocalDate date = visibleMonth.atDay(day);
                JToggleButton btn = dayButtons.get(index);
                ((com.eye.calendar.CalendarDayButton)btn).setDate(date);
                btn.putClientProperty("rest.today", date.equals(today));
                btn.setToolTipText(null);
                btn.setEnabled(true);
                btn.setSelected(selectedDates.contains(date));
                btn.addActionListener(e -> {
                    if (btn.isSelected()) {
                        selectedDates.add(date);
                    } else {
                        selectedDates.remove(date);
                    }
                    refreshSummary();
                });
            }
            refreshSummary();
        }

        private void refreshSummary() {
            summaryLabel.setForeground(Theme.SECONDARY);
            if (selectedDates.isEmpty()) {
                summaryLabel.setText(com.eye.i18n.I18n.text("未选择日期"));
                return;
            }
            StringBuilder sb = new StringBuilder(com.eye.i18n.I18n.text("已选择："));
            int count = 0;
            for (LocalDate date : selectedDates) {
                if (count > 0) sb.append(", ");
                sb.append(date.format(DATE_FMT));
                count++;
                if (count >= 3 && selectedDates.size() > 3) {
                    sb.append(com.eye.i18n.I18n.text(" ... 共 ")).append(selectedDates.size()).append(com.eye.i18n.I18n.text(" 天"));
                    break;
                }
            }
            summaryLabel.setText(sb.toString());
        }

        private Set<LocalDate> getSelectedDates() {
            return confirmed ? new LinkedHashSet<>(selectedDates) : null;
        }

        static Set<LocalDate> showDialog(Window owner, Set<LocalDate> initialDates, LocalDate today) {
            MultiDatePickerDialog dialog = new MultiDatePickerDialog(owner, initialDates, today);
            dialog.setVisible(true);
            return dialog.getSelectedDates();
        }
    }

}
