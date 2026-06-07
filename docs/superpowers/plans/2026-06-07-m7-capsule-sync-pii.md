# M7: 共鸣体同步 + PII 过滤 (画像/关系变 → 同步 + 用户审/拒) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the user's portrait or relationship state changes, sync relevant EchoCapsules with the filtered update, with user approval required before activation.

**Architecture:** `CapsuleSyncService` is invoked directly by M3 `SessionCloser.runAfterGoodbye` after the heavy portrait rewrite + relationship update complete. For each active capsule, it runs `PiiPrivacyFilter` on the new data, writes to `tb_capsule_sync_queue`, regenerates capsule context, then pushes to user review queue. User can allow / partial / reject.

**Tech Stack:** Spring `ApplicationEventPublisher`, MyBatis-Plus, existing LLM for capsule context regeneration.

**Depends on:** M1 (portrait + relationship), M3 (capsule sync called from SessionCloser).

---

## File Structure

**New services:**
- `src/main/java/com/innercosmos/ai/capsule/CapsuleSyncService.java`
- `src/main/java/com/innercosmos/ai/capsule/PiiPrivacyFilter.java`
- `src/main/java/com/innercosmos/ai/capsule/CapsuleContextRegenerator.java`
- `src/main/java/com/innercosmos/ai/capsule/dto/CapsuleContextDiff.java`

**New controller:**
- `src/main/java/com/innercosmos/controller/CapsuleSyncController.java` (`GET /api/capsule/sync/pending`, `POST /api/capsule/sync/{id}/decide`)

**Modified:**
- `src/main/java/com/innercosmos/service/impl/CapsuleServiceImpl.java` — read filtered portrait when generating persona_prompt
- `src/main/java/com/innercosmos/ai/agent/AuroraAgentServiceImpl.java` — publish `PortraitChanged` event after heavy rewrite (M1 already calls this implicitly via SessionCloser)
- `src/main/resources/schema.sql` — already in M1
- `src/main/resources/static/pages/echo-plaza.html` — show "2 个共鸣体待你审阅" banner

**Tests:**
- `src/test/java/com/innercosmos/ai/capsule/PiiPrivacyFilterTest.java`

---

## Tasks

### Task 1: PiiPrivacyFilter — one-way downgrade

**Files:**
- Create: `src/main/java/com/innercosmos/ai/capsule/PiiPrivacyFilter.java`
- Create: `src/test/java/com/innercosmos/ai/capsule/PiiPrivacyFilterTest.java`

- [ ] **Step 1: Implement**

```java
@Component
public class PiiPrivacyFilter {
  public record FilteredPortrait(
      String pseudonym, String city, String ageRange, String occupationCategory,
      List<String> values, Map<String, Object> agencyBoundary,
      List<String> auroraRoles,
      List<String> droppedFields  // for transparency
  ) {}

  public FilteredPortrait filter(UserPortraitService.PortraitSnapshot snapshot,
                                 LongTermMemory memory,
                                 AgentUserRelationship rel,
                                 Map<String, String> privacyOverrides /* per-field overrides */) {
    List<String> dropped = new ArrayList<>();
    String realName = memory.findByType("NAME").map(m -> m.factValue).orElse(null);
    String pseudonym = privacyOverrides.getOrDefault("pseudonym", derivePseudonym(realName));
    if (realName != null) dropped.add("real_name");

    String city = memory.findByType("LOCATION").map(m -> m.factValue).orElse("");
    if (city.contains("徐汇") || city.contains("路") || city.contains("号")) {
      city = city.split(" ")[0];  // keep city only
      dropped.add("precise_street");
    }
    String age = memory.findByType("AGE").map(m -> m.factValue).orElse("");
    String ageRange = toRange(age);
    if (!age.isBlank()) dropped.add("age_exact");
    String occ = memory.findByType("OCCUPATION").map(m -> m.factValue).orElse("");
    String occCat = toCategory(occ);
    if (!occ.isBlank()) dropped.add("occupation_exact");

    var values = snapshot.findDim("VALUES").map(p -> parseList(p.valueJson)).orElse(List.of());
    var agency = snapshot.findDim("AGENCY_BOUNDARY").map(p -> parseMap(p.valueJson)).orElse(Map.of());
    var roles = rel.auroraRoleInUserLife == null ? List.<String>of() : parseList(rel.auroraRoleInUserLife);

    return new FilteredPortrait(pseudonym, city, ageRange, occCat, values, agency, roles, dropped);
  }
  private String derivePseudonym(String realName) { return realName == null ? "TA" : realName.substring(0,1) + "同学"; }
  private String toRange(String age) { try { int a = Integer.parseInt(age); int lo = (a/5)*5; return lo + "-" + (lo+5); } catch (Exception e) { return ""; } }
  private String toCategory(String occ) {
    if (occ == null) return "";
    if (occ.contains("前端") || occ.contains("后端") || occ.contains("工程师") || occ.contains("程序员")) return "互联网/技术";
    if (occ.contains("设计")) return "设计";
    if (occ.contains("产品")) return "产品";
    if (occ.contains("教师") || occ.contains("老师")) return "教育";
    if (occ.contains("学生") || occ.contains("研究生")) return "学生";
    return "其他";
  }
  // ...parseList, parseMap helpers
}
```

