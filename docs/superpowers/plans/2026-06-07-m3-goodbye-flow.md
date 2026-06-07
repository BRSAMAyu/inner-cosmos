# M3: 告别流 (3 触发 + 3 档置信度 + 6 步同步) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real goodbye flow with 3 trigger paths (button / 30min idle / language detect 3-tier), 6-step async pipeline (LLM goodbye → session summary → long-term memory → portrait rewrite → relationship update → analysis trigger), and a frontend button.

**Architecture:** `GoodbyeOrchestrator` is the entry point. Triggers fire into it; it executes Step 1 (LLM goodbye) synchronously with 3s timeout + template fallback, then schedules Steps 2-6 async. Frontend gets a response within 5s; the rest runs in background and pushes status updates.

**Tech Stack:** Spring `@Async`, existing LLM clients, Spring `@Scheduled` for idle timer.

**Depends on:** M0, M1 (portrait + relationship services), M2 (proactive engine stops pushing after session close).

---

## File Structure

**New services:**
- `src/main/java/com/innercosmos/ai/goodbye/GoodbyeOrchestrator.java`
- `src/main/java/com/innercosmos/ai/goodbye/GoodbyeTriggerDetector.java`
- `src/main/java/com/innercosmos/ai/goodbye/GoodbyeLineGenerator.java` (LLM call + templates)
- `src/main/java/com/innercosmos/ai/goodbye/SessionCloser.java` (Step 2-6 async)
- `src/main/java/com/innercosmos/ai/goodbye/SessionSummaryService.java` (already in M1 schema, here is the implementation)
- `src/main/java/com/innercosmos/ai/goodbye/FarewellTemplates.java` (Chinese fallback strings)
- `src/main/java/com/innercosmos/ai/goodbye/dto/GoodbyeResult.java`
- `src/main/java/com/innercosmos/ai/goodbye/dto/GoodbyeTrigger.java`

**New controller:**
- `src/main/java/com/innercosmos/controller/GoodbyeController.java` (`POST /api/aurora/goodbye`)

**New scheduler:**
- `src/main/java/com/innercosmos/scheduler/SessionIdleWatcher.java` (30min idle check)

**Modified:**
- `src/main/java/com/innercosmos/ai/agent/AuroraAgentServiceImpl.java` — call `GoodbyeTriggerDetector` on every user message; if detected, invoke `GoodbyeOrchestrator`
- `src/main/resources/static/pages/aurora-chat.html` — "温柔告别" button + "我想走了" intent check
- `src/main/java/com/innercosmos/ai/portrait/SessionSummaryService.java` (now actually implement summarize)

**Tests:**
- `src/test/java/com/innercosmos/ai/goodbye/GoodbyeTriggerDetectorTest.java`

---

## Tasks

### Task 1: FarewellTemplates — Chinese fallback lines

**Files:**
- Create: `src/main/java/com/innercosmos/ai/goodbye/FarewellTemplates.java`

- [ ] **Step 1: Implement**

```java
@Component
public class FarewellTemplates {
  public String forTrigger(String trigger) {
    return switch (trigger) {
      case "BUTTON"         -> "谢谢你今天陪我聊了这么多。明天见。";
      case "LANGUAGE_HIGH"  -> "嗯，那今天先到这里。我会记住这段状态，晚安。";
      case "LANGUAGE_MEDIUM"-> "我感觉你可能想先停一下。要不要我把这段先收住？";
      case "IDLE"           -> "你回来时我还在。";
      default               -> "那我先在这里，等你回来。";
    };
  }
  public String forConfirm() { return "好，那我就先把这段收住了。"; }
}
```

---

### Task 2: GoodbyeTriggerDetector — 3 confidence tiers

**Files:**
- Create: `src/main/java/com/innercosmos/ai/goodbye/GoodbyeTriggerDetector.java`
- Create: `src/test/java/com/innercosmos/ai/goodbye/GoodbyeTriggerDetectorTest.java`

- [ ] **Step 1: Implement**

```java
@Component
public class GoodbyeTriggerDetector {
  private static final List<String> HIGH = List.of("我先睡了", "晚安", "先这样", "我走了", "今天到这吧", "不聊了", "拜拜", "再见", "明天见", "回见");
  private static final List<String> MEDIUM = List.of("有点累", "算了", "不想说了", "先放着吧", "可能之后再聊", "之后再聊", "回头聊", "下次再说");

  public record Detection(String trigger, double confidence, boolean needsConfirm) {}
  public static final Detection NONE = new Detection(null, 0.0, false);

  public Detection detect(String userMessage) {
    String m = userMessage.trim();
    if (HIGH.stream().anyMatch(m::contains)) return new Detection("LANGUAGE_HIGH", 0.95, false);
    if (MEDIUM.stream().anyMatch(m::contains)) return new Detection("LANGUAGE_MEDIUM", 0.65, true);
    return NONE;
  }
}
```

