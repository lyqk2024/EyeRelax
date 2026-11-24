package com.eye.win;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/** Read-only input desktop probe after resume; never switches Windows desktops. */
public final class DesktopAvailability {
    private interface DesktopApi extends StdCallLibrary {
        Pointer OpenInputDesktop(int flags, boolean inherit, int access);
        boolean GetUserObjectInformationW(Pointer handle, int index, Pointer value, int bytes, IntByReference needed);
        boolean CloseDesktop(Pointer handle);
    }
    private DesktopAvailability() {}
    public static boolean isAvailable() {
        if(!Platform.isWindows())return true;
        DesktopApi api=Native.load("user32",DesktopApi.class);
        Pointer desktop=api.OpenInputDesktop(0,false,1);
        if(desktop==null)return false;
        try {
            Memory name=new Memory(512);
            return api.GetUserObjectInformationW(desktop,2,name,(int)name.size(),new IntByReference())
                    && "Default".equalsIgnoreCase(name.getWideString(0));
        } finally {api.CloseDesktop(desktop);}
    }
}
