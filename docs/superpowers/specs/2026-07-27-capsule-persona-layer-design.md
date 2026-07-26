# 共鸣体人格层与快速拟真链路设计

日期：2026-07-27
状态：待实施（设计已评审通过，含评审方修订）

## 1. 问题陈述

当前共鸣体在一次短对话（目标 5 分钟）后几乎无法产出可用的拟真侧影。访客提问时它以拒答为主，
因此"先和侧影聊几句再决定要不要写信"这条产品主线走不通。

初始假设是"脱敏清洗过严"。经源码核验，该假设**不成立**。真实瓶颈是三道结构性闸门。

### 1.1 已核验的现状（每条均对应源码）

**证据产量闸门**

- `POST /api/aurora/settle?sessionId=` → `MemorySettlementServiceImpl.settleSession` 对一个会话
  只产出**一张** MemoryCard；`MemoryServiceImpl.java:123-129` 命中已存在的
  `source_session_id` 时执行 update 而非再插一条。一场对话 = 一条记忆。
- 前端默认预览路径 `AuroraApp.tsx:1083-1084` 只取 `.slice(0, 3)`。
- `CapsuleWorkbench.tsx:243, 284` 记忆勾选列表 `.slice(0, 10)`，第 11 条起无法授权或取消授权。

**IR 抽取闸门**

- `CapsuleServiceImpl.buildGenomeIr`（`:1305-1345`）：claims 每卡一条、截断 220 字；
  values / habits / temporalState 仅当被脱敏后的文本**字面包含**硬编码线索词
  （`VALUE_CUES` / `HABIT_CUES` / `TEMPORAL_CUES`，`:1101-1106`）时才生成。1 条卡 → 1 条 claim，
  其余三类几乎必然进入 `unknowns`。
- `inferStyleProfile`（`:1130-1189`）：`confidence = min(1.0, cards.size() / 5.0)` → 1 条卡时为 **0.2**；
  voice 取自 6 条主题词表 `VOICE_BY_THEME`（`:1079-1086`）最多 2 条，全部未命中则退为通用默认
  「温和、诚实、慢热」。

**运行时检索闸门（致命项）**

- `CapsuleRuntimeContextComposer.compose`（`:40-88`）：每一轮访客提问先 `classify()`，必须命中
  `INTENT_CUES`（`:26-30`）的字面词；否则退到 CLAIM，且必须与某条 claim 存在
  **2-gram 字面重叠**（`hasMeaningfulOverlap` / `tokens`，`:103-116`）；命中后再过一遍同样的
  overlap 才选中 feature，且 `:61` 截断到 3 条。
- `:64` `unsupported = selected.isEmpty()` —— 二元判定，无中间档。
- `unsupported=true` → `PersonaChatServiceImpl.PERSONA_CHAT_INSTRUCTION`（`:219-230`）明令
  "不能编造原主人的事实、经历**或偏好**"。**偏好被一并禁止**，而偏好恰是主人可自述的内容。

**脱敏并非瓶颈（原始假设的纠正）**

- 前端在 `api.ts:944`（preview）与 `api.ts:949`（create）两处**硬编码 `privacyLevel: "STRICT"`**，
  因此真实产品路径是 STRICT，而非本设计初稿所述的 BALANCED。
- `DataMaskingServiceImpl.maskText`（`:124-147`）两档是**交叉而非递进**的：
  - STRICT → `maskPatterns`（手机、邮箱、`.{2,6}(大学|学院|中学|小学|学校)`、QQ、微信）
    **且不调用 `maskNames`**；
  - BALANCED → `maskContactInfo` + `maskNames`（仅 `叫XX`/`我是XX` 窄模式，`:169-174`），
    **不挡学校名**。
- `CapsuleServiceImpl.previewUserMirror`（`:811`）使用 `safePrivacy(null)` = BALANCED，
  与其余全链路的 STRICT 遮蔽**种类不同**，所见即所得从根本上不成立。
- 结论：两档都只触及联系方式/学校/微信，不触及实质内容，因此脱敏不是拟真瓶颈；
  但档位必须收敛为单一显式值（见 §6.4）。

### 1.2 关键发现：人格层资产已存在，但共鸣体编译器不消费它

仓库中已有一套完整的、带逐条消息级来源、可确认可否决的 11 维用户模型：

