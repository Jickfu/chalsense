package io.github.chalsense.server.http;

import java.security.SecureRandom;
import java.util.Base64;

public final class RequestIds {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestIds() {
    }

    static String next() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String begin() {
        String requestId = next();
        CURRENT.set(requestId);
        return requestId;
    }

    public static String currentOrNext() {
        String current = CURRENT.get();
        return current == null ? next() : current;
    }

    public static void end() {
        CURRENT.remove();
    }
}
