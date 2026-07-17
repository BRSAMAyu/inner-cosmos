// 点击涟漪（§1.1 五母题之"涟漪" / UIUXdesign §2 §6.4）：每次在交互元素上按下，都从
// 指针位置发射一圈扩散淡出的涟漪，给出即时视觉反馈——直接缓解"点了没反应"的体验根因。
// 纯 CSS 动画 + 一个全局 pointerdown 监听；prefers-reduced-motion 时完全跳过。

const INTERACTIVE_SELECTOR =
  'button, a[href], [role="button"], [role="tab"], [role="switch"], summary, label, ' +
  'input[type="button"], input[type="submit"], input[type="checkbox"], input[type="radio"]';

/** 该指针目标是否落在一个交互元素（或其内部）上——决定是否发射涟漪。 */
export function interactiveAncestor(target: EventTarget | null): HTMLElement | null {
  if (!(target instanceof Element)) return null;
  const el = target.closest(INTERACTIVE_SELECTOR);
  if (!el) return null;
  if (el instanceof HTMLButtonElement && el.disabled) return null;
  return el as HTMLElement;
}

/** 是否应降级（用户偏好减少动效）。 */
export function prefersReducedMotion(): boolean {
  try {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  } catch {
    return false;
  }
}

/** 在视口坐标 (x, y) 处发射一圈涟漪，动画结束后自动移除。 */
export function spawnRipple(x: number, y: number, doc: Document = document): void {
  const r = doc.createElement("span");
  r.className = "ripple";
  r.setAttribute("aria-hidden", "true");
  r.style.left = `${x}px`;
  r.style.top = `${y}px`;
  doc.body.appendChild(r);
  const remove = () => r.remove();
  r.addEventListener("animationend", remove, { once: true });
  window.setTimeout(remove, 800); // 兜底：动画未触发也清理
}

/** 启动全局涟漪；返回停止函数。 */
export function startRipples(root: Document = document): () => void {
  const onPointerDown = (e: PointerEvent) => {
    if (prefersReducedMotion()) return;
    if (!interactiveAncestor(e.target)) return;
    spawnRipple(e.clientX, e.clientY, root);
  };
  root.addEventListener("pointerdown", onPointerDown, { capture: true, passive: true });
  return () => root.removeEventListener("pointerdown", onPointerDown, { capture: true } as EventListenerOptions);
}
