package io.github.chalsense.core.state;

import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeType;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;

import java.util.Objects;

/** Stable Core state consumed exactly once during challenge verification. */
public record ChallengeState(
        int storageVersion,
        ProtocolVersion protocolVersion,
        ChallengeType challengeType,
        SiteKey siteKey,
        ActionName action,
        ContextDigest contextDigest,
        long issuedAt,
        long expiresAt,
        SliderPuzzleGeometry geometry,
        String policyVersion) {
    public ChallengeState {
        if (storageVersion <= 0 || issuedAt < 0 || expiresAt <= issuedAt) {
            throw new IllegalArgumentException("invalid challenge state version or time range");
        }
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(challengeType, "challengeType");
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(contextDigest, "contextDigest");
        Objects.requireNonNull(geometry, "geometry");
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
    }
}

