package io.github.chalsense.core.challenge.resource;

import java.util.Objects;

/** Publicly merged resource lookup result; absence, expiry and dependency causes are not exposed. */
public sealed interface ChallengeResourceReadResult {
    record Found(ChallengeResourceContent content) implements ChallengeResourceReadResult {
        public Found {
            Objects.requireNonNull(content, "content");
        }
    }

    record Unavailable() implements ChallengeResourceReadResult {
    }
}