- `ClaimTypes.java:11-24`：FACT / PREFERENCE / VALUE / RELATION / EMOTION_PATTERN / HABIT /
  EXPRESSION_STYLE / NEED / BOUNDARY / TREND / UNCERTAINTY。
- `tb_understanding_claim`（`UnderstandingClaim.java`）：`claimKey` / `claimType` / `valueJson` /
  `authorityLevel` / `confidence` / `status` / `sourceType` / `evidenceRefs`（消息级来源） /
  `version` / `supersedesClaimId`。
- `ClaimCandidateController`：list / confirm / dismiss 三个接口齐备。
- `web/src/components/ClaimCandidateReview.tsx`：落卡与否决的 UI 组件已存在。
- `ClaimCandidateExtractListener.java:28-29`：已是 `@Async` + `AFTER_COMMIT`，不阻塞对话。

`UnderstandingClaim` 的消费方仅 `PromptBuilder` 与 `AuroraAgentServiceImpl`。
`CapsuleServiceImpl` / `CapsuleGenomeServiceImpl` / `CapsuleRuntimeContextComposer` **完全不读取它**。

因此本设计的实质是**接线 + 闸门分级**，而非新建人格层。

## 2. 目标与非目标

### 目标

1. 一次约 5 分钟的 Aurora 引导对话后，共鸣体能以主人的语气与立场持续回应，而不是持续拒答。
2. 主人在对话过程中**逐条**看见并可否决每一条将进入共鸣体的断言（知情前置，不是事后告知）。
3. "不编造事实"的承诺不被削弱：具体经历/第三人事实仍需授权记忆证据。
4. 第三人身份不因整体放宽而泄漏。

### 非目标

1. 不放宽脱敏档位强度（已核验其非瓶颈）。
2. 不让原始对话原文进入 Genome 或运行时提示词。
3. 不改动慢信、匹配、连接等下游域。
4. 不替代前次审查中 P0 的 #1（广场自身共鸣体）/ #2（屏蔽失效）/ #3（dashboard 泄漏）——
   三者独立存在，仍需单独修复。

## 3. Grounding 四级模型（评审方修订，替代初稿的三级）

`CapsuleRuntimeContextComposer` 输出新增 `groundingLevel` 字段，取代 `unsupported` 布尔量作为
提示词分支依据（`unsupported` 保留以兼容既有断言，但不再单独决定行为）：

| 级别 | 触发条件 | 共鸣体被允许做什么 |
|---|---|---|
| `EPISODIC_MEMORY` | 命中授权 MemoryCard 派生的事实证据 | 陈述具体经历、时间地点、第三人事实（经匿名化） |
| `PERSONA_CLAIM` | 未命中事实证据，但命中快照内已确认的 VALUE / PREFERENCE / NEED | 回答对应的自我描述 |
| `STYLE_ONLY` | 以上均未命中，但快照含 EXPRESSION_STYLE / BOUNDARY | **仅**约束语气与行为边界，回应当下这句话；不得作出任何自我描述性断言或事实陈述 |
| `UNSUPPORTED` | 快照为空（种子侧影、无记忆通用侧面），或相关证据缺失 | 坦白该部分未被授权（现状行为） |

**关键约束（评审方要求，写入实现）**

- `groundingLevel` **不得**仅因"存在人格数据"就跳出 `UNSUPPORTED`。它必须由**本轮检索结果**
  与**当前 ACTIVE Genome 快照内容**共同决定。
- `STYLE_ONLY` 与 `PERSONA_CLAIM` 是两个不同档：前者只影响"怎么说"，后者才允许"说我是谁"。
  初稿把二者合并为单一 PERSONA 档，会让语气档误授自我描述权限。

### 3.1 类型分区

| 分区 | claimType | 运行时归属 |
|---|---|---|
| Persona | `EXPRESSION_STYLE` / `BOUNDARY` | `STYLE_ONLY` 及以上常驻 |
| Persona | `VALUE` / `NEED` / `PREFERENCE` | `PERSONA_CLAIM` 命中时可用 |
| 条件性 Persona | `EMOTION_PATTERN` | 可用，但**必须保留时态限定与非诊断措辞**（见 §3.2） |
| 事实闸门 | `FACT` / `RELATION` / `HABIT` / `TREND` | 仅 `EPISODIC_MEMORY` |
| 默认不公开 | `UNCERTAINTY` | 除主人单独授权外，不进入快照 |

### 3.2 EMOTION_PATTERN 的附加约束

