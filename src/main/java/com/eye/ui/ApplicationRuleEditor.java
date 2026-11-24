package com.eye.ui;

import com.eye.core.*;
import com.eye.ui.theme.*;
import java.awt.*;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;
import static com.eye.i18n.I18n.text;

/** Uses the same cards, switches, time controls and dialog footer as the settings page. */
final class ApplicationRuleEditor {
    private ApplicationRuleEditor() {}
    static void open(Component owner,Config config,List<ApplicationLimitRule> selected,
                     Function<List<ApplicationLimitRule>,String> save) {
        ApplicationLimitRule rule=selected.get(0);
        JPanel form=new SettingsUi.Column(){@Override public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();return new Dimension(620,d.height);}};
        JCheckBox enabled=new JCheckBox(),daily=new JCheckBox(),window=new JCheckBox();
        enabled.setSelected(rule.enabled());daily.setSelected(rule.daily()>0);window.setSelected(rule.window());
        SettingsUi.toggle(enabled,text("启用这项规则"));SettingsUi.toggle(daily,text("限制每日用量"));SettingsUi.toggle(window,text("限制使用时段"));
        form.add(SettingsUi.group(text("规则状态"),SettingsUi.row(text("启用这项规则"),text("关闭后暂停此应用的限制。"),enabled)));form.add(Box.createVerticalStrut(12));
        JToggleButton ban=new JToggleButton(text("完全禁用")),timed=new JToggleButton(text("按时间限制"));
        Theme.button(ban,"secondary","lock");Theme.button(timed,"secondary","timer");
        ButtonGroup modes=new ButtonGroup();modes.add(ban);modes.add(timed);ban.setSelected(rule.blocked());timed.setSelected(!rule.blocked());
        JPanel choices=Theme.panel(new GridLayout(1,2,12,0));choices.add(timed);choices.add(ban);form.add(choices);form.add(Box.createVerticalStrut(12));
        DurationInput budget=DurationInput.forDailyBudget(),start=DurationInput.forTimeOfDay(false),end=DurationInput.forTimeOfDay(false);
        DurationInput.Mode mode=config.timeInputMode==Config.TimeInputMode.WHEEL?DurationInput.Mode.WHEEL:DurationInput.Mode.SEGMENTED;
        for(DurationInput input:new DurationInput[]{budget,start,end})input.setMode(mode);
        budget.setSeconds((int)(rule.daily()>0?rule.daily()/1000:3600));start.setSeconds(rule.start()*60);end.setSeconds(rule.end()*60);
        JPanel budgetBox=new JPanel(new BorderLayout()){@Override public Dimension getPreferredSize(){return new Dimension(216,super.getPreferredSize().height);}};budgetBox.setOpaque(false);budgetBox.add(budget);
        JPanel budgetRow=SettingsUi.row(text("每天最多使用"),text("按学习日分界重置，阅读和视频也计时。"),budgetBox);
        JPanel dailyCard=SettingsUi.group(text("每日额度"),SettingsUi.row(text("限制每日用量"),text("额度用完后遮挡应用窗口。"),daily),budgetRow);
        JPanel interval=Theme.panel(new GridLayout(1,2,20,0));
        interval.add(timeColumn(text("开始（包含）"),start));interval.add(timeColumn(text("结束（不包含）"),end));
        JPanel windowCard=SettingsUi.group(text("允许使用时段"),SettingsUi.row(text("限制使用时段"),text("支持跨午夜，时段之外遮挡应用窗口。"),window),interval);
        form.add(dailyCard);form.add(Box.createVerticalStrut(12));form.add(windowCard);
        JPanel banCard=SettingsUi.group(text("完全禁用"),new SettingsUi.Text(text("应用打开时会被遮挡，返回桌面会关闭应用。"),12,Theme.SECONDARY));form.add(banCard);
        JLabel saveError=Theme.label(" ",11,Theme.DANGER);saveError.setMaximumSize(new Dimension(Integer.MAX_VALUE,24));form.add(Box.createVerticalStrut(10));form.add(saveError);
        Runnable controls=()->{
            boolean active=enabled.isSelected(),limited=!ban.isSelected();ban.setEnabled(active);timed.setEnabled(active);
            ban.putClientProperty("rest.selected",ban.isSelected());timed.putClientProperty("rest.selected",timed.isSelected());ban.repaint();timed.repaint();
            dailyCard.setVisible(limited);windowCard.setVisible(limited);banCard.setVisible(!limited);
            daily.setEnabled(active);window.setEnabled(active);budget.setEnabled(active&&daily.isSelected());start.setEnabled(active&&window.isSelected());end.setEnabled(active&&window.isSelected());
            budgetRow.setVisible(daily.isSelected());interval.setVisible(window.isSelected());form.revalidate();form.repaint();
            Window dialog=SwingUtilities.getWindowAncestor(form);
            if(dialog!=null){
                Rectangle bounds=dialog.getGraphicsConfiguration().getBounds();Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(dialog.getGraphicsConfiguration());
                int height=Math.min(Math.max(304,form.getPreferredSize().height+200),Math.max(220,bounds.height-insets.top-insets.bottom-60));
                dialog.setSize(dialog.getWidth(),height);
                dialog.setLocation(dialog.getX(),Math.max(bounds.y+insets.top,Math.min(dialog.getY(),bounds.y+bounds.height-insets.bottom-height)));dialog.validate();
            }
        };
        for(AbstractButton button:new AbstractButton[]{enabled,daily,window,ban,timed})button.addActionListener(e->controls.run());controls.run();
        java.util.ArrayList<com.eye.tutorial.TutorialFormHelp.Hint> hints=new java.util.ArrayList<>();
        hints.add(new com.eye.tutorial.TutorialFormHelp.Hint("完全禁用始终拦截；按时间限制可组合每日额度与允许时段。",choices));
        hints.add(new com.eye.tutorial.TutorialFormHelp.Hint("每日额度按学习日分界重置；用完后拦截应用。",dailyCard));
        hints.add(new com.eye.tutorial.TutorialFormHelp.Hint("允许时段之外不能使用，支持跨午夜；确认后保存规则。",windowCard));
        hints.add(new com.eye.tutorial.TutorialFormHelp.Hint("完全禁用也会先显示原因，返回桌面后才关闭应用。",banCard));
        form.add(com.eye.tutorial.TutorialFormHelp.create(owner,hints));
        SettingsUi.confirm(owner,selected.size()==1?rule.name():text("批量设置应用限制"),
                "",form,text("保存规则"),()->{
            saveError.setText(" ");long millis=rule.daily();int from=rule.start(),to=rule.end();
            if(!ban.isSelected()){
                if(enabled.isSelected()&&!daily.isSelected()&&!window.isSelected()){saveError.setText(text("请选择每日额度或允许时段，或暂停这项规则。"));return false;}
                if(daily.isSelected()){
                    if(!InlineErrors.number(budget,budget::getSeconds,60,86400,text("每日额度应在 1 分钟至 24 小时之间。")))return false;
                    millis=budget.getSeconds()*1000L;
                }else millis=0;
                if(window.isSelected()){
                    try{from=start.getTimeOfDaySeconds()/60;}catch(Exception ex){return InlineErrors.show(start,text("请输入有效时刻。"));}
                    try{to=end.getTimeOfDaySeconds()/60;}catch(Exception ex){return InlineErrors.show(end,text("请输入有效时刻。"));}
                    if(from==to)return InlineErrors.show(end,text("开始与结束不能相同。"));
                }
            }
            final long amount=millis;final int first=from,last=to;
            List<ApplicationLimitRule> changed=selected.stream().map(old->new ApplicationLimitRule(old.path(),old.name(),enabled.isSelected(),ban.isSelected(),amount,window.isSelected(),first,last)).toList();
            String error=save.apply(changed);if(error!=null){saveError.setText(error);return false;}return true;
        });
    }
    private static JComponent timeColumn(String name,DurationInput input){
        JPanel panel=Theme.panel(new BorderLayout(0,10));JLabel label=Theme.label(name,12,Theme.SECONDARY);label.setIcon(Icons.of("clock",16,Theme.GREEN));label.setIconTextGap(7);panel.add(label,BorderLayout.NORTH);panel.add(input);panel.setBorder(BorderFactory.createEmptyBorder(12,0,6,0));return panel;
    }
}
