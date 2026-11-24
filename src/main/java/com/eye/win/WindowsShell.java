package com.eye.win;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.Unknown;
import java.awt.Component;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;

/** In-process Windows Shell integration. Does not launch executables or scripts. */
public final class WindowsShell {
    private WindowsShell() {}
    private static final class Com extends Unknown implements AutoCloseable {
        Com(Pointer p) { super(p); }
        int call(int slot,Object... args) {
            Object[] all=new Object[args.length+1];all[0]=getPointer();System.arraycopy(args,0,all,1,args.length);
            return _invokeNativeInt(slot,all);
        }
        public void close(){Release();}
    }
    private static void check(int hr){if(hr<0)throw new IllegalStateException("Windows Shell 错误：0x"+Integer.toHexString(hr));}
    private static Com create(String clsid,String iid){PointerByReference p=new PointerByReference();check(Ole32.INSTANCE.CoCreateInstance(new Guid.GUID(clsid),null,1,new Guid.GUID(iid),p).intValue());return new Com(p.getValue());}
    private static Com query(Com source,String iid){PointerByReference p=new PointerByReference();check(source.QueryInterface(new Guid.REFIID(new Guid.IID(iid)),p).intValue());return new Com(p.getValue());}
    private static void initialize(){check(Ole32.INSTANCE.CoInitializeEx(null,2).intValue());}
    @Structure.FieldOrder({"name","pattern"})
    public static class Filter extends Structure {public WString name;public WString pattern;}
    public static Path choose(Component parent,String title,boolean directory){
        initialize();
        try(Com dialog=create("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}","{D57C7288-D4AD-4768-BE02-9D969532D960}")){
            check(dialog.call(17,new WString(title)));
            check(dialog.call(9,0x800|0x1000|0x40|(directory?0x20:0)));
            Filter filter=null;
            if(!directory){filter=new Filter();filter.name=new WString("应用程序 (*.exe)");filter.pattern=new WString("*.exe");filter.write();check(dialog.call(4,1,filter.getPointer()));}
            java.awt.Window window=javax.swing.SwingUtilities.getWindowAncestor(parent);
            Pointer owner=window!=null&&window.isDisplayable()?Native.getComponentPointer(window):null;
            int hr=dialog.call(3,owner);if(hr==0x800704c7)return null;check(hr);
            PointerByReference item=new PointerByReference();check(dialog.call(20,item));
            try(Com selected=new Com(item.getValue())){PointerByReference text=new PointerByReference();check(selected.call(5,0x80058000,text));try{return Path.of(text.getValue().getWideString(0));}finally{Ole32.INSTANCE.CoTaskMemFree(text.getValue());}}
        }finally{Ole32.INSTANCE.CoUninitialize();}
    }
    public static Map<Path,String> commonApplications() throws IOException {
        initialize();
        try {
            Map<Path,String> found=new TreeMap<>();
            for(int folder:new int[]{0x2,0x17}){
                char[] buffer=new char[260];check(Shell32.INSTANCE.SHGetFolderPath(null,folder,null,new WinDef.DWORD(0),buffer).intValue());
                Path root=Path.of(Native.toString(buffer));if(!Files.isDirectory(root))continue;
                Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
                    public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){
                        String name=file.getFileName().toString();
                        if(name.toLowerCase(Locale.ROOT).endsWith(".lnk")&&!name.toLowerCase(Locale.ROOT).contains("uninstall")&&!name.contains("卸载")){
                            try {Path target=shortcutTarget(file);if(target!=null&&Files.isRegularFile(target)&&target.toString().toLowerCase(Locale.ROOT).endsWith(".exe"))found.putIfAbsent(target.toAbsolutePath().normalize(),name.substring(0,name.length()-4));}catch(RuntimeException ignored){/* A broken shortcut must not abort the catalog. */}
                        }return FileVisitResult.CONTINUE;
                    }
                    public FileVisitResult visitFileFailed(Path file,IOException error){return FileVisitResult.CONTINUE;}
                });
            }
            // Store/MSIX applications need not have a filesystem Start-menu shortcut.
            for(String executable:RunningApplications.sample()){
                Path path=Path.of(executable);
                if(executable.toLowerCase(Locale.ROOT).contains("\\windowsapps\\")&&Files.isRegularFile(path)){
                    String name=path.getFileName().toString();if(name.toLowerCase(Locale.ROOT).endsWith(".exe"))found.putIfAbsent(path,name.substring(0,name.length()-4));
                }
            }
            return found;
        }finally{Ole32.INSTANCE.CoUninitialize();}
    }
    public record Shortcut(Path target,String arguments) {}
    public static Shortcut readShortcut(Path file){
        initialize();
        try{return shortcut(file);}finally{Ole32.INSTANCE.CoUninitialize();}
    }
    private static Path shortcutTarget(Path file){return shortcut(file).target();}
    private static Shortcut shortcut(Path file){
        try(Com link=create("{00021401-0000-0000-C000-000000000046}","{000214F9-0000-0000-C000-000000000046}");Com persist=query(link,"{0000010b-0000-0000-C000-000000000046}")){
            check(persist.call(5,new WString(file.toString()),0));
            Memory buffer=new Memory(32768L*2);buffer.clear();check(link.call(3,buffer,32768,null,4));
            String path=buffer.getWideString(0);buffer.clear();check(link.call(10,buffer,32768));
            return new Shortcut(path.isBlank()?null:Path.of(path),buffer.getWideString(0));
        }
    }
}
