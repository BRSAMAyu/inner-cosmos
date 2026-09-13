# CP-29 编译失败保稳定版本负测 + 授权快照覆盖语义与冲突解释 — 增量

- 日期：2026-09-13；实施：主线程（与后台 agent 的 CP-31 并行，文件域不相交）
- 愿景：V02（共鸣体是用户授权的数字回声——授权变更必须可解释、失败不得破坏既有物）

## 交付内容

### 1. 编译失败保稳定版本（负测坐实）

`recompileGenome` 在 `genomeService.compile` 之前就覆写 capsule 行（personaPrompt/style/contextPreview/可见性）。方法本身 `@Transactional`，但此前**没有负测证明**编译失败时回滚真的发生——且本批测试首次尝试即暴露「空过」陷阱：守卫对无 tb_user 行的请求者 fail-closed 抛 UNAUTHORIZED，同样匹配 `assertThrows(RuntimeException)`，断言形同虚设。修正后（真实 ACTIVE 用户 + 断言异常消息含注入的 `"genome store down"`，证明走过的是编译失败路径而非任何前置拒绝）：

- `genomeCompileFailureLeavesTheStableVersionUntouched`：spy 注入 genome.compile 抛错 → capsule 的 personaPrompt/contextPreviewJson/visibilityStatus 全部保持稳定版原值（事务回滚坐实）。

降级人格守卫：复核 `CapsuleAgent.generateUserPersona` 源头已 fail-closed（真实模型不可用/返回空/异常均抛 AI_PROVIDER_ERROR，明确「未创建模板替身」）——**不返回 null/blank，无模板降级路径**，故无需在 recompile 处重复守卫（如实记录，未加死代码）。

### 2. updateContext 事务性（真实隐患修复）

`updateContext` 此前**无 @Transactional**，而 `replaceAuthorizations` 第一步就吊销全部既有授权（grants 撤销 + refs 翻 WITHDRAWN）——若后续步骤（markNeedsReview/updateById）抛异常，授权已被摧毁且不回滚。已加 `@Transactional(rollbackFor = Exception.class)`：授权快照替换成为原子操作，任一步失败整体回滚。

### 3. 授权快照覆盖语义与冲突解释文案

`replaceAuthorizations` 静默丢弃不合格记忆（continue），调用方只有一条笼统文案「所选记忆包含已撤回、非本人或禁止用于共鸣体的内容」——不说哪条、不说为什么。改为显式结果：

- `AuthorizationOutcome(accepted, conflicts)` + `AuthorizationConflict(memoryId, reason)`，拒绝原因分类：不存在 / 非本人记忆 / 已撤回或已失效 / 仅接受 Simulator 测试用途的记忆 / 该记忆的同意范围禁止用于共鸣体（LOCAL_ONLY、NO_EXTERNAL_PROCESSING 等带原值）；
- **recompileGenome**：冲突时抛「以下记忆不能用于此共鸣体——记忆 42：已撤回或已失效；记忆 43：非本人记忆；记忆 44：该记忆的同意范围禁止用于共鸣体（LOCAL_ONLY）」；
- **updateContext**（authorizedMemoryIds）：语义从「静默授权子集」改为同一解释性拒绝——owner 明确列出的清单被静默截断会让人误以为更多记忆已进入共鸣体；事务保证拒绝时既有快照原样保留。

## 测试证据

- 新增 `CapsuleRecompileStabilityTest` 3/3：编译失败稳定版保全（含路径真实性断言）；逐条命名拒绝原因（撤回/非本人/LOCAL_ONLY 三类各有断言）；updateContext 拒绝后授权快照原样（事务性验证）；
- 共鸣体域回归 **161/161**（*Capsule*Test 全集，含 CapsuleMatchingTest 22、ApplicationFlowTest 8、CapsuleRecompileStabilityTest 3）；无既有测试依赖「静默丢弃」语义。

## 诚实边界

- 测试消息断言依赖注入文案 `"genome store down"`（测试自有桩文案，非生产字符串）；
- updateContext 行为变化（静默子集 → 显式拒绝）是语义选型：以「明确清单必须完整成立」为契约，已在 Javadoc 写明理由；若未来需要「尽力授权」语义，应加显式参数而非回退静默；
- createSimulatorCapsule/createFromMemory 路径的授权失败行为未变（创建期失败即整体失败，无稳定版可保）。
