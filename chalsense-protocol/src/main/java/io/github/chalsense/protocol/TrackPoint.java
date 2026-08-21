package io.github.chalsense.protocol;

import java.util.Objects;

/** One untrusted relative pointer sample; Core validates semantic ranges after state consumption. */
public record TrackPoint(long x, long y, long t, TrackEvent event) {
    public TrackPoint {
        Objects.requireNonNull(event, "event");
    }

    @Override
    public String toString() {
        return "TrackPoint[REDACTED]";
    }
}
