package dev.scriptforge.headless;

import dev.scriptforge.macro.Macro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates one Bot per account, hands out proxies, staggers the logins and waits until every bot is finished. */
final class BotManager {
    private static final Logger LOG = LoggerFactory.getLogger(BotManager.class);

    private final Config cfg;
    private final Macro macro;
    private final List<Bot> bots = new ArrayList<>();
    private ScheduledExecutorService executor;

    BotManager(Config cfg, Macro macro) {
        this.cfg = cfg;
        this.macro = macro;
        assignProxies();
    }

    /** Proxy for each account: its own "proxy" if set, otherwise the next one from the pool (round-robin). */
    private void assignProxies() {
        AtomicInteger next = new AtomicInteger();
        int direct = 0;
        for (Config.Account a : cfg.accounts) {
            String proxy;
            if (a.proxy != null) {
                proxy = a.proxy;
            } else if (!cfg.proxies.isEmpty()) {
                proxy = cfg.proxies.get(next.getAndIncrement() % cfg.proxies.size());
            } else {
                proxy = null;
            }
            // Fail on a malformed proxy now, not halfway through a staggered login.
            ProxySpec.parse(proxy);

            String auth = a.authProxy != null ? a.authProxy : cfg.authProxy != null ? cfg.authProxy : proxy;
            ProxySpec.parse(auth);

            if (ProxySpec.isNone(proxy)) direct++;
            LOG.info("account {} -> {}", a.name, ProxySpec.redact(proxy));
            bots.add(new Bot(cfg, a, macro, proxy, auth));
        }
        if (direct > 0) {
            LOG.warn("{} of {} account(s) connect directly from this machine's IP address.", direct, cfg.accounts.size());
        }
        if (!cfg.proxies.isEmpty() && cfg.accounts.size() > cfg.proxies.size()) {
            LOG.warn("{} accounts share {} proxies: several accounts will appear from the same IP.",
                    cfg.accounts.size(), cfg.proxies.size());
        }
    }

    void run() {
        int threads = Math.max(2, Math.min(16, bots.size() / 4 + 2));
        executor = Executors.newScheduledThreadPool(threads, r -> {
            Thread t = new Thread(r, "sf-bot");
            t.setDaemon(true);
            return t;
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Bot b : bots) b.shutdown();
            try {
                Thread.sleep(500); // let the disconnect packets leave
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "sf-shutdown"));

        for (int i = 0; i < bots.size(); i++) {
            Bot bot = bots.get(i);
            executor.schedule(() -> {
                try {
                    bot.start(executor);
                } catch (Throwable t) {
                    LOG.error("[{}] failed to start", bot.name(), t);
                    bot.shutdown();
                }
            }, (long) i * cfg.joinDelayMs, TimeUnit.MILLISECONDS);
        }

        executor.scheduleWithFixedDelay(this::printStatus, 30, 30, TimeUnit.SECONDS);

        CompletableFuture<?>[] all = bots.stream().map(Bot::finished).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(all).join();

        printStatus();
        executor.shutdownNow();
    }

    private void printStatus() {
        StringBuilder sb = new StringBuilder("status:");
        for (Bot b : bots) sb.append("\n  ").append(b.status());
        LOG.info("{}", sb);
    }
}
