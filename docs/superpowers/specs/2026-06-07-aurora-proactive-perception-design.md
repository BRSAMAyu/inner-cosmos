# Aurora 主动式 + 真实感知 + 用户画像演化 + 模式切换 + 告别流 设计

**Date**: 2026-06-07
**Status**: Draft (awaiting user review)
**Scope**: 8 sub-milestones (M0–M7) of the Aurora agent upgrade

---

## 1. Background & Motivation

Aurora currently has partial implementations of the perception and proactive features
that the product promises. An honest audit (2026-06-07) found:

- **Real**: multi-bubble reply, user context injection, weather injection.
- **Half-wired**: proactive (no scheduler), time (only in greeting path),
  location (label flows, no real perception).
- **Missing/fake**: rich card rendering, server-pushed proactive messages,
  environment "state" beyond keyword matching.
- **Broken**: weekly review (schema mismatch between backend and frontend),
  emotion patterns (404 endpoints), and config-only user profile that never
  evolves from interactions.

This spec covers an end-to-end upgrade that turns Aurora into a real
longitudinal companion:

1. Aurora perceives time, weather, and location in LLM-meaningful form.
2. The user profile is multi-dimensional, written back from real interactions.
3. Aurora proactively reaches out under user-defined intensity, respecting
   quiet windows derived from todos, focus blocks, and sleep.
4. The user can switch Aurora's mode (DAILY_TALK / THOUGHT_CLARIFY / SOCRATIC)
   mid-conversation without losing context.
5. The user can switch the LLM model per session.
6. Sessions have a real goodbye that consolidates memory, updates portrait,
   runs analysis, and syncs relevant EchoCapsules — next session remembers.
7. EchoCapsules receive the updated portrait with PII filtered out, and the
   user controls what each capsule is allowed to see.

---

## 2. Architectural Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端 (browser)                          │
│  aurora-chat.html                                               │
│   ├─ 模式切换条 (DAILY_TALK / THOUGHT_CLARIFY / SOCRATIC / …)    │
│   ├─ 主动式接收 (SSE/poll 拉新事件)                              │
│   ├─ 模型选择器 (下拉：minimax/mimo/glm/deepseek)                │
│   └─ "温柔告别" 按钮 (触发 goodbye flow)                         │
│  settings.html                                                   │
│   └─ 主动式强度 / 免打扰窗口 / 焦点时段 / 睡眠时段               │
└──────────────────┬──────────────────────────────────────────────┘
                   │ HTTP/SSE
┌──────────────────┴──────────────────────────────────────────────┐
│                       Spring Boot 服务                           │
│                                                                 │
│  ┌─ Agent 层 ──────────────────────────────────────────────┐    │
│  │  AuroraAgentServiceImpl   (按 mode 路由 prompt)         │    │
│  │  AgentContextAssembler    (装配 context)                │    │
│  │  PromptBuilder            (per-mode prompt 模板)       │    │
│  │  AgentReflectionService   (新：写回画像 / 抽长记忆)     │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 感知层 (M0) ───────────────────────────────────────────┐    │
│  │  TimeContextService      (now + todo 上下文)            │    │
│  │  WeatherContextService   (实时 + 24h 预报 + 城市)       │    │
│  │  GeocodingService        (经纬度 → 城市/街道)          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 画像 / 记忆 (M1) ──────────────────────────────────────┐    │
│  │  UserPortraitService     (6 维 + 演化历史)              │    │
│  │  LongTermMemoryService   (姓名/年龄/职业/爱好 等键值)   │    │
│  │  SessionSummaryService   (每会话：索引/摘要/关键)       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 主动式 (M2) ───────────────────────────────────────────┐    │
│  │  ProactiveEngine         (强度→频次表)                  │    │
│  │  QuietWindowResolver     (免打扰/待办/焦点/睡眠叠加)   │    │
│  │  EventTriggerMatcher     (事件→触发模板)                │    │
│  │  AuroraProactiveJob @Scheduled  (随机+事件合并)        │    │
│  │  ProactiveDeliveryChannel (SSE push 到前端)            │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 告别 / 分析 (M3+M4) ───────────────────────────────────┐    │
│  │  GoodbyeOrchestrator     (触发→同步→画像→分析)        │    │
│  │  AnalysisPipeline         (情绪模式 / 周报真源)         │    │
│  │  WeeklyReviewV2Service   (修 schema 错位)              │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 共鸣体同步 (M7) ───────────────────────────────────────┐    │
│  │  CapsuleSyncService      (画像变 → 触发同步)            │    │
│  │  PiiPrivacyFilter        (实名/精确位置 → 化名/城市)   │    │
│  │  CapsuleReviewQueue      (用户可控的待审/拒收队列)      │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────┬──────────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │   H2 file DB         │
        │   新增表：            │
        │   - tb_user_portrait (6 维 + 演化历史 JSON)          │
        │   - tb_user_long_term_memory (key/value)            │
        │   - tb_session_summary (索引/摘要/关键)             │
        │   - tb_proactive_event_log                          │
        │   - tb_chat_session.current_mode / preferred_model  │
        │   - tb_capsule_sync_queue                           │
        └─────────────────────┘
