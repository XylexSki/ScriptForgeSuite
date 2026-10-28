package dev.scriptforge.api;

import dev.scriptforge.core.Hud;
import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.ScriptContext;

/**
 * hud.* : draw text and boxes on the in-game HUD. Elements stay until removed or the script ends.
 * Negative x/y anchor to the right/bottom edge. Colors: 0xRRGGBB or "#RRGGBB" / "#AARRGGBB".
 */
public class HudApi {
    private final ScriptContext ctx;

    public HudApi(ScriptContext ctx) {
        this.ctx = ctx;
    }

    public void text(String id, double x, double y, String text) {
        text(id, x, y, text, null);
    }

    public void text(String id, double x, double y, String text, Object color) {
        Hud.put(ctx.name, id, new Hud.Item(false, (int) x, (int) y, 0, 0, JsUtil.color(color, 0xFFFFFFFF), text));
    }

    public void rect(String id, double x, double y, double w, double h, Object color) {
        Hud.put(ctx.name, id, new Hud.Item(true, (int) x, (int) y, (int) w, (int) h, JsUtil.color(color, 0x80000000), ""));
    }

    public void remove(String id) {
        Hud.remove(ctx.name, id);
    }

    public void clear() {
        Hud.clear(ctx.name);
    }
}
