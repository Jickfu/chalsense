package io.github.chalsense.core.state;

import io.github.chalsense.protocol.CoordinateSystem;

/** Server-authoritative geometry and tolerance retained with a slider challenge. */
public record SliderPuzzleGeometry(
        long pieceStartX,
        long pieceTargetX,
        long pieceStartY,
        long pieceWidth,
        long pieceHeight,
        long tolerance) {
    public SliderPuzzleGeometry {
        long scale = CoordinateSystem.normalizedScale();
        if (pieceStartX < 0 || pieceTargetX < 0 || pieceStartY < 0
                || pieceWidth <= 0 || pieceHeight <= 0
                || pieceStartX > scale - pieceWidth
                || pieceTargetX > scale - pieceWidth
                || pieceStartY > scale - pieceHeight
                || tolerance < 0 || tolerance > scale) {
            throw new IllegalArgumentException("invalid slider puzzle geometry");
        }
    }
}