```

### 2.1 Three cross-cutting design principles

1. **Portrait is a first-class citizen.** The 6-dim portrait (M1) must be
   readable from M0/M2/M3/M5/M6/M7 prompts — not only by M1.
2. **Goodbye is a real goodbye.** M3 → Aurora enters "inactive" mode:
   proactive stops, real-time context stops being injected. Next session is
   a new `tb_chat_session` row, but Aurora still reads the previous
   `tb_session_summary` and `tb_user_long_term_memory`.
3. **Privacy by default isolation.** The PII filter rules in §6 are shared
   across the whole system. Fields the user has rejected are never
   written back to any capsule.

---

## 3. Milestone Map (M0–M7, dependency-ordered)

| # | Name | Core deliverable | Acceptance |
|---|---|---|---|
| **M0** | Context perception upgrade | `TimeContextService` / `WeatherContextService(24h)` / `GeocodingService` | Aurora says "上海 徐汇区 22°C 明天有雨"; city resolves correctly; rain triggers proactive mention |
| **M1** | 6-dim portrait + evolution | `tb_user_portrait` (6 dims, score+confidence+updated_at) + history table + light/heavy reflection | Portrait table shows multi-dim changes after goodbye; prompt receives structured fields, not free text |
| **M2** | Proactive engine | 5 intensity levels + 4-layer quiet window (quiet hours / todos / focus / sleep) + random + event triggers + SSE push | Intensity=COMPANION → proactive actually fires; OFF → 0 pushes; todo/focus/sleep windows are silent |
| **M3** | Goodbye flow | 3 triggers (button / idle 30min / language detect) → session summary + long-term memory + portrait rewrite + analysis pipeline trigger | Click goodbye → Aurora really says goodbye → `tb_session_summary` has new row → portrait changed |
| **M4** | Data analysis + weekly review | `tb_weekly_review` rebuilt to match frontend schema; emotion patterns from real aggregation; weekly review from real daily records | Weekly review page shows real data (no more "本周回顾" placeholder); emotion timeline has real curve |
| **M5** | Mode-aware prompt | `PromptBuilder` injects mode-specific segment (DAILY_TALK / THOUGHT / SOCRATIC) + transition turn preserves context | After mode switch, Aurora changes tone but references the previous turn |
| **M6** | LLM model selector | Per-session model preference dropdown in chat header; backend locks to chosen model for the session | Pick deepseek → Aurora's `lastProvider` for that session is DEEPSEEK throughout |
| **M7** | Capsule sync + PII | Portrait change → `CapsuleSyncEvent` → PII filter → user approval queue | Change portrait → related capsule prompt reflects new portrait (pseudonymized) within 24h, pending user OK |

---

## 4. Core Flows

### 4.1 Profile light write-back (every N messages)

```
[User sends N messages]
       │
       ▼
PortraitReflectionService.reflectOnTurn(messages, existingPortrait)
       │
       │  LLM call, strict JSON output:
       │  { dimension: "communication_style",
       │    delta: "用户更倾向具体例子而不是抽象概念",
       │    confidence: 0.72,
       │    evidence_turn_ids: [...] }
       │
       ▼
PortraitRepository.applyDeltas(deltas)
       │  - accumulate score (bounded 0..1)
       │  - decay old confidence
       │  - write portrait_history row (audit)
       │
       ▼
[Next Aurora context pull → sees updated portrait]
```

**Hard constraint**: LLM output is structured JSON. Free-form prose
("我觉得用户...") is rejected. Deltas go to `tb_user_portrait_history`;
the main `tb_user_portrait` table is the **current snapshot**, history is
the **evolution trail**.

### 4.2 Proactive trigger merge (random + event, dedup)

```
AuroraProactiveJob @Scheduled every 5min
       │
       ▼
