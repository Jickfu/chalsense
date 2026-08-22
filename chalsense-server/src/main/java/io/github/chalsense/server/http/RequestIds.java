package io.github.chalsense.server.http;

import java.security.SecureRandom;
import java.util.Base64;

final class RequestIds {
    private static final SecureRandom RANDOM = new SecureRandom();

    private RequestIds() {
    }

    static String next() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
