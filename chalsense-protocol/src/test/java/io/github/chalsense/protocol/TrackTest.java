package io.github.chalsense.protocol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackTest {
    private static final TrackPoint START = new TrackPoint(0, 0, 0, TrackEvent.START);
    private static final TrackPoint END = new TrackPoint(1, 0, 1, TrackEvent.END);

    @Test
    void carriesMinimumAndMaximumPointCountsWithoutSemanticValidation() {
        assertEquals(2, new Track(List.of(START, END)).points().size());

        List<TrackPoint> maximum = new ArrayList<>();
        maximum.add(START);
        for (int index = 1; index < 255; index++) {
            maximum.add(new TrackPoint(index, 0, index, TrackEvent.MOVE));
        }
        maximum.add(new TrackPoint(255, 0, 255, TrackEvent.END));
        assertEquals(256, new Track(maximum).points().size());
    }

    @Test
    void makesAStableDefensiveCopy() {
        List<TrackPoint> mutable = new ArrayList<>(List.of(START, END));
        Track track = new Track(mutable);
        mutable.clear();

        assertEquals(2, track.points().size());
        assertThrows(UnsupportedOperationException.class, () -> track.points().clear());
        assertEquals("Track[pointCount=2]", track.toString());
        assertEquals("TrackPoint[REDACTED]", track.points().get(0).toString());
    }

    @Test
    void carriesMalformedStructureSoCoreCanConsumeBeforeValidation() {
        new Track(List.of(
                START,
                new TrackPoint(1, 0, 0, TrackEvent.MOVE),
                new TrackPoint(2, 0, 0, TrackEvent.END)));

        Track backwards = new Track(List.of(
                START,
                new TrackPoint(1, 0, 2, TrackEvent.MOVE),
                new TrackPoint(2, 0, 1, TrackEvent.END)));
        assertEquals(1, backwards.points().get(2).t());
    }

    @Test
    void carriesInvalidStructureAndPointCounts() {
        assertEquals(1, new Track(List.of(START)).points().size());
        assertEquals(3, new Track(List.of(
                START, new TrackPoint(1, 0, 1, TrackEvent.START), END)).points().size());
    }

    @Test
    void carriesOutOfRangePointValuesForPostTakeValidation() {
        TrackPoint point = new TrackPoint(Long.MIN_VALUE, Long.MAX_VALUE, -1, TrackEvent.MOVE);
        assertEquals(Long.MIN_VALUE, point.x());
        assertEquals(Long.MAX_VALUE, point.y());
        assertEquals(-1, point.t());
    }
}
