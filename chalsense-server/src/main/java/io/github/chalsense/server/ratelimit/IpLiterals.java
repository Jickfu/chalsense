package io.github.chalsense.server.ratelimit;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

final class IpLiterals {
    private IpLiterals() {}

    static InetAddress parse(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.trim()) || value.contains("%")
                || value.startsWith("[") || value.endsWith("]")) {
            throw new IllegalArgumentException("IP literal is invalid");
        }
        if (value.indexOf(':') >= 0) return ipv6(value);
        return ipv4(value);
    }

    private static InetAddress ipv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("IPv4 literal is invalid");
        byte[] bytes = new byte[4];
        for (int index = 0; index < 4; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3 || part.length() > 1 && part.startsWith("0")
                    || !part.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("IPv4 literal is non-canonical");
            }
            int number = Integer.parseInt(part);
            if (number > 255) throw new IllegalArgumentException("IPv4 literal is invalid");
            bytes[index] = (byte) number;
        }
        try { return InetAddress.getByAddress(bytes); }
        catch (UnknownHostException exception) { throw new IllegalStateException(exception); }
    }

    private static InetAddress ipv6(String value) {
        if (!value.matches("[0-9A-Fa-f:.]+")) throw new IllegalArgumentException("IPv6 literal is invalid");
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet6Address)) throw new IllegalArgumentException("IPv6 literal is invalid");
            return address;
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("IPv6 literal is invalid", exception);
        }
    }
}
