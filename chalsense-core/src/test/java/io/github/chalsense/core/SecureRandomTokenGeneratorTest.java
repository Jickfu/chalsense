package io.github.chalsense.core;

import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.VerificationTicket;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecureRandomTokenGeneratorTest {
    @Test
    void generatesApprovedCanonicalTokenLengths() {
        SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

        ChallengeId challengeId = generator.newChallengeId();
        VerificationTicket ticket = generator.newVerificationTicket();

        assertEquals(22, challengeId.value().length());
        assertEquals(16, Base64.getUrlDecoder().decode(challengeId.value()).length);
        assertEquals(43, ticket.value().length());
        assertEquals(32, Base64.getUrlDecoder().decode(ticket.value()).length);
    }
}
