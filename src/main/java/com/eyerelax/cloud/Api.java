package com.eyerelax.cloud;
import static com.eyerelax.cloud.Json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Api {
  public static final class Failure extends IOException {
    public final int status;
    public final Map<String, Object> response;
    Failure(int status, Map<String, Object> response) {
      super(text(response, "error", "HTTP " + status));
      this.status = status;
      this.response = response;
    }
  }
  private final String base, token;
  public Api(String base, String token) {
    this.base = address(base);
    this.token = token;
  }
  public static String address(String value) {
    try {
      URI uri = new URI(value.trim());
      String h = uri.getHost();
      if (h == null || uri.getUserInfo() != null || uri.getQuery() != null
          || uri.getFragment() != null
          || (!"https".equals(uri.getScheme())
              && !("http".equals(uri.getScheme())
                  && (h.equals("127.0.0.1") || h.equals("localhost") || h.equals("[::1]")))))
        throw new IllegalArgumentException(
            "服务地址须为 HTTPS；本机测试可用 http://127.0.0.1:8765");
      return value.trim().replaceAll("/+$", "");
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("服务地址无效");
    }
  }
  public Map<String, Object> call(String method, String path, Map<String, Object> body)
      throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
    c.setConnectTimeout(10000);
    c.setReadTimeout(15000);
    c.setInstanceFollowRedirects(false);
    c.setRequestMethod(method);
    c.setRequestProperty("Accept", "application/json");
    if (token != null && !token.isEmpty())
      c.setRequestProperty("Authorization", "Bearer " + token);
    try {
      if (body != null) {
        byte[] data = write(body).getBytes(StandardCharsets.UTF_8);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = c.getOutputStream()) {
          out.write(data);
        }
      }
      int status = c.getResponseCode();
      InputStream stream = status < 400 ? c.getInputStream() : c.getErrorStream();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (stream != null)
        try (InputStream in = stream) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) {
            out.write(b, 0, n);
            if (out.size() > 10 * 1024 * 1024)
              throw new IOException("服务响应过大");
          }
        }
      Map<String, Object> result =
          object(read(new String(out.toByteArray(), StandardCharsets.UTF_8)));
      if (status < 200 || status >= 300)
        throw new Failure(status, result);
      return result;
    } catch (IllegalArgumentException invalid) {
      throw new IOException("服务响应格式错误", invalid);
    } finally {
      c.disconnect();
    }
  }
}
