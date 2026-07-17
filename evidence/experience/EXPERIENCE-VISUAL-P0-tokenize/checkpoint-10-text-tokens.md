# EXPERIENCE §1.1.1 · var() token 化 第 1 批：文字色（checkpoint 10）

亮色/莫兰迪时段带的前置是"全库清零硬编码色值、所有组件走 token"。styles.css 组件区仍有 ~619 处硬编码
暖色 hex。本批（多批中的第一批）先处理**最阻塞亮色模式的文字色**，且只做**等值替换**（hex 与 token 当前值
完全相等），保证暗色渲染像素不变、零回归。

## 改动

`web/src/styles.css` 组件规则区（第 92 行起，**不动** token 定义/`@property`/七时段覆盖块）等值替换：
- `#f0e8df` → `var(--text-primary)`（13 处）
- `#b7aaa0` → `var(--text-muted)`（11 处）
- `#8c8177` → `var(--text-faint)`（17 处）

共 **41 处**字面色 → 语义 token。用 perl 负向前瞻 `(?![0-9a-fA-F])` 避免误伤 alpha 变体（`#f0e8df14` 等
hairline 变体经确认在组件区未使用）。替换后组件区这三色残留为 0。

## 验证

- `npm test`：**108/108**（无新增测试；纯 CSS 结构重构）。
- `npm run build`：tsc + vite OK；构建产物 `index.css` 含 `var(--text-primary/muted/faint)` 引用。
- **浏览器等值确认**（生产静态 app @127.0.0.1:8126，getComputedStyle）：
  - token 值完好：`--text-primary #f0e8df` / `--text-muted #b7aaa0` / `--text-faint #8c8177`。
  - `var(--text-faint)` 解析为 `rgb(140,129,119)` = 原字面值 #8c8177（**像素一致**）。
  - `.connect-error-title`（用 `var(--text-primary)`）计算 `rgb(240,232,223)` = #f0e8df。
  - 证明只是"字面色→同值 token 引用"，暗色渲染零改变，且 token 引用无拼写错误、能正确解析。

## 未做（后续 token 化批次）

- **近似色归一化**（下一批，需视觉比对）：组件区还有一批与文字 token 相差几个 RGB 点的近似暖色——
  亮色系 `#e7dbcc`(15)/`#efe4d9`(6)/`#f0e9e3`(3) → 归 `--text-primary`；
  中性 `#a99c92`(9)/`#b3a597`(7)/`#b0a398`(7) → 归 `--text-muted`；
  更暗 `#a0948a`(5)/`#9a8f82`(4) → 归 `--text-faint`。这类是**归一化**（会让暗色偏移几个 RGB 点，肉眼
  无感但非零），必须逐语境判断并做前后 computed-style 比对，不能盲替，故单独成批。
- 表面色（`#211a18`/`#2a2220`/`#2a1f18`）、accent（`#a9bcae`/`#9fb0c2`/`#ddb17f` 等）、`#ffffffXX` 叠加
  层的 token 化，各自成批。
- 全部清零后 → 亮色/莫兰迪时段带 + 七时段浅色半区。
