# AWS-ACADEMY-CAP-002 current-session capability preflight

Recorded: 2026-07-15 11:05 Asia/Shanghai

Profile: `academy-eks`

Builder result: `PASS` for capability discovery and cleanup; deployment acceptance remains open.

Verified without persisting infrastructure identifiers:

- Current human LabRole identity and EKS API were valid; Kubernetes reported 2 nodes.
- All seven required create-permission probes passed.
- Gateway API and Metrics API were present.
- One legacy `kubernetes.io/aws-ebs` StorageClass exists, but the EBS CSI driver is absent. It is not accepted as a dynamic storage path; the overlay continues to require a static hostPath PV.
- Human LabRole SQS create/send/receive passed.
- Two credential-free Pods could not call STS or SQS, confirming that SQS is forbidden as an Academy workload dependency.
- Kustomize rendered successfully.
- The random probe namespace, both probe Pods, and the temporary SQS queue were deleted successfully.

Sanitization scan: `PASS`. The evidence contains no account id, ARN, endpoint, cluster name, node name, queue URL, load-balancer hostname, security group, credential, token, or key.

This evidence is current-session-only. It does not prove an Academy workload deployment, dynamic EBS, SQS workload identity, commercial Singapore infrastructure, or real Provider behavior.
