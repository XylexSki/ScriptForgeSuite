package dev.scriptforge.headless;

import org.geysermc.mcprotocollib.network.ProxyInfo;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Parses proxy URLs like socks5://user:pass@1.2.3.4:1080 into MCProtocolLib ProxyInfo objects. */
public final class ProxySpec {
    private ProxySpec() {}

    public static boolean isNone(String spec) {
        return spec == null || spec.isBlank() || spec.equalsIgnoreCase("none") || spec.equalsIgnoreCase("direct");
    }

    public static ProxyInfo parse(String spec) {
        if (isNone(spec)) return null;
        URI u = URI.create(spec.trim());
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        ProxyInfo.Type type = switch (scheme) {
            case "socks5", "socks5h", "socks" -> ProxyInfo.Type.SOCKS5;
            case "socks4", "socks4a" -> ProxyInfo.Type.SOCKS4;
            case "http", "https" -> ProxyInfo.Type.HTTP;
            default -> throw new IllegalArgumentException("Unsupported proxy scheme in '" + redact(spec)
                    + "' (use socks5://, socks4:// or http://)");
        };
        if (u.getHost() == null || u.getPort() < 0) {
            throw new IllegalArgumentException("Proxy must look like scheme://[user:pass@]host:port, got '" + redact(spec) + "'");
        }
        InetSocketAddress address = new InetSocketAddress(u.getHost(), u.getPort());
        String info = u.getRawUserInfo();
        if (info == null) return new ProxyInfo(type, address);
        int c = info.indexOf(':');
        String user = decode(c < 0 ? info : info.substring(0, c));
        String pass = c < 0 ? "" : decode(info.substring(c + 1));
        return new ProxyInfo(type, address, user, pass);
    }

    /** Safe for logs: hides the password. */
    public static String redact(String spec) {
        if (isNone(spec)) return "direct";
        return spec.replaceAll("://([^:@/]+):[^@/]*@", "://$1:***@");
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
