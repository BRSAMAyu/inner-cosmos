# AWS Academy EKS 约束与双轨部署架构

> 文档性质：L1 部署环境约束补充规范
> 状态：AUTHORITATIVE
> 形成时间：2026-07-15
> 证据来源：`D:/code/NUS_lab` 四个 EKS Lab、存储说明，以及 2026-07-15 当前 Learner Lab 的无敏感信息实时探针

## 0. 核心结论

AWS Academy Learner Lab 不是普通 AWS 生产账户的低配版本，而是一个预置资源、临时身份、受限 IAM、生命周期短且服务权限不完整的教学环境。Inner Cosmos 不应只设计一套部署然后期望它同时满足课程展示和新加坡商用。

最终采用三种明确配置：

| Profile | 目的 | 运行位置 | 质量承诺 |
|---|---|---|---|
| `local-complete` | 展示完整产品、真实 AI 效果和最佳 UIUX | 开发机 Docker Compose/kind 或团队可控环境 | 功能最完整，演示主路径 |
| `academy-eks` | 证明 Kubernetes、可靠性、扩缩容与云上部署能力 | AWS Academy 预置 EKS，固定 `us-east-1` | 可重建、可降级的课程 profile，不冒充生产 |
| `commercial-sg` | 未来真实发布与商用 | 自有 AWS 账户 `ap-southeast-1` | RDS/ElastiCache/SQS/Secrets/IaC/灾备完整生产目标 |

课程展示采用“双轨同场”：本地完整体负责产品惊艳度，Academy EKS 负责云原生证据。两者使用同一应用制品和领域代码，只允许基础设施适配器、数据规模、模型配置和演示数据生命周期不同。

## 1. Lab 文档明确的环境事实

四个 EKS Lab 共同证明：

- 只能使用 Learner Lab 指定的 `us-east-1`，不能把课程集群当作新加坡部署证据。
- 单次 Lab 会话约 4 小时；每次重启会话都需要刷新 Access Key、Secret 和 Session Token。
- EKS 集群和安全组由课程预置；教程默认名只是示例，实际参数必须运行时发现或从本地非 Git 配置注入。
- ECR 镜像推送和节点拉取是课程支持路径。
- Envoy Gateway、Gateway API、HTTPRoute 与 `Service type=LoadBalancer` 是课程外部入口路径。
- Deployment、Service、ConfigMap、Secret、StatefulSet、PV/PVC、探针、requests/limits、preStop 和 PDB 是教学覆盖能力。
- Learner Lab 通常无法创建或附加 EBS CSI 所需 IAM Role，因此没有正常 EBS 动态卷路径。
- Lab 使用单节点静态 `hostPath` PV；节点被替换、标签丢失或会话环境变化时，数据会丢失或不可达。

Lab 文档没有证明 RDS、ElastiCache、Secrets Manager、Route53、ACM、CloudWatch 托管栈、IRSA/Pod Identity、Terraform 创建 VPC/EKS/IAM 或新加坡区域资源可用。所有未证明能力必须标记 `UNVERIFIED`，不能靠普通 AWS 经验推断。

## 2. 2026-07-15 实时能力探针

本次探针没有输出或持久化任何凭据、账户号、集群端点、队列 URL、节点名或密钥。

| 能力 | 结果 | 准确含义 |
|---|---|---|
| 临时 AWS CLI 身份 | PASS | 当前 4 小时会话仍有效，不代表下次会话有效 |
| EKS API / nodes | PASS | 当前集群可连接，2 个 worker 可见 |
| SQS 创建/发送/接收/删除 | PASS | 当前 Learner Lab **用户角色**允许 SQS 数据面和临时队列操作 |
| 无凭据 Pod 调用 STS | FAIL | Pod 没有可用 Workload Identity/IRSA 身份 |
| 无凭据 Pod `ListQueues` | FAIL | SQS 不能直接作为当前 EKS Pod 的可靠运行依赖 |
| StorageClass | 0 | 无动态存储类 |
| EBS CSI Driver | absent | 不能使用正常 EBS 动态卷 |
| Metrics API | PASS | 可以实施和演示基于资源指标的 HPA |
| GatewayClass | 1 | Gateway API 控制器已存在；具体共享规则仍需环境探测 |
| K8s create 权限 | PASS | Deployment、StatefulSet、PV、PDB、HPA、NetworkPolicy、CRD 当前均允许创建 |

最重要的判定是：

> **SQS 在账户控制面可用，但没有安全的 Pod 身份链。因此 `academy-eks` 不得依赖 SQS；除非未来用一次新的实时 probe 证明课程允许创建并绑定最小权限 Workload Identity。**

禁止把人的临时 AWS Access Key 注入 Deployment 来绕过这个限制。它会在 4 小时后失效、难以轮换，并扩大凭据泄露面。

## 3. 能力分类与替代方案

