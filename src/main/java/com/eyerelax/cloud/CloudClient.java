package com.eyerelax.cloud;
import static com.eyerelax.cloud.Json.*;

import java.io.*;
import java.util.*;

/** Serialized by each platform's single background executor. Local data survives every error. */
public final class CloudClient {
  public interface Store {
    Map<String, Object> load();
    void save(Map<String, Object> value) throws IOException;
  }
  public interface Adapter {
    Map<String, Object> settings();
    Map<String, Object> records(String device);
    boolean manages(String id);
    void apply(List<Object> records) throws Exception;
    void restoreSettings(Map<String, Object> settings) throws Exception;
  }
  private final Store store;
  private final Adapter adapter;
  private Map<String, Object> state;
  public CloudClient(Store store, Adapter adapter) {
    this.store = store;
    this.adapter = adapter;
    state = store.load();
  }
  public Map<String, Object> state() {
    return state;
  }
  public boolean loggedIn() {
    return !text(state, "token", "").isEmpty();
  }
  public boolean locked() {
    return flag(state, "locked", false);
  }
  private Api api() {
    return new Api(text(state, "url", ""), text(state, "token", ""));
  }
  private void save() throws IOException {
    store.save(state);
  }
  private void time(Map<String, Object> response) throws IOException {
    long now = number(response, "serverTime", 0), until = number(response, "strictUntil", 0);
    if (now <= 0)
      throw new IOException("服务缺少可信时间");
    state.put("strictUntil", until);
    state.put("locked", until > now);
    state.put("serverTime", now);
    save();
  }
  public String login(String url, String username, String password, String platform,
      String deviceName, boolean register) throws Exception {
    if (locked())
      throw new IOException("严格期内不能切换账户或服务地址；请恢复原账户连接。");
    String device = username.trim().equalsIgnoreCase(text(state, "username", ""))
            && Api.address(url).equals(text(state, "url", ""))
        ? text(state, "deviceId", UUID.randomUUID().toString())
        : UUID.randomUUID().toString();
    Map<String, Object> r =
        new Api(url, "").call("POST", register ? "/v1/auth/register" : "/v1/auth/login",
            map("username", username, "password", password, "platform", platform, "deviceId",
                device, "deviceName", deviceName));
    String previous = text(state, "userId", "");
    if (!previous.isEmpty() && !previous.equals(text(r, "userId", "")))
      state = map("deviceId", device);
    state.putAll(r);
    state.remove("recoveryCode");
    state.put("url", Api.address(url));
    state.put("username", username);
    time(r);
    return text(r, "recoveryCode", "");
  }
  public void reconnect(String password, String platform, String name) throws Exception {
    Map<String, Object> r =
        new Api(text(state, "url", ""), "")
            .call("POST", "/v1/auth/login",
                map("username", text(state, "username", ""), "password", password, "platform",
                    platform, "deviceId", text(state, "deviceId", ""), "deviceName", name));
    if (!text(r, "userId", "").equals(text(state, "userId", "")))
      throw new IOException("账户不一致");
    state.put("token", r.get("token"));
    time(r);
  }
  public void logout() throws Exception {
    api().call("POST", "/v1/auth/logout", map());
    state.remove("token");
    save();
  }
  public void checkLock() throws Exception {
    time(api().call("GET", "/v1/me", null));
  }
  public void restoreSettings() throws Exception {
    Map<String, Object> r = api().call("GET", "/v1/settings", null);
    time(r);
    if (r.get("settings") == null)
      throw new IOException("此设备尚无云端设置");
    adapter.restoreSettings(object(r.get("settings")));
    syncSettings();
  }
  public void syncSettings() throws Exception {
    Map<String, Object> remote = api().call("GET", "/v1/settings", null);
    time(remote);
    Map<String, Object> current = adapter.settings();
    if (locked() && remote.get("settings") != null
        && !StrictRules.stricter(object(remote.get("settings")), current)) {
      state.put("requiredSettings", remote.get("settings"));
      save();
      throw new IOException("云端严格设置比本机更严格，请先恢复云端限制设置。");
    }
    Map<String, Object> r = api().call(
        "PUT", "/v1/settings", map("revision", number(remote, "revision", 0), "settings", current));
    time(r);
    state.put("acceptedSettings", current);
    state.remove("requiredSettings");
    save();
  }
  public void activate() throws Exception {
    syncSettings();
    time(api().call("POST", "/v1/strict/activate", map("confirm", "LOCK_7_DAYS")));
    state.put("acceptedSettings", adapter.settings());
    save();
  }
  public String sync() throws Exception {
    if (state.containsKey("conflicts"))
      throw new IOException("请先处理已有同步冲突。");
    checkLock();
    syncSettings();
    Map<String, Object> local = adapter.records(text(state, "deviceId", ""));
    Map<String, Object> known = state.containsKey("known") ? object(state.get("known")) : map();
    List<Object> changes = new ArrayList<>();
    for (String id : local.keySet()) {
      Map<String, Object> value = object(local.get(id)),
                          old = known.containsKey(id) ? object(known.get(id)) : null;
      if (old == null || flag(old, "deleted", false)
          || !write(value.get("body")).equals(write(old.get("body"))))
        changes.add(map("id", id, "kind", value.get("kind"), "body", value.get("body"), "base",
            old == null ? 0 : number(old, "revision", 0)));
    }
    for (String id : known.keySet()) {
      Map<String, Object> old = object(known.get(id));
      if (adapter.manages(id) && !local.containsKey(id) && !flag(old, "deleted", false))
        changes.add(map("id", id, "kind", old.get("kind"), "deleted", true, "base",
            number(old, "revision", 0)));
    }
    int sent = 0, received = 0;
    long cursor = number(state, "cursor", 0);
    boolean more = true;
    while (sent < changes.size() || more) {
      List<Object> batch =
          new ArrayList<>(changes.subList(sent, Math.min(sent + 100, changes.size())));
      Map<String, Object> r;
      try {
        r = api().call("POST", "/v1/sync", map("changes", batch, "cursor", cursor));
      } catch (Api.Failure conflict) {
        if (conflict.status == 409 && conflict.response.containsKey("conflicts")) {
          state.put("conflicts", conflict.response.get("conflicts"));
          state.put("conflictLocal", local);
          save();
          throw new IOException("同步存在冲突。两份数据均保留，请选择采用云端或保留本机后重试。");
        }
        throw conflict;
      }
      time(r);
      List<Object> records = array(r.get("records"));
      // Don't overwrite a newer local edit while a multi-batch upload is in progress.
      List<Object> applicable = new ArrayList<>();
      for (Object record : records) {
        Map<String, Object> item = object(record);
        String id = text(item, "id", "");
        boolean pending = false;
        for (int i = sent + batch.size(); i < changes.size(); i++)
          if (id.equals(text(object(changes.get(i)), "id", ""))) {
            pending = true;
            break;
          }
        if (!pending)
          applicable.add(record);
      }
      // Re-read after the network request: edits made while waiting must never be overwritten.
      Map<String, Object> fresh = adapter.records(text(state, "deviceId", ""));
      List<Object> raced = new ArrayList<>(), toApply = new ArrayList<>();
      for (Object record : applicable) {
        Map<String, Object> row = object(record);
        String id = text(row, "id", "");
        if (adapter.manages(id) && !write(local.get(id)).equals(write(fresh.get(id))))
          raced.add(record);
        else if (!local.containsKey(id) || flag(row, "deleted", false)
            || !write(object(local.get(id)).get("body")).equals(write(row.get("body"))))
          toApply.add(record);
      }
      if (!raced.isEmpty()) {
        state.put("conflicts", raced);
        state.put("conflictLocal", fresh);
        save();
        throw new IOException("同步等待期间本机内容发生变化，请先处理冲突。");
      }
      adapter.apply(toApply);
      for (Object record : applicable) {
        Map<String, Object> item = object(record);
        known.put(text(item, "id", ""), item);
      }
      cursor = number(r, "cursor", cursor);
      more = flag(r, "hasMore", false);
      sent += batch.size();
      received += applicable.size();
      state.put("known", known);
      state.put("cursor", cursor);
      state.put("lastSync", number(r, "serverTime", 0));
      save();
    }
    state.remove("conflicts");
    save();
    return "同步完成：上传 " + changes.size() + " 项，接收 " + received + " 项";
  }
  public void resolve(boolean useCloud) throws Exception {
    List<Object> conflicts = array(state.get("conflicts"));
    if (useCloud)
      adapter.apply(conflicts);
    Map<String, Object> known = state.containsKey("known") ? object(state.get("known")) : map();
    for (Object item : conflicts) {
      Map<String, Object> row = object(item);
      known.put(text(row, "id", ""), row);
    }
    state.put("known", known);
    state.remove("conflicts");
    save();
  }
}
