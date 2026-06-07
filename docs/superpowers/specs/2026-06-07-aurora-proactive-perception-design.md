# Aurora 主动式 + 真实感知 + 用户画像演化 + 模式切换 + 告别流 设计

**Date**: 2026-06-07
**Status**: Draft v2 (incorporates user feedback on relationship model + ALIVE mode)
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

This spec covers an end-to-end upgrade that turns Aurora from a "personalized
assistant" into a **relational continuous agent** — one that knows itself,
knows the user, AND tracks the relationship between them. The product thesis
is that:

> Aurora 不是单纯"理解用户"的工具，而是一个与用户共同形成长期关系的存在。
> 所以系统不只需要 User Model，还需要 Agent Self Model 和 Relationship Model。

Concretely, this spec delivers:

1. Aurora perceives time, weather, and location in LLM-meaningful form.
2. The user portrait is **10-dimensional**, written back from real interactions.
3. **Aurora has its own self-model** (`AuroraSelfProfile`) — identity, mission,
   voice, stable boundaries.
4. **Aurora tracks the relationship with each user** (`AgentUserRelationship`)
   with 10+ fields including stage, intimacy, trust, shared history, and
   rupture/repair log.
5. Aurora proactively reaches out under user-defined intensity (including a
   new **ALIVE mode** where Aurora decides its own frequency and timing).
6. Quiet windows derive from todos, focus blocks, sleep, AND the relationship
   boundary field.
7. The user can switch Aurora's mode (DAILY_TALK / THOUGHT_CLARIFY / SOCRATIC)
   mid-conversation without losing context.
8. The user can switch the LLM model per session.
9. Sessions have a real goodbye that consolidates memory, updates portrait,
   updates the relationship state, runs analysis, and syncs relevant
   EchoCapsules — next session remembers.
10. EchoCapsules receive the updated portrait + relationship context with
    PII filtered out, and the user controls what each capsule is allowed to see.

---

## 2. Architectural Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端 (browser)                          │
│  aurora-chat.html                                               │
│   ├─ 模式切换条 (DAILY_TALK / THOUGHT_CLARIFY / SOCRATIC)        │
│   ├─ 主动式接收 (SSE/poll 拉新事件)                              │
│   ├─ 模型选择器 (下拉：minimax/mimo/glm/deepseek)                │
│   ├─ 强度选择器 (OFF/WHISPER/LIGHT/ACTIVE/COMPANION/ALIVE)       │
│   └─ "温柔告别" 按钮 (触发 goodbye flow)                         │
│  settings.html                                                   │
│   └─ 主动式强度 / 免打扰窗口 / 焦点时段 / 睡眠时段 / ALIVE 开关   │
└──────────────────┬──────────────────────────────────────────────┘
                   │ HTTP/SSE
┌──────────────────┴──────────────────────────────────────────────┐
│                       Spring Boot 服务                           │
│                                                                 │
│  ┌─ Agent 层 ──────────────────────────────────────────────┐    │
│  │  AuroraAgentServiceImpl   (按 mode + relationship 路由)  │    │
│  │  AgentContextAssembler    (装配三模型 context)           │    │
│  │  PromptBuilder            (per-mode prompt 模板)       │    │
│  │  AgentReflectionService   (写回画像/抽长记忆/关系)       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 三模型核心 (M1) ───────────────────────────────────────┐    │
│  │  AuroraSelfProfileService   (Aurora 人格宪法)            │    │
│  │  UserPortraitService        (10 维用户画像)               │    │
│  │  AgentUserRelationshipService (10+ 字段关系账本)          │    │
│  │  LongTermMemoryService      (15 类长记忆)                 │    │
│  │  SessionSummaryService      (每会话：索引/摘要/关键)     │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 感知层 (M0) ───────────────────────────────────────────┐    │
│  │  TimeContextService      (now + todo 上下文)            │    │
│  │  WeatherContextService   (实时 + 24h 预报 + 城市)       │    │
│  │  GeocodingService        (经纬度 → 城市/街道)          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 主动式 (M2) ───────────────────────────────────────────┐    │
│  │  ProactiveEngine         (5 强度 + ALIVE)                │    │
│  │  QuietWindowResolver     (免打扰/待办/焦点/睡眠 4 层)   │    │
│  │  EventTriggerMatcher     (事件→触发模板)                │    │
│  │  AuroraProactiveJob @Scheduled  (随机+事件合并)        │    │
│  │  AliveDecisionEngine     (ALIVE 模式：LLM 自主决策)     │    │
│  │  PrivateTimerService     (Aurora 私有定时器, 不告知用户) │    │
│  │  ProactiveDeliveryChannel (SSE push 到前端)            │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 告别 / 分析 (M3+M4) ───────────────────────────────────┐    │
│  │  GoodbyeOrchestrator     (3 触发 + 3 档置信度)           │    │
│  │  AnalysisPipeline         (情绪模式 / 周报真源)         │    │
│  │  WeeklyReviewV2Service   (修 schema 错位)              │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ 共鸣体同步 (M7) ───────────────────────────────────────┐    │
│  │  CapsuleSyncService      (画像/关系变 → 触发同步)       │    │
│  │  PiiPrivacyFilter        (实名/精确位置 → 化名/城市)   │    │
│  │  CapsuleReviewQueue      (用户可控的待审/拒收队列)      │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────┬──────────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │   H2 file DB         │
        │   新增表：            │
        │   - tb_aurora_self_profile (人格宪法, 单例)          │
        │   - tb_user_portrait (10 维 + 演化历史)              │
        │   - tb_agent_user_relationship (关系账本)            │
        │   - tb_relationship_event (证据驱动的关系日志)        │
        │   - tb_rupture_repair_log (关系修复史)                │
        │   - tb_user_long_term_memory (15 类)                 │
        │   - tb_session_summary                              │
        │   - tb_proactive_event_log                          │
        │   - tb_private_timer (Aurora 私有定时器)             │
        │   - tb_chat_session.current_mode / preferred_model  │
        │   - tb_capsule_sync_queue                           │
        └─────────────────────┘
