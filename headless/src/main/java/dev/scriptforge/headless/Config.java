package dev.scriptforge.headless;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** headless.json */
public final class Config {
    /** "host" or "host:port". */
    public String server = "localhost:25565";
    /** Path to a .sfmacro recorded with the ScriptForge mod (relative paths resolve against the config file). */
    public String macro;

    /** "relative": replay from wherever each bot stands. "absolute": use the recorded coordinates as they are. */
    public String mode = "relative";
    /** How many times to replay the macro. 0 = forever. */
    public int loops = 1;
    public int loopDelayTicks = 40;

    /** Ticks to wait after joining before doing anything (the server needs a moment to finish loading the player). */
    public int startDelayTicks = 100;
    /** Ticks to wait after the last join command (e.g. a /home teleport) before the macro starts. */
    public int settleTicks = 40;
    /** Delay between two accounts connecting, in milliseconds. */
    public long joinDelayMs = 5000;

    /** Stop a bot's macro when the server has corrected its position this many times (it means the replay desynced). */
    public int maxSetbacks = 3;
    public boolean disconnectWhenDone = true;
    /** Reconnect this many times after an unexpected disconnect. */
    public int reconnects = 0;
    public int reconnectDelaySeconds = 30;

    public boolean replayChat = true;
    public boolean replayCommands = true;

    /** Chat messages/commands every account sends after joining and after its own joinCommands (e.g. /home farm). */
    public List<Step> joinCommands = new ArrayList<>();

    /** Proxy pool. Accounts without their own "proxy" are assigned from here round-robin. */
    public List<String> proxies = new ArrayList<>();
    /**
     * Proxy for Mojang's session server (online-mode authentication). Default: the same proxy as the game connection,
     * so one account always shows one IP. Set "none" for direct.
     */
    public String authProxy;

    public List<Account> accounts = new ArrayList<>();

    public static final class Step {
        public String text;
        /** Ticks to wait before sending this step. */
        public int delayTicks = 20;
    }

    public static final class Account {
        public String name;
        /** "offline" (offline-mode servers) or "token" (online-mode, you supply uuid + accessToken). */
        public String mode = "offline";
        public String uuid;
        /** The token itself, or "env:VARIABLE_NAME" to read it from an environment variable (recommended). */
        public String accessToken;
        /** "socks5://user:pass@host:port", "socks4://host:port", "http://host:port", or "none" for a direct connection. */
        public String proxy;
        public String authProxy;
        /** Per-account join commands, sent before the global ones (put /login here, then the shared /home goes in the global list). */
        public List<Step> joinCommands = new ArrayList<>();

        public String resolvedToken() {
            if (accessToken == null) return null;
            if (accessToken.startsWith("env:")) {
                String v = System.getenv(accessToken.substring(4));
                if (v == null) throw new IllegalStateException("Environment variable not set: " + accessToken.substring(4));
                return v;
            }
            return accessToken;
        }
    }

    public String host() {
        int i = server.lastIndexOf(':');
        return i > 0 && server.indexOf(']') < i ? server.substring(0, i) : server;
    }

    public int port() {
        int i = server.lastIndexOf(':');
        if (i > 0 && server.indexOf(']') < i) return Integer.parseInt(server.substring(i + 1).trim());
        return 25565;
    }

    private static final Pattern OFFLINE_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public static Config load(Path file) throws IOException {
        Gson gson = new GsonBuilder().setLenient().create();
        Config c;
        try (Reader r = Files.newBufferedReader(file)) {
            c = gson.fromJson(r, Config.class);
        }
        if (c == null) throw new IOException("Empty config: " + file);
        c.validate();
        return c;
    }

    /** Where the macro file is, resolved against the config's folder when relative. */
    public Path macroPath(Path configFile) {
        Path p = Path.of(macro);
        if (p.isAbsolute()) return p;
        Path base = configFile.toAbsolutePath().getParent();
        return base == null ? p : base.resolve(p);
    }

    private void validate() {
        if (macro == null || macro.isBlank()) throw new IllegalArgumentException("config: \"macro\" is required");
        if (!mode.equals("relative") && !mode.equals("absolute")) {
            throw new IllegalArgumentException("config: mode must be \"relative\" or \"absolute\"");
        }
        if (accounts.isEmpty()) throw new IllegalArgumentException("config: no accounts");
        for (Account a : accounts) {
            if (a.name == null || !OFFLINE_NAME.matcher(a.name).matches()) {
                throw new IllegalArgumentException("config: invalid account name '" + a.name + "' (3-16 letters, digits, _)");
            }
            if (a.mode.equals("token") && (a.uuid == null || a.accessToken == null)) {
                throw new IllegalArgumentException("config: account " + a.name + " uses mode \"token\" but has no uuid/accessToken");
            }
            if (!a.mode.equals("token") && !a.mode.equals("offline")) {
                throw new IllegalArgumentException("config: account " + a.name + ": mode must be \"offline\" or \"token\"");
            }
        }
    }
}
