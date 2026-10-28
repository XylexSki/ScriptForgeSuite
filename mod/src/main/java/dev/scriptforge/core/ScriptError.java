package dev.scriptforge.core;

/** A normal, script-visible error (bad argument, not in a world, ...). Catchable with try/catch in JS. */
public class ScriptError extends RuntimeException {
    public ScriptError(String message) {
        super(message);
    }
}
