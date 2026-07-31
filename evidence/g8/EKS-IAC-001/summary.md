# EKS-IAC-001 — commercial Singapore Terraform contract

> Date: 2026-07-31 · Region contract: `ap-southeast-1` · Acceptance: G8 `EKS-IAC`
> (`UNASSESSED` → `IN_PROGRESS`) · Evidence class: `STATIC/MOCKED CONTRACT ONLY`

## What is now reproducible

`deploy/terraform/commercial-sg/` defines a separate future-production stack rather than reusing
the constrained AWS Academy profile. The resource graph contains:

- a three-AZ VPC with private application/data subnets, per-AZ NAT paths and private AWS service
  endpoints;
- private-endpoint EKS 1.35 with encrypted managed nodes, control-plane logs, explicit operator
  access and EKS Pod Identity for the application service account;
- private, encrypted Multi-AZ RDS PostgreSQL 16 with forced TLS, RDS-managed master credentials,
  backups/PITR, deletion protection, Performance Insights and enhanced monitoring;
- encrypted three-node Valkey with TLS and automatic failover;
- KMS-encrypted, TLS-only SQS event and dead-letter queues with redrive controls;
- private, versioned, KMS-encrypted, TLS-only S3 storage, immutable ECR, external Secret containers and
  owner-routed CloudWatch alarms;
- resource-scoped runtime permissions for only the declared queues, bucket prefixes, secrets and
  KMS key.
- deny-by-default node egress limited to cluster/data paths, VPC DNS, Amazon Time Sync, the S3
  prefix list, private AWS endpoints and owner-reviewed Provider/OIDC HTTPS CIDRs; `0.0.0.0/0`
  egress is rejected by variable validation and the mock-plan contract.

Terraform state, plans, operator variable files and secrets are ignored. Repository examples contain
placeholders only. LLM, OIDC and Redis values are injected outside Terraform; the RDS master password
is generated and managed by RDS.

## Verification performed

The official Terraform 1.15.8 Windows binary was verified against HashiCorp's published SHA256SUMS.
The AWS provider is locked to `6.55.0` with signed provider checksums for both `windows_amd64` and
`linux_amd64`.

```text
terraform fmt -check -recursive  PASS
terraform init -backend=false    PASS; signed hashicorp/aws v6.55.0
terraform validate -no-color     PASS
terraform test -no-color         PASS; 2 passed, 0 failed
Trivy config HIGH/CRITICAL       PASS; 0 findings across 56 tracked-tree-equivalent targets
```

The mock-plan test asserts the Singapore region pin, private EKS endpoint, constrained node egress,
private AWS service endpoints, encrypted private nodes,
private/encrypted/Multi-AZ RDS, encrypted HA Valkey, exact event-queue-to-DLQ binding, private
versioned object storage, TLS-only queue/object policies, KMS-encrypted control-plane logs, scoped
Pod Identity and non-empty alarm destinations. GitHub Actions also
runs these four checks without AWS credentials. A second negative test proves that an operator
cannot plan with unrestricted `0.0.0.0/0` external egress.

GitHub Actions run
[`30642345403`](https://github.com/BRSAMAyu/inner-cosmos/actions/runs/30642345403) passed
`terraform-contract` (27s), `web-contract` (1m38s) and the full `verify` job (15m23s) at exact
infrastructure commit `41f5190e56691993e0c04937b536ad8d0b85a891`; all three job pages reported
zero annotations. The full CI path independently repeated the Terraform checks and the repository
HIGH/CRITICAL IaC gate before production-image smoke, signing/provenance and final image scanning.

## Claim boundary and remaining work

No AWS credentials, account, backend, network call to AWS APIs, saved real plan or resource apply was
used for this evidence. Therefore this does **not** prove that a Singapore environment exists, that
the chosen account permits every resource, that costs are approved, or that live workload/DR/SLO
acceptance passes.

`EKS-IAC` remains `IN_PROGRESS` until an owner-authorized run records a reviewed real plan, apply,
independent least-privilege review, failure/cleanup outcome and sanitized outputs. Account IDs,
endpoints, ARNs, credentials, secret values, state and plan files must not enter Git or evidence.
