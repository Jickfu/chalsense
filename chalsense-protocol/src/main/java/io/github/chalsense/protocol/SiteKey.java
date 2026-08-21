package io.github.chalsense.protocol;

import java.util.regex.Pattern;

/** A public site configuration identifier; this value is not a secret. */
public record SiteKey(String value) {
    private static final Pattern SYNTAX = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    public SiteKey {
        value = ProtocolLexicalRules.requirePattern(value, SYNTAX, "siteKey");
    }
}

