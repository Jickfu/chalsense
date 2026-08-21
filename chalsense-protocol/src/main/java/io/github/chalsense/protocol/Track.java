package io.github.chalsense.protocol;

import java.util.List;
import java.util.Objects;

/** An untrusted pointer track; Core performs structural validation only after atomic state consumption. */
public record Track(List<TrackPoint> points) {
    public Track {
        Objects.requireNonNull(points, "points");
        points = List.copyOf(points);
    }

    @Override
    public String toString() {
        return "Track[pointCount=" + points.size() + "]";
    }
}
