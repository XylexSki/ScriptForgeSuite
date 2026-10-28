package dev.scriptforge.macro;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File format (JSON Lines, UTF-8): line 1 is the header, every following line is one tick.
 * <pre>
 * {"sfmacro":2,"mc":"1.21.11","name":"farm","created":1730000000000,"dim":"minecraft:overworld","start":[x,y,z,yaw,pitch],"ticks":1200}
 * {"p":[x,y,z,yaw,pitch],"g":1,"c":0,"s":0,"r":0,"h":0,"k":5,"e":[{"k":"dig","a":"start","x":1,"y":64,"z":2,"f":1}]}
 * </pre>
 * p = position/rotation, g = on ground, c = horizontal collision, s = sneaking, r = sprinting, h = hotbar slot,
 * k = held-key bitmask (see Macro.KEYS), e = events (omitted when empty).
 */
public final class MacroIO {
    private MacroIO() {}

    private static final Gson GSON = new Gson();

    public static void write(Path file, Macro m) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            JsonObject h = new JsonObject();
            h.addProperty("sfmacro", Macro.VERSION);
            h.addProperty("mc", m.mc);
            h.addProperty("name", m.name);
            h.addProperty("created", m.created);
            h.addProperty("dim", m.dimension);
            h.add("start", arr(m.startX, m.startY, m.startZ, m.startYaw, m.startPitch));
            h.addProperty("ticks", m.frames.size());
            w.write(GSON.toJson(h));
            w.newLine();
            for (Frame f : m.frames) {
                JsonObject o = new JsonObject();
                o.add("p", arr(f.x, f.y, f.z, f.yaw, f.pitch));
                o.addProperty("g", f.onGround ? 1 : 0);
                o.addProperty("c", f.collided ? 1 : 0);
                o.addProperty("s", f.sneaking ? 1 : 0);
                o.addProperty("r", f.sprinting ? 1 : 0);
                o.addProperty("h", f.slot);
                o.addProperty("k", f.keys);
                if (!f.events.isEmpty()) {
                    JsonArray ev = new JsonArray();
                    for (Event e : f.events) ev.add(e.toJson());
                    o.add("e", ev);
                }
                w.write(GSON.toJson(o));
                w.newLine();
            }
        }
    }

    public static Macro read(Path file) throws IOException {
        Macro m = new Macro();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = r.readLine();
            if (line == null) throw new IOException("Empty macro file: " + file);
            JsonObject h = JsonParser.parseString(line).getAsJsonObject();
            if (!h.has("sfmacro")) throw new IOException("Not a ScriptForge macro (missing header): " + file);
            int version = h.get("sfmacro").getAsInt();
            if (version != Macro.VERSION) {
                throw new IOException("Unsupported macro version " + version + " (expected " + Macro.VERSION + ")");
            }
            m.mc = str(h, "mc");
            m.name = str(h, "name");
            m.created = h.has("created") ? h.get("created").getAsLong() : 0L;
            m.dimension = str(h, "dim");
            JsonArray s = h.getAsJsonArray("start");
            m.startX = s.get(0).getAsDouble();
            m.startY = s.get(1).getAsDouble();
            m.startZ = s.get(2).getAsDouble();
            m.startYaw = s.get(3).getAsFloat();
            m.startPitch = s.get(4).getAsFloat();

            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                Frame f = new Frame();
                JsonArray p = o.getAsJsonArray("p");
                f.x = p.get(0).getAsDouble();
                f.y = p.get(1).getAsDouble();
                f.z = p.get(2).getAsDouble();
                f.yaw = p.get(3).getAsFloat();
                f.pitch = p.get(4).getAsFloat();
                f.onGround = flag(o, "g");
                f.collided = flag(o, "c");
                f.sneaking = flag(o, "s");
                f.sprinting = flag(o, "r");
                f.slot = o.has("h") ? o.get("h").getAsInt() : 0;
                f.keys = o.has("k") ? o.get("k").getAsInt() : 0;
                if (o.has("e")) {
                    for (JsonElement el : o.getAsJsonArray("e")) f.events.add(Event.fromJson(el.getAsJsonObject()));
                }
                m.frames.add(f);
            }
        }
        return m;
    }

    private static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) a.add(d);
        return a;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : "";
    }

    private static boolean flag(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsInt() != 0;
    }
}
