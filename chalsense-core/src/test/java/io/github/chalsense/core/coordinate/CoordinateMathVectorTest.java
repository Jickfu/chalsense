package io.github.chalsense.core.coordinate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoordinateMathVectorTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> executesEveryFrozenCoordinateVector() throws IOException {
        JsonNode root = loadVectors();
        assertEquals("chalsense-coordinates-v1", root.required("vectorSet").textValue());
        assertEquals("D-014", root.required("approvedByDecision").textValue());
        assertEquals(1_000_000L, root.required("coordinateScale").longValue());

        return Stream.of(root.required("vectors")).flatMap(JsonNode::valueStream).map(vector -> DynamicTest.dynamicTest(
                vector.required("id").textValue(),
                () -> execute(vector)));
    }

    @Test
    void rejectsUndefinedOrUnsafeMathInputs() {
        assertThrows(IllegalArgumentException.class, () -> CoordinateMath.rationalToInteger(1, 0));
        assertThrows(IllegalArgumentException.class, () -> CoordinateMath.sourceToNormalized(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CoordinateMath.pointerDeltaToTrack(0, 0, 1, 1, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> CoordinateMath.piecePosition(0, 0, 1_000_001));
        assertThrows(IllegalArgumentException.class, () -> CoordinateMath.positionAccepted(0, 0, -1));
    }

    private static JsonNode loadVectors() throws IOException {
        try (InputStream input = CoordinateMathVectorTest.class.getResourceAsStream("/coordinates-v1.json")) {
            if (input == null) {
                throw new IllegalStateException("Frozen coordinate vectors are missing from the test classpath");
            }
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static void execute(JsonNode vector) {
        JsonNode input = vector.required("input");
        JsonNode expected = vector.required("expected");
        switch (vector.required("operation").textValue()) {
            case "sourceToNormalizedX" -> assertEquals(
                    expected.longValue(),
                    CoordinateMath.sourceToNormalized(input.required("sourceX").longValue(), input.required("sourceWidth").longValue()));
            case "sourceToNormalizedY" -> assertEquals(
                    expected.longValue(),
                    CoordinateMath.sourceToNormalized(input.required("sourceY").longValue(), input.required("sourceHeight").longValue()));
            case "sourceWidthToNormalized" -> assertEquals(
                    expected.longValue(),
                    CoordinateMath.sourceToNormalized(input.required("objectWidth").longValue(), input.required("sourceWidth").longValue()));
            case "rationalToInteger" -> assertEquals(
                    expected.longValue(),
                    CoordinateMath.rationalToInteger(input.required("numerator").longValue(), input.required("denominator").longValue()));
            case "pointerDeltaToTrack" -> assertTrackDelta(input, expected);
            case "piecePosition" -> assertEquals(
                    expected.longValue(),
                    CoordinateMath.piecePosition(
                            input.required("pieceStartX").longValue(),
                            input.required("trackX").longValue(),
                            input.required("pieceWidth").longValue()));
            case "toleranceDraft1" -> assertEquals(
                    expected.longValue(),
                    CoordinateMath.tolerance(
                            input.required("pieceWidth").longValue(),
                            input.required("ratioNumerator").longValue(),
                            input.required("ratioDenominator").longValue(),
                            input.required("min").longValue(),
                            input.required("max").longValue()));
            case "positionAccepted" -> assertEquals(
                    expected.booleanValue(),
                    CoordinateMath.positionAccepted(
                            input.required("finalPieceX").longValue(),
                            input.required("pieceTargetX").longValue(),
                            input.required("tolerance").longValue()));
            case "backingStoreSize" -> assertBackingStoreSize(input, expected);
            default -> throw new AssertionError("Unsupported vector operation: " + vector.required("operation").textValue());
        }
    }

    private static void assertTrackDelta(JsonNode input, JsonNode expected) {
        JsonNode rectangle = input.required("rect");
        JsonNode start = input.required("start");
        JsonNode end = input.required("end");
        CoordinateMath.TrackDelta actual = CoordinateMath.pointerDeltaToTrack(
                start.required("clientX").doubleValue(),
                start.required("clientY").doubleValue(),
                end.required("clientX").doubleValue(),
                end.required("clientY").doubleValue(),
                rectangle.required("width").doubleValue(),
                rectangle.required("height").doubleValue());
        assertEquals(expected.required("x").longValue(), actual.x());
        assertEquals(expected.required("y").longValue(), actual.y());
    }

    private static void assertBackingStoreSize(JsonNode input, JsonNode expected) {
        CoordinateMath.BackingStoreSize actual = CoordinateMath.backingStoreSize(
                input.required("cssWidth").doubleValue(),
                input.required("cssHeight").doubleValue(),
                input.required("devicePixelRatio").doubleValue(),
                input.required("maxDpr").doubleValue());
        assertEquals(expected.required("backingWidth").intValue(), actual.width());
        assertEquals(expected.required("backingHeight").intValue(), actual.height());
    }
}
