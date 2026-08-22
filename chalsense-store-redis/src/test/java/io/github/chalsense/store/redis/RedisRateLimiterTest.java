package io.github.chalsense.store.redis;

import io.github.chalsense.core.ratelimit.RateLimitOperation;
import io.github.chalsense.core.ratelimit.RateLimitPolicy;
import io.github.chalsense.core.ratelimit.RateLimitRequest;
import io.github.chalsense.core.ratelimit.RateLimitResult;
import io.github.chalsense.protocol.SiteKey;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisRateLimiterTest {
    @Test
    void sendsTwoSameSlotKeysAndMapsAllowedAndLimited() {
        FakeCommands commands = new FakeCommands(List.of(1L, 0L));
        RedisRateLimiter limiter = new RedisRateLimiter(commands, new RedisKeyspace("test"));
        assertInstanceOf(RateLimitResult.Allowed.class, limiter.acquire(request()));
        assertEquals(2, commands.keys.size());
        assertTrue(ascii(commands.keys.get(0)).contains("rate:{site_test}:create:client:"));
        assertTrue(ascii(commands.keys.get(1)).endsWith("rate:{site_test}:create:site"));

        commands.result = List.of(0L, 1250L);
        RateLimitResult.Limited limited = assertInstanceOf(RateLimitResult.Limited.class,
                limiter.acquire(request()));
        assertEquals(1250, limited.retryAfterMillis());
    }

    @Test
    void failsClosedOnUnknownResponseOrConnectionFailure() {
        FakeCommands commands = new FakeCommands(null);
        RedisRateLimiter limiter = new RedisRateLimiter(commands, new RedisKeyspace());
        assertInstanceOf(RateLimitResult.Unavailable.class, limiter.acquire(request()));
        commands.failure = new JedisConnectionException("lost");
        assertInstanceOf(RateLimitResult.Unavailable.class, limiter.acquire(request()));
    }

    private static RateLimitRequest request() {
        return new RateLimitRequest(new SiteKey("site_test"), RateLimitOperation.CREATE,
                "AAAAAAAAAAAAAAAAAAAAAA", new RateLimitPolicy(5, 1000), new RateLimitPolicy(100, 100));
    }

    private static String ascii(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static final class FakeCommands implements RateLimitRedisCommands {
        private Object result;
        private RuntimeException failure;
        private List<byte[]> keys;

        private FakeCommands(Object result) { this.result = result; }

        @Override
        public Object eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
            if (failure != null) throw failure;
            this.keys = keys;
            return result;
        }
    }
}
