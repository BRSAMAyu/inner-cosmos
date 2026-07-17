# EXPERIENCE-FLOW-OVERHAUL — §1.1 点击涟漪母题 · 检查点 5

> 归属：loop-goal-directive.md §1.1（五母题之"涟漪"+微交互）；直接回应检查点 4 根因（点击反馈缺失）。
> 日期：2026-07-16

## 交付
- `web/src/ripple.ts`：`interactiveAncestor`（纯函数，判定交互元素，排除 disabled）、`prefersReducedMotion`、`spawnRipple(x,y)`（视口定位 + animationend/800ms 兜底移除）、`startRipples`（capture pointerdown，reduced-motion 跳过）。
- `web/src/main.tsx`：`startRipples()`。
- `web/src/styles.css`：`.ripple`（fixed、`var(--accent-aurora)`、`ripple-expand` 620ms `var(--ease-flow)` scale1→9+opacity.5→0）；reduced-motion 压零。

## 验证
- `npm run build` OK；`npm test` **95/95**（+6 ripple 测试，基线 67→95）。
- 浏览器（throwaway :8098 in-mem）：对真实"登录"按钮派发 pointerdown → `.ripple` 出现于精确点击坐标 (500,503)、动画 `ripple-expand 0.62s`、色 `rgb(199,154,104)`(=--accent-aurora)、750ms 自动清理。截图工具超时（已知 flakiness），用 DOM/computed 数值取证。

## 效果
配合检查点 2 的按钮 scale/hover/focus，现在每次点击任意交互元素都有即时视觉反馈，直接缓解"点了没反应"感知（检查点 4 已证按钮均有 handler）。

## 后续
加载四态 + 异步 busy 文案；星尘/入场 reveal/透光；本地字体；白昼 morandi + 全量 var()化。
