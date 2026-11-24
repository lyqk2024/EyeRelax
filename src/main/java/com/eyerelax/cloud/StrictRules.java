package com.eyerelax.cloud;
import static com.eyerelax.cloud.Json.*;

import java.util.*;

/** Same partial order as server policy.py. Incomparable window shifts are rejected. */
public final class StrictRules {
  private StrictRules() {}
  public static boolean stricter(Map<String, Object> old, Map<String, Object> next) {
    if (number(next, "work", 0) > number(old, "work", 0)
        || number(next, "rest", 0) < number(old, "rest", 0)
        || number(next, "mode", 0) < number(old, "mode", 0))
      return false;
    for (String key : new String[] {"enabled", "autoRest", "autoWork"})
      if (flag(old, key, false) && !flag(next, key, false))
        return false;
    if (number(next, "boundary", 0) != number(old, "boundary", 0)
        || !write(old.get("fixed")).equals(write(next.get("fixed"))))
      return false;
    Map<String, Object> before = object(old.get("rules")), after = object(next.get("rules"));
    for (String key : before.keySet())
      if (!rule(object(before.get(key)), after.containsKey(key) ? object(after.get(key)) : null))
        return false;
    return true;
  }
  public static boolean rule(Map<String, Object> old, Map<String, Object> next) {
    if (old == null || !flag(old, "enabled", false))
      return true;
    if (next == null || !flag(next, "enabled", false))
      return false;
    if (flag(next, "blocked", false))
      return true;
    if (flag(old, "blocked", false))
      return false;
    for (String key : new String[] {"daily", "session"}) {
      long a = number(old, key, 0), b = number(next, key, 0);
      if ((b == 0 ? Long.MAX_VALUE : b) > (a == 0 ? Long.MAX_VALUE : a))
        return false;
    }
    if (number(next, "cooldown", 0) < number(old, "cooldown", 0))
      return false;
    for (int m = 0; m < 1440; m++)
      if (allowed(next, m) && !allowed(old, m))
        return false;
    return true;
  }
  private static boolean allowed(Map<String, Object> rule, int minute) {
    if (!flag(rule, "window", false))
      return true;
    long start = number(rule, "start", 0), end = number(rule, "end", 0);
    return start < end ? minute >= start && minute < end : minute >= start || minute < end;
  }
}