ProactiveEngine.tick(userId)
       │
       ├─ 1. Pull candidate events (last 5 min):
       │     - mood_drop  (emotion weather CLEAR/SUNNY → RAINY/STORM)
       │     - todo_completed  (user just checked off a todo)
       │     - todo_upcoming_soon  (todo within 15min)
       │     - weather_change_to_bad  (24h forecast worsened)
       │     - dormant_n_days  (no interaction for 3+ days)
       │     - daily_memory_added  (diary/memory added)
       │
       ├─ 2. QuietWindowResolver.canPushNow()  ──→  4-layer OR:
       │     - quietHoursStart/End
       │     - current time inside a todo's scheduled window
       │     - focusModeEnabled && current in focusWindows
       │     - inferred sleep (23:00–07:00 + no activity tonight)
       │     Any layer true → silent (return 0 candidates)
       │
       ├─ 3. Intensity → daily random budget:
       │     OFF=0   WHISPER=0/day   LIGHT=1/day
       │     ACTIVE=3/day   COMPANION=6/day
       │     budget = intensity-level - already-sent-today
       │
       ├─ 4. Merge events + random slots, dedup (same type within 24h)
       │
       ▼
For each candidate user: LLM generates push content
       │
       ▼
ProactiveDeliveryChannel.push(userId, msg) → SSE → frontend
       │
       ▼
Write tb_proactive_event_log
  (userId, type, content, ts, accepted?)
```

**Key decision**: intensity controls **frequency budget only**.
Silence is a separate concern owned by `QuietWindowResolver`. The two
axes are orthogonal.

### 4.3 Goodbye flow (3 trigger paths → 1 orchestrator)

```
Trigger sources:
  (A) User clicks "温柔告别" button
  (B) Aurora detects ≥2 farewell-signal words ("我要走了"/"拜拜"/"晚安"/…)
  (C) 30min idle timer

       │  (any one)
       ▼
GoodbyeOrchestrator.startSession(userId, sessionId, trigger)
       │
       ├─ Step 1: Aurora says goodbye (LLM real generation, not template)
       │           "谢谢你今天陪我聊了那么多。明天见。"
       │           (SSE push, wait for frontend ack or 5s timeout)
       │           On LLM failure → fall back to local template line.
       │
       ├─ Step 2: Session summary (async, non-blocking)
       │   SessionSummaryService.summarize(messages)
       │     → tb_session_summary { userId, sessionId,
       │         summary_2_sentences, key_topics[],
       │         emotional_arc, timestamp }
       │
       ├─ Step 3: Long-term memory extraction (1 LLM call)
       │   LongTermMemoryExtractor.extract(messages, existing)
       │     → diff against existing, upsert to tb_user_long_term_memory
       │     → enum types: NAME / AGE / OCCUPATION / HOBBY /
       │                  HABIT / RELATION / LOCATION / HEALTH /
       │                  GOAL / PREFERENCE
       │
       ├─ Step 4: Portrait heavy rewrite (1 LLM call, stronger than
       │   light reflection)
       │   PortraitReflectionService.heavyReflect(messages,
       │                                          existingPortrait)
       │     → output 6-dim portrait JSON, replace main table
       │     → old values go to history
       │
       ├─ Step 5: Trigger analysis pipeline
       │   AnalysisPipelineService.processSessionClosure(userId,
       │                                                  sessionId)
       │     → re-aggregate weekly emotion curve
       │     → re-cluster memory themes
       │     → refresh emotion_patterns materialized view
       │
       ├─ Step 6: Capsule sync
       │   CapsuleSyncService.onPortraitChanged(userId)
       │     → see §4.6
       │
       ▼
Return to frontend:
  { status: "goodbye_complete",
    remembered: [3 new facts],
    portrait_changed: true/false,
    capsules_synced: 2 }
```

**Hard rules**:
- Step 1 must complete within 3s; on timeout, send a template fallback
  so the user never gets stuck waiting for a goodbye.
- Steps 2–6 are async. UI returns within 5s; the rest runs in background
  and pushes status updates.
- If all LLMs are down: Step 1 uses local template; Steps 2–6 marked
  "deferred", retried on next service recovery.
- If the user sends a new message mid-goodbye, abort the rest of the
  flow and put the session back to active.

### 4.4 Session recall (next session starts)

```
New session opens
   │
   ▼
AgentContextAssembler.assemble(userId, sessionId)
   │
   ├─ Pull tb_session_summary for last 5 sessions (desc by time)
   │     → "前情提要" segment:
   │       "你之前跟 Aurora 聊过:
   │        - 6/5: 项目 deadline 压力, Aurora 选用了分析型回应
   │        - 6/3: 母亲节没回家, Aurora 陪你整理情绪
   │        - ..."
   │
   ├─ Pull tb_user_long_term_memory (full)
   │     → "关键事实" segment (token-budgeted to < 800 tokens):
   │       "已记住: 名字=林澈, 28岁, 前端工程师,
   │        爱好=徒步/黑胶, 习惯=23:00 睡, 住=上海徐汇"
   │
   ├─ Pull tb_user_portrait (full)
   │     → "性格快照" segment
   │
   ▼
