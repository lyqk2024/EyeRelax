package com.eye.ui;

import com.eye.core.Config;
import com.eye.sync.*;
import com.eye.win.StartupRegistration;
import com.eye.ui.theme.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import static com.eye.i18n.I18n.text;

public final class LocalStrictPanel extends JPanel {
    private final JLabel state=Theme.label("",12,Theme.GREEN);
    private final JButton activate=new JButton(text("开启一周严格设置"));
    private final JPanel copy=Theme.column();
    public JComponent tutorialTarget(String key){return switch(key){case "activate"->activate;case "state"->state;default->copy;};}
    public LocalStrictPanel(Config config) {
        super(new BorderLayout(12,12));setOpaque(false);
        copy.add(state);copy.add(Box.createVerticalStrut(8));
        copy.add(new SettingsUi.Text(text("开启后连续 7 天只能收紧：减少额度、缩小允许时段、完全禁用应用、缩短工作或延长休息。不能暂停或删除有效限制。"),12,Theme.SECONDARY));
        copy.add(Box.createVerticalStrut(6));copy.add(new SettingsUi.Text(text("严格期离线也有效，7 天后自动结束。期间其余受保护的设置保持锁定。"),11,Theme.SECONDARY));
        add(copy);state.putClientProperty("tutorial.region",copy);Theme.smallButton(activate,"primary","shield");JPanel action=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0));action.add(activate);add(action,BorderLayout.SOUTH);activate.putClientProperty("tutorial.region",this);
        activate.addActionListener(e->{
            if(JOptionPane.showConfirmDialog(this,text("将以当前已保存的设置开启连续 7 天严格期。开启后不能提前结束，只能进一步收紧。是否开启？"),text("严格设置"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try {
                StartupRegistration.apply(true);config.launchAtLogin=true;
                if(!config.save())throw new IllegalStateException(text("设置未保存，请检查严格规则或数据目录。"));
                LocalStrictSettings.get().activate(StrictSettings.properties(config));refresh();
            }catch(Exception ex){state.setForeground(Theme.DANGER);state.setText(ex.getMessage());}
        });
        javax.swing.Timer timer=new javax.swing.Timer(60_000,e->refresh());
        addHierarchyListener(e->{if(isShowing()){refresh();timer.start();}else timer.stop();});refresh();
    }
    private void refresh(){
        boolean active=LocalStrictSettings.get().active();activate.setEnabled(!active);state.setForeground(Theme.GREEN);
        state.setText(active?text("本机严格期至：")+Instant.ofEpochMilli(LocalStrictSettings.get().until()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")):text("本机严格设置未开启"));
    }
}
