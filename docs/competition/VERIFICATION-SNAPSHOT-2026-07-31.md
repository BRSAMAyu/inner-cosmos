# Verification snapshot — 2026-07-31

This is a local workspace snapshot, not a permanent release certificate. It records the exact
evidence boundary after the competition-readiness repair pass.

## Verified in this workspace

| Gate | Result |
|---|---|
| Backend full suite, JDK 21 | `1503` tests, `0` failures, `0` errors, `31` skipped; Maven `BUILD SUCCESS`. |
| Frontend component/protocol suite | `93` test files, `696` tests passed. |
| Focused repaired regressions | `41` tests passed across claim candidates, Aurora fallback, and token forecast. |
| OpenAPI generated-type gate | Passed. |
| OpenAPI breaking-rule unit gate | `6` tests passed. |
| React/TypeScript production build | Passed; PWA assets and service worker generated. |
| Executable Spring Boot package | Passed; the repackaged JAR and CycloneDX SBOM were generated. |
| Local teacher-demo smoke | Passed: health, demo login/session, and React shell. |
| Local browser product journey | Passed in the in-app browser: login, Aurora reply, memory provenance, resonance dialogue, slow-letter space, privacy/settings, and five-space navigation. |
| Mobile responsive journey | Passed at `390 × 844`; bottom navigation and settings content remained operable. |
| Browser console | `0` application warnings or errors during the accepted journey. |
| Public-demo Compose parse | Passed with validation-only placeholder environment values. |
| Public-demo PowerShell parse | Passed. |
| Academy schema-version gate | Passed: highest Flyway migration `V35`, manifest expectation `35`, three gated workloads. |
| Academy manifest structural gate | Passed: `22` resources, `0` forbidden findings, `0` missing controls. |
| Current-tree secret scan | Passed with `0` findings. |
| Reachable-history secret scan | Passed with `0` findings. |
| Competition poster source syntax | All four Python generators compiled successfully. |

The backend suite now uses `@Testcontainers(disabledWithoutDocker = true)` consistently. This makes
missing infrastructure explicit as skipped tests instead of misreporting Docker discovery failures
as product-code errors. CI still executes these tests when Docker is present.

The accepted browser journey used the isolated H2 + Mock teacher-demo profile. It proves the local
product interaction and failure-free UI path; it does not prove PostgreSQL/Redis, real-provider,
public-network, multi-user, or physical-device behavior.

## Not live-verified in this workspace

| Gate | Status | Reason / next action |
|---|---|---|
| PostgreSQL/Redis Testcontainers | `BLOCKED` | Docker daemon was unavailable; rerun the full Maven gate with Docker Desktop healthy. |
| Production image smoke and signature | `BLOCKED` | Requires Docker daemon and the CI-style image workflow. |
| Public HTTPS multi-user journey | `NOT-RERUN` | Requires operator credentials, Docker, tunnel, and current device/browser acceptance. |
| Real-provider semantic quality | `NOT-RERUN` | Requires operator-owned provider keys and the named evaluation harnesses. |
| Android physical-device acceptance | `HUMAN-GATED` | Requires a freshly built APK, a real device, permissions, lifecycle, and network-transition checks. |
| Academy EKS / commercial production | `HUMAN-GATED` | Requires live accounts, current credentials, deployment windows, and owner sign-off. |
| Independent UX, accessibility, legal/privacy review | `HUMAN-GATED` | Automated tests are supporting evidence, not substitutes. |

The production web build currently reports one non-blocking optimization warning: the main
minified JavaScript chunk is about `739 kB` (`239 kB` gzip), above Vite's `500 kB` advisory
threshold. This is a performance-improvement candidate, not a functional or build failure.

## Repairs included in this snapshot

- Restored CSRF and distributed rate limiting in the public competition runtime; classroom
  capacity is now expressed as bounded quotas rather than a security bypass.
- Restored semantic memory embedding as the public-demo default when the already-required local
  DashScope/Qwen credential is present. `-DisableMemoryEmbedding` is an explicit recovery mode.
- Kept the proactive background job disabled only in the 30-user public-demo runtime because its
  current full-profile scan can create uncontrolled background provider load; formal product
  profiles retain the feature. This is a disclosed capacity boundary, not a product-wide removal.
- Fixed calendar-month token forecasting and state-aware Chinese Aurora outage feedback.
- Preserved the precision-first six-message threshold for expression-style claims and corrected
  stale tests that still expected the old two-message demo shortcut.

## Reproduction

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
.\mvnw.cmd test

Push-Location web
npm.cmd run api:check
npm.cmd run api:diff:test
npm.cmd test -- --run
npm.cmd run build
Pop-Location

.\scripts\scan-secrets.ps1
.\scripts\scan-secrets.ps1 -History
.\scripts\academy\validate-schema-version.ps1
.\scripts\academy\validate-manifests.ps1
```

Use the repository's pinned `pnpm@11.9.0` in CI or a clean developer checkout. This workspace used
the already-installed dependency tree and `npm.cmd` script runner because the sandboxed pnpm
wrapper attempted a registry metadata refresh.