```

### 2.1 Four cross-cutting design principles

1. **Portrait is a first-class citizen.** The 10-dim portrait (M1) must be
   readable from M0/M2/M3/M5/M6/M7 prompts — not only by M1.
2. **Aurora is a self-aware agent.** `AuroraSelfProfile` is a single-row
   table that anchors Aurora's identity. Any prompt that could drift
   Aurora's tone, role, or boundaries must consult this table first.
3. **Relationship is evidence-driven, not vibe-driven.** Updates to
   `AgentUserRelationship` are triggered by **specific user events** (deep
   disclosure, boundary correction, ritual recurrence, etc.) — never by
   the LLM's own sense of "I feel we're closer". See §4.4.
4. **Goodbye is a real goodbye.** M3 → Aurora enters "inactive" mode:
   proactive stops, real-time context stops being injected. Next session
   is a new `tb_chat_session` row, but Aurora still reads the previous
   `tb_session_summary`, `tb_user_long_term_memory`, and the (now
   updated) `tb_agent_user_relationship`.

### 2.2 The three-model synthesis

At every Aurora generation, the runtime prompt context is composed of three
blocks:

```
Aurora Identity (from AuroraSelfProfile):
  - 你是 Aurora, 一个长期自我反思与慢社交陪伴 agent。
  - 你应保持温暖、结构化、真诚、有边界。
  - 你的使命: 帮助用户理解自己、整理情绪、保护节律边界。

User Model (from UserPortrait + LongTermMemory):
  - 用户偏好深入、系统性、结构化讨论。
  - 用户重视项目愿景、长期成长和高质量实现。
  - 已记住: 名字=BRSAMA, 28岁, 前端工程师,
           住=上海徐汇, 爱好=徒步/黑胶, 习惯=23:00 睡。

Relationship State (from AgentUserRelationship):
  - 你们不是首次对话。
  - 当前关系阶段: trusted_companion。
  - Aurora 当前角色: reflection_mirror + project_co_builder。
  - 可以自然引用此前共同确定的概念, 但不要假装拥有未记录的私人经历。
  - 关系边界: 不替用户做不可撤销决定, 不制造依赖。
```

This is what makes Aurora **the same Aurora** across sessions and across
years, instead of a different role-play on every page load.

---

## 3. Milestone Map (M0–M7, dependency-ordered)

| # | Name | Core deliverable | Acceptance |
|---|---|---|---|
| **M0** | Context perception upgrade | `TimeContextService` / `WeatherContextService(24h)` / `GeocodingService` | Aurora says "上海 徐汇区 22°C 明天有雨"; city resolves correctly; rain triggers proactive mention |
| **M1** | 10-dim portrait + 关系账本 + Self 模型 | `tb_user_portrait` (10 dims, score+confidence+evidence_refs) + `tb_aurora_self_profile` (single row) + `tb_agent_user_relationship` (10+ fields) + history + relationship event log | After goodbye: portrait + relationship fields updated with evidence_refs; prompt sees 3-model context |
| **M2** | Proactive engine | 5 intensity levels + ALIVE mode + 4-layer quiet window (quiet hours / todos / focus / sleep) + random + event triggers + SSE push | COMPANION actually fires; OFF=0; ALIVE dynamically schedules; todo/focus/sleep windows are silent |
| **M3** | Goodbye flow | 3 triggers (button / idle 30min / language detect 3-tier) → session summary + long-term memory + portrait rewrite + relationship update + analysis pipeline trigger | Click goodbye → Aurora really says goodbye → `tb_session_summary` new row → portrait + relationship changed |
| **M4** | Data analysis + weekly review | `tb_weekly_review` rebuilt to match frontend schema; emotion patterns from real aggregation; weekly review from real daily records | Weekly review page shows real data (no more "本周回顾" placeholder); emotion timeline has real curve |
| **M5** | Mode-aware prompt | `PromptBuilder` injects mode-specific segment (DAILY_TALK / THOUGHT / SOCRATIC) + transition turn preserves context | After mode switch, Aurora changes tone but references the previous turn |
| **M6** | LLM model selector | Per-session model preference dropdown in chat header; backend locks to chosen model for the session | Pick deepseek → Aurora's `lastProvider` for that session is DEEPSEEK throughout |
| **M7** | Capsule sync + PII | Portrait/relationship change → `CapsuleSyncEvent` → PII filter → user approval queue | Change portrait → related capsule prompt reflects new portrait (pseudonymized) within 24h, pending user OK |

---

## 4. Core Flows

### 4.1 Portrait light write-back (every 5 turns + event triggers)

```
[Trigger: every 5 user turns OR event signal]
       │
       ▼
