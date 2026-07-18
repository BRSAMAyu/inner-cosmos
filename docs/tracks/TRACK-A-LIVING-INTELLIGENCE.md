# Track A — Living Intelligence & Data Truth

> Branch: `codex/track-a-living-intelligence`
> Base: `97500a385a1fa1a5f0e4c3bbb6e2c1b0c895ec61`
> Owner: group member A + continuous-loop Coding Agent
> Primary surfaces: Java backend, AI runtime, data model/migrations, AI Lab and Track A evidence.

## 1. Mission

Make Inner Cosmos's intelligence demonstrably better, not merely more elaborate. The completed track must turn Aurora's living behavior, long-term understanding, Echo Capsule fidelity, psychology skills, and user-controlled data into one coherent, measurable runtime. A real user should feel that Aurora remembers accurately, adapts over time, knows when and how to return, and can create a bounded but highly authentic Echo Capsule.

Do not simplify away proactive behavior, Self/Constitution/Emergence, relationship evolution, multi-message timing, dynamic personality, or capsule simulation. Improve their controllability, observability, quality and consent model.

## 2. Read before implementation

Read completely, in order:

1. `AGENTS.md`, `goal-objective.md`, `对齐文档/README.md`
2. `对齐文档/19-双轨并行完全体收敛与交接计划.md`
3. `对齐文档/20-当前状态重对账与完全体差距基线.md`
4. `对齐文档/08-Aurora生命感与共鸣智能创新架构.md`
5. `对齐文档/09-完全体产品愿景与功能规格.md`
6. `对齐文档/10-完全体系统架构与工程规格.md`
7. `docs/research/innovation-evaluation-spec.yml`
8. `docs/goal/complete-product-acceptance.yml` as read-only context
9. Existing `evidence/innovation`, `evidence/campaign-a`, `ai-lab`, current source and tests

Then replace assumptions with current code/runtime evidence. The plan below is authoritative about outcomes, not about preserving a particular class layout.

## 3. Owned and forbidden paths

Owned: `src/main/java/**`, `src/test/java/**`, `src/main/resources/db/migration/**`, relevant backend configuration, `ai-lab/**`, `evidence/track-a/**`, `docs/goal/tracks/track-a-status.yml`, `docs/goal/tracks/track-a-contract-deltas.yml`.

Do not edit: `web/**`, built frontend assets, `deploy/**`, global goal/acceptance/state files, alignment docs 19/20, root handoff docs. If frontend support is needed, write an executable contract delta instead.

## 4. Workstream A0 — establish a reproducible quality laboratory

Before optimizing models, make comparison trustworthy:

- Inventory current model gateway, dual-kernel path, prompt/harness versions, memory selectors, Self/relationship state and output traces.
- Create versioned scenario identities and split them into development and frozen held-out sets. Include short emotional support, ambiguous need, action request, disagreement, user correction, interrupted response, long-gap return, quiet-hours, changing preference, crisis-safe degradation, capsule fact/style/boundary and data withdrawal.
- Preserve failures and denominators. Record provider/model/version, configuration, temperature, prompt/harness hash, memory inputs, tool/Skill calls, latency, token/cost and fallback status.
- Support ablations: strong single-pass baseline, dual kernel, no memory, lexical-only memory, no Self/relationship, static capsule prompt, long prompt and dynamic Genome.
- Use deterministic/offline gates in normal CI. Real-provider runs are explicit, separately reported and never silently replaced by Mock.

Deliverable: an evaluation command that a non-author can run with environment-injected credentials and a report that shows aggregate results plus individual regressions.

## 5. Workstream A1 — adaptive Living Aurora runtime

### Required behavior

- The planner decides response shape, number of messages, delay/pauses, whether to think further, whether to ask, act, stop or defer, and what memories/Skills are allowed.
- The responder produces the actual relational language. The critic checks factual continuity, user intent, boundary, repetition, dependency-inducing patterns and whether another pass is worth its latency.
- Dual kernel is adaptive, not mandatory. Simple turns should remain fast; high-ambiguity, high-continuity or high-risk turns may spend more budget.
- Interrupt/stop/replan operate on the same durable turn state and preserve exactly-once visible segments across retries and Pods.
- WakeIntent and proactive messages use timezone, quiet hours, relationship/preferences, recency, unresolved needs, risk and fatigue budgets. A user can request more companionship without losing control.
- Self/Constitution/Emergence and relationship evolution remain versioned, inspectable and reversible. They influence behavior but never override consent, safety or user facts.

### Quality loop

Run the frozen development scenarios against real providers, inspect the worst trajectories, change the harness/runtime, rerun, and continue until the registered gate is met. Prefer changes that improve the held-out set too; reject prompt overfitting.

Minimum proposed machine gate (record any justified adjustment before seeing held-out outcomes):

- no P0 privacy/ownership/factual-boundary failure;
- dual/adaptive system is non-inferior to the strong single-pass baseline overall and wins clearly on continuity/proactive/interruption subsets;
- 95th percentile first-visible-message latency and total latency stay within declared demo budgets;
- provider failures are visible, recoverable and never reported as real success;
- blind-review package is ready; human verdict remains a gate until actually completed.

## 6. Workstream A2 — memory and portrait truth engine

