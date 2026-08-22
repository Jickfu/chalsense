package io.github.chalsense.core.ratelimit;

@FunctionalInterface
public interface RateLimiter {
    RateLimitResult acquire(RateLimitRequest request);
}