| 商业目标能力 | Academy 状态 | `academy-eks` 方案 | `commercial-sg` 方案 |
|---|---|---|---|
| EKS | 已预置、可连接 | 复用指定集群与独立 namespace | Terraform 创建受控 EKS |
| Region | 固定 `us-east-1` | 明确标记课程区域 | `ap-southeast-1` |
| ECR | Lab 明确支持 | 推送同一不可变镜像 digest | ECR + 签名/provenance |
| Gateway | Lab 明确支持 | Envoy Gateway + HTTPRoute + AWS LB DNS | Gateway/WAF/TLS/custom domain |
| PostgreSQL | 无 StorageClass/EBS CSI | 单副本静态 hostPath PV，或经批准的外部测试 DB；数据视为可重建 | RDS PostgreSQL Multi-AZ + pgvector |
| Redis | 托管服务未验证 | 集群内单实例，数据视为短期；只存 session/rate/lease | ElastiCache TLS/HA |
| SQS/DLQ | 用户角色 PASS，Pod FAIL | JDBC outbox polling + DB inbox/idempotency；不调用 SQS | SQS + DLQ + outbox dispatcher |
| S3 | 未验证 | 不作为核心依赖；小型演示资产随镜像/临时卷 | S3/KMS/lifecycle |
| Secrets Manager | 未验证 | 从操作员本地环境创建 K8s Secret；不入 Git，使用低权限演示 Key | Secrets Manager + External Secrets |
| IRSA/Pod Identity | 未证明且 IAM 受限 | 不使用 AWS SDK runtime 依赖 | 每个 role 最小权限身份 |
| Terraform | 不能假设可创建预置基础设施 | 只管理 namespace 内资源；集群参数外置 | 管理完整 AWS 基础设施 |
| Durable storage | 明确不生产级 | 可重建数据、课前导入、课后导出；不做灾备承诺 | PITR、快照、RPO/RTO 演练 |
| HTTPS/DNS | 未证明 | LB DNS/课程允许方式，敏感演示优先本地 | ACM/Route53/TLS/WAF |

## 4. Academy 事件与运行时设计

应用代码通过端口隔离外部设施：

```text
Domain transaction
  -> PostgreSQL outbox
  -> EventDispatcherPort
       academy-eks: JdbcOutboxDispatcher -> worker claims rows
       commercial-sg: SqsOutboxDispatcher -> SQS/DLQ
```

两种适配器共享 event schema、幂等 inbox、重试上限、trace id 和业务处理器。Academy profile 不是删除事件架构，而是在同一 PostgreSQL 中轮询并使用 `FOR UPDATE SKIP LOCKED`/lease 竞争；商业 profile 再把跨进程投递放入 SQS。

禁止为课程环境额外维护一套业务实现。Profile 差异只能出现在 adapter/config/deployment，不得改变 Aurora、记忆、共鸣体或慢信语义。

## 5. Academy 数据策略

### 5.1 PostgreSQL

- 使用课程 Lab 3 相同的静态 PV/PVC 和 node label 机制，但 namespace、路径、Secret 和镜像独立。
- 明示它只证明 Pod 重启与 PVC 绑定，不证明节点故障耐久性。
- 提供 `seed-demo`、`export-demo`、`reset-demo` 和 schema migration 命令。
- 每次演示开始前检查 node label、PV/PVC binding、Flyway 版本和数据集 hash。
- 节点替换时接受重新导入，不把恢复后的新数据库描述为“数据未丢失”。

### 5.2 Redis

- 可用 Deployment 或单副本 StatefulSet；没有可靠动态卷时默认将其视为可丢失短状态。
- 丢失 session 会要求重新登录；rate-limit/lease 状态重建。PostgreSQL 仍是领域事实来源。
- 由于生产配置对 Redis fail-closed，演示启动脚本必须先验证 Redis Ready，再启动 API。
- 不把 Academy Redis 故障结果推广为 ElastiCache HA 证据。

### 5.3 LLM 与对象资产

- 完整效果优先在 `local-complete` 使用真实 Provider。
- Academy 若需要真实 Provider，只允许通过当次部署创建的 K8s Secret 注入低额度演示凭据，并在结束后删除；不得提交 YAML value。
- 先探测 Pod 外网 egress、DNS、Provider TLS 和延迟，再决定 EKS demo 是否启用真实模型。
- 音频/大对象、评测语料和用户导出不依赖未验证的 S3；课程 demo 使用非敏感小资产。

## 6. Academy 工作负载最小拓扑

为课程展示保留足够清晰的云原生结构：

```text
Envoy Gateway / HTTPRoute
  -> web/API Service
     -> API Deployment (2 replicas)
     -> Worker Deployment (1-2 replicas)
     -> Scheduler Deployment (1 replica + Redis/ShedLock lease)
  -> PostgreSQL StatefulSet (1 replica, static hostPath PV)
  -> Redis Deployment (1 replica, short state)
```

必须演示：readiness/liveness/startup、requests/limits、rolling update、PDB、HPA、graceful shutdown、两副本 session/rate-limit 一致性、scheduler 单次执行和 Pod 删除恢复。

