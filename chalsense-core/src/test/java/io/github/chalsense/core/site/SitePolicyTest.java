package io.github.chalsense.core.site;

import io.github.chalsense.protocol.ActionName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SitePolicyTest {
    private static final Set<ActionName> ACTIONS = Set.of(new ActionName("login"));

    @Test
    void acceptsInclusiveTtlBoundaries() {
        assertDoesNotThrow(() -> policy(Duration.ofSeconds(30), Duration.ofSeconds(10), Set.of(), false));
        assertDoesNotThrow(() -> policy(Duration.ofSeconds(300), Duration.ofSeconds(120), Set.of(), false));
    }

    @Test
    void rejectsTtlOutsideApprovedBoundaries() {
        assertThrows(IllegalArgumentException.class,
                () -> policy(Duration.ofSeconds(29), Duration.ofSeconds(60), Set.of(), false));
        assertThrows(IllegalArgumentException.class,
                () -> policy(Duration.ofSeconds(120), Duration.ofSeconds(121), Set.of(), false));
    }

    @Test
    void permitsInsecureOriginsOnlyForExplicitLoopback() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                Duration.ofSeconds(120), Duration.ofSeconds(60),
                Set.of(WebOrigin.parse("http://localhost:3000")), false));
        assertDoesNotThrow(() -> policy(
                Duration.ofSeconds(120), Duration.ofSeconds(60),
                Set.of(WebOrigin.parse("http://localhost:3000")), true));
        assertThrows(IllegalArgumentException.class, () -> policy(
                Duration.ofSeconds(120), Duration.ofSeconds(60),
                Set.of(WebOrigin.parse("http://example.com")), true));
    }

    private static SitePolicy policy(
            Duration challengeTtl,
            Duration ticketTtl,
            Set<WebOrigin> origins,
            boolean allowInsecureLoopback) {
        return new SitePolicy(
                challengeTtl, ticketTtl, "policy-v1", ACTIONS, origins, allowInsecureLoopback);
    }
}