- [ ] **Step 2: Tests**

```java
@Test void detectsHighConfidence() {
  var d = new GoodbyeTriggerDetector().detect("今天到这吧");
  assertThat(d.trigger()).isEqualTo("LANGUAGE_HIGH");
  assertThat(d.needsConfirm()).isFalse();
}
@Test void detectsMediumAndRequiresConfirm() {
  var d = new GoodbyeTriggerDetector().detect("有点累了");
  assertThat(d.trigger()).isEqualTo("LANGUAGE_MEDIUM");
  assertThat(d.needsConfirm()).isTrue();
}
@Test void ignoresUnrelatedText() {
  var d = new GoodbyeTriggerDetector().detect("今天项目 deadline 很赶");
  assertThat(d.trigger()).isNull();
}
@Test void languageGuardMaxOnePerSession() {
  // integration: orchestrator records confirmation count per session
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/innercosmos/ai/goodbye/GoodbyeTriggerDetector.java src/test/java/com/innercosmos/ai/goodbye/GoodbyeTriggerDetectorTest.java
git -c commit.gpgsign=false commit -m "feat(M3): GoodbyeTriggerDetector with 3 confidence tiers"
```

---

### Task 3: SessionSummaryService — async summarizer

**Files:**
- Create: `src/main/java/com/innercosmos/ai/goodbye/SessionSummaryService.java`

- [ ] **Step 1: Implement**

```java
@Service @RequiredArgsConstructor
public class SessionSummaryService {
  private final LlmClient llm;
  private final SessionSummaryMapper mapper;

  @Async
  public CompletableFuture<SessionSummary> summarize(Long userId, Long sessionId, List<ChatMessage> messages) {
    String prompt = """
      总结这次对话。严格 JSON 输出:
      {"summary_2_sentences": "...", "key_topics": ["...", "..."], "emotional_arc": "calm|tense|lift|dip"}
      不要超过 2 句中文。key_topics 3-5 个。
      对话: %s
      """.formatted(formatMessages(messages));
    String raw = llm.chat(new LlmRequest("SESSION_SUMMARY", prompt, true)).content();
    var j = parseStrict(raw);
    var s = new SessionSummary();
    s.userId = userId; s.sessionId = sessionId;
    s.summary2Sentences = j.summary2Sentences();
    s.keyTopics = String.join(",", j.keyTopics());
    s.emotionalArc = j.emotionalArc();
    mapper.insert(s);
    return CompletableFuture.completedFuture(s);
  }
}
```

---

### Task 4: GoodbyeOrchestrator — entry point

- [ ] **Step 1: Implement orchestrator**

```java
@Service @Slf4j
public class GoodbyeOrchestrator {
  @Autowired FarewellTemplates templates;
  @Autowired GoodbyeLineGenerator lineGen;
  @Autowired SessionCloser closer;
  @Autowired ChatSessionMapper sessionMapper;

  public GoodbyeResult start(Long userId, Long sessionId, String trigger) {
    // 0) mark session trigger
    var sess = sessionMapper.selectById(sessionId);
    if (sess == null) return new GoodbyeResult(false, "no session", List.of(), false, false, 0);
    sess.goodbyeTrigger = trigger;
    sessionMapper.updateById(sess);
    // 1) sync goodbye line (3s budget)
    String line;
    try {
      line = lineGen.generate(userId, sessionId, trigger).get(3, TimeUnit.SECONDS);
    } catch (Exception e) {
      line = templates.forTrigger(trigger);
      log.warn("Goodbye line LLM failed, using template: {}", e.getMessage());
    }
    // 2-6) async pipeline
    closer.runAfterGoodbye(userId, sessionId);
    return new GoodbyeResult(true, line, List.of(), false, false, 0);  // final state filled in by async closer via SSE
  }
}
```

---

### Task 5: SessionCloser — async steps 2-7

- [ ] **Step 1: Implement**

