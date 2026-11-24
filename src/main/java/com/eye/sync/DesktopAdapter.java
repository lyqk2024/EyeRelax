package com.eye.sync;
import static com.eyerelax.cloud.Json.*;

import com.eye.core.*;
import com.eye.plan.*;
import com.eye.reminder.*;
import com.eyerelax.cloud.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

public final class DesktopAdapter implements CloudClient.Adapter {
  private final Set<String> unsupported = new HashSet<>();
  private final Config config;
  private final CycleEngine engine;
  private final PlanManager plans;
  private final ReminderManager reminders;
  private final ComputerActivity computer;
  public DesktopAdapter(
      Config c, CycleEngine e, PlanManager p, ReminderManager r, ComputerActivity a) {
    config = c;
    engine = e;
    plans = p;
    reminders = r;
    computer = a;
  }
  public Map<String, Object> settings() {
    return StrictSettings.normalized(StrictSettings.properties(config));
  }
  private static String type(PlanItem p) {
    if (p.kind == PlanItem.PlanKind.TODAY_ONLY)
      return "once";
    if (p.kind == PlanItem.PlanKind.MULTI_DAY)
      return "range";
    return switch (p.recurringRule) {
      case EVERY_DAY -> "daily";
      case WEEKDAY, WEEKEND, BY_WEEKDAY -> "weekdays";
      case WORKDAY -> "workdays";
      case NON_WORKDAY -> "restdays";
      case PUBLIC_HOLIDAY -> "holidays";
      case NORMAL_WORKDAY -> "regular";
      default -> "custom";
    };
  }
  public Map<String, Object> records(String device) {
    Map<String, Object> out = map();
    unsupported.clear();
    for (PlanItem p : plans.listAll()) {
      String type = type(p);
      if (type.equals("custom")) {
        unsupported.add("plan/" + p.id);
        continue;
      }
      LocalDate start = p.createdDate, end = p.cloudEnd;
      if (type.equals("range")) {
        if (p.selectedDates.isEmpty()) {
          unsupported.add("plan/" + p.id);
          continue;
        }
        start = Collections.min(p.selectedDates);
        end = Collections.max(p.selectedDates);
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1 != p.selectedDates.size()) {
          unsupported.add("plan/" + p.id);
          continue;
        }
      }
      int weekdays = p.recurringRule == PlanItem.RecurringRule.WEEKDAY ? 31
          : p.recurringRule == PlanItem.RecurringRule.WEEKEND          ? 96
                                                                       : 127;
      if (p.recurringRule == PlanItem.RecurringRule.BY_WEEKDAY) {
        weekdays = 0;
        for (int d : p.weekdays) weekdays |= 1 << (d - 1);
      }
      PlanItem active = plans.get(p.id);
      String done =
          active != null && active.remainingSeconds == 0 ? plans.getToday().toString() : "";
      Map<String, Object> body = map("id", p.id, "name", p.name, "type", type, "start",
          start.toString(), "end", end == null ? "" : end.toString(), "weekdays", weekdays,
          "durationSeconds", p.plannedSeconds, "template", p.template, "pinned", p.pinned,
          "archived", p.archivedOn != null, "doneDay", done);
      out.put("plan/" + p.id, map("kind", "plan", "body", body));
    }
    for (ReminderItem r : reminders.list()) {
      if (r.lifecycle == ReminderItem.Lifecycle.TEMPLATE)
        continue;
      String mode = switch (r.kind) {
        case DAILY, WEEKLY -> "daily";
        case INTERVAL -> "fixed";
        case SEQUENCE -> "sequence";
        default -> "once";
      };
      int weekdays = r.kind == ReminderItem.ScheduleKind.DAILY ? 127 : 0;
      for (DayOfWeek d : r.weekdays) weekdays |= 1 << (d.getValue() - 1);
      List<Object> sequence = new ArrayList<>();
      if (mode.equals("sequence"))
        for (int seconds : r.sequenceSeconds) sequence.add((seconds + 59) / 60);
      String sequenceText = String.join("/", sequence.stream().map(String::valueOf).toList());
      String id = r.cloudId.isEmpty() ? "reminder/" + device + "/" + r.id : r.cloudId;
      if (r.cloudId.isEmpty()) {
        r.cloudId = id;
        r.cloudStart = r.nextTriggerEpochMs;
        reminders.upsert(r);
      }
      String dayRule = switch (r.dayCategory) {
        case WORKDAY -> "workdays";
        case NON_WORKDAY -> "restdays";
        case HOLIDAY -> "holidays";
        case NORMAL_WORKDAY -> "regular";
        default -> "weekdays";
      };
      Map<String, Object> body =
          map("title", r.title, "mode", mode, "enabled", r.enabled, "next", r.cloudStart,
              "interval", mode.equals("fixed") ? r.intervalSeconds * 1000L : 0, "maxTimes",
              mode.equals("fixed") ? r.cloudMaxTimes : 0, "minute", r.time.toSecondOfDay() / 60,
              "weekdays", weekdays, "dayRule", dayRule, "sequence", sequence);
      out.put(id, map("kind", "reminder", "body", body));
    }
    LocalDate today = StudyDay.dateNow(config);
    if (engine != null)
      for (SessionStore.Interval r :
          engine.getActivityStore().sessions().intervals(today.minusDays(365), today))
        if (!r.isRest()) {
          Map<String, Object> body = map("from", r.startAt(), "to", r.endAt(), "task", r.taskId(),
              "name", r.taskName(), "boundary", r.boundarySeconds(), "zone", r.zoneId(), "planDay",
              r.studyDate().toString());
          out.put("focus/" + device + "/" + r.id(), map("kind", "focus", "body", body));
        }
    if (computer != null)
      for (int day = 0; day < 31; day++) {
        LocalDate date = computer.today().minusDays(day);
        for (var entry : computer.day(date).apps().entrySet()) {
          String app = entry.getKey();
          String display = app.replace('\\', '/');
          display = display.substring(display.lastIndexOf('/') + 1);
          String id = "usage/" + device + "/" + date + "/"
              + Base64.getUrlEncoder().withoutPadding().encodeToString(
                  display.getBytes(java.nio.charset.StandardCharsets.UTF_8));
          long amount = entry.getValue();
          if (out.containsKey(id))
            amount += number(object(object(out.get(id)).get("body")), "millis", 0);
          out.put(id,
              map("kind", "usage", "body",
                  map("app", display, "day", date.toString(), "millis", amount, "platform",
                      "windows")));
        }
      }
    return out;
  }
  public void restoreSettings(Map<String, Object> value) throws Exception {
    if (!SwingUtilities.isEventDispatchThread()) {
      FutureTask<Void> task = new FutureTask<>(() -> {
        restoreSettings(value);
        return null;
      });
      SwingUtilities.invokeLater(task);
      task.get();
      return;
    }
    Config candidate = StrictSettings.copy(config);
    Properties p = StrictSettings.properties(candidate);
    object(value.get("fixed")).forEach((k, v) -> p.setProperty(k, String.valueOf(v)));
    p.setProperty("workSeconds", "" + number(value, "work", 2700));
    p.setProperty("relaxSeconds", "" + number(value, "rest", 301));
    p.setProperty(
        "mode", new String[] {"A_SOFT", "B_STRONG", "C_ULTRA"}[(int) number(value, "mode", 1)]);
    p.setProperty("autoContinueCycles", "" + flag(value, "autoWork", true));
    p.setProperty("dayBoundarySeconds", "" + number(value, "boundary", 14400));
    Map<String, Object> rules = object(value.get("rules")), device = object(rules.get("@device"));
    Map<String,Object> appRules = new TreeMap<>();
    rules.forEach((key,rule)->{if(key.startsWith("app:"))appRules.put(key.substring(4),rule);});
    p.setProperty("applicationLimits",write(appRules));
    p.setProperty("dailyUsageLimitEnabled",
        "" + (flag(device, "enabled", false) && number(device, "daily", 0) > 0));
    p.setProperty("dailyUsageLimitSeconds", "" + (number(device, "daily", 28800000) / 1000));
    p.setProperty("usageWindowEnabled", "" + flag(device, "window", false));
    p.setProperty("usageWindowStartMinute", "" + number(device, "start", 480));
    p.setProperty("usageWindowEndMinute", "" + number(device, "end", 1380));
    for (String key : new String[] {"launchAtLogin", "usageGuardEnabled", "adminExtrasEnabled",
             "showTaskCompletionNotification", "autoStartAfterPlanSelection"})
      p.setProperty(key, "" + flag(object(rules.get("@" + key)), "enabled", false));
    if (!StrictSettings.allow(p))
      throw new java.io.IOException(StrictSettings.MESSAGE);
    LocalStrictSettings.get().accepted(p);
    AtomicProperties.write(DataPaths.file("config.properties"), p);
    config.loadIfExists();
    engine.updateDurations(config.workSeconds, config.relaxSeconds);
  }
  public boolean manages(String id) {
    return !unsupported.contains(id) && (id.startsWith("plan/") || id.startsWith("reminder/"));
  }
  public void apply(List<Object> records) throws Exception {
    if (!SwingUtilities.isEventDispatchThread()) {
      FutureTask<Void> task = new FutureTask<>(() -> {
        apply(records);
        return null;
      });
      SwingUtilities.invokeLater(task);
      task.get();
      return;
    }
    for (Object value : records) {
      Map<String, Object> r = object(value);
      String id = text(r, "id", ""), kind = text(r, "kind", "");
      if (unsupported.contains(id)) continue;
      boolean deleted = flag(r, "deleted", false);
      if (kind.equals("plan")) {
        String key = id.substring(5);
        if (deleted) {
          plans.deletePlan(key);
          continue;
        }
        Map<String, Object> p = object(r.get("body"));
        int duration = (int) number(p, "durationSeconds", 1800);
        if (duration < 1 || duration > 86400)
          throw new IllegalArgumentException("计划时长无效");
        PlanItem plan = new PlanItem(key, text(p, "name", ""), duration, duration);
        String type = text(p, "type", "once");
        plan.createdDate = LocalDate.parse(text(p, "start", LocalDate.now().toString()));
        if (type.equals("range")) {
          plan.kind = PlanItem.PlanKind.MULTI_DAY;
          LocalDate end = LocalDate.parse(text(p, "end", plan.createdDate.toString()));
          if (java.time.temporal.ChronoUnit.DAYS.between(plan.createdDate, end) > 3660)
            throw new IllegalArgumentException("计划日期跨度过长");
          for (LocalDate d = plan.createdDate; !d.isAfter(end); d = d.plusDays(1))
            plan.selectedDates.add(d);
        } else if (!type.equals("once")) {
          plan.kind = PlanItem.PlanKind.RECURRING;
          plan.recurringRule = switch (type) {
            case "daily" -> PlanItem.RecurringRule.EVERY_DAY;
            case "weekdays" -> PlanItem.RecurringRule.BY_WEEKDAY;
            case "workdays" -> PlanItem.RecurringRule.WORKDAY;
            case "restdays" -> PlanItem.RecurringRule.NON_WORKDAY;
            case "holidays" -> PlanItem.RecurringRule.PUBLIC_HOLIDAY;
            case "regular" -> PlanItem.RecurringRule.NORMAL_WORKDAY;
            default -> throw new IllegalArgumentException("不支持的计划周期");
          };
          for (int d = 1; d <= 7; d++)
            if ((number(p, "weekdays", 127) & (1 << (d - 1))) != 0)
              plan.weekdays.add(d);
        }
        if (!text(p, "end", "").isEmpty())
          plan.cloudEnd = LocalDate.parse(text(p, "end", ""));
        plan.template = flag(p, "template", false);
        plan.pinned = flag(p, "pinned", false);
        if (flag(p, "archived", false))
          plan.archivedOn = plans.getToday();
        plans.applyCloud(plan, text(p, "doneDay", ""));
      } else if (kind.equals("reminder")) {
        ReminderItem old = null;
        for (ReminderItem item : reminders.list())
          if (id.equals(item.cloudId))
            old = item;
        if (deleted) {
          if (old != null)
            reminders.delete(old.id);
          continue;
        }
        Map<String, Object> p = object(r.get("body"));
        ReminderItem item = old == null ? new ReminderItem() : old.copy();
        item.cloudId = id;
        item.cloudStart = number(p, "next", 0);
        item.title = text(p, "title", "");
        item.enabled = flag(p, "enabled", true);
        String mode = text(p, "mode", "once");
        item.kind = switch (mode) {
          case "daily" -> ReminderItem.ScheduleKind.WEEKLY;
          case "fixed" -> ReminderItem.ScheduleKind.INTERVAL;
          case "sequence" -> ReminderItem.ScheduleKind.SEQUENCE;
          default -> ReminderItem.ScheduleKind.ONCE;
        };
        item.cloudMaxTimes = (int) number(p, "maxTimes", 0);
        item.intervalSeconds = (int) (number(p, "interval", 300000) / 1000);
        item.time = LocalTime.ofSecondOfDay(number(p, "minute", 540) * 60);
        item.weekdays.clear();
        for (int d = 1; d <= 7; d++)
          if ((number(p, "weekdays", 127) & (1 << (d - 1))) != 0)
            item.weekdays.add(DayOfWeek.of(d));
        item.dayCategory = switch (text(p, "dayRule", "weekdays")) {
          case "workdays" -> com.eye.calendar.DayCategory.WORKDAY;
          case "restdays" -> com.eye.calendar.DayCategory.NON_WORKDAY;
          case "holidays" -> com.eye.calendar.DayCategory.HOLIDAY;
          case "regular" -> com.eye.calendar.DayCategory.NORMAL_WORKDAY;
          default -> com.eye.calendar.DayCategory.EVERY_DAY;
        };
        if (p.containsKey("sequence")) {
          item.sequenceSeconds = new ArrayList<>();
          for (Object n : array(p.get("sequence")))
            item.sequenceSeconds.add(((Number) n).intValue() * 60);
        }
        if (old == null || old.cloudStart != item.cloudStart || old.kind != item.kind
            || old.intervalSeconds != item.intervalSeconds || !old.time.equals(item.time)
            || !old.weekdays.equals(item.weekdays) || old.dayCategory != item.dayCategory
            || !old.sequenceSeconds.equals(item.sequenceSeconds)) {
          item.cloudFired = 0;
          item.sequenceIndex = 0;
          item.nextTriggerEpochMs = item.cloudStart;
          if (item.nextTriggerEpochMs <= System.currentTimeMillis())
            item.advanceAfter(System.currentTimeMillis());
        }
        reminders.upsert(item);
      }
    }
  }
}
