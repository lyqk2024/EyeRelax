package com.eye.core;

import com.eye.ui.CornerStatusOverlay;
import com.eye.ui.Overlay;
import com.eye.win.InputBlocker;
import com.eye.win.SessionWatcher;
import com.eye.win.WinLock;

import javax.swing.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CycleEngine {
    private final Config config;
    private final RuntimeState.Store stateStore = new RuntimeState.Store();
    private final ActivityStore activityStore;
    private final SharedRestStore sharedRestStore = new SharedRestStore();
    private volatile boolean recoveryEnabled, shuttingDown;
    private volatile long activityLastTickMs;
    private volatile String activitySessionId = java.util.UUID.randomUUID().toString();
    private volatile SessionStore.Kind normalRestKind = SessionStore.Kind.REST;
    private volatile Config.ForceMode activeRelaxMode;
    private volatile int activeWorkTargetSeconds, activeRelaxTargetSeconds;
    private volatile long pausedElapsedMs;

    private volatile String sharedRestId = "";
    private volatile Config.ForceMode sharedRestMode;
    private final AtomicBoolean finishingRelax = new AtomicBoolean(false);
    private volatile RuntimeState interruptedWork;
    /** A scheduled reminder overlay temporarily pauses work and is counted as rest. */
    private volatile boolean reminderActive = false;
    private volatile boolean reminderInterruptedWork = false;
    private volatile boolean restLockSuppressedForReminder = false;
    private volatile long reminderWorkRemainingMs = 0;
    private volatile ReminderHooks reminderHooks;

    public ActivityStore getActivityStore() { return activityStore; }
    public boolean isForcedRest() { return inRelax && relaxMode() != Config.ForceMode.A_SOFT; }
    private Config.ForceMode relaxMode() {
        return sharedRestMode != null ? sharedRestMode : activeRelaxMode != null ? activeRelaxMode : config.mode;
    }
    public int getActiveWorkTargetSeconds() { return inWork && activeWorkTargetSeconds > 0 ? activeWorkTargetSeconds : config.workSeconds; }
    public int getActiveRelaxTargetSeconds() { return inRelax && activeRelaxTargetSeconds > 0 ? activeRelaxTargetSeconds : config.relaxSeconds; }



    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Overlay overlay = new Overlay();
    private final CornerStatusOverlay corner = new CornerStatusOverlay();
    private final SessionWatcher watcher = new SessionWatcher();
    private volatile java.util.function.IntConsumer sessionObserver = event -> {};
    public void setSessionObserver(java.util.function.IntConsumer observer) { sessionObserver = observer; }
    private final InputBlocker inputBlocker = new InputBlocker();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> workFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> workTickerFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> relaxEndFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> overlayTickerFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> cornerTickerFuture = new AtomicReference<>();

    private volatile boolean inRelax = false;
    private volatile boolean inWork = false;
    private volatile long workEndEpochMs = 0;
    private volatile long workStartEpochMs = 0;
    private volatile long relaxStartEpochMs = 0;
    private volatile long relaxEndEpochMs = 0;
    private volatile long planLastTickMs = 0;
    private volatile LocalDate lastStudyDate;
    private volatile long pausedSinceEpochMs = 0;
    private volatile long schedulerHeartbeatMs = System.currentTimeMillis();

    private volatile boolean sessionLocked = false;
    /** Nominal rest timer reached zero, but a locked workstation keeps the rest phase alive. */
    private volatile boolean relaxDeadlineReached = false;
    private volatile boolean relaxFinishedPendingConfirm = false;

    private volatile int workDisplayMode = 0;  // 0:本轮剩余 1:本轮已用 2:计划剩余 3:计划已用
    private volatile int relaxDisplayMode = 0; // 0:剩余 1:已休息

    private volatile Runnable onStateChanged;
    private volatile Runnable onRelaxFinishedStartRequested;
    private volatile Runnable onPauseRequested;
    private volatile Runnable onResumeRequested;

    public interface ReminderHooks {
        /** Earliest reminder due in the inclusive window, or -1 when none is scheduled. */
        long nextDueBetween(long fromEpochMs, long toEpochMs);
        /** Inform the UI that this rest stays unlocked because a reminder overlaps it. */
        void onRestOverlap(long reminderEpochMs, long restEndEpochMs);
    }

    public interface PlanHooks {
        int onConsumeSeconds(String planId, int seconds);
        void onPlanDepleted(String planId);
        default void onStudyDayChanged(LocalDate studyDate) {}
        int getPlannedSeconds(String planId);
        int getRemainingSeconds(String planId);
        String getPlanName(String planId);
    }

    private volatile PlanHooks planHooks;
    private volatile String currentPlanId = null;

    private enum PausedPhase { NONE, WORK, RELAX }
    private volatile PausedPhase pausedPhase = PausedPhase.NONE;
    private volatile long pausedRemainingMs = 0;
    private volatile String pausedConfigSignature = null;

    public CycleEngine(Config config) {
        inputBlocker.setPresentationGuard(overlay::isPresented);
        this.config = config;
        this.activityStore = new ActivityStore(config.dayBoundarySeconds);
        this.lastStudyDate = StudyDay.dateNow(config);

        corner.setToggleAction(this::toggleCornerDisplayMode);
        corner.setPositionChangedListener((x, y) -> {
            config.overlayCustomPosition = true;
            config.overlayX = x;
            config.overlayY = y;
            config.save();
        });
        corner.setPreferences(config.overlayShowSwitchButton, config.overlayShowButtonText);
        if (config.overlayCustomPosition) corner.setCustomPosition(config.overlayX, config.overlayY);

        watcher.start(evt -> {
            sessionObserver.accept(evt);
            if (evt == SessionWatcher.WTS_CONSOLE_CONNECT || evt == SessionWatcher.WTS_REMOTE_CONNECT
                    || evt == SessionWatcher.WTS_SESSION_UNLOCK || evt == SessionWatcher.WTS_SESSION_DESKTOP_READY) {
                sessionLocked = false;
                if (recoveryEnabled) checkSharedRest();
            }
            if (!running.get()) { refreshCornerNow(); return; }

            if (evt == SessionWatcher.WTS_CONSOLE_DISCONNECT || evt == SessionWatcher.WTS_REMOTE_DISCONNECT
                    || evt == SessionWatcher.SYSTEM_SUSPEND) {
                if (inWork) stop(); // A disconnected desktop must not accrue focus or task time.
                sessionLocked = true;
                inputBlocker.stop();
                persistRuntimeState();
                refreshCornerNow();
                return;
            }
            if (evt == SessionWatcher.WTS_SESSION_LOCK) {
                sessionLocked = true;
                if (inRelax && relaxMode() == Config.ForceMode.B_STRONG) {
                    inputBlocker.stop();
                    SwingUtilities.invokeLater(overlay::hideOverlay);
                }
                refreshCornerNow();
                return;
            }

            if (evt != SessionWatcher.WTS_SESSION_UNLOCK && evt != SessionWatcher.WTS_CONSOLE_CONNECT
                    && evt != SessionWatcher.WTS_REMOTE_CONNECT && evt != SessionWatcher.WTS_SESSION_DESKTOP_READY) return;
            sessionLocked = false;
            if (!inRelax) {
                refreshCornerNow();
                return;
            }
            if (reminderActive) {
                refreshCornerNow();
                return;
            }

            boolean restComplete = relaxDeadlineReached || System.currentTimeMillis() >= relaxEndEpochMs;
            if (restComplete) relaxDeadlineReached = true;

            if (relaxMode() == Config.ForceMode.A_SOFT) {
                if (restComplete) finishRelaxAndAskStart();
                else refreshCornerNow();
                return;
            }

            if (relaxMode() == Config.ForceMode.B_STRONG) {
                if (restComplete) finishRelaxAndAskStart();
                else showBlockingRelaxOverlay();
                return;
            }

            // C: any early return to the desktop immediately goes back to the lock screen.
            if (!restComplete) {
                sessionLocked = WinLock.lockNow();
            } else {
                finishRelaxAndAskStart();
            }
        });
        scheduler.scheduleAtFixedRate(() -> {
            if (!recoveryEnabled || shuttingDown) return;
            try {
                long now = System.currentTimeMillis();
                long heartbeatGap = Math.max(0, now - schedulerHeartbeatMs);
                schedulerHeartbeatMs = now;
                if (config.resetAfterLongPause && heartbeatGap > Math.max(60, config.longPauseResetSeconds) * 1_000L) {
                    resetRuntimeAfterLongPause();
                }
                checkStudyDayBoundary();
                checkLongPauseExpiry();
                checkSharedRest();
                recordActivity();
                persistRuntimeState();
                activityStore.flush(false);
            } catch (Exception ex) { System.err.println("Session checkpoint: " + ex.getMessage()); }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /** Called only after plan hooks/UI are wired, before any launch chooser. */
    public boolean restoreSession() {
        if (recoveryEnabled) return hasPausedSnapshot() || inRelax;
        activityStore.prepareRecording();
        RuntimeState saved = stateStore.load();
        recoveryEnabled = true;
        long now = System.currentTimeMillis();
        schedulerHeartbeatMs = now;
        LocalDate currentStudyDate = StudyDay.dateAt(now, ZoneId.systemDefault(), config.dayBoundarySeconds);
        LocalDate savedStudyDate = studyDateForRecovery(saved);
        boolean sameStudyDay = saved == null || saved.savedAt <= 0 || currentStudyDate.equals(savedStudyDate);
        long gapOrigin = saved == null ? 0 : (saved.pausedSinceEpochMs > 0 ? saved.pausedSinceEpochMs : saved.savedAt);
        boolean longGap = saved != null && config.resetAfterLongPause && gapOrigin > 0
                && now - gapOrigin > Math.max(60, config.longPauseResetSeconds) * 1_000L;
        boolean restored = saved != null && sameStudyDay && !longGap && saved.phase != RuntimeState.Phase.IDLE
                && (saved.remainingMs > 0 || (saved.phase == RuntimeState.Phase.RELAX && saved.awaitingUnlockAfterDeadline));
        if (restored && !saved.planId.isBlank() && planHooks != null && planHooks.getRemainingSeconds(saved.planId) > 0) {
            currentPlanId = saved.planId;
        }
        if (restored && saved.phase == RuntimeState.Phase.WORK) {
            // Recover elapsed work, but NEVER start charging time before the user's explicit action.
            pausedPhase = PausedPhase.WORK;
            pausedRemainingMs = saved.remainingMs;
            pausedConfigSignature = saved.signature;
            pausedElapsedMs = Math.max(0, saved.workSeconds * 1000L - saved.remainingMs);
            pausedSinceEpochMs = saved.pausedSinceEpochMs > 0 ? saved.pausedSinceEpochMs : saved.savedAt;
        } else if (restored) {
            SharedRestStore.Rest shared = sharedRestStore.poll();
            if (!saved.awaitingUnlockAfterDeadline && !saved.sharedRestId.isBlank() && shared != null && saved.sharedRestId.equals(shared.id())) {
                if (shared.remainingMs() <= 0) restored = false;
                else {
                    saved.remainingMs = shared.remainingMs(); sharedRestId = shared.id(); sharedRestMode = shared.mode();
                }
            }
            if (restored) {
                running.set(true);
                Config.ForceMode savedMode = config.mode;
                try { if (!saved.restMode.isBlank()) savedMode = Config.ForceMode.valueOf(saved.restMode); }
                catch (IllegalArgumentException ex) { /* legacy snapshots fall back to their configuration */ }
                try { if (!saved.restKind.isBlank()) normalRestKind = SessionStore.Kind.valueOf(saved.restKind); }
                catch (IllegalArgumentException ex) { normalRestKind = SessionStore.Kind.REST; }
                startRelaxWithRemaining(saved.awaitingUnlockAfterDeadline ? 1L : saved.remainingMs, true,
                        saved.awaitingUnlockAfterDeadline, saved.relaxSeconds, savedMode);
            }
        }
        if (!restored && saved != null && saved.phase != RuntimeState.Phase.IDLE) {
            currentPlanId = null;
            clearPauseSnapshot();
        }
        lastStudyDate = currentStudyDate;
        checkSharedRest();
        startCornerTicker();
        notifyStateChanged();
        persistRuntimeState();
        return restored || inRelax;
    }

    private synchronized void recordActivity() {
        long now = System.currentTimeMillis();
        boolean restLike = inRelax || reminderActive;
        long end = inWork ? Math.min(now, workEndEpochMs)
                : restLike ? ((reminderActive || relaxDeadlineReached) ? now : Math.min(now, relaxEndEpochMs))
                : now;
        boolean recordable = (running.get() && (inWork || inRelax)) || reminderActive;
        if (recoveryEnabled && recordable && activityLastTickMs > 0) {
            // Do not turn hours of computer sleep or a clock jump into verified activity.
            if (end >= activityLastTickMs && end - activityLastTickMs <= 5000) {
                activityStore.record(activityLastTickMs, end,
                        reminderActive ? SessionStore.Kind.REMINDER_REST : inRelax ? normalRestKind : SessionStore.Kind.FOCUS,
                        restLike ? "" : currentPlanId, restLike ? "" : getCurrentPlanName(), activitySessionId);
            }
        }
        activityLastTickMs = now;
    }

    private RuntimeState snapshot() {
        RuntimeState state = new RuntimeState();
        state.workSeconds = getActiveWorkTargetSeconds(); state.relaxSeconds = getActiveRelaxTargetSeconds();
        state.planId = currentPlanId; state.signature = configSignature();
        state.restMode = relaxMode().name(); state.restKind = normalRestKind.name();
        state.savedAt = System.currentTimeMillis(); state.sharedRestId = sharedRestId;
        state.studyDate = StudyDay.dateAt(state.savedAt, ZoneId.systemDefault(), config.dayBoundarySeconds).toString();
        if (inRelax && running.get()) {
            state.phase = RuntimeState.Phase.RELAX; state.remainingMs = Math.max(0, relaxEndEpochMs-state.savedAt);
            state.awaitingUnlockAfterDeadline = relaxDeadlineReached && sessionLocked;
        } else if (inWork && running.get()) {
            state.phase = RuntimeState.Phase.WORK; state.remainingMs = Math.max(0, workEndEpochMs-state.savedAt);
        } else if (pausedPhase != PausedPhase.NONE) {
            state.phase = pausedPhase == PausedPhase.WORK ? RuntimeState.Phase.WORK : RuntimeState.Phase.RELAX;
            state.remainingMs = pausedRemainingMs; state.signature = pausedConfigSignature == null ? configSignature() : pausedConfigSignature;
            state.pausedSinceEpochMs = pausedSinceEpochMs;
        }
        return state;
    }

    public synchronized void persistRuntimeState() {
        if (!recoveryEnabled || shuttingDown) return;
        recordActivity();
        stateStore.save(snapshot());
    }

    /** Also installed as a shutdown hook; forcibly killing the JVM still has the last one-second checkpoint. */
    public synchronized void persistForShutdown() {
        if (shuttingDown) return;
        consumePlanElapsedTime();
        persistRuntimeState();
        activityStore.flush(true);
        shuttingDown = true;
    }

    private synchronized void checkSharedRest() {
        if (!sharedRestStore.isEnabled() || shuttingDown) return;
        SharedRestStore.Rest shared = sharedRestStore.poll();
        if (shared == null) return;
        if (inRelax && shared.id().equals(sharedRestId)) {
            if (shared.remainingMs() <= 0) return; // normal scheduled completion owns the existing next-cycle flow
            return;
        }
        if (shared.remainingMs() <= 0 || sessionLocked) return;
        if (inWork || pausedPhase == PausedPhase.WORK) {
            interruptedWork = snapshot();
            if (running.get()) stop();
        }
        sharedRestId = shared.id(); sharedRestMode = shared.mode();
        clearPauseSnapshot();
        running.set(true);
        startRelaxWithRemaining(shared.remainingMs(), true);
    }

    public void setPlanHooks(PlanHooks hooks) { this.planHooks = hooks; }
    public void setReminderHooks(ReminderHooks hooks) { this.reminderHooks = hooks; }

    public void setCurrentPlanId(String planId) {
        recordActivity();
        consumePlanElapsedTime();
        activitySessionId = java.util.UUID.randomUUID().toString();
        this.currentPlanId = planId;
        this.planLastTickMs = System.currentTimeMillis();
        notifyStateChanged();
        refreshCornerNow();
        persistRuntimeState();
    }

    public String getCurrentPlanId() { return currentPlanId; }
    public void setOnRelaxFinishedStartRequested(Runnable r) { this.onRelaxFinishedStartRequested = r; }

    public void setQuickActionHandlers(Runnable onPauseRequested, Runnable onResumeRequested) {
        this.onPauseRequested = onPauseRequested;
        this.onResumeRequested = onResumeRequested;
        refreshCornerNow();
    }

    public long getWorkRemainingMs() {
        if (!running.get() || !inWork) return 0;
        return Math.max(0, workEndEpochMs - System.currentTimeMillis());
    }

    public long getRelaxRemainingMs() {
        if (!running.get() || !inRelax) return 0;
        return Math.max(0, relaxEndEpochMs - System.currentTimeMillis());
    }

    public long getPausedRemainingMs() { return Math.max(0, pausedRemainingMs); }

    public void onOverlaySettingChanged() {
        SwingUtilities.invokeLater(() -> corner.setPreferences(config.overlayShowSwitchButton, config.overlayShowButtonText));
        refreshCornerNow();
    }

    /** Apply a changed study-day boundary / long-pause policy without interrupting active work or rest. */
    public synchronized void onDayPolicyChanged() {
        // Flush the pre-change tail with its OLD ledger policy before registering the new rule.
        recordActivity();
        activityStore.setDayBoundarySeconds(config.dayBoundarySeconds);
        activityStore.flush(true);
        checkStudyDayBoundary();
        checkLongPauseExpiry();
        persistRuntimeState();
    }

    /** Live target update. Elapsed fact time is never shortened and no pause is required.
     * A running B/C rest pins its target AND mode at entry; edits affect subsequent cycles.
     * Scheduled reminder holds are unaffected by this API.
     */
    public synchronized void updateDurations(int workSeconds, int relaxSeconds) {
        if (workSeconds <= 0 || relaxSeconds <= 0) throw new IllegalArgumentException("Duration must be positive");
        Config candidate=com.eye.sync.StrictSettings.copy(config);candidate.workSeconds=workSeconds;candidate.relaxSeconds=relaxSeconds;
        if(!com.eye.sync.StrictSettings.allow(candidate))return;
        recordActivity();
        consumePlanElapsedTime();
        long now = System.currentTimeMillis();
        int previousWork = config.workSeconds;
        config.workSeconds = workSeconds;
        config.relaxSeconds = relaxSeconds;
        if (inWork && running.get() && !reminderActive) {
            activeWorkTargetSeconds = workSeconds;
            long elapsed = Math.max(0, now - workStartEpochMs);
            long remaining = workSeconds * 1000L - elapsed;
            cancelFuture(workFuture);
            if (remaining <= 0) startRelax();
            else {
                workEndEpochMs = now + remaining;
                workFuture.set(scheduler.schedule(this::startRelaxSafe, remaining, TimeUnit.MILLISECONDS));
            }
        } else if (inRelax && running.get() && !reminderActive && relaxMode() == Config.ForceMode.A_SOFT) {
            activeRelaxTargetSeconds = relaxSeconds;
            long elapsed = Math.max(0, now - relaxStartEpochMs);
            long remaining = Math.max(0, relaxSeconds * 1000L - elapsed);
            relaxEndEpochMs = now + remaining;
            relaxDeadlineReached = false;
            cancelFuture(relaxEndFuture);
            if (remaining == 0) onRelaxDeadlineReached();
            else relaxEndFuture.set(scheduler.schedule(this::onRelaxDeadlineReached, remaining, TimeUnit.MILLISECONDS));
        } else if (!running.get() && pausedPhase != PausedPhase.NONE) {
            // A paused timer stays paused. Its already-earned elapsed time is retained.
            long target = (pausedPhase == PausedPhase.WORK ? workSeconds : relaxSeconds) * 1000L;
            pausedRemainingMs = Math.max(0, target - pausedElapsedMs);
            pausedConfigSignature = configSignature();
        } else if (reminderActive && reminderInterruptedWork) {
            // Do not let duration editing close or shorten a reminder's independent mask.
            long elapsed = Math.max(0, previousWork * 1000L - reminderWorkRemainingMs);
            reminderWorkRemainingMs = Math.max(0, workSeconds * 1000L - elapsed);
            pausedRemainingMs = reminderWorkRemainingMs; pausedConfigSignature = configSignature();
        }
        notifyStateChanged(); refreshCornerNow(); persistRuntimeState();
    }

    public void setOnStateChanged(Runnable r) { this.onStateChanged = r; }

    public boolean canResumeFromPause() {
        return pausedPhase != PausedPhase.NONE
                && !isPauseExpired(System.currentTimeMillis())
                && pausedConfigSignature != null
                && pausedConfigSignature.equals(configSignature());
    }

    public boolean hasPausedSnapshot() { return pausedPhase != PausedPhase.NONE; }
    public boolean isInWork() { return inWork; }
    public boolean isInRelax() { return inRelax; }
    public boolean isRunning() { return running.get(); }
    public boolean isReminderActive() { return reminderActive; }
    public boolean isSessionLocked() { return sessionLocked; }

    /** Project the current/next nominal rest window for conflict checks in the reminder editor. */
    public long[] projectedRelaxWindow() {
        long now = System.currentTimeMillis();
        if (inRelax) return new long[]{Math.min(relaxStartEpochMs, now), relaxEndEpochMs};
        if (inWork) {
            long start = workEndEpochMs;
            return new long[]{start, start + config.relaxSeconds * 1_000L};
        }
        if (!running.get() && pausedPhase == PausedPhase.WORK) {
            long start = now + Math.max(0, pausedRemainingMs);
            return new long[]{start, start + config.relaxSeconds * 1_000L};
        }
        return null;
    }

    public boolean canPauseNow() {
        return !reminderActive && running.get() && (inWork || (inRelax && relaxMode() == Config.ForceMode.A_SOFT));
    }

    private void notifyStateChanged() {
        Runnable r = onStateChanged;
        if (r != null) SwingUtilities.invokeLater(r);
    }

    public void start() {
        if (reminderActive) return;
        if (!running.compareAndSet(false, true)) return;
        relaxFinishedPendingConfirm = false;
        notifyStateChanged();
        startCornerTicker();
        if (canResumeFromPause()) resumeFromPause();
        else {
            clearPauseSnapshot();
            scheduleWork();
        }
    }

    public synchronized void restartFresh() {
        recordActivity();
        consumePlanElapsedTime();
        running.set(true);
        clearPauseSnapshot();
        relaxFinishedPendingConfirm = false;
        if (reminderActive) {
            cancelFuture(workFuture);cancelFuture(workTickerFuture);cancelFuture(relaxEndFuture);cancelFuture(overlayTickerFuture);
            inWork=false;inRelax=false;inputBlocker.stop();SwingUtilities.invokeLater(overlay::hideOverlay);
            reminderInterruptedWork=true;reminderWorkRemainingMs=config.workSeconds*1_000L;
            activityLastTickMs=System.currentTimeMillis();notifyStateChanged();persistRuntimeState();return;
        }
        notifyStateChanged();
        startCornerTicker();
        scheduleWork();
    }

    /** Enter a full rest from idle, work, pause, or an existing rest. */
    public synchronized boolean startRestNow() {
        clearPauseSnapshot();relaxFinishedPendingConfirm=false;
        reminderInterruptedWork=false;reminderWorkRemainingMs=0;
        running.set(true);
        sharedRestId="";sharedRestMode=null;
        normalRestKind = SessionStore.Kind.IMMEDIATE_REST;
        startRelaxWithRemaining(config.relaxSeconds * 1_000L, !reminderActive, false);
        return true;
    }

    public void stop() { stop(true, false); }
    public void stopWithoutResume() { stop(false, false); }
    private void stopForRelaxFinished() { stop(false, true); }

    private void stop(boolean preserveForResume, boolean keepRelaxFinishedFlag) {
        if (reminderActive) return;
        recordActivity();
        consumePlanElapsedTime();
        if (!running.compareAndSet(true, false)) return;

        if (preserveForResume) capturePauseSnapshot();
        else clearPauseSnapshot();
        if (!keepRelaxFinishedFlag) relaxFinishedPendingConfirm = false;

        inRelax = false;
        inWork = false;
        inputBlocker.stop();
        SwingUtilities.invokeLater(overlay::hideOverlay);
        cancelFuture(workFuture);
        cancelFuture(workTickerFuture);
        cancelFuture(relaxEndFuture);
        cancelFuture(overlayTickerFuture);
        startCornerTicker();
        notifyStateChanged();
        persistRuntimeState();
    }

    public void shutdown() {
        persistForShutdown();
        stopWithoutResume();
        cancelFuture(cornerTickerFuture);
        SwingUtilities.invokeLater(corner::hide);
        watcher.stop();
        scheduler.shutdownNow();
    }

    private void scheduleWork() { scheduleWorkWithRemaining(config.workSeconds * 1_000L, true); }

    private void scheduleWorkWithRemaining(long remainingMs, boolean freshCycle) {
        if (!running.get()) return;

        recordActivity();
        activeRelaxMode = null;
        activitySessionId = java.util.UUID.randomUUID().toString();
        activeWorkTargetSeconds = config.workSeconds;
        inRelax = false;
        relaxDeadlineReached = false;
        sharedRestMode = null;
        sharedRestId = "";
        inWork = true;
        inputBlocker.stop();

        long delayMs = Math.max(1_000L, remainingMs);
        long totalMs = config.workSeconds * 1_000L;
        long now = System.currentTimeMillis();
        workStartEpochMs = now - Math.max(0, totalMs - delayMs);
        workEndEpochMs = now + delayMs;
        activityLastTickMs = now;
        planLastTickMs = now;
        if (freshCycle) workDisplayMode = 0;

        SwingUtilities.invokeLater(overlay::hideOverlay);
        cancelFuture(workFuture);
        cancelFuture(workTickerFuture);
        cancelFuture(relaxEndFuture);
        cancelFuture(overlayTickerFuture);
        cancelFuture(cornerTickerFuture);
        startCornerTicker();

        ScheduledFuture<?> tick = scheduler.scheduleAtFixedRate(this::consumePlanElapsedTime, 250, 250, TimeUnit.MILLISECONDS);
        workTickerFuture.set(tick);
        workFuture.set(scheduler.schedule(this::startRelaxSafe, delayMs, TimeUnit.MILLISECONDS));
        notifyStateChanged();
        persistRuntimeState();
    }

    private synchronized void consumePlanElapsedTime() {
        if (!running.get() || !inWork) return;
        String planId = currentPlanId;
        PlanHooks hooks = planHooks;
        long now = System.currentTimeMillis();
        long last = planLastTickMs;
        long wholeSeconds = Math.max(0, (now - last) / 1_000L);
        if (wholeSeconds <= 0) return;
        planLastTickMs = last + wholeSeconds * 1_000L;
        if (planId == null || hooks == null) return;

        int consume = (int) Math.min(Integer.MAX_VALUE, wholeSeconds);
        int remaining = hooks.onConsumeSeconds(planId, consume);
        notifyStateChanged();
        if (remaining <= 0 && planId.equals(currentPlanId)) {
            recordActivity();
            currentPlanId = null;
            activitySessionId = java.util.UUID.randomUUID().toString();
            hooks.onPlanDepleted(planId);
        }
    }

    private synchronized void startRelaxSafe() {
        // A cancelled timer may already have been dispatched during a live duration edit.
        if (running.get() && inWork && System.currentTimeMillis() >= workEndEpochMs) startRelax();
    }

    private void startRelax() {
        normalRestKind = SessionStore.Kind.REST;
        startRelaxWithRemaining(config.relaxSeconds * 1_000L, true, false);
    }

    private void startRelaxWithRemaining(long remainingMs, boolean lockNow) {
        startRelaxWithRemaining(remainingMs, lockNow, false);
    }

    private void startRelaxWithRemaining(long remainingMs, boolean lockNow, boolean deadlineAlreadyReached) {
        startRelaxWithRemaining(remainingMs, lockNow, deadlineAlreadyReached, config.relaxSeconds, config.mode);
    }

    private void startRelaxWithRemaining(long remainingMs, boolean lockNow, boolean deadlineAlreadyReached,
                                         int targetSeconds, Config.ForceMode pinnedMode) {
        if (!running.get()) return;
        // Flush whole seconds immediately before work ticker is cancelled.
        recordActivity();
        consumePlanElapsedTime();
        cancelFuture(workFuture);
        finishingRelax.set(false);
        activeRelaxMode = pinnedMode;
        activeRelaxTargetSeconds = targetSeconds;
        activitySessionId = java.util.UUID.randomUUID().toString();
        inWork = false;
        cancelFuture(workTickerFuture);
        inRelax = true;

        long now = System.currentTimeMillis();
        long requiredMs = targetSeconds * 1_000L;
        long cappedRemainingMs = Math.max(1L, remainingMs);
        relaxDeadlineReached = deadlineAlreadyReached;
        if (!deadlineAlreadyReached && recoveryEnabled && sharedRestStore.isEnabled() && relaxMode() != Config.ForceMode.A_SOFT && sharedRestId.isBlank()) {
            SharedRestStore.Rest shared = sharedRestStore.begin(cappedRemainingMs, relaxMode());
            if (shared != null) {
                sharedRestId = shared.id(); sharedRestMode = shared.mode(); cappedRemainingMs = shared.remainingMs();
            }
        }
        activityLastTickMs = now;
        long elapsedBeforeMs = Math.max(0, requiredMs - cappedRemainingMs);
        relaxStartEpochMs = now - elapsedBeforeMs;
        relaxEndEpochMs = deadlineAlreadyReached ? now : now + cappedRemainingMs;
        relaxDisplayMode = 0;
        startCornerTicker();
        notifyStateChanged();

        persistRuntimeState(); // persist BEFORE locking, including a crash during the OS transition
        restLockSuppressedForReminder = reminderActive;
        long due = -1;
        ReminderHooks hooks = reminderHooks;
        if (!deadlineAlreadyReached && hooks != null && relaxEndEpochMs > now) {
            due = hooks.nextDueBetween(now, relaxEndEpochMs);
        }
        if (lockNow && due > 0) {
            // An overlapping reminder must be reachable on the desktop. This rest still counts,
            // but its initial lock/forced overlay is deferred until the reminder is completed.
            restLockSuppressedForReminder = true;
            sessionLocked = false;
            long dueCopy = due;
            SwingUtilities.invokeLater(() -> hooks.onRestOverlap(dueCopy, relaxEndEpochMs));
        } else if (lockNow) {
            sessionLocked = WinLock.lockNow();
        }

        inputBlocker.stop();
        SwingUtilities.invokeLater(overlay::hideOverlay);

        if (!restLockSuppressedForReminder && relaxMode() == Config.ForceMode.C_ULTRA) startOverlayTicker();

        cancelFuture(relaxEndFuture);
        long delay = deadlineAlreadyReached ? 1L : cappedRemainingMs;
        relaxEndFuture.set(scheduler.schedule(this::onRelaxDeadlineReached, delay, TimeUnit.MILLISECONDS));
        persistRuntimeState(); // commit the new rest phase immediately, not just at the next tick
    }


    /**
     * The configured duration is a minimum rest duration, not permission to start work behind
     * the lock screen.  If the workstation is still locked at zero, the rest remains active
     * until Windows reports an unlock/connect event.
     */
    private synchronized void onRelaxDeadlineReached() {
        if (!running.get() || !inRelax || System.currentTimeMillis() < relaxEndEpochMs) return;
        relaxDeadlineReached = true;
        persistRuntimeState();
        if (reminderActive) {
            refreshCornerNow();
            return;
        }
        if (restLockSuppressedForReminder) {
            // Give the due-reminder scheduler a short race-free window to take ownership.
            scheduler.schedule(() -> {
                if (!running.get() || !inRelax || reminderActive) return;
                restLockSuppressedForReminder = false;
                finishRelaxAndAskStart();
            }, 900, TimeUnit.MILLISECONDS);
            return;
        }
        if (sessionLocked) {
            refreshCornerNow();
            return;
        }
        finishRelaxAndAskStart();
    }

    private void showBlockingRelaxOverlay() {
        if (!running.get() || !inRelax || reminderActive || relaxMode() != Config.ForceMode.B_STRONG) return;
        startOverlayTicker();
        // Hook installation is deferred until the EDT has presented the shield.
    }

    private void activateRelaxInputAfterPaint(){
        overlay.afterPaint(()->{
            if(running.get()&&inRelax&&!reminderActive&&!sessionLocked&&relaxMode()==Config.ForceMode.B_STRONG)inputBlocker.start();
        });
    }

    private void startOverlayTicker() {
        SwingUtilities.invokeLater(() -> {
            if(!running.get()||!inRelax||reminderActive||sessionLocked)return;
            overlay.showOverlay(formatRemain());
            activateRelaxInputAfterPaint();
        });
        cancelFuture(overlayTickerFuture);
        overlayTickerFuture.set(scheduler.scheduleAtFixedRate(() -> {
            if (!running.get() || !inRelax || reminderActive) return;
            SwingUtilities.invokeLater(() -> {
                if(!running.get()||!inRelax||reminderActive||sessionLocked)return;
                overlay.updateText(formatRemain());
                if(!overlay.isPresented())inputBlocker.stop();
                else if(relaxMode()==Config.ForceMode.B_STRONG&&!inputBlocker.isActive())activateRelaxInputAfterPaint();
            });
        }, 0, 1, TimeUnit.SECONDS));
    }

    private void startCornerTicker() {
        cancelFuture(cornerTickerFuture);
        cornerTickerFuture.set(scheduler.scheduleAtFixedRate(this::refreshCornerNow, 0, 1, TimeUnit.SECONDS));
    }

    private void toggleCornerDisplayMode() {
        if (!running.get()) return;
        if (inRelax) relaxDisplayMode = (relaxDisplayMode + 1) % 2;
        else if (inWork) workDisplayMode = (workDisplayMode + 1) % (hasCurrentPlan() ? 4 : 2);
        refreshCornerNow();
    }

    private boolean hasCurrentPlan() {
        if (currentPlanId == null) return false;
        PlanHooks hooks = planHooks;
        return hooks != null && hooks.getPlannedSeconds(currentPlanId) > 0;
    }

    private String getCurrentPlanName() {
        PlanHooks hooks = planHooks;
        return currentPlanId == null || hooks == null ? null : hooks.getPlanName(currentPlanId);
    }

    private int getCurrentPlanPlannedSeconds() {
        return hasCurrentPlan() ? Math.max(0, planHooks.getPlannedSeconds(currentPlanId)) : 0;
    }

    private int getCurrentPlanRemainingSeconds() {
        return hasCurrentPlan() ? Math.max(0, planHooks.getRemainingSeconds(currentPlanId)) : 0;
    }

    private int getCurrentPlanElapsedSeconds() {
        return Math.max(0, getCurrentPlanPlannedSeconds() - getCurrentPlanRemainingSeconds());
    }

    private String formatPlanStatus(boolean showRemaining) {
        if (!hasCurrentPlan()) return null;
        String name = getCurrentPlanName();
        if (name == null || name.isBlank()) name = com.eye.i18n.I18n.text("当前计划");
        String detail = showRemaining
                ? com.eye.i18n.I18n.text("剩余 ") + PlanItemFmt.fmtHms(getCurrentPlanRemainingSeconds())
                : com.eye.i18n.I18n.text("已投入 ") + PlanItemFmt.fmtHms(getCurrentPlanElapsedSeconds());
        return name + " · " + detail;
    }

    private void refreshCornerActionButton() {
        String text = com.eye.i18n.I18n.text("暂停");
        boolean enabled = false;
        Runnable action = null;
        if (!running.get()) {
            text = canResumeFromPause() ? com.eye.i18n.I18n.text("继续") : com.eye.i18n.I18n.text("开始");
            enabled = onResumeRequested != null;
            action = onResumeRequested;
        } else if (inWork || (inRelax && relaxMode() == Config.ForceMode.A_SOFT)) {
            enabled = onPauseRequested != null;
            action = onPauseRequested;
        }
        final String buttonText = text;
        final boolean buttonEnabled = enabled;
        final Runnable buttonAction = action;
        SwingUtilities.invokeLater(() -> {
            corner.setPreferences(config.overlayShowSwitchButton, config.overlayShowButtonText);
            corner.setActionButton(buttonText, buttonEnabled, buttonAction);
        });
    }

    private void refreshCornerNow() {
        refreshCornerActionButton();
        if (!config.showStatusOverlay || sessionLocked) {
            SwingUtilities.invokeLater(corner::hide);
            return;
        }

        if (relaxFinishedPendingConfirm) {
            SwingUtilities.invokeLater(() -> corner.showStatus(com.eye.i18n.I18n.text("休息结束"), com.eye.i18n.I18n.text("等待下一步"), "00:00", 0, true));
            return;
        }

        if (!running.get()) {
            long remain = hasPausedSnapshot() ? pausedRemainingMs : config.workSeconds * 1_000L;
            String clock = fmtMmSs(remain);
            String idleText = hasPausedSnapshot() ? com.eye.i18n.I18n.text("已暂停") : hasCurrentPlan() ? com.eye.i18n.I18n.text("点击开始计划") : com.eye.i18n.I18n.text("选择一个计划开始");
            SwingUtilities.invokeLater(() -> corner.showStatus(idleText, "", clock,
                    ratio(remain, config.workSeconds * 1_000L), false));
            return;
        }

        if (inWork) {
            long remain = Math.max(0, workEndEpochMs - System.currentTimeMillis());
            long total = config.workSeconds * 1_000L;
            long elapsed = Math.max(0, total - remain);
            String planName = getCurrentPlanName();
            String primary = planName == null || planName.isBlank() ? com.eye.i18n.I18n.text("自由专注") : planName;
            String secondary;
            String clock;
            double ring;
            switch (workDisplayMode) {
                case 1 -> {
                    secondary = com.eye.i18n.I18n.text("本轮已专注 ") + fmtMmSs(elapsed);
                    clock = fmtMmSs(elapsed);
                    ring = ratio(remain, total);
                }
                case 2 -> {
                    if (hasCurrentPlan()) {
                        int planRemain = getCurrentPlanRemainingSeconds();
                        int planTotal = getCurrentPlanPlannedSeconds();
                        secondary = com.eye.i18n.I18n.text("任务剩余 ") + PlanItemFmt.fmtCompact(planRemain);
                        clock = PlanItemFmt.fmtCompact(planRemain);
                        ring = ratio(planRemain * 1_000L, planTotal * 1_000L);
                    } else {
                        secondary = com.eye.i18n.I18n.text("本轮剩余 ") + fmtMmSs(remain);
                        clock = fmtMmSs(remain);
                        ring = ratio(remain, total);
                    }
                }
                case 3 -> {
                    if (hasCurrentPlan()) {
                        int planElapsed = getCurrentPlanElapsedSeconds();
                        int planTotal = getCurrentPlanPlannedSeconds();
                        secondary = com.eye.i18n.I18n.text("任务已用 ") + PlanItemFmt.fmtCompact(planElapsed);
                        clock = PlanItemFmt.fmtCompact(planElapsed);
                        ring = ratio(Math.max(0, planTotal - planElapsed) * 1_000L, planTotal * 1_000L);
                    } else {
                        secondary = com.eye.i18n.I18n.text("本轮剩余 ") + fmtMmSs(remain);
                        clock = fmtMmSs(remain);
                        ring = ratio(remain, total);
                    }
                }
                default -> {
                    secondary = com.eye.i18n.I18n.text("本轮剩余 ") + fmtMmSs(remain);
                    clock = fmtMmSs(remain);
                    ring = ratio(remain, total);
                }
            }
            final String outPrimary = primary, outSecondary = secondary, outClock = clock;
            final double outRing = ring;
            SwingUtilities.invokeLater(() -> corner.showStatus(outPrimary, outSecondary, outClock, outRing, false));
            return;
        }

        if (inRelax) {
            long now = System.currentTimeMillis();
            long remain = Math.max(0, relaxEndEpochMs - now);
            long total = getActiveRelaxTargetSeconds() * 1_000L;
            // Do not cap elapsed rest at the configured minimum: locked time after the deadline
            // and reminder-overlay time continue to be genuine rest until the user returns.
            long elapsed = Math.max(0, now - relaxStartEpochMs);
            boolean elapsedView = relaxDisplayMode == 1 || relaxDeadlineReached;
            String secondary = elapsedView ? com.eye.i18n.I18n.text("本轮已休息 ") + fmtMmSs(elapsed) : com.eye.i18n.I18n.text("本轮剩余 ") + fmtMmSs(remain);
            String clock = fmtMmSs(elapsedView ? elapsed : remain);
            double ring = ratio(remain, total);
            SwingUtilities.invokeLater(() -> corner.showStatus(reminderActive ? com.eye.i18n.I18n.text("提醒中 · 计入休息") : com.eye.i18n.I18n.text("休息中"), secondary, clock, ring, true));
            return;
        }
        if (reminderActive) {
            SwingUtilities.invokeLater(() -> corner.showStatus(com.eye.i18n.I18n.text("提醒中"), com.eye.i18n.I18n.text("遮罩期间计入休息"), "—", 0, true));
        }
    }

    private static double ratio(long remaining, long total) {
        if (total <= 0) return 0;
        return Math.max(0, Math.min(1, remaining / (double) total));
    }

    private static String fmtMmSs(long ms) {
        long sec = Math.max(0, ms) / 1000;
        long hh = sec / 3600;
        long mm = (sec % 3600) / 60;
        long ss = sec % 60;
        return hh > 0 ? String.format("%02d:%02d:%02d", hh, mm, ss) : String.format("%02d:%02d", mm, ss);
    }

    private String formatRemain() {
        long ms = Math.max(0, relaxEndEpochMs - System.currentTimeMillis());
        return relaxDeadlineReached ? com.eye.i18n.I18n.text("休息中 · 等待解锁") : com.eye.i18n.I18n.text("休息中 ") + fmtMmSs(ms);
    }

    private void finishRelaxAndAskStart() {
        if (!running.get() || !inRelax || reminderActive) return;
        if (sessionLocked) {
            relaxDeadlineReached = true;
            persistRuntimeState();
            return;
        }
        if (!finishingRelax.compareAndSet(false, true)) return;
        recordActivity();
        relaxFinishedPendingConfirm = true;
        stopForRelaxFinished();
        relaxDeadlineReached = false;
        restLockSuppressedForReminder = false;
        sharedRestId = ""; sharedRestMode = null;
        if (interruptedWork != null) {
            pausedPhase = PausedPhase.WORK; pausedRemainingMs = interruptedWork.remainingMs;
            pausedConfigSignature = interruptedWork.signature; currentPlanId = interruptedWork.planId;
            interruptedWork = null;
        }
        persistRuntimeState();

        if (config.autoContinueCycles) {
            relaxFinishedPendingConfirm = false;
            refreshCornerNow();
            Runnable handler = onRelaxFinishedStartRequested;
            if (handler != null) handler.run();
            else start();
            return;
        }

        final int completedTarget = activeRelaxTargetSeconds;
        SwingUtilities.invokeLater(() -> showStoppedDialogWithStart(com.eye.i18n.I18n.text("你已经休息满 ") + PlanItemFmt.fmtHms(completedTarget) + com.eye.i18n.I18n.text("。\n\n是否立即开始下一轮计时？")));
    }

    private void showStoppedDialogWithStart(String message) {
        final int[] result = {JOptionPane.CLOSED_OPTION};
        runOnEdtAndWait(() -> {
            Object[] options = {com.eye.i18n.I18n.text("立即开始"), com.eye.i18n.I18n.text("稍后再说")};
            JOptionPane pane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION, null, options, options[0]);
            JDialog dialog = pane.createDialog(null, com.eye.i18n.I18n.text("提示"));
            dialog.setAlwaysOnTop(true);
            dialog.setModal(true);
            dialog.setVisible(true);
            Object value = pane.getValue();
            if (value != null) {
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equals(value)) { result[0] = i; break; }
                }
            }
        });

        relaxFinishedPendingConfirm = false;
        refreshCornerNow();
        if (result[0] == 0) {
            Runnable handler = onRelaxFinishedStartRequested;
            if (handler != null) handler.run();
            else start();
        }
    }

    /** Called when a scheduled reminder takes the screen. The overlay interval is counted as rest. */
    public synchronized void beginReminderOverlay() {
        if (reminderActive) return;
        recordActivity();
        long now = System.currentTimeMillis();
        reminderInterruptedWork = false;
        reminderWorkRemainingMs = 0;
        if (running.get() && inWork) {
            consumePlanElapsedTime();
            reminderInterruptedWork = true;
            reminderWorkRemainingMs = Math.max(1_000L, workEndEpochMs - now);
            pausedPhase = PausedPhase.WORK;
            pausedRemainingMs = reminderWorkRemainingMs;
            pausedConfigSignature = configSignature();
            inWork = false;
            cancelFuture(workFuture);
            cancelFuture(workTickerFuture);
        }
        reminderActive = true;
        if (!inRelax) activitySessionId = java.util.UUID.randomUUID().toString();
        activityLastTickMs = now;
        inputBlocker.stop();
        SwingUtilities.invokeLater(overlay::hideOverlay);
        cancelFuture(overlayTickerFuture);
        notifyStateChanged();
        persistRuntimeState();
    }

    /** Complete a reminder after the user's manual close. Work resumes only now. */
    public synchronized void endReminderOverlay(boolean resetWorkTimer) {
        if (!reminderActive) return;
        recordActivity();
        reminderActive = false;
        if (!inRelax) activitySessionId = java.util.UUID.randomUUID().toString();
        activityLastTickMs = System.currentTimeMillis();

        if (inRelax) {
            restLockSuppressedForReminder = false;
            if (System.currentTimeMillis() >= relaxEndEpochMs || relaxDeadlineReached) {
                relaxDeadlineReached = true;
                finishRelaxAndAskStart();
            } else {
                applyRestEnforcementNowOrSuppress();
                notifyStateChanged();
                persistRuntimeState();
            }
            return;
        }

        if (reminderInterruptedWork && running.get()) {
            long remaining = resetWorkTimer ? config.workSeconds * 1_000L : Math.max(0L, reminderWorkRemainingMs);
            clearPauseSnapshot();
            reminderInterruptedWork = false;
            reminderWorkRemainingMs = 0;
            if (remaining <= 0) startRelax();
            else scheduleWorkWithRemaining(remaining, resetWorkTimer);
            return;
        }

        // A reminder that fired while the work timer was already paused remains paused.
        if (!running.get() && resetWorkTimer && pausedPhase == PausedPhase.WORK) {
            pausedRemainingMs = config.workSeconds * 1_000L;
            pausedConfigSignature = configSignature();
        }
        reminderInterruptedWork = false;
        reminderWorkRemainingMs = 0;
        notifyStateChanged();
        persistRuntimeState();
    }

    /** Re-evaluate a suppressed rest after the user edits reminder schedules. */
    public synchronized void onReminderScheduleChanged() {
        if (!inRelax || reminderActive || !restLockSuppressedForReminder) return;
        long now = System.currentTimeMillis();
        ReminderHooks hooks = reminderHooks;
        long due = hooks == null ? -1 : hooks.nextDueBetween(now, relaxEndEpochMs);
        if (due <= 0) {
            restLockSuppressedForReminder = false;
            applyRestEnforcementNowOrSuppress();
        }
    }

    private void applyRestEnforcementNowOrSuppress() {
        if (!running.get() || !inRelax || reminderActive) return;
        long now = System.currentTimeMillis();
        ReminderHooks hooks = reminderHooks;
        long due = hooks == null ? -1 : hooks.nextDueBetween(now, relaxEndEpochMs);
        if (due > 0) {
            restLockSuppressedForReminder = true;
            long dueCopy = due;
            SwingUtilities.invokeLater(() -> hooks.onRestOverlap(dueCopy, relaxEndEpochMs));
            return;
        }
        restLockSuppressedForReminder = false;
        sessionLocked = WinLock.lockNow();
        if (!sessionLocked && relaxMode() == Config.ForceMode.B_STRONG) showBlockingRelaxOverlay();
        else if (!sessionLocked && relaxMode() == Config.ForceMode.C_ULTRA) startOverlayTicker();
        persistRuntimeState();
    }

    private void capturePauseSnapshot() {
        long now = System.currentTimeMillis();
        if (pausedSinceEpochMs <= 0) pausedSinceEpochMs = now;
        if (inWork) {
            consumePlanElapsedTime();
            pausedPhase = PausedPhase.WORK;
            pausedElapsedMs = Math.max(0, now - workStartEpochMs);
            pausedRemainingMs = Math.max(1_000L, workEndEpochMs - now);
            pausedConfigSignature = configSignature();
            return;
        }
        if (inRelax) {
            pausedPhase = PausedPhase.RELAX;
            pausedElapsedMs = Math.max(0, now - relaxStartEpochMs);
            pausedRemainingMs = Math.max(1_000L, relaxEndEpochMs - now);
            pausedConfigSignature = configSignature();
            return;
        }
        clearPauseSnapshot();
    }

    private void clearPauseSnapshot() {
        pausedPhase = PausedPhase.NONE;
        pausedRemainingMs = 0;
        pausedConfigSignature = null;
        pausedSinceEpochMs = 0;
        pausedElapsedMs = 0;
    }

    private void resumeFromPause() {
        PausedPhase phase = pausedPhase;
        long remaining = pausedRemainingMs;
        clearPauseSnapshot();
        if (phase == PausedPhase.WORK && remaining <= 0) startRelax();
        else if (phase == PausedPhase.WORK) scheduleWorkWithRemaining(remaining, false);
        else if (phase == PausedPhase.RELAX) startRelaxWithRemaining(remaining, false);
        else scheduleWork();
    }

    private boolean isPauseExpired(long now) {
        return config.resetAfterLongPause && pausedSinceEpochMs > 0
                && now - pausedSinceEpochMs > Math.max(60, config.longPauseResetSeconds) * 1_000L;
    }

    /** A long OS suspend/clock gap invalidates resumable work/rest state, just like a long
     * manual pause or a long shutdown. Reminder overlays are intentionally not dismissed here:
     * sleeping the PC must not be a way to bypass a due forced reminder. */
    private synchronized void resetRuntimeAfterLongPause() {
        if (reminderActive) return;
        if (!running.get() && pausedPhase == PausedPhase.NONE) return;
        recordActivity();
        running.set(false);
        inWork = false;
        inRelax = false;
        relaxDeadlineReached = false;
        relaxFinishedPendingConfirm = false;
        restLockSuppressedForReminder = false;
        interruptedWork = null;
        currentPlanId = null;
        sharedRestId = "";
        sharedRestMode = null;
        clearPauseSnapshot();
        inputBlocker.stop();
        SwingUtilities.invokeLater(overlay::hideOverlay);
        cancelFuture(workFuture);
        cancelFuture(workTickerFuture);
        cancelFuture(relaxEndFuture);
        cancelFuture(overlayTickerFuture);
        activityLastTickMs = System.currentTimeMillis();
        planLastTickMs = activityLastTickMs;
        notifyStateChanged();
    }

    private synchronized void checkLongPauseExpiry() {
        if (running.get() || reminderActive || pausedPhase == PausedPhase.NONE) return;
        if (!isPauseExpired(System.currentTimeMillis())) return;
        clearPauseSnapshot();
        currentPlanId = null;
        notifyStateChanged();
    }

    private LocalDate studyDateForRecovery(RuntimeState saved) {
        if (saved == null || saved.savedAt <= 0) return StudyDay.dateNow(config);
        if (saved.studyDate != null && !saved.studyDate.isBlank()) {
            try { return LocalDate.parse(saved.studyDate); }
            catch (RuntimeException ignored) { /* fall through to timestamp-derived day */ }
        }
        return StudyDay.dateAt(saved.savedAt, ZoneId.systemDefault(), config.dayBoundarySeconds);
    }

    private synchronized void checkStudyDayBoundary() {
        LocalDate today = StudyDay.dateNow(config);
        if (today.equals(lastStudyDate)) return;
        lastStudyDate = today;
        PlanHooks hooks = planHooks;
        if (hooks != null) hooks.onStudyDayChanged(today);
        if (currentPlanId != null && (hooks == null || hooks.getRemainingSeconds(currentPlanId) <= 0)) {
            currentPlanId = null;
        }
        // Work, normal rest and reminder-rest continue across 04:00. A paused/idle snapshot does not.
        if (!inWork && !inRelax && !reminderActive) clearPauseSnapshot();
        notifyStateChanged();
        persistRuntimeState();
    }

    private String configSignature() {
        return config.workSeconds + "|" + config.relaxSeconds + "|" + config.mode.name();
    }

    private void runOnEdtAndWait(Runnable action) {
        try {
            if (SwingUtilities.isEventDispatchThread()) action.run();
            else SwingUtilities.invokeAndWait(action);
        } catch (Exception ignored) {}
    }

    private static void cancelFuture(AtomicReference<ScheduledFuture<?>> ref) {
        ScheduledFuture<?> f = ref.getAndSet(null);
        if (f != null) f.cancel(false);
    }

    private static final class PlanItemFmt {
        private static String fmtHms(int totalSeconds) {
            int s = Math.max(0, totalSeconds);
            return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        }
        private static String fmtCompact(int totalSeconds) {
            int s = Math.max(0, totalSeconds);
            return s >= 3600 ? fmtHms(s) : String.format("%02d:%02d", s / 60, s % 60);
        }
    }
}
