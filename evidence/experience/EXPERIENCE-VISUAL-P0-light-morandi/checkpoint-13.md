# EXPERIENCE §1.1.1/§1.1.6 · 白昼莫兰迪浅色主题（opt-in）（checkpoint 13）

cp10-12 把文字/accent 全部 token 化后，表面色 token 化的尾巴（~400 处、多为脚本生成的近似深褐+alpha）
需要"消费判断 + 可靠视觉验证"，而截图工具不可靠——形成验证死结。本 checkpoint 用**先上亮色主题**解开它：
上一个 opt-in 的白昼莫兰迪主题后，已 token 化的部分自动翻转、**未 token 化的硬编码深色表面在浅底上"现形"**，
把不可见的 token 化苦工变成可见、可枚举、可验证的清单。

## 交付

- `theme.ts`：新增与七时段正交的**明暗轴** `ColorScheme = "day"|"night"`；`getColorScheme/setColorScheme/
  applyColorScheme`；`startTimeOfDayTheme` 启动时一并 `applyColorScheme`。仅 "day" 写 `<html data-theme="day">`，
  默认(null=跟随/night)不写属性 → **暗色生产默认 100% 不变**。
- `styles.css`：`:root[data-theme="day"]` 覆盖核心 token 为莫兰迪浅色——米白基底 `#F7F2EC`、深暖褐文字
  `#2E2620`、hairline 改深色 alpha、amb/grad 浅色渐变、accent 加深以保证对比。走既有 @property 过渡 → 明暗切换平滑。
- `components/AppearanceToggle.tsx` + 接入 MeSpace：三选一「跟随时间/白昼/夜色」，直改 `data-theme`，CSS 即时响应。
  样式全走 token（两主题下都协调）。

## 验证

- `npm test`：**115/115**（原 108 + 4 color-scheme + 3 AppearanceToggle）。`npm run build`：OK。
- **真实后端端到端**（JDK21 dev H2+Mock，注册 cp13probe，localStorage 锁 day 后重载已认证界面，getComputedStyle）：
  - `data-theme="day"` 生效、authenticated。token 正确翻转：`--surface-canvas` = rgb(247,242,236)=#F7F2EC（米白）、
    `--text-primary` = rgb(46,38,32)=#2E2620（深褐）、muted rgb(107,95,84)、faint rgb(114,101,86)。
  - **WCAG 2.2 AA 对比度全部达标**（正文需 ≥4.5）：primary/canvas **13.34**、muted/canvas **5.57**、
    faint/canvas **5.08** —— 均通过。
  - 截图工具在动画页仍超时；用 computed-style + 对比度计算取证。

## 残留清单（本主题"照出"的未 token 化深色表面，共 10 类——下一步工作）

`app-shell-nav`、`input`、`button`、`textarea`、`.active`(选中态)、`skill-catalog`、`skill-runner`、
`plaza-search`、`composer`、及个别内联元素。这些用的是硬编码深褐/半透明表面（`#251d18dc`/`#ffffffXX`/
`#241bXX` 等），在浅底上仍显深色。后续按此清单逐个 token 化（用浅色主题实时验证），即可让白昼主题完全干净。

## 意义

- §1.1 首次拥有**可切换的白昼浅色主题**，AA 达标——directive 退出标准"两时段截图证据"的浅色半区就位。
- 表面 token 化从"不可见苦工"变成"对着浅色主题逐个消灭 10 类深色残留"的可验证任务。
- 默认暗色零改变，opt-in 无生产风险。
