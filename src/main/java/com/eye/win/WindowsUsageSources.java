package com.eye.win;

import com.eye.core.ComputerActivity.Range;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Optional read-only sources; does not change audit policy or request elevation. */
public final class WindowsUsageSources {
    public record History(List<Range> ranges,String message) {}
    private static String run(String script) throws Exception {
        Path output=Files.createTempFile("EyeRelax-source-",".txt");
        try {
            String wrapped="$ErrorActionPreference='Stop';try { $rows=@(& {"+script+"});"
                + "[IO.File]::WriteAllLines($env:EYERELAX_SOURCE_OUTPUT,[string[]]$rows,(New-Object Text.UTF8Encoding($false))) }"
                + "catch {[IO.File]::WriteAllText($env:EYERELAX_SOURCE_OUTPUT,'ERROR|'+$_.Exception.Message,(New-Object Text.UTF8Encoding($false)));exit 2}";
            ProcessBuilder pb=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",Base64.getEncoder().encodeToString(wrapped.getBytes(StandardCharsets.UTF_16LE)));
            pb.environment().put("EYERELAX_SOURCE_OUTPUT",output.toString());
            Process p=pb.start();
            if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("读取 Windows 记录超时，请稍后重试。");}
            String result=Files.readString(output);
            if(p.exitValue()!=0||result.startsWith("ERROR|"))throw new IllegalStateException(result.replaceFirst("^ERROR\\|",""));
            return result;
        }finally {Files.deleteIfExists(output);}
    }
    public static History history() throws Exception {
        String script=" $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;"
            + "$start=(Get-Date).AddDays(-30);"
            + "try {$events=Get-WinEvent -FilterHashtable @{LogName='Security';Id=4800,4801,4634,4647;StartTime=$start} -MaxEvents 20000 -ErrorAction Stop}"
            + "catch {if($_.FullyQualifiedErrorId -like 'NoMatchingEventsFound*'){'NONE';return}else{throw}};"
            + "foreach($e in $events){[xml]$x=$e.ToXml();$m=@{};foreach($d in $x.Event.EventData.Data){$m[$d.Name]=[string]$d.'#text'};"
            + "if($m['TargetUserSid'] -eq $sid){$kind=if($e.Id -eq 4801){'open'}else{'close'};"
            + "$ms=([DateTimeOffset]$e.TimeCreated).ToUnixTimeMilliseconds();\"$ms|$kind|$($m['TargetLogonId'])\"}};"
            + "$power=Get-WinEvent -FilterHashtable @{LogName='System';Id=42,6006,12;StartTime=$start} -MaxEvents 20000 -ErrorAction SilentlyContinue;"
            + "foreach($e in $power){if(($e.Id -eq 42 -and $e.ProviderName -eq 'Microsoft-Windows-Kernel-Power') -or ($e.Id -eq 12 -and $e.ProviderName -eq 'Microsoft-Windows-Kernel-General') -or ($e.Id -eq 6006 -and $e.ProviderName -eq 'EventLog')){\"$(([DateTimeOffset]$e.TimeCreated).ToUnixTimeMilliseconds())|stop|all\"}}";
        String records=run(script);
        if(records.trim().equals("NONE"))return new History(List.of(),com.eye.i18n.I18n.text("没有可用于估算的 Windows 使用记录。"));
        List<Range> ranges=parseHistory(records);
        return new History(ranges,ranges.isEmpty()?com.eye.i18n.I18n.text("没有完整的解锁和锁屏记录，无法估算时长。"):com.eye.i18n.I18n.text("已导入最近 30 天的系统使用记录。"));
    }
    public static List<Range> parseHistory(String records) {
        record Event(long time,String kind,String session) {}
        List<Event> events=new ArrayList<>();
        for(String line:records.split("\\R")){String[] p=line.split("\\|",3);if(p.length==3)try{events.add(new Event(Long.parseLong(p[0]),p[1],p[2]));}catch(NumberFormatException ignored){}}
        events.sort(Comparator.comparingLong(Event::time));Map<String,Long> starts=new HashMap<>();List<Range> ranges=new ArrayList<>();
        for(Event e:events){
            if(e.kind.equals("open"))starts.putIfAbsent(e.session,e.time);
            else if(e.kind.equals("close")){Long a=starts.remove(e.session);if(a!=null&&e.time>a)ranges.add(new Range(a,e.time));}
            else if(e.kind.equals("stop")){for(long a:starts.values())if(e.time>a)ranges.add(new Range(a,e.time));starts.clear();}
        }
        return ranges;
    }
    public static Map<Path,String> commonApplications() throws Exception {
        return WindowsShell.commonApplications();
    }
}
