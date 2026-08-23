export const COORDINATE_SCALE = 1_000_000;
export const MAX_ATTEMPTS_PER_PARTICIPANT = 10;

const POLICY_CANDIDATE = "slider-tolerance-8pct-clamp-6250-18750";
const ALLOWED_KEYS = {
  errorHistogram: new Set([...rangeKeys(1_000, 50_000), "50000+"]),
  durationHistogram: new Set([...rangeKeys(250, 10_000), "10000+"]),
  inputType: new Set(["mouse", "touch", "pen", "keyboard", "unknown"]),
  displayWidth: new Set(["<=280", "281-360", ">360", "unknown"]),
  dpr: new Set(["<=1.25", "1.26-2", ">2", "unknown"]),
};

export function createAggregate() {
  return {
    schemaVersion: 1,
    policyCandidate: POLICY_CANDIDATE,
    coordinateScale: COORDINATE_SCALE,
    attempts: 0,
    errorHistogram: {},
    durationHistogram: {},
    inputType: {},
    displayWidth: {},
    dpr: {},
  };
}

export function recordAttempt(aggregate, attempt) {
  if (aggregate.attempts >= MAX_ATTEMPTS_PER_PARTICIPANT) {
    throw new RangeError("participant attempt limit reached");
  }
  if (!Number.isInteger(attempt.absoluteError) || attempt.absoluteError < 0
      || !Number.isFinite(attempt.durationMs) || attempt.durationMs < 0) {
    throw new TypeError("invalid calibration attempt");
  }
  aggregate.attempts += 1;
  increment(aggregate.errorHistogram, bucket(attempt.absoluteError, 1_000, 50_000));
  increment(aggregate.durationHistogram, bucket(Math.round(attempt.durationMs), 250, 10_000));
  increment(aggregate.inputType, accepted(attempt.inputType, ["mouse", "touch", "pen", "keyboard"]));
  increment(aggregate.displayWidth, displayWidthBucket(attempt.displayWidth));
  increment(aggregate.dpr, dprBucket(attempt.dpr));
  return aggregate;
}

export function exportAggregate(aggregate) {
  return JSON.stringify({
    ...aggregate,
    privacy: {
      containsFullTrack: false,
      containsIdentifier: false,
      containsUserAgent: false,
      containsNetworkAddress: false,
      minimumPublicCellSize: 20,
    },
  }, null, 2) + "\n";
}

export function mergeAggregates(aggregates) {
  const merged = createAggregate();
  for (const aggregate of aggregates) {
    validateParticipantAggregate(aggregate);
    merged.attempts += aggregate.attempts;
    for (const field of ["errorHistogram", "durationHistogram", "inputType", "displayWidth", "dpr"]) {
      for (const [key, value] of Object.entries(aggregate[field] ?? {})) {
        merged[field][key] = (merged[field][key] ?? 0) + value;
      }
    }
  }
  return merged;
}

function validateParticipantAggregate(aggregate) {
  if (aggregate?.schemaVersion !== 1 || aggregate.coordinateScale !== COORDINATE_SCALE
      || aggregate.policyCandidate !== POLICY_CANDIDATE
      || !Number.isInteger(aggregate.attempts) || aggregate.attempts < 0
      || aggregate.attempts > MAX_ATTEMPTS_PER_PARTICIPANT) {
    throw new TypeError("unsupported calibration aggregate");
  }
  for (const [field, allowedKeys] of Object.entries(ALLOWED_KEYS)) {
    const histogram = aggregate[field];
    if (histogram === null || typeof histogram !== "object" || Array.isArray(histogram)) {
      throw new TypeError(`invalid ${field}`);
    }
    let total = 0;
    for (const [key, value] of Object.entries(histogram)) {
      if (!allowedKeys.has(key) || !Number.isInteger(value) || value < 0) {
        throw new TypeError(`invalid ${field} entry`);
      }
      total += value;
    }
    if (total !== aggregate.attempts) throw new TypeError(`inconsistent ${field} count`);
  }
}

export function acceptanceBounds(errorHistogram, tolerance) {
  let definitelyAccepted = 0;
  let possiblyAccepted = 0;
  for (const [range, count] of Object.entries(errorHistogram)) {
    const [lowerText, upperText] = range.endsWith("+")
      ? [range.slice(0, -1), "Infinity"] : range.split("-");
    const lower = Number(lowerText);
    const upper = upperText === "Infinity" ? Infinity : Number(upperText);
    if (upper <= tolerance) definitelyAccepted += count;
    if (lower <= tolerance) possiblyAccepted += count;
  }
  return { definitelyAccepted, possiblyAccepted };
}

export function normalizedPosition(pixelX, width, pieceWidth) {
  if (!(width > pieceWidth) || !Number.isFinite(pixelX)) throw new TypeError("invalid geometry");
  const clamped = Math.min(Math.max(pixelX, 0), width - pieceWidth);
  return Math.round((clamped / width) * COORDINATE_SCALE);
}

function bucket(value, step, maximum) {
  if (value >= maximum) return `${maximum}+`;
  const lower = Math.floor(value / step) * step;
  return `${lower}-${lower + step - 1}`;
}

function displayWidthBucket(value) {
  if (!(value > 0)) return "unknown";
  if (value <= 280) return "<=280";
  if (value <= 360) return "281-360";
  return ">360";
}

function dprBucket(value) {
  if (!(value > 0)) return "unknown";
  if (value <= 1.25) return "<=1.25";
  if (value <= 2) return "1.26-2";
  return ">2";
}

function accepted(value, values) {
  return values.includes(value) ? value : "unknown";
}

function increment(target, key) {
  target[key] = (target[key] ?? 0) + 1;
}

function rangeKeys(step, maximum) {
  const keys = [];
  for (let lower = 0; lower < maximum; lower += step) {
    keys.push(`${lower}-${lower + step - 1}`);
  }
  return keys;
}
