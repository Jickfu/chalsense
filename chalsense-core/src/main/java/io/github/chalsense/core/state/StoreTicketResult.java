package io.github.chalsense.core.state;

/** Result of storing a ticket without overwriting an existing digest. */
public enum StoreTicketResult {
    CONFIRMED,
    FAILED,
    UNKNOWN
}

