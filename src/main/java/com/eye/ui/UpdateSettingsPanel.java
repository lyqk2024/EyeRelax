package com.eye.ui;
import com.eye.app.*;
import com.eye.core.*;
import com.eye.sync.CloudState;
import com.eye.ui.theme.*;
import com.eyerelax.cloud.Json;
import java.awt.*;
import java.nio.file.*;
import javax.swing.*;

/** Release lookup and download are explicit; installing remains a user action. */
public final class UpdateSettingsPanel extends JPanel {
  private final Config config;
  private final JCheckBox automatic = new JCheckBox(), requestAutomatically = new JCheckBox();
  private final JTextArea status = new JTextArea();
  private final JTextField feed = new JTextField(30);
  private final JButton check = new JButton(com.eye.i18n.I18n.text("检查更新")), download = new JButton(com.eye.i18n.I18n.text("下载更新")),
                        install = new JButton(com.eye.i18n.I18n.text("安装更新"));
  private UpdateClient.Release release;
  private Path downloaded;
  private boolean busy;
  public UpdateSettingsPanel(Config config) {
    super(new BorderLayout());
    this.config = config;
    setOpaque(false);
    SettingsUi.Column body = new SettingsUi.Column();
    SettingsUi.toggle(automatic, com.eye.i18n.I18n.text("自动检查版本"));
    SettingsUi.toggle(requestAutomatically, com.eye.i18n.I18n.text("检查后自动下载"));
    automatic.setSelected(config.updateAutoCheck);
    requestAutomatically.setSelected(config.updateAutoRequest);
    feed.setText(config.updateFeedUrl);
    JPanel actions = Theme.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    for (JButton b : new JButton[] {check, download, install}) {
      Theme.smallButton(b, "secondary", null);
      actions.add(b);
    }
    download.setEnabled(false);
    install.setEnabled(false);
    body.add(SettingsUi.group(com.eye.i18n.I18n.text("版本与更新"),
        SettingsUi.row(com.eye.i18n.I18n.text("当前版本"), "",
            Theme.label(AppVersion.CURRENT, 13, Theme.INK)),
        SettingsUi.row(com.eye.i18n.I18n.text("更新清单地址"), com.eye.i18n.I18n.text("留空使用账户服务器。"), feed),
        SettingsUi.row(com.eye.i18n.I18n.text("自动检查版本"), "", automatic),
        SettingsUi.row(com.eye.i18n.I18n.text("自动下载更新"), com.eye.i18n.I18n.text("下载后需手动安装。"), requestAutomatically),
        actions));
    status.setEditable(false);
    status.setLineWrap(true);
    status.setWrapStyleWord(true);
    status.setOpaque(false);
    status.setFont(Theme.font(11));
    status.setForeground(Theme.SECONDARY);
    status.setRows(3);
    check.addActionListener(e -> check());
    download.addActionListener(e -> download());
    install.addActionListener(e -> {
      if (downloaded != null
          && JOptionPane.showConfirmDialog(this, com.eye.i18n.I18n.text("将打开安装向导进行覆盖更新，是否继续？"),
                 com.eye.i18n.I18n.text("安装更新"), JOptionPane.OK_CANCEL_OPTION)
              == JOptionPane.OK_OPTION)
        try {
          Desktop.getDesktop().open(downloaded.toFile());
        } catch (Exception ex) {
          status.setText(com.eye.i18n.I18n.text("无法打开安装包：") + ex.getMessage());
        }
    });
    body.add(status);
    add(body);
  }
  private String address() {
    String value = feed.getText().trim();
    if (!value.isEmpty())
      return value;
    String server = Json.text(new CloudState().load(), "url", "");
    if (server.isEmpty())
      throw new IllegalStateException(com.eye.i18n.I18n.text("请先配置账户服务器或更新清单地址。"));
    return server + "/v1/updates/windows.properties?channel=" + AppVersion.updateChannel();
  }
  private void check() {
    if (busy)
      return;
    final String address;
    try {
      address = address();
    } catch (Exception e) {
      status.setText(e.getMessage());
      return;
    }
    busy = true;
    check.setEnabled(false);
    status.setText(com.eye.i18n.I18n.text("正在检查…"));
    new SwingWorker<UpdateClient.Release, Void>() {
      protected UpdateClient.Release doInBackground() throws Exception {
        return new UpdateClient().check(address);
      }
      protected void done() {
        busy = false;
        check.setEnabled(true);
        try {
          release = get();
          boolean newer = AppVersion.compare(release.version(), AppVersion.CURRENT) > 0;
          download.setEnabled(newer);
          status.setText(newer ? com.eye.i18n.I18n.text("新版本 ") + release.version() + "\n" + release.notes()
                               : com.eye.i18n.I18n.text("当前已是最新版本。"));
          if (newer && config.updateAutoRequest)
            download();
        } catch (Exception e) {
          status.setText(
              com.eye.i18n.I18n.text("检查未完成：") + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        }
      }
    }.execute();
  }
  private void download() {
    if (busy || release == null)
      return;
    busy = true;
    download.setEnabled(false);
    status.setText(com.eye.i18n.I18n.text("正在下载安装包…"));
    new SwingWorker<Path, Void>() {
      protected Path doInBackground() throws Exception {
        return new UpdateClient().download(release, DataPaths.bootstrapRoot().resolve("updates"));
      }
      protected void done() {
        busy = false;
        download.setEnabled(true);
        try {
          downloaded = get();
          install.setEnabled(true);
          status.setText(com.eye.i18n.I18n.text("下载完成，可点击安装更新。"));
        } catch (Exception e) {
          status.setText(
              com.eye.i18n.I18n.text("下载未完成：") + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        }
      }
    }.execute();
  }
  public void startAutomaticChecks() {
    if (config.updateAutoCheck
        && (!config.updateFeedUrl.isBlank()
            || !Json.text(new CloudState().load(), "url", "").isEmpty())) {
      Timer timer = new Timer(15000, e -> check());
      timer.setRepeats(false);
      timer.start();
    }
  }
  public void validateSettings() {
    if (!feed.getText().isBlank())
      UpdateClient.source(feed.getText());
  }
  public void applyTo(Config target) {
    target.updateAutoCheck = automatic.isSelected();
    target.updateAutoRequest = requestAutomatically.isSelected();
    target.updateFeedUrl = feed.getText().trim();
  }
  public void applyToConfig() {
    applyTo(config);
  }
}
