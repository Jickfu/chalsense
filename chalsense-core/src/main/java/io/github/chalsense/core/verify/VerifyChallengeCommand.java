package io.github.chalsense.core.verify;

import io.github.chalsense.core.site.CallerContext;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.Track;

import java.util.Objects;

/** A lexically parsed but semantically untrusted challenge solution. */
public record VerifyChallengeCommand(
        ProtocolVersion protocolVersion,
        SiteKey siteKey,
        ChallengeId challengeId,
        long finalPieceX,
        Track track,
        CallerContext callerContext) {
    public VerifyChallengeCommand {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(callerContext, "callerContext");
    }
}
