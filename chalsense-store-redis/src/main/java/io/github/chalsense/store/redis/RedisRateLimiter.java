package io.github.chalsense.store.redis;

import io.github.chalsense.core.ratelimit.RateLimitPolicy;
import io.github.chalsense.core.ratelimit.RateLimitRequest;
import io.github.chalsense.core.ratelimit.RateLimitResult;
import io.github.chalsense.core.ratelimit.RateLimiter;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Atomic two-scope GCRA limiter using Redis server time. */
public final class RedisRateLimiter implements RateLimiter {
    private static final byte[] SCRIPT = ("local tm=redis.call('TIME') "
            + "local now=tm[1]*1000+math.floor(tm[2]/1000) "
            + "local function inspect(key,interval,burst) "
            + " local tat=tonumber(redis.call('GET',key)) or now "
            + " if tat<now then tat=now end "
            + " local next=tat+interval local limit=now+burst*interval "
            + " if next>limit then return {0,next-limit,0} end "
            + " return {1,0,next} end "
            + "local c=inspect(KEYS[1],tonumber(ARGV[1]),tonumber(ARGV[2])) "
            + "local s=inspect(KEYS[2],tonumber(ARGV[3]),tonumber(ARGV[4])) "
            + "if c[1]==0 or s[1]==0 then return {0,math.max(c[2],s[2])} end "
            + "redis.call('SET',KEYS[1],c[3],'PX',ARGV[5]) "
            + "redis.call('SET',KEYS[2],s[3],'PX',ARGV[6]) return {1,0}")
            .getBytes(StandardCharsets.US_ASCII);

    private final RateLimitRedisCommands commands;
    private final RedisKeyspace keyspace;

    public RedisRateLimiter(RedisClient client, RedisKeyspace keyspace) {
        this(new JedisRateLimitRedisCommands(client), keyspace);
    }

    RedisRateLimiter(RateLimitRedisCommands commands, RedisKeyspace keyspace) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.keyspace = Objects.requireNonNull(keyspace, "keyspace");
    }

    @Override
    public RateLimitResult acquire(RateLimitRequest request) {
        Objects.requireNonNull(request, "request");
        RateLimitPolicy client = request.clientPolicy();
        RateLimitPolicy site = request.sitePolicy();
        try {
            Object raw = commands.eval(SCRIPT, List.of(
                    keyspace.rateLimitClientKey(request.siteKey(), request.operation(), request.clientKey()),
                    keyspace.rateLimitSiteKey(request.siteKey(), request.operation())), List.of(
                    ascii(client.intervalMillis()), ascii(client.burst()),
                    ascii(site.intervalMillis()), ascii(site.burst()),
                    ascii(client.idleTtlMillis()), ascii(site.idleTtlMillis())));
            if (!(raw instanceof List<?> values) || values.size() != 2
                    || !(values.get(0) instanceof Long allowed) || !(values.get(1) instanceof Long retry)) {
                return new RateLimitResult.Unavailable();
            }
            if (allowed == 1L && retry == 0L) return new RateLimitResult.Allowed();
            if (allowed == 0L && retry > 0L) return new RateLimitResult.Limited(retry);
            return new RateLimitResult.Unavailable();
        } catch (JedisException | ArithmeticException exception) {
            return new RateLimitResult.Unavailable();
        }
    }

    private static byte[] ascii(long value) {
        return Long.toString(value).getBytes(StandardCharsets.US_ASCII);
    }
}
