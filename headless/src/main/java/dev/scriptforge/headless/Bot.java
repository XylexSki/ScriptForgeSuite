package dev.scriptforge.headless;

import dev.scriptforge.macro.Macro;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.ProxyInfo;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerRotationPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One account on one connection (optionally through a proxy). Network events arrive on MCProtocolLib's packet thread;
 * everything that sends the macro runs on a 20 Hz ticker. The two only communicate through volatile fields.
 */
final class Bot {
    private static final Logger LOG = LoggerFactory.getLogger(Bot.class);
    /** Commands whose arguments are credentials: never written to the log. */
    private static final Set<String> SECRET_COMMANDS = Set.of(
            "login", "l", "log", "register", "reg", "password", "passwd", "changepassword", "unregister", "authme", "auth", "2fa", "totp");

    enum Phase { IDLE, CONNECTING, WAIT_START, COMMANDS, SETTLE, PLAYING, DONE, FAILED }

    private final Config cfg;
    private final Config.Account account;
    private final Macro macro;
    private final String proxySpec;
    private final String authProxySpec;
    private final PacketFactory pf = new PacketFactory();
    private final CompletableFuture<Void> finished = new CompletableFuture<>();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> ticker;
    private volatile ClientNetworkSession session;

    private volatile Phase phase = Phase.IDLE;
    private volatile boolean loggedIn;
    private volatile boolean shuttingDown;
    private volatile boolean expectedDisconnect;
    private volatile int entityId = -1;
    private volatile boolean positionKnown;
    private volatile double x, y, z;
    private volatile float yaw, pitch;
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger setbacks = new AtomicInteger();
    private volatile boolean resyncRequested;
    private volatile String abortReason;
    private volatile boolean playerLoadedSent;

    // ticker-thread state
    private int attempt;
    private int ticksInPhase;
    private long lastTickNanos;
    private int idleSincePos;
    private List<Config.Step> steps = List.of();
    private int stepIndex;
    private int stepWait;
    private MacroPlayer player;

    Bot(Config cfg, Config.Account account, Macro macro, String proxySpec, String authProxySpec) {
        this.cfg = cfg;
        this.account = account;
        this.macro = macro;
        this.proxySpec = proxySpec;
        this.authProxySpec = authProxySpec;
    }

    // ---- accessors used by MacroPlayer and BotManager
    String name() { return account.name; }
    double x() { return x; }
    double y() { return y; }
    double z() { return z; }
    float yaw() { return yaw; }
    float pitch() { return pitch; }
    boolean positionKnown() { return positionKnown; }
    int entityId() { return entityId; }
    int nextSequence() { return sequence.incrementAndGet(); }
    CompletableFuture<Void> finished() { return finished; }

    void setPosition(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    void send(Packet packet) {
        ClientNetworkSession s = session;
        if (s != null && s.isConnected()) s.send(packet);
    }

    String status() {
        String via = ProxySpec.redact(proxySpec);
        String p = switch (phase) {
            case PLAYING -> "PLAYING " + (player == null ? 0 : player.progressPercent()) + "% loop "
                    + ((player == null ? 0 : player.loopsDone()) + 1) + (cfg.loops == 0 ? "" : "/" + cfg.loops);
            default -> phase.name();
        };
        return String.format("%-16s %-22s via %s", account.name, p, via);
    }

    // ---- lifecycle
    void start(ScheduledExecutorService executor) {
        this.executor = executor;
        connect();
    }

    /** Ask the macro to stop and leave the server (Ctrl+C). */
    void shutdown() {
        shuttingDown = true;
        expectedDisconnect = true;
        ClientNetworkSession s = session;
        if (s != null && s.isConnected()) s.disconnect("ScriptForge headless shutting down");
        complete();
    }

    private void connect() {
        if (shuttingDown) return;
        phase = Phase.CONNECTING;
        resetRunState();
        LOG.info("[{}] connecting to {}:{} via {}{}", account.name, cfg.host(), cfg.port(), ProxySpec.redact(proxySpec),
                attempt > 0 ? " (retry " + attempt + "/" + cfg.reconnects + ")" : "");
        try {
            ProxyInfo gameProxy = ProxySpec.parse(proxySpec);
            ProxyInfo authProxy = ProxySpec.parse(authProxySpec);

            MinecraftProtocol protocol;
            if (account.mode.equals("token")) {
                protocol = new MinecraftProtocol(new GameProfile(parseUuid(account.uuid), account.name), account.resolvedToken());
            } else {
                protocol = new MinecraftProtocol(account.name);
            }

            ClientNetworkSession s = ClientNetworkSessionFactory.factory()
                    .setAddress(cfg.host(), cfg.port())
                    .setProtocol(protocol)
                    .setProxy(gameProxy)
                    .create();

            // Mojang's session server is contacted over plain HTTPS by MCProtocolLib itself, so it needs its own proxy.
            SessionService sessionService = new SessionService();
            sessionService.setProxy(authProxy);
            s.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);

            s.addListener(new Listener());
            session = s;
            s.connect(false);
        } catch (Exception e) {
            LOG.error("[{}] could not start connection: {}", account.name, e.toString());
            fail("could not start connection: " + e.getMessage());
            return;
        }
        startTicker();
    }