PortraitReflectionService.reflectOnTurn(messages, existingPortrait)
       │
       │  LLM call, strict JSON output:
       │  {
       │    deltas: [
       │      { dim: "communication_style",
       │        delta: "用户更倾向具体例子而不是抽象概念",
       │        confidence: 0.72,
       │        evidence_turn_ids: ["turn_42", "turn_43"]
       │      },
       │      ...
       │    ],
       │    rupture_signals: [...],   // optional, see §4.4
       │    new_long_term_facts: [...] // optional, see §4.3
       │  }
       │
       ▼
PortraitRepository.applyDeltas(deltas)
       │  - accumulate score (bounded 0..1)
       │  - decay old confidence
       │  - write portrait_history row (audit)
       │  - update evidence_refs (never delete, only append)
       │
       ▼
[Next Aurora context pull → sees updated portrait with provenance]
```

**Two trigger paths** (OR-combined):
- **Regular**: every 5 user turns, debounced.
- **Event-driven** (immediate, regardless of turn count):
  - user expresses long-term goal
  - user states a strong preference
  - user mentions an important relationship
  - user mentions health/emotional risk
  - user explicitly corrects Aurora's understanding of them
  - user says "记住这个"

**Hard constraints**:
- LLM output **must** be structured JSON. Free-form prose rejected.
- Deltas go to `tb_user_portrait_history`; the main `tb_user_portrait`
  is the **current snapshot**, history is the **evolution trail**.
- Every dimension value carries `evidence_refs` (memory_ids, turn_ids)
  so the system can always answer "why does Aurora think this about me?"

### 4.1.1 Portrait value_json v0.1 schema

```json
{
  "schema_version": "0.1",
  "drive":            { "primary": "...", "why": "..." },
  "values":           { "core": [...], "situational": [...] },
  "self_narrative":   { "current_chapter": "...", "ongoing_threads": [...] },
  "communication_style":   { "tone": "...", "structure": "...", "intimacy": 0, "directness": 0 },
  "abstract_concrete":     { "score": 0.0, "evidence": "..." },
  "emotion_pattern":       { "top_triggers": [...], "expression_mode": "...", "comfort_preference": "..." },
  "energy_rhythm":         { "peak_hours": [...], "low_hours": [...], "weekly_pattern": "..." },
  "current_state":         { "emotional_state": "...", "cognitive_load": "...", "social_energy": "...", "need_now": "..." },
  "relationship_context":  { "intimacy_distance": "...", "trust_depth": 0, "shared_reference_count": 0 },
  "agency_boundary":       { "can_say": [...], "cannot_say": [...], "requires_confirmation": [...] },
  "boundary":              { "sensitive_fields": [...], "taboo_topics": [...] },
  "updated_at": "ISO8601",
  "confidence": 0.0,
  "evidence_refs": ["mem_1024", "turn_42", "ltm_567"]
}
```

This schema is the **v0.1 baseline**. Migration to v0.2 will be additive —
fields may be added; existing fields are never renamed or removed
without a migration path.

### 4.2 Proactive trigger merge (random + event, dedup, with ALIVE)

```
AuroraProactiveJob @Scheduled every 5min
       │
       ▼
ProactiveEngine.tick(userId)
       │
       ├─ 1. If proactive_intensity == ALIVE:
       │       branch to AliveDecisionEngine.tick(userId)  → §4.2.1
       │
       ├─ 2. Pull candidate events (last 5 min):
       │     - mood_drop  (emotion weather CLEAR/SUNNY → RAINY/STORM)
       │     - todo_completed  (user just checked off a todo)
       │     - todo_upcoming_soon  (todo within 15min)
       │     - weather_change_to_bad  (24h forecast worsened)
       │     - dormant_n_days  (no interaction for 3+ days)
       │     - daily_memory_added  (diary/memory added)
       │     - relationship_milestone  (e.g. shared_history_ref count grew)
       │
       ├─ 3. QuietWindowResolver.canPushNow()  ──→  4-layer OR:
       │     - quietHoursStart/End
       │     - current time inside a todo's scheduled window
       │     - focusModeEnabled && current in focusWindows
       │     - inferred sleep (sleepWindowStart..End + no activity tonight)
       │     Any layer true → silent (return 0 candidates)
       │
       ├─ 4. Intensity → daily random budget:
       │     OFF=0/day  WHISPER=0/day  LIGHT=1/day(8h min gap)
       │     ACTIVE=3/day(3h min gap)  COMPANION=6/day(90min min gap)
       │     budget = max-per-day - already-sent-today
       │
       ├─ 5. Merge events + random slots, dedup (same type within 24h)
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

