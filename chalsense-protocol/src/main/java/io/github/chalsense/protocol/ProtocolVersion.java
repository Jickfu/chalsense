package io.github.chalsense.protocol;

import java.util.Objects;

/** A protocol version that can be represented on the wire. */
public enum ProtocolVersion {
    V1("1");

    private final String wireValue;

    ProtocolVersion(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ProtocolVersion fromWireValue(String wireValue) {
        Objects.requireNonNull(wireValue, "wireValue");
        for (ProtocolVersion version : values()) {
            if (version.wireValue.equals(wireValue)) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unsupported protocol version");
    }
}

