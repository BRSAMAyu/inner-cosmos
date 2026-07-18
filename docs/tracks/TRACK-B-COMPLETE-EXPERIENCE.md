# Track B — Complete Experience & Delivery

> Branch: `codex/track-b-complete-experience`
> Audited implementation base: `97500a385a1fa1a5f0e4c3bbb6e2c1b0c895ec61`
> Branch base: the same current `main` as Track A, containing handoff commit `7e06883`; record its full SHA at start
> Owner: group member B + continuous-loop Coding Agent
> Primary surfaces: React/PWA/Capacitor, local-complete, delivery manifests, E2E, accessibility, performance and Demo.

## 1. Mission

Turn Inner Cosmos from a dense showcase of capabilities into a complete product that a new user can understand, enjoy and remember. The final experience must make Aurora's living behavior, correctable self-understanding and high-quality resonance obvious within minutes, while keeping advanced mechanisms available through progressive disclosure.

The local complete experience is the primary product truth and the safest live-demo path. Academy EKS is a cloud-native proof using the same immutable artifact, not a reason to remove or reduce features.

## 2. Read before implementation

Read completely, in order:

1. `AGENTS.md`, `goal-objective.md`, `对齐文档/README.md`
2. `对齐文档/19-双轨并行完全体收敛与交接计划.md`
3. `对齐文档/20-当前状态重对账与完全体差距基线.md`
4. `对齐文档/09-完全体产品愿景与功能规格.md`
5. `对齐文档/11-完全体UIUX与交互设计规格.md`
6. `对齐文档/16-体验优先的完全体重构策略与产品战役.md`
7. `对齐文档/18-组员与Coding-Agent启动部署交接指南.md`
8. Existing browser screenshots/evidence, `web/**`, `deploy/**`, E2E and current runtime

Run the product before changing it. Observe every five-space route at desktop/mobile widths and record real UX findings rather than trusting old audit prose.

## 3. Owned and forbidden paths

Owned: `web/**`, built `src/main/resources/static/app/aurora/**`, `deploy/**`, relevant scripts and delivery/E2E workflows, `evidence/track-b/**`, `docs/goal/tracks/track-b-status.yml`, `docs/goal/tracks/track-b-integration-requests.yml`.

Do not edit Java, DB migrations, AI Lab, global goal/acceptance/state files, alignment docs 19/20 or root handoff docs. Record backend needs in the integration request file and use typed fixtures/adapters only as an honest temporary bridge.

## 4. Workstream B0 — observe and define the golden journeys

Create a current experience inventory covering all routes, controls, async states, content density, responsive layouts and API dependencies. Then define and test three journeys:

1. **Golden first experience**: enter safely → tell Aurora something meaningful → see living multi-message behavior → finish → see a proposed, correctable memory/portrait insight → understand what happens next.
2. **Longitudinal return**: receive/enter through a WakeIntent → Aurora recalls the right context → user interrupts/corrects → timeline remains coherent → change appears in Inner Cosmos.
3. **Resonance**: review what can be used → compile/preview a Capsule → discover an explainable match → converse → send/receive a slow letter → retain control over boundaries and withdrawal.

Also define recovery journeys for offline, Provider unavailable, expired auth, denied permission, partial stream, conflicting correction and withdrawn data.

## 5. Workstream B1 — product architecture and information hierarchy

- Replace the query-parameter/large conditional-panel orchestration with real client routes and stable deep links.
- Decompose `AuroraApp.tsx` into application shell, route pages, domain hooks/stores and small presentational components. Preserve behavior through characterization tests before large moves.
- Establish a single async/task/error model and eliminate duplicated request/status handling.
- Keep API access typed and centralized. Temporary Track A fixtures must be clearly marked and removable.
- Use progressive disclosure: default view answers “what happened, why it matters, what can I do”; source/Genome/model/debug details live behind intentional expansion.
- Preserve user context when moving between conversation, memory detail, correction, Capsule and letter.

Suggested route model (adapt when evidence supports a better one):

```text
/today
/aurora/:sessionId?
/cosmos/starfield
/cosmos/portrait
/cosmos/review
/resonance/discover
/resonance/capsule/:id
/resonance/conversation/:id
/connections/letters/:id?
/me/settings
/skills/:skillId
```

## 6. Workstream B2 — make the innovation visible

### Aurora stagecraft

- Render planning/thinking as subtle state, not fake chain-of-thought.
- Multi-message output must have intentional spacing and allow stop/interrupt at any point.
- Show when Aurora remembered something through a concise, optional provenance cue.
- Proactive entry explains why Aurora returned and gives postpone/quiet/stop controls without turning the moment into a settings form.
- Relationship/Self evolution is felt through language and continuity; advanced transparency is accessible without presenting Aurora as a deceptive sentient claim.

### Inner Cosmos

- Starfield is the primary emotional visualization; list/table alternatives make it accessible and useful.
- Time, topic, people and change filters create meaningful exploration rather than decorative stars.
- Correction and psychology Skill surfaces appear in context, not as a giant tool dashboard.

