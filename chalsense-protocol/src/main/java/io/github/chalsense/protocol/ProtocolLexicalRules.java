package io.github.chalsense.protocol;

import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

final class ProtocolLexicalRules {
    private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");

    private ProtocolLexicalRules() {
    }

    static String requireCanonicalBase64Url(String value, int encodedLength, int decodedLength, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() != encodedLength || !BASE64URL.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " has invalid encoding");
        }

        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " has invalid encoding", exception);
        }
        if (decoded.length != decodedLength
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException(name + " has non-canonical encoding");
        }
        return value;
    }

    static String requirePattern(String value, Pattern pattern, String name) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " has invalid syntax");
        }
        return value;
    }
}

