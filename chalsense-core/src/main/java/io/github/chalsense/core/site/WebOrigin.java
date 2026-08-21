package io.github.chalsense.core.site;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A canonical http/https browser origin containing only scheme, host and optional non-default port. */
public final class WebOrigin {
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern IPV4_CANDIDATE = Pattern.compile("[0-9.]+");

    private final String value;
    private final String scheme;
    private final String host;
    private final int port;
    private final boolean loopback;

    private WebOrigin(String value, String scheme, String host, int port, boolean loopback) {
        this.value = value;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.loopback = loopback;
    }

    public static WebOrigin parse(String input) {
        Objects.requireNonNull(input, "input");
        if (input.isEmpty() || !input.equals(input.trim()) || !input.chars().allMatch(character -> character <= 0x7f)) {
            throw new IllegalArgumentException("origin must be non-empty ASCII without surrounding whitespace");
        }

        URI uri;
        try {
            uri = new URI(input);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("origin has invalid URI syntax", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https"))
                || uri.getRawAuthority() == null
                || uri.getRawUserInfo() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("origin must contain only http/https scheme, host and port");
        }

        String uriHost = uri.getHost();
        if (uriHost == null || uriHost.isEmpty() || uriHost.endsWith(".") || uriHost.contains("%")) {
            throw new IllegalArgumentException("origin host is missing or non-canonical");
        }
        String host = canonicalHost(uriHost);
        int port = uri.getPort();
        if (port == 0 || port < -1 || port > 65_535) {
            throw new IllegalArgumentException("origin port is outside the valid range");
        }
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
            port = -1;
        }

        boolean loopback = isLoopback(host);
        String authorityHost = host.contains(":") ? "[" + host + "]" : host;
        String value = scheme + "://" + authorityHost + (port == -1 ? "" : ":" + port);
        return new WebOrigin(value, scheme, host, port, loopback);
    }

    public String value() {
        return value;
    }

    public String scheme() {
        return scheme;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean isLoopback() {
        return loopback;
    }

    public boolean isSecure() {
        return scheme.equals("https");
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof WebOrigin origin && value.equals(origin.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    private static String canonicalHost(String input) {
        String host = input.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.contains(":")) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (!(address instanceof Inet6Address)) {
                    throw new IllegalArgumentException("origin IPv6 host is invalid");
                }
                return address.getHostAddress().toLowerCase(Locale.ROOT);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("origin IPv6 host is invalid", exception);
            }
        }
        if (IPV4_CANDIDATE.matcher(host).matches()) {
            return canonicalIpv4(host);
        }

        String ascii;
        try {
            ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("origin DNS host is invalid", exception);
        }
        if (!ascii.equals(host)) {
            throw new IllegalArgumentException("internationalized origin hosts must be configured as ASCII punycode");
        }
        String[] labels = ascii.split("\\.", -1);
        if (ascii.length() > 253 || labels.length == 0) {
            throw new IllegalArgumentException("origin DNS host is invalid");
        }
        for (String label : labels) {
            if (!DNS_LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("origin DNS host is invalid");
            }
        }
        return ascii;
    }

    private static String canonicalIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("origin IPv4 host is invalid");
        }
        StringBuilder canonical = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || (part.length() > 1 && part.startsWith("0"))) {
                throw new IllegalArgumentException("origin IPv4 host is non-canonical");
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("origin IPv4 host is invalid", exception);
            }
            if (value > 255) {
                throw new IllegalArgumentException("origin IPv4 host is invalid");
            }
            if (index > 0) {
                canonical.append('.');
            }
            canonical.append(value);
        }
        return canonical.toString();
    }

    private static boolean isLoopback(String host) {
        if (host.equals("localhost")) {
            return true;
        }
        if (host.contains(":")) {
            try {
                return InetAddress.getByName(host).isLoopbackAddress();
            } catch (UnknownHostException exception) {
                return false;
            }
        }
        if (IPV4_CANDIDATE.matcher(host).matches()) {
            return host.startsWith("127.");
        }
        return false;
    }
}

