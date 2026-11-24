package com.eye.sync;

import com.eye.core.*;
import com.eye.ui.theme.*;
import java.io.IOException;
import java.util.*;
import javax.swing.*;

/**
 * Sync contract, not a network service. Explicit allow-list prevents leaking machine paths.
 * A local override masks a later account preference; it never mutates the account baseline.
 */
public final class PreferenceScopes {
  private PreferenceScopes() {}
  public static final Set<String> ACCOUNT_KEYS =
      Set.of("workSeconds", "relaxSeconds", "dayBoundarySeconds", "reminderDefaultMaskSeconds",
          "timeInputMode", "autoContinueCycles", "showTaskCompletionNotification");
  public static final Set<String> OVERRIDABLE_KEYS =
      Set.of("workSeconds", "relaxSeconds", "dayBoundarySeconds", "reminderDefaultMaskSeconds");
  public static Properties accountSnapshot(Config c) {
    Properties p = new Properties();
    p.setProperty("schemaVersion", "1");
    p.setProperty("workSeconds", "" + c.workSeconds);
    p.setProperty("relaxSeconds", "" + c.relaxSeconds);
    p.setProperty("dayBoundarySeconds", "" + c.dayBoundarySeconds);
    p.setProperty("reminderDefaultMaskSeconds", "" + c.reminderDefaultMaskSeconds);
    p.setProperty("timeInputMode", c.timeInputMode.name());
    p.setProperty("autoContinueCycles", "" + c.autoContinueCycles);
    p.setProperty("showTaskCompletionNotification", "" + c.showTaskCompletionNotification);
    return p;
  }
  /** Pure merge, for a future authenticated transport. Unknown/system keys are never accepted. */
  public static Properties resolve(Properties account, Properties device, Set<String> overrides) {
    Properties p = new Properties();
    for (String key : ACCOUNT_KEYS) {
      String v = account.getProperty(key);
      if (OVERRIDABLE_KEYS.contains(key) && overrides.contains(key))
        v = device.getProperty(key, v);
      if (v != null)
        p.setProperty(key, v);
    }
    return p;
  }
  public static Set<String> overrides() {
    try {
      Properties p = AtomicProperties.read(DataPaths.file("sync-preference-policy.properties"));
      Set<String> result = new HashSet<>();
      for (String key : OVERRIDABLE_KEYS)
        if (Boolean.parseBoolean(p.getProperty(key, "false")))
          result.add(key);
      return result;
    } catch (IOException e) {
      return Set.of();
    }
  }
  public static JComponent buildPolicyPanel(Config c) {
    JPanel rows = new SettingsUi.Column();
    Set<String> existing = overrides();
    String[][] items = {{"workSeconds", com.eye.i18n.I18n.text("工作时长")},
        {"relaxSeconds", com.eye.i18n.I18n.text("休息时长")},
        {"dayBoundarySeconds", com.eye.i18n.I18n.text("学习日分界")},
        {"reminderDefaultMaskSeconds", com.eye.i18n.I18n.text("默认遮罩时长")}};
    JLabel status = Theme.label(
        "", 10.5f,
        Theme.SECONDARY);
    rows.add(new SettingsUi.Text(com.eye.i18n.I18n.text("开启后使用本设备的设置，关闭后跟随账户。"), 11, Theme.SECONDARY));
    for (String[] item : items) {
      JCheckBox box = new JCheckBox();
      box.setSelected(existing.contains(item[0]));
      SettingsUi.toggle(box, item[1] + com.eye.i18n.I18n.text("本设备覆盖"));
      box.addActionListener(e -> {
        try {
          Properties p = AtomicProperties.read(DataPaths.file("sync-preference-policy.properties"));
          p.setProperty("schemaVersion", "1");
          p.setProperty(item[0], "" + box.isSelected());
          AtomicProperties.write(DataPaths.file("sync-preference-policy.properties"), p);
          status.setText(com.eye.i18n.I18n.text("已保存到本设备。"));
          status.setForeground(Theme.GREEN);
        } catch (IOException ex) {
          box.setSelected(!box.isSelected());
          status.setText(com.eye.i18n.I18n.text("策略保存失败：") + ex.getMessage());
          status.setForeground(Theme.DANGER);
        }
      });
      rows.add(SettingsUi.row(item[1], "", box));
    }
    rows.add(Box.createVerticalStrut(8));
    rows.add(status);
    return SettingsUi.group(com.eye.i18n.I18n.text("设备覆盖策略"), rows);
  }
}
