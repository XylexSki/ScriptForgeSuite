package dev.scriptforge.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Always-on ring buffer of recent chat. type is "chat" (player), "game" (system/server) or "out" (you sent it). */
public final class ChatLog {
    private ChatLog() {}

    public record Line(long seq, long time, String type, String sender, String text) {}

    private static final int MAX = 2000;
    private static final ArrayDeque<Line> BUFFER = new ArrayDeque<>();
    private static long seq = 0;

    /** Live listeners (used by chat.record()). Called on the client thread. */
    public static final List<Consumer<Line>> LISTENERS = new CopyOnWriteArrayList<>();

    public static Line add(String type, String sender, String text) {
        Line line;
        synchronized (ChatLog.class) {
            line = new Line(++seq, System.currentTimeMillis(), type, sender == null ? "" : sender, text);
            BUFFER.addLast(line);
            while (BUFFER.size() > MAX) BUFFER.removeFirst();
        }
        for (Consumer<Line> l : LISTENERS) {
            try {
                l.accept(line);
            } catch (Throwable ignored) {
                // a broken listener must never break chat
            }
        }
        return line;
    }

    public static synchronized long seq() {
        return seq;
    }

    public static synchronized List<Line> last(int n) {
        List<Line> all = new ArrayList<>(BUFFER);
        return all.subList(Math.max(0, all.size() - Math.max(0, n)), all.size());
    }

    /** First incoming line newer than {@code after} that matches. */
    public static synchronized Line firstMatchAfter(long after, Pattern p) {
        for (Line l : BUFFER) {
            if (l.seq() > after && !l.type().equals("out") && p.matcher(l.text()).find()) return l;
        }
        return null;
    }
}
