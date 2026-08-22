package io.github.chalsense.server.observability;

public enum ObservedOperation {
    CHALLENGE_CREATE,
    CHALLENGE_VERIFY,
    TICKET_CONSUME,
    RESOURCE_READ,
    LIVENESS,
    READINESS
}
