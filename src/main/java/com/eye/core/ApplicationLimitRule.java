package com.eye.core;

import com.eyerelax.cloud.Json;
import java.nio.file.Path;
import java.util.*;

/** Per-executable foreground restrictions. Zero daily milliseconds means unlimited. */
public record ApplicationLimitRule(String path, String name, boolean enabled, boolean blocked,
                                   long daily, boolean window, int start, int end) {
    public ApplicationLimitRule {
        path = key(path);
        if (path.isBlank() || daily < 0 || daily > 86_400_000L || start < 0 || start > 1439 || end < 0 || end > 1439)
            throw new IllegalArgumentException("应用限制参数无效。");
        if (window && start == end) throw new IllegalArgumentException("允许时段的开始与结束不能相同。");
        name = name == null || name.isBlank() ? Path.of(path).getFileName().toString() : name;
    }
    public static String key(String path) { return path == null ? "" : path.replace('/', '\\').toLowerCase(Locale.ROOT); }
    public boolean permits(int minute) { return !window || (start < end ? minute >= start && minute < end : minute >= start || minute < end); }
    public Map<String,Object> normalized() {
        return Json.map("enabled",enabled,"blocked",blocked,"daily",daily,"session",0,"cooldown",0,"window",window,"start",start,"end",end);
    }
    public static Map<String,ApplicationLimitRule> parse(String json) {
        Map<String,ApplicationLimitRule> result = new TreeMap<>();
        for (var entry : Json.object(Json.read(json)).entrySet()) {
            Map<String,Object> r = Json.object(entry.getValue());
            var rule = new ApplicationLimitRule(entry.getKey(), Json.text(r,"name",""),Json.flag(r,"enabled",true),
                    Json.flag(r,"blocked",false),Json.number(r,"daily",0),Json.flag(r,"window",false),
                    Math.toIntExact(Json.number(r,"start",480)),Math.toIntExact(Json.number(r,"end",1380)));
            result.put(rule.path(),rule);
        }
        return result;
    }
    public static String encode(Map<String,ApplicationLimitRule> rules) {
        Map<String,Object> out = new TreeMap<>();
        rules.forEach((key,r)->{Map<String,Object> value=r.normalized();value.put("name",r.name());out.put(r.path(),value);});
        return Json.write(out);
    }
}
