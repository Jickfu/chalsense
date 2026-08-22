package io.github.chalsense.core.challenge.resource;

import io.github.chalsense.core.challenge.ChallengeResource;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;

import java.util.List;

/** Publishes a complete short-lived resource bundle or throws without exposing partial references. */
public interface ChallengeResourcePublisher {
    List<ChallengeResource> publish(ChallengeResourceBundle bundle);

    /** Idempotent best-effort deletion. Hard expiry is still mandatory. */
    void delete(SiteKey siteKey, ChallengeId challengeId);
}
