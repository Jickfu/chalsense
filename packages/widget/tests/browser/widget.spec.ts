import { expect, test, type Page } from "@playwright/test";

const SCALE = 1_000_000;
const START_X = 62_500;
const TARGET_X = 593_750;
const PIECE_WIDTH = 156_250;
const PIECE_Y = 388_889;
const PIECE_HEIGHT = 277_778;

async function loadChallenge(page: Page, query = ""): Promise<void> {
  await page.goto(`/${query}`);
  await page.getByRole("button", { name: /开始验证|Start verification/ }).click();
  await expect(page.getByRole("slider", { name: /滑动拼图片|Move the puzzle piece/ })).toBeVisible();
  await expect(page.locator("chalsense-widget").getByRole("status")).toContainText(/拖动拼图片|Drag the puzzle/);
}

async function dragToTarget(page: Page, grabRatio = 0.5): Promise<void> {
  const canvas = page.getByRole("slider");
  const box = await canvas.boundingBox();
  if (box === null) throw new Error("canvas has no bounding box");
  const startX = box.x + box.width * (START_X + PIECE_WIDTH * grabRatio) / SCALE;
  const y = box.y + box.height * (PIECE_Y + PIECE_HEIGHT / 2) / SCALE;
  const endX = startX + box.width * (TARGET_X - START_X) / SCALE;
  await page.mouse.move(startX, y);
  await page.mouse.down();
  await page.mouse.move(endX, y, { steps: 8 });
  await page.mouse.up();
}

async function dragCoordinates(page: Page): Promise<{ startX: number; y: number; endX: number }> {
  const box = await page.getByRole("slider").boundingBox();
  if (box === null) throw new Error("canvas has no bounding box");
  const startX = box.x + box.width * (START_X + PIECE_WIDTH / 2) / SCALE;
  const y = box.y + box.height * (PIECE_Y + PIECE_HEIGHT / 2) / SCALE;
  return { startX, y, endX: startX + box.width * (TARGET_X - START_X) / SCALE };
}

test("HTTP demo loads its ES module and hides the file launch guidance", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator("#launch-help")).toBeHidden();
  await expect(page.getByRole("button", { name: /开始验证|Start verification/ })).toBeVisible();
});

test("pointer drag emits one ticket event using a real grab-point-relative track", async ({ page }) => {
  await loadChallenge(page);
  await dragToTarget(page, 0.1);
  await expect(page.locator("#events")).toContainText("chalsense-success");

  const state = await page.evaluate(() => (window as any).demoState);
  expect(state.createCalls).toBe(1);
  expect(state.verifyCalls).toBe(1);
  expect(state.lastVerify.solution.finalPieceX).toBe(TARGET_X);
  expect(state.lastVerify.solution.track[0]).toEqual({ x: 0, y: 0, t: 0, event: "START" });
  expect(state.lastVerify.solution.track.at(-1).event).toBe("END");
  expect(state.lastVerify.solution.track.every((point: object) =>
    Object.keys(point).sort().join(",") === "event,t,x,y")).toBe(true);
  expect(state.lastSuccess.verificationTicket).toHaveLength(43);
  expect(state.lastSuccess.valid).toBeUndefined();
  expect(state.lastSuccess.fixtureOnlyUnknownField).toBeUndefined();
});

test("keyboard interaction is available but keeps the alternative route visible", async ({ page }) => {
  await loadChallenge(page, "?locale=en");
  const slider = page.getByRole("slider", { name: "Move the puzzle piece" });
  await slider.focus();
  for (let index = 0; index < 68; index += 1) await slider.press("ArrowRight");
  await slider.press("Enter");
  await expect(page.locator("#events")).toContainText("chalsense-success");
  await expect(page.getByRole("button", { name: "Use another verification method" })).toBeVisible();

  const request = await page.evaluate(() => (window as any).demoState.lastVerify);
  expect(request.solution.track.length).toBeGreaterThan(2);
  expect(request.solution.track.length).toBeLessThanOrEqual(256);
  expect(request.solution.track.at(-1).event).toBe("END");
});

