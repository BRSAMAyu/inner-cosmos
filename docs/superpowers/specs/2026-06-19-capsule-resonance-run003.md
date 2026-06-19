# 共鸣体「让回声真实」改进路线图 — RUN-003

> 日期：2026-06-19 ｜ 主线：共鸣体（Capsule / Echo Capsule）
> 状态：已决策，执行中 ｜ 绿色基线：**507 tests, 0 fail, 0 skip, 0 error**（master @ `ed1fb0e`）
> 约束：BAGEL 角色分离 — Supervisor 只编排不写 `src/**` 产品码；每期 worktree 隔离 → TDD Implementer + 独立 Reviewer → Supervisor 独立验绿 → ff merge。

---

## 0. 背景：共鸣体承诺 vs 现状

共鸣体是 Inner Cosmos 的社交内核：**一个高度拟合用户的 agent，替用户做慢社交代理**，产品哲学 = 郑重慢连接 + 反依赖 + 不浮夸的真实。三份调研（Agent A/B/C）已落到 file:line，结论是：**承诺写在文档里，但代码里有三处让承诺站不住的根**。

| 承诺 | 现状（证据） | 后果 |
|---|---|---|
| 「慢社交边界」真实强制 | 轮次按 session 计（`PersonaChatServiceImpl.java:108-111`）；多开 session 即绕过；前端纯客户端计数刷新归零 | 边界形同虚设 |
| 「画像同步审批」生效 | `CapsuleSyncService.onPortraitOrRelationshipChanged()` **全仓零调用**（仅定义+docs）；失败静默 `:192-194` 只 log | 用户审批了一个永远不会发生的同步 |
| 「回声能量/新鲜度」真实 | `echoEnergy/freshnessScore` 创建时写死（`CapsuleServiceImpl:80-81`、`MockData`），从不更新；plaza 排序与匹配用假信号 | 排行榜与匹配都在演戏 |
| 「按共鸣匹配」智能 | 16 词硬表 + `containsSimilar` 双向子串；种子加权方向写反 `:220`；地板 0.16 `:222-224` | 人人都匹配，种子被压 |

## 1. 三期总览与依赖

```
Phase A (IC-CAP-001)  慢社交边界真实强制         ← 独立，确定性最高，先做
        │
        ▼  (A 改造 reply() 路径，B 在其上叠加能量 bump)
Phase B (IC-CAP-002)  拟合保真 · 回声衰减 · 同步通电
        │
        ▼  (C 的 energyScore 读 B 产出的动态 echoEnergy)
Phase C (IC-CAP-003)  智能匹配 · 语义重叠 + 动态能量
```

执行节奏（用户指定）：A 完成 → git 提交 → 派独立审查 agent（后台）→ **不停手**继续 B；B 完成 → 提交 → 审查 agent（后台）→ 继续 C；C 完成 → 提交 → 审查 agent。三期全完 → 派 **2 个 agent 验收**。

## 2. 关键决策（Supervisor 裁定，理由附注）

- **D1 · SEED 配额**：所有共鸣体（含 SEED）统一走「按 visitor×capsule×日」配额表。SEED 用一个高位常量（如 `SEED_EFFECTIVE_DAILY_LIMIT = 200`）——修复 session 绕过，但保留 SEED「实际不限」的体感。**理由**：反依赖边界应对全平台统一；真正要修的是「按 session 计数」这个绕过，不是 SEED 的可访问性。
- **D2 · 同步触发**：接通 `onPortraitOrRelationshipChanged()` 的调用方为「画像写入点 + 记忆变化事件 + 夜间基线 bridge」，三处触发源靠 **PENDING 去重**（同 capsule 已有 PENDING 则更新 diff、不新增行）防风暴。**理由**：复用现成事件总线，不新建调度链。
- **D3 · 通知不污染慢信**：新建独立 `tb_notification`，不复用 `tb_slow_letter`。**理由**：慢信有「投递仪式感」语义，系统通知（同步失败/完成）是不同物件。
- **D4 · 匹配地板**：移除 0.16 地板，改为「零语义重叠 → 近零分 → 自然落榜」。**理由**：地板是「人人都匹配」的直接元凶。
- **D5 · 种子加权方向**：纠正为 SEED 高位（`+0.12`）、用户体低位（`+0.06`）。**理由**：种子是官方精修高质量体，应优先；当前写反。
- **D6 · 能量/新鲜度双轨**：`echoEnergy` 由活跃度（成功对话轮）上扬 + 夜间乘性衰减趋近地板；`freshnessScore` 由近因驱动（活跃即回弹、夜间衰减趋零）。**理由**：分别表达「活跃度」与「新鲜度」两个不同语义，对应 plaza 排序与匹配两类信号。