PromptBuilder injects (token budget enforced)
```

**Session boundary = psychological boundary**: two sessions are
different `tb_chat_session` rows, but Aurora sees a continuous
relationship.

### 4.5 Mode switch (preserves context)

```
User clicks [思维整理]
   │
   ▼
Frontend: API.auroraModeSwitch({ sessionId,
                                 newMode: "THOUGHT_CLARIFY" })
   │
   ▼
Backend:
   1. tb_chat_session.current_mode = "THOUGHT_CLARIFY"
   2. Insert a "system turn" (hidden from user) into message history:
      "--- 用户希望切换到「思维整理」模式 ---
       从这一刻起: 把混乱内容拆成事实/感受/担心/需要/下一步。
       不要给建议，先帮 ta 理清楚。
       注意: 用户原本在进行倾诉, ta 不是否定之前的陪伴,
       是想升级到下一步。承接这份信任。"
   3. Trigger Aurora's "mode-acknowledgement" (M5 → M2 integration):
      "好，那我们来把刚才聊的理一理。你说的 X, 拆开看是..."
   │
   ▼
PromptBuilder reads current_mode → splices mode segment into system prompt:
   - DAILY_TALK:    陪伴 / 共鸣 / 不急着分析
   - THOUGHT:       事实-感受-担心-需要-下一步 五栏
   - SOCRATIC:      一次只问一个关键假设
```

**Key**: mode switch does **not** mutate history. Original messages stay
intact. The "system turn" is the transition glue. LLM sees original
context + a guidance paragraph.

### 4.6 Capsule sync + PII filter

```
PortraitService.update(userId, newPortrait)
   │
   ▼
EventBus.publish("PortraitChanged", {userId, newPortrait})
   │
   ▼
CapsuleSyncService.onPortraitChanged(userId)
   │
   ├─ 1. Find all active capsules for this user
   │
   ├─ 2. For each capsule:
   │     a. PiiPrivacyFilter.transform(newPortrait, privacyPolicy)
   │        - real name → pseudonym (from capsule.pseudonym;
   │          user-edited version takes precedence)
   │        - precise location → city (上海徐汇 → 上海)
   │        - age → age range (28 → 25-30)
   │        - occupation → category (前端工程师 → 互联网/技术)
   │        - fields marked "sensitive" → skip
   │     b. Write to tb_capsule_sync_queue
   │     c. Async: CapsuleAgent.regenerateContext(capsuleId,
   │                                               filteredPortrait)
   │
   ├─ 3. Push to CapsuleReviewQueue
   │     User sees on next login: "2 个共鸣体建议用新画像更新, 是否允许?"
   │     [允许] [允许部分] [拒绝]
   │
   ▼
tb_capsule_sync_queue.status:
  PENDING → APPROVED/REJECTED → DONE
```

**User control**:
- Each portrait field has `privacy_level: PUBLIC | INNER | CONFIDENTIAL`
  - PUBLIC:    goes to capsule by default
  - INNER:     goes by default; user can switch off per field
  - CONFIDENTIAL: does not go by default; user must opt in

### 4.7 Mode-triggered proactive

When the user switches to SOCRATIC, Aurora immediately says a single
opening question — this is the **mode-switch proactive** owned by M5
(integrates with M2's delivery channel), not M2's random scheduler.

---

## 5. Data Model

### 5.1 Schema changes (additive or replace-in-place)

| Table | Operation | New fields / notes |
|---|---|---|
| `tb_user_profile` | modify | + `proactive_intensity ENUM('OFF','WHISPER','LIGHT','ACTIVE','COMPANION')` <br> + `sleep_window_start TIME`, `sleep_window_end TIME` <br> + `privacy_mode ENUM('STRICT','BALANCED','OPEN')` |
| `tb_user_portrait` | **new** | `(user_id, dim ENUM(6 维), value_json, score, confidence, updated_at)` <br> + `tb_user_portrait_history(同上 + ts)` — audit only, never overwritten |
| `tb_user_long_term_memory` | **new** | `(user_id, fact_type ENUM(10 类), fact_value, source_session_id, confidence, privacy_level, user_approved, created_at)` |
| `tb_session_summary` | **new** | `(user_id, session_id, summary_2_sentences, key_topics JSON, emotional_arc, started_at, closed_at)` |
| `tb_proactive_event_log` | **new** | `(user_id, event_type, trigger_meta, content, sent_at, user_responded_at, accepted)` |
| `tb_chat_session` | modify | + `current_mode`, `preferred_model`, `closed_at` |
| `tb_capsule_sync_queue` | **new** | `(user_id, capsule_id, status, proposed_context_diff, reviewed_at)` |
| `tb_weekly_review` | **rebuild** | Replace fields to match frontend. New shape: `title / date_range / top_themes / memory_count / dominant_emotion / daily_snapshots / recommendation / aurora_observation`. Old rows tagged `legacy=true` for archival only. |

### 5.2 The 6 portrait dimensions

```
ENUM tb_user_portrait.dim:
  INNER_DRIVE              -- 内在驱动: achievement / belonging / curiosity / security
  EMOTIONAL_VOCABULARY     -- 情绪词云 (top 20 words, weighted)
  ABSTRACT_VS_CONCRETE     -- 抽象 ↔ 具体 倾向 (-1..+1)
  COMMUNICATION_STYLE      -- 沟通风格: supportive / direct / socratic / analytical
  ENERGY_RHYTHM            -- 能量节律: 一天中高效时段
  VALUES                   -- 价值观: 关键词集合
