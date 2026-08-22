import "../dist/index.js";

document.querySelector("#launch-help").hidden = true;

const parameters = new URLSearchParams(location.search);
const state = {
  createCalls: 0,
  verifyCalls: 0,
  alternativeCalls: 0,
  lastPointerType: null,
  lastCreate: null,
  lastVerify: null,
  lastSuccess: null,
};
window.demoState = state;

const challenge = {
  protocolVersion: "1",
  challengeId: "AAAAAAAAAAAAAAAAAAAAAA",
  challengeType: "SLIDER_PUZZLE",
  issuedAt: 1_787_356_800_000,
  expiresAt: 1_787_356_920_000,
  geometry: {
    coordinateScale: 1_000_000,
    logicalWidth: 320,
    logicalHeight: 180,
    pieceStartX: 62_500,
    pieceStartY: 388_889,
    pieceWidth: 156_250,
    pieceHeight: 277_778,
  },
  resources: [
    { role: "BACKGROUND", url: "/fixture/background.png", mediaType: "image/png", pixelWidth: 320, pixelHeight: 180 },
    { role: "PIECE", url: parameters.get("resource") === "missing" ? "/fixture/missing.png" : "/fixture/piece.png", mediaType: "image/png", pixelWidth: 50, pixelHeight: 50 },
  ],
};

const transport = {
  async createChallenge(request, signal) {
    state.createCalls += 1;
    state.lastCreate = request;
    await Promise.resolve();
    if (signal.aborted) throw new DOMException("aborted", "AbortError");
    if (parameters.get("challenge") === "invalid") return { ...challenge, challengeId: "invalid" };
    return challenge;
  },
  async verifyChallenge(request, signal) {
    state.verifyCalls += 1;
    state.lastVerify = request;
    await Promise.resolve();
    if (signal.aborted) throw new DOMException("aborted", "AbortError");
    if (parameters.get("verify") === "reject") throw new Error("fixture rejection");
    const target = 593_750;
    if (Math.abs(request.solution.finalPieceX - target) > 12_500) throw new Error("fixture mismatch");
    return {
      protocolVersion: "1",
      verificationTicket: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
      issuedAt: 1_787_356_801_000,
      expiresAt: 1_787_356_861_000,
      fixtureOnlyUnknownField: "must-not-reach-the-public-event",
    };
  },
};

const widget = document.querySelector("chalsense-widget");
const output = document.querySelector("#events");
widget.configure({
  transport,
  siteKey: "site_demo_01",
  action: "login",
  contextDigest: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  locale: parameters.get("locale") === "en" ? "en" : "zh-CN",
});
widget.shadowRoot.querySelector("canvas").addEventListener("pointerdown", (event) => {
  state.lastPointerType = event.pointerType;
});
widget.addEventListener("chalsense-success", (event) => {
  state.lastSuccess = event.detail;
  output.textContent = JSON.stringify({ event: "chalsense-success", detail: event.detail }, null, 2);
});
widget.addEventListener("chalsense-error", (event) => {
  output.textContent = JSON.stringify({ event: "chalsense-error", detail: event.detail }, null, 2);
});
widget.addEventListener("chalsense-alternative", () => {
  state.alternativeCalls += 1;
  output.textContent = JSON.stringify({ event: "chalsense-alternative" }, null, 2);
});
