package com.eye.win;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc;
import com.sun.jna.platform.win32.WinUser.MSG;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blocks ordinary keyboard input while a reminder overlay owns the desktop.
 * Mouse input deliberately remains available so the user can click the reminder's
 * own close button after the configured minimum hold. Ctrl+Alt+Delete remains an
 * OS secure-attention sequence and is intentionally outside application control.
 */
public final class KeyboardBlocker {
    private static final int WH_KEYBOARD_LL = 13;
    private static final int WM_QUIT = 0x0012;

    private final AtomicBoolean requested = new AtomicBoolean(false);
    private final AtomicBoolean hookInstalled = new AtomicBoolean(false);
    private volatile Thread hookThread;
    private volatile int hookThreadId;
    private volatile HHOOK keyboardHook;
    private volatile LowLevelKeyboardProc keyboardProc;

    private volatile java.util.function.BooleanSupplier presentationReady=()->true;
    public void setPresentationGuard(java.util.function.BooleanSupplier guard){presentationReady=java.util.Objects.requireNonNull(guard);}

    public synchronized boolean start() {
        if (!Platform.isWindows()) return false;
        if (!requested.compareAndSet(false, true)) return hookInstalled.get();
        CountDownLatch ready = new CountDownLatch(1);
        hookThread = new Thread(() -> runHook(ready), "EyeRelax-ReminderKeyboardBlocker");
        hookThread.setDaemon(true);
        hookThread.start();
        try { ready.await(1500, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return hookInstalled.get();
    }

    public synchronized void stop() {
        if (!requested.getAndSet(false)) return;
        int tid = hookThreadId;
        if (Platform.isWindows() && tid != 0) {
            User32.INSTANCE.PostThreadMessage(tid, WM_QUIT, new WPARAM(0), new LPARAM(0));
        }
        Thread t = hookThread;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(700); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    public boolean isActive() { return requested.get() && hookInstalled.get(); }

    private void runHook(CountDownLatch ready) {
        hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();
        HINSTANCE module = Kernel32.INSTANCE.GetModuleHandle(null);
        keyboardProc = (nCode, wParam, info) -> {
            if (nCode >= 0 && requested.get() && presentationReady.getAsBoolean()) return new LRESULT(1);
            return User32.INSTANCE.CallNextHookEx(keyboardHook, nCode, wParam, asLParam(info));
        };
        try {
            keyboardHook = User32.INSTANCE.SetWindowsHookEx(WH_KEYBOARD_LL, keyboardProc, module, 0);
            hookInstalled.set(keyboardHook != null);
            if (!hookInstalled.get()) { requested.set(false); return; }
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
            HHOOK key = keyboardHook;
            keyboardHook = null;
            if (key != null) User32.INSTANCE.UnhookWindowsHookEx(key);
            hookInstalled.set(false);
            requested.set(false);
            hookThreadId = 0;
            keyboardProc = null;
            hookThread = null;
        }
    }

    private static LPARAM asLParam(KBDLLHOOKSTRUCT info) {
        return info == null ? new LPARAM(0) : new LPARAM(Pointer.nativeValue(info.getPointer()));
    }
}
