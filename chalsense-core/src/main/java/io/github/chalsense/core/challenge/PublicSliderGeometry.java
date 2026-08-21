package io.github.chalsense.core.challenge;

import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.protocol.CoordinateSystem;

import java.util.Objects;

/** Public geometry deliberately excluding the answer and acceptance tolerance. */
public record PublicSliderGeometry(
        long coordinateScale,
        int logicalWidth,
        int logicalHeight,
        long pieceStartX,
        long pieceStartY,
        long pieceWidth,
        long pieceHeight) {
    public static PublicSliderGeometry from(GeneratedChallenge generated) {
        Objects.requireNonNull(generated, "generated");
        SliderPuzzleGeometry geometry = generated.geometry();
        return new PublicSliderGeometry(
                CoordinateSystem.normalizedScale(),
                generated.logicalWidth(),
                generated.logicalHeight(),
                geometry.pieceStartX(),
                geometry.pieceStartY(),
                geometry.pieceWidth(),
                geometry.pieceHeight());
    }

    public PublicSliderGeometry {
        if (coordinateScale != CoordinateSystem.normalizedScale()
                || logicalWidth <= 0 || logicalHeight <= 0
                || pieceStartX < 0 || pieceStartY < 0 || pieceWidth <= 0 || pieceHeight <= 0) {
            throw new IllegalArgumentException("invalid public slider geometry");
        }
    }
}
