# M1: 10 维用户画像 + Aurora 自我模型 + 关系账本 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the three-model relational architecture: 10-dim user portrait (with write-back), Aurora's self-profile (single-row), and the relationship ledger (10+ fields, evidence-driven).

**Architecture:** Three new entities + three services. PortraitReflectionService is LLM-driven and runs on trigger (5 turns OR event). RelationshipReflectionService is also LLM-driven but gated by evidence. AuroraSelfProfile is a single row seeded on startup.

**Tech Stack:** Spring Boot 3.3.6, MyBatis-Plus, LLM clients (minimax primary).

**Depends on:** M0 (uses TimeContextService for `last_interaction_at` derivation).

---

## File Structure

**New entities:**
- `src/main/java/com/innercosmos/entity/UserPortrait.java` (10-dim rows)
- `src/main/java/com/innercosmos/entity/UserPortraitHistory.java`
- `src/main/java/com/innercosmos/entity/AuroraSelfProfile.java` (single row, id=1)
- `src/main/java/com/innercosmos/entity/AgentUserRelationship.java` (1 row per user)
- `src/main/java/com/innercosmos/entity/RelationshipEvent.java`
- `src/main/java/com/innercosmos/entity/RuptureRepairLog.java`
- `src/main/java/com/innercosmos/entity/UserLongTermMemory.java` (15 types)
- `src/main/java/com/innercosmos/entity/SessionSummary.java`

**New mappers:** one per entity above.

**New services:**
- `src/main/java/com/innercosmos/ai/portrait/UserPortraitService.java`
- `src/main/java/com/innercosmos/ai/portrait/PortraitReflectionService.java`
- `src/main/java/com/innercosmos/ai/portrait/AuroraSelfProfileService.java`
- `src/main/java/com/innercosmos/ai/portrait/AgentUserRelationshipService.java`
- `src/main/java/com/innercosmos/ai/portrait/RelationshipReflectionService.java`
- `src/main/java/com/innercosmos/ai/portrait/LongTermMemoryService.java`
- `src/main/java/com/innercosmos/ai/portrait/SessionSummaryService.java`
- `src/main/java/com/innercosmos/ai/portrait/dto/PortraitDeltas.java` (LLM output schema)
- `src/main/java/com/innercosmos/ai/portrait/dto/RelationshipDeltas.java`

**New controller:**
- `src/main/java/com/innercosmos/controller/PortraitController.java` (GET /api/portrait, GET /api/portrait/history)
- `src/main/java/com/innercosmos/controller/RelationshipController.java` (GET/PATCH /api/relationship)
- `src/main/java/com/innercosmos/controller/LongTermMemoryController.java` (GET/PATCH /api/long-term-memory)

**Modified:**
- `src/main/resources/schema.sql` — add 8 new tables
- `src/main/java/com/innercosmos/config/MockDataInitializer.java` — seed `tb_aurora_self_profile` with default Aurora identity
- `src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java` — inject 3 new services; assemble the 3-model prompt
- `src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java` — call `PortraitReflectionService.reflectOnTurn` after every 5 turns or on event

**Tests:**
- `src/test/java/com/innercosmos/ai/portrait/PortraitReflectionServiceTest.java`
- `src/test/java/com/innercosmos/ai/portrait/RelationshipStageTransitionTest.java`

---

## Tasks

### Task 1: Schema additions

**Files:**
- Modify: `src/main/resources/schema.sql` (append all 8 tables)

- [ ] **Step 1: Add the 8 tables at the bottom of schema.sql**

