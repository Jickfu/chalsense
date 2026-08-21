package io.github.chalsense.core.security;

import java.util.Objects;

/** A privacy-minimized diagnostic event with no identifiers, ticket, context, coordinates or track. */
public record SecurityEvent(SecurityOperation operation, SecurityReason reason) {
    public SecurityEvent {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(reason, "reason");
    }
}

