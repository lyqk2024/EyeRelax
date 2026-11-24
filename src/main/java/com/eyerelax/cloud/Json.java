package com.eyerelax.cloud;
import java.util.*;

/** Small JSON codec for the shared Java 8 protocol; no platform-specific dependencies. */
public final class Json {
  private Json() {}
  public static Map<String, Object> map(Object... pairs) {
    Map<String, Object> value = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
    return value;
  }
  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    if (!(value instanceof Map))
      throw new IllegalArgumentException("Expected object");
    return (Map<String, Object>) value;
  }
  @SuppressWarnings("unchecked")
  public static List<Object> array(Object value) {
    if (!(value instanceof List))
      throw new IllegalArgumentException("Expected array");
    return (List<Object>) value;
  }
  public static long number(Map<String, Object> value, String key, long fallback) {
    Object n = value.get(key);
    return n instanceof Number ? ((Number) n).longValue() : fallback;
  }
  public static String text(Map<String, Object> value, String key, String fallback) {
    Object v = value.get(key);
    return v instanceof String ? (String) v : fallback;
  }
  public static boolean flag(Map<String, Object> value, String key, boolean fallback) {
    Object v = value.get(key);
    return v instanceof Boolean ? (Boolean) v : fallback;
  }
  public static String write(Object value) {
    if (value == null)
      return "null";
    if (value instanceof String) {
      StringBuilder s = new StringBuilder("\"");
      for (char c : ((String) value).toCharArray()) {
        switch (c) {
          case '"':
            s.append("\\\"");
            break;
          case '\\':
            s.append("\\\\");
            break;
          case '\n':
            s.append("\\n");
            break;
          case '\r':
            s.append("\\r");
            break;
          case '\t':
            s.append("\\t");
            break;
          default:
            if (c < 32)
              s.append(String.format("\\u%04x", (int) c));
            else
              s.append(c);
        }
      }
      return s.append('"').toString();
    }
    if (value instanceof Boolean || value instanceof Number)
      return value.toString();
    if (value instanceof Map) {
      StringJoiner j = new StringJoiner(",", "{", "}");
      Map<String, Object> m = object(value);
      for (String key : new TreeSet<>(m.keySet())) j.add(write(key) + ":" + write(m.get(key)));
      return j.toString();
    }
    if (value instanceof Iterable) {
      StringJoiner j = new StringJoiner(",", "[", "]");
      for (Object item : (Iterable<?>) value) j.add(write(item));
      return j.toString();
    }
    throw new IllegalArgumentException("Unsupported JSON type");
  }
  public static Object read(String text) {
    Parser p = new Parser(text);
    Object value = p.value(0);
    p.space();
    if (p.i != text.length())
      throw new IllegalArgumentException("Trailing JSON");
    return value;
  }
  private static final class Parser {
    final String s;
    int i;
    Parser(String s) {
      this.s = s;
    }
    void space() {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }
    char take() {
      if (i >= s.length())
        throw new IllegalArgumentException("Unexpected JSON end");
      return s.charAt(i++);
    }
    Object value(int depth) {
      if (depth > 64)
        throw new IllegalArgumentException("JSON nesting");
      space();
      char c = take();
      if (c == '"')
        return string();
      if (c == '{') {
        Map<String, Object> m = map();
        space();
        if (i < s.length() && s.charAt(i) == '}') {
          i++;
          return m;
        }
        while (true) {
          space();
          if (take() != '"')
            throw new IllegalArgumentException("Object key");
          String k = string();
          space();
          if (take() != ':')
            throw new IllegalArgumentException("Colon");
          if (m.containsKey(k))
            throw new IllegalArgumentException("Duplicate key");
          m.put(k, value(depth + 1));
          space();
          c = take();
          if (c == '}')
            return m;
          if (c != ',')
            throw new IllegalArgumentException("Object separator");
        }
      }
      if (c == '[') {
        List<Object> list = new ArrayList<>();
        space();
        if (i < s.length() && s.charAt(i) == ']') {
          i++;
          return list;
        }
        while (true) {
          list.add(value(depth + 1));
          space();
          c = take();
          if (c == ']')
            return list;
          if (c != ',')
            throw new IllegalArgumentException("Array separator");
        }
      }
      if (c == 't' && s.startsWith("rue", i)) {
        i += 3;
        return true;
      }
      if (c == 'f' && s.startsWith("alse", i)) {
        i += 4;
        return false;
      }
      if (c == 'n' && s.startsWith("ull", i)) {
        i += 3;
        return null;
      }
      int start = i - 1;
      while (i < s.length() && "0123456789eE+-.".indexOf(s.charAt(i)) >= 0) i++;
      String n = s.substring(start, i);
      if (!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
        throw new IllegalArgumentException("JSON number");
      if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0)
        return Double.parseDouble(n);
      return Long.parseLong(n);
    }
    String string() {
      StringBuilder out = new StringBuilder();
      while (true) {
        char c = take();
        if (c == '"')
          return out.toString();
        if (c < 32)
          throw new IllegalArgumentException("String control");
        if (c == '\\') {
          c = take();
          switch (c) {
            case '"':
            case '\\':
            case '/':
              out.append(c);
              break;
            case 'n':
              out.append('\n');
              break;
            case 'r':
              out.append('\r');
              break;
            case 't':
              out.append('\t');
              break;
            case 'b':
              out.append('\b');
              break;
            case 'f':
              out.append('\f');
              break;
            case 'u':
              if (i + 4 > s.length())
                throw new IllegalArgumentException("Unicode");
              out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
              i += 4;
              break;
            default:
              throw new IllegalArgumentException("Escape");
          }
        } else
          out.append(c);
      }
    }
  }
}
