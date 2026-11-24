package com.eye.win;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;

/** Stable Shell identity, set before creating any windows. */
public final class WindowsAppIdentity {
    public static final String APP_ID="EyeRelax.Desktop";
    private interface Shell extends StdCallLibrary {
        int SetCurrentProcessExplicitAppUserModelID(WString id);
    }
    private WindowsAppIdentity() {}
    public static void initialize(){
        if(!Platform.isWindows())return;
        int result=Native.load("shell32",Shell.class).SetCurrentProcessExplicitAppUserModelID(new WString(APP_ID));
        if(result<0)System.err.println("EyeRelax taskbar identity: 0x"+Integer.toHexString(result));
    }
}
