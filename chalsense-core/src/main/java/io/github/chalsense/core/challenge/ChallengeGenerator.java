package io.github.chalsense.core.challenge;

/** Trusted server-side extension point; implementations must not accept client-selected answers. */
@FunctionalInterface
public interface ChallengeGenerator {
    GeneratedChallenge generate(ChallengeGenerationRequest request);

    /** Best-effort cleanup when generated resources cannot become a usable challenge. */
    default void discard(ChallengeGenerationRequest request, GeneratedChallenge generated) {
    }
}
