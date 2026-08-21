package io.github.chalsense.core.verify;

import io.github.chalsense.protocol.Track;
import io.github.chalsense.protocol.TrackEvent;
import io.github.chalsense.protocol.TrackPoint;

import java.util.List;

final class TrackValidator {
    private static final int MINIMUM_POINTS = 2;
    private static final int MAXIMUM_POINTS = 256;
    private static final long MAXIMUM_TIME = 30_000L;
    private static final long MAXIMUM_ABSOLUTE_COORDINATE = 2_000_000L;

    private TrackValidator() {
    }

    static boolean isStructurallyValid(Track track) {
        List<TrackPoint> points = track.points();
        if (points.size() < MINIMUM_POINTS || points.size() > MAXIMUM_POINTS) {
            return false;
        }
        TrackPoint first = points.get(0);
        if (first.x() != 0 || first.y() != 0 || first.t() != 0 || first.event() != TrackEvent.START) {
            return false;
        }
        if (points.get(points.size() - 1).event() != TrackEvent.END) {
            return false;
        }

        long previousTime = 0;
        for (int index = 0; index < points.size(); index++) {
            TrackPoint point = points.get(index);
            if (!coordinateInRange(point.x()) || !coordinateInRange(point.y())
                    || point.t() < 0 || point.t() > MAXIMUM_TIME
                    || point.t() < previousTime) {
                return false;
            }
            if (index > 0 && index < points.size() - 1 && point.event() != TrackEvent.MOVE) {
                return false;
            }
            previousTime = point.t();
        }
        return true;
    }

    private static boolean coordinateInRange(long coordinate) {
        return coordinate >= -MAXIMUM_ABSOLUTE_COORDINATE && coordinate <= MAXIMUM_ABSOLUTE_COORDINATE;
    }
}

