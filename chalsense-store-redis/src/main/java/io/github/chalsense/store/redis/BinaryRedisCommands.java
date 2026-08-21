package io.github.chalsense.store.redis;

import redis.clients.jedis.params.SetParams;

interface BinaryRedisCommands {
    String set(byte[] key, byte[] value, SetParams params);

    byte[] getDel(byte[] key);
}
