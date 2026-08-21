package io.github.chalsense.store.redis;

import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** Deterministic v1 Redis key schema. Keys are internal and never contain raw tickets. */
public final class RedisKeyspace {
    public static final String DEFAULT_NAMESPACE = "chalsense";

    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String KEY_SCHEMA_VERSION = "v1";

    private final String namespace;

    public RedisKeyspace() {
        this(DEFAULT_NAMESPACE);
    }

    public RedisKeyspace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "namespace must contain 1..64 ASCII letters, digits, dot, underscore or hyphen");
        }
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }

    public byte[] challengeKey(SiteKey siteKey, ChallengeId challengeId) {
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(challengeId, "challengeId");
        return ascii(namespace + ":" + KEY_SCHEMA_VERSION + ":challenge:"
                + siteKey.value() + ":" + challengeId.value());
    }

    public byte[] ticketKey(TicketDigest ticketDigest) {
        Objects.requireNonNull(ticketDigest, "ticketDigest");
        return ascii(namespace + ":" + KEY_SCHEMA_VERSION + ":ticket:" + ticketDigest.hexValue());
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
