package com.eye.reminder;

import com.eye.i18n.I18n;
import com.eye.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Editable delays between occurrences. Timing and persistence belong to ReminderItem. */
final class ReminderSequenceInput extends SettingsUi.Column {
    private final List<Integer> delays=new ArrayList<>();
    private final DurationInput.Mode mode;
    private final SettingsUi.Column rows=new SettingsUi.Column();
    private final SettingsUi.Text hint=new SettingsUi.Text(I18n.text("每次间隔从上一次提醒算起；第一段从保存时算起。全部提醒完成后停止。"),11,Theme.SECONDARY);
    ReminderSequenceInput(DurationInput.Mode mode){
        this.mode=mode;add(hint);add(Box.createVerticalStrut(8));add(rows);
        JButton add=new JButton(I18n.text("增加一次提醒"));Theme.smallButton(add,"secondary","plus");
        add.addActionListener(e->{delays.add(delays.isEmpty()?300:delays.get(delays.size()-1));rebuild();resize();});
        JPanel actions=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,8));actions.add(add);add(actions);
    }
    void setDelays(List<Integer> values){delays.clear();delays.addAll(values);rebuild();}
    List<Integer> delays(){return new ArrayList<>(delays);}
    private void resize(){revalidate();SwingUtilities.invokeLater(()->SettingsUi.resizeDialogToContent(this));}
    private void rebuild(){
        rows.removeAll();long elapsed=0;
        for(int i=0;i<delays.size();i++){
            final int index=i;int delay=delays.get(i);elapsed+=delay;
            JButton value=new JButton(duration(delay));SettingsUi.valueButton(value,"clock");
            value.addActionListener(e->{DurationInput input=new DurationInput();input.setMode(mode);input.setSeconds(delays.get(index));
                if(SettingsUi.confirm(value,I18n.text("提醒间隔"),I18n.text("从上一次提醒算起；第一段从保存时算起。"),input,I18n.text("确定"),()->InlineErrors.number(input,input::getSeconds,1,Integer.MAX_VALUE,I18n.text("请填写大于 0 秒的时长。")))){
                    delays.set(index,input.getSeconds());rebuild();resize();
                }});
            JButton remove=SettingsUi.iconButton("trash",I18n.text("移除此段"),"ghost");remove.setEnabled(delays.size()>1);
            remove.addActionListener(e->{delays.remove(index);rebuild();resize();});
            JPanel controls=Theme.panel(new FlowLayout(FlowLayout.RIGHT,6,0));controls.add(value);controls.add(remove);
            rows.add(SettingsUi.row(I18n.text("第 ")+(i+1)+I18n.text(" 次提醒"),I18n.text("保存后 ")+duration(elapsed),controls));
        }
        rows.revalidate();rows.repaint();
    }
    private static String duration(long seconds){return com.eye.stats.Statistics.duration(seconds*1000);}
}
