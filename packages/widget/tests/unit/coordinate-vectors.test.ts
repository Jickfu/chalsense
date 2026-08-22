import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, test } from "vitest";
import {
  backingStoreSize,
  draftTolerance,
  piecePosition,
  pointerDeltaToTrack,
  positionAccepted,
  rationalToInteger,
  sourceToNormalized,
} from "../../src/coordinate.js";

interface Vector {
  readonly id: string;
  readonly operation: string;
  readonly input: Record<string, any>;
  readonly expected: any;
}

const vectorPath = fileURLToPath(new URL("../../../../docs/test-vectors/coordinates-v1.json", import.meta.url));
const vectors = (JSON.parse(readFileSync(vectorPath, "utf8")) as { vectors: Vector[] }).vectors;

describe("D-014 frozen coordinate vectors", () => {
  for (const vector of vectors) {
    test(vector.id, () => {
      const input = vector.input;
      switch (vector.operation) {
        case "sourceToNormalizedX":
          expect(sourceToNormalized(input.sourceX, input.sourceWidth)).toBe(vector.expected);
          break;
        case "sourceToNormalizedY":
          expect(sourceToNormalized(input.sourceY, input.sourceHeight)).toBe(vector.expected);
          break;
        case "sourceWidthToNormalized":
          expect(sourceToNormalized(input.objectWidth, input.sourceWidth)).toBe(vector.expected);
          break;
        case "rationalToInteger":
          expect(rationalToInteger(input.numerator, input.denominator)).toBe(vector.expected);
          break;
        case "pointerDeltaToTrack":
          expect(pointerDeltaToTrack(input.start, input.end, input.rect)).toEqual(vector.expected);
          break;
        case "piecePosition":
          expect(piecePosition(input.pieceStartX, input.trackX, input.pieceWidth)).toBe(vector.expected);
          break;
        case "toleranceDraft1":
          expect(draftTolerance(
            input.pieceWidth, input.ratioNumerator, input.ratioDenominator, input.min, input.max,
          )).toBe(vector.expected);
          break;
        case "positionAccepted":
          expect(positionAccepted(input.finalPieceX, input.pieceTargetX, input.tolerance)).toBe(vector.expected);
          break;
        case "backingStoreSize": {
          const result = backingStoreSize(input.cssWidth, input.cssHeight, input.devicePixelRatio, input.maxDpr);
          expect({ backingWidth: result.backingWidth, backingHeight: result.backingHeight }).toEqual(vector.expected);
          break;
        }
        default:
          throw new Error(`unknown coordinate operation: ${vector.operation}`);
      }
    });
  }
});
