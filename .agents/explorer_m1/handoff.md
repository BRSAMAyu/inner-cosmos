# Architectural & Specification Alignment Audit Report (Milestone M1 / R1)

**Working Directory**: `d:\code\inner cosmos\.agents\explorer_m1`  
**Auditor**: Explorer Subagent  
**Date**: 2026-07-22  
**Scope**: Architecture & Spec Alignment Audit across `src/`, `web/`, `对齐文档/`, `goal-objective.md`, `CLAUDE.md`, `README.md`, `docs/`.

---

## Executive Summary

A comprehensive, read-only architectural and specification alignment audit was conducted for Inner Cosmos. The investigation identified **8 primary technical and specification discrepancies** across three core domains:
1. **Slow Letter (慢信) Lifecycle & State Machine**: Non-atomic background updates, instantaneous `FLYING` state transit, dead controller endpoints, missing draft-edit capabilities, and decoupled reply state transitions.
2. **EchoCapsule (共鸣体) Boundary & Quota Enforcement**: Disconnect between `CapsuleBoundary.maxConversationTurns` and `EchoCapsule.conversationLimitPerDay`, and sole reliance on LLM prompt compliance for topic boundary enforcement without backend code validation.
3. **MemoryCard & Nightly Settlement State Machine**: Severe gravity calculation conflict between `@Async` `GravityRecalculateListener` (which slashes gravity by 78% for new cards due to `null` `lastTouchedAt`) and `NightlyMemorySettlementJob` (which uses `createdAt`), causing gravity bouncing; plus unordered execution between memory extraction and recalculation listeners.
4. **Timezone & Timing Calibration**: Timezone fragmentation between `Asia/Shanghai` (used in `PersonaChatServiceImpl`) and system default timezone (used in `SlowLetterServiceImpl` and `LetterDeliveryJob`), plus hardcoded 3-minute parallax delivery.

---

## Detailed Audit Findings

### Finding 1: Non-Atomic Status Update in `LetterDeliveryJob` causing Race Conditions
- **Exact File Path & Line Numbers**: `com/innercosmos/scheduler/LetterDeliveryJob.java:71–96`
- **Observation**:
  ```java
  SlowLetter fresh = letterMapper.selectById(letter.id);
  if (fresh == null || !fromStatus.equals(fresh.status)) {
      return true;
  }
  fresh.status = toStatus;
  if (setDeliveredAt) fresh.deliveredAt = LocalDateTime.now();
  letterMapper.updateById(fresh);
  ```
- **Root Cause Analysis**: `LetterDeliveryJob.advanceWithRetry` reads a letter via `selectById`, checks status, and then mutates `fresh.status` before executing `letterMapper.updateById(fresh)`. Unlike `SlowLetterServiceImpl.transition` (which uses `UpdateWrapper` with `.eq("status", from)`), `updateById` is non-conditional.
- **Scenario Reproduction / Failure Deduction**: If a user (sender/recipient) triggers a state transition (e.g. `READ`, `BLOCKED`, `ARCHIVED`) concurrently between the job's `selectById` check and `updateById` call, the background scheduler unconditionally overwrites the record back to `FLYING` or `DELIVERED`, clobbering user action.
- **Impact Assessment**: **High** (Data corruption of letter state graph; user privacy/blocking choices silently overwritten).
- **Recommended Fix**:
  Refactor `advanceWithRetry` to use atomic conditional update:
  ```java
  UpdateWrapper<SlowLetter> updateWrapper = new UpdateWrapper<SlowLetter>()
          .eq("id", letter.id)
          .eq("status", fromStatus)
          .set("status", toStatus);
  if (setDeliveredAt) updateWrapper.set("delivered_at", LocalDateTime.now());
  int rows = letterMapper.update(null, updateWrapper);
  if (rows == 0) return true; // lost race to user transition
  ```

---