- [ ] **Step 2: Tests**

```java
@Test void realNameIsReplacedWithPseudonym() {
  var p = filter.with("name", "林澈").build();
  assertThat(p.pseudonym()).isEqualTo("林同学");
  assertThat(p.droppedFields()).contains("real_name");
}
@Test void preciseAddressIsDowngradedToCity() {
  var p = filter.with("location", "上海徐汇区").build();
  assertThat(p.city()).isEqualTo("上海");
  assertThat(p.droppedFields()).contains("precise_street");
}
@Test void ageIsReplacedWithRange() {
  var p = filter.with("age", "28").build();
  assertThat(p.ageRange()).isEqualTo("25-30");
}
@Test void occupationIsDowngradedToCategory() {
  var p = filter.with("occupation", "前端工程师").build();
  assertThat(p.occupationCategory()).isEqualTo("互联网/技术");
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/innercosmos/ai/capsule/PiiPrivacyFilter.java src/test/java/com/innercosmos/ai/capsule/PiiPrivacyFilterTest.java
git -c commit.gpgsign=false commit -m "feat(M7): PiiPrivacyFilter — one-way downgrade of real data for capsules"
```

---

### Task 2: CapsuleContextRegenerator

- [ ] **Step 1: Implement**

```java
@Component
public class CapsuleContextRegenerator {
  @Autowired LlmClient llm;
  @Autowired EchoCapsuleMapper capsuleMapper;

  public void regenerate(Long capsuleId, PiiPrivacyFilter.FilteredPortrait portrait, List<String> recentThemes) {
    var capsule = capsuleMapper.selectById(capsuleId);
    String prompt = """
      这是用户的最新画像（已脱敏）:
      %s
      近期主题: %s
      请基于此重新生成这枚共鸣体的 persona_prompt（中文, 2-3 段, 保留原有 pseudonym 和 style）:
      原 persona_prompt: %s
      """.formatted(portrait, recentThemes, capsule.personaPrompt);
    String newPrompt = llm.chat(new LlmRequest("CAPSULE_REGEN", prompt, false)).content();
    capsule.personaPrompt = newPrompt;
    capsule.contextPreviewJson = "{\"portrait_version\":\"v0.1\", \"portrait\": " + JsonUtil.toJson(portrait) + "}";
    capsuleMapper.updateById(capsule);
  }
}
```

---

### Task 3: CapsuleSyncService

- [ ] **Step 1: Implement**

