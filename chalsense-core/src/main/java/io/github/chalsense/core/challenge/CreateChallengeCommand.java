package io.github.chalsense.core.challenge;

import io.github.chalsense.core.site.CallerContext;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;

import java.util.Objects;

public record CreateChallengeCommand(
        ProtocolVersion protocolVersion,
        SiteKey siteKey,
        ActionName action,
        ContextDigest contextDigest,
        CallerContext callerContext) {
    public CreateChallengeCommand {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(contextDigest, "contextDigest");
        Objects.requireNonNull(callerContext, "callerContext");
    }
}
