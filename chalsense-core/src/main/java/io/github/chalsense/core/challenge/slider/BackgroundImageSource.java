package io.github.chalsense.core.challenge.slider;

import io.github.chalsense.core.challenge.ChallengeGenerationRequest;

import java.awt.image.BufferedImage;

/** Supplies trusted, locally governed source material; implementations must not fetch arbitrary client URLs. */
@FunctionalInterface
public interface BackgroundImageSource {
    BufferedImage load(ChallengeGenerationRequest request);
}
