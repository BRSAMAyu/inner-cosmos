import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createParticles, particleCount, startStardust } from "./stardust";

function mockReducedMotion(reduce: boolean) {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: reduce && query.includes("reduce"),
    media: query,
    addEventListener: () => {}, removeEventListener: () => {},
    addListener: () => {}, removeListener: () => {}, dispatchEvent: () => false, onchange: null,
  }));
}

describe("星尘母题", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.body.querySelectorAll("canvas.stardust").forEach(c => c.remove());
  });

  describe("particleCount 密度与降级", () => {
    it("移动端(coarse pointer) 明显比桌面稀疏", () => {
      const desktop = particleCount(1440, 900, false);
      const mobile = particleCount(1440, 900, true);
      expect(mobile).toBeLessThan(desktop);
    });
    it("有上限，超大视口不会无限增长", () => {
      expect(particleCount(10000, 10000, false)).toBeLessThanOrEqual(70);
      expect(particleCount(10000, 10000, true)).toBeLessThanOrEqual(28);
    });
    it("零尺寸视口返回 0", () => {
      expect(particleCount(0, 0, false)).toBe(0);
    });
  });

  describe("createParticles", () => {
    it("生成指定数量，坐标落在视口内", () => {
      let seed = 0;
      const rand = () => { seed += 0.1; return seed % 1; }; // 确定化
      const ps = createParticles(20, 800, 600, rand);
      expect(ps).toHaveLength(20);
      for (const p of ps) {
        expect(p.x).toBeGreaterThanOrEqual(0);
        expect(p.x).toBeLessThanOrEqual(800);
        expect(p.y).toBeGreaterThanOrEqual(0);
        expect(p.y).toBeLessThanOrEqual(600);
        expect(p.baseAlpha).toBeLessThan(0.5); // 极淡
      }
    });
  });

  describe("startStardust 纪律", () => {
    beforeEach(() => mockReducedMotion(false));

    it("prefers-reduced-motion 时不创建 canvas，返回 no-op", () => {
      mockReducedMotion(true);
      const stop = startStardust(document);
      expect(document.body.querySelector("canvas.stardust")).toBeNull();
      expect(() => stop()).not.toThrow();
    });

    it("无 2D context(jsdom) 时安全退化，不抛错", () => {
      // jsdom 的 canvas.getContext 默认返回 null -> 应安全退化为 no-op
      const stop = startStardust(document);
      expect(() => stop()).not.toThrow();
    });
  });
});