> 以上均为可逆产品决策。用户若不认同任一条，可在任一期合并前叫停。

---

## 3. Phase A — IC-CAP-001 慢社交边界真实强制

### 目标
把对话上限从「按 session」改成「按 visitor×capsule×日」硬配额；代码侧加第二道硬闸（不依赖 LLM 边界文本）；前端剩余轮次读后端真值。

### 根因证据（file:line）
- `PersonaChatServiceImpl.java:108-111` — 唯一判额点，`session.turnCount >= session.dailyLimit`，范围单 session。
- `PersonaChatServiceImpl.java:76-78` — SEED → `dailyLimit=0`，被 `dailyLimit > 0` 短路成无限。
- `PersonaChatServiceImpl.java:158` — `turnCount++` 只作用于当前 session。
- `PersonaChatServiceImpl.java:228-237` — `verifyOwnership` 只校验归属，不防多开。
- `schema.sql:233-244` — `tb_persona_chat_session` 仅有非唯一 `idx_persona_session_visitor`。
- 前端 `capsule-chat.html:227-358` — `usedTurns++` 纯客户端，刷新归零。
- **零** persona chat 测试。

### 设计
新增 `tb_capsule_daily_quota(visitor_user_id, capsule_id, quota_date, turn_count, daily_limit)`，`UNIQUE(visitor_user_id, capsule_id, quota_date)`。配额行在「当日首次创建 session」时 idempotent upsert，`daily_limit` 当日冻结。

有效日上限：
```
SEED → SEED_EFFECTIVE_DAILY_LIMIT = 200   (体感"不限"，但走同一套配额机制)
非SEED → clamp(2, capsule.conversationLimitPerDay ?: 30, 50)
```

**第二道硬闸**（代码侧，LLM 之前）：`reply()` 入口先查当日配额行，`turn_count >= daily_limit` 直接返回引导文本 + 置 `LETTER_GUIDED`，**且不写新的 visitor 消息**（避免占额度之外的副作用）。session.turnCount 保留做兼容展示，但**权威计数 = quota.turn_count**，reply 成功后 `quota.turn_count += 1`。

### 新增文件
1. `entity/CapsuleDailyQuota.java`（`@TableName("tb_capsule_daily_quota")`，字段：visitorUserId, capsuleId, quotaDate(LocalDate), turnCount, dailyLimit）
2. `mapper/CapsuleDailyQuotaMapper.java`（`@Select` 按 (visitor,capsule,date) 查、upsert、`incrementTurn`）
3. `config/SchemaCapsuleQuotaInitializer.java`（`@Order(7)`，建表 + UNIQUE，仿 `SchemaM5Initializer` 的 information_schema 探测 + guarded DDL；同时更新 fresh-install `schema.sql`）
4. `dto/CapsuleQuotaVO.java`（turnCount, dailyLimit, remaining, isSeed, quotaDate）
5. `PersonaChatQuotaIntegrationTest.java`（见测试计划）

### 修改文件
- `PersonaChatServiceImpl.java`
  - 注入 `CapsuleDailyQuotaMapper`。
  - `create()`：计算 effective daily limit，upsert 当日 quota 行（已存在则不覆盖 daily_limit）。
  - `reply()`：入口加硬闸（quota 查询 + 超限即返回引导、不插 visitor 消息）；成功分支末尾 `quota.turn_count += 1`（权威）；保留 session.turnCount++ 做展示。
- `PersonaChatService.java`：+ `quota(userId, capsuleId)` 方法。
- `PersonaChatController.java`：+ `GET /api/persona-chat/quota?capsuleId=` 返回 `CapsuleQuotaVO`。
- `static/js/api.js`：+ `personaChatQuota(capsuleId)`。
- `static/pages/capsule-chat.html`：剩余轮次改为初始化 + 每轮后从 `/quota` 拉真值；移除纯客户端 `usedTurns++`；SEED 显示「不限」。

