# M2: 主动式引擎 (5 强度 + ALIVE + Quiet Window + 事件触发) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real server-side proactive push engine with 5 intensity levels + ALIVE mode, 4-layer quiet window, event-driven triggers, and SSE delivery.

**Architecture:** A `@Scheduled` job runs every 5min per active user. `ProactiveEngine` merges random+event candidates, defers to `QuietWindowResolver`, then dispatches to LLM and SSE. ALIVE mode delegates to `AliveDecisionEngine` (LLM-driven, unbounded).

**Tech Stack:** Spring `@Scheduled`, SSE (Spring's `SseEmitter`), MyBatis-Plus, existing LLM clients.

**Depends on:** M0 (uses TimeContextService + WeatherContextService), M1 (uses UserPortrait + LongTermMemory for context in push content).

---

## File Structure

**New entities:**
- `src/main/java/com/innercosmos/entity/ProactiveEventLog.java`
- `src/main/java/com/innercosmos/entity/PrivateTimer.java`

**New mappers:** one per entity.

**New services:**
- `src/main/java/com/innercosmos/ai/proactive/ProactiveEngine.java`
- `src/main/java/com/innercosmos/ai/proactive/QuietWindowResolver.java`
- `src/main/java/com/innercosmos/ai/proactive/EventTriggerMatcher.java`
- `src/main/java/com/innercosmos/ai/proactive/AliveDecisionEngine.java`
- `src/main/java/com/innercosmos/ai/proactive/IntensityPolicy.java` (table lookup)
- `src/main/java/com/innercosmos/ai/proactive/ProactiveDeliveryChannel.java` (SSE)
- `src/main/java/com/innercosmos/ai/proactive/ProactiveContentGenerator.java` (LLM call for content)
- `src/main/java/com/innercosmos/ai/proactive/dto/ProactiveCandidate.java`
- `src/main/java/com/innercosmos/ai/proactive/dto/AliveDecision.java`

**New scheduler:**
- `src/main/java/com/innercosmos/scheduler/AuroraProactiveJob.java`

**New controller:**
- `src/main/java/com/innercosmos/controller/ProactiveSseController.java` (GET /api/proactive/stream)

**Modified:**
- `src/main/java/com/innercosmos/entity/UserProfile.java` — add `proactiveIntensity`, `sleepWindowStart`, `sleepWindowEnd`, `boostUntil`
- `src/main/resources/schema.sql` — add 2 new tables, ALTER tb_user_profile
- `src/main/java/com/innercosmos/controller/UserController.java` (settings endpoint) — accept new fields
- `src/main/resources/static/pages/settings.html` — UI for intensity, sleep window, boost toggle
- `src/main/resources/static/js/ic-proactive-client.js` (NEW) — SSE client
- `src/main/resources/static/pages/aurora-chat.html` — connect to SSE stream on page open

**Tests:**
- `src/test/java/com/innercosmos/ai/proactive/QuietWindowResolverTest.java` (pure logic)
- `src/test/java/com/innercosmos/ai/proactive/IntensityPolicyTest.java` (table lookup)

---

## Tasks

### Task 1: Schema additions

**Files:**
- Modify: `src/main/resources/schema.sql`

- [ ] **Step 1: ALTER tb_user_profile and add 2 new tables**

```sql
ALTER TABLE tb_user_profile ADD COLUMN IF NOT EXISTS proactive_intensity VARCHAR(16) DEFAULT 'LIGHT';
ALTER TABLE tb_user_profile ADD COLUMN IF NOT EXISTS sleep_window_start TIME DEFAULT '23:00:00';
ALTER TABLE tb_user_profile ADD COLUMN IF NOT EXISTS sleep_window_end TIME DEFAULT '07:00:00';
ALTER TABLE tb_user_profile ADD COLUMN IF NOT EXISTS boost_until TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS tb_proactive_event_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  trigger_meta TEXT,
  content TEXT NOT NULL,
  sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  user_responded_at TIMESTAMP NULL,
  accepted BOOLEAN,
  decision_source VARCHAR(16) DEFAULT 'SCHEDULED',
  reason_internal TEXT
);

CREATE TABLE IF NOT EXISTS tb_private_timer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  fire_at TIMESTAMP NOT NULL,
  kind VARCHAR(16) NOT NULL,  -- ALIVE_INTERNAL or USER_VISIBLE
  content TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  fired_at TIMESTAMP NULL,
  cancelled_at TIMESTAMP NULL
);
```

(Note: H2 does not support `ADD COLUMN IF NOT EXISTS`. Drop and re-add or use a separate `CREATE TABLE IF NOT EXISTS tb_user_profile_v2` migration. See spec §7 for failure-mode handling.)

---

### Task 2: IntensityPolicy — frequency table

**Files:**
- Create: `src/main/java/com/innercosmos/ai/proactive/IntensityPolicy.java`

- [ ] **Step 1: Implement table**

```java
@Component
public class IntensityPolicy {
  public record Policy(int maxPerDay, Duration minGap) {}
  private static final Map<String, Policy> TABLE = Map.of(
      "OFF",       new Policy(0, Duration.ZERO),
      "WHISPER",   new Policy(0, Duration.ZERO),
      "LIGHT",     new Policy(1, Duration.ofHours(8)),
      "ACTIVE",    new Policy(3, Duration.ofHours(3)),
      "COMPANION", new Policy(6, Duration.ofMinutes(90)),
      "ALIVE",     new Policy(Integer.MAX_VALUE, Duration.ofMinutes(15))
  );
  public Policy get(String intensity) { return TABLE.getOrDefault(intensity, TABLE.get("LIGHT")); }
  public boolean isAlive(String intensity) { return "ALIVE".equalsIgnoreCase(intensity); }
}
```

- [ ] **Step 2: Test**

```java
@Test void companionHasSixPerDay() {
  assertThat(new IntensityPolicy().get("COMPANION").maxPerDay()).isEqualTo(6);
}
```

---

### Task 3: QuietWindowResolver — 4-layer OR

**Files:**
- Create: `src/main/java/com/innercosmos/ai/proactive/QuietWindowResolver.java`
- Create: `src/test/java/com/innercosmos/ai/proactive/QuietWindowResolverTest.java`

- [ ] **Step 1: Implement**

```java
@Component
public class QuietWindowResolver {
  @Autowired UserProfileMapper profileMapper;
  @Autowired TodoMapper todoMapper;

  public record Reason(boolean quiet, String cause) {}

  public Reason canPushNow(Long userId, ZonedDateTime now) {
    var p = profileMapper.selectById(userId);
    if (p == null) return new Reason(false, "");
    var nowL = now.toLocalTime();
    // 1) quiet hours
    if (isInWindow(nowL, p.quietWindowStart, p.quietWindowEnd)) return new Reason(true, "quiet_hours");
    // 2) sleep window
    if (isInWindow(nowL, p.sleepWindowStart, p.sleepWindowEnd)) return new Reason(true, "sleep");
    // 3) todo time block
    var todos = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
        .eq(Todo::getUserId, userId).eq(Todo::getStatus, "PENDING"));
    for (var t : todos) {
      if (t.scheduledStart != null && t.scheduledEnd != null
          && !nowL.isBefore(t.scheduledStart.toLocalTime())
          && nowL.isBefore(t.scheduledEnd.toLocalTime())) {
        return new Reason(true, "todo:" + t.id);
      }
    }
    // 4) focus window
    if (Boolean.TRUE.equals(p.focusModeEnabled) && p.focusWindowsJson != null) {
      var windows = parseWindows(p.focusWindowsJson);
      for (var w : windows) {
        if (!nowL.isBefore(w.start) && nowL.isBefore(w.end)) return new Reason(true, "focus");
      }
    }
    return new Reason(false, "");
  }
  private boolean isInWindow(LocalDateTime t, LocalTime start, LocalTime end) {
    if (start == null || end == null) return false;
    if (start.isBefore(end)) return !t.isBefore(start) && t.isBefore(end);
    return !t.isBefore(start) || t.isBefore(end);  // wraps midnight
  }
  private boolean isInWindow(LocalTime t, LocalTime start, LocalTime end) { return isInWindow(t.atDate(LocalDate.now()), start, end); }
  private record Window(LocalTime start, LocalTime end) {}
  private List<Window> parseWindows(String json) { /* simple parse */ return List.of(); }
}
```

- [ ] **Step 2: Test all 4 layers**

```java
@Test void quietHoursSilences() { /* set 09:00-18:00 quiet, query at 12:00, expect quiet */ }
@Test void todoTimeBlockSilences() { /* insert todo 14:00-15:00, query at 14:30, expect quiet */ }
@Test void focusWindowSilences() { /* enable focus, set 10-12, query at 11:00, expect quiet */ }
@Test void sleepSilences() { /* set 23-07, query at 02:00, expect quiet */ }
@Test void openWindowAllows() { /* empty profile, query 12:00, expect !quiet */ }
```

---

### Task 4: EventTriggerMatcher — candidate events

- [ ] **Step 1: Implement (queries recent DB events)**

```java
@Component
public class EventTriggerMatcher {
  @Autowired EmotionTraceMapper emotionMapper;
  @Autowired TodoMapper todoMapper;
  @Autowired WeatherContextService weatherSvc;
  @Autowired MemoryCardMapper memoryMapper;
  @Autowired AiInteractionLogMapper aiLogMapper;

  public List<ProactiveCandidate> candidates(Long userId, Duration lookback) {
    Instant since = Instant.now().minus(lookback);
    var out = new ArrayList<ProactiveCandidate>();
    // 1) mood drop
    var recent = emotionMapper.selectList(...since...);
    if (moodDropped(recent)) out.add(new ProactiveCandidate("mood_drop", ...));
    // 2) todo completed
    var completed = todoMapper.selectList(completedSince(since));
    if (!completed.isEmpty()) out.add(new ProactiveCandidate("todo_completed", completed.get(0).title));
    // 3) todo upcoming (within 15 min)
    // 4) weather change
    // 5) dormant (last interaction > 3 days)
    // 6) new memory card
    return out;
  }
}
```

---

### Task 5: ProactiveDeliveryChannel — SSE

**Files:**
- Create: `src/main/java/com/innercosmos/ai/proactive/ProactiveDeliveryChannel.java`
- Create: `src/main/java/com/innercosmos/controller/ProactiveSseController.java`

- [ ] **Step 1: In-memory emitter registry**

```java
@Component
public class ProactiveDeliveryChannel {
  private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
  public SseEmitter subscribe(Long userId) {
    SseEmitter em = new SseEmitter(0L);  // no timeout
    emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(em);
    em.onCompletion(() -> emitters.get(userId).remove(em));
    em.onTimeout(() -> emitters.get(userId).remove(em));
    return em;
  }
  public void push(Long userId, String content, String type) {
    var set = emitters.get(userId);
    if (set == null || set.isEmpty()) {
      // offline: log to tb_proactive_event_log with sent_at=null
      return;
    }
    for (var em : set) {
      try { em.send(SseEmitter.event().name("proactive").data(Map.of("type", type, "content", content, "ts", Instant.now().toString()))); }
      catch (IOException e) { set.remove(em); }
    }
  }
}
```

- [ ] **Step 2: Controller**

```java
@GetMapping(value = "/api/proactive/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(HttpSession session) {
  Long userId = currentUserId(session);
  return deliveryChannel.subscribe(userId);
}
```

---

### Task 6: ProactiveEngine — the orchestrator

- [ ] **Step 1: Implement `tick(userId)`**

```java
public void tick(Long userId) {
  var profile = profileMapper.selectById(userId);
  if (profile == null) return;
  String intensity = profile.proactiveIntensity == null ? "LIGHT" : profile.proactiveIntensity;
  if ("OFF".equals(intensity) || "WHISPER".equals(intensity)) return;  // WHISPER greets on return, not push
  if (intensityPolicy.isAlive(intensity)) { aliveEngine.tick(userId); return; }
  // regular flow
  var quiet = quietResolver.canPushNow(userId, ZonedDateTime.now());
  if (quiet.quiet()) return;
  var events = eventMatcher.candidates(userId, Duration.ofMinutes(5));
  var policy = intensityPolicy.get(intensity);
  int sentToday = countSentToday(userId);
  int budget = policy.maxPerDay() - sentToday;
  if (budget <= 0 && events.isEmpty()) return;
  // dedup events by type, 24h
  events = events.stream().filter(e -> !sentWithin24h(userId, e.type())).limit(2).toList();
  if (!events.isEmpty()) budget = Math.max(budget, events.size());
  for (int i = 0; i < Math.min(budget, events.size() + 1); i++) {
    var cand = i < events.size() ? events.get(i) : randomSlot();
    var content = contentGenerator.generate(userId, cand);  // LLM call
    deliveryChannel.push(userId, content, cand.type());
    logEvent(userId, cand, content, "SCHEDULED", null);
  }
}
```

---

### Task 7: AuroraProactiveJob — scheduled runner

**Files:**
- Create: `src/main/java/com/innercosmos/scheduler/AuroraProactiveJob.java`

- [ ] **Step 1: Implement**

```java
@Component @RequiredArgsConstructor
public class AuroraProactiveJob {
  private final ProactiveEngine engine;
  private final UserMapper userMapper;
  private final PrivateTimerMapper privateTimerMapper;

  @Scheduled(fixedDelay = 5 * 60 * 1000)  // every 5 min
  public void run() {
    // 1) iterate active users
    var users = userMapper.selectActive();
    for (var u : users) engine.tick(u.id);
    // 2) fire any private timers that are due
    var due = privateTimerMapper.selectDue(Instant.now());
    for (var t : due) {
      deliveryChannel.push(t.userId, t.content, "alive_internal");
      privateTimerMapper.markFired(t.id);
    }
  }
}
```

(Enable scheduling: ensure main class has `@EnableScheduling`)

---

### Task 8: AliveDecisionEngine

- [ ] **Step 1: Implement**

```java
@Component
public class AliveDecisionEngine {
  @Autowired LlmClient llm;
  @Autowired QuietWindowResolver quiet;
  @Autowired PrivateTimerMapper timerMapper;
  @Autowired UserPortraitService portrait;
  @Autowired AgentUserRelationshipService rel;

  public void tick(Long userId) {
    var q = quiet.canPushNow(userId, ZonedDateTime.now());
    if (q.quiet()) return;
    String prompt = """
      你是 Aurora。用户当前开启了 ALIVE 模式。
      请决定这一刻是否要主动发起对话。
      只输出严格 JSON:
      {"decide":"push|wait|schedule", "wait_minutes":N, "content_for_user":"..."}
      wait_minutes ∈ [5, 1440]。push 时必须填 content_for_user。
      当前时间: %s
      用户画像: %s
      关系状态: %s
      最近 7d 主动式日志: %s
      """.formatted(Instant.now(), summary(portrait.getAll(userId)),
                    summary(rel.getOrInit(userId)), recentProactiveLog(userId));
    var raw = llm.chat(new LlmRequest("ALIVE_DECISION", prompt, true)).content();
    var decision = parse(raw);
    switch (decision.decide()) {
      case "push" -> deliveryChannel.push(userId, decision.content(), "alive_push");
      case "schedule" -> timerMapper.insert(new PrivateTimer(userId, Instant.now().plusSeconds(decision.waitMinutes()*60), "ALIVE_INTERNAL", null));
      case "wait" -> {} // do nothing this tick
    }
    logEvent(userId, "ALIVE_LLM", decision.content(), decision.reasonInternal());
  }
}
```

- [ ] **Step 2: Hard caps** (`MaxConsecutivePushesPerHour = 4`, `MinPushesPerDay = 1`)

```java
if (decision.decide().equals("push") && recentPushCountInHour(userId) >= 4) return;  // hard cap
if (decision.decide().equals("wait") && todayPushCount(userId) == 0
    && now.getHour() >= 19) { /* force a push to satisfy min */ }
```

---

### Task 9: Frontend — intensity selector + SSE client

**Files:**
- Modify: `src/main/resources/static/pages/settings.html`
- Create: `src/main/resources/static/js/ic-proactive-client.js`
- Modify: `src/main/resources/static/pages/aurora-chat.html` (load SSE client on open)

- [ ] **Step 1: Settings UI — intensity dropdown**

```html
<div class="panel">
  <h3>Aurora 主动式</h3>
  <label>强度 <select id="intensity">
    <option value="OFF">关闭</option>
    <option value="WHISPER">静默 (不主动, 回来时温柔接住)</option>
    <option value="LIGHT" selected>轻柔 (1/天)</option>
    <option value="ACTIVE">主动 (3/天)</option>
    <option value="COMPANION">陪伴 (6/天)</option>
    <option value="ALIVE">ALIVE (Aurora 自主)</option>
  </select></label>
  <label>睡眠窗口 <input type="time" id="sleepStart" value="23:00"> 至 <input type="time" id="sleepEnd" value="07:00"></label>
  <label><input type="checkbox" id="boost24h"> 开启 24h 加强陪伴 (12/天, 临时)</label>
  <button onclick="saveProactive()">保存</button>
</div>
```

- [ ] **Step 2: SSE client**

```js
// ic-proactive-client.js
window.ICProactive = {
  start(userId) {
    if (this.es) return;
    this.es = new EventSource(`/api/proactive/stream`);
    this.es.addEventListener("proactive", e => {
      const data = JSON.parse(e.data);
      IC.toast(data.content, "aurora", 8000);
      // also append a special "proactive" bubble to chat
      if (window.addAuroraProactive) addAuroraProactive(data);
    });
  }
};
```

- [ ] **Step 3: Load in aurora-chat.html**

```html
<script src="/js/ic-proactive-client.js"></script>
<script>ICProactive.start();</script>
```

- [ ] **Step 4: Commit + copy to target/classes + restart**

```bash
git add ...
git -c commit.gpgsign=false commit -m "feat(M2): 5 intensity + ALIVE proactive engine + 4-layer quiet window + SSE"
```

---

## Acceptance Criteria

- Set intensity=COMPANION, wait 5 min → push arrives via SSE in browser console
- Set intensity=OFF → 0 pushes over 30 min
- Set sleep=23-07, query at 02:00 → `quietResolver.canPushNow` returns quiet=true
- Set ALIVE → LLM-driven decision visible in `tb_proactive_event_log.reason_internal`
- Frontend toast appears with proactive content

## Dependencies

- M0 (TimeContextService, WeatherContextService, GeocodingService)
- M1 (UserPortrait, AuroraSelfProfile, AgentUserRelationship)

## Risks

- SSE connection leak if user closes tab → `SseEmitter(0L)` no timeout + `onCompletion` cleanup, but consider `AsyncContext` timeout
- ALIVE LLM may decide to push every tick → hard cap `MaxConsecutivePushesPerHour = 4`
- Quiet window resolver may incorrectly silence a critical event → add admin override endpoint (out of scope for this milestone)
