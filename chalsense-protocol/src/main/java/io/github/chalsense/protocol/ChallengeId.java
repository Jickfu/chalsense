package io.github.chalsense.protocol;

/** A canonical 128-bit challenge identifier encoded without Base64url padding. */
public record ChallengeId(String value) {
    public ChallengeId {
        value = ProtocolLexicalRules.requireCanonicalBase64Url(value, 22, 16, "challengeId");
    }
}

