package io.github.chalsense.server.config;

import io.github.chalsense.store.redis.RedisKeyspace;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import redis.clients.jedis.RedisClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigurationTest {
    @Test
    void refusesNonLoopbackBindingUntilRateLimitIsEnabled() throws Exception {
        ServerConfiguration configuration = new ServerConfiguration();
        ChalSenseServerProperties properties = new ChalSenseServerProperties();
        MockEnvironment publicAddress = new MockEnvironment().withProperty("server.address", "0.0.0.0");
        try (RedisClient client = RedisClient.create("127.0.0.1", 1)) {
            assertThrows(IllegalArgumentException.class, () -> configuration.rateLimiter(
                    client, new RedisKeyspace(), properties, publicAddress));

            properties.getRateLimit().setEnabled(true);
            assertDoesNotThrow(() -> configuration.rateLimiter(
                    client, new RedisKeyspace(), properties, publicAddress));
        }
    }
}
