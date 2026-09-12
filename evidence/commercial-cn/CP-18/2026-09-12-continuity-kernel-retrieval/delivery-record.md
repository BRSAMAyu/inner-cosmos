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

## 第二增量（同日）：生产接线（检查点 16）

1. **CP-19 生产信号提取与路由接线**：
   - `KernelRoutingPolicy.TurnSignals.from(turnContext)`：五信号生产端提取（长度/明确分析请求/多部分/困境信号/危机词），复用与 `DualKernelBudgetPolicy` 相同的 `CrisisKeywordRule`/`DistressSignalDetector` 分类器——单一调优边界，无平行词典
   - `AuroraDualKernelRuntime.shouldUseDualKernelForTurn` adaptive 模式改为双腿决策：预算评分器（风险/歧义/边界/连续性）OR 内核路由复杂度轴（CP-19）；危机经预算腿保持双核（其 safetyContract 即支持流），绝不退化为单遍分析轮
   - `AuroraAgentServiceImpl` 每轮计算 kernelRoute 并入 turnContext + runtimeMeta（所有运行时模式下可观测）；SUPPORT_FLOW 置 `supportFlowTurn=true` 供规划/表达核以支持优先
   - 说话人/规划核指令新增：carry-forward 轻用与 provenance 约束、首次对话禁引"上次"、supportFlowTurn 支持优先语义
2. **CP-18 开场接线**：
   - `AuroraAgentServiceImpl.continuityGrounding(OpeningContext)`（public static，两处开场共享）：归来者→provenance 标注 carry notes（数据非指令）；全新用户→`crossSessionContinuityFirstConversation` 守卫；有前次但无存留材料→空 map（既不伪造连续也不谎称首次）
   - 主动问候 `generateGreeting` 与首条用户消息轮（`recentMessages.size()<=1`）都注入；问候指令新增规则 7/8（轻引一条带日期 provenance、禁"一直记得"、首次对话禁引旧经历）
   - 隐私：诊断只暴露 carry 条数（`crossSessionContinuityCarry`）与守卫布尔（`firstConversationGuard`），绝不暴露 carry 文本
3. **测试（AuroraContinuityRoutingWiringTest 6/6）**：TurnSignals.from 五信号提取；真实 replyRich 路径 kernelRoute 可见且复杂度挣得 DUAL；开场轮 carry 计数=2、后续轮归零；新用户首轮守卫、后续轮移除；continuityGrounding 三态诚实性；危机语言被同步安全门拦截于内核路由之前（防御纵深断言）
4. 全量回归 **1560/1560** 通过（新增 6 测试），1 个既有 Docker 门控跳过；无 schema 变更

## 第三增量（同日）：CP-22 任务化查询归一（检查点 17）

1. `RetrievalQueryNormalizer`（`com.innercosmos.ai.retrieval`）：确定性剥离对话服务语（"帮我分析/梳理一下/想聊聊/在吗"等紧密清单+语气词与标点折叠）——服务动词不是要找的记忆内容
2. 接线 `MemoryRetrievalServiceImpl.retrieve()`：词法准入、本地/provider 语义相似度都按归一后的内容词计分；**Evidence Pack 仍报告用户原话**（归一是打分关切，不是用户可见改写）
3. 精度语义修正：meta-only 请求（"帮我分析一下"）此前可凭"分析"二字词法命中无关记忆（如"朋友说我什么都反复分析"）进入证据包；现在诚实返回空
4. **测试（MemoryRetrievalQualityTest 4/4）**：归一器边界（null/meta-only/内容保留）；meta-only 诚实空检索+原话透明；内容查询召回正确记忆且动词重叠记忆不入包；ACTION 任务下 PROSPECTIVE/TODO 记忆排序高于同等相关的 EPISODIC
5. 全量回归 **1564/1564** 通过（新增 4 测试），1 个既有 Docker 门控跳过；无 schema 变更
6. 诚实边界：冻结集（cn-commercial-bank-v1）是回复行为场景库，无检索相关性标签，不能直接做 precision@k 对拍；真实向量质量评测需要带标签检索集（后续外部门）与真实 provider 向量

## 第四增量（2026-09-12）：CP-18 前端开屏接线（检查点 18）

1. **API 层**（`web/src/api.ts`）：`DialogContinuity`/`DialogContinuityCarryNote` 类型 + `api.dialogContinuity()` → `GET /api/dialog/continuity`
2. **组件**（`web/src/components/AuroraOpeningContinuity.tsx`）：诚实开屏卡片——归来者看到开场行 + 每条带"来源：上次对话（M月d日）的整理"标注的 carry 清单 + "想继续，也可以从新的开始——由你决定"；全新用户看到"第一次对话"守卫态与"没有历史包袱"提示，绝不出现伪造的"上次"；有前次但无存留材料也按首次形态呈现（不谎称首次也不伪造）；双语（zh-CN/en-SG）；可手动收起
3. **Hook**（`web/src/hooks/useAuroraSession.ts`）：新建会话（bootstrap 无可恢复会话 / 显式"新对话"）时拉取 opening context；打开既有会话、用户发出第一条消息、登出时清除——开场卡只属于开场节拍；拉取失败静默为 null，绝不阻塞对话
4. **接线**（`AuroraApp.tsx`）：卡片渲染于记忆回声卡之后、合成器之前（首次用户无需滚动即可见）；配套 `.opening-continuity` 样式（沿用 continuity-recovery 视觉语言，fresh 态弱化）
5. **测试**：`AuroraOpeningContinuity.test.tsx` 5/5（null 不渲染/归来者 provenance 双标注/新用户守卫态无伪造/可收起/无存留材料按首次形态）；`useAuroraSession.test.ts` 新增 4 测（新建拉取/恢复既有不拉取/失败不阻塞/新对话重取+打开清除+发言清除）；`npm test -- --run` **717/717 绿**，`tsc -b` 零错误

## 状态

- CP-18: IN_PROGRESS（服务+API+问候/首轮+前端开屏完成；连续性对用户可见的撤回开关与移动端联动后续）
- CP-19: IN_PROGRESS（信号提取+adaptive 路由+全轮可观测完成；CP-04 冻结集上的真实增益对拍后续）
- CP-22: IN_PROGRESS（撤回硬边界+查询归一落地；带标签检索评测集与真实向量对拍后续）
