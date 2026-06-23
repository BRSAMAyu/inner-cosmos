# 情绪理解升级 · 设计 Spec（IC-EMO）

版本：v1.0 · 日期：2026-06-19 · 状态：已对齐获批
关联记忆：aurora-emotion-modeling-direction · 关联 run：BAGEL RUN-002

## 0. 目标与定位

把 Inner Cosmos 的情绪识别从**纯关键词匹配**升级为**语义级理解**，并将其确立为 Aurora **用户建模**维度的核心信号，**回流**进 Aurora，使其像朋友一样**克制、自然**地感知用户心情。区分两条时间尺度：实时（动态心情）与中长期（连贯基线）。

非目标：不做夸张的人格突变；不改 Aurora 的身份/宪法；不引入新的外部依赖；不破坏 mock-first 的确定性。

## 1. 现状（已核验，含 file:line）

- `EmotionTraceListener`（[event/EmotionTraceListener.java](../../../src/main/java/com/innercosmos/event/EmotionTraceListener.java)）：6 词硬编码表 + 1:1 weather 映射，异步监听 `DialogFinishedEvent` 写一条 `EmotionTrace`。**最弱环节。**
- `MemorySettlementServiceImpl.settleSession/settleDiary`（[service/impl/MemorySettlementServiceImpl.java](../../../src/main/java/com/innercosmos/service/impl/MemorySettlementServiceImpl.java)）：用 LLM（StructuredAiService）**也写一条 EmotionTrace** → 与监听器**双写重复**。
- `EmotionTimelineServiceImpl`（[service/impl/EmotionTimelineServiceImpl.java](../../../src/main/java/com/innercosmos/service/impl/EmotionTimelineServiceImpl.java)）：日聚合从 **MemoryCard** 取数（已用 LLM），**不读 EmotionTrace**；`EmotionPatternServiceImpl` 读 EmotionTimeline。
- `AgentContextAssembler.weatherLabel()`（[ai/context/AgentContextAssembler.java:275](../../../src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java)）：**已**把最新一条 EmotionTrace 当"情绪天气"注入 Aurora prompt（仅 `weatherType / emotionName`）。← 实时回流的现有接口面。
- `UserPortrait`（dim/valueJson/score/confidence）经 `PortraitReflectionService.applyDeltas` 慢更新，含情绪维 `EMOTION_PATTERN / CURRENT_STATE / ENERGY_RHYTHM`。← 长期落点。
- 现成可复用：`PseudoSemanticAnalyzer`（词典级情感/主题/意图/强度，确定性）、`StructuredAiService`（真 LLM 网关，自带 JSON 修复+兜底+A/B）。
- Schema 迁移范式：`SchemaM5Initializer`（information_schema 探测 + 守卫 ALTER，H2/MySQL 通用，永不阻断启动）。
- 绿线 floor：**455** 测试（master @ 4315074）。

## 2. 协调模型（一个真相源，两条时间尺度）

```
对话/日记 ──► EmotionInsightService ──► 富化 EmotionTrace（唯一真相源）
                                          │  主情绪 + 光谱[{emotion,ratio}] + 强度(0-10) + 触发场景 + 天气 + 来源
            ┌──────────────────────────────┴──────────────────────────────┐
       【实时】最新 trace（会抖动）                            【中长期】EmotionBaselineService
       ▼                                                       ▼  N 日 EWMA 基线（连贯/持久）
  AgentContextAssembler「此刻情绪」注入                    桥接 PortraitReflection 情绪维（缓冲，防抖动）
  · Aurora 克制感知（朋友式，不浮夸）                       · threeModelBlock 注入情绪基线行
  · 前端「能量丸」实时展示                                   · 情绪时间线/星空可视化 + 分析
```

**要害**：实时 trace 直接喂 Aurora 此刻感知 + 能量丸；但**绝不**让每条瞬时 trace 改画像——画像只接受 `EmotionBaselineService` 汇总后的**基线**。这保证"持久性/连贯性"，防止画像被一时情绪带偏。

## 3. 分三期（每期一个 BAGEL slice：TDD + 绿线不破 + 独立评审 + 正向才合并）

### Phase 1 — 情绪理解基础（IC-EMO-001）· 纯数据质量，不改 Aurora 行为

组件：
- `EmotionInsight`（值对象）：`primaryEmotion, emotionScore(0–10), List<{emotion,ratio}> spectrum, triggerScene, weatherType, analysisSource("LLM"|"LEXICON"|"SETTLEMENT")`。
- `EmotionInsightService`（接口+impl，建议置于 `ai.semantic` 或 `service`）：
  - `EmotionInsight analyze(Long userId, String text)`：LLM 主路（`StructuredAiService.call(userId, "EMOTION_INSIGHT", …)`）+ 词典兜底（`PseudoSemanticAnalyzer` + 下述 helper），**永不**退回 6 词硬编码。
  - `void writeTrace(Long userId, Long sessionId, EmotionInsight insight)`：幂等——`sessionId != null` 时按 `(user_id, source_session_id)` 先查后更（仿 `upsertDailyRecord`）；为 null（日记）时 insert。
  - `EmotionInsight fromSettlement(StructuredAiResults.SettlementResult)`：把 settlement 已产出的情绪转成 insight（+ 推导光谱），避免二次 LLM 调用。
