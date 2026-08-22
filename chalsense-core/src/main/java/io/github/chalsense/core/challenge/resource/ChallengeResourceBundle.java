package io.github.chalsense.core.challenge.resource;

import io.github.chalsense.core.challenge.ChallengeResourceRole;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Two resources published atomically under the challenge lifetime. */
public record ChallengeResourceBundle(
        SiteKey siteKey,
        ChallengeId challengeId,
        long expiresAt,
        List<ChallengeBinaryResource> resources) {
    public ChallengeResourceBundle {
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(challengeId, "challengeId");
        if (expiresAt < 0) throw new IllegalArgumentException("expiresAt must not be negative");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        Set<ChallengeResourceRole> roles = resources.stream()
                .map(ChallengeBinaryResource::role)
                .collect(Collectors.toUnmodifiableSet());
        if (resources.size() != 2 || roles.size() != 2) {
            throw new IllegalArgumentException("exactly one background and one piece are required");
        }
    }
}
