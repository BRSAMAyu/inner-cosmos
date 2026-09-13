# CP-24 周报 V2 证据引用 + 纠正跨视图旅程 — 增量

- 日期：2026-09-13；实施：主线程
- 愿景：V08（周报不编造——每个维度要么给出可回溯证据，要么如实声明无数据）、V06（用户纠正贯穿所有下游视图）

## 交付内容

### 1. WeeklyReviewV2VO 证据结构

新增两个字段与记录类型：
- `List<EvidenceRef> evidenceRefs` —— `EvidenceRef(dimension, sourceType, sourceIds)`：该维度结论来自哪些真实数据行；
- `List<MissingNote> missingNotes` —— `MissingNote(dimension, reason)`：该维度为什么没有结论。

### 2. WeeklyReviewV2ServiceImpl 诚实填充（三个维度）

- **topThemes**：本周有记忆卡片 → evidenceRefs 带 MemoryCard id；无卡片 → missingNotes 写明「本周没有沉淀记忆卡片——没有材料，不编主题」；
- **dominantEmotion**：有情绪轨迹 → evidenceRefs 带 EmotionTrace id；无轨迹 → 「本周没有情绪轨迹记录——情绪维度无数据，不作猜测」；
- **todoRatio**：有待办 → evidenceRefs 带 TodoItem id；无待办 → 「没有待办记录——完成率不适用，不显示 0%」。

### 3. 纠正跨视图旅程测试（`CorrectionAcrossViewsJourneyTest` 1/1）

同一条用户纠正（「晚睡怕黑开着灯」→「后来发现听雨声比开灯更能让我睡着」）贯穿三个下游视图：
1. `MemoryOperationCommand("UPDATE", ..., expectedVersion=1)` → versionNo==2，纠正后摘要在位；
2. `memoryService.starfield(user)` 含「雨声」、不含「开着灯」（星图视图吃到纠正）；
3. `weeklyReview.generateForRange(...)` topThemes.evidenceRefs 含该卡片 id（周报引用的是纠正后材料），dominantEmotion 落在 missingNotes 而非 evidenceRefs（无情绪数据时不假装有结论）。

## 测试证据

- 新增 1 test（1/1 绿）；周报既有测试（WeeklyReviewV2 系列）随全量回归通过；
- 主线程整合全量回归：**1711 tests, 0 failures, 2 Docker-gated skips，BUILD SUCCESS**。

## 诚实边界

- recommendation 文案维度尚未接入 evidenceRefs（生成逻辑是规则式汇总，非逐条证据推导，接线收益低，暂以既有回归覆盖）；
- 本批只覆盖三个高价值维度；其余维度（dailySnapshots 等）为聚合快照，材料缺失时自然为空列表，无编造面。
