package io.github.chalsense.store.redis;

import io.github.chalsense.core.challenge.ChallengeResource;
import io.github.chalsense.core.challenge.ChallengeResourceRole;
import io.github.chalsense.core.challenge.resource.ChallengeBinaryResource;
import io.github.chalsense.core.challenge.resource.ChallengeResourceBundle;
import io.github.chalsense.core.challenge.resource.ChallengeResourceContent;
import io.github.chalsense.core.challenge.resource.ChallengeResourcePublisher;
import io.github.chalsense.core.challenge.resource.ChallengeResourceReadResult;
import io.github.chalsense.core.challenge.resource.ChallengeResourceReader;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Atomic, repeat-readable Redis/Valkey resource bundle with a hard absolute TTL. */
public final class RedisChallengeResourceStore implements ChallengeResourcePublisher, ChallengeResourceReader {
    private static final int MAXIMUM_ID_ATTEMPTS = 3;
    private static final byte[] FIELD_BACKGROUND = ascii("background");
    private static final byte[] FIELD_PIECE = ascii("piece");
    private static final byte[] FIELD_EXPIRES_AT = ascii("expiresAt");
    private static final byte[] PUBLISH_SCRIPT = ("if redis.call('EXISTS',KEYS[1])~=0 then return 0 end "
            + "redis.call('HSET',KEYS[1],ARGV[1],ARGV[2],ARGV[3],ARGV[4],ARGV[5],ARGV[6]) "
            + "redis.call('PEXPIREAT',KEYS[1],ARGV[6]) return 1").getBytes(StandardCharsets.US_ASCII);

    private final ResourceRedisCommands commands;
    private final RedisKeyspace keyspace;
    private final SecureRandom secureRandom;
    private final String publicPath;
    private final Pattern ownedResourceUrl;

    public RedisChallengeResourceStore(
            RedisClient client, RedisKeyspace keyspace, SecureRandom secureRandom, String publicPath) {
        this(new JedisResourceRedisCommands(client), keyspace, secureRandom, publicPath);
    }

    RedisChallengeResourceStore(
            ResourceRedisCommands commands, RedisKeyspace keyspace, SecureRandom secureRandom, String publicPath) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.keyspace = Objects.requireNonNull(keyspace, "keyspace");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        if (publicPath == null || !publicPath.startsWith("/") || publicPath.endsWith("/")
                || publicPath.contains("//") || publicPath.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("publicPath must be a canonical absolute path without a trailing slash");
        }
        this.publicPath = publicPath;
        this.ownedResourceUrl = Pattern.compile(Pattern.quote(publicPath)
                + "/([A-Za-z0-9_-]{22})/(background|piece)");
    }

    @Override
    public List<ChallengeResource> publish(ChallengeResourceBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        byte[] background = bytes(bundle, ChallengeResourceRole.BACKGROUND);
        byte[] piece = bytes(bundle, ChallengeResourceRole.PIECE);
        byte[] expiresAt = ascii(Long.toString(bundle.expiresAt()));
        for (int attempt = 0; attempt < MAXIMUM_ID_ATTEMPTS; attempt++) {
            String resourceId = resourceId();
            try {
                Object response = commands.eval(PUBLISH_SCRIPT, List.of(keyspace.resourceKey(resourceId)), List.of(
                        FIELD_BACKGROUND, background, FIELD_PIECE, piece, FIELD_EXPIRES_AT, expiresAt));
                if (Long.valueOf(1L).equals(response)) return references(bundle, resourceId);
                if (!Long.valueOf(0L).equals(response)) throw new IllegalStateException("unexpected Redis resource response");
            } catch (JedisException exception) {
                throw new IllegalStateException("resource publication result is unknown", exception);
            }
        }
        throw new IllegalStateException("resource identifier collisions exhausted");
    }

    @Override
    public ChallengeResourceReadResult read(String resourceId, ChallengeResourceRole role, long now) {
        if (now < 0 || role == null || resourceId == null || !resourceId.matches("[A-Za-z0-9_-]{22}")) {
            return new ChallengeResourceReadResult.Unavailable();
        }
        try {
            byte[] field = role == ChallengeResourceRole.BACKGROUND ? FIELD_BACKGROUND : FIELD_PIECE;
            List<byte[]> values = commands.hmget(keyspace.resourceKey(resourceId), field, FIELD_EXPIRES_AT);
            if (values.size() != 2 || values.get(0) == null || values.get(1) == null) {
                return new ChallengeResourceReadResult.Unavailable();
            }
            long expiresAt = Long.parseLong(new String(values.get(1), StandardCharsets.US_ASCII));
            if (now >= expiresAt) return new ChallengeResourceReadResult.Unavailable();
            int maximum = role == ChallengeResourceRole.BACKGROUND ? 1024 * 1024 : 256 * 1024;
            if (values.get(0).length == 0 || values.get(0).length > maximum) {
                return new ChallengeResourceReadResult.Unavailable();
            }
            return new ChallengeResourceReadResult.Found(
                    new ChallengeResourceContent("image/png", values.get(0), expiresAt));
        } catch (JedisException | IllegalArgumentException exception) {
            return new ChallengeResourceReadResult.Unavailable();
        }
    }

    @Override
    public void delete(List<ChallengeResource> publishedResources) {
        Objects.requireNonNull(publishedResources, "publishedResources");
        if (publishedResources.size() != 2) throw new IllegalArgumentException("two published resources are required");
        String resourceId = null;
        java.util.EnumSet<ChallengeResourceRole> roles = java.util.EnumSet.noneOf(ChallengeResourceRole.class);
        for (ChallengeResource resource : publishedResources) {
            Matcher matcher = ownedResourceUrl.matcher(resource.url());
            if (!matcher.matches()) throw new IllegalArgumentException("resource URL is not owned by this publisher");
            ChallengeResourceRole urlRole = ChallengeResourceRole.valueOf(matcher.group(2).toUpperCase(Locale.ROOT));
            if (urlRole != resource.role() || !roles.add(urlRole)) {
                throw new IllegalArgumentException("resource role does not match publisher reference");
            }
            if (resourceId == null) resourceId = matcher.group(1);
            else if (!resourceId.equals(matcher.group(1))) throw new IllegalArgumentException("mixed resource bundles");
        }
        commands.del(keyspace.resourceKey(Objects.requireNonNull(resourceId)));
    }

    private String resourceId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<ChallengeResource> references(ChallengeResourceBundle bundle, String resourceId) {
        return bundle.resources().stream().map(resource -> new ChallengeResource(
                resource.role(), publicPath + "/" + resourceId + "/"
                        + resource.role().name().toLowerCase(Locale.ROOT), resource.mediaType(),
                resource.pixelWidth(), resource.pixelHeight())).toList();
    }

    private static byte[] bytes(ChallengeResourceBundle bundle, ChallengeResourceRole role) {
        return bundle.resources().stream().filter(resource -> resource.role() == role).findFirst()
                .map(ChallengeBinaryResource::bytes).orElseThrow();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
