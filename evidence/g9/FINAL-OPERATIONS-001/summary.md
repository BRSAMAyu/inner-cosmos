# FINAL-OPERATIONS-001 — competition operations evidence

> Reconciled: 2026-08-01 (Asia/Shanghai)
>
> Acceptance state: `IN_PROGRESS`
>
> Evidence boundary: one owner-authorized signed release is complete; the full operations pack is not independently exercised

## Machine-checkable operations contract

The repository has one reviewer-facing operational entry point covering signed release, rollback,
privacy-safe incident response, no-fallback Provider failure/recovery, data rights, and disaster
recovery. `docs/operations/operations-contract.yml` binds every scenario to explicit owners,
preconditions, success/STOP gates, recovery actions, sanitized evidence fields and live source
assertions. CI rejects drift and premature global `PASS` claims.

Local contract gates after this reconciliation:

```text
pnpm run operations:test  -> 6 tests, 6 pass, 0 fail
pnpm run operations:check -> 6 scenarios, 16 artifacts, 46 source assertions; status=IN_PROGRESS
pnpm run acceptance:check -> 10 gates, 69 items, 5 human gates, 203 evidence paths; overall goal remains IN_PROGRESS
```

## Failure-inclusive signed-release receipt

Owner authorization selected Apache-2.0, approved `v0.1.0-rc.1`, approved deletion of six fully
merged remote branches, preserved the mentor-material branch, and explicitly deferred history
rewriting.

GitHub Actions run `30684645510` failed its SBOM HIGH/CRITICAL dependency gate on
`CVE-2026-41695` in `org.springframework.data:spring-data-commons:3.5.11`. The release was stopped.
Commit `188b708a220191c34a3f47426363276b16c2526b` imported Spring Data BOM `2025.0.12`, resolving the
affected modules to `3.5.12`. Local `clean verify` passed 1503 tests with 0 failures/0 errors,
31 Docker-gated skips and SpotBugs 0; a fresh Trivy SBOM scan reported 0 HIGH/CRITICAL findings.

GitHub Actions run `30685860220` then passed all required main checks:
`terraform-contract`, `web-contract`, and `verify`. Its `verify` job independently passed clean
verify, the 100,000-row pgvector gate, current-tree and reachable-history secret scans, schema,
SBOM and IaC gates, non-root production smoke, Cosign/provenance round-trip, and final image scan.

The annotated tag `v0.1.0-rc.1` points to that exact commit. GitHub Actions run 30686402705
re-ran the release verification and completed the multi-architecture OCI build, BuildKit SBOM,
max-mode provenance, keyless Cosign signing and signature verification. It created a non-draft
GitHub Pre-release with this immutable identity:

```text
Commit: 188b708a220191c34a3f47426363276b16c2526b
Image:  ghcr.io/brsamayu/inner-cosmos:v0.1.0-rc.1
Digest: ghcr.io/brsamayu/inner-cosmos@sha256:58499fdee146cf2801c6da164bf90e28f961175feebac32f1c2981d9244dad69
Release: https://github.com/BRSAMAyu/inner-cosmos/releases/tag/v0.1.0-rc.1
```

## Repository governance receipt

Six remote branches were deleted atomically only after live object IDs matched the audited refs and
each had zero commits unique to `origin/main`: `codex/capsule-persona-layer`,
`codex/english-demo-i18n`, `codex/track-a-living-intelligence`,
`codex/track-b-complete-experience`, `codex/w0-integration`, and
`codex/windows-final-closure`. Post-delete `fetch --prune` and `ls-remote` showed only `main` and
`feat/run006-aurora-self-understanding`; the retained branch still had two unique mentor-material
commits. No history rewrite or force push occurred.

Main protection is owner-authorized but not yet applied: the local GitHub CLI account is active with
an invalid cached token, so the repository-administration API cannot be called safely. The required
target remains strict `terraform-contract`, `web-contract`, and `verify`, with force-push and branch
deletion disabled. This is an explicit external-admin-authentication gate, not a hidden PASS.

## Remaining before `FINAL-OPERATIONS = PASS`

An independent operator/reviewer must sign off the release receipt and execute the documented
release-to-rollback flow, a privacy-safe incident tabletop plus controlled incident, a
production-faithful Provider failure/recovery drill, a disposable-account data-rights rehearsal,
and restore from an encrypted off-cluster backup. Until all records exist, both the operations
contract and `FINAL-OPERATIONS` remain `IN_PROGRESS`.
