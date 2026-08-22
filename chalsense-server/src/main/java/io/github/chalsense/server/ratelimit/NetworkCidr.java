package io.github.chalsense.server.ratelimit;

import java.net.InetAddress;
import java.util.Arrays;

final class NetworkCidr {
    private final byte[] network;
    private final int prefixLength;

    private NetworkCidr(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    static NetworkCidr parse(String value) {
        if (value == null || !value.equals(value.trim()) || value.chars().filter(c -> c == '/').count() != 1) {
            throw new IllegalArgumentException("trusted proxy CIDR is invalid");
        }
        int slash = value.indexOf('/');
        byte[] address = IpLiterals.parse(value.substring(0, slash)).getAddress();
        int prefix;
        try { prefix = Integer.parseInt(value.substring(slash + 1)); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("trusted proxy CIDR is invalid", exception); }
        if (prefix < 0 || prefix > address.length * 8) throw new IllegalArgumentException("trusted proxy CIDR prefix is invalid");
        byte[] masked = mask(address, prefix);
        if (!Arrays.equals(masked, address)) throw new IllegalArgumentException("trusted proxy CIDR must use its network address");
        return new NetworkCidr(masked, prefix);
    }

    boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        return candidate.length == network.length && Arrays.equals(mask(candidate, prefixLength), network);
    }

    private static byte[] mask(byte[] source, int prefix) {
        byte[] result = source.clone();
        int fullBytes = prefix / 8;
        int remainder = prefix % 8;
        if (remainder != 0) result[fullBytes] &= (byte) (0xff << (8 - remainder));
        int firstZero = fullBytes + (remainder == 0 ? 0 : 1);
        Arrays.fill(result, firstZero, result.length, (byte) 0);
        return result;
    }
}
