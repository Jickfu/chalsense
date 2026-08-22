package io.github.chalsense.core.ratelimit;

import io.github.chalsense.protocol.SiteKey;

import java.util.Objects;
import java.util.Base64;

public record RateLimitRequest(
        SiteKey siteKey,
        RateLimitOperation operation,
        String clientKey,
        RateLimitPolicy clientPolicy,
        RateLimitPolicy sitePolicy) {
    public RateLimitRequest {
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(operation, "operation");
        if (clientKey == null || !clientKey.matches("[A-Za-z0-9_-]{22}") || !canonicalClientKey(clientKey)) {
            throw new IllegalArgumentException("clientKey must be a 128-bit Base64url value");
        }
        Objects.requireNonNull(clientPolicy, "clientPolicy");
        Objects.requireNonNull(sitePolicy, "sitePolicy");
    }

    private static boolean canonicalClientKey(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 16
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