### Resonance

- Split Genome authorization/compile, discovery, Capsule detail, conversation and letters into focused steps.
- Matching explanations show meaningful commonality/complementarity and user controls.
- Capsule fidelity is expressed through conversation quality and a compact “why this feels like me” review, not raw internal feature dumps.

## 7. Workstream B3 — design-system completion and content polish

- Consolidate tokens, typography, elevation/glass, motion, icons, illustration and density into reusable primitives.
- Preserve the warm day/dusk/night language and improve visual hierarchy; do not regress to a cold AI console.
- Give every state intentional copy: first-run, empty, loading under 1s/1–3s/>3s, partial, error, retry, offline, permission denial, destructive confirmation and success receipt.
- Audit every clickable control for feedback, disabled reason, focus and recovery.
- Use motion to reveal cause/effect and personality. Respect reduced-motion and avoid continuous decorative work on low-power devices.
- Establish screenshot stories and visual regression for critical breakpoints/themes, including text expansion.

## 8. Workstream B4 — global i18n, Singapore experience and accessibility

- Move every user-visible string out of components into a typed message catalog; no mixed hard-coded Chinese remains on supported routes.
- Complete Chinese and English/en-SG. Localize dates, timezone, relative time, numbers, validation, notifications, safety resources and legal placeholders.
- Set and persist locale; deep links and error recovery keep it.
- Target WCAG 2.2 AA for the core journeys: semantic landmarks/headings, keyboard order, visible focus, accessible names/status, contrast, zoom/reflow, touch targets, reduced motion and non-canvas alternatives.
- Run automated axe plus manual keyboard and screen-reader smoke; fix product-impacting issues, not only tool warnings.

## 9. Workstream B5 — PWA and mobile completion

- Add/verify web manifest, service worker, installability, versioned asset cache, offline app shell, API/network error distinction and safe update flow.
- Never cache sensitive P0 API payloads in a general service-worker cache.
- Make reconnect/resume idempotent for streams and queued user actions; show what is pending.
- Validate Capacitor deep links, secure storage, network/background transitions, microphone and push-permission UX.
- On available real devices, run golden/recovery journeys. Record unavailable iOS signing, APNs/FCM or production domain as human/device gates while completing all simulator/emulator work possible.

## 10. Workstream B6 — local-complete as product truth

Deliver a reproducible local environment that includes PostgreSQL/pgvector, TLS Redis, required application roles and built frontend. Requirements:

- one documented start command, readiness wait, status command and cleanup command;
- explicit `mock` versus `real-provider` mode; production-like acceptance refuses accidental Mock/fallback;
- credentials injected through the process/environment or an ignored local file, never committed or echoed;
- deterministic seed/import/reset for the Demo user story without registering demo initializers in production;
- core Playwright journeys run against this profile from a clean checkout;
- Windows-first operation plus portable Compose commands where practical;
- startup failures name the missing dependency/config and recovery action.

Keep Academy EKS overlays renderable and the same artifact deployable. Do not spend the track rebuilding commercial Singapore infrastructure; record it as later production capability unless needed to preserve workload portability.

## 11. Workstream B7 — demo, experience telemetry and operational handoff

Create an 8—12 minute script with four acts:

1. Aurora is alive: multi-message, interrupt/replan and meaningful return.
2. The system understands me: memory/portrait source, correction and starfield change.
3. Understanding becomes connection: authorized Capsule, explainable match, conversation and slow letter.
4. The product is real: local complete architecture plus a short EKS scale/recovery/observability proof.

Provide deterministic setup, expected visual cues, timing, fallback if a Provider is slow, and cleanup. Add privacy-safe product telemetry for journey completion, perceived understanding feedback, correction, proactive helpfulness and resonance outcome; do not send P0 text.

Prepare a non-author runbook that explains product value before infrastructure commands.

## 12. Backend integration requests

Append every backend dependency to `docs/goal/tracks/track-b-integration-requests.yml`. A request must include user impact, desired typed contract, error/loading semantics, priority and the temporary fixture/adapter. Do not silently hardcode fake data into the production path.

## 13. Verification and evidence

At PR readiness:

- Vitest and TypeScript/Vite production build.
- Playwright golden and recovery journeys against `local-complete`.
- visual regression across key themes/breakpoints; axe and manual keyboard evidence; performance budgets.
- PWA install/offline/update test; Capacitor Android build and all available device/simulator checks.
- Compose clean-start/stop/restart/data-preservation and real-provider smoke with secrets redacted.
- Kustomize base/academy-eks/eks-prod render; relevant manifest policy scans.
- Secret scan and `git diff --check`.
- Evidence index in `evidence/track-b/README.md` and current status YAML.

## 14. Definition of done

Track B is done only when B0—B7 produce a coherent product, not a set of styled panels; all machine-actionable gates pass; local-complete and the Demo are reproducible; device/account-only gates are isolated; temporary fixtures and known integration requests are explicit; the worktree is clean; and a PR can be reviewed without the agent conversation.
