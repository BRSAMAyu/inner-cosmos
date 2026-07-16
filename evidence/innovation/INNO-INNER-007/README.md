# INNO-INNER-007 — Automatic user-model claim extraction (candidate pipeline, precision gate)

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-16. Campaign B.

## Problem

doc-16 requires an automatic user-modeling pipeline: `conversation evidence → claim candidate
extraction → normalization → provenance + confidence → conflict/uncertainty → user confirm/correct →
propagation`, evaluated by **claim precision** on an annotated set. Before this checkpoint, every
`UnderstandingClaim` was created only by an explicit user correction (`UserCorrectionServiceImpl`);
there was no path from conversation to claim candidates. `CorrectionTargetingEvaluationTest` measures
correction *targeting* precision, not claim *extraction* precision.

## Implemented (Slice 1 — extractor + precision gate + provider-ready service)

- `com.innercosmos.ai.claim`:
  - `ClaimTypes` — the eleven doc-16 user-model dimensions (事实/偏好/价值/关系/情绪模式/习惯/表达风格/
    需求/边界/变化趋势/不确定性) as string constants matching the free-string claim_type column.
  - `ClaimAuthority` — the auto-extraction evidence tiers from the Campaign B authority rule
    (`REPEATED_EXPLICIT > REPEATED_BEHAVIOR > SINGLE_EXPLICIT > MODEL_INFERENCE`). The top tiers
    (`USER_CORRECTION`, `USER_CONFIRMED`) require an explicit user act and stay owned by the
    correction service — auto-extraction can never mint an authoritative fact.
  - `ClaimCandidate` — a proposal carrying claimType, value, authorityLevel, calibrated confidence,
    `provenanceMessageIds` (never empty), the matched evidenceText and an `uncertain` flag.
  - `ClaimCandidateExtractor` — deterministic, precision-first engine. Rejects questions,
    hypotheticals, reported speech (others' words) and momentary one-off feelings; emits typed
    candidates only for explicit first-person self-statements; requires a recurrence marker AND an
    emotion word for EMOTION_PATTERN; escalates a repeated preference to REPEATED_EXPLICIT and merges
    provenance across messages.
- `ClaimExtractionService`/`ClaimExtractionServiceImpl` — wraps the deterministic engine in the
  mock-safe `StructuredAiService` harness (a real Provider is attempted when configured; the
  deterministic engine is the fallback that runs in test/mock mode). Every candidate — provider or
  deterministic — is **sanitized**: unknown claim types are dropped, provenance ids that do not
  reference a message in this conversation are stripped, empty-provenance candidates are dropped, and
  confidence is clamped to [0,1]. An unreliable Provider therefore cannot fabricate a claim, mislabel
  a type, or attach a claim to a message the user never sent.

## Verification (Java 21, offline)

- `ClaimExtractionEvaluationTest` (pure, no Spring) over `claim-extraction-v1.json` (26 annotated
  cases: 17 genuine claims across FACT/PREFERENCE/VALUE/HABIT/NEED/BOUNDARY/EMOTION_PATTERN/TREND/
  UNCERTAINTY + a repeated-explicit, and 9 hard negatives: questions, preference-shaped questions,
  hypotheticals, two reported-speech cases, two momentary feelings, a vague non-statement, and an
  Aurora-only turn). Result: **precision 1.0, recall 1.0, typeAccuracy 1.0 (tp 17 / fp 0 / fn 0)**
  against published thresholds
  (precision ≥ 0.9, recall ≥ 0.7, typeAccuracy ≥ 0.9). RED first (stub extractor → recall 0.0).
  Report: `target/evaluation/claim-extraction-v1-report.json`.
- `ClaimExtractionServiceImplTest` — mock-mode falls back to the deterministic extractor and rejects
  a question in the same slice; a hallucinated Provider result (unknown `HOROSCOPE` type, provenance
  id 999 the user never sent, confidence 1.7) is sanitized down to the one valid VALUE claim with
  confidence clamped to 1.0.
- Full H2 Spring context boots with the new `@Service` (`AuroraStreamControllerTest` green).

## Implemented (Slice 2 — persistence, auto-trigger, confirm-to-ACTIVE, owner scoping)

- `ClaimCandidateService`/`ClaimCandidateServiceImpl` persist candidates into the existing
  `tb_understanding_claim` table with `status=CANDIDATE` and `sourceType=AUTO_EXTRACTION`, so they
  coexist with — but are never mistaken for — authoritative `ACTIVE` claims (no schema change; the
  free-string status/sourceType/authorityLevel/confidence/evidenceRefs columns suffice). Staging is
  idempotent per claim key (an existing CANDIDATE is refreshed, not duplicated); version advances past
  the highest existing version for the key to respect the `(user_id, claim_key, version)` unique index;
  provenance message ids are stored in `evidenceRefs` and the value JSON.
- `ClaimCandidateExtractListener` stages candidates automatically on `DialogFinishedEvent`
  (AFTER_COMMIT, async, failure-swallowing) — mirroring `MemoryExtractListener` but retaining the
  per-message provenance that memory extraction discards.
- `confirmCandidate` promotes a candidate to an authoritative claim through
  `UserCorrectionService.confirm`, so the confirmed claim inherits impact preview and the existing
  downstream propagation (Aurora retrieval, portrait, memory, capsule sync); the candidate row is
  then marked `CONFIRMED`. `dismissCandidate` marks `DISMISSED` (audit, not hard delete). This honors
  the authority rule: auto-extraction never writes an `ACTIVE` claim — the user's confirmation does.
- `ClaimCandidateController` (`/api/aurora/claims/candidates`) exposes owner-scoped list / confirm /
  dismiss so the user can see what Aurora inferred (with provenance) and accept or reject it.

## Verification — Slice 2 (Java 21, H2)

- `ClaimCandidateServiceImplIntegrationTest` — staging derives one PREFERENCE candidate (a question in
  the same session is not turned into a claim), carries provenance, and re-staging is idempotent (one
  CANDIDATE row); confirm promotes to an `ACTIVE` `USER_CORRECTION`-authority claim and retires the
  candidate; a foreign user can neither list, confirm, dismiss, nor stage from the owner's session.
- `ClaimCandidateControllerTest` — list/confirm/dismiss over the real DispatcherServlet with session
  auth; a foreign user gets `NOT_FOUND` on confirm and an empty list; confirm/dismiss remove the item
  from the pending list.
- Full Java regression 833/833 green (no regression from the new dialog-finish listener).

## Implemented (Slice 3 — web review surface, "看懂自己")

- `web/src/components/ClaimCandidateReview.tsx` — a controlled, presentational panel (same idiom as
  `UnderstandingCorrection`) that shows each candidate with a friendly Chinese dimension name, the
  extracted value, the concrete evidence phrase, a confidence bar, how many conversation moments
  support it, an authority-tier label, and `uncertain` / `already-in-your-understanding` badges. The
  user confirms ("对，就是我") or dismisses ("不太是我", with an inline confirm). Renders nothing when
  there are no candidates.
- `web/src/api.ts` — hand-written `ClaimCandidate` type + `claimCandidates()` / `confirmClaimCandidate(id)`
  / `dismissClaimCandidate(id)` mirroring the `/api/aurora/corrections` idiom (these endpoints are
  outside the governed `/api/v1` OpenAPI slice, like corrections, so no contract change).
- `web/src/AuroraApp.tsx` — loads candidates in bootstrap (append-only, non-breaking to the indexed
  `Promise.all`), renders the review panel at the top of the Cosmos space, and wires confirm/dismiss:
  confirm removes the candidate, prepends the new ACTIVE claim and refreshes corrections; dismiss
  removes it. `web/src/styles.css` adds the dark-theme card styling in the existing token language.

## Verification — Slice 3 (frontend)

- `ClaimCandidateReview.test.tsx` — 6 Vitest cases: empty → renders null; friendly type/value/evidence/
  confidence/provenance shown; confirm fires `onConfirm(id)`; dismiss requires inline confirm then
  fires `onDismiss(id)`; busy state disables; uncertain + already-known badges surface.
- `tsc --noEmit` clean; full web suite **66/66** (was 60); production `vite build` succeeds.
- The end-to-end data path (session finish → staged candidates with provenance → list/confirm/dismiss
  API) is proven by the Slice-2 backend integration + controller tests.

## Honest boundary

Slices 1–3 deliver the extractor, the machine-verifiable claim-precision floor, a provider-ready
sanitized service, candidate persistence, automatic staging on session finish, confirm-to-ACTIVE
promotion reusing the correction propagation, and the web review surface. NOT yet done: a live browser
screenshot of the populated panel against a running backend with a seeded conversation (component +
API paths are covered by tests; the live visual with real data remains a manual/human check);
semantic conflict detection against existing ACTIVE claims (needs embeddings/real-provider judgement);
extraction on real long conversations at scale; entity/time/relation normalization beyond value +
provenance; and an English (en-SG) locale for this panel (currently Chinese-only, matching the app
default). Real-provider extraction quality, counter-prompt robustness and blind review remain the
human gate (REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW). The precision floor is deterministic and
provider-independent by design.
