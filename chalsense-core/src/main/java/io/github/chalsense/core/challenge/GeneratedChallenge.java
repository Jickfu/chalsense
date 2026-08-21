package io.github.chalsense.core.challenge;

import io.github.chalsense.core.state.SliderPuzzleGeometry;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Generator output containing authoritative geometry and public rendering resources. */
public record GeneratedChallenge(
        SliderPuzzleGeometry geometry,
        int logicalWidth,
        int logicalHeight,
        List<ChallengeResource> resources) {
    public GeneratedChallenge {
        Objects.requireNonNull(geometry, "geometry");
        if (logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("logical dimensions must be positive");
        }
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        Set<ChallengeResourceRole> roles = resources.stream()
                .map(ChallengeResource::role)
                .collect(Collectors.toUnmodifiableSet());
        if (resources.size() != 2 || roles.size() != 2) {
            throw new IllegalArgumentException("exactly one background and one piece resource are required");
        }
    }
}
