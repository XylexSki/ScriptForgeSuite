package dev.scriptforge.api;

import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** fs.* : read/write text files, restricted to the scriptforge/data folder. */
public class FsApi {
    private final ScriptContext ctx;

    public FsApi(ScriptContext ctx) {
        this.ctx = ctx;
    }

    private Path resolve(String name) {
        Path base = ctx.manager.dataDir.toAbsolutePath().normalize();
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base)) throw new ScriptError("Path escapes the data folder: " + name);
        return p;
    }

    /** File contents, or null if it does not exist. */
    public String read(String name) {
        Path p = resolve(name);
        try {
            return Files.exists(p) ? Files.readString(p) : null;
        } catch (IOException e) {
            throw new ScriptError("Cannot read " + name + ": " + e.getMessage());
        }
    }

    public void write(String name, String text) {
        put(name, text, false);
    }

    public void append(String name, String text) {
        put(name, text, true);
    }

    public void appendLine(String name, String text) {
        put(name, text + System.lineSeparator(), true);
    }

    private void put(String name, String text, boolean append) {
        Path p = resolve(name);
        try {
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            if (append) {
                Files.writeString(p, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(p, text, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new ScriptError("Cannot write " + name + ": " + e.getMessage());
        }
    }

    public boolean exists(String name) {
        return Files.exists(resolve(name));
    }

    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(resolve(name));
        } catch (IOException e) {
            throw new ScriptError("Cannot delete " + name + ": " + e.getMessage());
        }
    }

    public Scriptable list() {
        List<Object> names = new ArrayList<>();
        try (Stream<Path> s = Files.list(ctx.manager.dataDir)) {
            s.map(p -> p.getFileName().toString()).sorted().forEach(names::add);
        } catch (IOException ignored) {
            // empty list
        }
        return JsUtil.arr(names);
    }
}
