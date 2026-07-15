# INNO-PSY-001 — Psychology Skill Runtime / First Experience Slice

Date: 2026-07-15
Status: BUILDER_VERIFIED / IN_PROGRESS

## Delivered

- Three versioned, bilingual, L1 non-diagnostic manifests: emotion/needs clarification, values compass, and decision-conflict mapping.
- Authenticated discovery, run history, execution, and owner-scoped revocation APIs.
- Explicit per-run consent and exact scope matching; the runtime only accepts answers entered in the current run and exposes no external tools.
- User-selected retention: discard after session, save a derived result, or mark it as eligible for a later separate profile confirmation.
- Input form payloads are not stored separately. A saved derived result can quote phrases the user entered; discard and crisis escalation store no result JSON, and crisis input receives a randomized redacted fingerprint.
- Crisis input stops ordinary reflection and redirects to the existing local safety-resource path.
- React Skill Studio exposes theory, limitations, version, data/tool access, retention, consent, result alternatives, Aurora continuation, and revocation.

## Verification

| Gate | Result |
|---|---|
| `mvn -Dtest=PsychologySkillRegistryTest,PsychologySkillControllerTest test` | PASS — 3 tests, 0 failures |
| `npm test -- --run` | PASS — 3 tests |
| `npm run build` | PASS — TypeScript + Vite production build |
| `npx playwright test --grep "psychology Skill"` | PASS — real browser consent/run/save/revoke journey |
| `mvn test` | PASS — 780 tests, including PostgreSQL/Flyway and Redis integration |
| `npx playwright test` | PASS — 10 complete Living Aurora browser journeys |
| Visual inspection | PASS — result hierarchy, alternative framing, action, continuation, and revocation are legible |

Visual evidence: `psychology-skill-studio.png`.

## Safety and evidence boundary

The first Skill is informed by affect-labelling research (Lieberman et al., 2007, DOI `10.1111/j.1467-9280.2007.01916.x`; Lieberman et al., 2011, DOI `10.1037/a0023503`). Values and decision Skills use values/autonomy and approach-avoidance concepts as reflection scaffolds, not as validated assessments. Every result says it is reflective rather than diagnostic and offers an alternative interpretation.

## Honest remaining work

- No psychology expert review, non-author human review, or blind-experience result exists yet.
- Aurora can suggest the capability contractually, but suggestion quality and refusal behavior still require an evaluated integration journey.
- The deterministic first slice does not yet provide replay snapshots, disable/rollback administration, comparative no-Skill uplift, or a published evaluator corpus.
- Bilingual manifest copy exists; the current React surface renders `zh-CN` only.
- Therefore `SKILL-RUNTIME` and `SKILL-PRODUCT` remain `IN_PROGRESS`, not `PASS`.
