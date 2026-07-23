# INNO-CAP-013 — Resonance matching: local semantic-cue signal + graded match-tier bucket

Date: 2026-07-23. Branch `codex/w1-product-6` (worktree off `codex/w0-integration` @ `d4c634f`).
Campaign W1, acceptance G6/MATCH-MULTI.

## Problem

`INNO-CAP-012`'s fixed precision/recall baseline (`evidence/innovation/INNO-CAP-012/`) established
the first honest measurement of the current lexical/theme-family matching algorithm and disclosed
two specific, reproducible gaps in its own `remaining:` note, later carried into the ledger's
`MATCH-MULTI` remaining note verbatim:

1. "a same-domain paraphrase with zero exact lexicon keyword overlap is a genuine reproducible
   recall miss" — a capsule describing job-burnout exhaustion in different words than the viewer's
   task-pressure profile was not recognized as resonant at all.
2. "a two-family viewer shows the algorithm has no graded neutral/partial-overlap bucket (any
   nonzero family overlap is fully resonant)" — a viewer whose recent memories span two theme
   families gets the exact same boolean `resonant=true` whether a capsule covers one of those
   families or both, with no way to tell the difference.

**No real embedding/LLM provider API key (GLM/DeepSeek/MiniMax/OpenAI-compatible) is configured in
this environment.** `CapsuleServiceImpl` already has a real-embedding path
(`CapsuleEmbeddingIndexService` → `MemoryEmbeddingClient`), added in prior work, that would
contribute a genuine cosine-similarity signal — but it degrades to an empty map whenever no
provider is configured (`DisabledMemoryEmbeddingClient`), which is the case here. This item does
**not** touch that real-provider path or claim to exercise it. Everything below is a **local,
deterministic, hand-curated stand-in** — explicitly described as "Mock" in the code comments — not
a real semantic-understanding fix. This is disclosed up front, not discovered later.

## What was built

### 1. TDD: failing tests first

`src/test/java/com/innercosmos/service/impl/CapsuleMatchingPrecisionRecallTest.java` gained 3 new
test methods, written and run **before** any production code change to confirm they failed against
the pre-fix algorithm:

```
[ERROR] Tests run: 6, Failures: 3
  mixedTwoFamilyViewer_isTaggedPartialNotFull: expected <PARTIAL> but was <null>
  paraphraseSameDomainCapsule_nowRecognizedViaLocalSemanticCueSignal: expected <true> but was <false>
  singleFamilyFullOverlap_isTaggedFull: expected <FULL> but was <null>
```

(The other 3 pre-existing tests — the 144-pair aggregate baseline, the two-family "still resonant"
case, and the disclosed-limitation case — passed unchanged at this point, proving the new tests were
genuinely exercising un-implemented behavior, not a broken harness.)

### 2. The fix — `CapsuleServiceImpl.java`

**(a) Paraphrase recall.** A new `PARAPHRASE_CUES` map (private, static, scoped entirely inside
`CapsuleServiceImpl`) holds a small hand-curated set of broader vocabulary per theme family —
deliberately **separate from** the strict `PseudoSemanticAnalyzer.THEME_KEYWORDS` lexicon that
`themesOf()` uses everywhere else (Aurora mode suggestion, sentiment, intent detection, memory
theme profiles). Keeping it separate means this fix has **zero blast radius** outside capsule
matching — nothing else in the product changes behavior.

```java
private static final Map<String, Set<String>> PARAPHRASE_CUES = Map.of(
        "任务压力", Set.of("熬夜", "赶稿", "加班", "连轴转", "焦头烂额", "扛不住", "身心俱疲"),
        "关系牵动", Set.of("疏远", "隔阂", "不理我", "渐行渐远", "话不投机"),
        "情绪承压", Set.of("喘不过气", "情绪崩溃", "心力交瘁", "无力感", "快撑不下去"),
        "认知探索", Set.of("拿不定主意", "理不出头绪", "举棋不定", "不知道该信谁"),
        "自我评价", Set.of("配不上", "抬不起头", "一无是处", "自我怀疑"),
        "希望期待", Set.of("满怀期待", "值得期待", "心生向往", "盼望已久"));
```

A new `weakParaphraseFamiliesOf(text)` method detects families via this cue lexicon. In the main
scoring loop, a family only counts toward the new `paraphraseSignal` when the viewer's
`userThemeProfile` already actively cares about it **and** the STRICT theme match on this capsule
missed it — so a family already driving the existing `themeOverlap` is never double-counted. The
signal is capped low (`PARAPHRASE_SIGNAL_UNIT = PARAPHRASE_SIGNAL_CAP = 0.10`), well below a single
strong exact-keyword family match (`THEME_OVERLAP_UNIT = 0.18`), so a paraphrase can never out-rank
or match the strength of a genuine lexical hit. When it fires, the existing `"语义相近"`
(semantically similar) match-reason is added — the same reason text already used for the real
embedding signal — so the contribution stays explainable, not a silent score bump.

**(b) Graded match-tier bucket.** A new additive `matchTier` field
(`"FULL" | "PARTIAL" | "NONE"`) is computed per result item: `FULL` when every one of the viewer's
currently active theme families is represented by the capsule (via strict OR the new paraphrase
match), `PARTIAL` when only some are, `NONE` when none are (mirrors the existing `resonant=false`
backfill case). This is purely additive metadata — it does **not** change `resonant`, `matchScore`,
sort order or backfill behavior, so every pre-existing assertion in `CapsuleMatchingTest` (18
tests covering diversity re-rank, safety filtering, seed/user boost, FIX-A relevance gating, etc.)
continues to pass unchanged.

