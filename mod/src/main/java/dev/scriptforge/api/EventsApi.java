package dev.scriptforge.api;

import dev.scriptforge.core.Keys;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import org.mozilla.javascript.Function;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * events.* : react to things. A script with handlers stays alive until stopped.
 *   "chat" -> {type, sender, text, content, time}     (type: "chat" | "game")
 *   "send" -> {text, command}                          (you sent a message or command)
 *   "tick" -> ticks                                    (every client tick, coalesced if you are slow)
 */
public class EventsApi {
    private static final Set<String> EVENTS = Set.of("chat", "send", "tick");

    private final ScriptContext ctx;

    public EventsApi(ScriptContext ctx) {
        this.ctx = ctx;
    }

    public void on(String event, Function handler) {
        String e = event.toLowerCase(Locale.ROOT);
        if (!EVENTS.contains(e)) throw new ScriptError("Unknown event '" + event + "'. Valid: " + EVENTS);
        ctx.handlers.computeIfAbsent(e, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Remove all handlers of an event. */
    public void off(String event) {
        ctx.handlers.remove(event.toLowerCase(Locale.ROOT));
    }

    /** Run a handler each time a physical key is pressed (only while no screen is open). */
    public void onKey(String key, Function handler) {
        ctx.keyWatches.put(Keys.physical(key), new ScriptContext.KeyWatch(handler));
    }

    public void offKey(String key) {
        ctx.keyWatches.remove(Keys.physical(key));
    }

    public void clear() {
        ctx.handlers.clear();
        ctx.keyWatches.clear();
    }
}
