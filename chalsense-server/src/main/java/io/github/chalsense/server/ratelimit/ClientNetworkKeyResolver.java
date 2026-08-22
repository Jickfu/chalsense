package io.github.chalsense.server.ratelimit;

import io.github.chalsense.server.config.ChalSenseServerProperties;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;

public final class ClientNetworkKeyResolver {
    private static final int MAXIMUM_FORWARDED_LENGTH = 2048;
    private static final int MAXIMUM_HOPS = 16;
    private final List<NetworkCidr> trustedProxies;
    private final byte[] hmacKey;

    public ClientNetworkKeyResolver(ChalSenseServerProperties.RateLimit configuration) {
        trustedProxies = configuration.getTrustedProxyCidrs().stream().map(NetworkCidr::parse).toList();
        try {
            hmacKey = Base64.getUrlDecoder().decode(configuration.getHmacKey());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("rate-limit hmac-key must be canonical Base64url", exception);
        }
        if (hmacKey.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(hmacKey)
                .equals(configuration.getHmacKey())) {
            throw new IllegalArgumentException("rate-limit hmac-key must encode exactly 32 bytes");
        }
    }

    public String resolve(HttpServletRequest request) {
        InetAddress peer = IpLiterals.parse(request.getRemoteAddr());
        InetAddress client = trusted(peer) ? forwardedClient(request, peer) : peer;
        byte[] address = client.getAddress().clone();
        if (client instanceof Inet6Address) java.util.Arrays.fill(address, 8, 16, (byte) 0);
        byte[] input = new byte[address.length + 1];
        input[0] = (byte) address.length;
        System.arraycopy(address, 0, input, 1, address.length);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(input);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(java.util.Arrays.copyOf(digest, 16));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private InetAddress forwardedClient(HttpServletRequest request, InetAddress peer) {
        List<String> hops = forwardedHops(request);
        InetAddress current = peer;
        for (int index = hops.size() - 1; index >= 0; index--) {
            if (!trusted(current)) return current;
            InetAddress candidate = IpLiterals.parse(hops.get(index));
            current = candidate;
            if (!trusted(candidate)) return candidate;
        }
        return current;
    }

    private static List<String> forwardedHops(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        List<String> hops = new ArrayList<>();
        int length = 0;
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            length += header.length();
            if (length > MAXIMUM_FORWARDED_LENGTH) throw new IllegalArgumentException("X-Forwarded-For is too long");
            for (String hop : header.split(",", -1)) {
                if (hop.isEmpty() || !hop.equals(hop.trim()) && hop.trim().isEmpty()) {
                    throw new IllegalArgumentException("X-Forwarded-For contains an empty hop");
                }
                hops.add(hop.trim());
                if (hops.size() > MAXIMUM_HOPS) throw new IllegalArgumentException("X-Forwarded-For has too many hops");
            }
        }
        return hops;
    }

    private boolean trusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(cidr -> cidr.contains(address));
    }
}