test("native touch input follows the same untrusted track schema", async ({ page }) => {
  await loadChallenge(page);
  const { startX, y, endX } = await dragCoordinates(page);
  const session = await page.context().newCDPSession(page);
  await session.send("Input.dispatchTouchEvent", {
    type: "touchStart", touchPoints: [{ x: startX, y, id: 1, radiusX: 4, radiusY: 4 }],
  });
  for (let step = 1; step <= 6; step += 1) {
    await session.send("Input.dispatchTouchEvent", {
      type: "touchMove",
      touchPoints: [{ x: startX + (endX - startX) * step / 6, y, id: 1, radiusX: 4, radiusY: 4 }],
    });
  }
  await session.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await expect(page.locator("#events")).toContainText("chalsense-success");
  const state = await page.evaluate(() => (window as any).demoState);
  expect(state.lastPointerType).toBe("touch");
  expect(Object.keys(state.lastVerify.solution.track[0]).sort()).toEqual(["event", "t", "x", "y"]);
});

test("native pen input does not add pressure or device fields to the protocol", async ({ page }) => {
  await loadChallenge(page);
  const { startX, y, endX } = await dragCoordinates(page);
  const session = await page.context().newCDPSession(page);
  await session.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: startX, y, pointerType: "pen" });
  await session.send("Input.dispatchMouseEvent", {
    type: "mousePressed", x: startX, y, button: "left", buttons: 1, clickCount: 1, pointerType: "pen", force: 0.7,
  });
  await session.send("Input.dispatchMouseEvent", {
    type: "mouseMoved", x: endX, y, button: "left", buttons: 1, pointerType: "pen", force: 0.5,
  });
  await session.send("Input.dispatchMouseEvent", {
    type: "mouseReleased", x: endX, y, button: "left", buttons: 0, clickCount: 1, pointerType: "pen",
  });
  await expect(page.locator("#events")).toContainText("chalsense-success");
  const state = await page.evaluate(() => (window as any).demoState);
  expect(state.lastPointerType).toBe("pen");
  expect(state.lastVerify.solution.track.every((point: object) =>
    Object.keys(point).sort().join(",") === "event,t,x,y")).toBe(true);
});

test("pointer cancellation does not call verify", async ({ page }) => {
  await loadChallenge(page);
  const canvas = page.getByRole("slider");
  const box = await canvas.boundingBox();
  if (box === null) throw new Error("canvas has no bounding box");
  const x = box.x + box.width * (START_X + PIECE_WIDTH / 2) / SCALE;
  const y = box.y + box.height * (PIECE_Y + PIECE_HEIGHT / 2) / SCALE;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await canvas.dispatchEvent("pointercancel", { pointerId: 1, isPrimary: true });
  await page.mouse.up();
  expect(await page.evaluate(() => (window as any).demoState.verifyCalls)).toBe(0);
});

test("a failed verify is sent once and requires a new challenge", async ({ page }) => {
  await loadChallenge(page, "?verify=reject");
  await dragToTarget(page);
  await expect(page.locator("chalsense-widget").getByRole("status")).toContainText("本次挑战不可继续");
  expect(await page.evaluate(() => (window as any).demoState.verifyCalls)).toBe(1);
  await expect(page.getByRole("button", { name: "获取新挑战" })).toBeVisible();
});

test("resource failures abandon the challenge without a placeholder submission", async ({ page }) => {
  await page.goto("/?resource=missing");
  await page.getByRole("button", { name: "开始验证" }).click();
  await expect(page.locator("#events")).toContainText('"stage": "RESOURCE"');
  const state = await page.evaluate(() => (window as any).demoState);
  expect(state.createCalls).toBe(1);
  expect(state.verifyCalls).toBe(0);
});

test("malformed challenge responses fail before loading resources or verifying", async ({ page }) => {
  await page.goto("/?challenge=invalid");
  await page.getByRole("button", { name: "开始验证" }).click();
  await expect(page.locator("#events")).toContainText('"stage": "CREATE"');
  const state = await page.evaluate(() => (window as any).demoState);
  expect(state.createCalls).toBe(1);
  expect(state.verifyCalls).toBe(0);
});

test("alternative verification is a composed public event", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "使用其他验证方式" }).click();
  await expect(page.locator("#events")).toContainText("chalsense-alternative");
  expect(await page.evaluate(() => (window as any).demoState.alternativeCalls)).toBe(1);
});

test("strict CSP loads the packaged Shadow DOM stylesheet without inline allowances", async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  const response = await page.goto("/");
  expect(response?.headers()["content-security-policy"]).toContain("style-src 'self'");
  const stylesheet = page.locator("chalsense-widget").locator("link[rel=stylesheet]");
  await expect.poll(() => stylesheet.evaluate((link: HTMLLinkElement) => link.sheet !== null)).toBe(true);
  expect(consoleErrors).toEqual([]);
});
