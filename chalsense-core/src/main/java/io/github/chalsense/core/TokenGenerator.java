package io.github.chalsense.core;

import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.VerificationTicket;

/** Generates unpredictable protocol credentials. */
public interface TokenGenerator {
    ChallengeId newChallengeId();

    VerificationTicket newVerificationTicket();
}