### 测试计划（TDD red→green）
1. `multiSession_doesNotBypassDailyLimit`：同一 visitor+capsule，开 3 个 session，发满上限后第 3 个 session 仍被闸。**red 前置**（当前代码会过）。
2. `seedCapsule_effectivelyUnlimited`：SEED 单 session 发 50 轮不被提前闸（cap=200）。
3. `quota_incrementsAcrossSessions`：两 session 交替发，turn_count 累计在 quota 行。
4. `quotaEndpoint_returnsRemaining`：`GET /quota` 数值正确。
5. `newDay_resetsQuota`：跨 quota_date 后 turn_count 归零（用不同 date 插桩）。

### 验收
- 上述 5 测试 green；全量 ≥ 507+5；master 干净；ff merge。

---

## 4. Phase B — IC-CAP-002 拟合保真 · 回声衰减 · 同步通电

### 目标
三件事：(1) 接通死掉的同步链路 + 失败可感（通知+重试）；(2) `echoEnergy/freshnessScore` 变成活跃驱动 + 夜间衰减的真实信号；(3) 记忆/画像变化触发重生成（去重防风暴）。

### 根因证据（file:line）
- `CapsuleSyncService.java:75` `onPortraitOrRelationshipChanged` — **零生产调用方**（grep 确认：仅定义 + docs）。
- `CapsuleSyncService.java:192-194` — `asyncRegenerate` catch 只 `log.error`，不回写、不通知、不重试。
- `CapsuleSyncQueue.java` — 仅 `status(PENDING/APPROVED/REJECTED)` + `createdAt/decidedAt`，无 `FAILED/SYNCED`、无 `attemptCount/lastError/failedAt/nextRetryAt`。
- `CapsuleSyncService.java:103-105` — 只同步 `USER_CAPSULE`（seed 跳过，合理）。
- `schema.sql:203-204` — `echo_energy/freshness_score DOUBLE DEFAULT 0`，无任何更新路径。
- `CapsuleServiceImpl.java:80-81` / `MockDataInitializer:202-203,359-360` — 创建时硬编码。
- `CapsuleServiceImpl.java:183`、`:219`、`DashboardServiceImpl:63` — 三处只读消费这两个假信号。
- 事件总线：`DialogFinishedEvent` 已由 `DialogServiceImpl:97` 发布，5 个 `@Async` 监听器消费。`MemoryServiceImpl.extractFromSession` 直接 insert、不发记忆事件。
- `NightlyMemorySettlementJob.java:39` `@Scheduled(0 0 2 * * ?)` 每用户循环 + per-user try/catch — 衰减挂载点。
- `LetterDeliveryJob.java:18-87` — 完整 retry+backoff 模板，重试 job 直接抄。
- **零** sync/energy 测试。

### 设计

#### B-1 同步通电 + 去重
- `UserPortraitService.applyDeltas()` 末尾、夜间 `EmotionBaselineService.bridgeToPortrait()` 之后、`MemoryServiceImpl.extractFromSession` 之后 → 三处触发 `syncService.onPortraitOrRelationshipChanged(userId)`。
- `onPortraitOrRelationshipChanged` 加去重：对该 user 已有 PENDING 的 capsule，**更新** `proposedContextDiff` + 刷新 `createdAt`，不新增行（避免风暴）。
- 记忆触发：新增 `MemoryChangedEvent(userId)` 由 `MemoryServiceImpl.extractFromSession:96` 之后 `publishEvent`；新增 `CapsuleRegenerateListener`（`@Async("taskExecutor")`）消费 → 调 `onPortraitOrRelationshipChanged`。