```sql
-- 10-dim user portrait (current snapshot)
CREATE TABLE IF NOT EXISTS tb_user_portrait (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  dim VARCHAR(64) NOT NULL,
  value_json TEXT NOT NULL,
  score DOUBLE DEFAULT 0.5,
  confidence DOUBLE DEFAULT 0.0,
  evidence_refs TEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_dim (user_id, dim)
);
CREATE TABLE IF NOT EXISTS tb_user_portrait_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  dim VARCHAR(64) NOT NULL,
  value_json TEXT,
  score DOUBLE,
  confidence DOUBLE,
  evidence_refs TEXT,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Aurora self (single row)
CREATE TABLE IF NOT EXISTS tb_aurora_self_profile (
  id INT PRIMARY KEY,
  identity_json TEXT NOT NULL,
  mission_json TEXT,
  voice_style_json TEXT,
  stable_boundaries_json TEXT,
  continuity_rules_json TEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Agent <-> user relationship (1 row per user)
CREATE TABLE IF NOT EXISTS tb_agent_user_relationship (
  user_id BIGINT PRIMARY KEY,
  relationship_stage VARCHAR(32) DEFAULT 'new_user',
  intimacy_level INT DEFAULT 0,
  trust_level INT DEFAULT 0,
  familiarity_level INT DEFAULT 0,
  user_disclosure_level INT DEFAULT 0,
  aurora_role_in_user_life TEXT,
  shared_history_refs TEXT,
  interaction_rituals TEXT,
  preferred_addressing TEXT,
  relationship_boundaries TEXT,
  continuity_anchors TEXT,
  last_stage_change_at TIMESTAMP NULL,
  last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Evidence-driven relationship events
CREATE TABLE IF NOT EXISTS tb_relationship_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  evidence_turn_ids TEXT,
  delta_proposed TEXT,
  applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Rupture & repair log
CREATE TABLE IF NOT EXISTS tb_rupture_repair_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event TEXT,
  user_feedback TEXT,
  repair_action TEXT,
  status VARCHAR(16) DEFAULT 'open',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 15-type long-term memory
CREATE TABLE IF NOT EXISTS tb_user_long_term_memory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  fact_value TEXT NOT NULL,
  source_session_id BIGINT,
  confidence DOUBLE DEFAULT 0.7,
  privacy_level VARCHAR(16) DEFAULT 'INNER',
  user_approved BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Session summary (one per closed session)
CREATE TABLE IF NOT EXISTS tb_session_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  summary_2_sentences TEXT,
  key_topics TEXT,
  emotional_arc VARCHAR(32),
  started_at TIMESTAMP,
  closed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: Restart server, verify tables created**

```bash
# H2 picks up new schema on next start. Restart via mvn spring-boot:run
```

---

### Task 2: Entity classes (skeleton)

- [ ] **Step 1: Create `UserPortrait.java`**

```java
package com.innercosmos.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data @TableName("tb_user_portrait")
public class UserPortrait {
  @TableId(type = IdType.AUTO) public Long id;
  public Long userId;
  public String dim;
  public String valueJson;
  public Double score;
  public Double confidence;
  public String evidenceRefs;
}
```

- [ ] **Step 2: Create `AuroraSelfProfile.java`, `AgentUserRelationship.java`, etc.** (mirror same pattern for the 6 new entities)

- [ ] **Step 3: Create 6 mappers** (one-liner MyBatis-Plus mapper interfaces)

```java
package com.innercosmos.mapper;
public interface UserPortraitMapper extends BaseMapper<UserPortrait> {}
```

(do the same for each entity)

---

### Task 3: Seed AuroraSelfProfile on startup

**Files:**
- Modify: `src/main/java/com/innercosmos/config/MockDataInitializer.java`

- [ ] **Step 1: Add seeding method**

```java
@PostConstruct
public void ensureAuroraSelfProfile() {
  AuroraSelfProfile p = new AuroraSelfProfile();
  p.id = 1;
  p.identityJson = """
    {"name":"Aurora","role":"long-term reflective companion",
     "core_positioning":"陪伴用户自我观察、表达、成长与慢社交"}""";
  p.missionJson = """
    ["帮助用户理解自己","帮助用户整理情绪与长期目标",
     "在慢社交中提供温柔的表达缓冲","保护用户的节律、边界与隐私"]""";
  p.voiceStyleJson = """
    {"warmth":0.8,"structure":0.9,"directness":0.7,
     "poetic_level":0.4,"professional_level":0.7}""";
  p.stableBoundariesJson = """
    ["不假装自己是人类","不替用户做不可撤销决定",
     "不制造情感依赖","不编造共享经历","不越权读取或表达用户隐私"]""";
  p.continuityRulesJson = """
    ["引用记忆时必须基于真实记录",
     "关系亲密度变化必须基于用户行为和授权",
     "说话风格可以适配，但核心身份不能漂移"]""";
  auroraSelfProfileMapper.insertOrUpdate(p);  // ensure id=1 row exists
}
```

- [ ] **Step 2: Add unit test that self profile is non-null after startup**

---

### Task 4: UserPortraitService — read/write 10 dims

- [ ] **Step 1: Implement `UserPortraitService`**

```java
@Service
public class UserPortraitService {
  @Autowired UserPortraitMapper mapper;
  public List<UserPortrait> getAll(Long userId) {
    return mapper.selectList(new LambdaQueryWrapper<UserPortrait>().eq(UserPortrait::getUserId, userId));
  }
  public UserPortrait get(Long userId, String dim) {
    return mapper.selectOne(new LambdaQueryWrapper<UserPortrait>()
        .eq(UserPortrait::getUserId, userId).eq(UserPortrait::getDim, dim));
  }
  @Transactional
  public void applyDeltas(Long userId, List<PortraitDeltas.Delta> deltas) {
    for (var d : deltas) {
      // 1) write history
      var existing = get(userId, d.dim);
      if (existing != null) {
        var hist = new UserPortraitHistory();
        hist.userId = userId; hist.dim = d.dim; hist.valueJson = existing.valueJson;
        hist.score = existing.score; hist.confidence = existing.confidence; hist.evidenceRefs = existing.evidenceRefs;
        historyMapper.insert(hist);
      }
      // 2) update or insert current
      var row = existing != null ? existing : new UserPortrait();
      row.userId = userId; row.dim = d.dim; row.valueJson = d.valueJson;
      row.score = (existing == null ? 0.5 : existing.score * 0.7 + d.confidence * 0.3);
      row.confidence = d.confidence;
      row.evidenceRefs = mergeEvidence(existing == null ? null : existing.evidenceRefs, d.evidenceTurnIds);
      if (existing == null) mapper.insert(row); else mapper.updateById(row);
    }
  }
  private String mergeEvidence(String old, List<String> additions) {
    if (old == null) old = "[]";
    try { var arr = new ObjectMapper().readValue(old, List.class);
          for (var a : additions) if (!arr.contains(a)) arr.add(a);
          return new ObjectMapper().writeValueAsString(arr); }
    catch (Exception e) { return old; }
  }
}
```

---

### Task 5: PortraitReflectionService — LLM call returning strict JSON

- [ ] **Step 1: Define `PortraitDeltas` record**

```java
public record PortraitDeltas(List<Delta> deltas, List<RuptureSignal> ruptures, List<NewFact> newFacts) {
  public record Delta(String dim, String valueJson, double confidence, List<String> evidenceTurnIds) {}
  public record RuptureSignal(String event, String userFeedback) {}
  public record NewFact(String factType, String factValue, double confidence) {}
}
```

- [ ] **Step 2: Implement `PortraitReflectionService.reflectOnTurn`**

```java
@Service
public class PortraitReflectionService {
  @Autowired LlmClient llm; @Autowired UserPortraitService portraitSvc;

