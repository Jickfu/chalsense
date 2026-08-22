package io.github.chalsense.store.redis;

import java.util.List;

interface ResourceRedisCommands {
    Object eval(byte[] script, List<byte[]> keys, List<byte[]> args);
    List<byte[]> hmget(byte[] key, byte[]... fields);
    long del(byte[] key);
}
