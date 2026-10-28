package dev.scriptforge.core;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.List;
import java.util.Map;

/** Helpers for building and reading JS values. Only call from a script thread. */
public final class JsUtil {
    private JsUtil() {}

    private static Object norm(Object v) {
        if (v instanceof Float f) return (double) f;
        if (v instanceof Long l) return (double) l;
        if (v instanceof Character c) return String.valueOf(c);
        return v;
    }

    /** obj("a", 1, "b", "x") -> {a: 1, b: "x"} */
    public static Scriptable obj(Object... keyValues) {
        ScriptContext sc = ScriptContext.current();
        Scriptable o = Context.getCurrentContext().newObject(sc.scope);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            ScriptableObject.putProperty(o, (String) keyValues[i], norm(keyValues[i + 1]));
        }
        return o;
    }

    public static Scriptable fromMap(Map<String, Object> map) {
        ScriptContext sc = ScriptContext.current();
        Scriptable o = Context.getCurrentContext().newObject(sc.scope);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            ScriptableObject.putProperty(o, e.getKey(), norm(e.getValue()));
        }
        return o;
    }

    public static Scriptable arr(List<?> items) {
        ScriptContext sc = ScriptContext.current();
        Object[] copy = new Object[items.size()];
        for (int i = 0; i < copy.length; i++) copy[i] = norm(items.get(i));
        return Context.getCurrentContext().newArray(sc.scope, copy);
    }

    private static Object raw(Scriptable o, String key) {
        if (o == null) return null;
        Object v = ScriptableObject.getProperty(o, key);
        return v == Scriptable.NOT_FOUND || v == Undefined.instance ? null : v;
    }

    public static double num(Scriptable o, String key, double def) {
        Object v = raw(o, key);
        return v == null ? def : Context.toNumber(v);
    }

    public static boolean bool(Scriptable o, String key, boolean def) {
        Object v = raw(o, key);
        return v == null ? def : Context.toBoolean(v);
    }

    public static String str(Scriptable o, String key, String def) {
        Object v = raw(o, key);
        return v == null ? def : Context.toString(v);
    }

    /** Accepts 0xRRGGBB / 0xAARRGGBB numbers or "#RRGGBB" / "#AARRGGBB" strings. Returns ARGB. */
    public static int color(Object value, int def) {
        if (value == null || value == Undefined.instance) return def;
        int argb;
        if (value instanceof Number n) {
            argb = (int) n.longValue();
        } else {
            String s = Context.toString(value).trim();
            if (s.startsWith("#")) s = s.substring(1);
            try {
                argb = (int) Long.parseLong(s, 16);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return (argb >>> 24) == 0 ? argb | 0xFF000000 : argb;
    }
}