### Finding 2: Instantaneous `FLYING` State Transit & Invisible In-Transit Flight Experience
- **Exact File Path & Line Numbers**: `com/innercosmos/scheduler/LetterDeliveryJob.java:42–56`
- **Observation**:
  ```java
  // Stage 1: SENT letters whose arrival time has come -> FLYING.
  QueryWrapper<SlowLetter> sentQuery = new QueryWrapper<>();
  sentQuery.eq("status", "SENT").le("estimated_arrival_at", now);
  List<SlowLetter> sentLetters = letterMapper.selectList(sentQuery);
  for (SlowLetter letter : sentLetters) {
      if (advanceWithRetry(letter, "SENT", "FLYING", false)) flown++;
  }
  // Stage 2: FLYING letters -> DELIVERED (completes the journey).
  QueryWrapper<SlowLetter> flyingQuery = new QueryWrapper<>();
  flyingQuery.eq("status", "FLYING");
  List<SlowLetter> flyingLetters = letterMapper.selectList(flyingQuery);
  for (SlowLetter letter : flyingLetters) {
      if (advanceWithRetry(letter, "FLYING", "DELIVERED", true)) delivered++;
  }
  ```
- **Root Cause Analysis**: Stage 1 converts `SENT` to `FLYING` when `estimated_arrival_at <= now`. In the exact same job execution tick, Stage 2 queries all `FLYING` letters and converts them to `DELIVERED` with no time check.
- **Scenario Reproduction / Failure Deduction**: Any letter reaching its delivery time is promoted from `SENT` to `FLYING` and then within milliseconds from `FLYING` to `DELIVERED`. The `FLYING` state duration is < 1ms. Neither sender nor receiver ever sees a letter in `FLYING` status in outbox/inbox.
- **Impact Assessment**: **Medium** (Violates signature product spec `功能说明书.md` §7 requirement for visible in-transit flight experience).
- **Recommended Fix**:
  Promote letters `SENT -> FLYING` at send time (or upon departure), and execute `FLYING -> DELIVERED` when `now >= estimated_arrival_at`:
  ```java
  // Stage 1: SENT -> FLYING immediately upon departure
  // Stage 2: FLYING -> DELIVERED when estimated_arrival_at <= now
  QueryWrapper<SlowLetter> flyingQuery = new QueryWrapper<>();
  flyingQuery.eq("status", "FLYING").le("estimated_arrival_at", now);
  ```

---

### Finding 3: Dead/Broken Controller Endpoint `POST /api/letters/{id}/deliver`
- **Exact File Path & Line Numbers**: `com/innercosmos/controller/LetterController.java:34–37` vs `com/innercosmos/service/impl/SlowLetterServiceImpl.java:104–107`
- **Observation**:
  - `LetterController.java`:
    ```java
    @PostMapping("/{id}/deliver")
    public ApiResponse<SlowLetter> deliver(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(slowLetterService.transition(currentUserId(session), id, "DELIVERED"));
    }
    ```
  - `SlowLetterServiceImpl.java`:
    ```java
    if (List.of("FLYING", "DELIVERED").contains(targetStatus)) {
        throw new com.innercosmos.exception.BusinessException(
                com.innercosmos.common.ErrorCode.UNAUTHORIZED, "信件抵达只能由调度系统推进");
    }
    ```
- **Root Cause Analysis**: The REST controller exposes an HTTP endpoint `/api/letters/{id}/deliver`, but the underlying service method explicitly blocks user-initiated transitions to `DELIVERED`.
- **Scenario Reproduction / Failure Deduction**: Any API request sent to `POST /api/letters/{id}/deliver` will unconditionally fail with a 401/403 business exception (`UNAUTHORIZED`).
- **Impact Assessment**: **Low** (Dead API endpoint; contract mismatch).
- **Recommended Fix**: Remove the endpoint from `LetterController` or reserve it for internal administrative integration testing with proper authorization annotations.

---

