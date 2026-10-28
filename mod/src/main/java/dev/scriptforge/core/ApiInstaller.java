package dev.scriptforge.core;

import dev.scriptforge.api.ChatApi;
import dev.scriptforge.api.EventsApi;
import dev.scriptforge.api.FsApi;
import dev.scriptforge.api.Globals;
import dev.scriptforge.api.GuiApi;
import dev.scriptforge.api.HudApi;
import dev.scriptforge.api.InputApi;
import dev.scriptforge.api.MacroApi;
import dev.scriptforge.api.MouseApi;
import dev.scriptforge.api.MoveApi;
import dev.scriptforge.api.PlayerApi;
import dev.scriptforge.api.WorldApi;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

/** Puts the script-facing globals (chat, player, input, ...) into a script's scope. */
final class ApiInstaller {
    private ApiInstaller() {}

    private static final String PRELUDE = """
            var __fmt = function (v) {
                if (v !== null && typeof v === 'object') { try { return JSON.stringify(v); } catch (e) { } }
                return String(v);
            };
            var print = function () {
                var parts = [];
                for (var i = 0; i < arguments.length; i++) parts.push(__fmt(arguments[i]));
                __g.print(parts.join(' '));
            };
            var console = { log: print, info: print, warn: print, error: print };
            var sleep = function (ms) { __g.sleep(ms); };
            var waitTicks = function (n) { __g.waitTicks(n === undefined ? 1 : n); };
            var waitUntil = function (cond, timeoutMs) { return __g.waitUntil(cond, timeoutMs || 0); };
            var setTimeout = function (fn, ms) { return __g.setTimeout(fn, ms || 0); };
            var setInterval = function (fn, ms) { return __g.setInterval(fn, ms || 1); };
            var clearTimeout = function (id) { __g.clearTimer(id); };
            var clearInterval = function (id) { __g.clearTimer(id); };
            var exit = function () { __g.exit(); };
            """;

    static void install(ScriptContext ctx, Context cx, ScriptableObject scope) {
        expose(scope, "__g", new Globals(ctx));
        expose(scope, "chat", new ChatApi(ctx));
        expose(scope, "player", new PlayerApi(ctx));
        expose(scope, "world", new WorldApi(ctx));
        expose(scope, "input", new InputApi(ctx));
        expose(scope, "mouse", new MouseApi(ctx));
        expose(scope, "move", new MoveApi(ctx));
        expose(scope, "gui", new GuiApi(ctx));
        expose(scope, "hud", new HudApi(ctx));
        expose(scope, "events", new EventsApi(ctx));
        expose(scope, "fs", new FsApi(ctx));
        expose(scope, "macro", new MacroApi(ctx));
        cx.evaluateString(scope, PRELUDE, "prelude", 1, null);
    }

    private static void expose(ScriptableObject scope, String name, Object api) {
        ScriptableObject.putProperty(scope, name, Context.javaToJS(api, scope));
    }
}
