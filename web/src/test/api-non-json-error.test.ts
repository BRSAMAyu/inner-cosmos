import { afterEach, describe, expect, it, vi } from "vitest";

describe("API non-JSON error presentation", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("turns an expired Cloudflare quick tunnel response into an actionable Chinese message", async () => {
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
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      "<html>upstream unavailable</html>",
      { status: 502, headers: { "Content-Type": "text/html" } }
    )));

    const { api } = await import("../api");
    await expect(api.getProfile()).rejects.toThrow("服务暂时没有返回可用内容（HTTP 502）。请稍后重试。");
  });
});
