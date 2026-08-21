export const COORDINATE_SCALE = 1_000_000;
export const DEFAULT_LOGICAL_WIDTH = 320;
export const DEFAULT_LOGICAL_HEIGHT = 180;
export const DEFAULT_MAX_DPR = 3;

function assertFiniteNumber(value, name) {
  if (!Number.isFinite(value)) {
    throw new TypeError(`${name} must be a finite number`);
  }
}

function assertPositive(value, name) {
  assertFiniteNumber(value, name);
  if (value <= 0) {
    throw new RangeError(`${name} must be greater than zero`);
  }
}

export function roundHalfAwayFromZero(value) {
  assertFiniteNumber(value, "value");
  if (value === 0) {
    return 0;
  }
  return Math.sign(value) * Math.floor(Math.abs(value) + 0.5);
}

export function roundRational(numerator, denominator) {
  if (!Number.isSafeInteger(numerator) || !Number.isSafeInteger(denominator)) {
    throw new RangeError("numerator and denominator must be safe integers");
  }
  if (denominator === 0) {
    throw new RangeError("denominator must not be zero");
  }

  let n = BigInt(numerator);
  let d = BigInt(denominator);
  const negative = (n < 0n) !== (d < 0n);
  if (n < 0n) n = -n;
  if (d < 0n) d = -d;

  const quotient = n / d;
  const remainder = n % d;
  const roundedMagnitude = remainder * 2n >= d ? quotient + 1n : quotient;
  const result = negative ? -roundedMagnitude : roundedMagnitude;
  const asNumber = Number(result);
  if (!Number.isSafeInteger(asNumber)) {
    throw new RangeError("rounded result exceeds the safe integer range");
  }
  return asNumber;
}

export function clamp(value, minimum, maximum) {
  assertFiniteNumber(value, "value");
  assertFiniteNumber(minimum, "minimum");
  assertFiniteNumber(maximum, "maximum");
  if (minimum > maximum) {
    throw new RangeError("minimum must not exceed maximum");
  }
  return Math.min(Math.max(value, minimum), maximum);
}

export function sourceToNormalized(sourceCoordinate, sourceSize) {
  if (!Number.isSafeInteger(sourceCoordinate) || !Number.isSafeInteger(sourceSize)) {
    throw new RangeError("source coordinate and size must be safe integers");
  }
  if (sourceSize <= 0) {
    throw new RangeError("source size must be greater than zero");
  }
  const product = sourceCoordinate * COORDINATE_SCALE;
  if (!Number.isSafeInteger(product)) {
    throw new RangeError("source conversion intermediate exceeds the safe integer range");
  }
  return roundRational(product, sourceSize);
}

export function pointerDeltaToTrack(start, current, rect) {
  assertPositive(rect.width, "rect.width");
  assertPositive(rect.height, "rect.height");
  const deltaX = current.clientX - start.clientX;
  const deltaY = current.clientY - start.clientY;
  assertFiniteNumber(deltaX, "pointer delta x");
  assertFiniteNumber(deltaY, "pointer delta y");
  return {
    x: roundHalfAwayFromZero(deltaX * COORDINATE_SCALE / rect.width),
    y: roundHalfAwayFromZero(deltaY * COORDINATE_SCALE / rect.height)
  };
}

export function piecePosition(pieceStartX, trackX, pieceWidth) {
  for (const [name, value] of Object.entries({pieceStartX, trackX, pieceWidth})) {
    if (!Number.isSafeInteger(value)) {
      throw new RangeError(`${name} must be a safe integer`);
    }
  }
  if (pieceWidth < 0 || pieceWidth > COORDINATE_SCALE) {
    throw new RangeError("pieceWidth is outside the normalized background");
  }
  return clamp(pieceStartX + trackX, 0, COORDINATE_SCALE - pieceWidth);
}

export function draftTolerance(pieceWidth) {
  if (!Number.isSafeInteger(pieceWidth) || pieceWidth < 0) {
    throw new RangeError("pieceWidth must be a non-negative safe integer");
  }
  return clamp(roundRational(pieceWidth * 8, 100), 6_250, 18_750);
}

export function positionAccepted(finalPieceX, pieceTargetX, tolerance) {
  for (const [name, value] of Object.entries({finalPieceX, pieceTargetX, tolerance})) {
    if (!Number.isSafeInteger(value)) {
      throw new RangeError(`${name} must be a safe integer`);
    }
  }
  return Math.abs(finalPieceX - pieceTargetX) <= tolerance;
}

export function finalPositionConsistent(submittedFinalPieceX, recomputedFinalPieceX) {
  return Math.abs(submittedFinalPieceX - recomputedFinalPieceX) <= 1;
}

export function normalizedToLogical(value, logicalExtent) {
  assertFiniteNumber(value, "normalized value");
  assertPositive(logicalExtent, "logical extent");
  return value * logicalExtent / COORDINATE_SCALE;
}

export function backingStoreSize(cssWidth, cssHeight, devicePixelRatio, maxDpr = DEFAULT_MAX_DPR) {
  assertPositive(cssWidth, "cssWidth");
  assertPositive(cssHeight, "cssHeight");
  assertPositive(devicePixelRatio, "devicePixelRatio");
  assertPositive(maxDpr, "maxDpr");
  const effectiveDpr = clamp(devicePixelRatio, 1, maxDpr);
  return {
    effectiveDpr,
    backingWidth: Math.max(1, roundHalfAwayFromZero(cssWidth * effectiveDpr)),
    backingHeight: Math.max(1, roundHalfAwayFromZero(cssHeight * effectiveDpr))
  };
}

export function aspectRatioAccepted(rectWidth, rectHeight, logicalWidth, logicalHeight, relativeTolerance = 0.001) {
  assertPositive(rectWidth, "rectWidth");
  assertPositive(rectHeight, "rectHeight");
  assertPositive(logicalWidth, "logicalWidth");
  assertPositive(logicalHeight, "logicalHeight");
  assertFiniteNumber(relativeTolerance, "relativeTolerance");
  const actual = rectWidth / rectHeight;
  const expected = logicalWidth / logicalHeight;
  return Math.abs(actual - expected) / expected <= relativeTolerance;
}
