import { describe, expect, test } from "vitest";
import { aspectRatioAccepted, backingStoreSize, clamp, pointerDeltaToTrack } from "../../src/coordinate.js";

describe("coordinate preconditions", () => {
  test("rejects invalid rectangles and clamp ranges", () => {
    expect(() => pointerDeltaToTrack({ clientX: 0, clientY: 0 }, { clientX: 1, clientY: 1 }, { width: 0, height: 1 }))
      .toThrow(RangeError);
    expect(() => clamp(1, 2, 1)).toThrow(RangeError);
  });

  test("DPR never enters pointer conversion and is capped", () => {
    const one = backingStoreSize(320, 180, 1);
    const huge = backingStoreSize(320, 180, 100);
    expect(one.effectiveDpr).toBe(1);
    expect(huge).toEqual({ backingWidth: 960, backingHeight: 540, effectiveDpr: 3 });
  });

  test("accepts only matching aspect ratios", () => {
    expect(aspectRatioAccepted(333.3, 187.48125, 320, 180)).toBe(true);
    expect(aspectRatioAccepted(320, 200, 320, 180)).toBe(false);
  });
});
