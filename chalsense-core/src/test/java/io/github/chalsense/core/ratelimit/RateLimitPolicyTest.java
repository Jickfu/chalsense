package io.github.chalsense.core.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitPolicyTest {
    @Test
    void boundsBurstIntervalAndPrivacyTtl() {
        assertEquals(72_000, new RateLimitPolicy(5, 12_000).idleTtlMillis());
        assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(1000, 86_400));
    }
}
