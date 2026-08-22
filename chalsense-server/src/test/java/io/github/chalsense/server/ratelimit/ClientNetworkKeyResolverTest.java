package io.github.chalsense.server.ratelimit;

import io.github.chalsense.server.config.ChalSenseServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientNetworkKeyResolverTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        ClientNetworkKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest forged = request("203.0.113.9", "not-an-ip");
        MockHttpServletRequest direct = request("203.0.113.9", null);
        assertEquals(resolver.resolve(direct), resolver.resolve(forged));
    }

    @Test
    void walksTrustedChainFromRightToLeft() {
        ClientNetworkKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest proxied = request("10.0.0.2", "198.51.100.7, 10.0.0.1");
        assertEquals(resolver.resolve(request("198.51.100.7", null)), resolver.resolve(proxied));
    }

    @Test
    void rejectsMalformedTrustedForwardingAndNormalizesIpv6ToSlash64() {
        ClientNetworkKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(request("10.0.0.2", "unknown")));
        assertEquals(resolver.resolve(request("2001:db8:1:2::1", null)),
                resolver.resolve(request("2001:db8:1:2::ffff", null)));
        assertNotEquals(resolver.resolve(request("2001:db8:1:2::1", null)),
                resolver.resolve(request("2001:db8:1:3::1", null)));
    }

    @Test
    void rejectsNonNetworkCidrAndNonCanonicalKey() {
        assertThrows(IllegalArgumentException.class, () -> resolver(List.of("10.0.0.1/8")));
        ChalSenseServerProperties.RateLimit config = new ChalSenseServerProperties.RateLimit();
        config.setHmacKey("bad");
        assertThrows(IllegalArgumentException.class, () -> new ClientNetworkKeyResolver(config));
    }

    private static ClientNetworkKeyResolver resolver(List<String> cidrs) {
        ChalSenseServerProperties.RateLimit config = new ChalSenseServerProperties.RateLimit();
        config.setHmacKey(KEY);
        config.setTrustedProxyCidrs(cidrs);
        return new ClientNetworkKeyResolver(config);
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
