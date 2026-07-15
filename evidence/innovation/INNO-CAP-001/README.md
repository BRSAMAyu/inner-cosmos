# INNO-CAP-001 — Consent-bound capsule runtime withdrawal foundation

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Security and product semantics

- Creating a capsule with no selected memories now means exactly that. The previous implicit
  fallback silently compiled the owner's top five memories; it has been removed.
- Explicit current, owner-bound memory selections create `AUTHORIZED` audit references and the
  capsule stores only the accepted IDs. Foreign, stale or missing rows are not compiled.
- Runtime memory assembly now requires an `AUTHORIZED` reference, current memory status and the
  capsule owner's identity. Legacy capsules that claim memory IDs without audit references fail
  closed instead of trusting the old JSON field.
- Correcting an authorized memory marks its reference `NEEDS_REVIEW`, immediately removes the
  affected capsule from the public plaza and prevents existing sessions from invoking Safety or AI.
- Changing authorization also unpublishes the capsule for review. Archiving clears the active ID
  projection, marks every reference `WITHDRAWN`, and existing sessions fail with
  `CAPSULE_WITHDRAWN` before persisting a visitor message or spending Provider/quota budget.

## Verification

- `CapsuleMatchingTest`: 12/12 PASS, including no-implicit-memory and authorization withdrawal.
- `PersonaChatServiceImplPhaseBTest`: 6/6 PASS, including existing-session withdrawal before AI.
- `PersonaChatServiceImplQuotaTest`: 10/10 PASS.
- `UserCorrectionControllerTest`: 9/9 PASS, including correction-driven unpublish/review.
- `ApplicationFlowTest`: 7/7 PASS; the legacy generic-capsule journey remains functional without
  covert memory authorization.

## Honest remaining work

This is the fail-closed authorization and withdrawal foundation, not the complete Capsule Genome
Compiler. Genome versions, compiled scenario indexes, sandbox “像不像我” controls, an explicit
review/regenerate/republish flow, fidelity/adversarial pairwise, public UI and deletion of all search
derivatives remain. A capsule whose authorization changes is intentionally left `NEEDS_REVIEW`
until that compiler workflow exists. G6 therefore remains `IN_PROGRESS`.
