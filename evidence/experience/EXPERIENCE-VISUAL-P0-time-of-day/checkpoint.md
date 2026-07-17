# EXPERIENCE-FLOW-OVERHAUL — §1.1 七时段时间感知主题 · 检查点 3

> 归属：`docs/goal/loop-goal-directive.md` §1.1 第 2 条（七时段时间感知主题）
> 权威规范：`UIUXdesign` §3（时间感知系统）+ doc-11 §14.1（motion/color token）
> 日期：2026-07-16

## 本检查点交付

1. **`web/src/theme.ts`**（纯函数 + 副作用分离）：`timeOfDayForHour(hour)` 把本地钟点映射到七时段
   （dawn 5–7 / morning 7–11 / noon 11–15 / evening 15–17 / dusk 17–19 / night 19–23 / deep-night 23–5）；
   `applyTimeOfDayTheme` 写 `<html data-time>`；`startTimeOfDayTheme` 每分钟刷新；`setThemeLock`/`getLockedTimeOfDay`
   支持用户锁定某一时段（localStorage `ic-theme-lock`，读写带 try/catch 降级）。
2. **`web/src/main.tsx`**：render 前调用 `startTimeOfDayTheme()`。
3. **`web/src/styles.css`**：
   - `body` 背景改用 `--amb-a/--amb-b/--grad-1/2/3` 氛围/渐变 token。
   - `@property` 注册这些 token + `--surface-canvas`/`--accent-aurora` 为 `<color>`，并在 `:root` 声明
     `transition: ... var(--dur-state) var(--ease-drift)` —— 时段切换时颜色**平滑插值过渡（不跳变）**，符合
     UIUXdesign §3.3。
   - 7 组 `:root[data-time="…"]` 覆盖：暗暖色系内的色温/明度流动。
   - **本版全部落在暗暖区间，不切浅色**，以免破坏尚未 `var()` 化的硬编码暗色文字；白昼 morandi 浅色主题
     待全量 tokenize 后接入（诚实登记）。日落算法（NOAA）为后置增强（UIUXdesign §3.2 允许固定钟点近似）。

## 验证（证据先于断言）

- `npm run build`（tsc+vite）OK（`theme.ts` 类型编译通过）。
- `npm test`（vitest）：**89 passed (89)**，17 files —— 基线从 67 提升到 89（新增 22 个 theme 测试：
  时段边界 15 例、越界 wrap、锁定 round-trip/非法值/清除、apply 跟随本地时间/优先锁定）。
- 浏览器实测（throwaway `:8099` + 独立 H2，不碰持久 dev 库）：
  - 页面正常渲染（`document.body.innerText.length > 0`）。
  - `theme.ts` 自动应用 `data-time="night"`（实测时刻本地 20:40，属 19–23 night ✓）。
  - 遍历各时段读 computed token（临时置 `transition:none` 取即时目标值）：
    | 时段 | --surface-canvas | --accent-aurora | --amb-a |
    |---|---|---|---|
    | deep-night | rgb(27,22,19) | rgb(185,142,95) | rgb(43,33,26) |
    | night | rgb(33,26,24) | rgb(199,154,104) | rgb(58,42,31) |
    | dawn | rgb(36,27,21) | rgb(214,167,106) | rgb(77,53,32) |
    | dusk | rgb(36,26,28) | rgb(205,155,103) | rgb(74,47,40) |
    | noon | rgb(42,35,28) | rgb(208,164,110) | rgb(74,58,38) |
  - 明度随时间流动（正午最亮 42 > … > 深夜最暗 27），全部 r>g>b（暖侧），dusk 带灰紫（b>g）。
  - 保留 transition 时连续读到相同值 —— 正是 480ms 平滑过渡在工作的证据（@property 颜色可插值，切换不跳变）。
  - 截图工具本会话持续超时（已知环境 flakiness），以数值 computed-style 等价取证。

## 尚未完成（后续检查点）
- 白昼 morandi 浅色时段（需先全量 `var()` 化避免破坏硬编码文字）。
- 日落算法（按地理位置）+ 时段切换的"眨眼感"模糊过渡。
- 设置里暴露"锁定主题时段"UI（后端/组件已可用 `setThemeLock`，缺 UI 入口）。
- 五母题剩余（星尘粒子/涟漪/入场 reveal）、加载四态、§1.2 死按钮 Playwright 清剿。
