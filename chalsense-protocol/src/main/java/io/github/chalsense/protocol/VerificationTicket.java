package io.github.chalsense.protocol;

/** A canonical 256-bit bearer credential encoded without Base64url padding. */
public record VerificationTicket(String value) {
    public VerificationTicket {
        value = ProtocolLexicalRules.requireCanonicalBase64Url(value, 43, 32, "verificationTicket");
    }

    @Override
    public String toString() {
        return "VerificationTicket[REDACTED]";
    }
}