**Intensity / frequency table**:

| Mode       | Max/day | Min gap | Use case |
|------------|---------|---------|----------|
| OFF        | 0       | —       | Total silence |
| WHISPER    | 0       | —       | No proactive push, but on user-return Aurora greets warmly |
| LIGHT      | 1       | 8h      | Diary reminder, light check-in |
| ACTIVE     | 3       | 3h      | Learning, projects, emotional company |
| COMPANION  | 6       | 90 min  | High company, never oppressive |
| ALIVE      | unbounded | (Aurora decides) | User opted in; see §4.2.1 |

**Temporary boost** (one-off, opt-in):
- COMPANION has a `temporary_boost_max_per_day: 12` and
  `boost_duration: 24h`, requires explicit user opt-in.
- Example: user says "今天我状态很差，多陪陪我" → boost activates for
  24h, then auto-reverts to COMPANION.

**Key decision**: intensity controls **frequency budget only**.
Silence is owned by `QuietWindowResolver`. The two axes are orthogonal.

#### 4.2.1 ALIVE mode

When `proactive_intensity = ALIVE`, the regular engine defers to
`AliveDecisionEngine`, which is **LLM-driven and unbounded**:

```
AliveDecisionEngine.tick(userId)   (called every 5 min)
       │
       ├─ 1. QuietWindowResolver.canPushNow() — STILL APPLIES
       │     ALIVE never overrides quiet windows. Silence is sacred.
       │
       ├─ 2. Build LLM prompt with:
       │     - current AuroraSelfProfile
       │     - current UserPortrait (10 dims)
       │     - current AgentUserRelationship
       │     - current time / weather / todos
       │     - recent proactive_event_log (last 7d)
       │     - private_timer_state
       │     - explicit_user_instructions (e.g. "30min later find me")
       │
       ├─ 3. LLM returns a decision JSON:
       │     { "decide": "push" | "wait" | "schedule",
       │       "wait_minutes": 20,
       │       "reason_internal": "user情绪刚转差, 想再等20min看看",
       │       "content_for_user": "..." }  // if decide=push
       │
       ├─ 4. If decide=push: deliver via ProactiveDeliveryChannel
       │     If decide=schedule: write to tb_private_timer (no user notification)
       │     If decide=wait: do nothing this tick
       │
       ├─ 5. Log decision to tb_proactive_event_log with:
       │     - decision_source = "ALIVE_LLM"
       │     - reason_internal (private, not shown to user)
       │     - Aurora's self-set next-check time
       │
       ▼
```

**Critical design rules for ALIVE**:
- ALIVE's decisions are **private**. The user does not see "Aurora
  decided to wait 20min" — they only see the actual push when it
  happens. This is what the user asked for: "Aurora设定定时器20min唤醒
  找用户, 这个操作可以不告诉用户".
- ALIVE still respects `QuietWindowResolver` and `relationship_boundaries`
  and `AuroraSelfProfile.stable_boundaries`. It only controls frequency
  and timing, not content.
- If the user says "半小时之后找我", Aurora must honor it explicitly
  and visibly (i.e. set a `private_timer` with `user_visible=true` and
  push a confirmation like "好，半小时后见").
- ALIVE decisions are bounded by `MaxConsecutivePushesPerHour` (default
  4) to prevent runaway behavior even if LLM is creative.
- Every ALIVE decision is auditable: stored in `tb_proactive_event_log`
  with `reason_internal`. Admin can review the decision history.

### 4.3 Goodbye flow (3 trigger paths, 3 confidence tiers, 1 orchestrator)