    private void resetRunState() {
        loggedIn = false;
        entityId = -1;
        positionKnown = false;
        playerLoadedSent = false;
        expectedDisconnect = false;
        resyncRequested = false;
        abortReason = null;
        setbacks.set(0);
        sequence.set(0);
        ticksInPhase = 0;
        idleSincePos = 0;
        stepIndex = 0;
        player = null;
        steps = List.of();
    }

    private void startTicker() {
        cancelTicker();
        lastTickNanos = 0;
        ticker = executor.scheduleAtFixedRate(this::tickSafe, 50, 50, TimeUnit.MILLISECONDS);
    }

    private void cancelTicker() {
        ScheduledFuture<?> t = ticker;
        if (t != null) t.cancel(false);
        ticker = null;
    }

    // ---- 20 Hz ticker
    private void tickSafe() {
        try {
            long now = System.nanoTime();
            // scheduleAtFixedRate runs late invocations back-to-back; a burst of packets would look like speed hacking.
            if (lastTickNanos != 0 && now - lastTickNanos < 25_000_000L) return;
            lastTickNanos = now;
            tick();
        } catch (Throwable t) {
            LOG.error("[{}] tick failed", account.name, t);
            fail("internal error: " + t);
        }
    }

    private void tick() {
        Phase ph = phase;
        ClientNetworkSession s = session;
        if (s == null || !s.isConnected() || !loggedIn) return;
        if (ph == Phase.DONE || ph == Phase.FAILED || ph == Phase.IDLE || ph == Phase.CONNECTING) {
            if (ph == Phase.DONE) idleTick();
            return;
        }
        ticksInPhase++;

        if (resyncRequested) {
            resyncRequested = false;
            if (player != null) player.resync();
        }

        switch (ph) {
            case WAIT_START -> {
                if (positionKnown && !playerLoadedSent && ticksInPhase >= 10) {
                    playerLoadedSent = true;
                    send(pf.playerLoaded());
                }
                if (positionKnown && ticksInPhase >= cfg.startDelayTicks) {
                    steps = new ArrayList<>(account.joinCommands); // per-account first (/login), then the shared ones
                    steps.addAll(cfg.joinCommands);
                    stepIndex = 0;
                    stepWait = steps.isEmpty() ? 0 : steps.get(0).delayTicks;
                    enter(steps.isEmpty() ? Phase.SETTLE : Phase.COMMANDS);
                }
                sendTickEnd();
            }
            case COMMANDS -> {
                if (stepWait > 0) {
                    stepWait--;
                } else {
                    sendStep(steps.get(stepIndex));
                    stepIndex++;
                    if (stepIndex >= steps.size()) enter(Phase.SETTLE);
                    else stepWait = steps.get(stepIndex).delayTicks;
                }
                sendTickEnd();
            }
            case SETTLE -> {
                if (ticksInPhase >= cfg.settleTicks) {
                    player = new MacroPlayer(this, macro, cfg);
                    enter(Phase.PLAYING);
                    LOG.info("[{}] starting macro '{}' ({} ticks, {} loop{})", account.name, macro.name, macro.ticks(),
                            cfg.loops == 0 ? "infinite" : cfg.loops, cfg.loops == 1 ? "" : "s");
                }
                sendTickEnd();
            }
            case PLAYING -> {
                String why = abortReason;
                if (why != null) player.abort(why);
                boolean done = player.tick();
                sendTickEnd();
                if (done) macroFinished(why);
            }
            default -> { }
        }
    }

