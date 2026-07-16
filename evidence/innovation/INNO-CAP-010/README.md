# INNO-CAP-010 — Resonance matching: safety filter + diversity re-rank

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-16. Campaign C, acceptance G6/MATCH-MULTI.

## Problem

Resonance matching (`CapsuleServiceImpl.matchedCapsules`) was an explainable, switchable lexical/
theme-family heuristic (Mirror/Complement/Growth-Edge/Serendipity/Contextual, 16 passing tests) but
its scoring pipeline had two gaps flagged by review:

1. **No safety-filter stage.** The plaza query gates only `is_public`/`visibility_status`; it does not
   consult `tb_block_relation`. A capsule owned by someone the viewer had blocked (or who had blocked
   the viewer) could still surface as a resonance match — a real social-safety leak.
2. **No diversity stage.** Ranking was pure score-descending, so several near-identical capsules
   (same dominant theme family) could crowd the top slots and bury a genuinely different, relevant
   resonance.

## Implemented

- **Safety filter** (`blockedCounterparties`): a single per-viewer lookup of `tb_block_relation` in
  both directions (viewer→owner and owner→viewer); any capsule whose owner is in that set is skipped
  before scoring. Mirrors the block semantics already used by the letters/social services.
- **Diversity re-rank** (`diversify`): maximal-marginal-relevance selection within each ranking group.
  `mmr = λ·relevance − (1−λ)·maxSimilarityToSelected`, λ=0.72, similarity = Jaccard over the capsule's
  matched theme families. Applied independently to the resonant group and the backfill group so the
  FIX-A invariant (a genuinely relevant capsule always outranks a zero-overlap backfill one) is
  preserved. Ties fall back to the existing deterministic comparator (matchScore ↓, echoEnergy ↓,
  id ↑). The transient theme-key feature is stripped from the returned items.

## Verification (Java 21)

- RED→GREEN `CapsuleMatchingTest.diversityRerankPromotesADissimilarCapsuleOverNearDuplicates`: three
  near-duplicate 任务压力 capsules (energy 0.85) vs one 关系牵动 capsule (energy 0.80). On raw score the
  task clones take the top three slots and the 关系牵动 capsule lands last; the diversity stage promotes
  the dissimilar capsule into slot two (mmr 0.27 vs a penalized clone's ≈0.001). Uses clean
  single-family keywords (作业/考试/拖延 vs 朋友/同学/吵架; avoiding 压力/累 which map to two families).
- `CapsuleMatchingTest.blockedOwnerCapsuleIsExcludedFromMatches`: a blocked owner's high-energy,
  strong-overlap capsule is filtered out while a non-blocked capsule still matches.
- The prior 16 matching tests remain green (diversity preserves behavior when profiles are distinct);
  full Java regression 835/835.

## Honest boundary

This closes the two named scoring-stage gaps (safety filter, diversity re-rank) and keeps matching
explainable and switchable. It is still lexical/theme-family based: real user-vector / embedding
similarity and calibrated semantic relevance remain provider-gated
(REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW), and the diversity similarity metric is theme-family
Jaccard, not embedding cosine. Report values are deterministic and provider-independent.
