package io.github.chalsense.store.redis;

import redis.clients.jedis.RedisClient;

import java.util.List;
import java.util.Objects;

final class JedisResourceRedisCommands implements ResourceRedisCommands {
    private final RedisClient client;

    JedisResourceRedisCommands(RedisClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Object eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
        return client.eval(script, keys, args);
    }

    @Override
    public List<byte[]> hmget(byte[] key, byte[]... fields) {
        return client.hmget(key, fields);
    }

    @Override
    public long del(byte[] key) {
        return client.del(key);
    }
}
