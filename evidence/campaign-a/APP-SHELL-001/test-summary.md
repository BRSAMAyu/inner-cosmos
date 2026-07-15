# APP-SHELL-001 verification summary

Date: 2026-07-15

| Gate | Result | Scope |
|---|---:|---|
| `pnpm test -- --run` | PASS, 17/17 | API-origin, mobile-auth, shell navigation and controlled Aurora conversation contracts |
| `pnpm run build` | PASS | TypeScript project build and Vite production bundle |
| `scripts/run-living-aurora-e2e.ps1` | PASS, 11/11 | Packaged-JAR browser journeys across all five spaces and narrow viewport |
| `scripts/run-living-aurora-experience.ps1` | PASS, 1/1 | Scheduler -> durable notification -> SSE -> deep link -> feedback |
| Focused packaged-JAR Playwright | PASS, 3/3 | Interrupt/replan, durable SSE recovery and mobile offline recovery after conversation extraction |
| Focused packaged-JAR Playwright | PASS, 1/1 | Aurora Self proposal, evaluation, consented activation and rollback after Self extraction |
| Focused packaged-JAR Playwright | PASS, 1/1 | Understanding correction preview, authoritative confirmation and visible replacement |

The first browser run correctly failed because domain content and action status had become hidden
behind the selected space. The final implementation makes navigation explicit in the tests, keeps
operation feedback global, and treats restored AppShell state rather than the Aurora composer as the
login/reload readiness contract. No assertions were removed or weakened.
