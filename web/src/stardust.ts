// 星尘母题（§1.1 五母题之"星尘" / UIUXdesign §2）：在所有内容之后铺一层极淡的暖色粒子，
// 缓慢上浮 + 轻微横向摇曳 + 呼吸式明灭，营造"安静流动的内在宇宙"的纵深，而不是一块死背景。
// 纪律：prefers-reduced-motion 完全跳过；移动端/coarse pointer 降低粒子数（directive §1.1.4）；
// canvas 绝不拦截指针（pointer-events:none），jsdom 下 getContext 返回 null 时安全退化为 no-op。

import { prefersReducedMotion } from "./ripple";

export type Particle = { x: number; y: number; r: number; baseAlpha: number; vy: number; sway: number; phase: number };

/** 视口面积决定粒子数；coarse pointer（触屏/移动端）显著降级；带上下限防极端视口。 */
export function particleCount(width: number, height: number, coarsePointer: boolean): number {
  const area = Math.max(0, width) * Math.max(0, height);
  const density = coarsePointer ? 42000 : 22000; // 每颗粒子覆盖的像素面积；移动端更稀疏
  const raw = Math.round(area / density);
  const cap = coarsePointer ? 28 : 70;
  return Math.max(0, Math.min(cap, raw));
}

/** 生成一批粒子。rand 可注入以便测试确定化（默认 Math.random）。 */
export function createParticles(count: number, width: number, height: number, rand: () => number = Math.random): Particle[] {
  const particles: Particle[] = [];
  for (let i = 0; i < count; i++) {
    particles.push({
      x: rand() * width,
      y: rand() * height,
      r: 0.6 + rand() * 1.4,
      baseAlpha: 0.12 + rand() * 0.28,
      vy: 4 + rand() * 10,            // 每秒上浮的像素数（很慢）
      sway: 6 + rand() * 14,          // 横向摇曳幅度
      phase: rand() * Math.PI * 2     // 明灭相位
    });
  }
  return particles;
}

/** 是否走 coarse pointer 降级（触屏）。matchMedia 不可用时保守返回 false。 */
export function isCoarsePointer(): boolean {
  try {
    return window.matchMedia("(pointer: coarse)").matches;
  } catch {
    return false;
  }
}

/**
 * 启动星尘背景，返回停止函数。prefers-reduced-motion 或无 2D context 时不创建任何东西、返回 no-op。
 */
export function startStardust(doc: Document = document): () => void {
  if (prefersReducedMotion()) return () => undefined;

  const canvas = doc.createElement("canvas");
  canvas.className = "stardust";
  canvas.setAttribute("aria-hidden", "true");
  const ctx = canvas.getContext ? canvas.getContext("2d") : null;
  if (!ctx) return () => undefined; // jsdom / 不支持 canvas：安全退化

  doc.body.appendChild(canvas);
  const view = doc.defaultView ?? window;

  let width = 0, height = 0, particles: Particle[] = [];
  const resize = () => {
    width = view.innerWidth;
    height = view.innerHeight;
    const dpr = Math.min(2, view.devicePixelRatio || 1);
    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    particles = createParticles(particleCount(width, height, isCoarsePointer()), width, height);
  };
  resize();

  // 暖色调（读取当前时段 accent，回退到暖金）。星尘只用暖色，绝不冷蓝。
  const warm = () => {
    try {
      const c = getComputedStyle(doc.documentElement).getPropertyValue("--accent-aurora").trim();
      return c || "#c79a68";
    } catch {
      return "#c79a68";
    }
  };
  let tint = warm();

  let raf = 0;
  let last = view.performance ? view.performance.now() : 0;
  const frame = (now: number) => {
    const dt = Math.min(0.05, (now - last) / 1000 || 0); // 秒；封顶防后台标签页跳变
    last = now;
    ctx.clearRect(0, 0, width, height);
    for (const p of particles) {
      p.y -= p.vy * dt;
      p.phase += dt * 0.8;
      if (p.y < -4) { p.y = height + 4; p.x = Math.random() * width; }
      const twinkle = 0.6 + 0.4 * Math.sin(p.phase);
      const x = p.x + Math.sin(p.phase * 0.5) * p.sway;
      ctx.globalAlpha = p.baseAlpha * twinkle;
      ctx.fillStyle = tint;
      ctx.beginPath();
      ctx.arc(x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
    raf = view.requestAnimationFrame(frame);
  };
  raf = view.requestAnimationFrame(frame);

  const onResize = () => { resize(); tint = warm(); };
  view.addEventListener("resize", onResize);

  return () => {
    view.cancelAnimationFrame(raf);
    view.removeEventListener("resize", onResize);
    canvas.remove();
  };
}
