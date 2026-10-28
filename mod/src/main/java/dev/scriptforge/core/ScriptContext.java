package dev.scriptforge.core;

import dev.scriptforge.ScriptForge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * One running script. It owns a thread and a single-threaded event loop:
 * the script body runs top to bottom, and while it is blocked in sleep()/waitTicks()/chat.waitFor()
 * (or after it finishes) queued event handlers, timers and GUI callbacks run on the SAME thread.
 * That gives plain, race-free JavaScript semantics with simple blocking calls.
 */
public final class ScriptContext implements Runnable {
    static final Object TL_KEY = "scriptforge.ctx";
    private static final Runnable WAKE = () -> { };
    private static final ScheduledExecutorService TIMERS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ScriptForge-Timers");
        t.setDaemon(true);
        return t;
    });

    public static final class KeyWatch {
        public final Function fn;
        public boolean down;

        public KeyWatch(Function fn) {
            this.fn = fn;
        }
    }

    public final ScriptManager manager;
    public final String name;
    public volatile boolean stopped;
    public volatile boolean stopRequested;
    public volatile Scriptable scope;

    /** Keys this script is holding down (re-applied every tick). */
    public final Set<KeyBinding> heldKeys = ConcurrentHashMap.newKeySet();
    public final Map<String, List<Function>> handlers = new ConcurrentHashMap<>();
    public final Map<InputUtil.Key, KeyWatch> keyWatches = new ConcurrentHashMap<>();

    private final Map<Integer, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final AtomicInteger timerIds = new AtomicInteger();
    private final Path file;
    private final String inline;
    private final Thread thread;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final AtomicLong ticks = new AtomicLong();
    private final AtomicInteger waiters = new AtomicInteger();
    private final List<Runnable> cleanups = new CopyOnWriteArrayList<>();
    private volatile boolean tickPending;

    public ScriptContext(ScriptManager manager, String name, Path file, String inlineSource) {
        this.manager = manager;
        this.name = name;
        this.file = file;
        this.inline = inlineSource;
        this.thread = new Thread(this, "ScriptForge-" + name);
        this.thread.setDaemon(true);
    }

    /** The context of the script thread we are currently on (null elsewhere). */
    public static ScriptContext current() {
        Context cx = Context.getCurrentContext();
        return cx == null ? null : (ScriptContext) cx.getThreadLocal(TL_KEY);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        stopRequested = true;
        stopped = true;
        thread.interrupt();
        queue.offer(WAKE);
    }

    public void checkStopped() {
        if (stopped) throw new ScriptStopped();
    }

    public void onCleanup(Runnable r) {
        cleanups.add(r);
    }

    /** Queue work for the script thread (event handlers, button callbacks, ...). */
    public void post(Runnable r) {
        if (!stopped) queue.offer(r);
    }

    // ------------------------------------------------------------------ blocking primitives

    /**
     * Blocks the script thread until done is true or the timeout passes (timeoutMs <= 0 = no timeout),
     * running queued events in the meantime. Returns false on timeout.
     */
    public boolean await(BooleanSupplier done, long timeoutMs) {
        long deadline = timeoutMs > 0 ? System.nanoTime() + timeoutMs * 1_000_000L : 0L;
        waiters.incrementAndGet();
        try {
            while (true) {
                checkStopped();
                if (done.getAsBoolean()) return true;
                long waitMs = 50;
                if (deadline != 0L) {
                    long left = (deadline - System.nanoTime()) / 1_000_000L;
                    if (left <= 0) return false;
                    waitMs = Math.min(waitMs, left);
                }
                Runnable r;
                try {
                    r = queue.poll(waitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new ScriptStopped();
                }
                if (r != null) r.run();
            }
        } finally {
            waiters.decrementAndGet();
        }
    }

    public void sleep(long ms) {
        await(() -> false, Math.max(1, ms));
    }

    public void waitTicks(int n) {
        long target = ticks.get() + Math.max(1, n);
        await(() -> ticks.get() >= target, 0);
    }

    public boolean waitUntil(Function cond, long timeoutMs) {
        Context cx = Context.getCurrentContext();
        return await(() -> Context.toBoolean(cond.call(cx, scope, scope, new Object[0])), timeoutMs);
    }

    // ------------------------------------------------------------------ timers

    public int setTimer(Function fn, long ms, boolean repeat) {
        int id = timerIds.incrementAndGet();
        Runnable fire = () -> post(() -> {
            if (!repeat) timers.remove(id);
            callSafe(fn);
        });
        ScheduledFuture<?> f = repeat
                ? TIMERS.scheduleAtFixedRate(fire, ms, ms, TimeUnit.MILLISECONDS)
                : TIMERS.schedule(fire, ms, TimeUnit.MILLISECONDS);
        timers.put(id, f);
        return id;
    }

    public void clearTimer(int id) {
        ScheduledFuture<?> f = timers.remove(id);
        if (f != null) f.cancel(false);
    }

    private boolean isAlive() {
        return !timers.isEmpty() || !keyWatches.isEmpty() || handlers.values().stream().anyMatch(l -> !l.isEmpty());
    }

    // ------------------------------------------------------------------ events (called from the client thread)

    public boolean wants(String event) {
        List<Function> l = handlers.get(event);
        return l != null && !l.isEmpty();
    }

    public void dispatch(String event, Map<String, Object> data) {
        if (stopped || !wants(event)) return;
        queue.offer(() -> {
            List<Function> list = handlers.get(event);
            if (list == null) return;
            Scriptable arg = JsUtil.fromMap(data);
            for (Function f : list) callSafe(f, arg);
        });
    }

    void onTick() {
        ticks.incrementAndGet();
        if (waiters.get() > 0) queue.offer(WAKE);
        if (wants("tick") && !tickPending) {
            tickPending = true;
            double t = ticks.get();
            queue.offer(() -> {
                tickPending = false;
                List<Function> list = handlers.get("tick");
                if (list != null) for (Function f : list) callSafe(f, t);
            });
        }
    }

    void pollKeys(MinecraftClient c) {
        if (keyWatches.isEmpty()) return;
        for (Map.Entry<InputUtil.Key, KeyWatch> e : keyWatches.entrySet()) {
            KeyWatch w = e.getValue();
            boolean down = c.currentScreen == null && Keys.isDown(e.getKey());
            if (down && !w.down) queue.offer(() -> callSafe(w.fn));
            w.down = down;
        }
    }

    // ------------------------------------------------------------------ calling into JS

    /** Calls a JS function on the script thread; errors are reported, not thrown. */
    public Object callSafe(Function fn, Object... args) {
        try {
            Context cx = Context.getCurrentContext();
            return fn.call(cx, scope, scope, args);
        } catch (RhinoException e) {
            fail(describe(e));
            return null;
        }
    }

    static String describe(RhinoException e) {
        String msg = e.details();
        if (e instanceof WrappedException we && we.getWrappedException() != null) {
            msg = String.valueOf(we.getWrappedException().getMessage());
        }
        String where = e.sourceName() != null ? " (" + e.sourceName() + ":" + e.lineNumber() + ")" : "";
        return msg + where;
    }

    public void print(String text) {
        ScriptForge.LOG.info("[{}] {}", name, text);
        Mc.chat("\u00a77[\u00a7b" + name + "\u00a77] \u00a7f" + text);
    }

    public void fail(String message) {
        ScriptForge.LOG.warn("[{}] {}", name, message);
        Mc.chat("\u00a7c[" + name + "] " + message);
    }

    // ------------------------------------------------------------------ thread body

    @Override
    public void run() {
        Context cx = ScriptEngine.FACTORY.enterContext();
        boolean failed = false;
        try {
            cx.putThreadLocal(TL_KEY, this);
            cx.setClassShutter(className -> className.startsWith("dev.scriptforge.api."));
            ScriptableObject root = cx.initSafeStandardObjects(); // no Packages/java/LiveConnect
            scope = root;
            ApiInstaller.install(this, cx, root);

            String src = inline != null ? inline : Files.readString(file);
            if (src.startsWith("\uFEFF")) src = src.substring(1);
            cx.evaluateString(root, src, name, 1, null);

            // Body finished: stay alive as long as handlers/timers exist.
            await(() -> !isAlive(), 0);
        } catch (ScriptStopped ignored) {
            // normal shutdown (stop command, exit(), disconnect)
        } catch (RhinoException e) {
            failed = true;
            fail(describe(e));
        } catch (Throwable t) {
            failed = true;
            ScriptForge.LOG.error("Script " + name + " crashed", t);
            fail(String.valueOf(t));
        } finally {
            stopped = true;
            Thread.interrupted(); // clear the interrupt flag so cleanup I/O isn't aborted
            runCleanups();
            Context.exit();
            manager.finished(this, failed);
        }
    }

    private void runCleanups() {
        for (ScheduledFuture<?> f : timers.values()) f.cancel(false);
        timers.clear();
        Set<KeyBinding> held = new HashSet<>(heldKeys);
        heldKeys.clear();
        if (!held.isEmpty()) Mc.later(() -> held.forEach(Keys::restore));
        Hud.clear(name);
        for (Runnable r : cleanups) {
            try {
                r.run();
            } catch (Throwable t) {
                ScriptForge.LOG.warn("Cleanup failed in " + name, t);
            }
        }
    }
}
