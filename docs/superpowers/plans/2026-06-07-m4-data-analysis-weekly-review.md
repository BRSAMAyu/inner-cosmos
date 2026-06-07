# M4: 数据分析 + 周报真实化 (Schema 修复 + 情绪模式真接) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the broken `tb_weekly_review` schema (currently returns wrong shape to frontend), wire real emotion pattern endpoints, and ensure weekly review content is genuinely aggregated from real data.

**Architecture:** New `WeeklyReviewV2Service` builds a VO matching the frontend's expected fields, querying real `tb_daily_record`, `tb_memory_card`, `tb_todo_item`, `tb_emotion_trace`. New `EmotionPatternService` computes real weekly/monthly aggregates. Old `tb_weekly_review` rows tagged `legacy=true`.

**Tech Stack:** Spring Boot, MyBatis-Plus aggregations, existing LLM for narrative generation.

**Depends on:** M1 (uses portrait for context), M3 (AnalysisPipelineService.processSessionClosure hook).

---

## File Structure

**New services:**
- `src/main/java/com/innercosmos/service/impl/WeeklyReviewV2Service.java`
- `src/main/java/com/innercosmos/service/EmotionPatternService.java`
- `src/main/java/com/innercosmos/vo/WeeklyReviewV2VO.java`
- `src/main/java/com/innercosmos/vo/WeeklyDailySnapshotVO.java`
- `src/main/java/com/innercosmos/vo/EmotionPatternVO.java`

**Modified controllers:**
- `src/main/java/com/innercosmos/controller/DailyRecordController.java` — change return type to WeeklyReviewV2VO
- `src/main/java/com/innercosmos/controller/EmotionTimelineController.java` — verify endpoints return real data

**Modified:**
- `src/main/resources/schema.sql` — alter `tb_weekly_review` to add new fields, mark old legacy
- `src/main/java/com/innercosmos/entity/WeeklyReview.java` — add new fields
- `src/main/java/com/innercosmos/ai/prompt/AuroraContentLibrary.java` — remove hardcoded mock `buildWeeklyReviewJson`

**Tests:**
- `src/test/java/com/innercosmos/service/WeeklyReviewV2ServiceTest.java` (uses H2 with seeded records)

---

## Tasks

### Task 1: Schema migration for tb_weekly_review

**Files:**
- Modify: `src/main/resources/schema.sql`

- [ ] **Step 1: Add new fields, leave old fields for legacy rows**

```sql
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS title VARCHAR(200);
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS date_range VARCHAR(64);
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS top_themes TEXT;
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS memory_count INT DEFAULT 0;
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS dominant_emotion VARCHAR(32);
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS daily_snapshots TEXT;  -- JSON array
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS recommendation TEXT;
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS aurora_observation TEXT;
ALTER TABLE tb_weekly_review ADD COLUMN IF NOT EXISTS legacy BOOLEAN DEFAULT FALSE;
```

(Note: H2 needs different syntax. Use a separate migration file or drop+recreate if not yet in production. Since this is dev/demo, `CREATE TABLE IF NOT EXISTS` pattern with new shape is acceptable.)

- [ ] **Step 2: Mark existing rows legacy**

```sql
UPDATE tb_weekly_review SET legacy = TRUE WHERE title IS NULL;
```

---

### Task 2: WeeklyReviewV2VO

**Files:**
- Create: `src/main/java/com/innercosmos/vo/WeeklyReviewV2VO.java`

- [ ] **Step 1: Define VO matching frontend expectations**

```java
@Data @AllArgsConstructor @NoArgsConstructor
public class WeeklyReviewV2VO {
  public String title;
  public String dateRange;
  public List<String> topThemes;
  public Integer memoryCount;
  public String dominantEmotion;
  public List<WeeklyDailySnapshotVO> dailySnapshots;
  public String recommendation;
  public String auroraObservation;
}
```

```java
@Data @AllArgsConstructor @NoArgsConstructor
public class WeeklyDailySnapshotVO {
  public String date;
  public String theme;
  public String summary;
  public String emotionWeather;  // CLEAR/CLOUDY/RAINY/STORM
}
```

---

### Task 3: WeeklyReviewV2Service — real aggregation

**Files:**
- Create: `src/main/java/com/innercosmos/service/impl/WeeklyReviewV2Service.java`

- [ ] **Step 1: Implement `latest(userId)`**

