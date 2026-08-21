package io.github.chalsense.core.state;

import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeType;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;

import java.util.Objects;

/** Stable Core state consumed exactly once by a trusted business caller. */
public record TicketState(
        int storageVersion,
        ProtocolVersion protocolVersion,
        SiteKey siteKey,
        ActionName action,
        ContextDigest contextDigest,
        ChallengeType challengeType,
        String policyVersion,
        long verifiedAt,
        long issuedAt,
        long expiresAt) {
    public TicketState {
        if (storageVersion <= 0 || verifiedAt < 0 || issuedAt < 0 || expiresAt <= issuedAt) {
            throw new IllegalArgumentException("invalid ticket state version or time range");
        }
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(contextDigest, "contextDigest");
        Objects.requireNonNull(challengeType, "challengeType");
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
    }
}

