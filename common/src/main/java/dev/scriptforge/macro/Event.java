package dev.scriptforge.macro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Something the player did during a tick, in protocol-neutral terms.
 * Faces are 0..5 = down, up, north, south, west, east. Hands are 0 = main, 1 = off hand.
 */
public final class Event {
    public static final String DIG = "dig";           // a = "start" | "finish"; x,y,z,f
    public static final String USE_BLOCK = "useBlock"; // hand; x,y,z,f; cx,cy,cz (cursor inside block); inside
    public static final String USE_ITEM = "useItem";   // hand
    public static final String SWING = "swing";        // hand
    public static final String CHAT = "chat";          // m
    public static final String CMD = "cmd";            // m (without the leading slash)

    public String k;
    public String a;
    public int hand;
    public int x, y, z, f;
    public float cx, cy, cz;
    public boolean inside;
    public String m;

    public static Event dig(String action, int x, int y, int z, int face) {
        Event e = new Event();
        e.k = DIG; e.a = action; e.x = x; e.y = y; e.z = z; e.f = face;
        return e;
    }

    public static Event useBlock(int hand, int x, int y, int z, int face, float cx, float cy, float cz, boolean inside) {
        Event e = new Event();
        e.k = USE_BLOCK; e.hand = hand; e.x = x; e.y = y; e.z = z; e.f = face;
        e.cx = cx; e.cy = cy; e.cz = cz; e.inside = inside;
        return e;
    }

    public static Event useItem(int hand) {
        Event e = new Event();
        e.k = USE_ITEM; e.hand = hand;
        return e;
    }

    public static Event swing(int hand) {
        Event e = new Event();
        e.k = SWING; e.hand = hand;
        return e;
    }

    public static Event chat(String text) {
        Event e = new Event();
        e.k = CHAT; e.m = text;
        return e;
    }

    public static Event cmd(String commandWithoutSlash) {
        Event e = new Event();
        e.k = CMD; e.m = commandWithoutSlash;
        return e;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("k", k);
        switch (k) {
            case DIG -> {
                o.addProperty("a", a);
                block(o);
            }
            case USE_BLOCK -> {
                o.addProperty("h", hand);
                block(o);
                JsonArray c = new JsonArray();
                c.add(cx); c.add(cy); c.add(cz);
                o.add("c", c);
                o.addProperty("i", inside ? 1 : 0);
            }
            case USE_ITEM, SWING -> o.addProperty("h", hand);
            case CHAT, CMD -> o.addProperty("m", m);
            default -> { }
        }
        return o;
    }

    private void block(JsonObject o) {
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("f", f);
    }

    static Event fromJson(JsonObject o) {
        Event e = new Event();
        e.k = o.get("k").getAsString();
        if (o.has("a")) e.a = o.get("a").getAsString();
        if (o.has("h")) e.hand = o.get("h").getAsInt();
        if (o.has("x")) e.x = o.get("x").getAsInt();
        if (o.has("y")) e.y = o.get("y").getAsInt();
        if (o.has("z")) e.z = o.get("z").getAsInt();
        if (o.has("f")) e.f = o.get("f").getAsInt();
        if (o.has("c")) {
            JsonArray c = o.getAsJsonArray("c");
            e.cx = c.get(0).getAsFloat();
            e.cy = c.get(1).getAsFloat();
            e.cz = c.get(2).getAsFloat();
        }
        if (o.has("i")) e.inside = o.get("i").getAsInt() != 0;
        if (o.has("m")) e.m = o.get("m").getAsString();
        return e;
    }
}
