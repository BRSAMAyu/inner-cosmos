# CP-18/19/22 首个工程增量 — 跨会话上下文连续性、确定性双核路由、检索撤回硬边界

- 日期：2026-09-12；CP-18（对话编排与真实连续性）+ CP-19（双核、上下文与中文理解增益）+ CP-22（任务化检索与真实向量质量）首个增量
- 愿景：V03/V14/V15（CP-18）、V03/V04/V16（CP-19）、V03/V05/V16（CP-22）

## CP-18 交付：诚实的跨会话连续性

1. `SessionContinuityService.openingContext(userId)` → `OpeningContext(hasPrior, priorSessionId, priorActiveAt, carryForward, openingLine)`，`CarryNote(kind, text, provenance)`
2. 诚实性规则（与蓝图 §CP-18"禁止伪造连续性"一致）：
   - **全新用户**：hasPrior=false、carryForward 为空、开场"我们从头开始。你想说的那件事，慢慢来。"——绝不引用不存在的"上次"
   - **归来的用户**：只从真实 FINISHED 会话的 `tb_dialog_summary` 取材料——PRIOR_SUMMARY（120 字截断）+ PRIOR_TOPICS，每条都带 provenance"上次对话（M月d日）的整理"，开场"你M月d日聊过一次，我带着那次留下的整理在这里。想继续，也可以从新的开始。"（继续与否由用户决定，不是系统强加）
   - **30 天静默窗**：超过窗口视为诚实的新开始（hasPrior 归 false），沉默被尊重而不是被翻旧账
3. 接线：`GET /api/dialog/continuity`（登录态）

## CP-19 交付：确定性内核路由（首块）

- `KernelRoutingPolicy.route(TurnSignals)` 纯函数：SINGLE / DUAL / SUPPORT_FLOW
  - crisisHit → **SUPPORT_FLOW**（危机永远不进深度分析路径）
  - riskContext → DUAL（谨慎双核带批判）
  - 复杂度三信号（输入长度 ≥160 / 用户明确要分析 / 多部分请求）任一 → DUAL
  - 简单短轮次默认 SINGLE（额外流水线永远不是默认值——成本与延迟纪律）
  - null 信号兜底 SINGLE
- 可测试、无副作用：作为后续把路由接入 Aurora 回复编排的确定性地基

## CP-22 交付：检索的撤回硬边界

- `MemoryRetrievalServiceImpl.retrieve()` 在状态过滤后追加 tombstone 检查：`RetractionTombstoneService.blockedIds("MEMORY", userId)` 命中即 `notIn("id", …)`
- **抗备份复活**：即使备份恢复把已撤回行复活为 ACTIVE，读取时 tombstone 仍然拦截（use-time enforcement，不是 write-time 一次性状态）

## 测试（ContinuityKernelRetrievalTest 4/4）

1. 新用户零伪造连续性（无 prior、无 carry、开场含"从头开始"）
2. 归来用户带 provenance 的 carry-forward（PRIOR_SUMMARY/PRIOR_TOPICS 断言）+ 45 天静默后诚实归零
3. 撤回记忆在备份复活（行改回 ACTIVE）后仍绝不进入检索证据
4. 内核路由确定性矩阵：简单→SINGLE；长度/分析请求/多部分/风险→DUAL；危机（即便同时全信号）→SUPPORT_FLOW；null 兜底

全量回归：**1554/1554 通过，1 个既有 Docker 门控跳过，BUILD SUCCESS**（本轮无 schema 变更，PG 基线计数不变，仍为 V42/42 迁移/100 表/93 身份列）。

## 诚实边界

- CP-18：开场上下文尚未接入 Aurora 首条回复的 prompt 组装（下一增量：PromptBuilder 消费 OpeningContext + 前端开屏消费）
- CP-19：路由策略已冻结为纯函数，但回复编排尚未按 kernel 分流（单核直答/双核批判重写/支持流旁路）；TurnSignals 的生产端信号提取（长度/多部分解析/风险上下文）随接线补
- CP-22：向量检索质量基线（重排、任务化查询改写、中文评测集对拍）依赖 CP-04 冻结集 + 真实向量库；本轮先钉死撤回硬边界这一不可妥协项

## 状态

- CP-18: IN_PROGRESS（连续性服务+API 落地；prompt/前端接线后续）
- CP-19: IN_PROGRESS（路由纯函数+测试落地；编排接线与信号提取后续）
- CP-22: IN_PROGRESS（撤回硬边界落地；任务化检索与质量评测后续）
