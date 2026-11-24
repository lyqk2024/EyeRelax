package com.eye.win;

import com.eye.core.ComputerActivity;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import java.nio.file.Path;

/** Read-only foreground and idle sampling. Never records titles, URLs or key contents. */
public final class ForegroundActivity {
    private final char[] executableBuffer=new char[32768];
    public ComputerActivity.Sample sample() {
        if (!Platform.isWindows()) return new ComputerActivity.Sample(false, false, "");
        boolean available = DesktopAvailability.isAvailable();
        if (!available) return new ComputerActivity.Sample(false, false, "");
        WinUser.LASTINPUTINFO input = new WinUser.LASTINPUTINFO();
        boolean active = User32.INSTANCE.GetLastInputInfo(input)
                && Integer.toUnsignedLong(Kernel32.INSTANCE.GetTickCount() - input.dwTime) < 300_000;
        String executable = "";
        WinDef.HWND window = User32.INSTANCE.GetForegroundWindow();
        if (window != null) {
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, pid);
            WinNT.HANDLE process = Kernel32.INSTANCE.OpenProcess(0x1000, false, pid.getValue());
            if (process != null) {
                try {
                    char[] name = executableBuffer; IntByReference length = new IntByReference(name.length);
                    if (Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, name, length))
                        executable = Path.of(new String(name, 0, length.getValue())).toAbsolutePath().normalize().toString();
                } finally { Kernel32.INSTANCE.CloseHandle(process); }
            }
        }
        return new ComputerActivity.Sample(true, active, executable);
    }
}
