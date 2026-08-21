package io.github.chalsense.core.challenge;

import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ChallengeType;

import java.util.List;
import java.util.Objects;

public record CreatedChallenge(
        ChallengeId challengeId,
        ChallengeType challengeType,
        long issuedAt,
        long expiresAt,
        PublicSliderGeometry geometry,
        List<ChallengeResource> resources) {
    public CreatedChallenge {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(challengeType, "challengeType");
        if (issuedAt < 0 || expiresAt <= issuedAt) {
            throw new IllegalArgumentException("invalid challenge time range");
        }
        Objects.requireNonNull(geometry, "geometry");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    }
}
