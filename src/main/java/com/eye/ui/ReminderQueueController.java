package com.eye.ui;
import com.eye.core.Config;
import com.eye.core.CycleEngine;
import com.eye.reminder.ReminderItem;
import javax.swing.Timer;
import java.util.ArrayDeque;
/** Serial reminder presentation; tray navigation does not own queue polling or overlays. */
final class ReminderQueueController {
    private final Config config;
    private final CycleEngine engine;
    private final Runnable onStateChanged;
    interface Presentation {boolean isActive();void show(String title,int seconds,Runnable onClose);}
    private final Presentation presentation;
    private final Runnable chime;
    private final ArrayDeque<ReminderItem> reminderQueue=new ArrayDeque<>();
    private boolean reminderQueuePolling;
    private String activeReminderId;
    private java.util.function.Consumer<ReminderItem> completionHandler=item->{};
    void setCompletionHandler(java.util.function.Consumer<ReminderItem> handler){completionHandler=handler;}
    ReminderQueueController(Config config,CycleEngine engine,Runnable onStateChanged){
        this(config,engine,onStateChanged,overlayPresentation(new ReminderOverlay()),SoftChime::play);
    }
    ReminderQueueController(Config config,CycleEngine engine,Runnable onStateChanged,Presentation presentation,Runnable chime){
        this.config=config;this.engine=engine;this.onStateChanged=onStateChanged;this.presentation=presentation;this.chime=chime;
    }
    private static Presentation overlayPresentation(ReminderOverlay overlay){
        return new Presentation(){public boolean isActive(){return overlay.isActive();}public void show(String title,int seconds,Runnable onClose){overlay.show(title,seconds,onClose);}};
    }
    boolean isActive(){return presentation.isActive();}
    void enqueue(ReminderItem item) {
        if (item == null) return;
        // An unattended repeating reminder must not accumulate an overlay backlog.
        if(item.kind==ReminderItem.ScheduleKind.INTERVAL &&
                (item.id.equals(activeReminderId)||reminderQueue.stream().anyMatch(queued->item.id.equals(queued.id))))return;
        reminderQueue.addLast(item.copy());
        processReminderQueue();
    }

    private void processReminderQueue() {
        if (presentation.isActive() || reminderQueue.isEmpty()) return;
        if (engine.isSessionLocked()) {
            if (!reminderQueuePolling) {
                reminderQueuePolling = true;
                Timer wait = new Timer(1000, null);
                wait.addActionListener(e -> {
                    if (!engine.isSessionLocked()) {
                        ((Timer)e.getSource()).stop();
                        reminderQueuePolling = false;
                        processReminderQueue();
                    }
                });
                wait.start();
            }
            return;
        }
        ReminderItem item = reminderQueue.removeFirst();
        activeReminderId=item.id;
        engine.beginReminderOverlay();
        chime.run();
        int hold = item.effectiveMaskSeconds(config.reminderDefaultMaskSeconds);
        presentation.show(item.title, hold, () -> {
            activeReminderId=null;
            completionHandler.accept(item.copy());
            engine.endReminderOverlay(item.resetWorkTimer);
            onStateChanged.run();
            processReminderQueue();
        });
        onStateChanged.run();
    }
}
