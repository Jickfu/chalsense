package io.github.chalsense.core.challenge;

import java.util.Objects;

/** Public rendering resource. The URL may be relative or an adapter-provided short-lived URL. */
public record ChallengeResource(
        ChallengeResourceRole role,
        String url,
        String mediaType,
        int pixelWidth,
        int pixelHeight) {
    public ChallengeResource {
        Objects.requireNonNull(role, "role");
        if (url == null || url.isBlank() || url.length() > 2048 || url.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("url must be a non-blank resource reference without control characters");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType must not be blank");
        }
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IllegalArgumentException("pixel dimensions must be positive");
        }
    }
}
