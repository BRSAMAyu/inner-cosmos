# EXPERIENCE §1.1.1 · var() token 化 第 3 批：近似文字色归一化（checkpoint 12）

cp10/11 处理了与 token 精确等值的字面色。本批处理**与文字 token 相差几个 RGB 点的近似暖色**——它们
本应就是三档文字色之一，只是原 CSS 不够规范散出了近似值。这是**归一化**（暗色渲染偏移 ≤ 几个 RGB 点，
肉眼无感），不是等值替换，因此单独成批并用**真实后端登录做端到端验证**。

先经 grep 语境确认：这 8 个近似色在组件区 **95%+ 用作 `color:`（文字）**，仅个别 border/background
（1 处 #e7dbcc 背景、#a99c92/#efe4d9 各 1 处 border）——映射为文字 token 作边框/背景色也合理。

## 改动（styles.css 组件区，第 92 行起）

按亮度分桶归一化，共 **~57 处**：
- → `var(--text-primary)`：`#e7dbcc`(15)、`#efe4d9`(6)、`#f0e9e3`(3)
- → `var(--text-muted)`：`#a99c92`(9)、`#b3a597`(7)、`#b0a398`(7)
- → `var(--text-faint)`：`#a0948a`(5)、`#9a8f82`(4)

perl 负向前瞻保护 alpha 变体。组件区硬编码 hex 从 ~540 → **401**。

## 验证（真实后端端到端——本批最强证据）

- `npm test`：**108/108**；`npm run build`：OK。
- **启动真实 Spring Boot 后端**（JDK21，dev profile H2+Mock，:8080，~70s 起）→ 通过 `/api/auth/register`
  注册测试账号 cp12probe（id 33）→ 重新加载进入**已认证五空间界面**，getComputedStyle 取证：
  - `authenticated: true`，space=aurora，界面完整渲染（bodyText 392 字符，五空间 shell 就位）。
  - `.app-mark`（原 #e7dbcc）计算 `rgb(240,232,223)` = --text-primary，暖白可见、归一化正确。
  - **`invisibleTextCount: 0`**：遍历 shell 内所有 p/h1/h2/span/small/strong，**无任何透明/不可见文字**——
    归一化没有破坏任何文字渲染（这是近似色归一化最关键的回归检查）。
  - 截图工具在动画页（orb 呼吸 + 星尘）仍超时，故用 computed-style + 结构 + 不可见文字扫描取证。
- 测试后关闭后端；cp12probe 为无害 dev 测试账号，留在本地 dev H2（不入库、不影响产品）。

## token 化累计

cp10 文字精确 41 + cp11 固定 accent 38 + cp12 近似归一化 57 = **~136 处**字面色 → 语义 token。
组件区剩余硬编码 ~401（`#ffffffXX` 叠加、`#211a18XX` 表面 alpha、随时段变的 `#c79a68`/`#211a18`、
阴影 `#000X`、更零散的暖色/暖金 `#c8b697` 等）。

## 下一步

随时段变的 alpha 表面（`#211a18d9/e8/ed`）+ `#ffffffXX` hairline 叠加层归一到 hairline/color-mix；
`#c79a68`→`--accent-aurora`（跨时段验证）；清零后 → 亮色/莫兰迪时段带。