情绪模式若以无时态的稳定特质形式进入提示词，等同于对主人下心理判断。实现要求：

- 快照中该类条目必须携带 `temporalQualifier`（如"最近这段时间"）与
  `scope = "NOT_A_DIAGNOSIS"`。
- 提示词须明确：可以说"我最近容易这样"，不得说"我是一个……的人"，不得使用任何诊断类词汇。

## 4. Persona 快照：编译期，而非运行期

**评审方修订的核心一条。** 初稿隐含运行时读取最新 claim，这会绕过 Genome 的版本、撤回、
重编译与下架机制。

正确语义："人格层常驻"= **当前 ACTIVE Genome 内的已授权 persona 快照常驻**。

- 快照在 `genomeService.compile` 时从 CONFIRMED claims **物化**进 `contextPreviewJson.personaLayer`，
  与 `genomeIr` 同级、同一版本号、同一 `compilerVersion`。
- 运行时 `CapsuleRuntimeContextComposer` **只读快照**，不查 `tb_understanding_claim`。
- 因此以下既有机制自动继续生效，无需新增逻辑：
  - `requireRunnableCapsule`（`PersonaChatServiceImpl.java:640-664`）的撤回 / 需复核 / 编译器版本漂移检查；
  - `archiveCapsule`（`CapsuleServiceImpl.java:888-911`）的下架与 grant 撤销；
  - `recompileGenome` 的新版本生成与历史可追溯。
- 主人在对话后新增或修改 claim，**不会**静默改变已发布侧影的行为——必须显式重编译。
  这与"公开人格不得暗中漂移"的既有承诺一致。

## 5. 落卡链路契约（评审方修订）

初稿的"前端每轮拉一次并按 messageId 对齐"方向被接受，但初稿遗漏了必需契约。核验结论：

- `protocol.ts:31-40` 的 `AuroraStreamEvent` 中，`bubble.completed { order }` 与
  `turn.completed { message }` **均不携带持久化 DialogMessage id**。
- `AuroraConversation.tsx:10` 实时气泡仅有随机 `key`，无法与 `provenanceMessageIds` 对齐。
- `ClaimCandidateServiceImpl.listCandidates(Long userId)`（`:128-130`）只按
  `user_id` + `status=CANDIDATE` 过滤，**无 sessionId**；`ClaimCandidateController:33-35` 亦无参数。
  直接用于每轮轮询会把历史会话的候选挂到当前对话上。
- 抽取仅监听 `DialogFinishedEvent`（`ClaimCandidateExtractListener.java:29-32`），
  **仅靠前端轮询不会产生任何新候选**。

### 5.1 必需契约

1. **新增每轮抽取触发**：新增"本轮消息已持久化完成"事件，`AFTER_COMMIT` + `@Async` 触发
   `stageForSession`。沿用现有监听器形态，不阻塞对话；失败仅记录日志。
   `stageForSession`（`:70-81`）本身按 `claimKey` upsert，天生幂等增量，无需改动其内部逻辑。
2. **候选接口增加 `sessionId` 过滤**：`listCandidates(userId, sessionId)` 与
   `GET /api/aurora/claims/candidates?sessionId=`。缺省不传时保持现有全量行为，供既有复核入口使用。
3. **前端对齐流程**：`turn.completed` 后
   → 重新读取该 session 的持久化消息（取得权威 `DialogMessage.id`）
   → 调用 `claimCandidates(sessionId)`
   → 按 `evidenceRefs` 中的 `DialogMessage.id` 将候选卡挂到对应气泡。
4. **有限轮询**：立即、+500ms、+1500ms 三次后停止。不做无限轮询，不做指数退避。
   三次后仍无新候选即视为本轮无可落卡内容。
5. **并发控制**：per-session 抽取租约或串行队列；并携带 revision，
   **旧 revision 的抽取结果不得覆盖更新 revision 的结果**。
6. **GET 副作用必须显式化**：`listCandidates` 当前在读取路径内将过期候选直接改写为
   DISMISSED（`:137-140`）。每轮轮询会把该副作用由低频变为高频。要求：
   将自动 dismiss 从读取路径移出（交由既有 `sweepStaleCandidates` 批处理承担），
   使候选查询成为纯读操作。

## 6. 必须先修正的既有缺陷（阻塞项）

以下四项均已核验，是本设计的前置条件。不修则设计无法成立。

### 6.1 确认候选会丢失 11 维类型

