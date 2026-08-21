import {
  COORDINATE_SCALE,
  DEFAULT_LOGICAL_HEIGHT,
  DEFAULT_LOGICAL_WIDTH,
  aspectRatioAccepted,
  backingStoreSize,
  draftTolerance,
  normalizedToLogical,
  piecePosition,
  pointerDeltaToTrack,
  positionAccepted
} from "./coordinate-core.mjs";

const geometry = Object.freeze({
  pieceStartX: 62_500,
  pieceTargetX: 593_750,
  pieceStartY: 388_889,
  pieceWidth: 156_250,
  pieceHeight: 277_778
});
const tolerance = draftTolerance(geometry.pieceWidth);

const canvas = document.querySelector("#challenge");
const shell = document.querySelector("#canvas-shell");
const widthSelect = document.querySelector("#css-width");
const dprSelect = document.querySelector("#dpr");
const statusOutput = document.querySelector("#status");
const rectOutput = document.querySelector("#rect");
const backingOutput = document.querySelector("#backing");
const finalXOutput = document.querySelector("#final-x");
const errorOutput = document.querySelector("#error");
const toleranceOutput = document.querySelector("#tolerance");
const pointsOutput = document.querySelector("#points");
const resultOutput = document.querySelector("#result");
const matrixOutput = document.querySelector("#matrix-output");

let currentPieceX = geometry.pieceStartX;
let drag = null;
let suppressLostCapture = false;

function selectedDpr() {
  return dprSelect.value === "actual" ? window.devicePixelRatio : Number(dprSelect.value);
}

function canvasCssSize() {
  const width = Number(widthSelect.value);
  return {width, height: width * DEFAULT_LOGICAL_HEIGHT / DEFAULT_LOGICAL_WIDTH};
}

function configureCanvas() {
  const css = canvasCssSize();
  shell.style.width = `${css.width}px`;
  canvas.style.width = `${css.width}px`;
  canvas.style.height = `${css.height}px`;
  const backing = backingStoreSize(css.width, css.height, selectedDpr());
  canvas.width = backing.backingWidth;
  canvas.height = backing.backingHeight;
  backingOutput.textContent = `${backing.backingWidth} × ${backing.backingHeight} @ ${backing.effectiveDpr}`;
  const rect = canvas.getBoundingClientRect();
  rectOutput.textContent = `${rect.width.toFixed(3)} × ${rect.height.toFixed(3)} CSS px`;
  draw();
}

function logicalGeometry() {
  return {
    startX: normalizedToLogical(geometry.pieceStartX, DEFAULT_LOGICAL_WIDTH),
    targetX: normalizedToLogical(geometry.pieceTargetX, DEFAULT_LOGICAL_WIDTH),
    y: normalizedToLogical(geometry.pieceStartY, DEFAULT_LOGICAL_HEIGHT),
    width: normalizedToLogical(geometry.pieceWidth, DEFAULT_LOGICAL_WIDTH),
    height: normalizedToLogical(geometry.pieceHeight, DEFAULT_LOGICAL_HEIGHT),
    currentX: normalizedToLogical(currentPieceX, DEFAULT_LOGICAL_WIDTH)
  };
}

function draw() {
  const context = canvas.getContext("2d");
  const g = logicalGeometry();
  context.setTransform(canvas.width / DEFAULT_LOGICAL_WIDTH, 0, 0, canvas.height / DEFAULT_LOGICAL_HEIGHT, 0, 0);
  context.clearRect(0, 0, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT);

  const gradient = context.createLinearGradient(0, 0, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT);
  gradient.addColorStop(0, "#dbeafe");
  gradient.addColorStop(1, "#a7c7ef");
  context.fillStyle = gradient;
  context.fillRect(0, 0, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT);

  context.fillStyle = "rgba(255,255,255,.42)";
  context.beginPath(); context.arc(64, 46, 28, 0, Math.PI * 2); context.fill();
  context.fillStyle = "rgba(22,99,199,.16)";
  context.fillRect(18, 126, 284, 30);
  context.fillStyle = "rgba(255,255,255,.3)";
  context.fillRect(142, 20, 138, 28);

  context.save();
  context.setLineDash([5, 4]);
  context.lineWidth = 2;
  context.strokeStyle = "#0d4f9f";
  context.fillStyle = "rgba(255,255,255,.28)";
  context.fillRect(g.targetX, g.y, g.width, g.height);
  context.strokeRect(g.targetX, g.y, g.width, g.height);
  context.restore();

  context.fillStyle = "#1769d2";
  context.strokeStyle = "#083f86";
  context.lineWidth = 1.5;
  context.fillRect(g.currentX, g.y, g.width, g.height);
  context.strokeRect(g.currentX, g.y, g.width, g.height);
  context.fillStyle = "rgba(255,255,255,.8)";
  context.beginPath();
  context.arc(g.currentX + g.width / 2, g.y + g.height / 2, 5, 0, Math.PI * 2);
  context.fill();
}

function normalizedPointerPosition(event, rect) {
  return {
    x: (event.clientX - rect.left) * COORDINATE_SCALE / rect.width,
    y: (event.clientY - rect.top) * COORDINATE_SCALE / rect.height
  };
}

function pointerInsidePiece(event, rect) {
  const point = normalizedPointerPosition(event, rect);
  return point.x >= currentPieceX && point.x <= currentPieceX + geometry.pieceWidth &&
    point.y >= geometry.pieceStartY && point.y <= geometry.pieceStartY + geometry.pieceHeight;
}

function updateTelemetry() {
  finalXOutput.textContent = String(currentPieceX);
  errorOutput.textContent = String(Math.abs(currentPieceX - geometry.pieceTargetX));
  toleranceOutput.textContent = String(tolerance);
  pointsOutput.textContent = String(drag?.track.length ?? 0);
}

