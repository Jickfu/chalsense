import { COORDINATE_SCALE } from "./coordinate.js";
import type {
  CreatedChallenge,
  VerificationTicketResult,
  WidgetConfiguration,
} from "./types.js";

const SITE_KEY = /^[A-Za-z0-9_-]{8,64}$/;
const ACTION = /^[a-z][a-z0-9._-]{0,63}$/;
const BASE64URL_32 = /^[A-Za-z0-9_-]{43}$/;
const BASE64URL_16 = /^[A-Za-z0-9_-]{22}$/;
const SAFE_INTEGER = Number.MAX_SAFE_INTEGER;

function integerIn(value: unknown, minimum: number, maximum: number): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= minimum && value <= maximum;
}

export function configurationAccepted(configuration: WidgetConfiguration | undefined): configuration is WidgetConfiguration {
  return configuration !== undefined
    && typeof configuration.transport?.createChallenge === "function"
    && typeof configuration.transport?.verifyChallenge === "function"
    && SITE_KEY.test(configuration.siteKey)
    && ACTION.test(configuration.action)
    && BASE64URL_32.test(configuration.contextDigest)
    && (configuration.locale === undefined || configuration.locale === "zh-CN" || configuration.locale === "en");
}

export function challengeAccepted(value: unknown): value is CreatedChallenge {
  if (typeof value !== "object" || value === null) return false;
  const challenge = value as Partial<CreatedChallenge>;
  if (challenge.protocolVersion !== "1" || challenge.challengeType !== "SLIDER_PUZZLE"
      || typeof challenge.challengeId !== "string" || !BASE64URL_16.test(challenge.challengeId)
      || !integerIn(challenge.issuedAt, 0, SAFE_INTEGER)
      || !integerIn(challenge.expiresAt, 1, SAFE_INTEGER) || challenge.expiresAt <= challenge.issuedAt
      || typeof challenge.geometry !== "object" || challenge.geometry === null
      || !Array.isArray(challenge.resources) || challenge.resources.length !== 2) {
    return false;
  }
  const geometry = challenge.geometry;
  if (geometry.coordinateScale !== COORDINATE_SCALE
      || !integerIn(geometry.logicalWidth, 1, 4096)
      || !integerIn(geometry.logicalHeight, 1, 4096)
      || !integerIn(geometry.pieceStartX, 0, COORDINATE_SCALE)
      || !integerIn(geometry.pieceStartY, 0, COORDINATE_SCALE)
      || !integerIn(geometry.pieceWidth, 1, COORDINATE_SCALE)
      || !integerIn(geometry.pieceHeight, 1, COORDINATE_SCALE)
      || geometry.pieceStartX + geometry.pieceWidth > COORDINATE_SCALE
      || geometry.pieceStartY + geometry.pieceHeight > COORDINATE_SCALE) {
    return false;
  }
  const roles = new Set<string>();
  for (const resource of challenge.resources) {
    if (typeof resource !== "object" || resource === null
        || (resource.role !== "BACKGROUND" && resource.role !== "PIECE")
        || roles.has(resource.role)
        || (resource.mediaType !== "image/webp" && resource.mediaType !== "image/png")
        || (resource.role === "PIECE" && resource.mediaType !== "image/png")
        || typeof resource.url !== "string" || !resourceUrlAccepted(resource.url)
        || !integerIn(resource.pixelWidth, 1, 8192)
        || !integerIn(resource.pixelHeight, 1, 8192)) {
      return false;
    }
    roles.add(resource.role);
  }
  return roles.has("BACKGROUND") && roles.has("PIECE");
}

function resourceUrlAccepted(raw: string): boolean {
  if (raw.length === 0 || raw.length > 2048 || /[\u0000-\u001f\u007f]/u.test(raw)) return false;
  if (raw.startsWith("//") || raw.startsWith("\\\\")) return false;
  try {
    const isAbsolute = /^[A-Za-z][A-Za-z0-9+.-]*:/u.test(raw);
    const absolute = isAbsolute ? new URL(raw) : new URL(raw, document.baseURI);
    if (absolute.username || absolute.password || absolute.hash) return false;
    if (isAbsolute) return absolute.protocol === "https:";
    return (absolute.protocol === "https:" || absolute.protocol === "http:")
      && absolute.origin === document.location.origin;
  } catch {
    return false;
  }
}

export function ticketAccepted(value: unknown): value is VerificationTicketResult {
  if (typeof value !== "object" || value === null) return false;
  const ticket = value as Partial<VerificationTicketResult>;
  return ticket.protocolVersion === "1"
    && typeof ticket.verificationTicket === "string" && BASE64URL_32.test(ticket.verificationTicket)
    && integerIn(ticket.issuedAt, 0, SAFE_INTEGER)
    && integerIn(ticket.expiresAt, 1, SAFE_INTEGER)
    && ticket.expiresAt > ticket.issuedAt;
}