不应在 Academy 声称：多 AZ 数据库、高可用 Redis、EBS 节点故障恢复、SQS 工作负载身份、Terraform 全栈重建、Singapore residency、WAF/TLS/自定义域名或商业 RPO/RTO。

## 7. 本地完整体拓扑

`local-complete` 是产品验收和主展示环境：

- Docker Compose 启动 PostgreSQL/pgvector、Redis 和必要本地辅助服务。
- 后端 API/Worker/Scheduler 可以多进程或容器形式运行，验证多副本语义。
- React/PWA/Capacitor 连接同一 API；真实 Provider 与完整评测仅在受控本地启用。
- 使用与 Academy 相同的镜像、Flyway、配置 schema、健康检查和 OpenTelemetry 语义。
- 可选 kind 用于重复 Academy Kubernetes 演练，但不取代真实 EKS 部署证据。

本地不是“逃避云”，而是把受限教学账户无法可靠承载的产品体验和数据智能与课程 Kubernetes 证据解耦。

## 8. 商业新加坡拓扑

`commercial-sg` 保留 `10` 中的目标架构：新加坡 EKS、RDS PostgreSQL/pgvector、ElastiCache、SQS/DLQ、S3、Secrets Manager、KMS、Terraform、OTel、备份恢复和发布治理。

Academy 适配不得反向削弱商业目标；商业文档也不得把 Academy 未验证能力写成当前完成。

## 9. 配置与仓库规则

- 集群名、安全组、AWS 账户、endpoint、LB hostname、ECR registry 和 namespace 都使用环境变量/本地 `.env`/Secret 注入。
- Git 文档可以记录发现命令和非敏感默认示例，不应硬编码真实账户号、API endpoint 和安全组 ID。
- 不提交 Learner Lab Access Key、Secret、Session Token、kubeconfig、LLM Key 或生成后的 Secret YAML。
- `academy-eks` Kustomize overlay 不创建 EKS/VPC/IAM/RDS/ElastiCache；只引用预置资源并创建 namespace 级工作负载。
- 每次 Lab session 先运行 capability preflight；结果与上次不一致时 fail closed，而不是继续假设。

建议环境变量：`AWS_REGION`、`EKS_CLUSTER_NAME`、`EKS_SECURITY_GROUP_ID`、`ECR_REGISTRY`、`K8S_NAMESPACE`、`INFRA_PROFILE`。

## 10. Capability Preflight 合同

目标 Agent 应实现一个不会打印敏感值的 preflight，至少检查：

1. 临时 STS 身份有效期和区域。
2. EKS API、节点数量、可调度资源和 namespace 权限。
3. ECR 登录/目标仓库（只在授权时创建）。
4. GatewayClass、LoadBalancer 事件、HTTPRoute status。
5. StorageClass/EBS CSI；Academy 预期为缺失并选择静态 PV。
6. Metrics API 与 HPA。
7. Pod 外网 DNS/TLS/Provider egress。
8. SQS 用户角色与 Pod 身份分别探测；不得混为一个结果。
9. Redis/PostgreSQL TLS 与 readiness。
10. 所有临时 probe 资源均成功清理。

结果写入脱敏 evidence，并带时间、profile、集群逻辑别名和 Git SHA。能力探针有时效性，每个新 Lab 会话重新运行。

## 11. 演示编排

推荐 8—12 分钟演示顺序：

1. 本地完整体展示 Aurora 打断/重规划、真实模型、记忆星空、共鸣体与慢信。
2. 展示同一 commit/image digest 已推入 ECR 并运行于 Academy EKS。
3. 通过 LB/Gateway 访问一个核心旅程，显示 trace id。
4. 删除一个 API Pod，证明 session、限流和业务数据不绑定 Pod。
5. 展示 readiness、PDB、HPA 或 rolling update。
6. 展示 Scheduler lease 防重复和 PostgreSQL outbox worker 竞争。
7. 明确指出 Academy 静态 PV 和无 Pod AWS 身份的限制，再展示商业新加坡架构图。

坦诚展示限制反而能体现对云原生边界的理解；禁止用课程环境伪造商业可靠性。

## 12. 完成门

`academy-eks` 只有在以下证据齐全时算课程部署完成：

- preflight 当前会话 PASS，且临时资源清理 PASS。
- 同一不可变镜像能在本地与 EKS 启动。
- Gateway/HTTPRoute、两副本 API、Worker、Scheduler、PostgreSQL、Redis 形成最小闭环。
- 探针、资源、PDB、HPA、rolling update、graceful shutdown 有实际日志/截图/trace。
- 多副本 session/rate-limit、scheduler lease、outbox 幂等通过故障测试。
- 节点替换/静态 PV 局限明确记录，演示数据可重建。
- 没有把人的临时 AWS 密钥注入 Pod，没有在 Git 中留下环境标识或凭据。
- 本地完整体仍通过核心产品 E2E，Academy profile 没有反向阉割产品能力。
