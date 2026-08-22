package io.github.chalsense.core.ratelimit;

/** One token per interval with a bounded burst. */
public record RateLimitPolicy(int burst, long intervalMillis) {
    private static final long MAXIMUM_IDLE_TTL = 86_400_000L;

    public RateLimitPolicy {
        if (burst < 1 || burst > 1_000_000) throw new IllegalArgumentException("burst must be between 1 and 1000000");
        if (intervalMillis < 1 || intervalMillis > 86_400_000L) {
            throw new IllegalArgumentException("intervalMillis must be between 1 and 86400000");
        }
        long idleTtl = Math.multiplyExact((long) burst + 1L, intervalMillis);
        if (idleTtl > MAXIMUM_IDLE_TTL) {
            throw new IllegalArgumentException("rate limit bucket idle TTL must not exceed 24 hours");
        }
    }

    public long idleTtlMillis() {
        return Math.multiplyExact((long) burst + 1L, intervalMillis);
    }
}
