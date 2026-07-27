import { afterEach, describe, expect, it, vi } from "vitest";

describe("API non-JSON error presentation", () => {
  afterEach(() => {
    document.documentElement.lang = "zh-CN";
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("turns an expired Cloudflare quick tunnel response into an actionable Chinese message", async () => {
    document.documentElement.lang = "zh-CN";
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      "<html><title>Cloudflare Tunnel error</title><p>Error 1033</p></html>",
      { status: 530, headers: { "Content-Type": "text/html" } }
    )));

    const { api } = await import("../api");
    await expect(api.getProfile()).rejects.toThrow(
      "这次临时演示连接已经失效。请打开最新的演示链接；你的操作没有被提交。"
    );
  });

  it("does not expose parser jargon for other upstream HTML failures", async () => {
    document.documentElement.lang = "zh-CN";
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      "<html>upstream unavailable</html>",
      { status: 502, headers: { "Content-Type": "text/html" } }
    )));

    const { api } = await import("../api");
    await expect(api.getProfile()).rejects.toThrow("服务暂时没有返回可用内容（HTTP 502）。请稍后重试。");
  });

  it("uses actionable English copy when the interface language is English", async () => {
    document.documentElement.lang = "en-SG";
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      "<html><title>Cloudflare Tunnel error</title><p>Error 1033</p></html>",
      { status: 530, headers: { "Content-Type": "text/html" } }
    )));

    const { api } = await import("../api");
    await expect(api.getProfile()).rejects.toThrow(
      "This temporary demo connection has expired. Open the latest demo link; your action was not submitted."
    );
  });

  it("turns HTTP 429 into a retry countdown instead of a connection failure", async () => {
    document.documentElement.lang = "zh-CN";
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({
      success: false, code: "RATE_LIMIT_EXCEEDED", message: "too fast",
      data: { retryAfter: 17 }
    }), {
      status: 429,
      headers: { "Content-Type": "application/json", "Retry-After": "17" }
    })));

    const { api, ApiRateLimitError } = await import("../api");
    const pending = api.getProfile();
    await expect(pending).rejects.toBeInstanceOf(ApiRateLimitError);
    await expect(pending).rejects.toThrow("约 17 秒后重试");
  });
});
