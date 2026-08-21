package io.github.chalsense.core.challenge;

import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;

import java.util.Objects;

public record ChallengeGenerationRequest(
        SiteRegistration site,
        ActionName action,
        ChallengeId challengeId,
        long issuedAt,
        long expiresAt) {
    public ChallengeGenerationRequest {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(challengeId, "challengeId");
        if (issuedAt < 0 || expiresAt <= issuedAt) {
            throw new IllegalArgumentException("invalid generation time range");
        }
    }
}
