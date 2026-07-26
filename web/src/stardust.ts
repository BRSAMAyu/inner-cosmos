import { prefersReducedMotion } from "./ripple";
import {
  ADAPTIVE_THEME_EVENT,
  isTimeOfDay,
  timePresentationFor,
  type TimeOfDay,
} from "./theme";

export type Particle = {
  x: number;
  y: number;
  r: number;
  baseAlpha: number;
  vy: number;
  sway: number;
  phase: number;
};

export type StardustProfile = {
  time: TimeOfDay;
  density: number;
  speed: number;
  driftX: number;
  twinkle: number;
};

/** Maps the semantic time presentation onto restrained canvas behavior. */
export function stardustProfileForTimeOfDay(tod: TimeOfDay): StardustProfile {
  const presentation = timePresentationFor(tod);
  return {
    time: tod,
    density: presentation.ambientDensity,
    speed: presentation.motionRhythm,
    driftX: presentation.driftX,
    twinkle: tod === "night" ? 1.12 : tod === "deep-night" ? 0.62 : 0.82,
  };
}

function timeOfDayFromDocument(doc: Document): TimeOfDay {
  const value = doc.documentElement.dataset.time;
  return isTimeOfDay(value) ? value : "night";
}

/**
 * Viewport area determines the base particle count. Coarse pointers keep the
 * existing mobile cap; time density can only scale within those hard limits.
 */
export function particleCount(
  width: number,
  height: number,
  coarsePointer: boolean,
  densityMultiplier = 1,
): number {
  const area = Math.max(0, width) * Math.max(0, height);
  const areaPerParticle = coarsePointer ? 42_000 : 22_000;
  const scaled = Math.max(0, Math.min(1.25, densityMultiplier));
  const raw = Math.round((area / areaPerParticle) * scaled);
  const cap = coarsePointer ? 28 : 70;
  return Math.max(0, Math.min(cap, raw));
}

export function createParticles(
  count: number,
  width: number,
  height: number,
  rand: () => number = Math.random,
  speedMultiplier = 1,
): Particle[] {
  const particles: Particle[] = [];
  for (let i = 0; i < count; i += 1) {
    particles.push({
      x: rand() * width,
      y: rand() * height,
      r: 0.6 + rand() * 1.4,
      baseAlpha: 0.12 + rand() * 0.28,
      vy: (4 + rand() * 10) * speedMultiplier,
      sway: 6 + rand() * 14,
      phase: rand() * Math.PI * 2,
    });
  }
  return particles;
}

export function isCoarsePointer(view: Window = window): boolean {
  try {
    return view.matchMedia("(pointer: coarse)").matches;
  } catch {
    return false;
  }
}

/**
 * Starts the ambient canvas. Reduced-motion remains a complete opt-out and
 * coarse-pointer devices retain their lower density/cap. Theme updates rebuild
 * the small particle set so period changes are visible without a reload.
 */
export function startStardust(doc: Document = document): () => void {
  if (prefersReducedMotion()) return () => undefined;

  const canvas = doc.createElement("canvas");
  canvas.className = "stardust";
  canvas.setAttribute("aria-hidden", "true");
  const ctx = canvas.getContext ? canvas.getContext("2d") : null;
  if (!ctx) return () => undefined;

  doc.body.appendChild(canvas);
  const view = doc.defaultView ?? window;
  const root = doc.documentElement;

  let width = 0;
  let height = 0;
  let particles: Particle[] = [];
  let profile = stardustProfileForTimeOfDay(timeOfDayFromDocument(doc));

  const warm = () => {
    try {
      return view.getComputedStyle(root).getPropertyValue("--accent-aurora").trim() || "#c79a68";
    } catch {
      return "#c79a68";
    }
  };
  let tint = warm();

  const rebuild = () => {
    profile = stardustProfileForTimeOfDay(timeOfDayFromDocument(doc));
    particles = createParticles(
      particleCount(width, height, isCoarsePointer(view), profile.density),
      width,
      height,
      Math.random,
      profile.speed,
    );
    canvas.dataset.time = profile.time;
    tint = warm();
  };

  const resize = () => {
    width = view.innerWidth;
    height = view.innerHeight;
    const dpr = Math.min(2, view.devicePixelRatio || 1);
    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    rebuild();
  };
  resize();

  let raf = 0;
  let last = view.performance ? view.performance.now() : 0;
  const frame = (now: number) => {
    const dt = Math.min(0.05, (now - last) / 1000 || 0);
    last = now;
    ctx.clearRect(0, 0, width, height);
    for (const particle of particles) {
      particle.y -= particle.vy * dt;
      particle.x += profile.driftX * 5 * dt;
      particle.phase += dt * (0.55 + profile.speed * 0.25);
      if (particle.y < -4) {
        particle.y = height + 4;
        particle.x = Math.random() * width;
      }
      if (particle.x < -4) particle.x = width + 4;
      if (particle.x > width + 4) particle.x = -4;
      const twinkle = 0.6 + 0.4 * Math.sin(particle.phase);
      const x = particle.x + Math.sin(particle.phase * 0.5) * particle.sway;
      ctx.globalAlpha = particle.baseAlpha * twinkle * profile.twinkle;
      ctx.fillStyle = tint;
      ctx.beginPath();
      ctx.arc(x, particle.y, particle.r, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
    raf = view.requestAnimationFrame(frame);
  };
  raf = view.requestAnimationFrame(frame);

  const onResize = () => resize();
  const onThemeChange = () => rebuild();
  view.addEventListener("resize", onResize);
  root.addEventListener(ADAPTIVE_THEME_EVENT, onThemeChange);

  return () => {
    view.cancelAnimationFrame(raf);
    view.removeEventListener("resize", onResize);
    root.removeEventListener(ADAPTIVE_THEME_EVENT, onThemeChange);
    canvas.remove();
  };
}
