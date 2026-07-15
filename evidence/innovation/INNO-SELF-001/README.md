# INNO-SELF-001 — observable and rollbackable Aurora Self

Builder status: `EVALUATED / IN_PROGRESS`.

This checkpoint protects and completes the previously uncommitted Self/Emergence work:

- immutable version chain with Constitution hash, parent/proposal/rollback edges and a
  single active version;
- candidate → proposal with evidence, counterevidence, impact and rollback target;
- deterministic Constitution/safety/continuity/fidelity sandbox evaluation;
- fail-closed Constitution change and owner-scoped opaque failures;
- explicit user consent before runtime activation;
- forward rollback creates a new version instead of rewriting history;
- React experience explains what Aurora may learn, why, evaluation differences and how
  the user can restore a prior version.

Verification: `SelfEvolutionServiceIntegrationTest` 3/3 and packaged-JAR Playwright Self
journey pass. `AURORA-SELF` remains `IN_PROGRESS` until real dual-core replay/pairwise,
independent review and longitudinal non-implementer experience are complete.
