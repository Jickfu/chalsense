package io.github.chalsense.core.ticket;

import java.util.Objects;
import java.util.OptionalLong;

/** Public consumption result; internal binding mismatch reasons are deliberately absent. */
public record ConsumeResult(
        ConsumeOutcome outcome,
        OptionalLong verifiedAt,
        OptionalLong consumedAt) {
    public ConsumeResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(consumedAt, "consumedAt");
        boolean success = outcome == ConsumeOutcome.CONSUMED;
        if (success != verifiedAt.isPresent() || success != consumedAt.isPresent()) {
            throw new IllegalArgumentException("consumption times must be present only for CONSUMED");
        }
    }

    public static ConsumeResult consumed(long verifiedAt, long consumedAt) {
        return new ConsumeResult(ConsumeOutcome.CONSUMED, OptionalLong.of(verifiedAt), OptionalLong.of(consumedAt));
    }

    public static ConsumeResult failure(ConsumeOutcome outcome) {
        if (outcome == ConsumeOutcome.CONSUMED) {
            throw new IllegalArgumentException("success requires consumption times");
        }
        return new ConsumeResult(outcome, OptionalLong.empty(), OptionalLong.empty());
    }
}
