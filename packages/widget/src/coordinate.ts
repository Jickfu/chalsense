export const COORDINATE_SCALE = 1_000_000;
export const MAX_DPR = 3;

export interface PointerLike {
  readonly clientX: number;
  readonly clientY: number;
}

export interface RectSnapshot {
  readonly width: number;
  readonly height: number;
}

export function roundHalfAwayFromZero(value: number): number {
  if (!Number.isFinite(value)) {
    throw new RangeError("value must be finite");
  }
  return Math.sign(value) * Math.floor(Math.abs(value) + 0.5);
}

export function clamp(value: number, minimum: number, maximum: number): number {
  if (minimum > maximum) {
    throw new RangeError("minimum must not exceed maximum");
  }
  return Math.min(Math.max(value, minimum), maximum);
}

export function pointerDeltaToTrack(
  start: PointerLike,
  current: PointerLike,
  rect: RectSnapshot,
): { readonly x: number; readonly y: number } {
  if (!(rect.width > 0) || !(rect.height > 0)) {
    throw new RangeError("rect dimensions must be positive");
  }
  return {
    x: roundHalfAwayFromZero(((current.clientX - start.clientX) * COORDINATE_SCALE) / rect.width),
    y: roundHalfAwayFromZero(((current.clientY - start.clientY) * COORDINATE_SCALE) / rect.height),
  };
}

export function piecePosition(pieceStartX: number, trackX: number, pieceWidth: number): number {
  return clamp(pieceStartX + trackX, 0, COORDINATE_SCALE - pieceWidth);
}

export function normalizedToLogical(value: number, logicalExtent: number): number {
  return (value * logicalExtent) / COORDINATE_SCALE;
}

export function rationalToInteger(numerator: number, denominator: number): number {
  if (!Number.isSafeInteger(numerator) || !Number.isSafeInteger(denominator) || denominator === 0) {
    throw new RangeError("rational inputs must be safe integers with a non-zero denominator");
  }
  return roundHalfAwayFromZero(numerator / denominator);
}

export function sourceToNormalized(sourceValue: number, sourceExtent: number): number {
  return rationalToInteger(sourceValue * COORDINATE_SCALE, sourceExtent);
}

export function draftTolerance(
  pieceWidth: number,
  ratioNumerator = 8,
  ratioDenominator = 100,
  minimum = 6_250,
  maximum = 18_750,
): number {
  return clamp(rationalToInteger(pieceWidth * ratioNumerator, ratioDenominator), minimum, maximum);
}

export function positionAccepted(finalPieceX: number, pieceTargetX: number, tolerance: number): boolean {
  return Math.abs(finalPieceX - pieceTargetX) <= tolerance;
}

export function backingStoreSize(
  cssWidth: number,
  cssHeight: number,
  devicePixelRatio: number,
  maxDpr = MAX_DPR,
): { readonly backingWidth: number; readonly backingHeight: number; readonly effectiveDpr: number } {
  if (!(cssWidth > 0) || !(cssHeight > 0) || !(maxDpr >= 1)) {
    throw new RangeError("canvas dimensions and maxDpr must be positive");
  }
  const effectiveDpr = clamp(Number.isFinite(devicePixelRatio) ? devicePixelRatio : 1, 1, maxDpr);
  return {
    backingWidth: Math.max(1, roundHalfAwayFromZero(cssWidth * effectiveDpr)),
    backingHeight: Math.max(1, roundHalfAwayFromZero(cssHeight * effectiveDpr)),
    effectiveDpr,
  };
}

export function aspectRatioAccepted(
  cssWidth: number,
  cssHeight: number,
  logicalWidth: number,
  logicalHeight: number,
  relativeTolerance = 0.002,
): boolean {
  if (!(cssWidth > 0) || !(cssHeight > 0) || !(logicalWidth > 0) || !(logicalHeight > 0)) {
    return false;
  }
  const expected = logicalWidth / logicalHeight;
  return Math.abs(cssWidth / cssHeight - expected) / expected <= relativeTolerance;
}
