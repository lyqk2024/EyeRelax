package com.eye.sync;
import com.eye.core.*;
import com.eyerelax.cloud.*;
import com.sun.jna.platform.win32.Crypt32Util;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** DPAPI protects the session token for the current Windows user. Never part of data exports. */
public final class CloudState implements CloudClient.Store {
  private static Path path() {
    return DataPaths.bootstrapRoot().resolve("cloud-account.json");
  }
  public Map<String, Object> load() {
    synchronized (CloudState.class) {
      if (!Files.exists(path()))
        return Json.map();
      try {
        Map<String, Object> state = Json.object(Json.read(Files.readString(path())));
        String secret = Json.text(state, "protectedToken", "");
        state.remove("protectedToken");
        if (!secret.isEmpty())
          state.put("token",
              new String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(secret)),
                  StandardCharsets.UTF_8));
        return state;
      } catch (Exception e) {
        return Json.map("locked", true, "stateError", "账户状态无法读取，保留严格限制。");
      }
    }
  }
  public void save(Map<String, Object> input) throws IOException {
    synchronized (CloudState.class) {
      Map<String, Object> state = Json.object(Json.read(Json.write(input)));
      String token = Json.text(state, "token", "");
      state.remove("token");
      if (!token.isEmpty())
        state.put("protectedToken",
            Base64.getEncoder().encodeToString(
                Crypt32Util.cryptProtectData(token.getBytes(StandardCharsets.UTF_8))));
      Files.createDirectories(path().getParent());
      Path next = path().resolveSibling("cloud-account.new");
      Files.writeString(next, Json.write(state));
      try {
        Files.move(
            next, path(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(next, path(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }
  public static boolean locked() {
    return Json.flag(new CloudState().load(), "locked", false);
  }
}
