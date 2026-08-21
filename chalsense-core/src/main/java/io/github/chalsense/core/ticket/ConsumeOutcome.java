package io.github.chalsense.core.ticket;

public enum ConsumeOutcome {
    CONSUMED,
    TICKET_INVALID,
    TICKET_UNAVAILABLE,
    CALLER_UNAUTHORIZED,
    DEPENDENCY_UNAVAILABLE
}
