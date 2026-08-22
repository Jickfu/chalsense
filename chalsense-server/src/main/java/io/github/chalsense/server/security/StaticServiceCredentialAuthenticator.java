package io.github.chalsense.server.security;

import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.core.site.SiteStatus;
import io.github.chalsense.server.config.ChalSenseServerProperties;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class StaticServiceCredentialAuthenticator implements ServiceCredentialAuthenticator {
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final byte[] UNKNOWN_DIGEST = new byte[32];
    private final Map<String, Credential> credentials;

    public StaticServiceCredentialAuthenticator(ChalSenseServerProperties properties) {
        Map<String, Credential> configured = new HashMap<>();
        for (ChalSenseServerProperties.Site site : properties.getSites()) {
            for (ChalSenseServerProperties.Credential source : site.getCredentials()) {
                if (!KEY_ID.matcher(source.getKeyId() == null ? "" : source.getKeyId()).matches()
                        || !SECRET.matcher(source.getSecretSha256() == null ? "" : source.getSecretSha256()).matches()) {
                    throw new IllegalArgumentException("credential keyId or secretSha256 is invalid");
                }
                byte[] digest = decode32(source.getSecretSha256());
                if (source.getNotBefore() != null && source.getNotBefore() < 0
                        || source.getExpiresAt() != null && source.getExpiresAt() < 0
                        || source.getNotBefore() != null && source.getExpiresAt() != null
                        && source.getNotBefore() >= source.getExpiresAt()) {
                    throw new IllegalArgumentException("credential validity window is invalid");
                }
                Credential prior = configured.put(source.getKeyId(), new Credential(
                        site.getSiteKey(), digest, source.isActive() && site.getStatus() == SiteStatus.ACTIVE,
                        source.getNotBefore(), source.getExpiresAt()));
                if (prior != null) throw new IllegalArgumentException("credential keyId must be globally unique");
            }
        }
        credentials = Map.copyOf(configured);
    }

    @Override
    public boolean authenticate(SiteKey siteKey, String authorization, long now) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 128) return false;
        String token = authorization.substring(7);
        int separator = token.indexOf('.');
        if (separator < 8 || separator != token.lastIndexOf('.')) return false;
        String keyId = token.substring(0, separator);
        String secret = token.substring(separator + 1);
        if (!KEY_ID.matcher(keyId).matches() || !SECRET.matcher(secret).matches()) return false;
        Credential credential = credentials.get(keyId);
        byte[] presented;
        try {
            presented = sha256(decode32(secret));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        boolean digestMatches = MessageDigest.isEqual(
                credential == null ? UNKNOWN_DIGEST : credential.secretSha256(), presented);
        return digestMatches && credential.active() && credential.siteKey().equals(siteKey.value())
                && (credential.notBefore() == null || now >= credential.notBefore())
                && (credential.expiresAt() == null || now < credential.expiresAt());
    }

    private static byte[] decode32(String value) {
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException("non-canonical base64url");
        }
        return decoded;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Credential(String siteKey, byte[] secretSha256, boolean active, Long notBefore, Long expiresAt) {
        private Credential { secretSha256 = secretSha256.clone(); }
        @Override public byte[] secretSha256() { return secretSha256.clone(); }
    }
}
