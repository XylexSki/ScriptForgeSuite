package dev.scriptforge.core;

/**
 * Thrown inside a script thread to unwind it when the script is stopped or calls exit().
 * It is an Error on purpose: Rhino never lets JS try/catch swallow Errors.
 */
public class ScriptStopped extends Error {
    public ScriptStopped() {
        super("Script stopped", null, false, false);
    }
}