#### B-2 失败可感 + 重试
- `CapsuleSyncQueue` 扩字段：`attemptCount INT DEFAULT 0`、`lastError TEXT NULL`、`failedAt TIMESTAMP NULL`、`nextRetryAt TIMESTAMP NULL`；状态机加 `FAILED` / `SYNCED`。
- `asyncRegenerate`：try 成功 → queue `APPROVED→SYNCED` + `Notification(SYNC_DONE)`；catch → `FAILED` + `attemptCount++` + `lastError` + `nextRetryAt`（指数退避）+ `Notification(SYNC_FAILED)`。
- 新增 `CapsuleSyncRetryJob`（`@Scheduled fixedDelay`，抄 `LetterDeliveryJob`）：扫 `FAILED && nextRetryAt<=now && attemptCount<MAX_ATTEMPTS` → 重跑 regen。
- `CapsuleSyncController`：+ `POST /retry`（手动重试单条）；`/pending` 同时返回 `FAILED` 项供用户看到失败。

#### B-3 通知基础设施
- 新建 `tb_notification(id, user_id, type, title, body, ref_id, ref_type, read, created_at)`。
- `entity/Notification.java` + `mapper/NotificationMapper.java` + `service/NotificationService.java`（`notify(userId, type, title, body, refId, refType)`、`unread(userId)`、`markRead(id)`）。
- `controller/NotificationController.java`：`GET /api/notifications`、`POST /api/notifications/{id}/read`。
- 同步成功/失败两类通知通过它发。

#### B-4 回声衰减 + 活跃上扬
- `EchoCapsule` 加列（`SchemaCapsuleQuotaInitializer` 同一迁移器或独立 `SchemaCapsuleEnergyInitializer @Order(8)`）：`last_activity_at TIMESTAMP NULL`（echo_energy/freshness 已存在，复用）。
- **活跃上扬**（挂在 Phase A 已改造的 `reply()` 成功分支）：成功一轮对话后 `echoEnergy = min(1.0, echoEnergy + 0.02)`、`freshnessScore = min(1.0, max(freshnessScore, 0.9))`、`last_activity_at = now`。需要注入 `EchoCapsuleMapper`（已有）。
- **夜间衰减**（挂 `NightlyMemorySettlementJob` 每用户循环）：对该用户所有公开 capsule：`echoEnergy = max(0.3, echoEnergy * 0.97)`、`freshnessScore = max(0.0, freshnessScore * 0.95)`。纯确定性。

### 新增文件
1. `event/MemoryChangedEvent.java`
2. `event/CapsuleRegenerateListener.java`（`@Async`）
3. `scheduler/CapsuleSyncRetryJob.java`
4. `entity/Notification.java` + `mapper/NotificationMapper.java`
5. `service/NotificationService.java` + `service/impl/NotificationServiceImpl.java`
6. `controller/NotificationController.java`
7. `config/SchemaCapsuleEnergyInitializer.java`（`@Order(8)`，echo_capsule 加 `last_activity_at`；sync_queue 加 4 字段；建 `tb_notification`）—— 或并入 Phase A 的迁移器，按 Orchestrator 判断。
8. 测试：`CapsuleSyncWiringIT`、`CapsuleSyncRetryJobTest`、`CapsuleEnergyDecayTest`、`NotificationServiceTest`、`CapsuleRegenerateListenerTest`。

### 修改文件
- `ai/capsule/CapsuleSyncService.java`：去重 + 失败回写 + 通知 + `retryFailed()` 方法。
- `ai/capsule/CapsuleContextRegenerator.java`：成功后回填 `lastMemoryUpdateAt` + queue→SYNCED。
- `entity/CapsuleSyncQueue.java`：+ 4 字段。
- `entity/EchoCapsule.java`：+ `lastActivityAt`。
- `ai/portrait/UserPortraitService.java`（或 impl）：`applyDeltas` 后触发同步。
- `service/impl/MemoryServiceImpl.java`：`extractFromSession` 后 publish `MemoryChangedEvent`。
- `scheduler/NightlyMemorySettlementJob.java`：循环内加能量衰减调用。
- `service/impl/PersonaChatServiceImpl.java`：reply 成功分支加能量上扬（在 Phase A 改造之上）。
- `controller/CapsuleSyncController.java`：+ `/retry`，`/pending` 返回 FAILED。
- `static/js/api.js` + `static/pages/echo-plaza.html`（或合适页面）：通知小红点 / 失败重试入口（最小）。