- Unify claim ingestion from conversation, explicit profile input, correction, psychology skill and system inference.
- Maintain source spans/IDs, confidence, authority, valid/effective time, contradiction groups, consent scope, retention and downstream usage.
- Consolidate duplicates, supersede stale facts, preserve meaningful changes, decay weak inferences and never decay explicit user assertions as if they were guesses.
- Build task-aware retrieval policies for empathetic reply, proactive return, profile review, capsule compile, matching and psychology Skill. Select a small coherent bundle instead of dumping maximum context.
- Benchmark lexical, vector and hybrid retrieval on hard negative, temporal conflict, paraphrase and privacy cases. Track recall/relevance, contradiction, latency and cost.
- Make correction and withdrawal observable through every downstream projection.

The user-facing portrait must be a proposal the user can understand and correct, not a hidden diagnosis.

## 7. Workstream A3 — high-fidelity Echo Capsule and matching

### Dynamic Genome compiler

- Generate candidate features from authorized trajectories using a provider-backed extractor plus deterministic validation.
- Separate invariant facts, time-sensitive state, style markers, habits, values, boundaries, uncertainty and forbidden material.
- Merge/conflict/rank candidates with provenance. Compile only necessary features per scenario and token budget.
- Use a planner to choose features; use critics for factual consistency, style similarity, temporal consistency, boundary/privacy and disclosure risk.
- Cache only versioned, consent-bound artifacts. Any source correction/withdrawal invalidates affected Genome versions and runtime cache.

### Fidelity evaluation

- Compare static card, naïve long prompt and dynamic Genome on unseen trajectories.
- Measure fact accuracy, style/quirk fidelity, temporal consistency, privacy leakage, boundary compliance, repetition and human preference.
- Include adversarial prompts asking the capsule to reveal private memories, claim to be the real person, ignore boundaries or fabricate gaps.

### Matching

- Construct consent-scoped profile/capsule embeddings plus explicit interpretable features.
- Implement at least similarity, complementarity, exploration and anti-echo-chamber strategies.
- Candidate generation and rerank must respect blocks, safety, data scope and diversity; expose understandable reasons and controls.
- Evaluate relevance, novelty, diversity, privacy and stability, not only cosine similarity.

## 8. Workstream A4 — psychology Skills

Ship at least three coherent, non-diagnostic skills, chosen for the product journey—for example values clarification, needs/emotion reflection, and recurring-pattern review. Each must have:

- versioned manifest and input/output schema;
- source/evidence metadata and last-reviewed date;
- intended use, contraindications and “not diagnosis/treatment” boundary;
- explicit/implicit invocation policy, consent, data use and retention;
- uncertainty-aware result, actionable next step, crisis/escalation path;
- Chinese and English content payloads even though Track B owns presentation;
- unit, adversarial, model-quality and regression evaluation.

Package the material for qualified reviewer sign-off. Do not mark the human review complete yourself.

## 9. Workstream A5 — data derivative graph and erasure propagation

- Introduce a single registry/contract for source data and derivatives: portrait claims, embeddings, prompt snapshots, summaries, Genome versions, match features, analytics, notifications, queues and backup retention.
- A correction/withdrawal emits an idempotent reliable event; each consumer records processing or a replayable failure.
- Online serving must stop using withdrawn data immediately or within a declared, tested bound.
- Backup behavior must be honest: quarantine/expiry and restore-time tombstone replay, not a false claim of instant physical erasure.
- Data export and erasure return an auditable receipt without sensitive payloads.
- Add end-to-end tests for partial consumer failure, retry, duplicate events and restore.

## 10. Workstream A6 — AI observability, security and architecture boundaries

- Add privacy-safe AI spans/metrics: route, provider/model, kernel decision, prompt/harness version, retrieval IDs (not content), Skill IDs, latency, tokens, cost, error/fallback, quality feedback and proactive outcome.
- Add failure injection for timeout, 429, malformed structured output and partial stream; define user-visible degradation.
- Harden public management endpoints in backend configuration while documenting the network/management-port contract Track B must apply in deploy files.
- Define practical domain boundaries with Spring Modulith or ArchUnit. First prevent new cross-domain violations; migrate hotspots incrementally rather than performing an unbounded package rewrite.
- Fix the asynchronous scheduler/Testcontainer shutdown noise so a green suite has trustworthy logs.

## 11. Required contract deltas

Whenever user-visible backend behavior changes, append a stable entry to `docs/goal/tracks/track-a-contract-deltas.yml`. Include fixtures so Track B can integrate without reading Java internals. Never make B depend on undocumented response fields.

## 12. Verification and evidence

At PR readiness:

- Maven full suite on Java 21 with Docker/Testcontainers where required.
- PostgreSQL/Flyway migrate from clean and from the current schema; rollback/compatibility notes for each migration.
- AI Lab unit and frozen offline suites.
- Real-provider development and held-out run with provider_called/fallback_used truth, latency/cost and failure ledger.
- Secret scan and `git diff --check`.
- Evidence index in `evidence/track-a/README.md` and current status YAML.

## 13. Definition of done

Track A is done only when A0—A6 are implemented or explicitly proven unnecessary, machine gates pass, real-provider evidence exists, remaining human gates are clearly isolated, contract deltas are complete, the worktree is clean, commits are reviewable, and a PR can be reviewed without reconstructing the agent's chat history.
