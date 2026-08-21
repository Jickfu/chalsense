import fs from "node:fs";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {
  COORDINATE_SCALE,
  backingStoreSize,
  draftTolerance,
  piecePosition,
  pointerDeltaToTrack,
  positionAccepted,
  roundRational,
  sourceToNormalized
} from "./coordinate-core.mjs";

const directory = path.dirname(fileURLToPath(import.meta.url));
const vectorPath = path.resolve(directory, "../../docs/test-vectors/coordinates-v1.json");
const vectorSet = JSON.parse(fs.readFileSync(vectorPath, "utf8"));

function deepEqual(actual, expected) {
  return JSON.stringify(actual) === JSON.stringify(expected);
}

function evaluate(vector) {
  const input = vector.input;
  switch (vector.operation) {
    case "sourceToNormalizedX":
      return sourceToNormalized(input.sourceX, input.sourceWidth);
    case "sourceToNormalizedY":
      return sourceToNormalized(input.sourceY, input.sourceHeight);
    case "sourceWidthToNormalized":
      return sourceToNormalized(input.objectWidth, input.sourceWidth);
    case "rationalToInteger":
      return roundRational(input.numerator, input.denominator);
    case "pointerDeltaToTrack":
      return pointerDeltaToTrack(input.start, input.end, input.rect);
    case "piecePosition":
      return piecePosition(input.pieceStartX, input.trackX, input.pieceWidth);
    case "toleranceDraft1":
      return draftTolerance(input.pieceWidth);
    case "positionAccepted":
      return positionAccepted(input.finalPieceX, input.pieceTargetX, input.tolerance);
    case "backingStoreSize": {
      const result = backingStoreSize(input.cssWidth, input.cssHeight, input.devicePixelRatio, input.maxDpr);
      return {backingWidth: result.backingWidth, backingHeight: result.backingHeight};
    }
    default:
      throw new Error(`Unsupported operation: ${vector.operation}`);
  }
}

const failures = [];
for (const vector of vectorSet.vectors) {
  const actual = evaluate(vector);
  if (!deepEqual(actual, vector.expected)) {
    failures.push({id: vector.id, expected: vector.expected, actual});
  }
}

const fixture = {
  pieceStartX: 62_500,
  pieceTargetX: 593_750,
  pieceWidth: 156_250
};
const targetDelta = fixture.pieceTargetX - fixture.pieceStartX;
const cssWidths = [240, 320, 333.3, 480];
const dprs = [1, 1.25, 1.5, 2, 3];
const grabFractions = [0.1, 0.5, 0.9];
let matrixCases = 0;

for (const cssWidth of cssWidths) {
  const cssHeight = cssWidth * 180 / 320;
  for (const dpr of dprs) {
    const backing = backingStoreSize(cssWidth, cssHeight, dpr);
    if (backing.backingWidth <= 0 || backing.backingHeight <= 0) {
      failures.push({id: "matrix-backing-store", cssWidth, dpr, backing});
    }
    for (const grabFraction of grabFractions) {
      const grabX = cssWidth * (fixture.pieceStartX + fixture.pieceWidth * grabFraction) / COORDINATE_SCALE;
      const travelCss = cssWidth * targetDelta / COORDINATE_SCALE;
      const track = pointerDeltaToTrack(
        {clientX: grabX, clientY: cssHeight / 2},
        {clientX: grabX + travelCss, clientY: cssHeight / 2},
        {width: cssWidth, height: cssHeight}
      );
      const finalX = piecePosition(fixture.pieceStartX, track.x, fixture.pieceWidth);
      if (finalX !== fixture.pieceTargetX || track.y !== 0) {
        failures.push({id: "matrix-grab-point", cssWidth, dpr, grabFraction, track, finalX});
      }
      matrixCases += 1;
    }
  }
}

const report = {
  vectorSet: vectorSet.vectorSet,
  vectorCases: vectorSet.vectors.length,
  matrixCases,
  totalCases: vectorSet.vectors.length + matrixCases,
  failures
};

console.log(JSON.stringify(report, null, 2));
if (failures.length > 0) {
  process.exitCode = 1;
}