`ClaimCandidateServiceImpl.confirmCandidate`（`:174` 附近）构造
`CorrectionCommand("AURORA_UNDERSTANDING", ...)`；`UserCorrectionServiceImpl`（`:142` 附近）
执行 `claim.claimType = command.targetType()`，即新建的 ACTIVE 行 `claimType` 被写成
**`"AURORA_UNDERSTANDING"`**。原始 11 维类型仅残留在人类可读的 reason 字符串
（`"确认 Aurora 自动理解：" + candidate.claimType`）中，结构化列已丢失。

由于 §3.1 的整个分区依赖 `claimType`，**必须保留原始类型**（在 ACTIVE 行上保留 11 维类型，
或增列携带原始类型）。

### 6.2 "已确认候选进入 Aurora"这条消费链实际匹配不到任何记录

`AuroraAgentServiceImpl.safeConfirmedClaims`（`:2096` 附近）查询条件为
`status=ACTIVE AND source_type=AUTO_EXTRACTION`。
而确认路径产生的是 `status=ACTIVE AND source_type=USER_CORRECTION`
（`UserCorrectionServiceImpl` 中 `claim.sourceType = "USER_CORRECTION"`）。
`stageForSession` 的 upsert 产生的是 `CANDIDATE + AUTO_EXTRACTION`。

即：**没有任何代码路径产生 `ACTIVE + AUTO_EXTRACTION`，该查询恒返回空**。
这条链路当前是死的。本设计不能建立在"确认后 claim 已在被消费"的假设上；
需修正查询条件或写入端的 `sourceType`，并补回归测试钉住。

### 6.3 Mock / 确定性抽取器不产出 RELATION 与 EXPRESSION_STYLE

`ClaimCandidateExtractor`（`:72` 起）实际仅产出 UNCERTAINTY / BOUNDARY / TREND /
EMOTION_PATTERN / PREFERENCE / HABIT / VALUE / NEED / FACT ——
**无 RELATION，无 EXPRESSION_STYLE**。
`ClaimExtractionServiceImpl.java:67` 将其作为结构化 AI 调用的 fallback。

后果：无 Key 的课堂 Demo 走 Mock 路径时，`STYLE_ONLY` 档所依赖的 EXPRESSION_STYLE
**永远为空**，人格层退化为仅 BOUNDARY。真实 Provider 可产出，但不能假设 Demo 环境具备。

要求：为确定性抽取器补 EXPRESSION_STYLE 的确定性规则（如句长、标点密度、重复回避等可从
消息文本直接测量的特征），使无 Key 环境也能达到 `STYLE_ONLY`。
RELATION 在 Mock 下缺失可接受（它属事实闸门，缺失只导致更保守）。

### 6.4 时长文案与隐私档位

- `CapsuleShapingStrategy.java:5` 的类注释写的是 **"in about ten minutes"**，
  与 5 分钟目标不一致。策略提示词与产品文案需统一到同一时长承诺。
- 隐私档位必须由知情面板**显式确定**，并由**预览、编译、沙盒共用同一个值**。
  当前前端两处硬编码 STRICT（`api.ts:944, 949`）、`previewUserMirror` 用 BALANCED（`:811`）、
  `CapsuleWorkbench.tsx:139` 展示默认 STRICT，三者不一致且两档遮蔽种类交叉（见 §1.1）。

## 7. 第三人匿名化（评审方修订）

同意为必须项，但**不改写底层权威 claim**。

- `UnderstandingClaim` 保留主人私有的原始表述与来源，不被破坏。
- 编译期派生 `capsuleSafeValue`，只有该派生值进入 Genome 快照与运行时提示词。
- 知情面板**默认展示即将进入 Genome 的脱敏版本**，同时提供"查看来源原话"的展开入口。
- 第三人使用稳定关系词或会话内稳定别名："家人" / "一位朋友" / "同事 A"。
  同一实体在同一快照内必须映射到同一别名（保持可指代性，避免叙述断裂）。
- 扫描范围**不限于 RELATION**：其他类型的 `valueJson` 中夹带的第三人姓名同样需要处理。
- **不得**以 `DataMaskingServiceImpl.maskNames`（`:169-174`）作为本层实现：
  它只覆盖 `叫XX` / `我是XX` 窄模式，无法处理"小林今天……"这类第三人主语姓名。
  需要独立的第三人实体识别与别名映射组件。

## 8. 五分钟时间线

