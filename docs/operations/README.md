# Inner Cosmos competition operations runbook

This is the single operator route for release, rollback, incident response, Provider failure,
data-rights handling and disaster recovery. It complements the classroom
[`DEMO-RUNBOOK`](../demo/DEMO-RUNBOOK.md); it does not replace the product journey or cloud-native
presentation narrative.

Current evidence boundary: **ready for rehearsal, not independently exercised as a complete pack**.
The machine contract in [`operations-contract.yml`](operations-contract.yml) binds every scenario to
the implementation artifacts that make it possible. CI rejects missing scenarios, source drift,
missing stop/recovery gates and a premature `PASS` claim.

Never put credentials, account IDs, endpoints, user text, exports, database dumps, Terraform state,
mobile signing material or private logs in Git evidence. Evidence contains hashes, counts, state
transitions, timestamps and redacted error classes only.

## Operator sequence

1. Name one operator and one reviewer; record environment, Git SHA and start time.
2. Run the scenario preflight. A failed precondition is a **STOP**, not permission to weaken a guard.
3. Exercise only the named disposable or isolated target.
4. Evaluate the declared success gates and recovery path.
5. Record failed attempts as well as successful attempts.
6. Restore the environment and have the reviewer sign the sanitized receipt.

## 1. Release

Owner: `release-owner`. Publishing a tag, image, APK or GitHub Release is an owner-authorized action.

Preflight:

```powershell
git status --short --branch
git fetch origin
git rev-parse HEAD
git rev-parse origin/main
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\scan-secrets.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\scan-secrets.ps1 -History
```

Require a clean reviewed commit; synchronized refs; green `terraform-contract`, `web-contract` and
`verify`; an owner-selected license; and an approved semantic `vX.Y.Z[-suffix]` identity. Then run
the existing `Release signed OCI image` workflow. It rebuilds/tests, publishes a digest-addressed
multi-architecture image, attaches SBOM/provenance and verifies a keyless Cosign signature. Android
release candidates separately run `scripts/mobile/verify-release.ps1` before distribution.

Success: immutable digest recorded, signature verified, SBOM/provenance attached, mobile leakage
gate passed where applicable, and sanitized release notes identify the exact commit and CI run.

STOP on a dirty/divergent ref, failed check, absent owner approval, mutable tag reuse, secret-shaped
output or a debug endpoint. Never move a published tag. Mark the candidate rejected, correct it on a
new commit and publish a new version only after all gates rerun.

## 2. Rollback

Owner: `platform-operator`. First record the exact kube context, namespace, stable digest, candidate
digest and migration compatibility. Abort if the context or last-good digest is uncertain.

The Argo Rollout uses `progressDeadlineAbort`, `maxUnavailable: 0` and immediate failed-canary
scale-down. Observe before acting:

```powershell
kubectl config current-context
kubectl -n inner-cosmos-rollouts get rollout inner-cosmos-api -o wide
kubectl -n inner-cosmos-rollouts get rs,pod -l app.kubernetes.io/name=inner-cosmos -o wide
```

If the automated gate has not already aborted a bad candidate, use the installed Argo Rollouts
plugin to abort, then restore the repository-declared last-good manifest/digest. Verify the failed
ReplicaSet reaches zero, stable replicas remain Ready, `/actuator/health/readiness` passes and one
read-only product probe succeeds.

Database rollback is **expand/contract only**. Do not reverse an irreversible Flyway migration or
restore a production database merely to match an old application image. If schema compatibility is
uncertain, stop traffic promotion, keep the compatible stable image and escalate.

## 3. Incident response

Owner: `incident-commander`. Classify `SEV-0` (privacy/safety/data-loss), `SEV-1` (core journey or
multi-user outage) or `SEV-2` (degraded optional capability). Open a privacy-safe timeline containing
environment, Git SHA, detection source and timestamps—never P0 content.

Triage in order:

