# EXPERIENCE 体验层重造 · §1.1.5 加载四态 / 三档延迟加载（checkpoint 6）

指令来源：`docs/goal/loop-goal-directive.md` §1 Phase 0 (0-A 视觉系统重造 §1.1.5)：
"加载态永不用 spinner（<1s 无、1-3s 文案、>3s 文案+微动画）；每个界面空/错/等待/恢复四态齐全"。
直接闭合 checkpoint 4 死按钮扫描诊断出的体验根因：**点击到异步结果之间缺少可见的"等待"反馈**
（checkpoint 5 的涟漪已覆盖"点击瞬时"反馈，本 checkpoint 覆盖"异步等待"反馈）。

## 交付

- `web/src/loading.tsx` 新增加载原语：
  - `useDelayedBusy(busy)` 三档状态机：`idle`(<1s 不显示，避免快响应闪烁) → `text`(1s，文案+静态点)
    → `anim`(3s，文案+微动画三点)。`busy` 变假立即回 `idle`。
  - `prefers-reduced-motion` 时封顶在 `text` 档，永不进入 `anim`（复用 `ripple.ts` 的 `prefersReducedMotion`）。
  - `AsyncButton`：busy 时禁用 + `aria-busy`，按三档把标签换成忙碌文案；默认 `type="button"`。
  - `LoadingText` / `LoadingDots`：区块级"等待"文案；三点渐隐指示器，**绝不是旋转 spinner**。
- `web/src/styles.css`：`.loading-dots`（三点渐隐 `@keyframes loading-dot`，走 `--ease-flow` token）、
  `.loading-text`（`--text-muted` 暖色）、`.async-busy`。reduced-motion 由既有全局块（styles.css:245）降级为静态。
- 接入：
  - `AccountSettings.tsx`（我的空间）3 个异步动作按钮（导出/修改密码/删除账户）→ `AsyncButton` + 忙碌文案。
  - `AuroraApp.tsx` 首屏连接页 → `LoadingText`（`正在连接你的内宇宙`），符合"<1s 无"规范。

## 验证

- `npm test`：**101/101 通过**（原 95 + 6 个新 `loading.test.tsx` 用例，fake timers 断言三档时序 + reduced-motion 封顶 + type=button）。
- `npm run build`：tsc + vite OK；`assets/index.css` 49.42kB，加载态 CSS 规则已进入产物（`.loading-dots{...}`、
  `.loading-dots:not(.static) i{animation:loading-dot 1.2s var(--ease-flow) infinite}`、`@keyframes loading-dot`、
  `.async-busy`、`.loading-text{color:var(--text-muted)...}` 均在构建产物中确认）。
- 浏览器可视化验证（`loading-preview-harness.html`，内联了**生产构建的 index.css**，`<html data-time="night">`）：
  用 `javascript_tool` getComputedStyle 取证（截图工具在无限 CSS 动画页面上必然超时——前序 checkpoint 已多次记录
  的环境 flakiness，本次同样，故用 computed-style/DOM 文本证据，与前序 checkpoint 一致的替代取证方式）：
  - 1-3s 档按钮：`text="正在导出"`、`disabled=true`、`aria-busy=true`，静态点 `animationName: none`（不动）。
  - >3s 档按钮：`text="正在删除"`，`.loading-dots:not(.static) i` 正在跑 `animationName: loading-dot`、
    `animationDuration: 1.2s`，点色 `rgb(231,219,204)`（currentColor，暖色，非冷蓝霓虹）。
  - <1s 档按钮：仍显示原标签 `导出数据`、`disabled=true`（不闪）。
  - 首屏连接页 `LoadingText`：`正在连接你的内宇宙`，色 `rgb(183,170,160)` = `--text-muted`（r>g>b 暖侧）。
  - `data-time=night` 生效。

## 未做（后续 checkpoint）

- 把 `AsyncButton` 铺开到其余异步动作按钮（UnderstandingCorrection 确认/退休、CapsuleWorkbench 保存边界/编译/发布、
  PortraitView 校准、PeopleDiscovery 发起认识、LettersInbox 处理信件、MemoryStarfield 保存重要度/归档等）。
- 每个界面"空/错/恢复"三态的系统化补齐（本 checkpoint 聚焦"等待"态；空态部分组件已有，如 AuroraConversation `.empty`）。
- 后续仍按 next_machine_actions：渐进 var() token 化 + 亮色/莫兰迪 + 本地字体打包 + 五母题补齐。
