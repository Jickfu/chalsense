package io.github.chalsense.core.verify;

public enum VerifyOutcome {
    TICKET_ISSUED,
    VERIFICATION_FAILED,
    CHALLENGE_UNAVAILABLE,
    CALLER_UNAUTHORIZED,
    ORIGIN_NOT_ALLOWED,
    DEPENDENCY_UNAVAILABLE
}
