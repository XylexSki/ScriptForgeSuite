package dev.scriptforge.core;

import dev.scriptforge.ScriptForge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Finds, starts, stops and ticks scripts. */
public final class ScriptManager {
    private static final String[] EXAMPLES = {
            "hello.js", "chat_recorder.js", "hud_clock.js", "walk_square.js", "gui_panel.js", "auto_reply.js", "macro_recorder.js"};
    private static final String PREFIX = "\u00a77[ScriptForge] \u00a7f";

    public final Path root;
    public final Path scriptsDir;
    public final Path dataDir;
    public final Path macrosDir;
    public final Path recordingsDir;

    private final Map<String, ScriptContext> running = new ConcurrentHashMap<>();
    private final Map<String, Boolean> hotkeyDown = new HashMap<>();
    private List<ScriptInfo> hotkeyCache = List.of();
    private boolean wasInWorld;
    private int worldTicks;
    private int tickCount;

    public ScriptManager(Path root) {
        this.root = root;
        this.scriptsDir = root.resolve("scripts");
        this.dataDir = root.resolve("data");
        this.macrosDir = root.resolve("macros");
        this.recordingsDir = root.resolve("recordings");
    }

    public void init() {
        try {
            for (Path p : List.of(scriptsDir, dataDir, macrosDir, recordingsDir)) Files.createDirectories(p);
            boolean empty;
            try (Stream<Path> s = Files.list(scriptsDir)) {
                empty = s.noneMatch(p -> p.toString().endsWith(".js"));
            }
            if (empty) {
                for (String ex : EXAMPLES) {
                    try (InputStream in = ScriptManager.class.getResourceAsStream("/assets/scriptforge/examples/" + ex)) {
                        if (in != null) Files.copy(in, scriptsDir.resolve(ex), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            ScriptForge.LOG.error("Could not prepare the scriptforge folder", e);
        }
    }

    // ------------------------------------------------------------------ lookup

    public List<ScriptInfo> list() {
        try (Stream<Path> s = Files.list(scriptsDir)) {
            return s.filter(p -> p.toString().endsWith(".js")).sorted().map(ScriptInfo::parse).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String strip(String name) {
        return name.endsWith(".js") ? name.substring(0, name.length() - 3) : name;
    }

    public ScriptInfo find(String name) {
        String n = strip(name.trim());
        for (ScriptInfo i : list()) if (i.name().equalsIgnoreCase(n)) return i;
        return null;
    }

    private ScriptContext findRunning(String name) {
        String n = strip(name.trim());
        for (Map.Entry<String, ScriptContext> e : running.entrySet()) {
            if (e.getKey().equalsIgnoreCase(n) && !e.getValue().stopped) return e.getValue();
        }
        return null;
    }

    public boolean isRunning(String name) {
        return findRunning(name) != null;
    }

    public Collection<String> runningNames() {
        List<String> out = new ArrayList<>();
        for (ScriptContext c : running.values()) if (!c.stopped) out.add(c.name);
        return out;
    }

    // ------------------------------------------------------------------ start / stop

    /** Starts a script file. Returns an error message, or null on success. */
    public String run(String name) {
        ScriptInfo info = find(name);
        if (info == null) return "No script named '" + name + "' in " + scriptsDir;
        if (isRunning(info.name())) return "'" + info.name() + "' is already running";
        ScriptContext ctx = new ScriptContext(this, info.name(), info.path(), null);
        running.put(info.name(), ctx);
        Mc.chat(PREFIX + "Started \u00a7b" + info.name());
        ctx.start();
        return null;
    }

    /** Runs a snippet of JS source under the given label (used by /script play). */
    public String runInline(String label, String source) {
        if (isRunning(label)) return "'" + label + "' is already running";
        ScriptContext ctx = new ScriptContext(this, label, null, source);
        running.put(label, ctx);
        Mc.chat(PREFIX + "Started \u00a7b" + label);
        ctx.start();
        return null;
    }

    public boolean stop(String name) {
        ScriptContext c = findRunning(name);
        if (c == null) return false;
        c.stop();
        Mc.chat(PREFIX + "Stopped \u00a7b" + c.name);
        return true;
    }

    public void stopAll() {
        for (ScriptContext c : running.values()) c.stop();
    }

    void finished(ScriptContext ctx, boolean failed) {
        running.remove(ctx.name, ctx);
        if (!failed && !ctx.stopRequested) Mc.chat(PREFIX + "\u00a7b" + ctx.name + " \u00a7ffinished");
    }

    public void toggle(String name) {
        if (isRunning(name)) {
            stop(name);
        } else {
            String err = run(name);
            if (err != null) Mc.chat("\u00a7c" + err);
        }
    }

    // ------------------------------------------------------------------ events and ticking

    public void fire(String event, Map<String, Object> data) {
        for (ScriptContext c : running.values()) c.dispatch(event, data);
    }

    /** Start of every client tick: re-apply held keys before the game reads input, record macros. */
    public void onStartTick(MinecraftClient c) {
        for (ScriptContext s : running.values()) {
            for (KeyBinding kb : s.heldKeys) kb.setPressed(true);
        }
    }

    /** End of every client tick (snapshot already refreshed). */
    public void onEndTick(MinecraftClient c) {
        tickCount++;
        boolean inWorld = c.player != null && c.world != null;
        worldTicks = inWorld ? worldTicks + 1 : 0;
        if (!inWorld && wasInWorld) stopAll(); // leaving a world always stops scripts
        wasInWorld = inWorld;
        if (inWorld && worldTicks == 40) {
            for (ScriptInfo i : list()) {
                if (i.autorun()) {
                    String err = run(i.name());
                    if (err != null) Mc.chat("\u00a7c" + err);
                }
            }
        }

        for (ScriptContext s : running.values()) {
            s.pollKeys(c);
            s.onTick();
        }

        if (tickCount % 40 == 1) hotkeyCache = list();
        for (ScriptInfo info : hotkeyCache) {
            if (info.hotkey() == null) continue;
            InputUtil.Key key = Keys.physicalOrNull(info.hotkey());
            if (key == null) continue;
            boolean down = c.currentScreen == null && Keys.isDown(key);
            boolean prev = hotkeyDown.getOrDefault(info.name(), false);
            if (down && !prev && inWorld) toggle(info.name());
            hotkeyDown.put(info.name(), down);
        }
    }
}
