# A2 — Memory and Portrait Truth Engine (Track A / Living Intelligence)

Status: **IN_PROGRESS** (one real, test-verified slice landed this session; A2 as a whole is far from
COMPLETE — see "What is still missing" below). This is the first session to touch workstream A2;
`current_front` moves here from `A1-living-aurora`.

## 0. What this session found before writing any code

Before implementing anything, I read `docs/tracks/TRACK-A-LIVING-INTELLIGENCE.md` §6 in full and
audited the existing claim/memory code (`com.innercosmos.ai.claim.*`, `MemoryRetrievalService`,
`UserCorrectionServiceImpl`, `MemoryLifecycleServiceImpl`, `ClaimCandidateServiceImpl`) rather than
assuming A2 was greenfield. Contrary to the `NOT_STARTED` status this workstream carried coming into
this session, a substantial, already-tested unified claim system exists:

- **Unified ingestion already exists.** `ClaimCandidateServiceImpl` (Campaign B) turns conversation
  into `CANDIDATE` `UnderstandingClaim` rows via the deterministic `ClaimCandidateExtractor`
  (`com.innercosmos.ai.claim`), tagged with an evidence-tier `authorityLevel`
  (`MODEL_INFERENCE < SINGLE_EXPLICIT < REPEATED_BEHAVIOR < REPEATED_EXPLICIT`, see
  `ClaimAuthority`), source message provenance (`provenanceMessageIds`), and a calibrated confidence.
  `UserCorrectionServiceImpl` promotes an explicit user act to an `ACTIVE`, `USER_CORRECTION`-tier
  claim (confidence always 1.0), with version/`supersedesClaimId` chaining. This already satisfies
  most of §6's "unify claim ingestion ... source spans/IDs, confidence, authority ... contradiction
  groups" bullet.
- **Correction/withdrawal propagation is already broad**, not the gap I expected going in.
  `UserCorrectionServiceImpl.confirm()`/`.deleteCorrection()` and `MemoryLifecycleServiceImpl.execute()`
  (the `FORGET` path especially) already: supersede the old `MemoryCard`, mark `AuthorizedMemoryRef`
  rows `NEEDS_REVIEW`, flip the `EchoCapsule` to `NEEDS_REVIEW`/private and null its compiled
  `stylePprofile`/`contextPreview` JSON, call `CapsuleGenomeService.markNeedsReview` (withdraws the
  active Genome version), invalidate `MemoryEmbedding` rows to `STALE`, revoke `DataUseGrantService`
  grants, delete derived `ThoughtFragment`/`TodoItem`/`RelationMention` rows, and emit
  `ClaimPropagation`/`MemoryProjectionReceipt` audit rows plus a `CapsuleSyncTriggerEvent`. This is a
  real, already-tested (`UserCorrectionControllerTest`, `ClaimCandidateServiceImplIntegrationTest`,
  `ForgettingCompletenessEvaluationTest`, `CorrectionConflictEvaluationTest`,
  `CorrectionTargetingEvaluationTest`) derivative graph, not a stub.
- **Task-aware retrieval is partially real, not flat.** `MemoryRetrievalServiceImpl.taskFit(task,
  card)` already conditions scoring on the `task` string (`ACTION`/`RELATION`/`CAPSULE`/`EMOTION`/
  `PROFILE` route to different layer/type preferences), which is a genuine — if simple — task
  policy, not a naive similarity/recency-only search.
- **The one piece with zero implementation, confirmed by exhaustive search:** `tb_understanding_claim
  .confidence` was written exactly once, at insertion (`ClaimCandidateServiceImpl#upsert` or
  `UserCorrectionServiceImpl#confirm`), and **never read or modified anywhere else in the codebase**
  (verified: `grep -r confidence` across every claim call site shows only assignment, never a
  time-aware recomputation). §6 explicitly requires the claim graph to "decay weak inferences and
  never decay explicit user assertions as if they were guesses" — before this session that sentence
  was 0% implemented: a six-month-old, never-confirmed single-utterance guess carried exactly the
  same weight as a claim restated ten times last week, forever, with no code path that could ever
  change that.

