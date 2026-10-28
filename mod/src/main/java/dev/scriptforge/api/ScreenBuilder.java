package dev.scriptforge.api;

import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import dev.scriptforge.gui.ScriptScreen;
import dev.scriptforge.gui.ScriptScreen.Elem;
import dev.scriptforge.gui.ScriptScreen.Kind;
import org.mozilla.javascript.Function;

/**
 * Fluent builder returned by gui.screen("Title"). Positions are offsets from the screen center.
 * Callbacks (buttons, onClose) run on your script thread, so they can sleep() and call any API.
 */
public class ScreenBuilder {
    private final ScriptContext ctx;
    private final ScriptScreen screen;
    private int auto;

    public ScreenBuilder(ScriptContext ctx, String title) {
        this.ctx = ctx;
        this.screen = new ScriptScreen(title);
        ctx.onCleanup(() -> Mc.later(() -> {
            if (Mc.client().currentScreen == screen) Mc.client().setScreen(null);
        }));
    }

    private Runnable wrap(Function fn) {
        return fn == null ? null : () -> ctx.post(() -> ctx.callSafe(fn));
    }

    private ScreenBuilder add(Elem e) {
        screen.add(e);
        Mc.later(() -> {
            if (Mc.client().currentScreen == screen) screen.rebuild();
        });
        return this;
    }

    public ScreenBuilder label(double x, double y, String text) {
        return label("label" + (++auto), x, y, text);
    }

    public ScreenBuilder label(String id, double x, double y, String text) {
        return add(new Elem(Kind.LABEL, id, (int) x, (int) y, 0, 0, text, null));
    }

    public ScreenBuilder button(double x, double y, double w, double h, String text, Function onClick) {
        return add(new Elem(Kind.BUTTON, "button" + (++auto), (int) x, (int) y, (int) w, (int) h, text, wrap(onClick)));
    }

    public ScreenBuilder textField(String id, double x, double y, double w, double h, String placeholder) {
        return add(new Elem(Kind.FIELD, id, (int) x, (int) y, (int) w, (int) h, placeholder, null));
    }

    /** Change a label's text, a button's text, or a text field's contents. */
    public ScreenBuilder setText(String id, String text) {
        Elem e = screen.get(id);
        if (e == null) throw new ScriptError("No element with id '" + id + "'");
        if (e.kind == Kind.FIELD) e.value = text; else e.text = text;
        return this;
    }

    /** Current contents of a text field. */
    public String getText(String id) {
        Elem e = screen.get(id);
        if (e == null) throw new ScriptError("No element with id '" + id + "'");
        return e.kind == Kind.FIELD ? e.value : e.text;
    }

    /** Called when the screen is closed (Esc, close(), or replaced). */
    public ScreenBuilder onClose(Function fn) {
        Runnable r = wrap(fn);
        screen.onClosed(r == null ? () -> { } : r);
        return this;
    }

    public ScreenBuilder open() {
        Mc.run(() -> Mc.client().setScreen(screen));
        return this;
    }

    public void close() {
        Mc.run(() -> {
            if (Mc.client().currentScreen == screen) Mc.client().setScreen(null);
        });
    }

    public boolean isOpen() {
        return Mc.call(() -> Mc.client().currentScreen == screen);
    }
}
