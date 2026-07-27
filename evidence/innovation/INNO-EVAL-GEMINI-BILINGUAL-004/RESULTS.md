# INNO-EVAL-GEMINI-BILINGUAL-004 — exclusive real-Gemini result

Status: **REAL OUTPUTS COMPLETE / HUMAN REVIEW PENDING**

`effectiveness_claim=false`.

This is the first formal run after the explicit Aurora output-language contract,
session-level `GEMINI` pin, router repair and Demo-unlimited repair. It ran in an
exclusive Provider window. The preceding `-003` run is separately marked
`INVALID_CONCURRENT_LOAD` and is excluded from every number below.

## Frozen design

- Dataset: 24 semantic prompts × `zh-CN` / `en-US` = 48 paired cases.
- Dataset SHA-256:
  `96f12ab65ef2dae218a563e0fb32f202fe12fc3b06c4c6f9617a45433f44f3ad`.
- Seed: `20260728`; left/right order was frozen before Provider calls.
- Both arms used `gemini-3.6-flash`.
- Baseline arm: direct native Gemini with a neutral assistant system prompt.
- Product arm: isolated registered account, explicit per-session `GEMINI` pin,
  current full Aurora `/api/v1/aurora/message-rich` path.
- All 48 rows, including degradations, remain in the ledger and review sheets.

## Machine results

| Measure | Direct Gemini | Full Aurora |
|---|---:|---:|
| Complete semantic responses | 48/48 | 45/48 |
| HTTP/transport failures | 0 | 0 |
| HTTP-200 business failures | 0 | 3 |
| Fallback/degraded responses | 0 | 3 |
| Visible response language match | 48/48 | 48/48 |
| Non-degraded language match | 48/48 | 45/45 |
| p50 latency, non-degraded | 10,379 ms | 8,193 ms |
| p95 latency, non-degraded | 20,326 ms | 19,828 ms |
| maximum latency, non-degraded | 32,903 ms | 26,646 ms |

Full Aurora by language:

- Chinese: 22 complete + 2 business failures; all 24 visible responses were Chinese.
- English: 23 complete + 1 business failure; all 24 visible responses were English.

The repaired language contract therefore removed the pre-fix defect where full
Aurora answered 0/24 English inputs in English. That is a language-routing result,
not evidence that the semantic quality is better.

## Failure-inclusive detail

The three product-arm failures were HTTP 200 responses carrying an explicit
user-visible recovery template. They are classified as `BUSINESS_FAILURE`, counted
as fallback/degraded, and kept in the blind-review denominator:

1. `relationship-ambiguity::zh-CN` — 7,733 ms.
2. `concise-request::zh-CN` — 7,703 ms.
3. `ask-before-analysis::en-US` — 7,756 ms.

This exposed an observability defect in the API payload: these responses carried
no `riskFlags`, no `fallbackReason`, and an ordinary `continueReason`, even though
their visible text was the deterministic recovery contract. The evaluation
harness now detects the explicit English/Chinese templates as well as future
`riskFlags`, `agentLoop`, and `aiState` failure signals.

### High-confidence Java path

All three rows reported:

- `runtime=single-pass.v1`;
- `aiState.provider=GEMINI`, `model=gemini-3.6-flash`;
- `aiState.responseSource=REAL_MODEL`;
- `fallbackReason=""`, `riskFlags=[]`;
- approximately 7.7 seconds elapsed.

The exact combination identifies the post-processing empty-segment path rather
than a 30-second Provider timeout:

1. `produceReplyWithinTurn` selects the single-pass branch and calls
   `callWithRetry(...)`.
2. `toReply(...)` calls `cleanSegments(safeAi.segments, recentAurora)`.
3. When the Provider result has no usable segment—empty, `[[SILENCE]]`, or removed
   as a recent/within-turn near-duplicate—`messages.isEmpty()` becomes true.
4. That branch copies only
   `fallbackAuroraResult(...).segments` into `messages`. It does **not** replace
   `safeAi`, so the fallback result's `riskFlags=["FALLBACK_USED"]` and
   `continueReason="provider-recovery-required"` are discarded.
5. The outer `fallbackUsed` boolean is set only by a thrown exception. It remains
   false here, causing `runtimeFallbackReason(...)` to return empty and
   `responseSource(...)` to publish `REAL_MODEL`.

The ~7.7-second cluster is below Gemini's 30-second default request timeout and
has no timeout/failure metadata. It therefore supports “valid call followed by no
usable visible segment” much more strongly than “hard timeout.”

Minimal repair:

- when `cleanSegments(...)` is empty, replace `safeAi` with the complete
  `fallbackAuroraResult(...)`, not only its `segments`;
- propagate `vo.riskFlags`/a typed generation outcome back to
  `produceReplyWithinTurn` before computing `aiState`, setting
  `fallbackUsed=true`, `responseSource=BASIC_RESPONSE`, and a non-empty
  `fallbackReason`;
- longer term, use `StructuredAiService.callObserved(...)` in the single-pass
  branch so provider/parse/empty-output status is typed and retry decisions do
  not depend on exceptions that `StructuredAiService.call(...)` already catches.

## Diagnostic-only signals

The deterministic checker found:

- direct arm: two possible advice-boundary violations and one meta-AI phrase;
- full Aurora: zero possible advice-boundary violations and zero meta-AI phrases.

These are lexical diagnostics only. They are not empathy, naturalness or preference
scores and must not be quoted as a quality win.

## Human gate

The three independently blinded sheets each contain all 48 rows, including the
three business failures:

- `blind-review-R01.csv`
- `blind-review-R02.csv`
- `blind-review-R03.csv`

Reviewers must not receive `unblinding-key.json`. A quality-benefit claim is
permitted only after all three sheets are independently frozen and the fail-closed
scorer passes:

- Full-Aurora preference excluding ties ≥ 0.60;
- Wilson 95% lower bound > 0.50;
- no regression across the six rating dimensions;
- Chinese/English Full-Aurora win-rate gap ≤ 0.10;
- all Provider failures remain in the denominator.

Until then, the defensible statement is:

> The repaired system matched the user’s language in all 48 visible outputs and
> produced 45 complete responses, while transparently recording three degraded
> recovery responses. Human preference evidence is still pending.