### 测试计划
1. `syncTrigger_createsPendingRow`：画像 delta → 产生 PENDING 行（**red 前置**：当前无调用方）。
2. `syncTrigger_dedupesMultipleChanges`：连续 3 次 delta → 同 capsule 仅 1 行 PENDING（diff 更新）。
3. `asyncRegenerate_failure_marksFailedAndNotifies`：regen 抛异常 → queue=FAILED + attemptCount=1 + 1 条 SYNC_FAILED 通知。
4. `retryJob_retriesUntilMaxThenGivesUp`：3 次失败后停止重试。
5. `energyBumps_onSuccessfulTurn`：reply 成功 → echoEnergy↑、freshness↑、lastActivityAt 写入。
6. `nightlyJob_decaysEnergy`：跑夜间 job → echoEnergy*f、freshness*f、趋近地板。
7. `memoryChanged_triggersRegen`：publish `MemoryChangedEvent` → 监听器调 sync（mock 验证）。
8. `notification_readUnreadMarkRead`：通知读写生命周期。

### 验收
- 上述 8 测试 green；全量 ≥ 507 + A5 + B8；同步链路真的通电（pending 端点在画像变化后有行）。

---

## 5. Phase C — IC-CAP-003 智能匹配 · 语义重叠 + 动态能量

### 目标
用 `PseudoSemanticAnalyzer` 的 6 主题族 + 意图/强度替换 16 词硬表，叠加 Phase B 动态能量，修种子方向 + 移除地板，让星海广场真正按共鸣推荐。

### 根因证据（file:line）
- `CapsuleServiceImpl.java:188-235` `matchedCapsules`：
  - `:194-200` userSignals 来自记忆 keyword/emotion tags + 16 词表命中
  - `:208-210` capsuleSignals = publicTags + pseudonym + intro
  - `:218` `semanticScore = min(0.72, overlap.size()*0.12)`
  - `:219` `energyScore = min(0.18, echoEnergy*0.18)` ← 读 Phase B 动态能量（依赖确认）
  - `:220` `seedBoost = SEED ? 0.04 : 0.08` ← **方向写反**
  - `:221` `score = min(0.99, Σ)`
  - `:222-224` `if(score<0.16) score=0.16+energyScore` ← **地板，人人匹配**
  - `:233-234` sort desc, limit 12
- `:364` 16 词硬表（项目/考试/关系/朋友/边界/孤独/日记/真实AI/Aurora/行动/拖延/深夜/睡前/产品/理解/慢社交）。
- `:371-381` `containsSimilar` 双向子串 → 误命中。
- `PseudoSemanticAnalyzer.java`（`ai/semantic/`）：6 主题族（任务压力/关系牵动/情绪承压/认知探索/自我评价/希望期待）、`AnalysisResult{detectedThemes, primaryIntent, sentimentScore, intensityScore, extractedKeywords, ...}`，静态 `analyze(text)`，**无需注入**。
- 用户侧强信号未被用：`UserPortrait` 10 维（`CURRENT_STATE/EMOTION_PATTERN/INNER_DRIVE/...`），`matchedCapsules` 当前完全不读。
- 前端 `echo-plaza.html` 只依赖 `matchScore/matchSummary/capsule` → **后端改分前端无需动**。
- **零** matching 测试。

### 设计
新打分（确定性，无 LLM）：
```
userThemeProfile   = 聚合 PseudoSemanticAnalyzer.analyze(24 条记忆).detectedThemes  (带频次)
capsuleThemeProfile= 聚合 analyze(capsule.intro + publicTags 文本).detectedThemes
themeOverlap       = 交集主题的加权（频次 min 归一化）        // 0..0.55
portraitSignal     = UserPortrait(CURRENT_STATE/EMOTION_PATTERN/INNER_DRIVE) 与 capsule publicTags 语义重合  // 0..0.20
energyScore        = Phase B 动态 echoEnergy * 0.18           // 0..0.18（真实）
seedBoost          = SEED ? 0.12 : 0.06                        // D5 纠正方向
score              = min(0.99, themeOverlap + portraitSignal + energyScore + seedBoost)
// 不再有地板：零重叠 → ~seedBoost，自然落榜
```
`matchReasons` = 交集主题 top5（人类可读）；`matchSummary` 用主题词生成。

