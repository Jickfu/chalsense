package io.github.chalsense.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ConstantTime {
    private ConstantTime() {
    }

    public static boolean equalsUtf8(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}

