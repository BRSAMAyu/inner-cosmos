# CP-11/23/25 首个工程增量 — 状态矩阵契约、可纠正画像视图、人格激活门锁定

- 日期：2026-09-06；CP-11（全状态、弱网和性能打磨）+ CP-23（可纠正画像与信念变化）+ CP-25（人格连续性与受控演化）首个增量
- 愿景：V02/V03/V14（CP-11）、V05/V06/V11（CP-23）、V04/V16（CP-25）

## CP-11 交付

1. **状态矩阵 v1**（`docs/commercialization/product/cp11-state-matrix.md`）：J01–J12 × loading/empty/partial/offline/timeout/error/retry/expired/permission-denied/long-content/removed/account-frozen 逐格标注实现锚点（既有测试/审计引用）与缺口（3D 退化、键盘焦点 200% 文本、三运营商弱网预算、视觉回归基线——登记 owner 与包归属）
2. **失败保留输入服务端契约测试**（StateMatrixAndPortraitAndPersonaTest.cp11）：
   - 发送被凭据硬拦 → 草稿保持 DRAFT、标题/正文原样、属主仍可编辑重试
   - 跨账户读取保留的草稿 → UNAUTHORIZED（离线缓存的账户隔离由 web Keystore 承担，见矩阵锚点）
   - 仅成功发送后进入寄件视图（CP-33 VO 契约）

## CP-23 交付

- `PortraitClaimViewService` + `GET /api/aurora/corrections/portrait`：
  - **四态视图**：CONFIRMED（用户确认/纠正）/ INFERRED（模型推断）/ CONFLICTING（同键两活跃值冲突，新值仍优先但冲突显性，不被旧多数淹没）/ SUPERSEDED 永不出现
  - **未知不补齐**：五个已知维度（价值/支持/表达/关系节律/变化轨迹）无材料即计 unknownDimensions，绝不用人格模板填空
  - **无分数**：视图只有逐项状态+作用域（PRIVATE/SOCIAL/CAPSULE_RUNTIME），不存在整体人格分
- 测试：空画像 5 未知、推断态、纠正后 CONFIRMED（SUPERSEDED 隐藏）、冲突态显性

## CP-25 交付

- **激活门负测锁定**（既有 `commitToModel` 守卫的合同化）：无用户明确确认 → 拒绝（"激活需要用户明确确认"）；成功激活后重复提交 → 拒绝（候选状态机）；配合既有 proposal→候选→确认→激活链与退役-回退结构（AuroraSelfModel active/retired + rollbackTarget）
- 诚实边界：proposal→评测→授权激活的**评测环节**随 CP-04 注册表执行（人格连续性盲评=注册表 CP-25 行）；显著人格变更说明与 7/30 日轨迹检验属真人研究

## 测试

- StateMatrixAndPortraitAndPersonaTest 3/3；无 schema 变更（基线 42/100/93/23 不变）

## 状态

- CP-11: IN_PROGRESS（矩阵+契约落地；视觉回归/弱网真机为后续窗口）
- CP-23: IN_PROGRESS（四态视图落地；搁置/删除操作与"明日全采用"旅程测试后续）
- CP-25: IN_PROGRESS（激活门锁定；评测环节与轨迹研究后续）