### 修改文件
- `CapsuleServiceImpl.java`：
  - 注入 `UserPortraitMapper`（或 service）。
  - 重写 `matchedCapsules` `:188-235`：用 `PseudoSemanticAnalyzer.analyze` 建主题画像，按上式打分。
  - 删 `addTerms` 16 词表 `:364` 与 `containsSimilar` `:371-381`（或保留 containsSimilar 给非匹配用途，按 Orchestrator 判断）。
- `schema.sql` / 迁移：无（纯算法）。

### 测试计划
1. `zeroOverlap_dropsBelowTopN`：用户记忆与某 capsule 零主题重叠 → 该 capsule 分 ≈ seedBoost，不进 top12。**red 前置**（当前地板 0.16 强制入选）。
2. `seedSemanticMatch_ranksAboveUserCapsule`：同语义命中下 SEED 排在用户体之前（方向纠正）。
3. `themeOverlap_drivesRanking`：高主题重叠 capsule 排首。
4. `portraitSignal_contributes`：有 portrait 维度数据时分数高于无。
5. `energyScore_usesDynamicEnergy`：Phase B 高能量 capsule 得分高于低能量（同语义）。
6. `matchReasons_areHumanThemes`：返回的主题是 6 族中的人类可读词。

### 验收
- 上述 6 测试 green；全量 ≥ 507 + A5 + B8 + C6；前端广场不需改。

---

## 6. BAGEL 执行协议（每期）

1. **Supervisor**（本会话）：从 master 切 worktree 分支 `run-003/cap-00X`。
2. **Orchestrator**（单 agent，worktree 内）：
   - 读本 spec 对应 Phase 段 + 列出的根因文件。
   - TDD：先写 red 测试（每期"测试计划"第 1 条为 red-before 证据），再实现至 green。
   - 实现遵守"最小改动清单"，不越界改其他子系统。
   - 跑测试：`JAVA_HOME="/c/Program Files/Java/jdk-21.0.10" M2_HOME="<worktree根>" ./mvnw clean test` —— **M2_HOME 必须指向 worktree 根**，否则 ClassNotFoundException。
   - 自检通过后回报：新增/改动文件、测试数、关键决策偏离。
3. **Supervisor 独立验绿**：在 master 干净前提下，对 worktree 跑同一测试命令确认 ≥ 基线 + 本期新增、0 fail。
4. **ff merge** 到 master。
5. **git commit**（每期一次， conventional commits：`feat(capsule): IC-CAP-00X ...`）。
6. **派独立审查 agent**（后台运行），Supervisor **不停手**进入下一期 worktree。
7. 审查 agent 发现的阻塞性问题 → Supervisor 立即处理（fix-forward 新提交，不 amend）。

## 7. 全局验收（三期完成后）

派 **2 个独立 agent** 做验收审查：
- **Agent-验收-1（正确性 & 边界）**：复核三期 red-before 证据真实、配额绕过/同步死链/匹配地板三个根因确被修复、无回退。
- **Agent-验收-2（架构 & 副作用）**：复核跨期耦合（A reply→B 能量 bump、B 能量→C 匹配）正确、无 N+1/事务漏洞、新表迁移 H2/MySQL 双兼容、绿色基线未破。

## 8. 绿色基线与回滚
- 起点：507/0/0/0 @ `ed1fb0e`。每期后递增（A+5, B+8, C+6 → 终态 ≈ 526）。
- 任一期独立验绿失败 → 不 merge，worktree 内修；绝不 amend 已发布提交。
- 任一期引入回退 → 修在新提交里。
- 硬停边界（唤醒用户）：不可逆/破坏性、安全/隐私（PII 泄露风险）、外部世界副作用、核心身份变更。其余自主推进。

## 9. 风险登记
- **R1 · 同步触发风暴**：多触发源 → 靠 PENDING 去重（B-1）。测试覆盖。
- **R2 · 能量衰减把老 capsule 打到地板**：地板 0.3 保底，活跃即回弹；DashboardServiceImpl 也消费，需回归。
- **R3 · 匹配改分改变广场排序体感**：移除地板后部分 capsule 落榜是预期；前端契约不变。
- **R4 · H2 vs MySQL 迁移差异**：所有新表/列走 information_schema 探测 + guarded DDL，fresh-install schema.sql 同步更新。
