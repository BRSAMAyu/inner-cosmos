# EXPERIENCE §1.1.1 · var() token 化 第 2 批：固定值 accent/danger（checkpoint 11）

承接 cp10（文字色）。本批处理组件区里与**不随七时段变化的**语义 token 值完全相等的 accent/danger 字面色，
仍是**等值替换、暗色像素恒等、零风险**。（会随时段变的 `--accent-aurora`/`--surface-canvas` 留 cp12
单独跨时段验证，因为把静态 hex 映射到它们虽符合"时段流动"意图但非像素恒等。）

## 改动

`web/src/styles.css` 组件区（第 92 行起，不动定义/`@property`/七时段块）等值替换，共 **38 处**：
- `#a9bcae` → `var(--accent-sage-soft)`（19）
- `#9fb0c2` → `var(--accent-sky-soft)`（7）
- `#ddb17f` → `var(--accent-aurora-soft)`（4）
- `#7e9281` → `var(--accent-sage)`（1）
- `#74869b` → `var(--accent-sky)`（3）
- `#8d7482` → `var(--accent-plum)`（1）
- `#c7a9c0` → `var(--accent-plum-soft)`（1）
- `#e9b4ae` → `var(--danger-soft)`（2）

perl 负向前瞻保护 alpha 变体：`#a9bcae4a`(4)、`#74869b35`(5)、`#9bb0a24a`(4) 均**正确保留未动**
（它们是半透明叠加，后续需单独的 alpha-token 或 color-mix 处理）。

## 验证

- `npm test`：**108/108**（纯 CSS 重构）。
- `npm run build`：OK；构建产物 `index.css` 含 `var(--accent-sage-soft)` 等引用。
- **浏览器等值确认**（生产静态 app @127.0.0.1:8127，getComputedStyle 探针）：
  - `var(--accent-sage-soft)` → `rgb(169,188,174)` = #a9bcae ✓
  - `var(--accent-sky-soft)` → `rgb(159,176,194)` = #9fb0c2 ✓
  - `var(--danger-soft)` → `rgb(233,180,174)` = #e9b4ae ✓
  - 全部原字面值，暗色渲染像素恒等，token 引用解析无误。

## token 化累计进度

- cp10：文字色 41 处；cp11：固定 accent/danger 38 处。**合计 79 处**字面色 → 语义 token。
- 剩余（后续批次）：随时段变的 `#c79a68`→`--accent-aurora`、`#211a18`→`--surface-canvas`（cp12，跨时段验证）；
  近似色归一化（`#e7dbcc`/`#a99c92` 等，需语境判断）；`#ffffffXX` 叠加层；其余零散暖色。清零后 → 亮色时段带。
