package com.eye.win;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;

/** Direct Windows workstation locking; no rundll32 child process is spawned. */
public final class WinLock {
    private WinLock() {}

    public static boolean lockNow() {
        if (!Platform.isWindows()) return false;
        try {
            return User32.INSTANCE.LockWorkStation().booleanValue();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
