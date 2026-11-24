package com.eye.win;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.LowLevelMouseProc;
import com.sun.jna.platform.win32.WinUser.MSLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.MSG;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches global Windows mouse button-down events without consuming them.
 *
 * <p>This is intentionally event-driven instead of polling GetAsyncKeyState. A short desktop or
 * other-process click can begin and end entirely between polling intervals; WH_MOUSE_LL observes
 * the actual button-down event and therefore gives tray popups native click-away behaviour. The
 * listener intentionally receives no native pixel coordinates: Swing/AWT may use a DPI-scaled
 * coordinate space, so consumers must sample the pointer through AWT before comparing UI bounds.</p>
 */
public final class GlobalMouseClickWatcher {
    private static final int WH_MOUSE_LL = 14;
    private static final int WM_QUIT = 0x0012;
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_RBUTTONDOWN = 0x0204;
    private static final int WM_MBUTTONDOWN = 0x0207;
    private static final int WM_XBUTTONDOWN = 0x020B;

    @FunctionalInterface
    public interface Listener {
        void onMouseButtonDown();
    }

    private final AtomicBoolean requested = new AtomicBoolean(false);
    private final AtomicBoolean hookInstalled = new AtomicBoolean(false);
    private volatile Thread hookThread;
    private volatile int hookThreadId;
    private volatile HHOOK mouseHook;
    private volatile LowLevelMouseProc mouseProc;
    private volatile Listener listener;

    public synchronized boolean start(Listener listener) {
        if (!Platform.isWindows() || listener == null) return false;
        if (!requested.compareAndSet(false, true)) return hookInstalled.get();
        this.listener = listener;

        CountDownLatch ready = new CountDownLatch(1);
        hookThread = new Thread(() -> runHook(ready), "EyeRelax-TrayOutsideClick");
        hookThread.setDaemon(true);
        hookThread.start();
        try {
            ready.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return hookInstalled.get();
    }

    public synchronized void stop() {
        if (!requested.getAndSet(false)) {
            listener = null;
            return;
        }
        int tid = hookThreadId;
        if (Platform.isWindows() && tid != 0) {
            User32.INSTANCE.PostThreadMessage(tid, WM_QUIT, new WPARAM(0), new LPARAM(0));
        }
        Thread t = hookThread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        listener = null;
    }

    public boolean isActive() {
        return requested.get() && hookInstalled.get();
    }

    private void runHook(CountDownLatch ready) {
        hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();
        HINSTANCE module = Kernel32.INSTANCE.GetModuleHandle(null);
        mouseProc = (nCode, wParam, info) -> {
            if (nCode >= 0 && requested.get() && isButtonDown(wParam.intValue()) && info != null) {
                Listener callback = listener;
                if (callback != null) {
                    try {
                        callback.onMouseButtonDown();
                    } catch (Throwable ignored) {
                        // A listener must never break the native hook chain.
                    }
                }
            }
            return User32.INSTANCE.CallNextHookEx(mouseHook, nCode, wParam, asLParam(info));
        };

        try {
            mouseHook = User32.INSTANCE.SetWindowsHookEx(WH_MOUSE_LL, mouseProc, module, 0);
            hookInstalled.set(mouseHook != null);
            if (!hookInstalled.get()) {
                requested.set(false);
                return;
            }

            ready.countDown();
            MSG msg = new MSG();
            while (requested.get()) {
                int result = User32.INSTANCE.GetMessage(msg, null, 0, 0);
                if (result <= 0) break;
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        } finally {
            ready.countDown();
            HHOOK hook = mouseHook;
            mouseHook = null;
            if (hook != null) User32.INSTANCE.UnhookWindowsHookEx(hook);
            hookInstalled.set(false);
            requested.set(false);
            hookThreadId = 0;
            mouseProc = null;
            listener = null;
            hookThread = null;
        }
    }

    private static boolean isButtonDown(int message) {
        return message == WM_LBUTTONDOWN
                || message == WM_RBUTTONDOWN
                || message == WM_MBUTTONDOWN
                || message == WM_XBUTTONDOWN;
    }

    private static LPARAM asLParam(MSLLHOOKSTRUCT info) {
        return info == null ? new LPARAM(0) : new LPARAM(Pointer.nativeValue(info.getPointer()));
    }
}
