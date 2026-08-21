package io.github.chalsense.core.coordinate;

import io.github.chalsense.protocol.CoordinateSystem;

import java.math.BigInteger;

/** Deterministic reference math for the approved version 1 coordinate rules. */
public final class CoordinateMath {
    private static final BigInteger TWO = BigInteger.valueOf(2);

    private CoordinateMath() {
    }

    public static long rationalToInteger(long numerator, long denominator) {
        if (denominator == 0) {
            throw new IllegalArgumentException("denominator must not be zero");
        }

        BigInteger signedNumerator = BigInteger.valueOf(numerator);
        BigInteger signedDenominator = BigInteger.valueOf(denominator);
        boolean negative = signedNumerator.signum() * signedDenominator.signum() < 0;
        BigInteger[] quotientAndRemainder = signedNumerator.abs()
                .divideAndRemainder(signedDenominator.abs());
        BigInteger magnitude = quotientAndRemainder[0];
        if (quotientAndRemainder[1].multiply(TWO).compareTo(signedDenominator.abs()) >= 0) {
            magnitude = magnitude.add(BigInteger.ONE);
        }
        return (negative ? magnitude.negate() : magnitude).longValueExact();
    }

    public static long sourceToNormalized(long sourceCoordinate, long sourceSize) {
        if (sourceSize <= 0) {
            throw new IllegalArgumentException("sourceSize must be positive");
        }
        return rationalToInteger(Math.multiplyExact(sourceCoordinate, CoordinateSystem.normalizedScale()), sourceSize);
    }

    public static TrackDelta pointerDeltaToTrack(
            double startClientX,
            double startClientY,
            double endClientX,
            double endClientY,
            double rectangleWidth,
            double rectangleHeight) {
        requirePositiveFinite(rectangleWidth, "rectangleWidth");
        requirePositiveFinite(rectangleHeight, "rectangleHeight");
        long x = roundFinite((endClientX - startClientX) * CoordinateSystem.normalizedScale() / rectangleWidth);
        long y = roundFinite((endClientY - startClientY) * CoordinateSystem.normalizedScale() / rectangleHeight);
        return new TrackDelta(x, y);
    }

    public static long piecePosition(long pieceStartX, long trackX, long pieceWidth) {
        long scale = CoordinateSystem.normalizedScale();
        if (pieceWidth < 0 || pieceWidth > scale) {
            throw new IllegalArgumentException("pieceWidth is outside the normalized coordinate range");
        }
        long unclamped = Math.addExact(pieceStartX, trackX);
        return clamp(unclamped, 0, scale - pieceWidth);
    }

    public static long tolerance(long pieceWidth, long ratioNumerator, long ratioDenominator, long minimum, long maximum) {
        if (pieceWidth < 0 || minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException("invalid tolerance parameters");
        }
        long calculated = rationalToInteger(Math.multiplyExact(pieceWidth, ratioNumerator), ratioDenominator);
        return clamp(calculated, minimum, maximum);
    }

    public static boolean positionAccepted(long finalPieceX, long targetPieceX, long tolerance) {
        if (tolerance < 0) {
            throw new IllegalArgumentException("tolerance must not be negative");
        }
        BigInteger difference = BigInteger.valueOf(finalPieceX).subtract(BigInteger.valueOf(targetPieceX)).abs();
        return difference.compareTo(BigInteger.valueOf(tolerance)) <= 0;
    }

    public static BackingStoreSize backingStoreSize(
            double cssWidth,
            double cssHeight,
            double devicePixelRatio,
            double maximumDevicePixelRatio) {
        requirePositiveFinite(cssWidth, "cssWidth");
        requirePositiveFinite(cssHeight, "cssHeight");
        requirePositiveFinite(devicePixelRatio, "devicePixelRatio");
        requirePositiveFinite(maximumDevicePixelRatio, "maximumDevicePixelRatio");
        double effectiveDevicePixelRatio = Math.min(devicePixelRatio, maximumDevicePixelRatio);
        return new BackingStoreSize(
                Math.toIntExact(roundFinite(cssWidth * effectiveDevicePixelRatio)),
                Math.toIntExact(roundFinite(cssHeight * effectiveDevicePixelRatio)));
    }

    private static long roundFinite(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > Long.MAX_VALUE - 1.0d) {
            throw new IllegalArgumentException("value cannot be represented as a long");
        }
        double magnitude = Math.floor(Math.abs(value) + 0.5d);
        return (long) Math.copySign(magnitude, value);
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    public record TrackDelta(long x, long y) {
    }

    public record BackingStoreSize(int width, int height) {
    }
}

