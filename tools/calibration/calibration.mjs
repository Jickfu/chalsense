import {
  MAX_ATTEMPTS_PER_PARTICIPANT,
  createAggregate,
  exportAggregate,
  normalizedPosition,
  recordAttempt,
} from "./calibration-core.mjs";

const canvas = document.querySelector("canvas");
const context = canvas.getContext("2d");
const consent = document.querySelector("#consent");
const startButton = document.querySelector("#start");
const submitButton = document.querySelector("#submit");
const exportButton = document.querySelector("#export");
const status = document.querySelector("#status");
const count = document.querySelector("#count");
const aggregate = createAggregate();
const pieceWidth = 50;
let pieceX = 30;
let targetX = 220;
let startedAt = 0;
let pointerId;
let pointerOffset = 0;
let inputType = "keyboard";

startButton.addEventListener("click", startAttempt);
submitButton.addEventListener("click", submitAttempt);
exportButton.addEventListener("click", downloadAggregate);
canvas.addEventListener("pointerdown", (event) => {
  if (submitButton.disabled || pointerId !== undefined) return;
  const x = canvasX(event);
  if (x < pieceX || x > pieceX + pieceWidth) return;
  pointerId = event.pointerId;
  pointerOffset = x - pieceX;
  inputType = ["mouse", "touch", "pen"].includes(event.pointerType) ? event.pointerType : "unknown";
  canvas.setPointerCapture(event.pointerId);
});
canvas.addEventListener("pointermove", (event) => {
  if (event.pointerId !== pointerId) return;
  pieceX = clamp(canvasX(event) - pointerOffset, 0, canvas.width - pieceWidth);
  draw();
});
canvas.addEventListener("pointerup", releasePointer);
canvas.addEventListener("pointercancel", releasePointer);
canvas.addEventListener("keydown", (event) => {
  if (submitButton.disabled || !["ArrowLeft", "ArrowRight"].includes(event.key)) return;
  event.preventDefault();
  inputType = "keyboard";
  const direction = event.key === "ArrowLeft" ? -1 : 1;
  pieceX = clamp(pieceX + direction * (event.shiftKey ? 5 : 1), 0, canvas.width - pieceWidth);
  draw();
});

function startAttempt() {
  if (aggregate.attempts >= MAX_ATTEMPTS_PER_PARTICIPANT) {
    status.textContent = "已达到每位参与者 10 次上限，请导出后关闭页面。";
    return;
  }
  if (!consent.checked) {
    status.textContent = "请先确认知情同意。";
    return;
  }
  const random = new Uint32Array(2);
  crypto.getRandomValues(random);
  pieceX = 20 + random[0] % 45;
  targetX = 190 + random[1] % 61;
  inputType = "keyboard";
  startedAt = performance.now();
  submitButton.disabled = false;
  canvas.tabIndex = 0;
  canvas.focus();
  status.textContent = "拖动黄色方块对齐虚线缺口；方向键可微调，Shift 为 5 px。";
  draw();
}

function submitAttempt() {
  if (submitButton.disabled) return;
  const finalNormalized = normalizedPosition(pieceX, canvas.width, pieceWidth);
  const targetNormalized = normalizedPosition(targetX, canvas.width, pieceWidth);
  recordAttempt(aggregate, {
    absoluteError: Math.abs(finalNormalized - targetNormalized),
    durationMs: performance.now() - startedAt,
    inputType,
    displayWidth: canvas.getBoundingClientRect().width,
    dpr: window.devicePixelRatio,
  });
  submitButton.disabled = true;
  exportButton.disabled = false;
  if (aggregate.attempts >= MAX_ATTEMPTS_PER_PARTICIPANT) startButton.disabled = true;
  count.textContent = String(aggregate.attempts);
  status.textContent = "本次已计入本地聚合；请开始下一次。";
}

function downloadAggregate() {
  const blob = new Blob([exportAggregate(aggregate)], { type: "application/json" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = "chalsense-calibration-aggregate.json";
  link.click();
  URL.revokeObjectURL(link.href);
}

function releasePointer(event) {
  if (event.pointerId === pointerId) pointerId = undefined;
}

function canvasX(event) {
  const rect = canvas.getBoundingClientRect();
  return ((event.clientX - rect.left) / rect.width) * canvas.width;
}

function draw() {
  canvas.setAttribute("aria-valuenow", String(Math.round(pieceX)));
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = "#17324d";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = "#284f70";
  for (let x = 0; x < canvas.width; x += 32) context.fillRect(x, 0, 16, canvas.height);
  context.strokeStyle = "#f3f6f8";
  context.setLineDash([6, 5]);
  context.lineWidth = 3;
  context.strokeRect(targetX, 65, pieceWidth, pieceWidth);
  context.setLineDash([]);
  context.fillStyle = "#e5b93f";
  context.fillRect(pieceX, 65, pieceWidth, pieceWidth);
}

function clamp(value, minimum, maximum) {
  return Math.min(Math.max(value, minimum), maximum);
}

draw();
