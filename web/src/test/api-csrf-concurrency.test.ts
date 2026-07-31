import { afterEach, describe, expect, it, vi } from "vitest";

function envelope(status: number, data: unknown, code = "OK"): Response {
  return new Response(JSON.stringify({
    success: status >= 200 && status < 300,
    code,
    message: status >= 200 && status < 300 ? "success" : "A valid CSRF token is required.",
    data
  }), { status, headers: { "Content-Type": "application/json" } });
}

describe("CSRF recovery under concurrent Aurora sends", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("deduplicates token refresh and lets both staged streams finish", async () => {
    vi.resetModules();
    let csrfLoads = 0;
    let rejectedStages = 0;
    let acceptedStages = 0;
    let releaseRejectedStages!: () => void;
    const bothRejectedStagesArrived = new Promise<void>(resolve => { releaseRejectedStages = resolve; });

    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const url = String(input);
      if (url.endsWith("/api/v1/auth/csrf")) {
        csrfLoads += 1;
        return envelope(200, {
          token: csrfLoads === 1 ? "csrf-old" : "csrf-fresh",
          headerName: "X-CSRF-TOKEN",
          parameterName: "_csrf"
        });
      }

      if (url.endsWith("/api/v1/aurora/stream-stage")) {
        const token = new Headers(init?.headers).get("X-CSRF-TOKEN");
        if (token === "csrf-old") {
          rejectedStages += 1;
          if (rejectedStages === 2) releaseRejectedStages();
          await bothRejectedStagesArrived;
          return envelope(403, null, "CSRF_INVALID");
        }
        expect(token).toBe("csrf-fresh");
        acceptedStages += 1;
        return envelope(200, { token: `stage-${acceptedStages}`, expiresInSeconds: 60 });
      }

      if (url.includes("/api/v1/aurora/stream?token=stage-")) {
        return new Response("", { status: 200, headers: { "Content-Type": "text/event-stream" } });
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { streamAurora } = await import("../api");
    const first = streamAurora({ sessionId: 1, message: "第一条", mode: "COMPANION" },
      new AbortController().signal, () => undefined);
    const second = streamAurora({ sessionId: 1, message: "第二条", mode: "COMPANION" },
      new AbortController().signal, () => undefined);

    // Gemini audit 4.2: the mocked stream response here has an empty body (closes immediately,
    // zero SSE frames), so it genuinely IS "connection closed with no terminal event ever seen" --
    // streamAurora's terminal-reason contract correctly classifies that as EOF_WITHOUT_TERMINAL,
    // not a silent success. This test's own purpose (CSRF-retry dedup) is unaffected either way.
    await expect(Promise.all([first, second])).resolves.toEqual(["EOF_WITHOUT_TERMINAL", "EOF_WITHOUT_TERMINAL"]);
    expect(csrfLoads).toBe(2);
    expect(rejectedStages).toBe(2);
    expect(acceptedStages).toBe(2);
  });

  it("materializes a post-switch token before a Demo persona reload begins", async () => {
    vi.resetModules();
    const calls: string[] = [];
    let csrfLoads = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const url = String(input);
      if (url.endsWith("/api/v1/auth/csrf")) {
        csrfLoads += 1;
        calls.push(`csrf-${csrfLoads}`);
        return envelope(200, {
          token: `csrf-${csrfLoads}`,
          headerName: "X-CSRF-TOKEN",
          parameterName: "_csrf"
        });
      }
      if (url.endsWith("/api/public/demo/enter/xia-yu")) {
        calls.push(`enter:${new Headers(init?.headers).get("X-CSRF-TOKEN")}`);
        return envelope(200, { id: 3, username: "cloud", nickname: "夏榆", role: "USER" });
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { api } = await import("../api");
    await expect(api.enterDemoPersona("xia-yu")).resolves.toMatchObject({ username: "cloud" });

    expect(calls).toEqual(["csrf-1", "enter:csrf-1", "csrf-2"]);
    expect(csrfLoads).toBe(2);
  });

  it("fails closed when the server does not issue a CSRF token", async () => {
    vi.resetModules();
    const calls: string[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL): Promise<Response> => {
      const url = String(input);
      if (url.endsWith("/api/v1/auth/csrf")) {
        calls.push("csrf-missing");
        return envelope(200, {});
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { api } = await import("../api");
    await expect(api.enterDemoPersona("lin-chuan")).rejects.toThrow(/secure session|安全会话/);
    expect(calls).toEqual(["csrf-missing"]);
  });
});
