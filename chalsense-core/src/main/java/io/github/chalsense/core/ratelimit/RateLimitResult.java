package io.github.chalsense.core.ratelimit;

public sealed interface RateLimitResult permits RateLimitResult.Allowed, RateLimitResult.Limited,
        RateLimitResult.Unavailable {
    record Allowed() implements RateLimitResult {}

    record Limited(long retryAfterMillis) implements RateLimitResult {
        public Limited {
            if (retryAfterMillis < 1) throw new IllegalArgumentException("retryAfterMillis must be positive");
        }
    }

    record Unavailable() implements RateLimitResult {}
}
