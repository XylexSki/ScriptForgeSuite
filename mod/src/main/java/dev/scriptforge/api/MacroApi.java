package dev.scriptforge.api;

import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Keys;
import dev.scriptforge.core.MacroRecorder;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import dev.scriptforge.macro.Frame;
import dev.scriptforge.macro.Macro;
import dev.scriptforge.macro.MacroIO;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * macro.* : record yourself (movement, camera, digging, placing, chat, commands) and replay it here.
 * The saved .sfmacro file can also be run by the headless client on other accounts.
 */
public class MacroApi {
    private final ScriptContext ctx;

    public MacroApi(ScriptContext ctx) {
        this.ctx = ctx;
    }

    private Path file(String name) {
        return MacroRecorder.file(ctx.manager.macrosDir, name);
    }

    /** Start recording from your current position. */
    public void record() {
        Mc.run(MacroRecorder::start);
    }

    public boolean isRecording() {
        return MacroRecorder.isRecording();
    }

    /** Stop recording and save scriptforge/macros/<name>.sfmacro. Returns the number of ticks recorded. */
    public double stop(String name) {
        Macro m = Mc.call(() -> MacroRecorder.stop(name));
        if (m == null) throw new ScriptError("Not recording");
        try {
            MacroRecorder.save(ctx.manager.macrosDir, m);
        } catch (IOException e) {
            throw new ScriptError("Could not save macro: " + e.getMessage());
        }
        return m.ticks();
    }

    public Scriptable list() {
        return JsUtil.arr(new ArrayList<Object>(MacroRecorder.names(ctx.manager.macrosDir)));
    }

    public boolean exists(String name) {
        return Files.exists(file(name));
    }

    /** {name, ticks, seconds, events, mc, dimension} for a saved macro. */
    public Scriptable info(String name) {
        Macro m = load(name);
        return JsUtil.obj("name", m.name, "ticks", m.ticks(), "seconds", m.seconds(), "events", m.eventCount(),
                "mc", m.mc, "dimension", m.dimension);
    }

    public void play(String name) {
        play(name, 1);
    }

    /**
     * Replay a macro in your own game using input emulation (keys + camera + hotbar).
     * loops = how many times (0 = until the script is stopped). Blocks until finished.
     */
    public void play(String name, double loops) {
        Macro m = load(name);
        if (m.frames.isEmpty()) return;
        long n = (long) loops;
        try {
            for (long i = 0; n <= 0 || i < n; i++) {
                int prev = 0;
                for (Frame fr : m.frames) {
                    final int before = prev;
                    Mc.run(() -> apply(fr, before));
                    prev = fr.keys;
                    ctx.waitTicks(1);
                }
            }
        } finally {
            Mc.later(() -> {
                for (KeyBinding kb : ctx.heldKeys) Keys.restore(kb);
                ctx.heldKeys.clear();
            });
        }
    }

    private Macro load(String name) {
        Path f = file(name);
        if (!Files.exists(f)) throw new ScriptError("No macro named '" + name + "'");
        try {
            return MacroIO.read(f);
        } catch (IOException | RuntimeException e) {
            throw new ScriptError("Could not load macro '" + name + "': " + e.getMessage());
        }
    }

    private void apply(Frame f, int prevMask) {
        ClientPlayerEntity p = Mc.player();
        for (int i = 0; i < Macro.KEYS.length; i++) {
            KeyBinding kb = Keys.binding(Macro.KEYS[i]);
            boolean on = f.key(i);
            boolean was = ((prevMask >> i) & 1) != 0;
            if (on) {
                ctx.heldKeys.add(kb);
                kb.setPressed(true);
                if (!was) Keys.bump(kb);
            } else {
                ctx.heldKeys.remove(kb);
                kb.setPressed(false);
            }
        }
        p.setYaw(f.yaw);
        p.setPitch(f.pitch);
        p.getInventory().setSelectedSlot(Math.max(0, Math.min(8, f.slot)));
    }
}
