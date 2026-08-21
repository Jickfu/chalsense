package io.github.chalsense.store.redis;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

import java.util.Objects;

final class JedisBinaryRedisCommands implements BinaryRedisCommands {
    private final UnifiedJedis client;

    JedisBinaryRedisCommands(UnifiedJedis client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public String set(byte[] key, byte[] value, SetParams params) {
        return client.set(key, value, params);
    }

    @Override
    public byte[] getDel(byte[] key) {
        return client.getDel(key);
    }
}