```java
@Service
public class CapsuleSyncService {
  @Autowired PiiPrivacyFilter filter;
  @Autowired CapsuleContextRegenerator regen;
  @Autowired EchoCapsuleMapper capsuleMapper;
  @Autowired CapsuleSyncQueueMapper queueMapper;
  @Autowired LongTermMemoryService ltm;
  @Autowired UserPortraitService portrait;
  @Autowired AgentUserRelationshipService rel;
  @Autowired RateLimiter limiter = RateLimiter.create(5.0 / 60.0);  // 5/min

  @Transactional
  public void onPortraitOrRelationshipChanged(Long userId) {
    var pSnap = portrait.getSnapshot(userId);
    var mems = ltm.getAll(userId);
    var r = rel.getOrInit(userId);
    var filtered = filter.filter(pSnap, mems, r, Map.of());
    var recent = mems.stream().map(m -> m.factType + ":" + m.factValue).limit(10).toList();
    for (var cap : capsuleMapper.findByOwner(userId)) {
      if (!limiter.tryAcquire()) { /* skip, will retry next minute */ continue; }
      var q = new CapsuleSyncQueue();
      q.userId = userId; q.capsuleId = cap.id; q.status = "PENDING";
      q.proposedContextDiff = JsonUtil.toJson(Map.of(
          "filtered_portrait", filtered,
          "recent_themes", recent
      ));
      queueMapper.insert(q);
      // async regenerate
      CompletableFuture.runAsync(() -> regen.regenerate(cap.id, filtered, recent));
    }
  }

  public List<CapsuleSyncQueue> pending(Long userId) {
    return queueMapper.findByUserAndStatus(userId, "PENDING");
  }
  @Transactional
  public void decide(Long userId, Long queueId, String decision, List<String> allowedFields) {
    var q = queueMapper.selectById(queueId);
    if (q == null || !q.userId.equals(userId)) throw new ApiException(404, "");
    switch (decision) {
      case "ALLOW"     -> { q.status = "APPROVED"; queueMapper.updateById(q); /* persona already regenerated */ }
      case "ALLOW_PARTIAL" -> { /* re-run regenerate with only allowedFields */ q.status = "APPROVED"; queueMapper.updateById(q); }
      case "REJECT"    -> {
        q.status = "REJECTED";
        queueMapper.updateById(q);
        // revert capsule.personaPrompt to pre-sync state (keep snapshot in queue.proposedContextDiff)
      }
    }
  }
}
```

---

### Task 4: CapsuleSyncController + frontend banner

- [ ] **Step 1: Controller**

```java
@RestController
@RequestMapping("/api/capsule/sync")
public class CapsuleSyncController extends BaseController {
  @GetMapping("/pending") public ApiResponse<List<CapsuleSyncQueue>> pending(HttpSession s) {
    return ApiResponse.ok(svc.pending(currentUserId(s)));
  }
  @PostMapping("/{id}/decide")
  public ApiResponse<Boolean> decide(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession s) {
    svc.decide(currentUserId(s), id, (String) body.get("decision"),
               (List<String>) body.getOrDefault("allowedFields", List.of()));
    return ApiResponse.ok(true);
  }
}
```

- [ ] **Step 2: Frontend — banner on echo-plaza.html**

```html
<div id="syncBanner" class="panel" style="display:none; background: rgba(241,230,227,0.7)">
  <h3>2 个共鸣体建议用新画像更新</h3>
  <p>你最近跟 Aurora 聊的内容，可能让你的共鸣体更懂你。</p>
  <button onclick="showSyncReview()">审阅</button>
</div>
<script>
const pending = (await API.capsuleSyncPending()).data;
if (pending.length) document.getElementById("syncBanner").style.display = "block";
</script>
```

- [ ] **Step 3: Commit + copy + restart**

```bash
git add ...
git -c commit.gpgsign=false commit -m "feat(M7): capsule sync with PII filter + user approval queue"
```

---

## Acceptance Criteria

- After M3 goodbye, `tb_capsule_sync_queue` has new PENDING rows
- `/api/capsule/sync/pending` returns them
- `POST /api/capsule/sync/{id}/decide` with `decision=ALLOW` updates capsule.personaPrompt
- `decision=REJECT` reverts (need to snapshot pre-sync state, save in `proposed_context_diff`)
- `tb_echo_capsule.context_preview_json` shows filtered portrait (no real name, no street)

## Dependencies

- M1 (portrait + relationship), M3 (called from SessionCloser)

## Risks

- LLM rate limit during bulk sync → Guava `RateLimiter` at 5/min
- User rejects → must rollback capsule.personaPrompt. Save pre-sync personaPrompt in `tb_capsule_sync_queue.proposed_context_diff` for recovery
- Privacy override per field is complex UI → defer to "ALLOW_PARTIAL" future enhancement