```java
@Service @RequiredArgsConstructor
public class WeeklyReviewV2Service {
  private final WeeklyReviewMapper weeklyMapper;
  private final DailyRecordMapper dailyMapper;
  private final MemoryCardMapper memoryMapper;
  private final TodoMapper todoMapper;
  private final LlmClient llm;
  private final ObjectMapper json = new ObjectMapper();

  public WeeklyReviewV2VO latest(Long userId) {
    var w = weeklyMapper.findLatestNonLegacy(userId);
    if (w == null) return null;
    return toVO(w);
  }

  @Transactional
  public WeeklyReviewV2VO generate(Long userId) {
    var weekStart = LocalDate.now().minusDays(7);
    var weekEnd = LocalDate.now();
    var dailyRecords = dailyMapper.findByUserAndDateRange(userId, weekStart, weekEnd);
    var memoryCount = memoryMapper.countByUserAndDateRange(userId, weekStart, weekEnd);
    var completedTodos = todoMapper.countCompletedByUserAndDateRange(userId, weekStart, weekEnd);
    var dominantEmotion = aggregateDominantEmotion(dailyRecords);
    var topThemes = aggregateTopThemes(memoryMapper.findByUserAndDateRange(userId, weekStart, weekEnd));
    var snapshots = dailyRecords.stream().map(d ->
        new WeeklyDailySnapshotVO(d.date.toString(), d.theme, d.summary, d.emotionWeather)).toList();

    String prompt = """
      你是 Aurora。基于以下真实数据, 写一份只属于这周的内在总结。严格 JSON:
      {"title":"...", "auroraObservation":"...", "recommendation":"..."}
      title 1 句, observation 2-3 句, recommendation 1 句。
      不要模板化, 不要"本周回顾"占位词。
      数据: 记忆 %d 条, 完成待办 %d 个, 主题 %s, 主导情绪 %s
      每日快照: %s
      """.formatted(memoryCount, completedTodos, topThemes, dominantEmotion, snapshots);
    String raw = llm.chat(new LlmRequest("WEEKLY_REVIEW", prompt, true)).content();
    var narrative = parseStrict(raw);

    var w = new WeeklyReview();
    w.userId = userId; w.weekStartDate = weekStart; w.weekEndDate = weekEnd;
    w.title = narrative.title(); w.auroraObservation = narrative.auroraObservation();
    w.recommendation = narrative.recommendation();
    w.dateRange = weekStart + " ~ " + weekEnd;
    w.topThemes = String.join(",", topThemes);
    w.memoryCount = memoryCount; w.dominantEmotion = dominantEmotion;
    w.dailySnapshots = json.writeValueAsString(snapshots);
    w.legacy = false;
    weeklyMapper.insert(w);
    return toVO(w);
  }

  private String aggregateDominantEmotion(List<DailyRecord> rs) {
    return rs.stream().collect(Collectors.groupingBy(r -> r.emotionWeather, Collectors.counting()))
        .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("CLEAR");
  }
  private List<String> aggregateTopThemes(List<MemoryCard> ms) {
    return ms.stream().flatMap(m -> Arrays.stream(m.tags == null ? new String[0] : m.tags.split(",")))
        .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
        .entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(5).map(Map.Entry::getKey).toList();
  }
}
```

---

### Task 4: Update DailyRecordController

- [ ] **Step 1: Change endpoint return type**

```java
@GetMapping("/weekly-review/latest")
public ApiResponse<WeeklyReviewV2VO> latest(HttpSession session) {
  return ApiResponse.ok(weeklyService.latest(currentUserId(session)));
}
@PostMapping("/weekly-review/generate")
public ApiResponse<WeeklyReviewV2VO> generate(HttpSession session) {
  return ApiResponse.ok(weeklyService.generate(currentUserId(session)));
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/innercosmos/service/impl/WeeklyReviewV2Service.java src/main/java/com/innercosmos/vo/WeeklyReviewV2VO.java src/main/java/com/innercosmos/vo/WeeklyDailySnapshotVO.java src/main/java/com/innercosmos/controller/DailyRecordController.java src/main/resources/schema.sql
git -c commit.gpgsign=false commit -m "feat(M4): WeeklyReviewV2 with real aggregation + frontend-matching VO"
```

---

### Task 5: EmotionPatternService — real pattern endpoints

**Files:**
- Create: `src/main/java/com/innercosmos/service/EmotionPatternService.java`
- Create: `src/main/java/com/innercosmos/vo/EmotionPatternVO.java`

- [ ] **Step 1: VO**

