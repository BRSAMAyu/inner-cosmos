# INNO-CAP-014 — Real-provider precision/recall harness for capsule semantic matching

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-24. Campaign C, acceptance G6/MATCH-MULTI.

## Why this exists (correcting a ledger mislabel)

The G6/MATCH-MULTI "remaining" text called embedding/user-vector similarity and calibrated semantic
relevance "the real-provider human gate." That mislabels the actual gap:

- `CapsuleEmbeddingIndexServiceImpl` + the `SEMANTIC_SIMILARITY_*` constants in
  `CapsuleServiceImpl.matchedCapsules` (see `evidence/track-a/A3-capsule-matching/`) already ensemble
  a real embedding cosine-similarity signal into every one of the five resonance strategies. This was
  not missing code — it already ships.
- What was actually missing was a **labeled precision/recall dataset and evaluation harness that
  drives that signal with a real embedding provider**, instead of only ever asserting around
  `CapsuleMatchingTest`'s deliberately Mock/deterministic `Map.of()` similarity stub. That is ordinary,
  machine-executable work gated only by an environment-variable API key — not a human-judgment gate —
  exactly like the already-proven `real-provider` chat tests
  (`TrackARealProviderSmokeEvaluationTest`) and, on the sibling `codex/w1-voice-backend` branch, the
  Qwen/DashScope memory-embedding real-provider proof (`INNO-INNER-012`).

## What was built

`CapsuleMatchingRealProviderPrecisionRecallEvaluationTest`
(`src/test/java/com/innercosmos/evaluation/`), tagged `@Tag("real-provider")` and excluded from the
default `./mvnw test` gate via the existing `pom.xml` `excludedGroups=real-provider` convention.

- **Dataset**: 6 labeled scenarios. Each has one viewer "memory" query text, one relevant capsule (a
  topic paraphrase with **zero shared characters** from `PseudoSemanticAnalyzer`'s fixed 6-family theme
  keyword lexicon — 任务压力/关系牵动/情绪承压/认知探索/自我评价/希望期待), and three distractor capsules
  on unrelated everyday topics (also outside that lexicon). Every candidate in a scenario shares the
  same `echoEnergy`/`capsuleType`, so `themeOverlap`, `portraitSignal`, `energyScore` and `seedBoost`
  are identical/zero across all four candidates — the ONLY thing that can separate them in
  `CapsuleService.matchedCapsules` is the real embedding cosine-similarity signal. This isolates
  exactly the capability the ledger asked about (calibrated semantic relevance on paraphrase, no
  shared-keyword cases) without re-testing the already-green lexical/theme path.
- **Credential gate**: reads `DASHSCOPE_API_KEY`, then `QWEN_API_KEY`, then the codebase's generic
  `MEMORY_EMBEDDING_API_KEY` (first non-blank wins) from process environment variables only — never a
  file, never logged. Absent all three, the test writes a single `SKIPPED_NO_CREDENTIAL` evidence row
  and returns without asserting any precision/recall number. It never silently substitutes the Mock/
  Disabled client and reports that as a real pass.
- **Metrics recorded per scenario** (when a key is present): `relevantCosine`, `distractorCosines`,
  `rawPrecisionAt1` / `rawRecallAt2` (from `CapsuleEmbeddingIndexService.similarities` directly),
  `pipelineRankedCapsuleIds` / `pipelineTop1IsRelevant` (from the full `CapsuleService.matchedCapsules`
  pipeline, to prove the signal is actually wired end-to-end, not just computable in isolation), and
  `capClampCollision` — flags when two candidates both cross cosine 0.8, at which point
  `SEMANTIC_SIMILARITY_CAP` (0.40) would flatten a real difference into a tie in the pipeline score.
  Aggregate `precisionAt1`/`recallAt2` and the provider model/version are written to
  `target/evaluation/capsule-matching-real-provider-report.json`.

## Verification this session (2026-07-24)

Environment check performed before writing any evidence: process environment variables (`env`, and
`cmd.exe /c set`) contain no `DASHSCOPE_API_KEY`, `QWEN_API_KEY`, `MEMORY_EMBEDDING_API_KEY`, `GLM_API_KEY`,
`MINIMAX_API_KEY` or `DEEPSEEK_API_KEY` in this session/branch. `application.yml` on this branch still
defaults `memory.embedding.enabled` to `false` and the embedding base-url/model to the generic OpenAI
placeholder (`api.openai.com` / `text-embedding-3-small`) — the Qwen/DashScope default
(`dashscope.aliyuncs.com`, `text-embedding-v4`) landed in commit `f89ed563` on branch
`codex/w1-voice-backend` (merged into `codex/w0-integration`), which is **not** an ancestor of this
worktree's branch. No key value exists anywhere in this repository, commit, or session — per this
project's own secrets policy, none should.

