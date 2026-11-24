package com.eye.core;

import com.eye.win.SessionWatcher;
import java.time.*;
import java.util.function.LongSupplier;

/** Enforcement orchestration. All desktop actions are injectable for isolated tests. */
public final class UsageLimitMonitor implements AutoCloseable {
    public interface Actions {
        void show(ComputerUsageLimits.Status status);
        void hide();
        void shutdown();
    }
    private final ComputerUsageLimits usage;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Actions actions;
    private ComputerUsageLimits.Status current;

    public UsageLimitMonitor(ComputerUsageLimits usage, Clock clock, LongSupplier nanos, Actions actions) {
        this.usage=usage;this.clock=clock;this.nanos=nanos;this.actions=actions;
        current=usage.status(clock.instant(),clock.getZone());
    }
    public ComputerUsageLimits.Status status() { return current; }
    public boolean blocked() { return current.blocked(); }
    public void tick() {
        current=usage.sample(clock.instant(),nanos.getAsLong(),clock.getZone());
        if(current.blocked()) {
            actions.show(current);
        } else actions.hide();
    }
    /** Startup must enforce already exhausted budgets before exposing the normal workspace. */
    public void start() {
        current=usage.sample(clock.instant(),nanos.getAsLong(),clock.getZone());
        if(current.blocked()){actions.show(current);}
    }
    public void sessionEvent(int event) {
        boolean unavailable=event==SessionWatcher.WTS_SESSION_LOCK || event==SessionWatcher.SYSTEM_SUSPEND
                || event==SessionWatcher.WTS_CONSOLE_DISCONNECT || event==SessionWatcher.WTS_REMOTE_DISCONNECT;
        boolean available=event==SessionWatcher.WTS_SESSION_UNLOCK || event==SessionWatcher.WTS_CONSOLE_CONNECT
                || event==SessionWatcher.WTS_REMOTE_CONNECT || event==SessionWatcher.WTS_SESSION_DESKTOP_READY;
        if(!unavailable&&!available)return;
        current=usage.sessionActive(available,clock.instant(),nanos.getAsLong(),clock.getZone());
        if(available&&current.blocked()){actions.show(current);}
        else if(!current.blocked())actions.hide();
    }
    public void shutdownComputer() {
        usage.sample(clock.instant(),nanos.getAsLong(),clock.getZone());usage.persist();actions.shutdown();
    }
    @Override public void close() {
        usage.sample(clock.instant(),nanos.getAsLong(),clock.getZone());usage.persist();
    }
}
