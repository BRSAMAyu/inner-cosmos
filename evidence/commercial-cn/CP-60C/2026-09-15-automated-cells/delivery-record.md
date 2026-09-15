# CP-60C web 可自动化单元格推进 — 增量（§2-26）

- 日期：2026-09-15；实施：后台 agent（矩阵 + 契约域）

## 交付

`cp60c-accessibility-matrix.yml` 240 格中**只推进 5 个有真实自动化证据的单元格**至 AUTOMATED_PASS，每格 evidence 引用真实存在的测试文件+用例名：
- J01/web/keyboard ← NonAuthorUsabilityContract（J01 同意对话框焦点/Escape）+ OnboardingGuide（Escape 恢复焦点、重读聚焦）；
- J02/web/keyboard ← HeartDiary.focus-visible（核心输入面真实样式表 :focus-visible 三断言）；
- J05/web/keyboard ← MemoryStarfield（星详情焦点进入+Escape 关闭、列表键盘可达）；
- J02/web/contrast ← ContrastStackAudit（opening-continuity 双主题 4.5:1）+ ContrastTokenAudit；
- J04/web/contrast ← ContrastStackAudit（portrait-claims 回访纠全面）+ token 基线。

诚实排除：MemoryStarfieldTextScalingContract 是 200% 缩放契约，不对应四维度任何列——未用于推进；其余 235 格无既有自动化证据保持 NOT_STARTED（不冒进）。

契约测试 `AccessibilityMatrixContractTest` 改为活体断言：推进数>0；AUTOMATED_PASS 仅允许 web×keyboard/contrast 列；evidence 引用的测试文件必须磁盘存在（重命名/删除即红）。

## 诚实边界

读屏/弱网真机/独立研究是 CP-60C 子门 operator 门禁；axe e2e 重型审计未推断推进。

## 测试

AccessibilityMatrixContractTest 3/3 + LandingCopyDeck 8/8 + 六个被引用 vitest 文件 61 用例全绿。