### 3. Updated tests (after the fix)

- `formerlyDisclosedLimitation_paraphrasedSameDomainCapsuleIsNowRecognizedAsResonant` — the OLD
  `disclosedLimitation_paraphrasedSameDomainCapsuleIsNotRecognizedAsResonant` test, **flipped, not
  deleted**, with an `UPDATE (INNO-CAP-013)` docstring explaining exactly what changed and why,
  preserving the historical before/after record in the source itself.
- `paraphraseSameDomainCapsule_nowRecognizedViaLocalSemanticCueSignal` — the new pinning test:
  asserts `resonant=true`, the `"语义相近"` reason is present, and the score sits strictly between
  the no-signal floor (`seedBoost+energyScore=0.168` for this fixture) and a strong exact-keyword
  match (`~0.348`), proving the signal moved the needle without overpowering real lexical matches.
- `mixedTwoFamilyViewer_isTaggedPartialNotFull` — same fixture as the pre-existing
  `mixedTwoFamilyViewer_resonatesOnPartialOverlap` (still resonant, unchanged), now additionally
  asserting `matchTier == "PARTIAL"`.
- `singleFamilyFullOverlap_isTaggedFull` — a control case proving `matchTier` genuinely reaches
  `"FULL"` too (not a renamed always-`"PARTIAL"` boolean).
- **New, honest disclosure:** `disclosedLimitation_aDifferentUncuredParaphraseIsStillNotRecognizedAsResonant`
  — a *different* same-domain paraphrase ("论文due日快到了，脑子转不动，只想找个地方躲起来") that
  uses neither the strict lexicon nor any `PARAPHRASE_CUES` word is confirmed **still missed**. This
  is deliberately included to keep the fix's honest scope visible: it is a small, narrow, curated
  cue list, not general semantic understanding. A different paraphrase than the one this dispatch
  happened to cue for is still a real, reproducible recall miss.

## Honest result (real numbers, real denominators)

```
mvn test -Dtest=com.innercosmos.service.impl.CapsuleMatchingPrecisionRecallTest
-> Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

[G6.MATCH-MULTI baseline] tp=24 fp=0 fn=0 tn=120 precision=1.0 recall=1.0
  任务压力 tp=4 fp=0 fn=0 tn=20
  关系牵动 tp=4 fp=0 fn=0 tn=20
  情绪承压 tp=4 fp=0 fn=0 tn=20
  认知探索 tp=4 fp=0 fn=0 tn=20
  自我评价 tp=4 fp=0 fn=0 tn=20
  希望期待 tp=4 fp=0 fn=0 tn=20
```

**The 144-pair aggregate baseline from INNO-CAP-012 is unchanged and reproduced identically**
(tp=24 fp=0 fn=0 tn=120, precision=1.0, recall=1.0). This is expected, not a coincidence: the new
`paraphraseSignal` only ever fires for a family the STRICT theme match already missed on that
capsule, and none of the 24 fixed library capsules' text (built purely from
`Family.pureKeywords` + a fixed neutral suffix) contains any `PARAPHRASE_CUES` word — verified by
grep before writing the cue lists. No tp/fp/fn/tn number needed to change, and none did.

```
mvn test -Dtest="com.innercosmos.service.impl.Capsule*Test,com.innercosmos.service.CapsuleP1P2PrivacyBoundaryTest"
-> 8 test classes, 44/44 green, no regression:
   CapsuleP1P2PrivacyBoundaryTest 5/5, CapsuleEmbeddingIndexServiceIntegrationTest 2/2,
   CapsuleEmbeddingPostgresIntegrationTest 1/1 (Testcontainers Postgres/pgvector, real Docker),
   CapsuleEmbeddingRetirementTest 4/4, CapsuleMatchingPrecisionRecallTest 7/7,
   CapsuleMatchingTest 18/18 (all pre-existing diversity/safety/FIX-A assertions unchanged),
   CapsuleQuotaIntegrationTest 5/5, CapsuleRuntimeContextComposerTest 2/2.
```

## What this closes and what remains for G6.MATCH-MULTI

Closes:
- Requirement (a) from the dispatch: a same-domain paraphrase with zero exact lexicon keyword
  overlap now gets a nonzero, capped, explainable semantic-similarity-shaped contribution instead of
  a hard miss.
- Requirement (b): a two-family viewer profile now produces a graded `matchTier` (`FULL`/`PARTIAL`/
  `NONE`) instead of a bare boolean that treats any nonzero family overlap as fully resonant.

**Explicitly does NOT close, and is not claimed to close:**
- **Real semantic/embedding matching.** This is a ~30-word hand-curated cue lexicon across 6
  families, not a trained embedding space. `disclosedLimitation_aDifferentUncuredParaphraseIsStillNotRecognizedAsResonant`
  proves a different paraphrase is still missed. Real embedding/user-vector similarity and
  calibrated semantic relevance remain the standing `REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW`
  human/provider gate named in the ledger.
- **A real-provider re-measurement of the fixed dataset.** No provider key was available in this
  environment; `CapsuleEmbeddingIndexService`'s real-provider path is untouched by this change and
  remains exactly as capable/incapable as before. A future dispatch with a real embedding key should
  re-run this same fixed dataset (plus the two new paraphrase cases here) through that real path and
  report the recall delta against both the cue-lexicon result above and a no-signal baseline.
- **General calibration of the graded bucket's thresholds.** `matchTier`'s FULL/PARTIAL boundary
  (100% vs partial family coverage) is a first cut, not validated against human judgment of what
  "mostly resonant" should mean at 3+ family breadth.
