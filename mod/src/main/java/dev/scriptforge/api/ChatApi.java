package dev.scriptforge.api;

import dev.scriptforge.core.ChatLog;
import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.mozilla.javascript.Scriptable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** chat.* : send, print, history, search, waitFor, record. */
public class ChatApi {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ScriptContext ctx;
    private Consumer<ChatLog.Line> recorder;
    private BufferedWriter writer;

    public ChatApi(ScriptContext ctx) {
        this.ctx = ctx;
        ctx.onCleanup(this::stopRecording);
    }

    /** Sends a chat message; text starting with "/" is sent as a command. */
    public void send(String message) {
        Mc.run(() -> {
            ClientPlayNetworkHandler h = Mc.client().getNetworkHandler();
            if (h == null) throw new ScriptError("Not connected to a server");
            if (message.startsWith("/")) h.sendChatCommand(message.substring(1));
            else h.sendChatMessage(message);
        });
    }

    public void command(String command) {
        send(command.startsWith("/") ? command : "/" + command);
    }

    /** Local-only message (nobody else sees it). Supports &a &c &l ... color codes. */
    public void print(String message) {
        Mc.chat(message.replaceAll("(?i)&([0-9a-fk-or])", "\u00a7$1"));
    }

    public Scriptable history() {
        return history(100);
    }

    /** The last n chat lines as [{time, type, sender, text}]. */
    public Scriptable history(double n) {
        return toJs(ChatLog.last((int) n));
    }

    /** All remembered lines matching a regex (case-insensitive). */
    public Scriptable search(String regex) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        List<ChatLog.Line> hits = new ArrayList<>();
        for (ChatLog.Line l : ChatLog.last(2000)) if (p.matcher(l.text()).find()) hits.add(l);
        return toJs(hits);
    }

    /** Blocks until an incoming message matches the regex. Returns its text, or null on timeout (0 = wait forever). */
    public String waitFor(String regex, double timeoutMs) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        long start = ChatLog.seq();
        String[] found = new String[1];
        boolean ok = ctx.await(() -> {
            ChatLog.Line l = ChatLog.firstMatchAfter(start, p);
            if (l != null) found[0] = l.text();
            return l != null;
        }, (long) timeoutMs);
        return ok ? found[0] : null;
    }

    public void record(String file) {
        record(file, null);
    }

    /** Appends every chat line to scriptforge/recordings/<file>. opts: {outgoing: true, filter: "regex"} */
    public synchronized void record(String file, Scriptable opts) {
        stopRecording();
        boolean outgoing = JsUtil.bool(opts, "outgoing", true);
        String filterText = JsUtil.str(opts, "filter", null);
        Pattern filter = filterText == null ? null : Pattern.compile(filterText, Pattern.CASE_INSENSITIVE);

        String fname = file.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!fname.contains(".")) fname += ".txt";
        Path path = ctx.manager.recordingsDir.resolve(fname);
        BufferedWriter w;
        try {
            Files.createDirectories(path.getParent());
            w = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new ScriptError("Cannot open " + path + ": " + e.getMessage());
        }
        writer = w;
        recorder = line -> {
            if (!outgoing && line.type().equals("out")) return;
            if (filter != null && !filter.matcher(line.text()).find()) return;
            String prefix = line.type().equals("out") ? ">> " : "";
            try {
                synchronized (w) {
                    w.write("[" + STAMP.format(Instant.ofEpochMilli(line.time())) + "] " + prefix + line.text());
                    w.newLine();
                    w.flush();
                }
            } catch (IOException ignored) {
                // recording was stopped while a line was in flight
            }
        };
        ChatLog.LISTENERS.add(recorder);
    }

    public synchronized void stopRecording() {
        if (recorder != null) {
            ChatLog.LISTENERS.remove(recorder);
            recorder = null;
        }
        BufferedWriter w = writer;
        writer = null;
        if (w != null) {
            synchronized (w) {
                try {
                    w.close();
                } catch (IOException ignored) {
                    // nothing useful to do
                }
            }
        }
    }

    public boolean isRecording() {
        return recorder != null;
    }

    private static Scriptable toJs(List<ChatLog.Line> lines) {
        List<Object> items = new ArrayList<>();
        for (ChatLog.Line l : lines) {
            items.add(JsUtil.obj("time", (double) l.time(), "type", l.type(), "sender", l.sender(), "text", l.text()));
        }
        return JsUtil.arr(items);
    }
}
