package io.github.chalsense.core.challenge.resource;

import io.github.chalsense.core.challenge.ChallengeResourceRole;

import java.util.Objects;

/** Bounded encoded resource passed to a short-lived publisher. */
public record ChallengeBinaryResource(
        ChallengeResourceRole role,
        String mediaType,
        int pixelWidth,
        int pixelHeight,
        byte[] bytes) {
    public ChallengeBinaryResource {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(mediaType, "mediaType");
        bytes = Objects.requireNonNull(bytes, "bytes").clone();
        if (mediaType.isBlank() || pixelWidth <= 0 || pixelHeight <= 0 || bytes.length == 0) {
            throw new IllegalArgumentException("invalid binary challenge resource");
        }
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public int byteLength() {
        return bytes.length;
    }
}
