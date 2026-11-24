package com.eye.sync;
import static com.eyerelax.cloud.Json.*;

import com.eye.core.*;
import com.eyerelax.cloud.*;
import java.util.*;

public final class StrictSettings {
  public static final String MESSAGE = "严格期内只能收紧限制，本次设置未保存。";
  private StrictSettings() {}
  public static Properties properties(Config c) {
    Properties p = new Properties();
    for (var field : Config.class.getFields()) try {
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()))
          p.setProperty(field.getName(), String.valueOf(field.get(c)));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    return p;
  }
  public static Config copy(Config c) {
    Config result = new Config();
    for (var field : Config.class.getFields()) try {
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()))
          field.set(result, field.get(c));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    return result;
  }
  private static boolean b(Properties p, String key, boolean fallback) {
    return Boolean.parseBoolean(p.getProperty(key, "" + fallback));
  }
  private static long n(Properties p, String key, long fallback) {
    return Long.parseLong(p.getProperty(key, "" + fallback));
  }
  private static Map<String, Object> toggle(boolean enabled) {
    return map("enabled", enabled, "blocked", false, "daily", 0, "session", 0, "cooldown", 0,
        "window", false, "start", 0, "end", 0);
  }
  public static Map<String, Object> normalized(Properties p) {
    Properties merged = properties(new Config());
    merged.putAll(p);
    p = merged;
    Map<String, Object> rules = map("@device",
        map("enabled", b(p, "dailyUsageLimitEnabled", false) || b(p, "usageWindowEnabled", false),
            "blocked", false, "daily",
            b(p, "dailyUsageLimitEnabled", false) ? n(p, "dailyUsageLimitSeconds", 28800) * 1000
                                                  : 0,
            "session", 0, "cooldown", 0, "window", b(p, "usageWindowEnabled", false), "start",
            n(p, "usageWindowStartMinute", 480), "end", n(p, "usageWindowEndMinute", 1380)));
    for (String key : new String[] {"launchAtLogin", "usageGuardEnabled", "adminExtrasEnabled",
             "showTaskCompletionNotification", "autoStartAfterPlanSelection"})
      rules.put("@" + key,
          toggle(b(p, key,
              key.equals("launchAtLogin") || key.equals("showTaskCompletionNotification"))));
    ApplicationLimitRule.parse(p.getProperty("applicationLimits","{}")).forEach((path,r)->rules.put("app:"+path,r.normalized()));
    Set<String> ordered = Set.of("workSeconds", "relaxSeconds", "workMinutes", "relaxMinutes",
        "mode", "autoContinueCycles", "dailyUsageLimitEnabled", "dailyUsageLimitSeconds",
        "usageWindowEnabled", "usageWindowStartMinute", "usageWindowEndMinute",
        "dayBoundarySeconds", "launchAtLogin", "usageGuardEnabled", "adminExtrasEnabled",
        "showTaskCompletionNotification", "autoStartAfterPlanSelection", "usageGuardAccountSid",
        "overlayX", "overlayY", "sidebarCollapsed", "applicationLimits");
    Map<String, Object> fixed = map();
    for (String key : p.stringPropertyNames())
      if (!ordered.contains(key))
        fixed.put(key, p.getProperty(key));
    return map("work", n(p, "workSeconds", 2700), "rest", n(p, "relaxSeconds", 301), "mode",
        List.of("A_SOFT", "B_STRONG", "C_ULTRA").indexOf(p.getProperty("mode", "B_STRONG")),
        "enabled", true, "autoRest", true, "autoWork", b(p, "autoContinueCycles", true), "boundary",
        n(p, "dayBoundarySeconds", 14400), "rules", rules, "fixed", fixed);
  }
  public static boolean allow(Properties proposed) {
    if (!LocalStrictSettings.get().allows(proposed)) return false;
    Map<String, Object> state = new CloudState().load();
    if (!flag(state, "locked", false))
      return true;
    try {
      Properties current = AtomicProperties.read(DataPaths.file("config.properties"));
      if (current.isEmpty())
        current = properties(new Config());
      Map<String, Object> old = normalized(current), next = normalized(proposed);
      if (!StrictRules.stricter(old, next))
        return false;
      Object accepted = state.getOrDefault("requiredSettings", state.get("acceptedSettings"));
      return accepted == null || StrictRules.stricter(object(accepted), next);
    } catch (Exception e) {
      return false;
    }
  }
  public static boolean allow(Config c) {
    return allow(properties(c));
  }
  public static boolean locked() { return LocalStrictSettings.get().active() || CloudState.locked(); }
}
