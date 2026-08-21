package io.github.chalsense.core.site;

import io.github.chalsense.protocol.ActionName;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Validated per-site challenge, ticket and browser-origin policy. */
public record SitePolicy(
        Duration challengeTtl,
        Duration ticketTtl,
        String policyVersion,
        Set<ActionName> allowedActions,
        Set<WebOrigin> allowedOrigins,
        boolean allowInsecureLoopbackOrigins) {
    public static final Duration DEFAULT_CHALLENGE_TTL = Duration.ofSeconds(120);
    public static final Duration DEFAULT_TICKET_TTL = Duration.ofSeconds(60);

    private static final Duration MINIMUM_CHALLENGE_TTL = Duration.ofSeconds(30);
    private static final Duration MAXIMUM_CHALLENGE_TTL = Duration.ofSeconds(300);
    private static final Duration MINIMUM_TICKET_TTL = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_TICKET_TTL = Duration.ofSeconds(120);

    public SitePolicy {
        Objects.requireNonNull(challengeTtl, "challengeTtl");
        Objects.requireNonNull(ticketTtl, "ticketTtl");
        if (challengeTtl.compareTo(MINIMUM_CHALLENGE_TTL) < 0
                || challengeTtl.compareTo(MAXIMUM_CHALLENGE_TTL) > 0) {
            throw new IllegalArgumentException("challengeTtl must be between 30 and 300 seconds");
        }
        if (ticketTtl.compareTo(MINIMUM_TICKET_TTL) < 0
                || ticketTtl.compareTo(MAXIMUM_TICKET_TTL) > 0) {
            throw new IllegalArgumentException("ticketTtl must be between 10 and 120 seconds");
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        allowedActions = Set.copyOf(Objects.requireNonNull(allowedActions, "allowedActions"));
        allowedOrigins = Set.copyOf(Objects.requireNonNull(allowedOrigins, "allowedOrigins"));
        if (allowedActions.isEmpty()) {
            throw new IllegalArgumentException("allowedActions must not be empty");
        }
        for (WebOrigin origin : allowedOrigins) {
            if (!origin.isSecure() && (!origin.isLoopback() || !allowInsecureLoopbackOrigins)) {
                throw new IllegalArgumentException("insecure origins require explicit loopback allowance");
            }
        }
    }

    public static SitePolicy defaults(
            String policyVersion,
            Set<ActionName> allowedActions,
            Set<WebOrigin> allowedOrigins) {
        return new SitePolicy(
                DEFAULT_CHALLENGE_TTL,
                DEFAULT_TICKET_TTL,
                policyVersion,
                allowedActions,
                allowedOrigins,
                false);
    }
}

