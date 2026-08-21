package io.github.chalsense.core.challenge;

import java.util.Objects;
import java.util.Optional;

/** Public create result; internal failure causes are intentionally absent. */
public record CreateResult(CreateOutcome outcome, Optional<CreatedChallenge> challenge) {
    public CreateResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(challenge, "challenge");
        if ((outcome == CreateOutcome.CHALLENGE_CREATED) != challenge.isPresent()) {
            throw new IllegalArgumentException("challenge data must be present only for CHALLENGE_CREATED");
        }
    }

    public static CreateResult created(CreatedChallenge challenge) {
        return new CreateResult(CreateOutcome.CHALLENGE_CREATED, Optional.of(challenge));
    }

    public static CreateResult failure(CreateOutcome outcome) {
        if (outcome == CreateOutcome.CHALLENGE_CREATED) {
            throw new IllegalArgumentException("success requires challenge data");
        }
        return new CreateResult(outcome, Optional.empty());
    }
}
