package dev.scriptforge.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A script file plus the metadata read from its header comments (// @description, // @hotkey, // @autorun). */
public record ScriptInfo(String name, Path path, String description, String hotkey, boolean autorun) {
    private static final Pattern TAG = Pattern.compile("^//\\s*@(\\w+)\\s*(.*)$");

    public static ScriptInfo parse(Path p) {
        String file = p.getFileName().toString();
        String name = file.endsWith(".js") ? file.substring(0, file.length() - 3) : file;
        String desc = "";
        String hotkey = null;
        boolean auto = false;
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 30) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!line.startsWith("//")) break;
                Matcher m = TAG.matcher(line);
                if (!m.matches()) continue;
                switch (m.group(1).toLowerCase()) {
                    case "description" -> desc = m.group(2).trim();
                    case "hotkey" -> hotkey = m.group(2).trim();
                    case "autorun" -> auto = true;
                    default -> { }
                }
            }
        } catch (IOException ignored) {
            // unreadable header: treat as no metadata
        }
        return new ScriptInfo(name, p, desc, hotkey, auto);
    }
}
