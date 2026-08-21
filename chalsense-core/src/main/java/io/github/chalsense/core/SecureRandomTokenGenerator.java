package io.github.chalsense.core;

import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.VerificationTicket;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** Default CSPRNG-backed token generator. */
public final class SecureRandomTokenGenerator implements TokenGenerator {
    private static final int CHALLENGE_ID_BYTES = 16;
    private static final int VERIFICATION_TICKET_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureRandomTokenGenerator() {
        this(new SecureRandom());
    }

    public SecureRandomTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public ChallengeId newChallengeId() {
        return new ChallengeId(randomBase64Url(CHALLENGE_ID_BYTES));
    }

    @Override
    public VerificationTicket newVerificationTicket() {
        return new VerificationTicket(randomBase64Url(VERIFICATION_TICKET_BYTES));
    }

    private String randomBase64Url(int byteCount) {
        byte[] value = new byte[byteCount];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
