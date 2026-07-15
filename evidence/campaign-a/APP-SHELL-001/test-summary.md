# APP-SHELL-001 verification summary

Date: 2026-07-15

| Gate | Result | Scope |
|---|---:|---|
| `pnpm test -- --run` | PASS, 15/15 | API-origin, mobile-auth, URL restoration and five-space navigation contracts |
| `pnpm run build` | PASS | TypeScript project build and Vite production bundle |
| `scripts/run-living-aurora-e2e.ps1` | PASS, 11/11 | Packaged-JAR browser journeys across all five spaces and narrow viewport |
| `scripts/run-living-aurora-experience.ps1` | PASS, 1/1 | Scheduler -> durable notification -> SSE -> deep link -> feedback |

The first browser run correctly failed because domain content and action status had become hidden
behind the selected space. The final implementation makes navigation explicit in the tests, keeps
operation feedback global, and treats restored AppShell state rather than the Aurora composer as the
login/reload readiness contract. No assertions were removed or weakened.
