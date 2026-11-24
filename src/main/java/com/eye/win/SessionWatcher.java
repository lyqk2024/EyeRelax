package com.eye.win;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;

import java.util.function.IntConsumer;

public class SessionWatcher {

    // WTS 常量
    private static final int WM_WTSSESSION_CHANGE = 0x02B1;
    private static final int NOTIFY_FOR_THIS_SESSION = 0;

    // 事件：解锁 0x8，锁定 0x7（Win API 定义）
    public static final int WTS_SESSION_LOCK   = 0x7;
    public static final int WTS_SESSION_UNLOCK = 0x8;

    public static final int WTS_CONSOLE_CONNECT = 0x1, WTS_CONSOLE_DISCONNECT = 0x2;
    public static final int WTS_REMOTE_CONNECT = 0x3, WTS_REMOTE_DISCONNECT = 0x4;
    public static final int WTS_SESSION_DESKTOP_READY = 0xF, SYSTEM_SUSPEND = 0x10004;
    public static final int SYSTEM_RESUME = 0x10012;

    private HWND hWnd;
    private Thread thread;

    public void start(IntConsumer onSessionEvent) {
        thread = new Thread(() -> runMessageLoop(onSessionEvent), "SessionWatcher");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (hWnd != null) {
            User32.INSTANCE.PostMessage(hWnd, WinUser.WM_CLOSE, new WPARAM(0), new LPARAM(0));
        }
    }

    private void runMessageLoop(IntConsumer onSessionEvent) {
        final String windowClass = "EyeRelaxHiddenWindow";

        WNDCLASSEX wc = new WNDCLASSEX();
        wc.cbSize = wc.size();
        wc.style = 0;
        wc.hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
        wc.lpszClassName = windowClass;

        wc.lpfnWndProc = new WindowProc() {
            @Override
            public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                if (uMsg == 0x0218 && wParam.intValue() == 0x4) onSessionEvent.accept(SYSTEM_SUSPEND);
                if (uMsg == 0x0218 && (wParam.intValue() == 0x12 || wParam.intValue() == 0x7)) onSessionEvent.accept(SYSTEM_RESUME);
                if (uMsg == WM_WTSSESSION_CHANGE) {
                    int evt = wParam.intValue();
                    onSessionEvent.accept(evt);
                }
                return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
            }
        };

        User32.INSTANCE.RegisterClassEx(wc);

        hWnd = User32.INSTANCE.CreateWindowEx(
                0, windowClass, "EyeRelaxHidden",
                0, 0, 0, 0, 0,
                null, null, wc.hInstance, null
        );

        // 注册 session 通知
        Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hWnd, NOTIFY_FOR_THIS_SESSION);

        MSG msg = new MSG();
        while (User32.INSTANCE.GetMessage(msg, hWnd, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }

        // 退出时注销
        Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hWnd);
        User32.INSTANCE.DestroyWindow(hWnd);
        hWnd = null;
    }
}