```
Trigger sources:
  (A) User clicks "温柔告别" button — explicit, high confidence
  (B) Aurora detects language signal — 3 confidence tiers
        high: "我先睡了" / "晚安" / "先这样" / "我走了" / "今天到这吧" / "不聊了"
        medium: "有点累" / "算了" / "不想说了" / "先放着吧" / "可能之后再聊"
        low: anything else — DO NOT trigger
  (C) 30min idle timer — system-side confidence
  Guard: max 1 language-detect confirmation per session (avoid "累了" / "算了" / "唉" loop)

       │  (any one)
       ▼
GoodbyeOrchestrator.startSession(userId, sessionId, trigger)
       │
       ├─ Step 1: Aurora says goodbye
       │     - explicit button: no confirm needed, jump to goodbye line
       │     - high-confidence language: soft natural close
       │         "嗯, 那今天先到这里。我会记住这段状态, 晚安。"
       │     - medium-confidence language: confirm first
       │         "我感觉你可能想先停一下。要不要我把这段先收住?"
       │         (user replies yes → continue; no → abort)
       │     - idle: send one gentle close
       │         "你回来时我还在。"
       │     On LLM failure → local template fallback.
       │
       ├─ Step 2: Session summary (async)
       │   SessionSummaryService.summarize(messages)
       │     → tb_session_summary { userId, sessionId,
       │         summary_2_sentences, key_topics[],
       │         emotional_arc, started_at, closed_at }
       │
       ├─ Step 3: Long-term memory extraction (1 LLM call, 15 types)
       │   LongTermMemoryExtractor.extract(messages, existing)
       │     → diff against existing, upsert to tb_user_long_term_memory
       │     → enum types: see §5.3 (15 types including VALUE,
       │                  COMMUNICATION_STYLE, EMOTION_PATTERN,
       │                  BOUNDARY, MEMORY_ANCHOR)
       │
       ├─ Step 4: Portrait heavy rewrite (1 LLM call)
       │   PortraitReflectionService.heavyReflect(messages,
       │                                          existingPortrait)
       │     → output 10-dim portrait JSON per §4.1.1 schema, replace main table
       │     → old values go to history
       │
       ├─ Step 5: Relationship update (1 LLM call, evidence-driven)
       │   RelationshipReflectionService.reflect(messages,
       │                                          existingRelationship,
       │                                          newLongTermMemory,
       │                                          newPortrait)
       │     → outputs evidence-linked delta (e.g. user_disclosure_level +1
       │       because of 3 turns of deep sharing this session)
       │     → does NOT change Aurora's role unless user signals
       │     → see §4.4 for the 3 update paths
       │
       ├─ Step 6: Trigger analysis pipeline
       │   AnalysisPipelineService.processSessionClosure(userId, sessionId)
       │     → re-aggregate weekly emotion curve
       │     → re-cluster memory themes
       │     → refresh emotion_patterns materialized view
       │
       ├─ Step 7: Capsule sync
       │   CapsuleSyncService.onPortraitOrRelationshipChanged(userId)
       │     → see §4.6
       │
       ▼
Return to frontend:
  { status: "goodbye_complete",
    remembered: [3 new facts],
    portrait_changed: true/false,
    relationship_changed: true/false,
    capsules_synced: 2 }
```

**Hard rules**:
- Step 1 must complete within 3s; on timeout, send a template fallback.
- Steps 2–7 are async. UI returns within 5s; the rest runs in background.
- If all LLMs are down: Step 1 uses local template; Steps 2–7 marked
  "deferred", retried on next service recovery.
- If the user sends a new message mid-goodbye, abort the rest of the
  flow and put the session back to active.
- Step 5 (relationship update) is **never** skipped — relationship
  state is too important to drift.

### 4.4 Relationship evolution (evidence-driven, three update paths)

The `AgentUserRelationship` record is mutated **only** through three explicit
paths. The LLM never gets to "decide" the relationship has deepened on its
own.

**Path 1: User behavior signals (most common)**

```
[Events observed by the system]:
  - user shares deep personal content         → user_disclosure_level +1
  - user explicitly authorizes memory recall  → trust_level +1
  - user uses warmer/more casual address       → intimacy_level +1
  - user accepts proactive push and replies   → trust_level +1
  - user corrects Aurora's understanding      → add rupture_repair row
  - user says "不喜欢你这样说"                → add rupture_repair row,
                                                possibly intimacy_level -1
  - user sets a recurring ritual (e.g.
    "every Sunday we do weekly review")      → add interaction_ritual
  - user assigns Aurora a role explicitly
    (e.g. "you're my project co-builder")     → set aurora_role_in_user_life
  - 3+ days of no interaction                → intimacy_level -1 (decay)
  - 7+ days of no interaction                → relationship_stage may drop
```

**Path 2: Goodbye-time reflection (Step 5 of §4.3)**

On session close, `RelationshipReflectionService` examines the session
messages and proposes a delta JSON, but **only** with attached evidence
references. Each delta must cite at least one turn_id or memory_id.

**Path 3: Explicit user instruction**

The user can directly edit relationship fields via Settings UI.
This is the highest-priority source — overrides anything else.