### Finding 4: Disconnect Between `CapsuleBoundary.maxConversationTurns` and `EchoCapsule.conversationLimitPerDay`
- **Exact File Path & Line Numbers**: `com/innercosmos/service/impl/CapsuleServiceImpl.java:746–757` vs `com/innercosmos/service/impl/PersonaChatServiceImpl.java:290–296`
- **Observation**:
  - `CapsuleServiceImpl.updateBoundary`:
    ```java
    if (boundary.maxConversationTurns != null) existing.maxConversationTurns = boundary.maxConversationTurns;
    boundaryMapper.update(null, new UpdateWrapper<CapsuleBoundary>()
            .set("max_conversation_turns", existing.maxConversationTurns)...);
    ```
  - `PersonaChatServiceImpl.resolveDailyLimit`:
    ```java
    private int resolveDailyLimit(EchoCapsule capsule) {
        if (capsule == null) return 30;
        boolean isSeed = "SEED_CAPSULE".equals(capsule.capsuleType) || "SEED".equals(capsule.capsuleType);
        if (isSeed) return SEED_EFFECTIVE_DAILY_LIMIT;
        int configured = capsule.conversationLimitPerDay != null ? capsule.conversationLimitPerDay : 30;
        return Math.max(2, Math.min(50, configured));
    }
    ```
- **Root Cause Analysis**: `updateBoundary` updates `max_conversation_turns` in `tb_capsule_boundary`, but does NOT update `conversation_limit_per_day` in `tb_echo_capsule`. Meanwhile, `PersonaChatServiceImpl.resolveDailyLimit` reads ONLY `capsule.conversationLimitPerDay` from `tb_echo_capsule`, completely ignoring `CapsuleBoundary`.
- **Scenario Reproduction / Failure Deduction**: An EchoCapsule owner calls `POST /api/capsule/{id}/boundary` to change the daily dialogue limit. The `tb_capsule_boundary` table updates, but active visitor sessions continue using the old `conversation_limit_per_day` value from `tb_echo_capsule`.
- **Impact Assessment**: **High** (Capsule owner configuration is silently ignored by the chat runtime engine).
- **Recommended Fix**:
  In `CapsuleServiceImpl.updateBoundary`, synchronize `maxConversationTurns` to `tb_echo_capsule.conversation_limit_per_day`:
  ```java
  if (boundary.maxConversationTurns != null) {
      existing.maxConversationTurns = boundary.maxConversationTurns;
      capsule.conversationLimitPerDay = boundary.maxConversationTurns;
      capsuleMapper.updateById(capsule);
  }
  ```

---

### Finding 5: Emotional Gravity Bouncing & Conflict Between `GravityRecalculateListener` and `NightlyMemorySettlementJob`
- **Exact File Path & Line Numbers**: `com/innercosmos/event/GravityRecalculateListener.java:34–42` vs `com/innercosmos/scheduler/NightlyMemorySettlementJob.java:127–135` vs `com/innercosmos/service/impl/GravityServiceImpl.java:9–17`
- **Observation**:
  - `GravityRecalculateListener.java`:
    ```java
    long days = card.lastTouchedAt != null
            ? java.time.Duration.between(card.lastTouchedAt, LocalDateTime.now()).toDays()
            : 30;
    double base = alpha * intensity + beta * recurrenceCount + gamma * userImportance + delta * triggerCount;
    card.emotionalGravity = Math.log(1 + Math.max(base, 0)) * Math.exp(-lambda * Math.max(days, 0));
    ```
  - `NightlyMemorySettlementJob.java`:
    ```java
    java.time.LocalDateTime anchor = card.lastTouchedAt != null
            ? card.lastTouchedAt : card.createdAt;
    long daysSince = anchor == null ? 0L
            : java.time.temporal.ChronoUnit.DAYS.between(anchor, now);
    card.emotionalGravity = gravityService.calculateGravity(
            card.intensityScore, card.recurrenceCount,
            card.userImportance, card.triggerCount, daysSince);
    ```
