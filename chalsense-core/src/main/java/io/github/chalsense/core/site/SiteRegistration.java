package io.github.chalsense.core.site;

import io.github.chalsense.protocol.SiteKey;

import java.util.Objects;

/** Site configuration used by Core security checks; displayName is operational metadata only. */
public record SiteRegistration(
        SiteKey siteKey,
        String displayName,
        SiteStatus status,
        SitePolicy policy) {
    public SiteRegistration {
        Objects.requireNonNull(siteKey, "siteKey");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policy, "policy");
    }
}