Run: `./mvnw test -Dtest=CapsuleMatchingRealProviderPrecisionRecallEvaluationTest -DexcludedGroups=`
Result: **1/1 passed** — the harness correctly self-skipped to a single `SKIPPED_NO_CREDENTIAL` row
(see `target/evaluation/capsule-matching-real-provider-report.json`); no precision/recall number was
produced or claimed this session. Confirmed the default gate excludes it
(`./mvnw test -Dtest=CapsuleMatchingRealProviderPrecisionRecallEvaluationTest` with no
`-DexcludedGroups=` override → "No tests were executed", matching every other `real-provider`-tagged
test in this codebase).
Focused regression: `CapsuleMatchingTest`, `CapsuleEmbeddingIndexServiceIntegrationTest`,
`CapsuleEmbeddingRetirementTest` — all green, no code under test was changed, only a new harness added.

## Honest boundary — what this does and does not prove

- **Proves**: the "real-provider human gate" framing was wrong — this is an ordinary, zero-human,
  env-var-gated machine task; the harness is real-network-capable end-to-end (query embed + candidate
  rebuild both go through the actual `MemoryEmbeddingClient`/`CapsuleEmbeddingIndexService` production
  code paths, not a bespoke embedding-only check); the dataset is designed to isolate the semantic
  signal specifically.
- **Does NOT prove**: no real precision/recall number exists yet. This session had no reachable
  embedding provider credential, so the harness has never been exercised against a live network call.
  Any future session that exports a real key and runs the command above will get real numbers — those
  numbers, not this README, are the actual acceptance evidence for calibrated semantic relevance.
- **Follow-up for the Integrator**: (1) run this harness with a real Qwen/DashScope (or any
  OpenAI-compatible) embedding key and record the resulting `precisionAt1`/`recallAt2` and any
  `capClampCollision` findings here; (2) if recall is low or clamp collisions are frequent, the next
  scoped fix is almost certainly a `SEMANTIC_SIMILARITY_CAP`/`SEMANTIC_SIMILARITY_WEIGHT` calibration
  pass, not new infrastructure; (3) consider porting the Qwen/DashScope default from
  `codex/w1-voice-backend` (`f89ed563`) into this lineage during integration so `application.yml`'s
  default matches the empirically-proven provider instead of the inert OpenAI placeholder.

## Integrator verification (2026-07-24, current `codex/w0-integration` HEAD)

This harness (this file + `CapsuleMatchingRealProviderPrecisionRecallEvaluationTest.java`) was authored
on an isolated worktree branched from an older ancestor commit and is ported here unchanged except for
the evidence ID (this repo's `codex/w1-product-5/6` sessions had already taken `INNO-CAP-011` for an
unrelated `CAPSULE-RUNTIME` harness by the time this landed, so it was renumbered `INNO-CAP-014`).

- Follow-up (3) above is **already done** on current HEAD: `application.yml`'s `memory.embedding`
  block defaults `base-url` to `dashscope.aliyuncs.com/compatible-mode/v1` and `model` to
  `text-embedding-v4` (commit `f89ed563` is an ancestor of this branch's HEAD). Only the credential
  itself, and `memory.embedding.enabled` (defaults `false`), are still unset.
- Re-verified independently, not just re-trusting the authoring session's report: no
  `DASHSCOPE_API_KEY`/`QWEN_API_KEY`/`MEMORY_EMBEDDING_API_KEY` in this shell's process env, in
  Windows `User`/`Machine` persisted environment variables, or in this machine's `.env.local` (which
  does carry real `MINIMAX_API_KEY`/`DEEPSEEK_API_KEY`/`GLM_API_KEY`/`MIMO_API_KEY` chat-provider
  keys — a real DashScope-family key has never been placed there).
- Re-ran on current HEAD: `CapsuleMatchingRealProviderPrecisionRecallEvaluationTest` with
  `-DexcludedGroups=` → 1/1, correctly `SKIPPED_NO_CREDENTIAL`; `Capsule*Test` regression → 131/131,
  no failures.
- Net: follow-up (1) genuinely remains a real-provider human/credential gate — narrowly scoped to
  "export one DashScope-compatible API key and set `MEMORY_EMBEDDING_ENABLED=true`", nothing else
  blocks it.