| 时间 | 内容 |
|---|---|
| 0:00–3:30 | Aurora `CAPSULE_SHAPING` 引导，约 6–8 轮；每轮后异步抽取，落卡出现在对应气泡旁，可否决 |
| 3:30–4:00 | Aurora 主动邀请生成草稿（`CapsuleShapingStrategy:29-30` 已有"四维度有证据即邀请"规则） |
| 4:00–4:30 | 知情面板总览：分组断言清单（脱敏版 + 可展开原话）＋ 可引用经历的记忆勾选 ＋ 隐私档位显式选择 |
| 4:30–5:00 | 批量 confirm → 编译 → 沙盒试聊 → 发布 |

落卡已在对话中逐条被看见并可否决，故结尾面板是复述而非首次告知，30 秒内可完成。

## 9. 编译器侧修正

- `inferStyleProfile` 的 `confidence`（`:1185`）计入 persona 快照条目数，
  否则 5 分钟路径恒为 0.2。
- `buildGenomeIr` 的 values / habits / temporalState（`:1321-1329`）优先取对应 claimType 的
  CONFIRMED claim，硬编码线索词降为兜底。
- voice 优先取 EXPRESSION_STYLE claim，`VOICE_BY_THEME` 降为兜底。

## 10. 提示词修改

`PersonaChatServiceImpl.PERSONA_CHAT_INSTRUCTION`（`:219-230`）按 `groundingLevel` 分支。
现行"不能编造原主人的事实、经历或偏好"中的**偏好**须移出禁令
（偏好在 `PERSONA_CLAIM` 档是主人已确认的自述），但同时新增：

- `STYLE_ONLY`：只借语气与边界回应当下这句话；不得作任何自我描述断言，不得陈述任何事实。
- `PERSONA_CLAIM`：可回答已授权的价值/偏好/需要；不得虚构经历、时间、地点、人物。
- `EMOTION_PATTERN` 条目：保留时态限定，禁用诊断措辞。

现有 `PromptLeakageGuard` 输出侧防护与 `DataMaskingUtils.maskContact` 兜底不变。

## 11. 测试要求

- Grounding 四级各自的判定边界：含"快照非空但本轮无相关证据仍为 UNSUPPORTED"的用例，
  钉住 §3 的关键约束。
- `STYLE_ONLY` 档不得输出自我描述断言（与 `PERSONA_CLAIM` 的隔离）。
- 撤回 / 重编译 / 下架后，运行时人格层随 ACTIVE Genome 立即失效（验证 §4 的快照语义）。
- 确认候选后 `claimType` 保留原始 11 维值（钉住 §6.1）。
- `ACTIVE + AUTO_EXTRACTION` 消费链可匹配到记录（钉住 §6.2 的回归）。
- Mock 抽取器可产出 EXPRESSION_STYLE，无 Key 环境可达 `STYLE_ONLY`（钉住 §6.3）。
- 候选查询按 sessionId 隔离，不返回其他会话候选。
- 候选查询为纯读，不产生 DISMISSED 写入（钉住 §5.1 第 6 条）。
- 第三人别名在同一快照内稳定且一致；非 RELATION 类型中夹带的姓名同样被匿名化。
- 预览 / 编译 / 沙盒使用同一隐私档位值。

## 12. 风险

- **每轮抽取的延迟与成本**：真 Provider 下每轮增加一次结构化调用。已 `@Async` 不阻塞对话，
  但需 §5.1 的租约与 revision 单调性防止抖动。
- **Mock 环境人格层仍偏薄**：即使补齐 EXPRESSION_STYLE，无 Key Demo 的人格层丰富度仍低于
  真 Provider。需在演示脚本中明确说明，不得以 Mock 结果宣称拟真度。
- **`AURORA_UNDERSTANDING` 类型语义变更的影响面**：§6.1 的修正会触及既有纠正/传播路径，
  需检查 `ClaimPropagation` 与 `UserCorrection` 的既有断言。

## 13. 与前次审查的关系

- 本设计使 P0 #5（`/api/echo/{id}/landed` 死链）具备接入意义，但仍需单独接线与防刷。
- P0 #1 / #2 / #3 独立于本设计，仍须修复。
- P1 #8（重编译静默下架）在本链路下**严重性上升**：5 分钟路径的终点即"编译 → 发布"，
  必须先修正文案，否则主人会在最后一步失去刚创建的侧影。