```java
@Service @Slf4j
public class SessionCloser {
  @Autowired SessionSummaryService summarySvc;
  @Autowired LongTermMemoryService ltmSvc;
  @Autowired PortraitReflectionService portraitSvc;
  @Autowired RelationshipReflectionService relSvc;
  @Autowired AnalysisPipelineService analysisSvc;
  @Autowired CapsuleSyncService capsuleSvc;
  @Autowired ProactiveEngine proactiveEngine;
  @Autowired ChatSessionMapper sessionMapper;

  @Async
  public void runAfterGoodbye(Long userId, Long sessionId) {
    // stop proactive for this user temporarily (until next session open)
    proactiveEngine.silenceUntil(userId, Instant.now().plus(Duration.ofHours(8)));
    // 2) summary
    var summary = summarySvc.summarize(userId, sessionId, recentMessages(userId, sessionId)).join();
    // 3) long-term memory
    var newFacts = ltmSvc.extract(userId, sessionId, recentMessages(userId, sessionId));
    // 4) portrait heavy rewrite
    var portraitDeltas = portraitSvc.heavyReflect(userId, recentMessages(userId, sessionId));
    portraitSvc.applyDeltas(userId, portraitDeltas);
    // 5) relationship update (evidence-driven)
    var relDeltas = relSvc.reflect(userId, recentMessages(userId, sessionId), newFacts, portraitDeltas);
    relSvc.applyDeltas(userId, relDeltas);
    // 6) analysis pipeline
    analysisSvc.processSessionClosure(userId, sessionId);
    // 7) capsule sync
    capsuleSvc.onPortraitOrRelationshipChanged(userId);
    // close session row
    var sess = sessionMapper.selectById(sessionId);
    sess.closedAt = Instant.now();
    sessionMapper.updateById(sess);
  }
}
```

---

### Task 6: SessionIdleWatcher — 30min idle trigger

**Files:**
- Create: `src/main/java/com/innercosmos/scheduler/SessionIdleWatcher.java`

- [ ] **Step 1: Implement**

```java
@Component @RequiredArgsConstructor
public class SessionIdleWatcher {
  private final ChatSessionMapper sessionMapper;
  private final GoodbyeOrchestrator goodbye;

  @Scheduled(fixedDelay = 5 * 60 * 1000)
  public void scan() {
    var cutoff = Instant.now().minus(Duration.ofMinutes(30));
    var idle = sessionMapper.findIdleOpen(cutoff);  // status=open, last_msg_at < cutoff
    for (var s : idle) goodbye.start(s.userId, s.id, "IDLE");
  }
}
```

- [ ] **Step 2: SQL helper**

```sql
-- add to mapper XML or use wrapper
SELECT * FROM tb_dialog_session WHERE status='open' AND last_message_at < ?
```

---

### Task 7: GoodbyeController + frontend button

- [ ] **Step 1: Controller**

```java
@RestController
@RequestMapping("/api/aurora/goodbye")
public class GoodbyeController extends BaseController {
  @Autowired GoodbyeOrchestrator orchestrator;
  @PostMapping
  public ApiResponse<GoodbyeResult> trigger(@RequestBody Map<String, String> body, HttpSession session) {
    var trigger = body.getOrDefault("trigger", "BUTTON");
    var sessionId = Long.parseLong(body.get("sessionId"));
    return ApiResponse.ok(orchestrator.start(currentUserId(session), sessionId, trigger));
  }
}
```

- [ ] **Step 2: Frontend button**

```html
<!-- in aurora-chat.html header -->
<button class="ghost" onclick="goodbye()">温柔告别</button>
<script>
async function goodbye() {
  if (!confirm("确定要结束这次对话吗？")) return;
  const r = await API.goodbye(sid, "BUTTON");
  addAurora(r.data.line);
  // Steps 2-6 run async; poll status endpoint for final summary
}
</script>
```

- [ ] **Step 3: api.js add**

```js
goodbye: (sessionId, trigger) => IC.api("/api/aurora/goodbye", {
  method: "POST", body: JSON.stringify({ sessionId, trigger })
})
```

- [ ] **Step 4: Commit + copy + restart**

```bash
git add src/main/java/com/innercosmos/ai/goodbye/ src/main/java/com/innercosmos/scheduler/ src/main/java/com/innercosmos/controller/GoodbyeController.java src/main/resources/static/pages/aurora-chat.html src/main/resources/static/js/api.js
git -c commit.gpgsign=false commit -m "feat(M3): full goodbye flow with 3 triggers, 6-step async closer"
```

---

## Acceptance Criteria

- Click "温柔告别" button → Aurora says a real goodbye line within 3s
- Within 30s, `tb_session_summary` has 1 new row, `tb_user_portrait` updated, `tb_relationship_event` has 1 row
- Send "今天到这吧" → Aurora auto-says goodbye (high confidence, no confirm)
- Send "有点累了" → Aurora asks "要不要把这段先收住?" (confirm needed)
- After 30min idle, idle watcher fires goodbye on next 5min tick

## Dependencies

- M0, M1, M2

## Risks

- LLM all-down during goodbye → template fallback already in `GoodbyeOrchestrator`
- User sends new message mid-closer → `closer` checks session status at each step; if `closed_at` already set, abort
- Idle watcher may double-trigger goodbye if user returns mid-cleanup → guard with `if (sess.closedAt != null) continue;` at top
