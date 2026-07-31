# Verification snapshot — 2026-07-31

This is an evidence snapshot, not a permanent release certificate. It records the exact boundary
after the competition-readiness and commercial-sg Terraform repair pass at commit
[`41f5190e`](https://github.com/BRSAMAyu/inner-cosmos/commit/41f5190e56691993e0c04937b536ad8d0b85a891)
and the successful
[`Java baseline` run 30642345403](https://github.com/BRSAMAyu/inner-cosmos/actions/runs/30642345403).

## Verified locally

| Gate | Result |
|---|---|
| Backend full suite, JDK 21 | `1503` tests, `0` failures, `0` errors, `31` skipped; Maven `BUILD SUCCESS`. |
| Frontend component/protocol suite | `93` test files, `696` tests passed. |
| Focused repaired regressions | `41` tests passed across claim candidates, Aurora fallback, and token forecast. |
| OpenAPI generated-type gate | Passed. |
| OpenAPI breaking-rule unit gate | `6` tests passed. |
| Acceptance-ledger integrity | Passed: `10` parent gates, `69` acceptance items, `5` human gates, and `199` repository evidence paths parsed and resolved. |
| Commercial Singapore Terraform | Terraform `1.15.8` formatting/schema validation passed; `2/2` mock-plan tests passed, including rejection of unrestricted external egress. |
| Local IaC security scan | Trivy `0.72.0` found `0` HIGH/CRITICAL findings across `56` tracked-tree-equivalent targets after the CI-found egress repair. |
| React/TypeScript production build | Passed; PWA assets and service worker generated. |
| Executable Spring Boot package | Passed; the repackaged JAR and CycloneDX SBOM were generated. |
| Local teacher-demo smoke | Passed: health, demo login/session, and React shell. |
| Local browser product journey | Passed in the in-app browser: login, Aurora reply, memory provenance, resonance dialogue, slow-letter space, privacy/settings, and five-space navigation. A fresh isolated-demo run also switched through the visible language control to `en-SG` and accepted the complete Safety Harbor. |
| Mobile responsive journey | Passed at `390 × 844`; bottom navigation and settings content remained operable. |
| Browser console | `0` application warnings or errors during the accepted journeys. |
| Public-demo Compose parse | Passed with validation-only placeholder environment values. |
| Public-demo PowerShell parse | Passed. |
| Academy schema-version gate | Passed: highest Flyway migration `V35`, manifest expectation `35`, three gated workloads. |
| Academy manifest structural gate | Passed: `22` resources, `0` forbidden findings, `0` missing controls. |
| Current-tree secret scan | Passed with `0` findings. |
| Reachable-history secret scan | Passed with `0` findings. |
| Competition poster source syntax | All four Python generators compiled successfully. |

The backend suite now uses `@Testcontainers(disabledWithoutDocker = true)` consistently. This makes
missing infrastructure explicit as skipped tests instead of misreporting Docker discovery failures
as product-code errors.

The accepted browser journey used the isolated H2 + Mock teacher-demo profile. It proves the local
product interaction and failure-free UI path; it does not prove PostgreSQL/Redis, real-provider,
public-network, multi-user, or physical-device behavior.

## Verified in GitHub Actions with Docker

| Gate | Result |
|---|---|
| Workflow | `terraform-contract`, `web-contract` and `verify` all passed at the exact commit above. |
| Workflow annotations | All three jobs completed with `0` annotations. |
| Credential-free Terraform contract | Passed in `27s`: formatting, provider initialization, schema validation and both secure mock-plan tests, without AWS credentials or resource creation. |
| Backend clean verify, JDK 21 | `292` suites, `1503` tests, `0` failures, `0` errors, `1` external-provider skip; SpotBugs gate passed. |
| PostgreSQL/Redis Testcontainers | Passed against PostgreSQL `16.12` and real Redis-backed integration paths. |
| pgvector contract and scale gate | Contract suite passed; `100,000`-row benchmark measured p95 `1.014 ms` and p99 `1.077 ms` on the CI runner. |
| Source and history credentials | Current-tree and reachable-history scans each passed with `0` findings. |
| Dependency SBOM | Trivy found `0` HIGH/CRITICAL Java vulnerabilities. |
| Infrastructure as code | The HIGH/CRITICAL gate passed across the runtime IaC, including the new commercial-sg Terraform; only explicitly scoped intentional policy-rejection fixtures were excluded. |
| Production-profile image smoke | Passed with `Health=UP`, PostgreSQL 16 + pgvector, database TLS `VERIFY_FULL`, Redis `7.4.2`, Redis TLS `VERIFIED_CA`, Flyway `35`, `0` failed migrations, `89/89` schema tables, `0` demo users, non-root `appuser`, migration role and JDBC outbox worker. |
| Distributed runtime controls | Smoke observed `1` Redis session key, `1` rate-limit key, and `2` scheduler lease keys. |
| Signature and provenance | Cosign `3.0.6` signature and SLSA provenance verified against the generated public key; temporary private key persistence was `False`. |
| Final OCI image scan | Trivy found `0` HIGH/CRITICAL vulnerabilities in both Alpine `3.23.5` and `app/app.jar`. |

This CI evidence closes the Docker-backed integration, production-image smoke, and
signature/provenance machine gates for this commit. It is not evidence of an authorized registry
release, a public classroom tunnel, a real-provider quality review, or a physical-device rehearsal.

## Remaining machine, external, and human gates

| Gate | Status | Reason / next action |
|---|---|---|
| Public HTTPS multi-user journey | `NOT-RERUN` | Requires operator credentials, Docker, tunnel, and current device/browser acceptance. |
| Real-provider semantic quality | `NOT-RERUN` | Requires operator-owned provider keys and the named evaluation harnesses. |
| Operational resilience completion | `IN_PROGRESS` | Provider-failure degradation/recovery and an off-cluster backup restore still need production-faithful drills. Canary rollback and JDBC dead-letter replay are already proven and are no longer listed as missing. |
| EKS Terraform | `IN_PROGRESS` | Credential-free validate/mock-plan and HIGH/CRITICAL scanning now pass. A reviewed real plan/apply, independent least-privilege review and sanitized outputs still require owner authorization. |
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
- Reverified the Singapore safety catalog against MOH, gov.sg, SCDF and SOS primary sources; added
  national mindline `1771` and WhatsApp `6669 1771`, retained SOS `1767` and CareText `9151 1767`,
  excluded legacy/confusable numbers, and rendered WhatsApp resources as `wa.me` rather than
  telephone actions. See the [dated verification record](SINGAPORE-SAFETY-RESOURCE-VERIFICATION-2026-07-31.md).
- Upgraded pinned official GitHub Actions to Node 24 releases and migrated Spring test overrides
  from deprecated `@MockBean` to `@MockitoBean`; the final CI run completed with zero annotations.
- Corrected the governance state from a premature human-gate-only terminal to `IN_PROGRESS`,
  reconciled completed canary/dead-letter evidence, and added a CI gate that rejects invalid
  statuses, inconsistent parent/child states, duplicate IDs and missing repository evidence paths.
- Added a separate `ap-southeast-1` Terraform contract for private EKS and managed services. The
  first full CI run rejected unrestricted node egress as `AWS-0104 (CRITICAL)`; the accepted design
  now uses private AWS endpoints and explicit cluster/data/DNS/NTP/S3/owner-approved HTTPS paths,
  and rejects `0.0.0.0/0` external egress in a negative test.

## Reproduction

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
.\mvnw.cmd test

Push-Location web
npm.cmd run acceptance:check
npm.cmd run api:check
npm.cmd run api:diff:test
npm.cmd test -- --run
npm.cmd run build
Pop-Location

.\scripts\scan-secrets.ps1
.\scripts\scan-secrets.ps1 -History
.\scripts\academy\validate-schema-version.ps1
.\scripts\academy\validate-manifests.ps1

Push-Location deploy/terraform/commercial-sg
terraform fmt -check -recursive
terraform init -backend=false -input=false
terraform validate -no-color
terraform test -no-color
Pop-Location
```

Use the repository's pinned `pnpm@11.9.0` in CI or a clean developer checkout. This workspace used
the already-installed dependency tree and `npm.cmd` script runner because the sandboxed pnpm
wrapper attempted a registry metadata refresh.
