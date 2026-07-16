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

- `ClaimExtractionEvaluationTest` (pure, no Spring) over `claim-extraction-v1.json` (24 annotated
  cases: 15 genuine claims across nine types + a repeated-explicit + an uncertainty, and 9 hard
  negatives: questions, preference-shaped questions, hypotheticals, two reported-speech cases, two
  momentary feelings, a vague non-statement, and an Aurora-only turn). Result: **precision 1.0,
  recall 1.0, typeAccuracy 1.0 (tp 15 / fp 0 / fn 0)** against published thresholds
  (precision ≥ 0.9, recall ≥ 0.7, typeAccuracy ≥ 0.9). RED first (stub extractor → recall 0.0).
  Report: `target/evaluation/claim-extraction-v1-report.json`.
- `ClaimExtractionServiceImplTest` — mock-mode falls back to the deterministic extractor and rejects
  a question in the same slice; a hallucinated Provider result (unknown `HOROSCOPE` type, provenance
  id 999 the user never sent, confidence 1.7) is sanitized down to the one valid VALUE claim with
  confidence clamped to 1.0.
- Full H2 Spring context boots with the new `@Service` (`AuroraStreamControllerTest` green).

## Honest boundary

Slice 1 delivers the extractor, the machine-verifiable claim-precision floor, and a provider-ready,
sanitized service. It does NOT yet persist candidates as `CANDIDATE` `UnderstandingClaim` rows,
expose a confirm/correct endpoint, auto-trigger on session finish, detect conflicts against existing
ACTIVE claims, or propagate confirmed claims downstream — those are Slice 2, reusing the existing
correction/propagation + DataUseGrant + FORGET-tombstone machinery. Real-provider extraction quality,
counter-prompt robustness and blind review remain the human gate
(REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW). The precision floor is deterministic and provider-
independent by design.
