package io.github.chalsense.core.site;

import io.github.chalsense.protocol.SiteKey;

import java.util.Optional;

/** Resolves validated site registrations without exposing configuration storage details. */
@FunctionalInterface
public interface SiteRegistry {
    Optional<SiteRegistration> find(SiteKey siteKey);
}

