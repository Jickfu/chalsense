package io.github.chalsense.protocol;

/** A canonical 256-bit digest used to bind verification to business context. */
public record ContextDigest(String value) {
    public ContextDigest {
        value = ProtocolLexicalRules.requireCanonicalBase64Url(value, 43, 32, "contextDigest");
    }

    @Override
    public String toString() {
        return "ContextDigest[REDACTED]";
    }
}