- **Root Cause Analysis**:
  1. `GravityRecalculateListener` duplicates gravity calculation logic instead of calling `GravityServiceImpl`.
  2. When `lastTouchedAt` is `null`, `GravityRecalculateListener` hardcodes `days = 30`, applying an instant 77.7% decay penalty (`exp(-0.05 * 30)`).
  3. `NightlyMemorySettlementJob` correctly falls back to `card.createdAt` (`daysSince = 0`, no decay).
- **Scenario Reproduction / Failure Deduction**: When a user finishes a dialogue, `GravityRecalculateListener` runs asynchronously. Any memory card with `lastTouchedAt == null` suffers an immediate ~78% drop in emotional gravity. Later at 2:00 AM, `NightlyMemorySettlementJob` runs and recalculates gravity using `createdAt`, causing gravity to suddenly jump back up by ~78%.
- **Impact Assessment**: **High** (Causes severe gravity bouncing, corrupts starfield positioning, violates deterministic time-decay rules).
- **Recommended Fix**:
  Refactor `GravityRecalculateListener` to use `GravityService`:
  ```java
  LocalDateTime anchor = card.lastTouchedAt != null ? card.lastTouchedAt : card.createdAt;
  long days = anchor == null ? 0L : Duration.between(anchor, LocalDateTime.now()).toDays();
  card.emotionalGravity = gravityService.calculateGravity(
          card.intensityScore != null ? card.intensityScore : 0.0,
          card.recurrenceCount != null ? card.recurrenceCount : 0,
          card.userImportance != null ? card.userImportance : 0.0,
          card.triggerCount != null ? card.triggerCount : 0,
          days);
  ```

---

### Finding 6: Unordered Async Execution in `DialogFinishedEvent` Listeners
- **Exact File Path & Line Numbers**: `com/innercosmos/event/MemoryExtractListener.java:20–21` & `com/innercosmos/event/GravityRecalculateListener.java:25–26`
- **Observation**:
  - `MemoryExtractListener.java`: `@Async("taskExecutor") @TransactionalEventListener(...)`
  - `GravityRecalculateListener.java`: `@Async("taskExecutor") @TransactionalEventListener(...)`
- **Root Cause Analysis**: Both listeners process `DialogFinishedEvent` asynchronously without explicit order definition (`@Order`).
- **Scenario Reproduction / Failure Deduction**: If `GravityRecalculateListener` executes prior to `MemoryExtractListener` finishing, `GravityRecalculateListener` recalculates gravity for pre-existing cards only. The newly extracted memory card is missed until the next night's batch settlement job.
- **Impact Assessment**: **Medium** (Newly extracted memory cards fail to participate in immediate post-session gravity recalculation).
- **Recommended Fix**: Remove `@Async` from `GravityRecalculateListener` and trigger gravity recalculation directly inside `MemoryServiceImpl.extractFromSession` after memory card insertion.

---

### Finding 7: Uncalibrated Slow Letter Parallax Timing & Timezone Fragmentation
- **Exact File Path & Line Numbers**: `com/innercosmos/service/impl/SlowLetterServiceImpl.java:86–87, 224–225` vs `com/innercosmos/service/impl/PersonaChatServiceImpl.java:64`
- **Observation**:
  - `SlowLetterServiceImpl.java`:
    ```java
    letter.parallaxDistance = 3;
    letter.estimatedArrivalAt = LocalDateTime.now().plusMinutes(3);
    ```
  - `PersonaChatServiceImpl.java`:
    ```java
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");
    ```
- **Root Cause Analysis**: `parallaxDistance` is hardcoded to `3` and `estimatedArrivalAt` is hardcoded to `+3 minutes` for all letters. Furthermore, `SlowLetterServiceImpl` uses un-zoned `LocalDateTime.now()` (system timezone), whereas `PersonaChatServiceImpl` forces `Asia/Shanghai`.
- **Scenario Reproduction / Failure Deduction**: Slow letters take a flat 3 minutes regardless of user/capsule topic distance or resonance. On servers running in non-Shanghai system timezones (e.g. UTC containers), letter delivery math and quota boundaries diverge.
- **Impact Assessment**: **Medium** (Slow social timing mechanism is non-functional/hardcoded; timezone fragmentation across backend services).
- **Recommended Fix**: Calculate `parallaxDistance` dynamically based on tag/topic overlap, wire distance to `estimatedArrivalAt`, and standardize timezone handling across all services using a unified `Clock` bean.