```java
@Data @AllArgsConstructor
public class EmotionPatternVO {
  public String period;            // "7d" | "30d"
  public List<EmotionPoint> timeline;  // [{date, weather, intensity}]
  public String stability;         // "stable" | "variable" | "volatile"
  public List<String> detectedPatterns;  // ["周一易低落", "工作日偏理性"]
  public Map<String, Integer> distribution;  // {CLEAR: 5, RAINY: 2, ...}
}
```

- [ ] **Step 2: Service**

```java
@Service @RequiredArgsConstructor
public class EmotionPatternService {
  private final EmotionTraceMapper emotionMapper;
  private final DailyRecordMapper dailyMapper;

  public EmotionPatternVO forUser(Long userId, String period) {
    int days = "30d".equals(period) ? 30 : 7;
    var since = LocalDate.now().minusDays(days);
    var traces = emotionMapper.findByUserSince(userId, since);
    // timeline
    var timeline = traces.stream().map(t -> new EmotionPoint(t.recordedAt.toLocalDate().toString(), t.weather, t.intensity)).toList();
    // distribution
    var dist = traces.stream().collect(Collectors.groupingBy(t -> t.weather, Collectors.counting()));
    // stability = stdev of intensity
    var stdev = computeStdev(traces.stream().map(t -> t.intensity).toList());
    var stability = stdev < 0.15 ? "stable" : stdev < 0.4 ? "variable" : "volatile";
    // patterns (simple heuristic, can be LLM later)
    var patterns = detectPatterns(traces);
    return new EmotionPatternVO(period, timeline, stability, patterns, dist);
  }
  private List<String> detectPatterns(List<EmotionTrace> traces) {
    // day-of-week pattern
    var byDow = traces.stream().collect(Collectors.groupingBy(
        t -> t.recordedAt.getDayOfWeek(), Collectors.averagingDouble(t -> t.intensity)));
    var lowest = byDow.entrySet().stream().min(Map.Entry.comparingByValue()).orElse(null);
    if (lowest != null) return List.of(lowest.getKey() + "情绪偏低");
    return List.of();
  }
}
```

- [ ] **Step 3: Verify EmotionTimelineController exposes this**

```java
@GetMapping("/patterns")
public ApiResponse<EmotionPatternVO> patterns(@RequestParam(defaultValue = "7d") String period, HttpSession session) {
  return ApiResponse.ok(patternService.forUser(currentUserId(session), period));
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/innercosmos/service/EmotionPatternService.java src/main/java/com/innercosmos/vo/EmotionPatternVO.java src/main/java/com/innercosmos/controller/EmotionTimelineController.java
git -c commit.gpgsign=false commit -m "feat(M4): EmotionPatternService with real timeline + stability + patterns"
```

---

### Task 6: Remove hardcoded mock

**Files:**
- Modify: `src/main/java/com/innercosmos/ai/prompt/AuroraContentLibrary.java`

- [ ] **Step 1: Delete or stub `buildWeeklyReviewJson`**

```java
// In AuroraContentLibrary.java, REPLACE the existing buildWeeklyReviewJson method body
// with a "no legacy content" stub so MockLlmClient still compiles:
public static String buildWeeklyReviewJson() {
  return "{\"legacy\": true, \"_note\": \"weekly review is now served by WeeklyReviewV2Service\"}";
}
```

If `buildWeeklyReviewJson` is called from anywhere outside `MockLlmClient`, add a `@Deprecated` annotation and a comment pointing to `WeeklyReviewV2Service` instead. The hardcoded narrative strings in the old method body are removed; only the empty-object shell remains.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/innercosmos/ai/prompt/AuroraContentLibrary.java
git -c commit.gpgsign=false commit -m "refactor(M4): remove hardcoded weekly review mock fallback"
```

---

## Acceptance Criteria

- `GET /api/daily-record/weekly-review/latest` returns shape with `title / dateRange / topThemes / memoryCount / dominantEmotion / dailySnapshots / recommendation / auroraObservation`
- `GET /api/emotion/timeline/patterns?period=7d` returns 200 with real data (not 404)
- Frontend `/pages/weekly-review.html` shows real numbers (not "本周回顾" placeholder)
- `emotion-timeline.html` displays actual data

## Dependencies

- M1 (portrait for narrative), M3 (AnalysisPipelineService hook)

## Risks

- H2 doesn't support `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` — handle via try/catch on startup
- LLM narrative quality varies — provide template fallback if LLM returns invalid JSON
- Performance: weekly review query scans 7 days of multiple tables — add index on `(user_id, date)` in each table if not present
