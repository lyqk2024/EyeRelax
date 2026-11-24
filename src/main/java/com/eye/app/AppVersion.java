package com.eye.app;

import java.math.BigInteger;
import java.util.*;

public final class AppVersion {
    public static final String CURRENT;
    static {Properties p=new Properties();try(var in=AppVersion.class.getResourceAsStream("/app-version.properties")){if(in!=null)p.load(in);}catch(Exception ignored){}CURRENT=p.getProperty("version","1.0.0");}
    public static String updateChannel() {
        return parts(CURRENT)[1].isEmpty() ? "stable" : "experimental";
    }
    /** SemVer ordering: release > prerelease; preview.10 > preview.9; build labels do not affect order. */
    public static int compare(String a,String b){
        String[] left=parts(a),right=parts(b);
        String[] x=left[0].split("\\."),y=right[0].split("\\.");
        for(int i=0;i<3;i++){int c=new BigInteger(x[i]).compareTo(new BigInteger(y[i]));if(c!=0)return Integer.signum(c);}
        if(left[1].isEmpty()||right[1].isEmpty())return left[1].isEmpty()?(right[1].isEmpty()?0:1):-1;
        x=left[1].split("\\.");y=right[1].split("\\.");
        for(int i=0;i<Math.min(x.length,y.length);i++){boolean nx=x[i].matches("[0-9]+"),ny=y[i].matches("[0-9]+");int c=nx&&ny?new BigInteger(x[i]).compareTo(new BigInteger(y[i])):nx!=ny?(nx?-1:1):x[i].compareTo(y[i]);if(c!=0)return Integer.signum(c);}
        return Integer.compare(x.length,y.length);
    }
    private static String[] parts(String text){
        String value=text==null?"":text.trim().replaceFirst("^[vV]","");
        if(!value.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"))throw new IllegalArgumentException("无法识别版本号："+text);
        value=value.split("\\+",2)[0];String[] p=value.split("-",2);return new String[]{p[0],p.length==2?p[1]:""};
    }
}
