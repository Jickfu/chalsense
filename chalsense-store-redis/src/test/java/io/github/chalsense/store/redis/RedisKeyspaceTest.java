package io.github.chalsense.store.redis;

import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.core.ratelimit.RateLimitOperation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisKeyspaceTest {
    @Test
    void createsDeterministicAsciiKeysWithoutRawTicketMaterial() {
        RedisKeyspace keyspace = new RedisKeyspace("chalsense.prod");
        assertEquals(
                "chalsense.prod:v1:challenge:site_test:AAAAAAAAAAAAAAAAAAAAAA",
                ascii(keyspace.challengeKey(
                        new SiteKey("site_test"), new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA"))));
        assertEquals(
                "chalsense.prod:v1:ticket:"
                        + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                ascii(keyspace.ticketKey(new TicketDigest(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))));
        assertEquals(
                "chalsense.prod:v1:resource:{AAAAAAAAAAAAAAAAAAAAAA}",
                ascii(keyspace.resourceKey("AAAAAAAAAAAAAAAAAAAAAA")));
        assertEquals("chalsense.prod:v1:rate:{site_test}:create:client:AAAAAAAAAAAAAAAAAAAAAA",
                ascii(keyspace.rateLimitClientKey(new SiteKey("site_test"), RateLimitOperation.CREATE,
                        "AAAAAAAAAAAAAAAAAAAAAA")));
        assertEquals("chalsense.prod:v1:rate:{site_test}:verify:site",
                ascii(keyspace.rateLimitSiteKey(new SiteKey("site_test"), RateLimitOperation.VERIFY)));
    }

    @Test
    void enforcesApprovedNamespaceSyntaxAndLength() {
        assertEquals(RedisKeyspace.DEFAULT_NAMESPACE, new RedisKeyspace().namespace());
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyspace(""));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyspace("chalsense:prod"));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyspace("{chalsense}"));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyspace("测试"));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyspace("a".repeat(65)));
        assertThrows(IllegalArgumentException.class,
                () -> new RedisKeyspace().resourceKey("BBBBBBBBBBBBBBBBBBBBBB"));
    }

    private static String ascii(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII);
    }
}
