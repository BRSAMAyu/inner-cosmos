import { afterEach, describe, expect, it, vi } from "vitest";

// CP-21 backend half, web side: the three portrait-claim owner actions now accept an
// expectedVersion pin (the claim row's `version` the caller last rendered). These cases pin
// the wire contract the CP-21 panel banner depends on:
//   - a pinned call reaches the server as ?expectedVersion=N, and a 409/code CONFLICT reply
//     surfaces through the exact channel isVersionConflictError recognizes;
//   - a legacy call (AuroraApp's current call sites, unchanged in this batch) sends NO
//     expectedVersion parameter — backwards compatible, not a silent overwrite switch.

function envelope(status: number, data: unknown, code = "OK"): Response {
  return new Response(JSON.stringify({
    success: status >= 200 && status < 300,
    code,
    message: status >= 200 && status < 300 ? "success" : "这条理解在你操作前已被他人更新，请查看最新后再试",
    data
  }), { status, headers: { "Content-Type": "application/json" } });
}

describe("portrait claim actions carry expectedVersion (CP-21)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("a pinned suppress sends ?expectedVersion and a 409 CONFLICT reply stays a version conflict", async () => {
    vi.resetModules();
    const urls: string[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL): Promise<Response> => {
      const url = String(input);
      if (url.endsWith("/api/v1/auth/csrf")) {
        return envelope(200, { token: "csrf-ok", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" });
      }
      urls.push(url);
      // The server's stale-pin reply: HTTP 409, code CONFLICT (never a message string).
      return envelope(409, null, "CONFLICT");
    });
    vi.stubGlobal("fetch", fetchMock);

    const { api, isVersionConflictError } = await import("../api");
    const caught = await api.suppressPortraitClaim(12, "这不太是我", 3).catch(error => error);
    expect(urls).toEqual(["/api/portrait/claims/12/suppress?expectedVersion=3"]);
    expect(isVersionConflictError(caught)).toBe(true);
  });

  it("legacy calls without a pin send no expectedVersion (delete and restore unchanged)", async () => {
    vi.resetModules();
    const urls: Array<{ url: string; method?: string }> = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const url = String(input);
      if (url.endsWith("/api/v1/auth/csrf")) {
        return envelope(200, { token: "csrf-ok", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" });
      }
      urls.push({ url, method: init?.method });
      return envelope(200, { id: 12, status: "DELETED", version: 4 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const { api } = await import("../api");
    await api.restorePortraitClaim(12);
    await api.deletePortraitClaim(12, "不想保留");
    expect(urls).toEqual([
      { url: "/api/portrait/claims/12/restore", method: "POST" },
      { url: "/api/portrait/claims/12", method: "DELETE" }
    ]);
  });
});