    private void enter(Phase p) {
        ticksInPhase = 0;
        phase = p; // published last: the ticker only acts on the new phase once the counter has been reset
    }

    private void sendTickEnd() {
        send(pf.tickEnd());
    }

    /** After the macro: a vanilla client still reports its position at least once a second. */
    private void idleTick() {
        if (++idleSincePos >= 20) {
            idleSincePos = 0;
            send(pf.pos(true, false, x, y, z));
        }
        sendTickEnd();
    }

    private void sendStep(Config.Step step) {
        if (step.text == null || step.text.isBlank()) return;
        String text = step.text.strip();
        boolean command = text.startsWith("/");
        String body = command ? text.substring(1) : text;
        String head = body.split("\\s+", 2)[0].toLowerCase();
        boolean secret = command && SECRET_COMMANDS.contains(head);
        LOG.info("[{}] join step {}/{}: {}", account.name, stepIndex + 1, steps.size(),
                secret ? "/" + head + " <hidden>" : text);
        send(command ? pf.command(body) : pf.chat(text));
    }

    private void macroFinished(String abortedBecause) {
        if (abortedBecause != null) {
            LOG.warn("[{}] macro stopped: {}", account.name, abortedBecause);
        } else {
            LOG.info("[{}] macro finished", account.name);
        }
        enter(Phase.DONE);
        if (cfg.disconnectWhenDone) {
            expectedDisconnect = true;
            ClientNetworkSession s = session;
            if (s != null) s.disconnect("macro finished");
            // The disconnect event normally completes us; this covers a connection that never reports one.
            if (executor != null) executor.schedule(this::complete, 10, TimeUnit.SECONDS);
        }
    }

    // ---- terminal states
    private void fail(String why) {
        if (phase != Phase.DONE) phase = Phase.FAILED;
        LOG.warn("[{}] stopped: {}", account.name, why);
        cancelTicker();
        ClientNetworkSession s = session;
        if (s != null && s.isConnected()) {
            expectedDisconnect = true;
            s.disconnect("stopped");
        }
        complete();
    }

    private void complete() {
        cancelTicker();
        finished.complete(null);
    }

    // ---- network events
    private final class Listener extends SessionAdapter {
        @Override
        public void connected(ConnectedEvent event) {
            LOG.debug("[{}] socket connected", account.name);
        }

        @Override
        public void packetReceived(Session session, Packet packet) {
            if (packet instanceof ClientboundLoginPacket login) {
                entityId = login.getEntityId();
                if (!loggedIn) {
                    loggedIn = true;
                    positionKnown = false;
                    enter(Phase.WAIT_START);
                    LOG.info("[{}] joined the server", account.name);
                }
            } else if (packet instanceof ClientboundPlayerPositionPacket p) {
                onServerPosition(p);
            } else if (packet instanceof ClientboundPlayerRotationPacket r) {
                yaw = r.isRelativeY() ? yaw + r.getYRot() : r.getYRot();
                pitch = r.isRelativeX() ? pitch + r.getXRot() : r.getXRot();
            } else if (packet instanceof ClientboundPlayerCombatKillPacket kill) {
                if (kill.getPlayerId() == entityId) {
                    LOG.warn("[{}] died: {}", account.name, plain(kill.getMessage()));
                    send(pf.respawn());
                    if (phase == Phase.PLAYING) abortReason = "the bot died";
                }
            } else if (packet instanceof ClientboundSystemChatPacket chat) {
                if (!chat.isOverlay() && LOG.isDebugEnabled()) LOG.debug("[{}] <server> {}", account.name, plain(chat.getContent()));
            }
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            onDisconnected(event);
        }
    }

