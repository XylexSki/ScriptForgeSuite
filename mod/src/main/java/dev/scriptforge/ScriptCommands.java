package dev.scriptforge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.scriptforge.core.MacroRecorder;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptInfo;
import dev.scriptforge.core.ScriptManager;
import dev.scriptforge.macro.Macro;
import dev.scriptforge.gui.ScriptsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;

import java.io.IOException;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** /script list | run <name> | stop <name> | stopall | gui | record start | record stop <name> | play <name> [loops] */
public final class ScriptCommands {
    private ScriptCommands() {}

    private static final String P = "\u00a77[ScriptForge] \u00a7f";

    private static final SuggestionProvider<FabricClientCommandSource> SCRIPTS = (ctx, b) ->
            CommandSource.suggestMatching(ScriptForge.scripts().list().stream().map(ScriptInfo::name).toList(), b);
    private static final SuggestionProvider<FabricClientCommandSource> RUNNING = (ctx, b) ->
            CommandSource.suggestMatching(ScriptForge.scripts().runningNames(), b);
    private static final SuggestionProvider<FabricClientCommandSource> MACROS = (ctx, b) ->
            CommandSource.suggestMatching(MacroRecorder.names(ScriptForge.scripts().macrosDir), b);

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("script")
                        .then(literal("list").executes(c -> {
                            list();
                            return 1;
                        }))
                        .then(literal("run").then(argument("name", StringArgumentType.greedyString())
                                .suggests(SCRIPTS)
                                .executes(c -> {
                                    String err = ScriptForge.scripts().run(StringArgumentType.getString(c, "name"));
                                    if (err != null) Mc.chat("\u00a7c" + err);
                                    return 1;
                                })))
                        .then(literal("stop").then(argument("name", StringArgumentType.greedyString())
                                .suggests(RUNNING)
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    if (!ScriptForge.scripts().stop(name)) Mc.chat("\u00a7c'" + name + "' is not running");
                                    return 1;
                                })))
                        .then(literal("stopall").executes(c -> {
                            ScriptForge.scripts().stopAll();
                            Mc.chat(P + "Stopped all scripts");
                            return 1;
                        }))
                        .then(literal("gui").executes(c -> {
                            // queue it so it opens after the chat screen has closed
                            Mc.client().send(() -> Mc.client().setScreen(new ScriptsScreen()));
                            return 1;
                        }))
                        .then(literal("record")
                                .then(literal("start").executes(c -> {
                                    try {
                                        MacroRecorder.start();
                                        Mc.chat(P + "Recording. Dig, place, walk, chat... then \u00a7b/script record stop <name>");
                                    } catch (RuntimeException e) {
                                        Mc.chat("\u00a7c" + e.getMessage());
                                    }
                                    return 1;
                                }))
                                .then(literal("stop").then(argument("name", StringArgumentType.word())
                                        .executes(c -> {
                                            Macro m = MacroRecorder.stop(StringArgumentType.getString(c, "name"));
                                            if (m == null) {
                                                Mc.chat("\u00a7cNot recording");
                                                return 0;
                                            }
                                            try {
                                                MacroRecorder.save(ScriptForge.scripts().macrosDir, m);
                                                Mc.chat(P + "Saved \u00a7b" + m.name + Macro.EXTENSION + "\u00a7f: " + m.ticks()
                                                        + " ticks (" + String.format("%.1f", m.seconds()) + "s), "
                                                        + m.eventCount() + " events");
                                            } catch (IOException e) {
                                                Mc.chat("\u00a7cCould not save: " + e.getMessage());
                                            }
                                            return 1;
                                        })))
                                .then(literal("status").executes(c -> {
                                    Mc.chat(P + (MacroRecorder.isRecording()
                                            ? "Recording: " + MacroRecorder.recordedTicks() + " ticks so far"
                                            : "Not recording"));
                                    return 1;
                                })))
                        .then(literal("play").then(argument("name", StringArgumentType.word())
                                .suggests(MACROS)
                                .executes(c -> play(StringArgumentType.getString(c, "name"), 1))
                                .then(argument("loops", IntegerArgumentType.integer(0))
                                        .executes(c -> play(StringArgumentType.getString(c, "name"),
                                                IntegerArgumentType.getInteger(c, "loops"))))))));
    }

    private static int play(String rawName, int loops) {
        String name = Macro.safeName(rawName);
        String err = ScriptForge.scripts().runInline("macro:" + name, "macro.play('" + name + "', " + loops + ");");
        if (err != null) Mc.chat("\u00a7c" + err);
        return 1;
    }

    private static void list() {
        ScriptManager m = ScriptForge.scripts();
        List<ScriptInfo> all = m.list();
        if (all.isEmpty()) {
            Mc.chat(P + "No scripts yet. Put .js files in " + m.scriptsDir);
            return;
        }
        Mc.chat(P + all.size() + " script(s):");
        for (ScriptInfo i : all) {
            String state = m.isRunning(i.name()) ? "\u00a7a[running] " : "\u00a77";
            String extra = (i.hotkey() != null ? " \u00a78[" + i.hotkey() + "]" : "") + (i.autorun() ? " \u00a78[autorun]" : "");
            Mc.chat(" " + state + "\u00a7b" + i.name() + extra + (i.description().isEmpty() ? "" : " \u00a77- " + i.description()));
        }
    }
}