```

### 5.3 Long-term memory fact types

```
ENUM tb_user_long_term_memory.fact_type:
  NAME / AGE / OCCUPATION / HOBBY / HABIT /
  RELATION / LOCATION / HEALTH / GOAL / PREFERENCE
```

---

## 6. Privacy Model (shared across all 8 milestones)

| Field class | Real-data store | Aurora prompt | EchoCapsule |
|---|---|---|---|
| Real name | ✓ | ✓ | ✗ → pseudonym |
| Pseudonym / nickname | ✓ | ✓ | ✓ (user-selected) |
| City | ✓ | ✓ | ✓ |
| Street / precise location | ✓ | ✗ | ✗ |
| Age number | ✓ | ✓ | ✗ → 25-30 range |
| Occupation name | ✓ | ✓ | ✗ → category |
| Emotion facts | ✓ | ✓ | ✓ (derived from portrait) |
| Personality dimensions | ✓ | ✓ | ✓ (user-approved) |
| Other private (user-marked) | ✓ | default ✗ | ✗ |

**PII filter is one-way**: real → public (downgrade), never reverse.

---

## 7. Failure Modes

| Failure | Symptom | Mitigation |
|---|---|---|
| All LLMs down | Aurora cannot generate | Step 1 of goodbye uses local template; light write-back deferred; Steps 2-6 of goodbye deferred and retried on recovery |
| Proactive push while user offline | Push has no listener | Buffer unsent pushes in `tb_proactive_event_log` (undelivered flag); frontend pulls on next login |
| Portrait LLM output illegal JSON | Write-back fails | Retry once + regex extract; if still fails, skip this round |
| User sends new message mid-goodbye | User changed mind | Abort remaining steps; put session back to active |
| Capsule sync hits LLM rate limit | Sync queue backs up | Rate-limit to 5/min; excess goes to back of queue |
| Weekly review schema migration | Old data | Tag old `tb_weekly_review` rows `legacy=true`; the rebuilt `tb_weekly_review` table starts empty and is populated by the new `WeeklyReviewV2Service` |

---

## 8. Testing Strategy

For each milestone, on landing:

1. **Unit** — pure logic in services
   (`QuietWindowResolver` 4-layer OR, `Portrait.applyDeltas`,
   `PiiPrivacyFilter`, intensity → budget table).
2. **Integration** — real LLM (minimax with real key) for 3 scenarios:
   - new user empty state
   - experienced user after goodbye
   - user rejecting capsule sync
3. **End-to-end** — Playwright drives the page:
   click goodbye → inspect DB → next Aurora entry shows the summary.

---

## 9. Open Items (to be resolved at first implementation kickoff)

- LLM temperature defaults per mode (DAILY_TALK warmer, THOUGHT cooler).
- Exact JSON schema for `value_json` of each of the 6 portrait dims.
- Frequency of light write-back (default: every 5 user turns, debounced).
- Frontend SSE endpoint: reuse existing SSE plumbing or new endpoint.
- Whether `current_mode` should be visible to the user as a status badge
  in the chat header (recommendation: yes).

---

## 10. Out of Scope (explicit)

- Voice input / output (ASR is already a separate module; no changes here).
- Multi-user / shared capsules.
- Migration of existing user data beyond the `legacy=true` tag.
- Admin-side debugging UI for the proactive engine (initial release is
  log + DB only).
