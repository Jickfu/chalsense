package io.github.chalsense.protocol;

import java.util.regex.Pattern;

/** The registered business action to which a challenge and ticket are bound. */
public record ActionName(String value) {
    private static final Pattern SYNTAX = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    public ActionName {
        value = ProtocolLexicalRules.requirePattern(value, SYNTAX, "action");
    }
}

