package io.github.chalsense.store.redis;

import redis.clients.jedis.RedisClient;

import java.util.List;
import java.util.Objects;

final class JedisRateLimitRedisCommands implements RateLimitRedisCommands {
    private final RedisClient client;

    JedisRateLimitRedisCommands(RedisClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Object eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
        return client.eval(script, keys, args);
    }
}
