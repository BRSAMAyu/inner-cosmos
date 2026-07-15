# INNO-DUAL-001 — Living Aurora dual-kernel runtime checkpoint

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Shipped runtime

- The production Aurora path now runs an understanding/planning kernel followed by an
  expression/relationship kernel. The speaker receives response decisions and constraints,
  not a request to reproduce hidden reasoning.
- A bounded critic runs only when the plan requests it or observable output checks fail.
  It can repair the final structured response; unauthorized memory expansion, excessive
  bubbles and oversized output are explicit triggers.
- The typed SSE `meta` event exposes only user-safe runtime facts: versioned runtime family,
  relationship move, whether repair occurred and issue categories. Plans and private
  reasoning are not sent to the browser.
- React renders the live `understanding -> composing -> speaking` state and recognizes the
  versioned `dual-kernel.v1` capability family.

## Security regression found by the product E2E

The first packaged run exposed a real authenticated-session race. The controller already
rotated the session ID at login, while Spring Security rotated it again whenever the
request-scoped authentication bridge ran. Concurrent SPA bootstrap requests could therefore
land on different IDs. The browser also retained the pre-login CSRF token.

The fix keeps the one explicit login/register rotation, disables duplicate framework
rotation, clears the pre-authentication CSRF token after login and adds an integration
contract that authenticated requests keep a stable protected session ID.

## Reproducible verification

- `AuroraDualKernelRuntimeTest`: planner -> speaker -> bounded critic ordering and repair.
- `AuroraStreamControllerTest`: SSE exposes `dual-kernel.v1` and safe critic outcome fields.
- `WebSessionSecurityIntegrationTest`: login rotation remains mandatory; subsequent
  authenticated requests do not rotate again.
- React Vitest: 3/3; production TypeScript/Vite build: PASS.
- AI Lab unit suite: 38/38, including same-model single-pass vs planner/speaker harness.
- Packaged-JAR Playwright: 4/4 for interrupt/replan, durable reconnect, WakeIntent and Self.
- Full Java 21 / Spring Boot 3.5 Maven gate: 734/734, including real PostgreSQL/Redis
  Testcontainers contracts; final dual-runtime/security focused gate also passed.

## Honest remaining gate

No approved `REAL_PROVIDER_BASE_URL`, `REAL_PROVIDER_API_KEY` and `REAL_PROVIDER_MODEL`
are present. No real Provider was called, no fallback score was substituted, and no
dual-kernel quality benefit is claimed yet. A completed source-blind pairwise review and
independent product review remain mandatory before this acceptance item can become `PASS`.
