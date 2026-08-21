package io.github.chalsense.core.verify;

import io.github.chalsense.protocol.Track;
import io.github.chalsense.protocol.TrackEvent;
import io.github.chalsense.protocol.TrackPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackValidatorTest {
    @Test
    void enforcesPointCountBoundaries() {
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of())));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(start()))));
        assertTrue(TrackValidator.isStructurallyValid(trackWithPointCount(2)));
        assertTrue(TrackValidator.isStructurallyValid(trackWithPointCount(256)));
        assertFalse(TrackValidator.isStructurallyValid(trackWithPointCount(257)));
    }

    @Test
    void enforcesTimeAndCoordinateBoundaries() {
        assertTrue(TrackValidator.isStructurallyValid(new Track(List.of(
                start(), new TrackPoint(-2_000_000, 2_000_000, 30_000, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                start(), new TrackPoint(-2_000_001, 0, 1, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                start(), new TrackPoint(0, 2_000_001, 1, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                start(), new TrackPoint(0, 0, 30_001, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                start(), new TrackPoint(0, 0, -1, TrackEvent.END)))));
    }

    @Test
    void enforcesEventOrderAndMonotonicTime() {
        assertTrue(TrackValidator.isStructurallyValid(new Track(List.of(
                start(),
                new TrackPoint(1, 0, 0, TrackEvent.MOVE),
                new TrackPoint(2, 0, 0, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                new TrackPoint(1, 0, 0, TrackEvent.START),
                new TrackPoint(2, 0, 1, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                start(),
                new TrackPoint(1, 0, 2, TrackEvent.MOVE),
                new TrackPoint(2, 0, 1, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                start(),
                new TrackPoint(1, 0, 1, TrackEvent.START),
                new TrackPoint(2, 0, 2, TrackEvent.END)))));
        assertFalse(TrackValidator.isStructurallyValid(new Track(List.of(
                start(), new TrackPoint(1, 0, 1, TrackEvent.MOVE)))));
    }

    private static Track trackWithPointCount(int pointCount) {
        List<TrackPoint> points = new ArrayList<>();
        if (pointCount == 0) {
            return new Track(points);
        }
        points.add(start());
        for (int index = 1; index < pointCount - 1; index++) {
            points.add(new TrackPoint(index, 0, index, TrackEvent.MOVE));
        }
        if (pointCount > 1) {
            points.add(new TrackPoint(pointCount - 1L, 0, pointCount - 1L, TrackEvent.END));
        }
        return new Track(points);
    }

    private static TrackPoint start() {
        return new TrackPoint(0, 0, 0, TrackEvent.START);
    }
}