  public PortraitDeltas reflectOnTurn(Long userId, List<ChatMessage> recent) {
    String existingJson = objectMapper.writeValueAsString(portraitSvc.getAll(userId));
    String prompt = """
      你是一个用户画像分析师。下面是用户的最近 %d 轮对话和当前 10 维画像。
      请输出严格 JSON（不要任何解释文字），格式：
      {"deltas": [...], "ruptures": [...], "new_facts": [...]}
      每一项 delta 必须含 dim ∈ 这 10 个之一：
      INNER_DRIVE / VALUES / SELF_NARRATIVE / COMMUNICATION_STYLE /
      ABSTRACT_VS_CONCRETE / EMOTION_PATTERN / ENERGY_RHYTHM /
      CURRENT_STATE / RELATIONSHIP_CONTEXT / AGENCY_BOUNDARY
      confidence 必须在 0..1，evidence_turn_ids 至少 1 个。
      只有确实有变化才输出 delta。

      当前画像: %s
      对话: %s
      """.formatted(recent.size(), existingJson, formatMessages(recent));

    String raw = llm.chat(new LlmRequest("PORTRAIT_REFLECTION", prompt, true)).content();
    return parseStrict(raw);   // regex rescue if JSON parse fails
  }
}
```

---

### Task 6: AuroraAgentServiceImpl hook — every 5 turns + event triggers

- [ ] **Step 1: Add counter in `AuroraAgentServiceImpl`**

```java
private final Map<Long, Integer> turnCounter = new ConcurrentHashMap<>();
private static final int REFLECT_EVERY = 5;
private static final List<String> REFLECT_EVENT_KEYWORDS =
    List.of("记住这个", "长期目标", "我打算", "我喜欢", "我不喜欢", "我讨厌", "健康", "重要的人");

