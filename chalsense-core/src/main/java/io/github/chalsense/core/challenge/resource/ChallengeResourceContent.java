package io.github.chalsense.core.challenge.resource;

import java.util.Objects;

/** A bounded short-lived resource returned to the HTTP adapter. */
public record ChallengeResourceContent(String mediaType, byte[] bytes, long expiresAt) {
    public ChallengeResourceContent {
        Objects.requireNonNull(mediaType, "mediaType");
        bytes = Objects.requireNonNull(bytes, "bytes").clone();
        if (mediaType.isBlank() || bytes.length == 0 || expiresAt < 0) {
            throw new IllegalArgumentException("invalid challenge resource content");
        }
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
