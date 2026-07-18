# Track B Evidence Index

> Track: `codex/track-b-complete-experience` — Complete Experience & Delivery.
> Spec: `docs/tracks/TRACK-B-COMPLETE-EXPERIENCE.md`. Status: `docs/goal/tracks/track-b-status.yml`.

## Workstream B0 — Experience observation (this checkpoint)

| Artifact | What it is |
|---|---|
| [`experience-inventory.md`](./experience-inventory.md) | Route/space-by-space inventory of the live product (desktop + mobile), auth/entry findings, shared primitives worth keeping, screenshot manifest, reproduction steps |
| [`golden-journeys.md`](./golden-journeys.md) | J1 golden-first-experience, J2 longitudinal-return, J3 resonance journeys plus 7 recovery journeys, each with target vs. observed behavior and a LIVE/SOURCE/UNVERIFIED verification tag |
| [`screenshots/`](./screenshots/) | Desktop (1440×900) and mobile (390×844) screenshots of all five spaces on a zero-content account, the registration flow, a live Aurora conversation (thinking/multi-message/settled states), a forced-offline reload, and an expired-auth deep link. See the manifest table in `experience-inventory.md` §6. |
| [`scripts/observe-b0-journeys.mjs`](./scripts/observe-b0-journeys.mjs) | The Playwright script used to produce the above — registers a fresh account, walks all five spaces at both viewports, drives one real conversation turn, and forces the offline/expired-auth recovery states. Must be run with `cwd=web/` (see reproduction steps in `experience-inventory.md` §7). |

### How this evidence was produced

`dev` profile (H2 + Mock AI provider), local `./mvnw.cmd spring-boot:run` after a fresh
`cd web && npm ci && npm run build`, against `http://localhost:8080`. No real provider keys, no
Docker/Testcontainers involved — this is a frontend/UX observation pass, not a backend or AI-quality
evaluation (that is Track A's evidence root, `evidence/track-a/`).

### Key findings carried into B1+ (see `golden-journeys.md` "What this unblocks for B1" for the full list)

1. The React AppShell (`/app/aurora/`) has no registration entry point at all; the only working
   registration form is the legacy static page `/pages/register.html`, unlinked from the new app. This is
   the single largest blocker to the "golden first experience" journey.
2. Registration → AppShell session hand-off works correctly (cookie carries over, no forced re-login).
3. The Aurora conversation experience itself (multi-message pacing, stop/interrupt controls, a visible
   dual-core status signal) is already working well and should be preserved through the B1 refactor, not
   rebuilt.
4. No real routes exist — all navigation is a `?space=` query param on one path, and all five spaces are
   simultaneously mounted in the DOM and toggled with the `hidden` attribute out of one 1292-line
   `AuroraApp.tsx`.
5. Mobile bottom navigation is not fixed/sticky; on the Aurora space it appears after several unrelated
   content blocks, pushing the composer below the fold on first load.
6. No PWA offline shell exists — a forced-offline reload produces a blank browser network-error page.
7. Expired-auth deep links silently drop the intended target and show an unexplained bare login form.
8. The Resonance space ships 11 fully-written sample capsule personas visible to every fresh account;
   whether this is intentional sample content or leaked seed data is unresolved and blocks designing the
   right Resonance first-run state.
9. The People-Discovery list surfaces QA/test accounts (`CSRF Runtime`, `Smoke`, duplicate `Bo Observer`
   test users) to real users — a data-hygiene problem for any local-complete demo.
10. A shared loading/error primitive set (`web/src/loading.tsx`) already implements the spec's 3-tier
    delayed-loading rule and a retry-capable error component — B1's "single async/task/error model" should
    audit for consistent adoption rather than invent a new one.

None of the above were fixed in this pass — B0 is observation-only per the task charter. They are the
starting backlog for B1 (architecture/IA), B2 (visible innovation), B3 (design/content), and B5
(PWA/mobile).
