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
  /** Average seconds between restrained shooting-star events; 0 disables them. */
  meteorInterval: number;
};

type Meteor = { x: number; y: number; vx: number; vy: number; life: number; maxLife: number };

/** Maps the semantic time presentation onto restrained canvas behavior. */
export function stardustProfileForTimeOfDay(tod: TimeOfDay): StardustProfile {
  const presentation = timePresentationFor(tod);
  return {
    time: tod,
    density: presentation.ambientDensity,
    speed: presentation.motionRhythm,
    driftX: presentation.driftX,
    twinkle: tod === "night" ? 1.12 : tod === "deep-night" ? 0.62 : 0.82,
    meteorInterval: tod === "night" ? 18 : tod === "dusk" ? 34 : tod === "deep-night" ? 46 : 0,
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
  let meteors: Meteor[] = [];
  let profile = stardustProfileForTimeOfDay(timeOfDayFromDocument(doc));
  let nextMeteorAt = 0;

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
    meteors = [];
    nextMeteorAt = (view.performance ? view.performance.now() : Date.now())
      + (profile.meteorInterval || 9999) * 1000 * (.72 + Math.random() * .56);
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
    if (profile.meteorInterval > 0 && now >= nextMeteorAt && meteors.length === 0) {
      const fromRight = Math.random() > .5;
      meteors.push({
        x: fromRight ? width * (.65 + Math.random() * .3) : width * (.1 + Math.random() * .25),
        y: height * (.06 + Math.random() * .28),
        vx: (fromRight ? -1 : 1) * (260 + Math.random() * 120),
        vy: 150 + Math.random() * 90,
        life: 0,
        maxLife: .75 + Math.random() * .45,
      });
      nextMeteorAt = now + profile.meteorInterval * 1000 * (.72 + Math.random() * .56);
    }
    meteors = meteors.filter(meteor => {
      meteor.life += dt;
      meteor.x += meteor.vx * dt;
      meteor.y += meteor.vy * dt;
      if (meteor.life >= meteor.maxLife) return false;
      const alpha = Math.sin(Math.PI * meteor.life / meteor.maxLife) * .62;
      const length = 58;
      const magnitude = Math.hypot(meteor.vx, meteor.vy) || 1;
      ctx.globalAlpha = alpha;
      ctx.strokeStyle = tint;
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(meteor.x, meteor.y);
      ctx.lineTo(meteor.x - meteor.vx / magnitude * length, meteor.y - meteor.vy / magnitude * length);
      ctx.stroke();
      return true;
    });
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
