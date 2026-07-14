# Known limitations

- Real Provider evaluation is `BLOCKED_BY_CREDENTIAL_GATE`; no historical or replacement credential was read.
- No real-user P0 conversation or unconsented persona was used.
- The current-production adapter proves source contracts and synthetic event semantics, not live Spring runtime behavior or real model quality.
- Current-production Mock runtime capture and approved redacted historical fixtures are registered but `NOT_RUN`.
- Human pairwise review is pending; the generated CSV is only an import/export template.
- Optional LLM Judge is `NOT_RUN`; Mock fallback cannot masquerade as a Judge result.
- A single completed deterministic Judge is explicitly insufficient to select a winning system.
- Structured Genome, Planner/Speaker, Critics, Compiler and reranker systems remain unavailable.
- No claim is made about real Persona fidelity, Capsule Genome improvement, dual-kernel quality or Psychology Skill effects.
- Spring Boot 3.5, DATA POC and production AI refactors remain outside this package.