@After public void maybeReflect(Long userId, String userMessage) {
  int n = turnCounter.merge(userId, 1, Integer::sum);
  boolean event = REFLECT_EVENT_KEYWORDS.stream().anyMatch(userMessage::contains);
  if (n % REFLECT_EVERY == 0 || event) {
    turnCounter.put(userId, 0);
    portraitReflection.reflectOnTurn(userId, recentMessages(userId, 20));
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/innercosmos/ai/portrait/ src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java src/main/resources/schema.sql
git -c commit.gpgsign=false commit -m "feat(M1): 10-dim portrait + AuroraSelfProfile + write-back on 5 turns / events"
```

---

### Task 7: AgentUserRelationshipService + RelationshipReflectionService

- [ ] **Step 1: Implement basic CRUD + ensure-row-on-first-chat**

```java
@Service
public class AgentUserRelationshipService {
  @Transactional
  public AgentUserRelationship getOrInit(Long userId) {
    var r = mapper.selectById(userId);
    if (r == null) { r = new AgentUserRelationship(); r.userId = userId; r.relationshipStage = "new_user";
      r.auroraRoleInUserLife = "[\"assistant\"]"; mapper.insert(r); }
    return r;
  }
  public void applyDeltas(Long userId, List<RelationshipDeltas.Delta> deltas) { /* merge into row */ }
}
```

- [ ] **Step 2: `RelationshipReflectionService` (similar to portrait)**

LLM call: given messages + new portrait + new long-term memory, output deltas with evidence. Each delta must include `evidence_turn_ids` ≥ 1.

- [ ] **Step 3: Stage transition gate** (see §4.4 of spec)

```java
public boolean canTransition(AgentUserRelationship r, String newStage) {
  return switch (newStage) {
    case "familiar"        -> r.intimacyLevel >= 2;
    case "trusted_companion" -> r.intimacyLevel >= 3 && r.trustLevel >= 3;
    case "deep_companion"    -> r.intimacyLevel >= 4 && r.userDisclosureLevel >= 4;
    case "co_creator"        -> r.intimacyLevel >= 4 && r.trustLevel >= 4
                                  && r.auroraRoleInUserLife.contains("project_co_builder");
    default -> false;
  };
}
```

---

### Task 8: LongTermMemoryService (15 types)

- [ ] **Step 1: Implement `extract()` LLM call**

Given a session's messages, output `{fact_type, fact_value, confidence}` for each new fact. Diff against existing rows (same fact_type + similar fact_value), upsert.

- [ ] **Step 2: 15 fact types enum**

In entity: `String factType;` validated against 15 allowed values via `@Pattern` or service-level check.

---

### Task 9: AgentContextAssembler — assemble 3-model prompt

- [ ] **Step 1: Modify `assemble()` to include the 3 blocks**

```java
String auroraIdentity = auroraSelfProfileService.get().identityJson;
String relationshipState = relationshipSvc.getOrInit(userId).toPromptString();
String portraitSnapshot = portraitSvc.getAll(userId).stream()
    .map(p -> p.dim + ":" + p.valueJson).collect(joining("\n"));
String longTermMemory = memorySvc.getAll(userId).stream()
    .limit(15).map(m -> m.factType + "=" + m.factValue).collect(joining("; "));
String ruptureCaution = ruptureRepo.findTop3ByUserId(userId).stream()
    .map(r -> "近期 user 反馈: " + r.userFeedback).collect(joining("; "));

ctx.systemPrompt = String.join("\n\n",
    "【Aurora Identity】\n" + auroraIdentity,
    "【Relationship State】\n" + relationshipState,
    "【User Portrait】\n" + portraitSnapshot,
    "【Long-Term Memory】\n" + longTermMemory,
    "【Caution】\n" + ruptureCaution
);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java
git -c commit.gpgsign=false commit -m "feat(M1): 3-model prompt assembly (Aurora + User + Relationship)"
```

---

### Task 10: Frontend — show relationship stage + portrait summary on settings

**Files:**
- Modify: `src/main/resources/static/pages/settings.html` — new section "Aurora 与你的关系"
- Modify: `src/main/resources/static/js/api.js` — `API.relationship()`, `API.portrait()`

- [ ] **Step 1: Add a "关系阶段" card to settings page**

```html
<div class="panel">
  <h3>我们之间</h3>
  <p>当前关系阶段: <span id="relStage">—</span></p>
  <p>亲密: <span id="relIntimacy">0</span>/5 · 信任: <span id="relTrust">0</span>/5</p>
  <p>我在你生活里的角色: <span id="relRole">—</span></p>
  <a class="button" href="/pages/portrait-detail.html">查看完整画像</a>
</div>
```

- [ ] **Step 2: Commit**

---

## Acceptance Criteria

- After 5 user turns, `tb_user_portrait` has at least 1 row with non-zero confidence
- On session close, `tb_relationship_event` has at least 1 row with evidence_turn_ids
- Aurora prompt in DB logs (`tb_ai_interaction_log`) shows "【Aurora Identity】..." block
- `/api/portrait` returns 10 dims
- `/api/relationship` returns stage + 5 levels

## Dependencies

- M0 (uses TimeContextService for last_interaction)

## Risks

- LLM may produce invalid JSON → fallback parser with regex extraction
- 5-turn counter resets on restart → use `tb_dialog_session.turn_count` (or, if not present, derive from `tb_dialog_message` count) instead, see step 6
- Portrait value_json schema migration: v0.1 defined in spec §4.1.1, do not add new required fields without migration
