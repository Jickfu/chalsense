import type {
  ChalSenseTransport,
  CreatedChallenge,
  CreateChallengeRequest,
  VerificationTicketResult,
  VerifyChallengeRequest,
} from "./types.js";
import { challengeAccepted, ticketAccepted } from "./validation.js";

export interface HttpTransportConfiguration {
  /** API origin, optionally followed by a path prefix. Defaults to the current origin. */
  readonly baseUrl?: string;
  /** Injectable for tests and non-browser runtimes. */
  readonly fetch?: typeof globalThis.fetch;
}

export function createHttpTransport(configuration: HttpTransportConfiguration = {}): ChalSenseTransport {
  const request = configuration.fetch ?? globalThis.fetch;
  if (typeof request !== "function") throw new TypeError("fetch is unavailable");
  const baseUrl = canonicalBaseUrl(configuration.baseUrl);

  return {
    async createChallenge(input, signal) {
      const response = await postJson(
        request,
        endpoint(baseUrl, `/v1/public/sites/${encodeURIComponent(input.siteKey)}/challenges`),
        {
          protocolVersion: input.protocolVersion,
          action: input.action,
          contextDigest: input.contextDigest,
        },
        signal,
      );
      const resolved = resolveResourceUrls(response, baseUrl);
      if (!challengeAccepted(resolved)) throw new Error("invalid create response");
      return resolved;
    },

    async verifyChallenge(input, signal) {
      const response = await postJson(
        request,
        endpoint(baseUrl, `/v1/public/sites/${encodeURIComponent(input.siteKey)}/challenges/${encodeURIComponent(input.challengeId)}/verify`),
        { protocolVersion: input.protocolVersion, solution: input.solution },
        signal,
      );
      if (!ticketAccepted(response)) throw new Error("invalid verify response");
      return response;
    },
  };
}

async function postJson(
  request: typeof globalThis.fetch,
  url: string,
  body: object,
  signal: AbortSignal,
): Promise<unknown> {
  const response = await request(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    credentials: "omit",
    cache: "no-store",
    redirect: "error",
    referrerPolicy: "no-referrer",
    signal,
  });
  if (!response.ok) throw new Error(`ChalSense request failed with HTTP ${response.status}`);
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!/^application\/json(?:\s*;|$)/iu.test(contentType)) throw new Error("invalid response content type");
  return response.json() as Promise<unknown>;
}

function canonicalBaseUrl(raw: string | undefined): string {
  const fallback = typeof document === "undefined" ? undefined : document.baseURI;
  if (raw === undefined && fallback === undefined) {
    throw new TypeError("baseUrl is required outside a browser");
  }
  const url = new URL(raw ?? "", fallback);
  if (url.username || url.password || url.search || url.hash) throw new TypeError("baseUrl must not contain credentials, query or fragment");
  if (url.protocol !== "https:" && !(url.protocol === "http:" && isLoopback(url.hostname))) {
    throw new TypeError("baseUrl must use HTTPS, except for loopback development");
  }
  return url.href.replace(/\/$/u, "");
}

function endpoint(baseUrl: string, path: string): string {
  return `${baseUrl}${path}`;
}

function resolveResourceUrls(value: unknown, baseUrl: string): unknown {
  if (typeof value !== "object" || value === null || !("resources" in value) || !Array.isArray(value.resources)) return value;
  return {
    ...value,
    resources: value.resources.map((resource: unknown) => {
      if (typeof resource !== "object" || resource === null || !("url" in resource) || typeof resource.url !== "string") return resource;
      return { ...resource, url: new URL(resource.url, `${baseUrl}/`).href };
    }),
  };
}

function isLoopback(hostname: string): boolean {
  return hostname === "localhost" || hostname === "[::1]" || /^127(?:\.\d{1,3}){3}$/u.test(hostname);
}