    /** Teleport / setback from the server. Same reply as the vanilla client: confirm, then echo the position. */
    private void onServerPosition(ClientboundPlayerPositionPacket p) {
        var rel = p.getRelatives();
        double nx = rel.contains(PositionElement.X) ? x + p.getPosition().getX() : p.getPosition().getX();
        double ny = rel.contains(PositionElement.Y) ? y + p.getPosition().getY() : p.getPosition().getY();
        double nz = rel.contains(PositionElement.Z) ? z + p.getPosition().getZ() : p.getPosition().getZ();
        float nyaw = rel.contains(PositionElement.Y_ROT) ? yaw + p.getYRot() : p.getYRot();
        float npitch = rel.contains(PositionElement.X_ROT) ? pitch + p.getXRot() : p.getXRot();
        setPosition(nx, ny, nz, nyaw, npitch);

        send(pf.teleportConfirm(p.getId()));
        send(pf.posRot(false, false, nx, ny, nz, nyaw, npitch));

        boolean first = !positionKnown;
        positionKnown = true;
        if (phase == Phase.PLAYING) {
            int n = setbacks.incrementAndGet();
            LOG.warn("[{}] server moved the bot to {} {} {} (setback {}/{})", account.name, round(nx), round(ny), round(nz),
                    n, cfg.maxSetbacks);
            if (n > cfg.maxSetbacks) {
                abortReason = "the server corrected the bot's position " + n + " times; the replay has desynced";
            } else {
                resyncRequested = true;
            }
        } else if (first) {
            LOG.info("[{}] spawned at {} {} {}", account.name, round(nx), round(ny), round(nz));
        }
    }

    private void onDisconnected(DisconnectedEvent event) {
        cancelTicker();
        Throwable cause = event.getCause();
        String reason = describe(event.getReason(), cause);
        boolean wasDone = phase == Phase.DONE;

        if (expectedDisconnect || shuttingDown) {
            LOG.info("[{}] disconnected ({})", account.name, reason);
            if (phase != Phase.DONE && phase != Phase.FAILED) phase = wasDone ? Phase.DONE : Phase.FAILED;
            complete();
            return;
        }

        LOG.warn("[{}] disconnected: {}", account.name, reason);
        if (hasCause(cause, UnexpectedEncryptionException.class)) {
            fail("the server is in online mode, but this account has mode \"offline\". Use mode \"token\" with a real access token.");
            return;
        }
        if (wasDone) {
            // The server kicked us after the macro was over. Nothing left to do.
            complete();
            return;
        }
        if (attempt < cfg.reconnects) {
            attempt++;
            phase = Phase.CONNECTING;
            LOG.info("[{}] reconnecting in {} s ({}/{})", account.name, cfg.reconnectDelaySeconds, attempt, cfg.reconnects);
            executor.schedule(this::connect, cfg.reconnectDelaySeconds, TimeUnit.SECONDS);
        } else {
            phase = Phase.FAILED;
            complete();
        }
    }

    // ---- helpers
    private static String describe(Component reason, Throwable cause) {
        String r = reason == null ? "" : plain(reason);
        if (cause != null) {
            String c = cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
            return r.isBlank() ? c : r + " [" + c + "]";
        }
        return r.isBlank() ? "no reason given" : r;
    }

    static String plain(Component c) {
        StringBuilder sb = new StringBuilder();
        append(sb, c);
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private static void append(StringBuilder sb, Component c) {
        if (c instanceof TextComponent t) {
            sb.append(t.content());
        } else if (c instanceof TranslatableComponent tr) {
            sb.append(tr.key());
            List<Component> args = tr.args();
            if (!args.isEmpty()) {
                sb.append('(');
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) sb.append(", ");
                    append(sb, args.get(i));
                }
                sb.append(')');
            }
        }
        for (Component child : c.children()) append(sb, child);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (int depth = 0; t != null && depth < 8; depth++, t = t.getCause()) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }

    private static UUID parseUuid(String s) {
        String t = s.trim();
        if (t.length() == 32) {
            t = t.substring(0, 8) + "-" + t.substring(8, 12) + "-" + t.substring(12, 16) + "-" + t.substring(16, 20) + "-" + t.substring(20);
        }
        return UUID.fromString(t);
    }

    private static String round(double d) {
        return String.format("%.1f", d);
    }
}
