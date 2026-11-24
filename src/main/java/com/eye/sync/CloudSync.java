package com.eye.sync;
import com.eye.core.*;
import com.eye.plan.PlanManager;
import com.eye.reminder.ReminderManager;
import com.eyerelax.cloud.CloudClient;
import java.util.concurrent.*;

public final class CloudSync {
  public static final ScheduledExecutorService IO =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EyeRelax-cloud");
        t.setDaemon(true);
        return t;
      });
  private static DesktopAdapter adapter;
  public static volatile String status = "尚未同步";
  public static void configure(Config c, CycleEngine engine, PlanManager plans,
      ReminderManager reminders, ComputerActivity computer) {
    adapter = new DesktopAdapter(c, engine, plans, reminders, computer);
    IO.scheduleWithFixedDelay(() -> {
      try {
        CloudClient client = client();
        if (client.loggedIn())
          status = client.sync();
      } catch (Exception e) {
        status = "同步未完成：" + e.getMessage();
      }
    }, 20, 60, TimeUnit.SECONDS);
  }
  public static CloudClient client() {
    if (adapter == null)
      throw new IllegalStateException("同步服务尚未就绪");
    return new CloudClient(new CloudState(), adapter);
  }
}
