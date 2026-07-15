# ACADEMY-LIVE-001 minimal cloud-native loop

Builder result: `PASS` for `ACADEMY-CAPABILITY`, `ACADEMY-WORKLOAD`, and the manifest-control scope of `EKS-WORKLOAD`. Broader Academy reliability remains `IN_PROGRESS`.

Current-session results:

- Sanitized preflight passed with two nodes, 7/7 workload RBAC checks, one GatewayClass, Metrics API, no EBS CSI, and mandatory static-PV selection for the legacy in-tree StorageClass.
- Human LabRole SQS create/send/receive was cleaned up; credential-free Pod STS and SQS probes were denied as expected. No learner credentials were injected into application Pods.
- An immutable ECR image from commit `9537515` ran as two API replicas plus independent worker, scheduler, and one-shot migration roles.
- Static-PV PostgreSQL and disposable TLS Redis became Ready. Migration completed Flyway V3 before long-running roles passed their schema gates.
- Gateway and HTTPRoute reported Accepted/Programmed/ResolvedRefs, and a real TLS Gateway request returned application health `UP`.
- API CSRF bootstrap created a Redis session; four scheduler lease keys and a Redis login rate-limit bucket were observed.
- A live PostgreSQL outbox event was claimed by the worker and reached `PUBLISHED` with exactly one inbox receipt.
- Deleting one API Pod recovered to two Ready replicas, and the same Redis session remained valid through recovery.
- A rolling restart returned to two Ready replicas, and the same Redis session remained valid afterward.
- PDB and HPA `2..6` contracts are installed. Actual HPA scale-out/load behavior was not exercised.

Evidence boundary:

- Provider and OIDC values were non-secret infrastructure-smoke placeholders. No real LLM response quality or real IdP flow is claimed.
- Static hostPath data survives namespace/PV deletion on the selected node but is not durable across node replacement. During the failed first bootstrap, the retained empty database password was reconciled without deleting the directory.
- No node identifier, account, registry endpoint, cluster endpoint, Gateway address, security group, queue URL, ARN, credential, session ID, or key value is recorded here.
- HPA scale behavior, node-loss recovery, provider failure, backup/restore, and production SLO/RPO/RTO remain open.
- Existing Aurora proactive, Self/Constitution/Emergence, relationship, portrait, capsule, matching, starfield, and slow-social semantics were preserved.

