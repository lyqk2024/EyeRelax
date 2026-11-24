package com.eye.ui;
import static com.eyerelax.cloud.Json.*;

import com.eye.sync.*;
import com.eye.ui.theme.*;
import com.eyerelax.cloud.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import javax.swing.*;

public final class CloudSettingsPanel extends JPanel {
  private final JTextField url = new JTextField(28), user = new JTextField(20);
  private final JPasswordField password = new JPasswordField(20);
  private final JTextArea status = new JTextArea(4, 45);
  public CloudSettingsPanel() {
    super(new BorderLayout());
    setOpaque(false);
    Map<String, Object> state = new CloudState().load();
    url.setText(text(state, "url", "http://127.0.0.1:8765"));
    user.setText(text(state, "username", ""));
    JPanel form = new SettingsUi.Column();
    form.add(SettingsUi.row("服务器地址", com.eye.i18n.I18n.text("公网服务器须使用 HTTPS。"), url));
    form.add(SettingsUi.row("用户名", "3—32 位字母、数字或下划线", user));
    form.add(SettingsUi.row("密码", com.eye.i18n.I18n.text("12–256 个字符"), password));
    JPanel actions = Theme.panel(new GridLayout(0, 2, 10, 10));
    add(actions, "登录 / 恢复连接", () -> run(() -> {
      CloudClient c = CloudSync.client();
      String secret = new String(password.getPassword());
      if (c.locked())
        c.reconnect(secret, "windows", System.getProperty("os.name"));
      else
        c.login(
            url.getText(), user.getText(), secret, "windows", System.getProperty("os.name"), false);
      SwingUtilities.invokeLater(() -> password.setText(""));
      return "登录成功，请立即同步。";
    }));
    add(actions, "创建账户", () -> run(() -> {
      String code = CloudSync.client().login(url.getText(), user.getText(),
          new String(password.getPassword()), "windows", System.getProperty("os.name"), true);
      SwingUtilities.invokeLater(() -> {
        password.setText("");
        recovery(code);
      });
      return "账户已创建。";
    }));
    add(actions, "立即同步", () -> run(() -> CloudSync.client().sync()));
    add(actions, "恢复本设备云端设置", () -> run(() -> {
      CloudSync.client().restoreSettings();
      return "云端设置已恢复。";
    }));
    add(actions, "跨设备统计", this::statistics);
    add(actions, "开启一周严格设置", () -> {
      if (JOptionPane.showConfirmDialog(this,
              "开启后连续 7 "
              + "天只能收紧限制，不能关闭严格策略。\n工作只能缩短，休息只能延长或加强；额度只能降低"
              + "、允许时段只能缩小。\n其他限制设置冻结；离线、注销和恢复旧备份不能解除，到期需联网"
              + "确认。\n其他设备联网同步后接收策略。是否开启？",
              "严格设置", JOptionPane.OK_CANCEL_OPTION)
          == JOptionPane.OK_OPTION)
        run(() -> {
          CloudClient c = CloudSync.client();
          c.activate();
          return "已开启，到期："
              + Instant.ofEpochSecond(number(c.state(), "strictUntil", 0))
                    .atZone(ZoneId.systemDefault());
        });
    });
    add(actions, "联网检查期限", () -> run(() -> {
      CloudClient c = CloudSync.client();
      c.checkLock();
      return c.locked() ? "严格期尚未结束" : "严格期已结束，可以正常修改设置。";
    }));
    add(actions, "处理同步冲突", () -> {
      Object[] choices = {"采用云端", "保留本机", "取消"};
      int answer = JOptionPane.showOptionDialog(this,
          "仅处理冲突条目，原本机冲突数据保留在账户恢复记录中。严格策略仍生效。", "同步冲突",
          JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[2]);
      if (answer == 0 || answer == 1)
        run(() -> {
          CloudClient c = CloudSync.client();
          c.resolve(answer == 0);
          return c.sync();
        });
    });
    add(actions, "退出登录", () -> run(() -> {
      CloudSync.client().logout();
      return "已退出，已有本机数据保留。";
    }));
    add(actions, "找回账户", this::recover);
    status.setEditable(false);
    status.setLineWrap(true);
    status.setWrapStyleWord(true);
    status.setOpaque(false);
    status.setFont(Theme.font(12));
    status.setForeground(Theme.SECONDARY);
    status.setText(flag(state, "locked", false) ? "严格设置生效中；结束时间："
                + Instant.ofEpochSecond(number(state, "strictUntil", 0))
                    .atZone(ZoneId.systemDefault())
            : text(state, "username", "").isEmpty()
            ? com.eye.i18n.I18n.text("尚未登录；未登录时不会上传任何数据。")
            : "账户：" + text(state, "username", "") + "\n" + CloudSync.status);
    form.add(actions);
    form.add(status);
    add(form);
  }
  private void add(JPanel panel, String title, Runnable action) {
    JButton button = new JButton(title);
    Theme.smallButton(button, "secondary", null);
    button.addActionListener(e -> action.run());
    panel.add(button);
  }
  private void run(Callable<String> action) {
    status.setText("正在连接…");
    CloudSync.IO.execute(() -> {
      try {
        String result = action.call();
        SwingUtilities.invokeLater(() -> status.setText(result));
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> status.setText("未完成：" + e.getMessage()));
      }
    });
  }
  private void recovery(String code) {
    JTextArea area = new JTextArea(code, 3, 38);
    area.setEditable(false);
    area.setLineWrap(true);
    JOptionPane.showMessageDialog(this,
        new Object[] {"请保存一次性恢复码；遗失密码时使用，不要发给他人。", area}, "恢复码",
        JOptionPane.INFORMATION_MESSAGE);
  }
  private void recover() {
    JTextField name = new JTextField(user.getText()), code = new JTextField();
    JPasswordField pass = new JPasswordField();
    if (JOptionPane.showConfirmDialog(this,
            new Object[] {"用户名", name, "一次性恢复码", code, "新密码（12—256 字符）", pass,
                "重设密码不会解除严格期。"},
            "找回账户", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION)
      return;
    run(() -> {
      Map<String, Object> r =
          new Api(url.getText(), "")
              .call("POST", "/v1/auth/recover",
                  map("username", name.getText(), "recoveryCode", code.getText(), "password",
                      new String(pass.getPassword())));
      SwingUtilities.invokeLater(() -> recovery(text(r, "recoveryCode", "")));
      return "密码已重设，请用新密码恢复连接。";
    });
  }
  private void statistics() {
    Map<String, Object> state = new CloudState().load();
    Map<String, Long> totals = new LinkedHashMap<>();
    if (state.containsKey("known"))
      for (Object row : object(state.get("known")).values()) {
        Map<String, Object> r = object(row);
        String kind = text(r, "kind", "");
        if (flag(r, "deleted", false) || text(r, "origin", "").equals(text(state, "deviceId", ""))
            || !(kind.equals("focus") || kind.equals("usage")))
          continue;
        Map<String, Object> b = object(r.get("body"));
        String key = text(r, "origin", "") + " · " + (kind.equals("focus") ? "专注" : "应用前台");
        long value = kind.equals("focus") ? Math.max(0, number(b, "to", 0) - number(b, "from", 0))
                                          : number(b, "millis", 0);
        totals.merge(key, value, Long::sum);
      }
    StringBuilder text = new StringBuilder("按来源设备保留，不合并为本机限制额度。\n");
    totals.forEach(
        (k, v) -> text.append('\n').append(k).append("\n").append(v / 60000).append(" 分钟\n"));
    JTextArea area =
        new JTextArea(totals.isEmpty() ? "暂无其他设备记录。" : text.toString(), 12, 48);
    area.setEditable(false);
    JOptionPane.showMessageDialog(
        this, new JScrollPane(area), "跨设备统计", JOptionPane.INFORMATION_MESSAGE);
  }
}
