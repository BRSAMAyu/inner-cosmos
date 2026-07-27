import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createParticles,
  particleCount,
  stardustProfileForTimeOfDay,
  startStardust,
} from "./stardust";
import { applyAdaptiveTheme, setPreviewHour } from "./theme";

function mockReducedMotion(reduce: boolean) {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: reduce && query.includes("reduce"),
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
    onchange: null,
  }));
}

describe("stardust ambient layer", () => {
  afterEach(() => {
    setPreviewHour(null);
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    document.body.querySelectorAll("canvas.stardust").forEach(canvas => canvas.remove());
  });

  describe("particle density and degradation", () => {
    it("uses fewer particles for coarse pointers", () => {
      expect(particleCount(1440, 900, true)).toBeLessThan(
        particleCount(1440, 900, false),
      );
    });

    it("retains hard caps for very large viewports", () => {
      expect(particleCount(10_000, 10_000, false, 1.2)).toBeLessThanOrEqual(70);
      expect(particleCount(10_000, 10_000, true, 1.2)).toBeLessThanOrEqual(28);
    });

    it("returns zero for a zero-area viewport", () => {
      expect(particleCount(0, 0, false)).toBe(0);
    });

    it("responds to semantic time density within the caps", () => {
      const sparse = particleCount(1440, 900, false, .58);
      const rich = particleCount(1440, 900, false, 1.2);
      expect(rich).toBeGreaterThan(sparse);
    });
  });

  describe("createParticles", () => {
    it("creates the requested particles inside the viewport", () => {
      let seed = 0;
      const rand = () => {
        seed += .1;
        return seed % 1;
      };
      const particles = createParticles(20, 800, 600, rand);
      expect(particles).toHaveLength(20);
      for (const particle of particles) {
        expect(particle.x).toBeGreaterThanOrEqual(0);
        expect(particle.x).toBeLessThanOrEqual(800);
        expect(particle.y).toBeGreaterThanOrEqual(0);
        expect(particle.y).toBeLessThanOrEqual(600);
        expect(particle.baseAlpha).toBeLessThan(.5);
      }
    });

    it("scales upward velocity with the selected rhythm", () => {
      const rand = () => .5;
      const slow = createParticles(1, 800, 600, rand, .5)[0];
      const awake = createParticles(1, 800, 600, rand, 1.1)[0];
      expect(awake.vy).toBeGreaterThan(slow.vy);
    });
  });

  describe("startStardust lifecycle", () => {
    beforeEach(() => mockReducedMotion(false));

    it("does not create a canvas when reduced motion is requested", () => {
      mockReducedMotion(true);
      const stop = startStardust(document);
      expect(document.body.querySelector("canvas.stardust")).toBeNull();
      expect(() => stop()).not.toThrow();
    });

    it("degrades to a no-op when a 2D context is unavailable", () => {
      vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);
      const stop = startStardust(document);
      expect(() => stop()).not.toThrow();
    });

    it("refreshes against the selected period when the theme changes", () => {
      const context = {
        setTransform: vi.fn(),
        clearRect: vi.fn(),
        beginPath: vi.fn(),
        arc: vi.fn(),
        fill: vi.fn(),
        globalAlpha: 1,
        fillStyle: "",
      };
      vi.spyOn(HTMLCanvasElement.prototype, "getContext")
        .mockReturnValue(context as unknown as CanvasRenderingContext2D);
      vi.stubGlobal("requestAnimationFrame", vi.fn(() => 1));
      vi.stubGlobal("cancelAnimationFrame", vi.fn());

      setPreviewHour(12);
      applyAdaptiveTheme();
      const stop = startStardust(document);
      const canvas = document.body.querySelector<HTMLCanvasElement>("canvas.stardust");
      expect(canvas?.dataset.time).toBe("noon");

      setPreviewHour(21);
      applyAdaptiveTheme();
      expect(canvas?.dataset.time).toBe("night");
      stop();
    });
  });
});

describe("time-responsive stardust profile", () => {
  it("is richer at night and calmer in deep night", () => {
    const night = stardustProfileForTimeOfDay("night");
    const deepNight = stardustProfileForTimeOfDay("deep-night");
    expect(night.density).toBeGreaterThan(deepNight.density);
    expect(night.speed).toBeGreaterThan(deepNight.speed);
    expect(night.driftX).not.toBe(deepNight.driftX);
    expect(night.meteorInterval).toBeGreaterThan(0);
    expect(stardustProfileForTimeOfDay("noon").meteorInterval).toBe(0);
  });
});