1. health/readiness and public edge reachability;
2. current image digest, replicas and recent rollout state;
3. PostgreSQL, Redis, outbox and scheduler health;
4. authenticated `/api/ai/health` for the affected operator's own Provider metadata;
5. sanitized error class, latency and correlation/trace identifiers.

Contain the smallest surface: stop the public tunnel, abort a rollout, disable one optional feature
through its documented switch, or isolate a compromised credential. Suspected secret exposure is an
immediate STOP: rotate/revoke externally before resuming. Restore one change at a time, rerun health
and the core product probes, record user impact and create a follow-up owner/deadline.

For the laptop Demo, preserve data with:

```powershell
.\scripts\demo\stop-public-demo.ps1
```

Use `-DeleteData` only for a deliberately disposable rehearsal after resolving its exact target.

## 4. Provider failure

Owner: `ai-runtime-operator`. Run only against isolated `local-complete`/staging with test-only
credentials and a baseline real-Provider turn. Confirm `/api/ai/health` reports `mockProvider=false`
and `fallbackAllowed=false` before injection.

Inject a reversible failure by blocking the test Provider endpoint or rotating a test-only token to
an invalid value. Do not revoke a production credential merely for a drill. Send one Aurora turn and
observe all of the following:

- a bounded, understandable user-visible degradation/retry state;
- no Mock reply and no silent fallback;
- failure metadata scoped to the current user and logs free of conversation content;
- no duplicate message, memory, outbox or billing side effect.

Restore the endpoint or a newly rotated test secret, re-check `/api/ai/health`, send exactly one
recovery turn, reconcile failed/leased outbox work and record recovery time. A health-only result is
insufficient: evidence must include the visible failure and recovery behavior. This production-faithful
drill is still open and must not be claimed from unit tests alone.

## 5. Data rights

Owner: `privacy-operator`. Use a fresh disposable, consented account—not a public-demo template and
never another user's account.

1. Export through `GET /api/user/export`; verify the archive belongs only to the authenticated user
   and store it outside Git with access control.
2. Correct/forget a memory, archive a capsule or revoke its grant; open the data-rights receipt panel
   and verify content-free derivative receipts.
3. Confirm the source no longer participates in retrieval, matching or persona execution.
4. Delete the disposable account through `DELETE /api/user/account` with password reauthentication.
5. Verify the session is invalid and sanitized counts for memories, embeddings, capsules and receipts
   are zero.

STOP if identity, ownership or export protection is uncertain. Deletion is irreversible; do not
restore deleted P0 data from Demo evidence. Automated ownership, receipt isolation and compiled-
derivative erasure tests support this procedure but do not replace an independent rehearsal.

## 6. Disaster recovery

Owner: `database-operator`. The existing CronJob produces a custom-format PostgreSQL dump and the
kind drill restored 42 seeded rows. That proves in-cluster mechanics, not off-cluster durability.

For the acceptance drill:

1. create a fresh dump and record a SHA-256 without exposing its path or contents;
2. copy it to an encrypted, access-controlled off-cluster/object-store location;
3. download that exact object into a new isolated restore target and verify the checksum;
4. run `pg_restore` only against the isolated target;
5. verify Flyway history, schema inventory and sanitized aggregate counts;
6. record measured RPO/RTO and every failed attempt;
7. destroy only the explicitly verified isolated target.

STOP on a checksum mismatch, an ambiguous target, a live database target or an unapproved backup
export. The commercial Terraform adds RDS backups/PITR and versioned encrypted S3, but no real AWS
copy/apply has occurred. `FINAL-OPERATIONS` and `OPS-RESILIENCE` remain open until restore from the
off-cluster copy is independently observed.

## Evidence receipt

Each rehearsal creates a new append-only evidence directory. Include:

```text
scenario, environment, git_sha, operator_role, reviewer
started_at, ended_at, preflight_result, success_gate_results
failure_attempts, recovery_result, remaining_boundary
```

Do not overwrite older failures. A complete operations pack requires all six scenarios to be
independently exercised; until then the contract remains `IN_PROGRESS`.
