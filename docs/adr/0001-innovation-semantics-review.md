# ADR-0001: Preserve innovation semantics during W0 safety hardening

- Status: PROPOSED
- Date: 2026-07-14
- Owners: Product and architecture owners
- Supersedes: pending formal mapping
- Superseded by: none
- Related work packages: M1-SEC-001, M1-SEC-002, M1-SEC-003, M1-BASE-001

## Context

Earlier M1 language treated Aurora as only a background Behavior Policy, constrained proactive behavior to a permanently minimal budget, and assumed static/limited Capsule interaction and conservative post-M1 innovation. The product owner has superseded those restrictions pending a dedicated Innovation ADR.

## Decision

While this ADR is proposed, W0 changes are limited to Secret/configuration safety, object ownership, fail-closed behavior, dependency upgrade, and regression proof. Existing Aurora proactive, Aurora Self / Constitution / Emergence, personality and relationship evolution, user portrait and psychological modeling, Capsule dynamic dialogue/persona, slow letter, starfield, matching, and related domain capabilities must remain intact.

This record does not decide the final innovation architecture. It prevents safety work from silently deciding that product question.

## Alternatives

- Continue the earlier conservative product restrictions: rejected by the current owner direction.
- Redesign innovation behavior during W0: rejected because it would mix product redesign with security closure.

## Consequences

Security fixes must be narrow and preserve product semantics. A later Innovation ADR must define product behavior, safety success/harm metrics, user control, and rollout strategy.

## Security/Privacy Impact

No safety or privacy control is relaxed. Ownership, Secret management, production fail-closed behavior, crisis boundaries, audit, consent, and anti-abuse controls remain mandatory. If a security fix truly conflicts with an innovation capability, implementation pauses for owner review.

## Verification

- Ownership and production configuration tests remain green.
- Full regression test count remains above the 613 baseline with zero failures, errors, or skipped tests.
- Diff review confirms no named innovation module or domain model was removed or disabled.

## Rollback

This proposed guardrail may only be replaced by an accepted Innovation ADR. Do not restore superseded product restrictions by editing implementation code.

## Evidence

See `evidence/m1/M1-SEC-001/`, `M1-SEC-002/`, and `M1-SEC-003/` for W0 safety evidence. Product validation evidence belongs to the future Innovation ADR.
