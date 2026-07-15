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
- Every run binds to a durable release id and immutable manifest hash. Admin-only lifecycle gates machine evaluation, real human review, publication, emergency disable, and rollback.
- Aurora can return an explainable `SUGGEST_ONLY` card from explicit wording. It does not create a run, read other memory, or bypass the second consent inside Skill Studio; high-risk input receives no Skill suggestion.

## Verification

| Gate | Result |
|---|---|
| Psychology focused Spring tests | PASS — 6 tests, including release lifecycle and suggest-only zero-write contract |
| `python -m unittest discover -s tests -v` in `ai-lab` | PASS — 41 tests, including 15 Psychology scenarios |
| `python -m evals.cli.main psychology --output ../evidence/innovation/INNO-PSY-001` | PASS — 3 manifests × ordinary/ambiguity/adversarial/crisis/i18n contract |
| `npm test -- --run` | PASS — 3 tests |
| `npm run build` | PASS — TypeScript + Vite production build |
| `npx playwright test --grep "psychology Skill"` | PASS — real browser consent/run/save/revoke journey |
| `mvn test` | PASS — 783 tests, including PostgreSQL/Flyway, Redis, release lifecycle and suggest-only integration |
| `npx playwright test` | PASS — 10 complete Living Aurora browser journeys |
| Visual inspection | PASS — result hierarchy, alternative framing, action, continuation, and revocation are legible |

Visual evidence: `psychology-skill-studio.png`.
Suggestion evidence: `aurora-suggest-only.png`.
Machine report: `psychology-contract-report.json`.

## Safety and evidence boundary

The first Skill is informed by affect-labelling research (Lieberman et al., 2007, DOI `10.1111/j.1467-9280.2007.01916.x`; Lieberman et al., 2011, DOI `10.1037/a0023503`). Values and decision Skills use values/autonomy and approach-avoidance concepts as reflection scaffolds, not as validated assessments. Every result says it is reflective rather than diagnostic and offers an alternative interpretation.

## Honest remaining work

- No psychology expert review, non-author human review, or blind-experience result exists yet.
- The automated admin lifecycle test uses a clearly synthetic review note; it proves gating and audit fields, not that expert review occurred.
- Aurora can suggest the capability contractually, but suggestion quality and refusal behavior still require an evaluated integration journey.
- Run snapshots now bind release id and manifest hash; disable/rollback is exercised. Comparative no-Skill uplift remains unmeasured, and the published contract corpus is synthetic rather than evidence of clinical effectiveness.
- Bilingual manifest copy exists; the current React surface renders `zh-CN` only.
- Therefore `SKILL-RUNTIME` and `SKILL-PRODUCT` remain `IN_PROGRESS`, not `PASS`.
