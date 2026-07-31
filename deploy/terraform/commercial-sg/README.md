# Commercial Singapore Terraform

This directory is the future `commercial-sg` infrastructure blueprint. It is intentionally
separate from `deploy/k8s/overlays/academy-eks`: Academy reuses a teaching cluster in `us-east-1`,
whereas this stack creates owner-controlled production resources only in `ap-southeast-1`.

## What the blueprint contains

- three-AZ VPC, private application/data subnets and one NAT path per AZ;
- private-endpoint EKS 1.35, encrypted managed nodes, control-plane logs and explicit operator access;
- EKS Pod Identity plus resource-scoped SQS, S3, Secrets Manager and KMS permissions;
- private Multi-AZ RDS PostgreSQL 16 with forced TLS, managed master secret, PITR backups,
  Performance Insights and deletion protection;
- TLS/at-rest encrypted three-node Valkey replication group with automatic failover;
- KMS-encrypted SQS queue/DLQ with explicit redrive and TLS-only resource policies;
- private, versioned, KMS-encrypted, TLS-only S3 object storage with retention controls;
- immutable KMS-encrypted ECR, runtime Secret containers and actionable CloudWatch alarms.

Terraform does not write LLM/OIDC/Redis secret values. The owner injects them after provisioning
through an approved secret workflow. The RDS master password is generated and managed by RDS in
Secrets Manager. Terraform state is sensitive and must use the separately bootstrapped encrypted S3
backend; the repository only contains a placeholder backend example.

## Credential-free repository gate

CI pins Terraform 1.15.8 and proves formatting, provider-schema validity and the security/resource graph without an AWS
account. Terraform's official mock-provider test does not create infrastructure and is not an AWS
deployment claim:

```powershell
terraform fmt -check -recursive
terraform init -backend=false -input=false
terraform validate -no-color
terraform test -no-color
```

The mock plan asserts Singapore region pinning, private EKS, encrypted private Multi-AZ RDS,
encrypted HA Valkey, SQS/DLQ, private versioned S3, encrypted private nodes, Pod Identity and alarm
destinations.

## Owner-authorized plan and apply

1. Obtain AWS account, budget, legal/data-residency and maintenance-window approval.
2. Bootstrap an encrypted, versioned Terraform-state bucket and KMS key outside this stack.
3. Copy `backend.hcl.example` and `terraform.tfvars.example` to operator-owned files outside Git.
4. Export short-lived AWS credentials and `TF_VAR_redis_auth_token` in the current process.
5. Run current-tree and reachable-history secret scans.
6. Initialize and produce a saved plan:

```powershell
terraform init -reconfigure -backend-config=<operator-backend.hcl>
terraform plan -var-file=<operator-commercial-sg.tfvars> -out=<operator-plan.tfplan>
terraform show -no-color <operator-plan.tfplan>
```

The plan must receive owner/security review before `terraform apply`. An apply must record sanitized
resource counts, region, Git SHA and failure/cleanup status without account IDs, endpoints, ARNs,
credentials or state. Until that happens, acceptance remains `IN_PROGRESS`; a green mock test proves
the blueprint contract, not a real Singapore environment.
