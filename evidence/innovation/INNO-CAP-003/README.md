# INNO-CAP-003 — Owner-visible Capsule sandbox and publish journey

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Experience contract implemented

- The React Resonance space now starts from the owner's mental model: choose the facet, explicitly
  select usable memories, inspect a strict masking preview, compile privately, try typical or custom
  questions, label fidelity problems, then publish or withdraw.
- `LOCAL_ONLY` and `NO_EXTERNAL_PROCESSING` memories are visible as unavailable rather than being
  silently selected. Preview does not publish anything, and initial compilation is always private.
- Owners see the selected Genome version and immutable history. Recompile creates a new version;
  publish is disabled while review is required or no active version exists.
- The isolated sandbox runs the selected compiled Genome without creating a visitor session,
  consuming a public quota or sending content to another person. Crisis input is checked before the
  model call. Every answer states that it is owner-only and not sent elsewhere.
- `LIKE_ME`, `NOT_ME`, `FACT_WRONG`, `TOO_EXPOSED` and `TONE_WRONG` feedback is durable and bound to
  the exact Genome version. Feedback is an explicit proposal signal and never mutates the live
  personality online.
- Publishing retains the AI-resonance identity notice. Withdrawal advances the selected version to
  `WITHDRAWN` and the existing runtime fail-closed behavior stops both new and old sessions.

## Verification

- Focused backend, ownership, correction, conversation and PostgreSQL gate: 51/51 PASS.
- Full Java 21 / Spring Boot 3.5 regression: 762/762 PASS across 116 suites, with zero
  failures, errors or skips.
- PostgreSQL 16 + pgvector: 12 Flyway migrations, 75 application tables and 73 identity columns;
  source schema and foreign-key reconciliation PASS.
- React TypeScript production build PASS; Vitest protocol suite 3/3 PASS.
- Playwright full browser regression: 9/9 PASS. The new journey covers preview → private compile →
  v1 → sandbox → fidelity feedback → publish → withdrawal. Screenshot:
  `resonance-owner-journey.png` (visually inspected; no blocking layout defect found).
- Repository secret scan, acceptance-ledger YAML parse and `git diff --check`: PASS.

## Honest remaining work

This is an executable owner journey, not proof of high-fidelity simulation. The current Genome is
still primarily a compiled persona/style/context artifact; structured facts, behavior policies,
conflicts, examples, scenario-conditioned retrieval, planner/speaker/critics and candidate reranking
remain. Real Provider pairwise, independent blind scoring and the visitor discovery → conversation →
slow-letter continuation are required before G6 can pass.