function cancelDrag(reason) {
  if (!drag) return;
  drag = null;
  canvas.classList.remove("dragging");
  statusOutput.textContent = reason;
  resultOutput.textContent = "本次拖动已取消，未提交";
  resultOutput.className = "result neutral";
  updateTelemetry();
}

function reset() {
  cancelDrag("已重置");
  currentPieceX = geometry.pieceStartX;
  statusOutput.textContent = "等待拖动";
  resultOutput.textContent = "尚未提交";
  resultOutput.className = "result neutral";
  updateTelemetry();
  configureCanvas();
}

canvas.addEventListener("pointerdown", event => {
  if (drag || !event.isPrimary) return;
  const rect = canvas.getBoundingClientRect();
  if (!aspectRatioAccepted(rect.width, rect.height, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT)) {
    statusOutput.textContent = "内容矩形宽高比无效";
    return;
  }
  if (!pointerInsidePiece(event, rect)) {
    statusOutput.textContent = "请从蓝色拼图片内部按下";
    return;
  }
  canvas.setPointerCapture(event.pointerId);
  drag = {
    pointerId: event.pointerId,
    start: {clientX: event.clientX, clientY: event.clientY},
    startTime: event.timeStamp,
    rect: {left: rect.left, top: rect.top, width: rect.width, height: rect.height},
    track: [{x: 0, y: 0, t: 0, event: "START"}]
  };
  canvas.classList.add("dragging");
  statusOutput.textContent = "拖动中";
  resultOutput.textContent = "尚未提交";
  resultOutput.className = "result neutral";
  updateTelemetry();
});

canvas.addEventListener("pointermove", event => {
  if (!drag || event.pointerId !== drag.pointerId) return;
  const delta = pointerDeltaToTrack(drag.start, event, drag.rect);
  currentPieceX = piecePosition(geometry.pieceStartX, delta.x, geometry.pieceWidth);
  const t = Math.max(0, Math.round(event.timeStamp - drag.startTime));
  if (drag.track.length < 255) {
    drag.track.push({...delta, t, event: "MOVE"});
  }
  updateTelemetry();
  draw();
});

canvas.addEventListener("pointerup", event => {
  if (!drag || event.pointerId !== drag.pointerId) return;
  const delta = pointerDeltaToTrack(drag.start, event, drag.rect);
  currentPieceX = piecePosition(geometry.pieceStartX, delta.x, geometry.pieceWidth);
  drag.track.push({...delta, t: Math.max(0, Math.round(event.timeStamp - drag.startTime)), event: "END"});
  const pointCount = drag.track.length;
  const accepted = positionAccepted(currentPieceX, geometry.pieceTargetX, tolerance);
  suppressLostCapture = true;
  canvas.releasePointerCapture(event.pointerId);
  drag = null;
  canvas.classList.remove("dragging");
  statusOutput.textContent = accepted ? "位置匹配" : "位置不匹配";
  resultOutput.textContent = accepted ? "PASS · 仅表示坐标落入原型容差" : "FAIL · 坐标位于原型容差外";
  resultOutput.className = accepted ? "result pass" : "result fail";
  pointsOutput.textContent = String(pointCount);
  updateTelemetry();
  pointsOutput.textContent = String(pointCount);
  draw();
  queueMicrotask(() => { suppressLostCapture = false; });
});

canvas.addEventListener("pointercancel", () => cancelDrag("pointercancel"));
canvas.addEventListener("lostpointercapture", () => {
  if (!suppressLostCapture) cancelDrag("失去 pointer capture");
});
document.addEventListener("visibilitychange", () => {
  if (document.hidden) cancelDrag("页面隐藏");
});

widthSelect.addEventListener("change", () => {
  cancelDrag("拖动中 resize，已取消");
  configureCanvas();
});
dprSelect.addEventListener("change", configureCanvas);
document.querySelector("#reset").addEventListener("click", reset);

function runMatrix() {
  const widths = [240, 320, 333.3, 480];
  const dprs = [1, 1.25, 1.5, 2, 3];
  const grabs = [0.1, 0.5, 0.9];
  const targetDelta = geometry.pieceTargetX - geometry.pieceStartX;
  const failures = [];
  let cases = 0;
  for (const width of widths) {
    const height = width * DEFAULT_LOGICAL_HEIGHT / DEFAULT_LOGICAL_WIDTH;
    for (const dpr of dprs) {
      const backing = backingStoreSize(width, height, dpr);
      if (backing.backingWidth < 1 || backing.backingHeight < 1) failures.push({width, dpr, backing});
      for (const grab of grabs) {
        const startX = width * (geometry.pieceStartX + geometry.pieceWidth * grab) / COORDINATE_SCALE;
        const endX = startX + width * targetDelta / COORDINATE_SCALE;
        const delta = pointerDeltaToTrack(
          {clientX: startX, clientY: height / 2},
          {clientX: endX, clientY: height / 2},
          {width, height}
        );
        if (piecePosition(geometry.pieceStartX, delta.x, geometry.pieceWidth) !== geometry.pieceTargetX) {
          failures.push({width, dpr, grab, delta});
        }
        cases += 1;
      }
    }
  }
  matrixOutput.textContent = JSON.stringify({cases, failures: failures.length, status: failures.length ? "FAIL" : "PASS"}, null, 2);
}

document.querySelector("#run-matrix").addEventListener("click", runMatrix);
window.addEventListener("resize", () => {
  if (drag) cancelDrag("窗口 resize，已取消");
  configureCanvas();
});

reset();
