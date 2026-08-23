import test from "node:test";
import assert from "node:assert/strict";
import {
  MAX_ATTEMPTS_PER_PARTICIPANT,
  acceptanceBounds,
  createAggregate,
  exportAggregate,
  mergeAggregates,
  normalizedPosition,
  recordAttempt,
} from "../calibration-core.mjs";

test("normalizes and clamps piece position", () => {
  assert.equal(normalizedPosition(0, 320, 50), 0);
  assert.equal(normalizedPosition(270, 320, 50), 843_750);
  assert.equal(normalizedPosition(999, 320, 50), 843_750);
});

test("exports only aggregate privacy-safe fields", () => {
  const aggregate = createAggregate();
  recordAttempt(aggregate, {
    absoluteError: 4_250,
    durationMs: 1_200,
    inputType: "mouse",
    displayWidth: 320,
    dpr: 2,
  });
  const exported = exportAggregate(aggregate);
  const parsed = JSON.parse(exported);
  assert.equal(parsed.attempts, 1);
  assert.equal(parsed.errorHistogram["4000-4999"], 1);
  assert.equal(parsed.durationHistogram["1000-1249"], 1);
  assert.equal(parsed.inputType.mouse, 1);
  assert.equal(parsed.privacy.containsFullTrack, false);
  assert.equal(parsed.privacy.containsIdentifier, false);
  assert.equal(Object.hasOwn(parsed, "userAgent"), false);
  assert.equal(Object.hasOwn(parsed, "track"), false);
  assert.equal(Object.hasOwn(parsed, "timestamp"), false);
  assert.equal(Object.hasOwn(parsed, "sessionId"), false);
});

test("rejects invalid attempts", () => {
  assert.throws(() => recordAttempt(createAggregate(), {
    absoluteError: -1,
    durationMs: 1,
    inputType: "mouse",
    displayWidth: 320,
    dpr: 1,
  }), TypeError);

  const aggregate = createAggregate();
  for (let index = 0; index < MAX_ATTEMPTS_PER_PARTICIPANT; index += 1) {
    recordAttempt(aggregate, {
      absoluteError: 0, durationMs: 1, inputType: "mouse", displayWidth: 320, dpr: 1,
    });
  }
  assert.throws(() => recordAttempt(aggregate, {
    absoluteError: 0, durationMs: 1, inputType: "mouse", displayWidth: 320, dpr: 1,
  }), RangeError);
});

test("merges aggregate histograms and reports binned acceptance bounds", () => {
  const first = createAggregate();
  const second = createAggregate();
  recordAttempt(first, { absoluteError: 6_100, durationMs: 500, inputType: "mouse", displayWidth: 320, dpr: 1 });
  recordAttempt(second, { absoluteError: 6_900, durationMs: 750, inputType: "touch", displayWidth: 320, dpr: 2 });
  const merged = mergeAggregates([first, second]);
  assert.equal(merged.attempts, 2);
  assert.deepEqual(acceptanceBounds(merged.errorHistogram, 6_250), {
    definitelyAccepted: 0,
    possiblyAccepted: 2,
  });
});

test("rejects untrusted aggregate keys and inconsistent counts", () => {
  const withIdentifierInBucket = createAggregate();
  withIdentifierInBucket.attempts = 1;
  withIdentifierInBucket.errorHistogram["participant-alice"] = 1;
  withIdentifierInBucket.durationHistogram["0-249"] = 1;
  withIdentifierInBucket.inputType.mouse = 1;
  withIdentifierInBucket.displayWidth["281-360"] = 1;
  withIdentifierInBucket.dpr["<=1.25"] = 1;
  assert.throws(() => mergeAggregates([withIdentifierInBucket]), TypeError);

  const inconsistent = createAggregate();
  inconsistent.attempts = 1;
  assert.throws(() => mergeAggregates([inconsistent]), TypeError);
});
