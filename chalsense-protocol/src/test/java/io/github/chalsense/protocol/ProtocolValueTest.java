package io.github.chalsense.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolValueTest {
    private static final String ID_128 = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String VALUE_256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void acceptsCanonicalFixedLengthValues() {
        assertEquals(ID_128, new ChallengeId(ID_128).value());
        assertEquals(VALUE_256, new VerificationTicket(VALUE_256).value());
        assertEquals(VALUE_256, new ContextDigest(VALUE_256).value());
    }

    @Test
    void rejectsInvalidOrNonCanonicalBase64Url() {
        assertThrows(IllegalArgumentException.class, () -> new ChallengeId(ID_128 + "A"));
        assertThrows(IllegalArgumentException.class, () -> new ChallengeId("AAAAAAAAAAAAAAAAAAAAA="));
        assertThrows(IllegalArgumentException.class, () -> new ChallengeId("AAAAAAAAAAAAAAAAAAAAA+"));
        assertThrows(IllegalArgumentException.class, () -> new ChallengeId("AAAAAAAAAAAAAAAAAAAAAB"));
        assertThrows(NullPointerException.class, () -> new VerificationTicket(null));
    }

    @Test
    void redactsBearerCredentialFromStringRepresentation() {
        VerificationTicket ticket = new VerificationTicket(VALUE_256);
        assertEquals("VerificationTicket[REDACTED]", ticket.toString());
        assertFalse(ticket.toString().contains(ticket.value()));

        ContextDigest contextDigest = new ContextDigest(VALUE_256);
        assertEquals("ContextDigest[REDACTED]", contextDigest.toString());
        assertFalse(contextDigest.toString().contains(contextDigest.value()));
    }

    @Test
    void validatesSiteKeyBoundariesAndAsciiSyntax() {
        assertEquals("site_001", new SiteKey("site_001").value());
        assertEquals("A".repeat(64), new SiteKey("A".repeat(64)).value());
        assertThrows(IllegalArgumentException.class, () -> new SiteKey("short"));
        assertThrows(IllegalArgumentException.class, () -> new SiteKey("A".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> new SiteKey("站点_demo_01"));
        assertThrows(IllegalArgumentException.class, () -> new SiteKey("site.demo"));
    }

    @Test
    void validatesActionBoundariesAndAsciiSyntax() {
        assertEquals("login", new ActionName("login").value());
        assertEquals("a" + "0".repeat(63), new ActionName("a" + "0".repeat(63)).value());
        assertThrows(IllegalArgumentException.class, () -> new ActionName("Login"));
        assertThrows(IllegalArgumentException.class, () -> new ActionName("1login"));
        assertThrows(IllegalArgumentException.class, () -> new ActionName("a" + "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new ActionName("登录"));
    }
}
