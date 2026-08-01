# Inner Cosmos verification snapshot — 2026-08-01

This is a failure-inclusive repository and release-governance checkpoint for competition reviewers.
It supplements, rather than erases, the broader product evidence in the acceptance ledger.

## Verified checkpoint

- Release commit: `188b708a220191c34a3f47426363276b16c2526b` on `main`.
- GitHub Actions run `30685860220`: `terraform-contract`, `web-contract`, and `verify` all succeeded.
- Local Java 21 `clean verify`: 1503 tests, 0 failures, 0 errors, 31 Docker-gated skips, SpotBugs 0.
- Fresh CycloneDX/Trivy scan after the dependency repair: 0 HIGH/CRITICAL Java findings.
- Apache-2.0 is declared by canonical root `LICENSE`, Maven metadata, both READMEs and the web package.

## Failure repaired before release

GitHub Actions run `30684645510` failed the SBOM vulnerability gate on `CVE-2026-41695` in Spring
Data Commons `3.5.11`. No tag was created at that point. Commit `188b708a` imports Spring Data BOM
`2025.0.12`, resolving Commons, Redis and KeyValue to `3.5.12`; the same GitHub SBOM gate passed in
run `30685860220` before the candidate tag was created.

## Published release candidate

- Release: <https://github.com/BRSAMAyu/inner-cosmos/releases/tag/v0.1.0-rc.1>
- Release record: non-draft, `prerelease=true`, published 2026-08-01 14:11:39 +08:00.
- Release workflow: <https://github.com/BRSAMAyu/inner-cosmos/actions/runs/30686402705>
- Image tag: `ghcr.io/brsamayu/inner-cosmos:v0.1.0-rc.1`.
- Immutable digest: `sha256:58499fdee146cf2801c6da164bf90e28f961175feebac32f1c2981d9244dad69`.
- Evidence: multi-architecture build, BuildKit SBOM, max-mode provenance, keyless Cosign signature,
  signature verification, and GitHub Pre-release creation all succeeded.

## Repository cleanup

Six live remote branches were deleted atomically after each was rechecked as fully merged with zero
unique commits. The retained `feat/run006-aurora-self-understanding` branch still had two unique
mentor-material commits. After pruning, the remote exposed only `main` and that retained branch.
No history rewrite, force push, or tag movement occurred.

## Explicit remaining governance gate

Main branch protection is authorized but not yet applied. The required configuration is strict
status checks `terraform-contract`, `web-contract`, and `verify`, with force pushes and branch
deletion disabled. The local GitHub CLI cache currently has an invalid token, so this repository
administration action requires a fresh authenticated owner session. This snapshot does not claim the
protection is active.

## Acceptance boundary

This checkpoint proves repository identity, reproducible gates, dependency repair, branch hygiene,
and a signed release candidate. It does not prove physical-device rehearsal, independent usability,
real AWS apply, qualified legal/psychology review, real-provider quality review, or the remaining
operations and disaster-recovery exercises. Overall product status remains `IN_PROGRESS`.
