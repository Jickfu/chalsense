package io.github.chalsense.core.ticket;

import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.VerificationTicket;

import java.util.Objects;

/** A trusted business caller's request to consume one verification ticket. */
public record ConsumeTicketCommand(
        ProtocolVersion protocolVersion,
        VerificationTicket verificationTicket,
        SiteKey siteKey,
        ActionName action,
        ContextDigest contextDigest) {
    public ConsumeTicketCommand {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(verificationTicket, "verificationTicket");
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(contextDigest, "contextDigest");
    }
}

