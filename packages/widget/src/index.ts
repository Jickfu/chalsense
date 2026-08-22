export { ChalSenseWidget } from "./chalsense-widget.js";
export { createHttpTransport } from "./http-transport.js";
export type { HttpTransportConfiguration } from "./http-transport.js";
export {
  COORDINATE_SCALE,
  MAX_DPR,
  aspectRatioAccepted,
  backingStoreSize,
  clamp,
  draftTolerance,
  normalizedToLogical,
  piecePosition,
  pointerDeltaToTrack,
  positionAccepted,
  rationalToInteger,
  roundHalfAwayFromZero,
  sourceToNormalized,
} from "./coordinate.js";
export type {
  ChalSenseErrorDetail,
  ChalSenseSuccessDetail,
  ChalSenseTransport,
  ChallengeResource,
  CreatedChallenge,
  CreateChallengeRequest,
  PublicSliderGeometry,
  TrackPoint,
  VerificationTicketResult,
  VerifyChallengeRequest,
  WidgetConfiguration,
  WidgetMessages,
} from "./types.js";

import { ChalSenseWidget } from "./chalsense-widget.js";

if (!customElements.get("chalsense-widget")) {
  customElements.define("chalsense-widget", ChalSenseWidget);
}

declare global {
  interface HTMLElementTagNameMap {
    "chalsense-widget": ChalSenseWidget;
  }
}
