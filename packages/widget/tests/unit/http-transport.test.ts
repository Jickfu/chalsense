import { describe, expect, it, vi } from "vitest";
import { createHttpTransport } from "../../src/http-transport.js";

const created = {
  protocolVersion: "1",
  challengeId: "AAAAAAAAAAAAAAAAAAAAAA",
  challengeType: "SLIDER_PUZZLE",
  issuedAt: 1,
  expiresAt: 2,
  geometry: { coordinateScale: 1_000_000, logicalWidth: 320, logicalHeight: 180, pieceStartX: 0, pieceStartY: 1, pieceWidth: 10, pieceHeight: 10 },
  resources: [
    { role: "BACKGROUND", url: "/v1/public/resources/BBBBBBBBBBBBBBBBBBBBBB/background", mediaType: "image/png", pixelWidth: 320, pixelHeight: 180 },
    { role: "PIECE", url: "/v1/public/resources/BBBBBBBBBBBBBBBBBBBBBB/piece", mediaType: "image/png", pixelWidth: 32, pixelHeight: 32 },
  ],
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("HTTP transport", () => {
  it("creates without credentials and resolves resource URLs against the API", async () => {
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(jsonResponse(created));
    const transport = createHttpTransport({ baseUrl: "https://api.example.test", fetch });
    const result = await transport.createChallenge({ protocolVersion: "1", siteKey: "site_key", action: "login", contextDigest: "A".repeat(43) }, new AbortController().signal);

    expect(fetch).toHaveBeenCalledOnce();
    expect(fetch.mock.calls[0]?.[0]).toBe("https://api.example.test/v1/public/sites/site_key/challenges");
    expect(fetch.mock.calls[0]?.[1]).toMatchObject({ method: "POST", credentials: "omit", cache: "no-store", redirect: "error" });
    expect(result.resources[0]?.url).toBe("https://api.example.test/v1/public/resources/BBBBBBBBBBBBBBBBBBBBBB/background");
  });

  it("does not retry an HTTP failure", async () => {
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(jsonResponse({}, 503));
    const transport = createHttpTransport({ baseUrl: "https://api.example.test", fetch });
    await expect(transport.createChallenge({ protocolVersion: "1", siteKey: "site_key", action: "login", contextDigest: "A".repeat(43) }, new AbortController().signal)).rejects.toThrow("HTTP 503");
    expect(fetch).toHaveBeenCalledOnce();
  });

  it("rejects malformed successful responses", async () => {
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(jsonResponse({ ...created, challengeId: "bad" }));
    const transport = createHttpTransport({ baseUrl: "https://api.example.test", fetch });
    await expect(transport.createChallenge({ protocolVersion: "1", siteKey: "site_key", action: "login", contextDigest: "A".repeat(43) }, new AbortController().signal)).rejects.toThrow("invalid create response");
  });
});
