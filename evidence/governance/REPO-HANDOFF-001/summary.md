# REPO-HANDOFF-001 — secure repository publication and deployment handoff

## Scope

This checkpoint makes the current Inner Cosmos development line safe to publish and gives teammates/Coding Agents a current, executable route into local development, `local-complete`, and AWS Academy EKS.

No credential value, AWS account identifier, cluster endpoint, node name, registry, or Secret payload is recorded here.

## Implemented at sanitized commit

- `319d830738f32894c4e1e733d9fb00cf5092c8a8`
- Replaced the obsolete prototype README with an English complete-product overview and a Chinese entry point.
- Added `README.zh-CN.md` and `对齐文档/18-组员与Coding-Agent启动部署交接指南.md`.
- Replaced stale Java 17 / Boot 3.3 / MySQL production deployment claims in `DEPLOY.md` with the real profile matrix.
- Routed deployment tasks from `AGENTS.md` and the alignment authority index to the new handoff.
- Expanded ignored operator material and current/history secret scanning, including temporary AWS access-key prefixes.
- Made Academy manifest validation genuinely offline by default; live API discovery is now explicit with `-ClusterSchemaDryRun`.
- Repaired two pre-existing unquoted colon-bearing list scalars that made `single-session-state.yml` invalid YAML for strict parsers.
- CI fetches full reachable history and runs both tree and history secret scans.

## Credential-history finding and remediation

- The current tree was already free of usable credentials.
- A redacted scan found one historical Provider credential default repeated across 35 snapshots of `src/main/resources/application.yml` on the unpublished local development line.
- `origin/main` had zero matching reachable snapshots and was not rewritten.
- Only `feat/run006-aurora-self-understanding` was rewritten before first publication. Recognized token material was replaced with `REMOVED_FROM_HISTORY`; no shared remote ref, unrelated local branch, or tag was force-updated.
- The sanitized branch has new commit identities. Old commit IDs from the unpublished local line must not be pushed or used as publication evidence.
- Provider-side revocation/rotation remains a human gate; history scrubbing and scans cannot prove it.

## Verification

| Check | Result |
|---|---|
| `scripts/scan-secrets.ps1` | PASS, 0 findings |
| `scripts/scan-secrets.ps1 -History` | PASS, 0 findings across `HEAD` ancestry |
| Markdown local-link check across both READMEs, DEPLOY and handoff | PASS |
| Stale-baseline/token-prefix check in handoff docs | PASS |
| `git diff --check` before commit | PASS |
| `npm test` | PASS, 60/60 |
| `npm run build` | PASS |
| Maven Wrapper package with JDK 21, tests skipped | BUILD SUCCESS |
| `scripts/local-complete.ps1 -Action Config` with non-secret test-only inputs | PASS |
| `scripts/academy/validate-manifests.ps1` | PASS, 20 resources, 0 forbidden findings, offline |
| `scripts/academy/preflight.ps1 -Mode Offline` | PASS, cleanup PASS, sensitive identifiers false |
| PyYAML parse of acceptance ledger and single-session state | PASS |

The full Java test suite was not repeated because this checkpoint changes documentation, security tooling, CI checkout policy, and offline validation only; frontend tests/build and packaging cover the affected executable surfaces. The last full Java gate remains recorded in `docs/goal/single-session-state.yml`.

## Remote publication contract

Publish only the sanitized `feat/run006-aurora-self-understanding` branch with a normal upstream push. Do not force-push or merge the heavily diverged remote `main` as part of this checkpoint. After pushing, verify the remote branch SHA and rerun the remote-reachable secret scan before recording publication complete.

## Remote publication result

- Status: PASS at `2026-07-16T12:19:04+08:00`.
- Repository: `BRSAMAyu/inner-cosmos`.
- Branch: `feat/run006-aurora-self-understanding`.
- Published checkpoint: `fb1d2e8b9d517f3f7e5a35436cc7331202b795d0`.
- Local HEAD, `git ls-remote`, and GitHub branch API returned the identical SHA.
- The branch was created with a normal upstream push. `main` was not merged, rewritten, or force-pushed.
- Tree and complete HEAD-history scans both passed immediately before publication. Because the remote branch points to the identical commit graph, the published reachable history is the same sanitized graph.
- GitHub accepted the push and warned that three existing FLAC music assets are above the recommended 50 MB size. They remain below GitHub's hard per-file limit; migration/compression or LFS is a separate portability/performance decision, not a hidden publication or secret-safety failure.
