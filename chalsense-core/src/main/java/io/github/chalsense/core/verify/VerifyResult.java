package io.github.chalsense.core.verify;

import io.github.chalsense.protocol.VerificationTicket;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Public verification result; internal diagnostic reasons are deliberately absent. */
public record VerifyResult(
        VerifyOutcome outcome,
        Optional<VerificationTicket> verificationTicket,
        OptionalLong issuedAt,
        OptionalLong expiresAt) {
    public VerifyResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(verificationTicket, "verificationTicket");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        boolean success = outcome == VerifyOutcome.TICKET_ISSUED;
        if (success != verificationTicket.isPresent()
                || success != issuedAt.isPresent()
                || success != expiresAt.isPresent()) {
            throw new IllegalArgumentException("ticket data must be present only for TICKET_ISSUED");
        }
    }

    public static VerifyResult ticketIssued(VerificationTicket ticket, long issuedAt, long expiresAt) {
        return new VerifyResult(
                VerifyOutcome.TICKET_ISSUED,
                Optional.of(ticket),
                OptionalLong.of(issuedAt),
                OptionalLong.of(expiresAt));
    }

    public static VerifyResult failure(VerifyOutcome outcome) {
        if (outcome == VerifyOutcome.TICKET_ISSUED) {
            throw new IllegalArgumentException("success requires ticket data");
        }
        return new VerifyResult(outcome, Optional.empty(), OptionalLong.empty(), OptionalLong.empty());
    }
}

