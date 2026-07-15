# AWS-ACADEMY-CAP-001 capability probe summary

Recorded: 2026-07-15 Asia/Shanghai

Sources read:

- `D:/code/NUS_lab/ENVIRONMENT.md`
- `D:/code/NUS_lab/LAB_GUIDE.zh-CN.md`
- `D:/code/NUS_lab/L1-lab/L1-lab/instructions-eks.md`
- `D:/code/NUS_lab/L2-lab/L2-lab/instructions-eks.md`
- `D:/code/NUS_lab/L3-lab/L3-lab/instructions-eks.md`
- `D:/code/NUS_lab/L3-lab/L3-lab/eks-note.md`
- `D:/code/NUS_lab/L4-lab/lab-4/instructions-eks.md`

Sanitized live results:

- AWS temporary identity: PASS for the current session.
- Kubernetes API: PASS; server `v1.34.9-eks-8f14419`; 2 nodes.
- SQS using the current Learner Lab user role: create, send, receive, delete PASS.
- Temporary SQS queue cleanup: PASS.
- Credential-free AWS CLI Pod STS: NO_CREDENTIALS_OR_DENIED.
- Credential-free AWS CLI Pod SQS ListQueues: NO_CREDENTIALS_OR_DENIED.
- Temporary probe Pod cleanup: PASS.
- StorageClasses: 0.
- EBS CSI driver: absent.
- Metrics API: present.
- GatewayClass count: 1.
- Kubernetes authorization reported create permission for Deployment, StatefulSet, PersistentVolume, PodDisruptionBudget, HorizontalPodAutoscaler, NetworkPolicy, and CustomResourceDefinition.

Interpretation:

- SQS is accessible to the current human/LabRole session, but no safe workload identity path was observed for EKS Pods. It is not an allowed `academy-eks` runtime dependency.
- Static hostPath storage can demonstrate Pod restart persistence only. It cannot support node replacement durability or production recovery claims.
- All unprobed AWS managed services remain `UNVERIFIED`.

Privacy and cleanup:

- No credential, account id, cluster endpoint, queue URL, node name, security group id, token, or secret value is included in this evidence.
- The temporary queue and temporary Pod were deleted successfully.
