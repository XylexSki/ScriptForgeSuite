package dev.scriptforge.api;

import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptStopped;
import org.mozilla.javascript.Function;

/** Backing object for the global functions (sleep, waitTicks, setTimeout, print, exit, ...). */
public class Globals {
    private final ScriptContext ctx;

    public Globals(ScriptContext ctx) {
        this.ctx = ctx;
    }

    public void sleep(double ms) {
        ctx.sleep((long) ms);
    }

    public void waitTicks(double n) {
        ctx.waitTicks((int) n);
    }

    public boolean waitUntil(Function cond, double timeoutMs) {
        return ctx.waitUntil(cond, (long) timeoutMs);
    }

    public double setTimeout(Function fn, double ms) {
        return ctx.setTimer(fn, Math.max(0, (long) ms), false);
    }

    public double setInterval(Function fn, double ms) {
        return ctx.setTimer(fn, Math.max(1, (long) ms), true);
    }

    public void clearTimer(double id) {
        ctx.clearTimer((int) id);
    }

    public void print(String text) {
        ctx.print(text);
    }

    public void exit() {
        throw new ScriptStopped();
    }
}
