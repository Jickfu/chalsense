package io.github.chalsense.core.challenge.resource;

import io.github.chalsense.core.challenge.ChallengeResourceRole;

@FunctionalInterface
public interface ChallengeResourceReader {
    ChallengeResourceReadResult read(String resourceId, ChallengeResourceRole role, long now);
}
