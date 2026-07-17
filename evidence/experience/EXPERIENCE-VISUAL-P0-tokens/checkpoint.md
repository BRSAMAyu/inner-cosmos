# EXPERIENCE-FLOW-OVERHAUL — §1.1 视觉系统重造 · 检查点 1（token 层 + 暖色夜间基底）

> 归属：`docs/goal/loop-goal-directive.md` §1.1（视觉系统重造 P0）
> 权威规范：`对齐文档/11-完全体UIUX与交互设计规格.md` §14.1（design tokens，冲突时 token 语义优先）+ 仓库根 `UIUXdesign` v1.0
> 日期：2026-07-16

## 问题（已核实的规范违反）

改造前 `web/src/styles.css` 是**冷色深绿/蓝黑**主题：
- `:root` `background: #0d1412`（rgb 13,20,18 —— 绿通道主导 = 冷）、`color: #eaf1ec`（冷绿白）、`font-family: Inter`。
- `body` 冷绿黑径向/线性渐变（`#263d35`/`#0b1110`/`#121c19`/`#0a0e0d`）。
- 全文件 365 个唯一色值，大量冷绿/冷蓝霓虹强调（`#8ed1b8`/`#b8d6c5`/`#86a7bc`…）。

直接违反 doc-11 §2（明令禁止"冷蓝霓虹、纯黑高对比 AI 控制台"）与 UIUXdesign §1.1（应为温暖流动色系，夜间主背景暖褐 `#211A18`/`#2A1F18`）。

## 本检查点交付

1. **Design token 层**（`web/src/styles.css` 顶部，`@layer tokens`）：按 doc-11 §14.1 落地 surface / accent(aurora/sage/sky/plum) / text / danger / hairline / 4 条统一 easing 曲线 / 时长 / 圆角 / 4 倍间距 / 字体族。夜色暖褐系为默认值。
2. **基底走 token 并暖化**：`:root` 改为 `background: var(--surface-canvas)`(#211A18) + `color: var(--text-primary)`(#F0E8DF) + `font-family: var(--font-sans)`；`body` 改为暖褐三层渐变（褐 `#3a2a1f` + 暖夜紫 `#2c2030` + 褐基底 `#241b17→#211a18→#1c1512`）。
3. **全文件冷→暖重映射**（`scratchpad/recolor.pl` + `recolor2.pl`，保留 alpha 后缀）：冷绿白正文→暖白，冷绿/冷蓝次要文字→暖灰，霓虹薄荷→鼠尾草 sage，冷蓝→暖雾蓝 sky-soft，冷绿/冷蓝深色面板底→暖褐。94 处 `#ffffffXX` 中性叠加保留（叠在暖底上自动变暖）。
   - 说明：sage(`#7E9281`) 与 sky(`#74869B`) 属 doc-11 认可的暖调绿/暖调蓝，非"冷科技"，予以保留（映射到该家族）。
   - 说明：本批次多数值改为字面暖色 hex；将各组件 `var(--token)` 化的内部整改在后续检查点逐空间进行（§4：用户可感知优先于内部洁癖）。

## 验证（证据先于断言）

- 冷色残留扫描（排除 sanctioned 暖调 palette 后）：仅剩 `#aa8bd3`（plum 家族紫，属认可色）。命令：
  `grep -oE "#[0-9a-f]{6}" src/styles.css | perl -ne '...(g或b通道明显主导)...'`
- `npm run build`（tsc + vite）：BUILD OK，产物 `src/main/resources/static/app/aurora/assets/index.css` 45.50 kB。
- `npm test`（vitest）：**67 passed (67)**，16 files —— 与基线一致。
- 浏览器实测（读态，未写库）：`http://localhost:8080/app/aurora/` 登录页渲染为暖褐主题；`INNER COSMOS` eyebrow 暖金、标题暖白衬线、卡片/输入框暖褐。
- **数值化暖度证明**（浏览器 `getComputedStyle`）：
  - `--surface-canvas` = `rgb(33,26,24)` → r>g>b，色相在暖侧（旧 `#0d1412` 为绿主导冷侧）。**满足 UIUXdesign §12「暗色主题不冷（h 值应在暖色侧）」验收项。**
  - 根背景实测 `rgb(33,26,24)`；`--text-primary` `#f0e8df`；`--accent-aurora` `#c79a68`；`.eyebrow` 实测 `rgb(200,182,151)`（暖褐）。

## 尚未完成（诚实登记，后续检查点）

- 白昼莫兰迪 / 七时段时间感知主题 + 平滑 lerp 过渡（需先把硬编码色值全部 `var()` 化，避免 light 模式下 300+ 硬编码深色破图）。本检查点仅夜间暖色，故 day/night 双时段截图中 day(light) 待光照主题接入后补。
- 字体文件本地打包（LXGW WenKai / Noto Serif SC / Cormorant / JetBrains Mono）—— 现为系统 fallback。
- 五母题（呼吸/星尘/涟漪/透光/流动）动效落地、微交互（scale 反弹+涟漪）、加载四态 —— 独立批次。
- 死按钮清剿（§1.2，Playwright 交互扫描）—— 独立批次。
