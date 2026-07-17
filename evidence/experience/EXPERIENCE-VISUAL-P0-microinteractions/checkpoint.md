# EXPERIENCE-FLOW-OVERHAUL — §1.1 微交互 + 统一动效曲线 · 检查点 2

> 归属：`docs/goal/loop-goal-directive.md` §1.1（视觉系统重造 P0，第 4/5 条：五母题 + 微交互）
> 权威规范：`UIUXdesign` §1.6（4 条统一 easing，禁 ease/linear）+ §6.4（按钮反馈）+ §10.5（`prefers-reduced-motion`）
> 日期：2026-07-16

## 本检查点交付（`web/src/styles.css`，纯 CSS，叠加式，不改布局/颜色）

1. **全局按钮微交互**（母题：流动/呼吸）：
   - `button` 统一 `transition`，transform 走 `--ease-bloom`（回弹），其余属性走 `--ease-flow`，时长 `--dur-micro`(220ms)。
   - `button:active:not(:disabled) { transform: scale(.97); transition-duration: 90ms }` —— 快按下、带弹性松开。
   - `button:hover:not(:disabled) { filter: brightness(1.06) }` —— hover 反馈。
   - `button:disabled { cursor: default }`；`a` 加平滑颜色过渡。
2. **focus 可见**：`:where(a,button):focus-visible` 统一 `outline: 2px solid var(--accent-aurora)`（暖金焦点环，替代原冷绿焦点色）。
3. **统一动效曲线**：把 `.match-card` 硬编码的 `transition: .2s ease` 换成 token 曲线（transform/border/bg/shadow 各走 `--ease-flow`）。全文件真正的缓动硬编码现仅剩 `.orb` 的 `breathe 6s ease-in-out infinite` —— UIUXdesign §1.6 明确允许无限呼吸循环用 ease-in-out，予以保留。
4. **`prefers-reduced-motion` 降级补全**：原全局降级块只压 `animation-duration`；本次补 `transition-duration: .01ms !important`，让按钮/卡片过渡在 reduced-motion 下也趋零。**刻意不加 `transform: none`**，以免破坏 `.cosmos-star translate(-50%,-50%)` 等布局用 transform。

## 验证（证据先于断言）

- `npm run build`（tsc+vite）：BUILD OK；产物 `static/app/aurora/assets/index.css` 含 `brightness(1.06)`。
- `npm test`（vitest）：**67 passed (67)**，16 files —— 保持基线。
- 浏览器实测（自启 throwaway 实例 :8099 + 独立 H2，不碰持久 dev 库）：
  - 页面正常渲染（非白屏）：`document.body.innerText.length=30`，登录按钮存在（text="登录"）。
  - 登录按钮 `getComputedStyle` 数值证据：
    - `transition-duration = 0.22s`（= `--dur-micro`）。
    - `transition-timing-function` 首项 `cubic-bezier(0.16, 1, 0.3, 1)`（= `--ease-bloom`，作用于 transform），其余项 `cubic-bezier(0.22, 0.61, 0.36, 1)`（= `--ease-flow`）。
    - `cursor: pointer`。
  - 截图工具本会话持续超时（已知环境 flakiness，非 app 问题，prior checkpoint 已记录同类现象）；以数值化 computed-style 作为等价证据，暖主题视觉已在检查点 1 截图确认。

## 尚未完成（诚实登记，后续检查点）

- 涟漪（ripple 从点击坐标扩散）需要 JS/TSX（记录点击位置），本批次未做。
- 入场 reveal（IntersectionObserver）、星尘粒子背景、透光玻璃统一化、加载四态（<1s 不显示 / 1-3s 文案 / >3s 文案+微动画）—— 独立批次。
- 白昼/七时段时间感知主题、本地字体打包 —— 见 UX-VISUAL-SYSTEM remaining。
- §1.2 死按钮清剿（Playwright 交互扫描）—— 独立批次。
