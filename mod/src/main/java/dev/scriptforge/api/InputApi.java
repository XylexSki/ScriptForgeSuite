package dev.scriptforge.api;

import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Keys;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import net.minecraft.client.option.KeyBinding;
import org.mozilla.javascript.Scriptable;

import java.util.ArrayList;
import java.util.List;

/**
 * input.* : press game keys the way a player would.
 * Keys: forward back left right jump sneak sprint attack use pick drop swap inventory hotbar1..hotbar9
 */
public class InputApi {
    private final ScriptContext ctx;

    public InputApi(ScriptContext ctx) {
        this.ctx = ctx;
    }

    /** Hold a key down until release() / releaseAll() / the script ends. */
    public void hold(String key) {
        KeyBinding kb = Keys.binding(key);
        Mc.run(() -> {
            ctx.heldKeys.add(kb);
            kb.setPressed(true);
        });
    }

    public void release(String key) {
        KeyBinding kb = Keys.binding(key);
        Mc.run(() -> {
            ctx.heldKeys.remove(kb);
            Keys.restore(kb);
        });
    }

    public void tap(String key) {
        tap(key, 1);
    }

    /** Press, wait the given ticks (default 1), release. Blocks while waiting. */
    public void tap(String key, double ticks) {
        KeyBinding kb = Keys.binding(key);
        Mc.run(() -> {
            ctx.heldKeys.add(kb);
            kb.setPressed(true);
            Keys.bump(kb); // fire the one-shot "pressed" event too (attack/use/drop/swap/...)
        });
        try {
            ctx.waitTicks((int) Math.max(1, ticks));
        } finally {
            release(key);
        }
    }

    public void releaseAll() {
        Mc.run(() -> {
            for (KeyBinding kb : ctx.heldKeys) Keys.restore(kb);
            ctx.heldKeys.clear();
        });
    }

    /** Is the game action currently active (held by you or by a script)? */
    public boolean isDown(String key) {
        KeyBinding kb = Keys.binding(key);
        return Mc.call(kb::isPressed);
    }

    /** Is a physical keyboard/mouse key held right now? Names: "r", "f6", "left.shift", "space", "mouse.left". */
    public boolean isKeyDown(String physicalKey) {
        var k = Keys.physical(physicalKey);
        return Mc.call(() -> Keys.isDown(k));
    }

    public Scriptable keys() {
        List<Object> names = new ArrayList<>(List.of(Keys.NAMES));
        return JsUtil.arr(names);
    }
}
