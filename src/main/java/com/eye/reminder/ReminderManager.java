package com.eye.reminder;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Owns persisted schedules and emits each due reminder exactly once per occurrence. */
public final class ReminderManager {
    private final ReminderStore store;
    private final List<ReminderItem> items = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EyeRelax-ReminderScheduler"); t.setDaemon(true); return t;
    });
    private volatile Consumer<ReminderItem> dueHandler;
    private volatile Runnable changedHandler;
    private volatile boolean started;

    public ReminderManager() { this(new ReminderStore()); }
    public ReminderManager(ReminderStore store) {
        this.store = store;
        items.addAll(store.load());
        boolean changed=false;
        for(ReminderItem item:items)if(item.lifecycle==ReminderItem.Lifecycle.CURRENT&&!item.enabled&&!item.awaitingCompletion&&isFinite(item)){
            boolean single=item.kind==ReminderItem.ScheduleKind.ONCE||item.kind==ReminderItem.ScheduleKind.RELATIVE;
            boolean ended=single&&item.lastTriggeredEpochMs>0||item.kind==ReminderItem.ScheduleKind.SEQUENCE&&item.sequenceIndex>=item.sequenceSeconds.size();
            if(ended||single&&item.nextTriggerEpochMs<System.currentTimeMillis()){
                item.lifecycle=ended?ReminderItem.Lifecycle.ENDED:ReminderItem.Lifecycle.EXPIRED;item.archivedAtEpochMs=System.currentTimeMillis();changed=true;
            }
        }
        if(changed)store.save(items);
    }

    public synchronized List<ReminderItem> list() {
        return items.stream().map(ReminderItem::copy)
                .sorted(Comparator.comparing((ReminderItem x)->!x.pinned).thenComparingLong(x -> x.enabled ? x.nextTriggerEpochMs : Long.MAX_VALUE)).toList();
    }

    public synchronized ReminderItem get(String id) {
        for (ReminderItem item : items) if (item.id.equals(id)) return item.copy();
        return null;
    }

    public void setDueHandler(Consumer<ReminderItem> handler) { dueHandler = handler; }
    public void setChangedHandler(Runnable handler) { changedHandler = handler; }

    public synchronized void start() {
        if (started) return;
        started = true;
        // Re-present an unfinished final overlay after restart, without scheduling it twice.
        for(ReminderItem item:items)if(item.awaitingCompletion){Consumer<ReminderItem> handler=dueHandler;if(handler!=null){ReminderItem occurrence=item.copy();occurrence.nextTriggerEpochMs=item.completionTriggerEpochMs;handler.accept(occurrence);}}
        scheduler.scheduleAtFixedRate(this::tick, 250, 250, TimeUnit.MILLISECONDS);
    }

    public synchronized void shutdown() {
        store.save(items);
        scheduler.shutdownNow();
        started = false;
    }

    public synchronized void upsert(ReminderItem incoming) {
        ReminderItem copy = incoming.copy();
        int index = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(copy.id)) { index = i; break; }
        if (index >= 0) items.set(index, copy); else items.add(copy);
        store.save(items); fireChanged();
    }

    public synchronized void delete(String id) {
        items.removeIf(x -> x.id.equals(id));
        store.save(items); fireChanged();
    }

    public synchronized void setEnabled(String id, boolean enabled) {
        for (ReminderItem item : items) if (item.id.equals(id)) {
            item.enabled = enabled;
            if(enabled){item.lifecycle=ReminderItem.Lifecycle.CURRENT;item.archivedAtEpochMs=0;item.awaitingCompletion=false;item.completionTriggerEpochMs=0;}
            if (enabled && item.kind == ReminderItem.ScheduleKind.INTERVAL) {
                item.nextTriggerEpochMs = System.currentTimeMillis() + item.intervalSeconds * 1000L;
            } else if (enabled && item.kind == ReminderItem.ScheduleKind.SEQUENCE) {
                if (item.sequenceIndex >= item.sequenceSeconds.size()) item.sequenceIndex = 0;
                item.nextTriggerEpochMs = System.currentTimeMillis() + item.sequenceSeconds.get(item.sequenceIndex) * 1000L;
            }
            if (enabled && (item.kind == ReminderItem.ScheduleKind.DAILY || item.kind == ReminderItem.ScheduleKind.WEEKLY)
                    && item.nextTriggerEpochMs <= System.currentTimeMillis()) {
                item.advanceAfter(System.currentTimeMillis());
            }
            if(enabled){item.cloudStart=item.nextTriggerEpochMs;item.cloudFired=0;}
            break;
        }
        store.save(items); fireChanged();
    }

    /** Earliest enabled reminder whose trigger falls in [fromInclusive, toInclusive], or -1. */
    public synchronized long nextDueBetween(long fromInclusive, long toInclusive) {
        long best = Long.MAX_VALUE;
        for (ReminderItem item : items) {
            if (!item.enabled) continue;
            long due = item.nextTriggerEpochMs;
            if (due >= fromInclusive && due <= toInclusive && due < best) best = due;
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    public synchronized ReminderItem itemDueAt(long epochMs) {
        ReminderItem best = null;
        long delta = Long.MAX_VALUE;
        for (ReminderItem item : items) if (item.enabled) {
            long d = Math.abs(item.nextTriggerEpochMs - epochMs);
            if (d < delta) { delta = d; best = item; }
        }
        return best == null ? null : best.copy();
    }

    private void tick() {
        ReminderItem fire = null;
        synchronized (this) {
            long now = System.currentTimeMillis();
            ReminderItem due = null;
            for (ReminderItem item : items) {
                if (!item.enabled || item.nextTriggerEpochMs <= 0 || item.nextTriggerEpochMs > now) continue;
                if (due == null || item.nextTriggerEpochMs < due.nextTriggerEpochMs) due = item;
            }
            if (due == null) return;
            fire = due.copy();
            due.lastTriggeredEpochMs = now;
            due.advanceAfter(now);
            if(!due.enabled&&isFinite(due)){due.awaitingCompletion=true;due.completionTriggerEpochMs=fire.nextTriggerEpochMs;}
            store.save(items);
        }
        fireChanged();
        Consumer<ReminderItem> handler = dueHandler;
        if (handler != null && fire != null) handler.accept(fire);
    }

    private void fireChanged() {
        Runnable handler = changedHandler;
        if (handler != null) SwingUtilities.invokeLater(handler);
    }
    private static boolean isFinite(ReminderItem item){return item.kind==ReminderItem.ScheduleKind.ONCE||item.kind==ReminderItem.ScheduleKind.RELATIVE||item.kind==ReminderItem.ScheduleKind.SEQUENCE;}
    public synchronized void completePresentation(ReminderItem occurrence){
        for(ReminderItem item:items)if(item.id.equals(occurrence.id)&&item.awaitingCompletion&&item.completionTriggerEpochMs==occurrence.nextTriggerEpochMs){
            item.awaitingCompletion=false;item.completionTriggerEpochMs=0;item.lifecycle=ReminderItem.Lifecycle.COMPLETED;item.archivedAtEpochMs=System.currentTimeMillis();store.save(items);fireChanged();break;
        }
    }
    public synchronized void setPinned(String id,boolean pinned){for(ReminderItem item:items)if(item.id.equals(id))item.pinned=pinned;store.save(items);fireChanged();}
    public synchronized ReminderItem saveTemplate(String id){
        ReminderItem source=get(id);if(source==null)return null;source.id=UUID.randomUUID().toString();source.cloudId="";source.cloudStart=0;source.cloudFired=0;source.lifecycle=ReminderItem.Lifecycle.TEMPLATE;
        source.enabled=false;source.awaitingCompletion=false;source.completionTriggerEpochMs=0;source.lastTriggeredEpochMs=0;source.archivedAtEpochMs=0;source.sequenceIndex=0;source.pinned=false;upsert(source);return source.copy();
    }
    public synchronized ReminderItem freshCopy(String id){
        ReminderItem source=get(id);if(source==null)return null;source.id=UUID.randomUUID().toString();source.cloudId="";source.cloudStart=0;source.cloudFired=0;source.lifecycle=ReminderItem.Lifecycle.CURRENT;
        source.enabled=true;source.awaitingCompletion=false;source.completionTriggerEpochMs=0;source.lastTriggeredEpochMs=0;source.archivedAtEpochMs=0;source.sequenceIndex=0;source.pinned=false;
        long now=System.currentTimeMillis();source.nextTriggerEpochMs=now+switch(source.kind){case INTERVAL->source.intervalSeconds*1000L;case SEQUENCE->source.sequenceSeconds.get(0)*1000L;case RELATIVE->source.relativeDelaySeconds*1000L;default->3600000L;};return source;
    }
}