- 纯函数 helper（确定性、可单测）：
  - `EmotionWeatherMapper`：`(primaryEmotion, intensity) → weatherType` 矩阵，取代散落的 `EMOTION_WEATHER` / `inferWeather`。
  - `EmotionSpectrumDeriver`：`PseudoSemanticAnalyzer.AnalysisResult → List<{emotion,ratio}>` 确定性推导。

数据/迁移：
- `EmotionTrace` 实体加 `public String emotionSpectrum;`（JSON）、`public String analysisSource;`。
- 新增 `SchemaEmotionSpectrumInitializer`（仿 M5，`information_schema.columns` 探测 + 守卫 ALTER 加两列；旧行 spectrum 为空，向后兼容），并同步 fresh-install DDL（schema.sql 若有定义 tb_emotion_trace）。
- `StructuredAiResults.SettlementResult.EmotionTrace` 可选加 `emotionSpectrum` 字段，让 settlement LLM 直接产出光谱。

接线（去重核心）：
- `EmotionTraceListener.onDialogFinished` → 删掉 6 词块，改 `analyze(text)` → `writeTrace(upsert)`（自动路径）。
- `settleSession` → 不再单独 insert EmotionTrace；改 `fromSettlement(...)` → `writeTrace(upsert 同 session)`（settlement 级覆盖/增强监听器那条，不再双写）。
- `settleDiary` → `fromSettlement(...)` → `writeTrace(sessionId=null)` insert。

错误处理：LLM 失败/不可解析 → StructuredAiService 修复+兜底链最终落到**词典级确定性** insight；`writeTrace` DB try/catch 记日志；迁移守卫永不阻断启动。

测试：
- 单测：`EmotionWeatherMapper` 矩阵；`EmotionSpectrumDeriver` 确定性；`analyze` 兜底路径（mock StructuredAiService 抛错→词典结果）；LLM 路径（mock 返回光谱）。
- 集成：对话结束→1 条富化 trace；再 settle→仍 1 条（已 upsert 增强，spectrum 非空）；日记→null session trace。
- 回归：全量 ≥455 绿（含修订断言旧关键词 weather 的存量测试，如 `EmotionTraceListener` 相关测试）。

验收：去重生效（同 session 不再双写）；trace 含光谱/来源；mock 模式确定性不变；绿线不破。

### Phase 2 — 实时回流 + 能量丸（IC-EMO-002）

- `AgentContextAssembler`：把 `weatherLabel()` 升级/补充为更准的「此刻情绪」信号（主情绪 + 强度 + 简光谱），注入 Aurora prompt；在 PromptBuilder/系统边界处加**克制感知**约束（自然回应、不复述分析、不浮夸）。
- 新增实时心情 API（如 `GET /api/aurora/mood` 或并入现有 context 接口），供前端能量丸取数。
- 前端：先核实 `aurora-chat.html` 与"能量丸"现状（grep 命中 capsule-personality.js/echo-plaza 等，疑为共鸣体能量概念），再决定复用或新建 Aurora 实时心情**能量丸**展示位（Morandi 设计令牌、reduced-motion、a11y）。
- 测试：context 注入单测；mood API 集成测试；前端无 console error、无横向溢出。
- 验收：Aurora 能感知此刻情绪并克制体现；能量丸实时反映；绿线不破。

### Phase 3 — 长期连贯性建模（IC-EMO-003）

- `EmotionBaselineService`：N 日滚动 EWMA 基线（dominant 倾向 + 强度均值/方差 + 稳定度），数据源 EmotionTrace（+ EmotionTimeline）。
- 桥接画像：基线作为 evidence 喂 `PortraitReflectionService` 的情绪维（EMOTION_PATTERN/CURRENT_STATE/ENERGY_RHYTHM），**缓冲式**更新（防抖动）；`AgentContextAssembler.threeModelBlock` 注入情绪基线行。
- 可视化/分析：情绪时间线、星空（weather/halo/新鲜度已有字段）按富化数据增强；可改 `EmotionTimeline` 兼读 trace（吸收原 C 档工作）。
- 测试：基线计算单测（EWMA 确定性）；画像桥接集成（瞬时情绪不直接改画像、基线才改）；可视化数据正确。
- 验收：长期情绪连贯进入画像与可视化；实时与长期两条尺度协调不打架；绿线不破。

## 4. 执行纪律（RUN-002）

- 角色：Supervisor（主会话，不写 src）→ Orchestrator（每期一个，建 worktree off 当前 HEAD，派 Implementer TDD + 独立 Reviewer，跑 `./mvnw test` 验绿不低于 floor，回报）→ Workers。
- Supervisor 独立复跑测试 + ff 合并（正向）/丢弃（负向），再进下一期。
- 测试命令：`JAVA_HOME="/c/Program Files/Java/jdk-21.0.10" M2_HOME="D:/code/inner cosmos" ./mvnw test`。
- 绿线 floor=455，逐期只升不降。
- 边界：不可逆/破坏性、安全隐私、外部世界效果、核心身份变更 → 唤醒用户；其余自主推进。
```
