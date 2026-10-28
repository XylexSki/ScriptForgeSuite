package dev.scriptforge.headless;

import dev.scriptforge.macro.Event;
import dev.scriptforge.macro.Frame;
import dev.scriptforge.macro.Macro;
import dev.scriptforge.macro.MacroIO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/** scriptforge-headless run|check <config.json>   |   info <macro.sfmacro> */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help") || args[0].equals("help")) {
            usage();
            return;
        }
        // Allow "scriptforge-headless headless.json" as a shortcut for "run".
        String cmd = args[0];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        if (!cmd.equals("run") && !cmd.equals("check") && !cmd.equals("info")) {
            rest = args;
            cmd = "run";
        }
        if (rest.length < 1) {
            usage();
            System.exit(2);
        }
        try {
            switch (cmd) {
                case "run" -> run(Path.of(rest[0]));
                case "check" -> System.exit(check(Path.of(rest[0])) ? 0 : 1);
                case "info" -> info(Path.of(rest[0]));
                default -> usage();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (System.getProperty("sf.debug") != null) e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(Path configFile) throws Exception {
        Config cfg = Config.load(configFile);
        Path macroFile = cfg.macroPath(configFile);
        if (!Files.isRegularFile(macroFile)) throw new IllegalArgumentException("macro file not found: " + macroFile);
        Macro macro = MacroIO.read(macroFile);
        if (macro.frames.isEmpty()) throw new IllegalArgumentException("macro has no recorded ticks: " + macroFile);
        System.out.printf("Macro '%s': %d ticks (%.1f s), recorded on %s, drift %.1f blocks%n",
                macro.name, macro.ticks(), macro.seconds(), macro.mc.isBlank() ? "?" : macro.mc, macro.endDrift());
        if (cfg.loops != 1 && macro.endDrift() > 2.0 && cfg.mode.equals("relative")) {
            System.out.printf("Note: the macro ends %.1f blocks away from where it starts, so each loop begins where the previous "
                    + "one ended. That suits tunnels and rows; for a farm that should be harvested in place, record it so "
                    + "that it finishes where it began.%n", macro.endDrift());
        }
        new BotManager(cfg, macro).run();
    }

    private static boolean check(Path configFile) throws Exception {
        Config cfg = Config.load(configFile);
        boolean ok = ProxyCheck.run(cfg);
        System.out.println(ok ? "All checks passed." : "Some checks failed.");
        return ok;
    }

    private static void info(Path file) throws Exception {
        Macro m = MacroIO.read(file);
        System.out.println("Name:       " + m.name);
        System.out.println("Minecraft:  " + (m.mc.isBlank() ? "?" : m.mc));
        System.out.println("Recorded:   " + (m.created == 0 ? "?" : Instant.ofEpochMilli(m.created)));
        System.out.println("Dimension:  " + m.dimension);
        System.out.printf("Start:      %.2f %.2f %.2f%n", m.startX, m.startY, m.startZ);
        System.out.printf("Length:     %d ticks (%.1f s)%n", m.ticks(), m.seconds());
        System.out.printf("End drift:  %.1f blocks from the start%n", m.endDrift());
        Map<String, Integer> kinds = new TreeMap<>();
        int moving = 0;
        for (Frame f : m.frames) {
            if (f.keys != 0) moving++;
            for (Event e : f.events) kinds.merge(e.k, 1, Integer::sum);
        }
        System.out.printf("Keys held:  on %d of %d ticks%n", moving, m.ticks());
        System.out.println("Events:     " + (kinds.isEmpty() ? "none" : kinds));
    }

    private static void usage() {
        System.out.println("""
                ScriptForge headless client

                  java -jar scriptforge-headless-all.jar run   <config.json>   replay the macro on every account
                  java -jar scriptforge-headless-all.jar check <config.json>   test the server and every proxy, then exit
                  java -jar scriptforge-headless-all.jar info  <macro.sfmacro> describe a recorded macro

                Record macros with the ScriptForge mod (/script record start, /script record stop <name>) and point
                "macro" in the config at the .sfmacro file. See headless.example.json.
                """);
    }
}
