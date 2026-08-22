package io.github.chalsense.server.security;

import io.github.chalsense.protocol.SiteKey;

@FunctionalInterface
public interface ServiceCredentialAuthenticator {
    boolean authenticate(SiteKey siteKey, String authorization, long now);
}
