package io.github.chalsense.core.security;

/** Receives internal low-cardinality diagnostics; adapters must not expose them to untrusted callers. */
@FunctionalInterface
public interface SecurityEventSink {
    void record(SecurityEvent event);

    static SecurityEventSink noop() {
        return event -> {
        };
    }
}

