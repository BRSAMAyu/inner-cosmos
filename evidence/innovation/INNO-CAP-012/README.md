# INNO-CAP-012 — Resonance matching: fixed precision/recall baseline

Date: 2026-07-23. Branch `codex/w1-product-5` (worktree off `codex/w0-integration` @ `ffcf92a`).
Campaign W1, acceptance G6/MATCH-MULTI.

## Problem

G6.MATCH-MULTI's remaining note (`docs/goal/complete-product-acceptance.yml`) says matching is
"lexical/theme-family based" and that INNO-CAP-010 added a safety filter and diversity re-rank, but
no prior evidence reports a **measured precision/recall** for the current algorithm
(`CapsuleServiceImpl.matchedCapsules`) over a fixed, labeled dataset. `evidence/innovation/INNO-CAP-005`
and `INNO-CAP-010` demonstrate individual scoring mechanics with hand-picked examples (diversity
promotion, safety filtering) but never a precision/recall number with a declared denominator. This
item establishes that missing baseline honestly, including a disclosed failure mode, rather than
reporting only favorable cases.

## Method

New test: `src/test/java/com/innercosmos/service/impl/CapsuleMatchingPrecisionRecallTest.java`
(pure Java/Mockito, deterministic, no LLM, no real user data — all fixtures are synthetic).

1. **Fixed labeled dataset.** Six single-family synthetic "viewer" memory profiles, one per
   `PseudoSemanticAnalyzer` theme family (任务压力/关系牵动/情绪承压/认知探索/自我评价/希望期待), built
   from keywords verified against the production lexicon (`THEME_KEYWORDS`) to map to **exactly
   one** family each (e.g. 任务压力 uses 作业/考试/拖延, deliberately avoiding "压力"/"累" which are
   shared with 情绪承压). A shared, fixed 24-capsule candidate library (4 synthetic capsules per
   family, synthetic owner ids/pseudonyms) is run against each of the 6 viewers, giving
   6 x 24 = **144 labeled (viewer, capsule) pairs**: GOOD when the capsule's family equals the
   viewer's family, BAD otherwise.
2. **Prediction = the algorithm's own `resonant` boolean** (not "did it make the top-12"), so the
   measurement is independent of `limit(12)`/backfill truncation and reflects exactly what the
   FIX-A resonant/non-resonant scoring decision does.
3. Two additional fixed cases: a **mixed two-family viewer** (partial-overlap / "neutral" case in
   the assignment's good/bad/neutral framing) and a **disclosed known-limitation case** (a
   same-real-world-domain paraphrase with zero exact lexicon keyword overlap).

Reproduce:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
.\mvnw.cmd test "-Dtest=com.innercosmos.service.impl.CapsuleMatchingPrecisionRecallTest"
```

## Honest result (real numbers, real denominators)

```
[G6.MATCH-MULTI baseline] tp=24 fp=0 fn=0 tn=120 precision=1.0 recall=1.0
  任务压力 tp=4 fp=0 fn=0 tn=20
  关系牵动 tp=4 fp=0 fn=0 tn=20
  情绪承压 tp=4 fp=0 fn=0 tn=20
  认知探索 tp=4 fp=0 fn=0 tn=20
  自我评价 tp=4 fp=0 fn=0 tn=20
  希望期待 tp=4 fp=0 fn=0 tn=20
```

Denominators: 144 total labeled pairs = 24 GOOD (4 per family x 6 families, each viewer's own
family) + 120 BAD (20 per viewer x 6 viewers, the other 5 families' 20 capsules). tp=24, fp=0,
fn=0, tn=120 -> **precision = 1.0 (24/24), recall = 1.0 (24/24)** on this fixed, exact-keyword-
overlap synthetic dataset.

**This is the honest scope of the result, not an overclaim:** it measures the algorithm's behavior
only within the regime it was designed for (exact/near-exact lexicon keyword overlap). It does
*not* claim anything about semantic/paraphrase inputs — the opposite is demonstrated next.

### First-run finding, fixed before the baseline was recorded

The first run of this exact test produced `fp=4` for the 情绪承压 viewer (precision 0.857, not
1.0) — not a production bug, but a **fixture-construction bug**: the synthetic capsule `intro` text
originally appended the human-readable family label (e.g. "任务压力相关"), and "任务压力" itself
contains the substring "压力", which is *also* a 情绪承压 lexicon keyword. That silently
cross-contaminated the 任务压力 fixture capsules with a second family, and the algorithm correctly
detected it. Fixed by using a family-agnostic descriptor with no lexicon-keyword substrings; the
mistake and fix are left in the test's inline comment as a caution against similar future
fixture-authoring drift.

### Disclosed known limitation (not fixed here)

`disclosedLimitation_paraphrasedSameDomainCapsuleIsNotRecognizedAsResonant`: a capsule describing
job-burnout exhaustion ("连续熬夜赶稿件，身心俱疲，不知道还能撑多久，却不敢和领导说") — the same
real-world domain as a 任务压力 (work/deadline-stress) viewer profile by human judgment — shares
**zero** exact lexicon keywords with it, so `themesOf()` returns an empty family set and the
algorithm does **not** flag it as resonant. This is a genuine, reproducible recall miss for
paraphrased/semantic-but-non-lexical matches, confirming (with a concrete example instead of an
unmeasured assertion) the ledger's existing remaining note: "Matching remains lexical/theme-family
based; embedding/user-vector similarity and calibrated semantic relevance remain the real-provider
human gate." No fix is attempted here — CapsuleServiceImpl already computes a `semanticSignal`
from `CapsuleEmbeddingIndexService` when a real embedding provider is configured (currently 0.0 in
this test, matching the production `DisabledMemoryEmbeddingClient` degrade with no provider); the
real-provider re-measurement of this exact paraphrase case is exactly the standing human/provider
gate, not additional machine work this dispatch should invent.

### Mixed two-family viewer ("neutral"/partial-overlap case)

`mixedTwoFamilyViewer_resonatesOnPartialOverlap`: a viewer profile spanning two families
(任务压力 + 情绪承压) still resonates with a capsule sharing only one of them. This discloses that
the current algorithm has **no graded partial-overlap/neutral bucket** — any nonzero shared-family
overlap is treated as fully resonant (OR-logic). Calibrated, graded relevance is part of the same
real-provider/semantic gate named above, not a machine fix attempted here.

## Verification

```
mvn test -Dtest=com.innercosmos.service.impl.CapsuleMatchingPrecisionRecallTest
-> Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

mvn test -Dtest="com.innercosmos.service.impl.Capsule*Test"
-> 7 test classes, all green, including the pre-existing CapsuleMatchingTest 18/18 (no regression)
```

## What this closes and what remains for G6.MATCH-MULTI

Closes: the previously-missing fixed, labeled, reproducible precision/recall baseline for the
CURRENT matching algorithm, with an honestly disclosed known failure mode (paraphrase/semantic
miss) and an honestly disclosed calibration gap (no neutral/partial-overlap grading) — not just a
prose claim.

Still open (unchanged from the existing ledger note, not re-solved here): embedding/user-vector
similarity and calibrated semantic relevance require a real embedding/LLM provider and are the
standing `REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW` human/provider gate. A future re-measurement
should rerun this exact fixed dataset (plus the paraphrase case) with a real provider configured
via `CapsuleEmbeddingIndexService` and report the recall delta.
