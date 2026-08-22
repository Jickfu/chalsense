package io.github.chalsense.store.redis;

import java.util.List;

interface RateLimitRedisCommands {
    Object eval(byte[] script, List<byte[]> keys, List<byte[]> args);
}