**This session's chosen highest-value increment: claim confidence decay**, closing that specific,
concretely-verified 0%-implemented gap, rather than re-building unified ingestion or correction
propagation that already exist and are already tested.

## 1. What was built

New pure policy class `com.innercosmos.ai.claim.ClaimConfidenceDecayPolicy`
(`src/main/java/com/innercosmos/ai/claim/ClaimConfidenceDecayPolicy.java`):

- `neverDecays(authorityLevel)` — true only for `USER_CORRECTION`/`USER_CONFIRMED` (the two tiers the
  automatic extractor can never produce — see `ClaimAuthority`'s own javadoc). Explicit user
  assertions are protected by construction, not by a confidence floor.
- `halfLifeDays(authorityLevel)` — per-tier half-life reflecting how much standalone weight a tier
  deserves without reinforcement: `MODEL_INFERENCE` 14d (fastest — a pure guess) <
  `SINGLE_EXPLICIT` 30d < `REPEATED_BEHAVIOR` 60d < `REPEATED_EXPLICIT` 90d (slowest — repeated,
  explicit evidence). An unrecognized/absent tier falls back to a conservative 21-day half-life
  instead of crashing.
- `effectiveConfidence(base, authorityLevel, referenceTime, now)` — exponential half-life decay from
  `referenceTime` (the claim's `updatedAt`, which `ClaimCandidateServiceImpl#upsert` already bumps on
  every re-mention — so genuine reinforcement legitimately resets the decay clock) to `now`. Pure
  function; never mutates the stored `confidence` column itself, which stays as immutable
  extraction-time provenance — only display and dismiss decisions use the decayed value. This
  deliberately avoids a nightly-multiplication design (like `NightlyMemorySettlementJob`'s capsule
  energy decay), which would double-decay or need extra bookkeeping to stay idempotent across
  repeated runs.
- `isStale(effectiveConfidence)` — below `DISMISS_THRESHOLD = 0.15`.

Wired into two real call sites, both in `com.innercosmos.service.impl.ClaimCandidateServiceImpl`:

1. `listCandidates(userId)` — now computes and returns the *decayed* confidence for each `CANDIDATE`
   row, and lazily auto-dismisses (`status = DISMISSED`) any row whose effective confidence has
   crossed the stale floor before it is returned to the caller. This makes decay observable the
   moment a user opens their pending-understanding review list, with no scheduler dependency.
2. New `sweepStaleCandidates(int batchSize)` (added to the `ClaimCandidateService` interface) — a
   global batch sweep, scoped to `status=CANDIDATE, sourceType=AUTO_EXTRACTION` only (so `ACTIVE`/
   confirmed claims are never selected, not merely never decayed), for candidates the owner never
   revisits. Backed by a new scheduled job `com.innercosmos.scheduler.ClaimDecaySweepJob`
   (`@Scheduled(cron = "0 30 2 * * ?")`, `claim.decay.sweep-batch-size` configurable, ShedLock-guarded
   like the project's other schedulers, `@ConditionalOnExpression` gated to the `all`/`scheduler`
   runtime role) — mirrors `MemoryEmbeddingRebuildJob`'s simpler global-batch pattern rather than
   `NightlyMemorySettlementJob`'s per-user loop, since decay needs no per-user portrait/gravity
   context, only the claim rows themselves.

## 2. Tests (all real, all green — see §4 for exact commands/output)

- `src/test/java/com/innercosmos/ai/claim/ClaimConfidenceDecayPolicyTest.java` — 9 pure/deterministic
  unit tests (no Spring context): explicit assertions never decay even after a year;
  `MODEL_INFERENCE` decays faster than `REPEATED_EXPLICIT` over the same window; half-life ordering
  matches evidence strength; confidence halves at exactly one half-life; floors at (near) zero for
  very long elapsed time and never goes negative; reinforcement (a fresher reference time) yields
  higher effective confidence than an unreinforced claim of the same age; `isStale` matches the
  threshold; null/non-positive elapsed time is a no-op; an unrecognized authority level falls back to
  the documented default instead of crashing.
- `src/test/java/com/innercosmos/scheduler/ClaimDecaySweepJobTest.java` — 2 Mockito tests confirming
  the scheduled job delegates to the real service method with the configured batch size.
- `src/test/java/com/innercosmos/service/impl/ClaimCandidateServiceImplIntegrationTest.java` — 3 new
  integration tests added to the existing Campaign B suite (real H2 Spring context, real
  `ClaimCandidateService`):
  - a 200-day-unreinforced `HABIT`/`REPEATED_BEHAVIOR` candidate (60-day half-life) is absent from
    `listCandidates` and is `DISMISSED` in the database;
  - a 30-day-old `PREFERENCE`/`SINGLE_EXPLICIT` candidate (30-day half-life, i.e. exactly one
    half-life elapsed) still appears in `listCandidates` but with a confidence roughly halved
    (strictly between 0.15 and 0.55) — proving decay is visible before a candidate is retired, not
    only a binary keep/drop;
  - `sweepStaleCandidates` across three different users in one call: dismisses a 200-day-stale
    candidate, leaves a fresh (unbackdated) candidate for a different user untouched, and — this is
    the explicit-assertion protection check — leaves a `confirm()`-promoted `ACTIVE`/
    `USER_CORRECTION` claim backdated **two years** completely untouched (`status` stays `ACTIVE`,
    `confidence` stays exactly `1.0`).
  - Backdating uses `claimMapper.update(null, new UpdateWrapper<UnderstandingClaim>()...set("updated_at",
    ...))` (bypassing `updateById(entity)`, whose `MybatisMetaObjectHandler.updateFill` unconditionally
    stamps `updated_at` to "now" on every entity-based update) — the same
    `update(null, UpdateWrapper)` escape hatch `MemoryLifecycleServiceImpl`'s `FORGET` path already
    uses for the same reason.
- `src/test/java/com/innercosmos/evaluation/TrackAClaimDecayAblationEvaluationTest.java` — a new
  sibling to the existing A0 ablation suites (real `ClaimCandidateService`/`UserCorrectionService`
  via Spring, not a disconnected script), 4 scenario rows (2 types × dev/held-out) proving two
  distinct real-vs-naive gaps:
  - `stale_weak_inference_retired` (210-day dev / 365-day held-out): the real policy retires a
    long-unconfirmed weak inference; a `naive_static` baseline (confidence never changes after
    extraction, defined only in the test) would keep reporting it as a fresh guess forever.
  - `explicit_correction_protected` (400-day dev / 730-day held-out): the real policy leaves an
    explicit `USER_CORRECTION` claim's confidence at exactly 1.0 no matter how old; a
    `naive_uniform_decay` baseline (applies the *same* exponential decay to every claim regardless of
    tier, defined only in the test) would erode it to ~1.8e-6 / ~3.4e-11 — i.e. treat an explicit
    correction exactly like a throwaway guess. This is the "never decay explicit user assertions as
    if they were guesses" half of §6, made concrete and measurable rather than asserted in prose.
  - Report: `claim-decay-ablation-report.json` (checked in here, copied from
    `target/track-a-eval/claim-decay-ablation-report.json`), `unexpectedFailureLedger` empty.

## 3. Why this design (read-time decay, not a mutating nightly multiply)

An earlier design considered multiplying `confidence` in place on a nightly cron (matching
`NightlyMemorySettlementJob`'s capsule-energy-decay style). Rejected because: (a) it destroys the
original evidence-based confidence, which is itself provenance worth keeping; (b) it is not
idempotent without an extra `lastDecayedAt` column and careful "days since last decay, not since
creation" bookkeeping — get that wrong and repeated runs compound incorrectly. Computing
`effectiveConfidence` as a pure function of the immutable stored confidence, the authority tier, and
elapsed wall-clock time avoids both problems entirely and is trivially unit-testable without a
scheduler or clock-mocking harness.

## 4. Reproducing this evidence

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"   # or your JDK 21 path
cd inner-cosmos-track-a
./mvnw test -Dtest=ClaimConfidenceDecayPolicyTest,ClaimDecaySweepJobTest,ClaimCandidateServiceImplIntegrationTest,TrackAClaimDecayAblationEvaluationTest
# -> Tests run: 20, Failures: 0 (9 + 2 + 6 + 1, respectively — 6 not 3 because 3 pre-existing
#    Campaign B tests are in the same class as the 3 new decay tests)
./mvnw test
# -> full suite green this session: 874/874, 0 failures/errors (see docs/goal/tracks/track-a-status.yml
#    "verification" for the exact run log; one AuroraStreamControllerTest concurrency flake was seen
#    in one full-suite run and reproduced as a pass both in isolation and in an immediate full-suite
#    rerun — unrelated to any file this session touched, see "discoveries" in the status file)
powershell -File scripts/scan-secrets.ps1
# -> Secret scan PASS: 0 findings
```

## 5. What is still missing before A2 can be called COMPLETE

Per `docs/tracks/TRACK-A-LIVING-INTELLIGENCE.md` §6, this session closed exactly one concrete,
previously-0%-implemented gap (claim confidence decay with explicit-assertion protection). Still
open:

- **Claims are not part of retrieval at all.** `MemoryRetrievalServiceImpl.retrieve()` queries
  `tb_memory_card` only; `tb_understanding_claim` (the portrait/correction ledger) is never consulted
  when assembling an `AURORA_CONVERSATION`/`AURORA_RETRIEVAL` evidence pack. An `ACTIVE`
  `USER_CORRECTION` claim ("我希望被叫小舟，不是小林") is authoritative but is not itself retrievable
  memory evidence — today it only reaches the conversation indirectly, through whatever `MemoryCard`
  rows the correction flow also touched. Bridging claims into retrieval (or documenting why they
  should stay a separate ledger) is the single largest remaining A2 architectural question.
- **Valid/effective time is still absent from `UnderstandingClaim`.** The entity has `createdAt`/
  `updatedAt` (transaction time) but no explicit effective-from/effective-until validity window, so
  "this was true from date X to date Y" (as opposed to "this was extracted/confirmed at time X")
  cannot be expressed. §6 explicitly asks for "valid/effective time." Explicit contradiction groups (a
  labeled set of mutually-exclusive claims, as opposed to today's simple `supersedesClaimId` chain)
  are not modeled either.
- **No hybrid/vector benchmark for claim or memory retrieval yet** (§6's "Benchmark lexical, vector
  and hybrid retrieval on hard negative, temporal conflict, paraphrase and privacy cases"). Only the
  lexical/local-ngram + optional provider-embedding path in `MemoryRetrievalServiceImpl` exists; there
  is no comparative recall/relevance/contradiction/latency/cost report across strategies.
  A5's "cross-referencing this catalog with ai-lab's existing Python harness" observation from A0
  applies here too — the decay half-lives chosen this session (14/30/60/90 days) are a first, reasoned
  calibration matching the qualitative "weak vs strong evidence" ordering in the extractor's own
  authority-tier javadoc, **not tuned against a labeled corpus of real staleness judgments** — flagged
  as a human gate below, the same way A1's adaptive-budget-policy weights were.
- Task-aware retrieval (`MemoryRetrievalServiceImpl.taskFit`) is real but still simple (five coarse
  task buckets); it has not been extended to reason about the claim graph's authority tiers or
  decay-adjusted confidence at all, since claims are not in the retrieval path (see above).
- No new contract delta was needed this session (`ClaimCandidateVO.confidence()` already existed as
  a `double`; its value is now decayed rather than static, which is a pure quality change with no
  shape change Track B's UI needs to react to differently — the field still means "current confidence
  in this pending understanding," which is if anything a more honest reading of the same contract).
