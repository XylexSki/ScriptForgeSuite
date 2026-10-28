package dev.scriptforge.headless;

import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.status.ServerStatusInfo;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** "check": pings the server through every configured proxy and, for token accounts, tests the route to Mojang. */
final class ProxyCheck {
    private static final int TIMEOUT_SECONDS = 15;

    private ProxyCheck() {}

    /** @return true when everything that was tested worked. */
    static boolean run(Config cfg) {
        Set<String> gameProxies = new LinkedHashSet<>();
        Set<String> authProxies = new LinkedHashSet<>();
        for (String p : cfg.proxies) gameProxies.add(p);
        for (Config.Account a : cfg.accounts) {
            if (a.proxy != null) gameProxies.add(a.proxy);
            if (a.mode.equals("token")) {
                String game = a.proxy != null ? a.proxy : cfg.proxies.isEmpty() ? "none" : cfg.proxies.get(0);
                authProxies.add(a.authProxy != null ? a.authProxy : cfg.authProxy != null ? cfg.authProxy : game);
            }
        }
        if (gameProxies.isEmpty()) gameProxies.add("none");

        boolean ok = true;
        System.out.println("Server " + cfg.host() + ":" + cfg.port());
        for (String spec : gameProxies) {
            String label = String.format("  game  %-48s", ProxySpec.redact(spec));
            long t0 = System.nanoTime();
            try {
                ServerStatusInfo info = ping(cfg, spec);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                String version = info.getVersionInfo() == null ? "?" : info.getVersionInfo().getVersionName();
                String players = info.getPlayerInfo() == null ? "?"
                        : info.getPlayerInfo().getOnlinePlayers() + "/" + info.getPlayerInfo().getMaxPlayers();
                System.out.println(label + " OK  " + ms + " ms  version " + version + "  players " + players
                        + (info.isEnforcesSecureChat() ? "  [server enforces secure chat: unsigned chat will be rejected]" : ""));
            } catch (Exception e) {
                ok = false;
                System.out.println(label + " FAILED  " + rootMessage(e));
            }
        }
        for (String spec : authProxies) {
            String label = String.format("  mojang %-47s", ProxySpec.redact(spec));
            try {
                SessionService service = new SessionService();
                service.setProxy(ProxySpec.parse(spec));
                service.getProfileByServer("ScriptForgeCheck", "0"); // answers "no such session": proves the route works
                System.out.println(label + " OK");
            } catch (Exception e) {
                ok = false;
                System.out.println(label + " FAILED  " + rootMessage(e)
                        + "  (online-mode accounts cannot log in through this route; try authProxy: \"none\" or another proxy)");
            }
        }
        return ok;
    }

    private static ServerStatusInfo ping(Config cfg, String proxySpec) throws Exception {
        CompletableFuture<ServerStatusInfo> result = new CompletableFuture<>();
        ClientNetworkSession session = ClientNetworkSessionFactory.factory()
                .setAddress(cfg.host(), cfg.port())
                .setProtocol(new MinecraftProtocol()) // no name: this is the server-list (status) protocol
                .setProxy(ProxySpec.parse(proxySpec))
                .create();
        session.setFlag(MinecraftConstants.SERVER_INFO_HANDLER_KEY, (s, info) -> result.complete(info));
        session.addListener(new SessionAdapter() {
            @Override
            public void disconnected(DisconnectedEvent event) {
                if (result.isDone()) return;
                Throwable cause = event.getCause();
                Component reason = event.getReason();
                result.completeExceptionally(cause != null ? cause
                        : new IllegalStateException(reason == null ? "disconnected" : Bot.plain(reason)));
            }
        });
        try {
            session.connect(false);
            return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("no answer after " + TIMEOUT_SECONDS + " s");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        } finally {
            if (session.isConnected()) session.disconnect("check finished");
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        for (int i = 0; i < 8 && r.getCause() != null; i++) r = r.getCause();
        String m = r.getMessage();
        return r.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }
}
