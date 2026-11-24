package com.eye.app;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Version;
import com.sun.jna.ptr.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** HTTPS manifest/download client. Downloading never executes or silently installs an update. */
public final class UpdateClient {
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"EyeRelax-update-timeout");t.setDaemon(true);return t;});
    private static InputStream bounded(InputStream input,int seconds){
        var alarm=DEADLINES.schedule(()->{try{input.close();}catch(IOException ignored){}},seconds,TimeUnit.SECONDS);
        return new FilterInputStream(input){public void close()throws IOException{alarm.cancel(false);super.close();}};
    }
    public record Release(String version,URI installer,String notes) {}
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NEVER).build();
    public static URI source(String url){
        URI uri=URI.create(url.trim());String scheme=uri.getScheme(),host=uri.getHost();
        if(host==null||uri.getUserInfo()!=null||!("https".equalsIgnoreCase(scheme)||("http".equalsIgnoreCase(scheme)&&Set.of("localhost","127.0.0.1","[::1]").contains(host))))throw new IllegalArgumentException("更新源必须为 HTTPS 地址（本机测试可用 localhost HTTP）。");
        return uri;
    }
    private HttpResponse<InputStream> get(URI uri)throws Exception {
        for(int i=0;i<6;i++){
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent","EyeRelax/"+AppVersion.CURRENT).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            if(response.statusCode()>=300&&response.statusCode()<400){response.body().close();uri=source(uri.resolve(response.headers().firstValue("Location").orElseThrow()).toString());continue;}
            if(response.statusCode()!=200){response.body().close();throw new IOException("更新服务器返回 HTTP "+response.statusCode());}
            return response;
        }
        throw new IOException("更新地址重定向次数过多。");
    }
    public Release check(String address)throws Exception {
        if(address==null||address.isBlank())throw new IllegalStateException("尚未配置更新清单地址。");
        Properties p=new Properties();try(var in=bounded(get(source(address)).body(),20)){byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IOException("更新清单过大。");p.load(new StringReader(new String(bytes,StandardCharsets.UTF_8)));}
        String version=p.getProperty("version","").trim();AppVersion.compare(version,AppVersion.CURRENT);
        URI download=source(p.getProperty("url","").trim());String notes=p.getProperty("notes","");
        return new Release(version,download,notes.length()>4000?notes.substring(0,4000):notes);
    }
    public Path download(Release release,Path directory)throws Exception {
        Files.createDirectories(directory);
        Path target=directory.resolve("EyeRelax-Setup-"+release.version.replaceFirst("^[vV]","")+".exe");
        if(Files.isRegularFile(target))try{verifyInstaller(target,release.version);return target;}catch(Exception ignored){}
        Path temp=Files.createTempFile(directory,"EyeRelax-update-",".part");boolean complete=false;
        try {
            try(var input=bounded(get(release.installer).body(),300);var output=Files.newOutputStream(temp)){
                byte[] buffer=new byte[65536];long total=0;int n;long deadline=System.nanoTime()+300_000_000_000L;
                while((n=input.read(buffer))>=0){total+=n;if(total>250L*1024*1024)throw new IOException("更新包超过允许大小。");if(System.nanoTime()>deadline)throw new IOException("下载超时，请重试。");output.write(buffer,0,n);}
            }
            verifyInstaller(temp,release.version);
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);complete=true;return target;
        }finally {if(!complete)Files.deleteIfExists(temp);}
    }
    public static void verifyInstaller(Path path,String expected)throws Exception {
        try(var input=Files.newInputStream(path)){if(input.read()!='M'||input.read()!='Z')throw new IOException("下载内容不是 Windows 安装程序。");}
        IntByReference unused=new IntByReference();int length=Version.INSTANCE.GetFileVersionInfoSize(path.toAbsolutePath().toString(),unused);
        if(length<=0)throw new IOException("安装包缺少版本信息。");Memory data=new Memory(length);
        if(!Version.INSTANCE.GetFileVersionInfo(path.toAbsolutePath().toString(),0,length,data))throw new IOException("无法读取安装包版本。");
        PointerByReference languages=new PointerByReference();IntByReference size=new IntByReference();
        if(!Version.INSTANCE.VerQueryValue(data,"\\VarFileInfo\\Translation",languages,size)||size.getValue()<4)throw new IOException("安装包缺少产品信息。");
        String base=String.format("\\StringFileInfo\\%04x%04x\\",languages.getValue().getShort(0)&0xffff,languages.getValue().getShort(2)&0xffff);
        PointerByReference value=new PointerByReference();
        if(!Version.INSTANCE.VerQueryValue(data,base+"ProductName",value,size)||!"EyeRelax".equals(value.getValue().getWideString(0)))throw new IOException("更新包不属于 EyeRelax。");
        if(!Version.INSTANCE.VerQueryValue(data,base+"ProductVersion",value,size)||AppVersion.compare(value.getValue().getWideString(0),expected)!=0)throw new IOException("安装包版本与清单不一致。");
    }
}
