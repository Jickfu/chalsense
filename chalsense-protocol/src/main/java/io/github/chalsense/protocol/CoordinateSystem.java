package io.github.chalsense.protocol;

/** Constants belonging to the version 1 wire coordinate system. */
public final class CoordinateSystem {
    private static final long NORMALIZED_SCALE = 1_000_000L;

    private CoordinateSystem() {
    }

    public static long normalizedScale() {
        return NORMALIZED_SCALE;
    }
}

