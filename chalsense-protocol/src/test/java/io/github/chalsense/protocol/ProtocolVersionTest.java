package io.github.chalsense.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolVersionTest {
    @Test
    void parsesOnlyTheApprovedWireVersion() {
        assertEquals(ProtocolVersion.V1, ProtocolVersion.fromWireValue("1"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolVersion.fromWireValue("01"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolVersion.fromWireValue("2"));
        assertThrows(NullPointerException.class, () -> ProtocolVersion.fromWireValue(null));
    }

    @Test
    void exposesTheApprovedCoordinateScale() {
        assertEquals(1_000_000L, CoordinateSystem.normalizedScale());
    }
}

