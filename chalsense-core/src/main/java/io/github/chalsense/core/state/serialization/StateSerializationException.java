package io.github.chalsense.core.state.serialization;

/** Indicates malformed, unsupported or schema-incompatible persisted state. */
public final class StateSerializationException extends IllegalArgumentException {
    public StateSerializationException(String message) {
        super(message);
    }

    public StateSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