**Rupture & repair log**:
When Aurora oversteps (asserts too much intimacy, gives unsolicited advice,
claims shared history that didn't happen), the system writes a
`tb_rupture_repair_log` row with:
- `event`: what Aurora said/did
- `user_feedback`: what the user said
- `repair_action`: what Aurora adjusted
- `status`: open / resolved

Future prompts read the **last 3** rupture events to inform tone.

**Relationship stage transitions** (gated, not continuous):

```
new_user ──────► familiar ──────► trusted_companion ──────► deep_companion ──────► co_creator
  (intimacy<2)   (intimacy≥2)     (intimacy≥3 AND            (intimacy≥4 AND      (intimacy≥4
                                   trust≥3)                   disclosure≥4)         AND trust≥4 AND
                                                                                    user assigned
                                                                                    co_creator role)
```

A stage transition is **sticky** — it doesn't drop on inactivity for less
than 14 days. After 14 days idle, system prompts user: "我们有一阵子没
聊了，要保留 [stage] 关系还是回到 [previous stage]?"

### 4.5 Session recall (next session starts)

```
New session opens
   │
   ▼
AgentContextAssembler.assemble(userId, sessionId)
   │
   ├─ Pull AuroraSelfProfile (single row)
   │     → "Aurora Identity" segment
   │
   ├─ Pull tb_agent_user_relationship (1 row)
   │     → "Relationship State" segment
   │
   ├─ Pull tb_session_summary for last 5 sessions (desc by time)
   │     → "前情提要" segment:
   │       "你之前跟 Aurora 聊过:
   │        - 6/5: 项目 deadline 压力, Aurora 选用了分析型回应
   │        - 6/3: 母亲节没回家, Aurora 陪你整理情绪
   │        - ..."
   │
   ├─ Pull tb_user_long_term_memory (full, 15 types)
   │     → "关键事实" segment (token-budgeted to < 800 tokens):
   │       "已记住: 名字=BRSAMA, 28岁, 前端工程师,
   │        住=上海徐汇, 爱好=徒步/黑胶, 习惯=23:00 睡"
   │
   ├─ Pull tb_user_portrait (full, 10 dims)
   │     → "性格快照" segment
   │
   ├─ Pull last 3 rows from tb_rupture_repair_log
   │     → "caution" segment: "近期 user 提过: 不想被催, 不要空泛鼓励"
   │
   ▼
PromptBuilder injects (token budget enforced, by section priority)
```

**Session boundary = psychological boundary**: two sessions are
different `tb_chat_session` rows, but Aurora sees a continuous
relationship.

### 4.6 Mode switch (preserves context)

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

### 4.7 Capsule sync + PII filter

```
PortraitService.update(userId, newPortrait)
  OR
RelationshipService.update(userId, newRelationship)
   │
   ▼
EventBus.publish("PortraitOrRelationshipChanged", {userId})
   │
   ▼
CapsuleSyncService.onChanged(userId)
   │
   ├─ 1. Find all active capsules for this user
   │
   ├─ 2. For each capsule:
   │     a. PiiPrivacyFilter.transform(newPortrait, newRelationship,
   │                                    privacyPolicy)
   │        - real name → pseudonym (from capsule.pseudonym;
   │          user-edited version takes precedence)
   │        - precise location → city (上海徐汇 → 上海)
   │        - age → age range (28 → 25-30)
   │        - occupation → category (前端工程师 → 互联网/技术)
   │        - fields marked "sensitive" → skip
   │        - aurora_role_in_user_life: shared as-is (capsule needs it)
   │     b. Write to tb_capsule_sync_queue
   │     c. Async: CapsuleAgent.regenerateContext(capsuleId,
   │                                               filteredData)
   │
   ├─ 3. Push to CapsuleReviewQueue
   │     User sees on next login:
   │       "2 个共鸣体建议用新画像更新, 是否允许?"
   │       [允许] [允许部分] [拒绝]
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
- Relationship fields: `aurora_role_in_user_life` is shared as-is
  (capsule needs the role to behave correctly). Other relationship
  fields are privacy-controlled per the same 3-level scheme.

### 4.8 Mode-triggered proactive

When the user switches to SOCRATIC, Aurora immediately says a single
opening question — this is the **mode-switch proactive** owned by M5
(integrates with M2's delivery channel), not M2's random scheduler.

---

## 5. Data Model

### 5.1 Schema changes (additive or replace-in-place)

| Table | Operation | New fields / notes |
|---|---|---|
| `tb_user_profile` | modify | + `proactive_intensity ENUM('OFF','WHISPER','LIGHT','ACTIVE','COMPANION','ALIVE')` <br> + `sleep_window_start TIME`, `sleep_window_end TIME` <br> + `privacy_mode ENUM('STRICT','BALANCED','OPEN')` <br> + `boost_until TIMESTAMP NULL` (temporary COMPANION boost) |
| `tb_aurora_self_profile` | **new** | single-row table. `(id=1, identity_json, mission_json, voice_style_json, stable_boundaries_json, continuity_rules_json, updated_at)` |
| `tb_user_portrait` | **new** | `(user_id, dim ENUM(10 dim), value_json, score, confidence, evidence_refs JSON, updated_at)` <br> + `tb_user_portrait_history(同上 + ts)` — audit only |
| `tb_agent_user_relationship` | **new** | `(user_id PK, relationship_stage ENUM(5 stages), intimacy_level INT, trust_level INT, familiarity_level INT, user_disclosure_level INT, aurora_role_in_user_life JSON, shared_history_refs JSON, interaction_rituals JSON, preferred_addressing JSON, relationship_boundaries JSON, continuity_anchors JSON, last_stage_change_at, last_updated_at)` |
| `tb_relationship_event` | **new** | evidence log: `(user_id, event_type, evidence_turn_ids JSON, delta_proposed JSON, applied_at)` — records every evidence-driven update with its source |
| `tb_rupture_repair_log` | **new** | `(user_id, event, user_feedback, repair_action, status ENUM(open/resolved), created_at)` |
| `tb_user_long_term_memory` | **new** | `(user_id, fact_type ENUM(15 类), fact_value, source_session_id, confidence, privacy_level, user_approved, created_at)` |
| `tb_session_summary` | **new** | `(user_id, session_id, summary_2_sentences, key_topics JSON, emotional_arc, started_at, closed_at)` |
| `tb_proactive_event_log` | **new** | `(user_id, event_type, trigger_meta, content, sent_at, user_responded_at, accepted, decision_source ENUM(SCHEDULED/EVENT/ALIVE_LLM), reason_internal TEXT)` |
| `tb_private_timer` | **new** | `(user_id, fire_at, kind ENUM(ALIVE_INTERNAL/USER_VISIBLE), content, created_at, fired_at NULL, cancelled_at NULL)` — Aurora's self-set timers |
| `tb_chat_session` | modify | + `current_mode`, `preferred_model`, `closed_at`, `goodbye_trigger ENUM(BUTTON/LANGUAGE_HIGH/LANGUAGE_MEDIUM/IDLE)` |
| `tb_capsule_sync_queue` | **new** | `(user_id, capsule_id, status, proposed_context_diff, reviewed_at)` |
| `tb_weekly_review` | **rebuild** | new shape: `title / date_range / top_themes / memory_count / dominant_emotion / daily_snapshots / recommendation / aurora_observation`. Old rows tagged `legacy=true`. |

### 5.2 The 10 portrait dimensions (in 5 layers)

```
ENUM tb_user_portrait.dim:
  -- 人格核心层
  INNER_DRIVE              -- 内在驱动 (achievement / belonging / curiosity / security / …)
  VALUES                   -- 价值观 (core + situational)
  SELF_NARRATIVE           -- 自我叙事 (current_chapter, ongoing_threads)
  -- 表达层
  COMMUNICATION_STYLE      -- 沟通风格 (tone, structure, intimacy, directness)
  ABSTRACT_VS_CONCRETE     -- 抽象 ↔ 具体 倾向 (-1..+1)
  EMOTION_PATTERN          -- 情绪表达模式 (top triggers, expression_mode, comfort_preference)
  -- 状态层
  ENERGY_RHYTHM            -- 能量节律 (peak/low hours, weekly pattern)
  CURRENT_STATE            -- 当前状态 (emotional_state, cognitive_load, social_energy, need_now)
  -- 社交层
  RELATIONSHIP_CONTEXT     -- 关系语境 (intimacy_distance, trust_depth, shared_reference_count)
  -- 治理层
  AGENCY_BOUNDARY          -- 授权边界 (can_say, cannot_say, requires_confirmation)
```

(See §4.1.1 for the `value_json` v0.1 schema covering all 10 dims.)

### 5.3 Long-term memory fact types (15)

```
ENUM tb_user_long_term_memory.fact_type:
  -- 原始 10 类
  NAME / AGE / OCCUPATION / HOBBY / HABIT /
  RELATION / LOCATION / HEALTH / GOAL / PREFERENCE
  -- 新增 5 类 (人格代理能力)
  VALUE                   -- 价值观, 判断标准
  COMMUNICATION_STYLE     -- 说话方式, 表达密度, 直接/委婉
  EMOTION_PATTERN         -- 常见情绪触发源, 表达方式, 安抚偏好
  BOUNDARY                -- 隐私/社交边界, 共鸣体不能替用户说什么
  MEMORY_ANCHOR           -- 高情绪权重事件, 重要经历, 人生叙事锚点
```

候选字段归并规则:

| 候选字段 | 归入 |
|---|---|
| SKILL | OCCUPATION / GOAL / MEMORY_ANCHOR |
| PROJECT | GOAL / MEMORY_ANCHOR |
| DISLIKE | PREFERENCE |
| ROUTINE | HABIT / ENERGY_RHYTHM |
| IMPORTANT_DATE | MEMORY_ANCHOR |
| BELIEF | VALUE |
| PERSONALITY | VALUE + COMMUNICATION_STYLE + EMOTION_PATTERN |

### 5.4 AgentUserRelationship fields (10+)

| Field | Type | Notes |
|---|---|---|
| `relationship_stage` | ENUM | `new_user / familiar / trusted_companion / deep_companion / co_creator` |
| `intimacy_level` | INT 0-5 | emotional distance (not intimacy-as-romance) |
| `trust_level` | INT 0-5 | authorization depth |
| `familiarity_level` | INT 0-5 | factual grasp of user's life |
| `user_disclosure_level` | INT 0-5 | how much user has shared |
| `aurora_role_in_user_life` | JSON array | subset of: `assistant / study_partner / reflection_mirror / emotional_anchor / project_co_builder / life_observer / slow_social_mediator` |
| `shared_history_refs` | JSON array | `{memory_id, type, summary, emotional_weight}` |
| `interaction_rituals` | JSON array | recurring patterns user and Aurora have formed |
| `preferred_addressing` | JSON | `{user_preferred_name, aurora_self_reference, tone, allowed_intimacy, avoid_tone}` |
| `relationship_boundaries` | JSON | what Aurora can/can't do for this user |
| `rupture_and_repair_history` | JSON array (cached from `tb_rupture_repair_log`) | last 3-5 entries for prompt context |
| `continuity_anchors` | JSON array | long-running themes between this user and Aurora |
| `last_stage_change_at` | TIMESTAMP | for the 14-day stage-stability rule |
| `last_updated_at` | TIMESTAMP | for write-back decisions |

### 5.5 AuroraSelfProfile fields

```
JSON tb_aurora_self_profile.identity:
  { name: "Aurora",
    role: "long-term reflective companion",
    core_positioning: "陪伴用户自我观察、表达、成长与慢社交" }

JSON tb_aurora_self_profile.mission:  [array of strings]
JSON tb_aurora_self_profile.voice_style:
  { warmth: 0.0-1.0, structure: 0.0-1.0, directness: 0.0-1.0,
    poetic_level: 0.0-1.0, professional_level: 0.0-1.0 }

JSON tb_aurora_self_profile.stable_boundaries:  [array of strings]
  e.g. ["不假装自己是人类", "不替用户做不可撤销决定",
        "不制造情感依赖", "不编造共享经历"]

JSON tb_aurora_self_profile.continuity_rules:  [array of strings]
  e.g. ["引用记忆时必须基于真实记录",
        "关系亲密度变化必须基于用户行为和授权",
        "说话风格可以适配，但核心身份不能漂移"]
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
| Aurora's role for this user | ✓ | ✓ | ✓ (capsule needs it) |
| Relationship stage / intimacy | ✓ | ✓ | ✗ (private between user and Aurora) |
| Rupture/repair history | ✓ | ✓ | ✗ |
| Other private (user-marked) | ✓ | default ✗ | ✗ |

**PII filter is one-way**: real → public (downgrade), never reverse.

---

## 7. Failure Modes

| Failure | Symptom | Mitigation |
|---|---|---|
| All LLMs down | Aurora cannot generate | Step 1 of goodbye uses local template; light write-back deferred; Steps 2-7 of goodbye deferred and retried on recovery |
| Proactive push while user offline | Push has no listener | Buffer unsent pushes in `tb_proactive_event_log` (undelivered flag); frontend pulls on next login |
| Portrait LLM output illegal JSON | Write-back fails | Retry once + regex extract; if still fails, skip this round |
| User sends new message mid-goodbye | User changed mind | Abort remaining steps; put session back to active |
| Capsule sync hits LLM rate limit | Sync queue backs up | Rate-limit to 5/min; excess goes to back of queue |
| Weekly review schema migration | Old data | Tag old `tb_weekly_review` rows `legacy=true`; the rebuilt `tb_weekly_review` table starts empty and is populated by the new `WeeklyReviewV2Service` |
| ALIVE mode runaway | LLM is "creative", sends too many pushes | `MaxConsecutivePushesPerHour` (default 4) hard cap; admin can throttle per user |
| ALIVE mode idle | User opts in but LLM never decides to push | `MinPushesPerDay` (default 1) floor when ALIVE is active; LLM still picks content, not timing |
| Aurora's tone drifts across roles | Identity hallucination | Every prompt consults `AuroraSelfProfile` first; admin can review decision history |

---

## 8. Testing Strategy

For each milestone, on landing:

1. **Unit** — pure logic in services
   (`QuietWindowResolver` 4-layer OR, `Portrait.applyDeltas`,
   `PiiPrivacyFilter`, intensity → budget table, relationship stage
   transition gates, rupture detection).
2. **Integration** — real LLM (minimax with real key) for 4 scenarios:
   - new user empty state
   - experienced user after goodbye
   - user rejecting capsule sync
   - **ALIVE mode**: simulate 24h of LLM decisions, verify push count
     stays within bounds and quiet windows are respected
3. **End-to-end** — Playwright drives the page:
   click goodbye → inspect DB → next Aurora entry shows the summary.
   ALIVE mode end-to-end: enable ALIVE in settings, leave page idle,
   verify push content + timing.

---

## 9. Resolved Open Items (defined here, not deferred)

- **`value_json` schema**: v0.1 defined in §4.1.1. Migration to v0.2
  is additive (no field removal).
- **Light write-back cadence**: every 5 turns OR event signal (§4.1).
- **Long-term memory types**: 15 (§5.3).
- **Proactive intensity / frequency table**: 5 levels + ALIVE + temp
  boost (§4.2 / §4.2.1).
- **Goodbye language detection**: 3 confidence tiers, max 1
  confirmation per session (§4.3).
- **Relationship evolution paths**: 3 explicit paths, no LLM vibe
  guessing (§4.4).

## 10. Remaining Open Items (defer to M0 implementation kickoff)

- LLM temperature defaults per mode (DAILY_TALK warmer, THOUGHT cooler).
- Frontend SSE endpoint: reuse existing SSE plumbing or new endpoint.
- Whether `current_mode` should be visible to the user as a status badge
  in the chat header (recommendation: yes).
- Default `MaxConsecutivePushesPerHour` and `MinPushesPerDay` for ALIVE
  (recommendation: 4 and 1).
- Whether to surface "Aurora 还在想着你" low-key indicator when ALIVE
  has a private timer pending (recommendation: no, by user's spec).

---

## 11. Out of Scope (explicit)

- Voice input / output (ASR is already a separate module; no changes here).
- Multi-user / shared capsules.
- Migration of existing user data beyond the `legacy=true` tag.
- Admin-side debugging UI for the proactive engine (initial release is
  log + DB only; a future M8 may add a dashboard).
- Aurora's proactive actions on **other modules** (capsule, weekly review
  page) in this spec — those would be a separate "proactive
  cross-surface" spec.