---

### Finding 8: Missing Draft Edit Capabilities & Decoupled Reply State Transition
- **Exact File Path & Line Numbers**: `com/innercosmos/service/SlowLetterService.java:8–28` & `com/innercosmos/service/impl/SlowLetterServiceImpl.java:198–237`
- **Observation**:
  - `SlowLetterService` lacks any `updateDraft` method.
  - `replyWithLetter` inserts a reply draft, but does NOT transition the original letter status to `REPLIED`.
- **Root Cause Analysis**: `SlowLetterService` supports `draft` and `transition`, but provides no update path for a `DRAFT` letter. Furthermore, letter status `REPLIED` is driven independently via `transition(..., "REPLIED")` without requiring an actual reply letter object.
- **Scenario Reproduction / Failure Deduction**: A user cannot fix typos in a draft letter without deleting/archiving it. A letter can be marked `REPLIED` even if no reply was created.
- **Impact Assessment**: **Low/Medium** (Incomplete draft lifecycle operations and potential state-graph decoupling).
- **Recommended Fix**: Add `updateDraft` to `SlowLetterService` / `LetterController`, and automatically transition original letter to `REPLIED` upon sending a reply letter.

---

## 5-Component Handoff Protocol Summary

### 1. Observation
- Verified codebase files in `src/main/java/com/innercosmos/`: `SlowLetterServiceImpl.java`, `LetterDeliveryJob.java`, `LetterController.java`, `PersonaChatServiceImpl.java`, `CapsuleServiceImpl.java`, `GravityRecalculateListener.java`, `NightlyMemorySettlementJob.java`, `GravityServiceImpl.java`, `MemoryExtractListener.java`, `SafetyServiceImpl.java`.
- Verified specs in `goal-objective.md`, `对齐文档/`, `docs/audit/final/`.

### 2. Logic Chain
1. *Observation*: `LetterDeliveryJob` calls `updateById(fresh)` without checking `status = fromStatus`.
   *Logic*: Race condition exists with user transitions (`READ`/`BLOCKED`). Scheduler clobbers user status.
2. *Observation*: `LetterDeliveryJob` converts `SENT -> FLYING` in Stage 1 and `FLYING -> DELIVERED` in Stage 2 during the same execution loop.
   *Logic*: `FLYING` state lasts < 1ms, breaking in-transit parallax flight visual spec.
3. *Observation*: `CapsuleServiceImpl.updateBoundary` updates `tb_capsule_boundary`, but `PersonaChatServiceImpl` reads `tb_echo_capsule`.
   *Logic*: Owner updates to `maxConversationTurns` are silently ignored in active chat quota enforcement.
4. *Observation*: `GravityRecalculateListener` hardcodes `days = 30` when `lastTouchedAt == null`, whereas `NightlyMemorySettlementJob` falls back to `createdAt`.
   *Logic*: Real-time event slashes gravity by 78% on session finish; nightly job restores it at 2 AM, causing gravity bouncing.

### 3. Caveats
- No caveats. Investigation was conducted read-only across all backend java source files, domain state machines, background schedulers, and specification documents.

### 4. Conclusion
The Inner Cosmos backend contains clear architectural and specification misalignments in Slow Letter state handling, EchoCapsule boundary sync, and MemoryCard gravity calculation. Implementing the 8 recommended refactoring fixes will bring the codebase into 100% alignment with `goal-objective.md` and `对齐文档/`.

### 5. Verification Method
1. Run backend unit & integration tests:
   ```powershell
   mvn test
   ```
2. Verify specific test classes:
   ```powershell
   mvn test -Dtest=ApplicationFlowTest
   ```
3. Inspect `handoff.md` and check that each finding references exact line numbers and concrete code fixes.
