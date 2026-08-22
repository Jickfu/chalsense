export type ProtocolVersion = "1";
export type ChallengeType = "SLIDER_PUZZLE";
export type ChallengeResourceRole = "BACKGROUND" | "PIECE";
export type TrackEvent = "START" | "MOVE" | "END";

export interface ChallengeResource {
  readonly role: ChallengeResourceRole;
  readonly url: string;
  readonly mediaType: "image/webp" | "image/png";
  readonly pixelWidth: number;
  readonly pixelHeight: number;
}

export interface PublicSliderGeometry {
  readonly coordinateScale: 1_000_000;
  readonly logicalWidth: number;
  readonly logicalHeight: number;
  readonly pieceStartX: number;
  readonly pieceStartY: number;
  readonly pieceWidth: number;
  readonly pieceHeight: number;
}

export interface CreatedChallenge {
  readonly protocolVersion: ProtocolVersion;
  readonly challengeId: string;
  readonly challengeType: ChallengeType;
  readonly issuedAt: number;
  readonly expiresAt: number;
  readonly geometry: PublicSliderGeometry;
  readonly resources: readonly ChallengeResource[];
}

export interface TrackPoint {
  readonly x: number;
  readonly y: number;
  readonly t: number;
  readonly event: TrackEvent;
}

export interface CreateChallengeRequest {
  readonly protocolVersion: ProtocolVersion;
  readonly siteKey: string;
  readonly action: string;
  readonly contextDigest: string;
}

export interface VerifyChallengeRequest {
  readonly protocolVersion: ProtocolVersion;
  readonly siteKey: string;
  readonly challengeId: string;
  readonly solution: {
    readonly finalPieceX: number;
    readonly track: readonly TrackPoint[];
  };
}

export interface VerificationTicketResult {
  readonly protocolVersion: ProtocolVersion;
  readonly verificationTicket: string;
  readonly issuedAt: number;
  readonly expiresAt: number;
}

export interface ChalSenseTransport {
  createChallenge(request: CreateChallengeRequest, signal: AbortSignal): Promise<CreatedChallenge>;
  verifyChallenge(request: VerifyChallengeRequest, signal: AbortSignal): Promise<VerificationTicketResult>;
}

export interface WidgetConfiguration {
  readonly transport: ChalSenseTransport;
  readonly siteKey: string;
  readonly action: string;
  readonly contextDigest: string;
  readonly locale?: "zh-CN" | "en";
  readonly messages?: Partial<WidgetMessages>;
}

export interface WidgetMessages {
  readonly title: string;
  readonly start: string;
  readonly loading: string;
  readonly ready: string;
  readonly dragging: string;
  readonly keyboardHint: string;
  readonly submitting: string;
  readonly success: string;
  readonly failed: string;
  readonly resourceFailed: string;
  readonly invalidConfiguration: string;
  readonly newChallenge: string;
  readonly alternative: string;
  readonly alternativeHint: string;
  readonly sliderLabel: string;
}

export interface ChalSenseSuccessDetail extends VerificationTicketResult {
  /** Receiving this event does not authorize the protected business action. */
  readonly challengeId: string;
}

export interface ChalSenseErrorDetail {
  readonly stage: "CONFIGURATION" | "CREATE" | "RESOURCE" | "VERIFY";
  readonly recoverableWithNewChallenge: boolean;
}
