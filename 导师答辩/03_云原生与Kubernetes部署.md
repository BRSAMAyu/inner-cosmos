# 导师答辩：云原生适配与 Kubernetes 声明式部署

> 本文档用于系统性总结 Inner Cosmos 的云原生与 Kubernetes (K8s) 声明式编排实践。

---

## 1. Kubernetes 部署拓扑 (Deployment Architecture)

我们的 K8s 部署配置文件位于 [deploy/k8s/](file:///d:/code/inner%20cosmos/deploy/k8s/) 目录中，采用 **Kustomize** 进行声明式、多层 Overlay 管理：
*   **[base/](file:///d:/code/inner%20cosmos/deploy/k8s/base/)**：存放基础无状态应用声明、服务隔离策略（NetworkPolicy）、水平自动伸缩规则（HPA）。
*   **[overlays/academy-eks/](file:///d:/code/inner%20cosmos/deploy/k8s/overlays/academy-eks/)**：针对 AWS Academy EKS (AWS云弹性Kubernetes集群) 的定制化重写，管理包含 pgvector 向量检索的 PostgreSQL 数据库 (StatefulSet)、Redis 节点、数据库一键迁移 (Flyway Job) 以及 AWS Edge Gateway 网关流量。

---

## 2. 五大核心云原生设计模式 (Cloud-Native Patterns)

### ① Monolith-Decoupling (多角色解耦伸缩模式)
在 K8s 中，我们部署同一个容器镜像，但通过设置环境变量 `INNER_COSMOS_RUNTIME_ROLE` 来解耦单体的运行角色，实现按需弹性扩缩容：
*   **`inner-cosmos-api` (Deployment)**：水平伸缩，专注处理外部 HTTP 流量与实时 SSE 消息流推送。
*   **`inner-cosmos-worker` (Deployment)**：独立伸缩，专注跑 AI 离线任务、向量比对等高 CPU/IO 任务，避免大模型生成卡顿导致 API 节点响应延迟。
*   **`inner-cosmos-scheduler` (Deployment)**：单实例调度器，负责触发定时慢信扫描、夜间结算。

### ② Flyway Migration Job (声明式迁移分离)
在分布式集群中，如果多个 Pod 在启动时都在各自的 JVM 内进行数据库 schema 迁移，极易引发死锁或数据脑裂。
我们编写了独立的 [migration-job.yml](file:///d:/code/inner%20cosmos/deploy/k8s/overlays/academy-eks/migration-job.yml)，以 **K8s Job** 的形式将 Flyway schema 迁移从业务 Pod 中剥离，做到数据库结构迁移的“声明式、单次执行、失败熔断”。

### ③ Wait-for-schema Gate via InitContainers (时序自愈栅栏)
为了解决 Pod 并发启动时的依赖锁竞争，我们在 [app-deployment.yml](file:///d:/code/inner%20cosmos/deploy/k8s/base/app-deployment.yml) 中配置了 `initContainers` 启动同步门禁：
```yaml
initContainers:
  - name: wait-for-schema-v3
    image: pgvector/pgvector:0.8.1-pg16
    command: ["sh", "-c", "until [ \"$(psql -h inner-cosmos-postgres -U inner_cosmos -d inner_cosmos -Atc \"SELECT COALESCE(MAX(version::integer),0) FROM flyway_schema_history WHERE success\" 2>/dev/null)\" -ge 3 ]; do sleep 2; done"]
```
这保证了 API Pod 在启动前，会自动阻塞等待数据库迁移 Job 成功升级至 v3 版本，实现**服务启动自愈**。

### ④ Kubernetes Gateway API 流量整合 (新一代网关适配)
抛弃了陈旧的 Ingress 资源，我们在 AWS EKS 上全面对接了 CNCF 标准的 **Gateway API**。通过配置 `Gateway` 承载 TLS 终止与基础设施绑定，通过 `HTTPRoute` 实现对 api 服务路径的解耦路由，使得网络流量控制更加规范化。

### ⑤ 优雅停机与无缝升级 (Graceful Shutdown & Rolling Update)
*   **拓扑分布约束 (TopologySpreadConstraints)**：按 `hostname` 实施 Pod 强制分散调度，最大偏差（`maxSkew`）设为 1，严防所有副本落入同一 EC2 节点。
*   **三阶段探针**：配置 `startupProbe` 延迟检查、`readinessProbe` 就绪检查、`livenessProbe` 存活检查。
*   **优雅停机钩子**：注入 `preStop` 执行 `sleep 15`，延缓 Pod 退出。当触发 K8s 滚动升级时，Pod 会先从 Service 就绪节点摘除，等待 15 秒以便处理完在途的 HTTP SSE 连接，再执行 JVM 关闭，实现 **零停机时间升级**。

---

## 3. 安全架构加固 (SecOps Security)

*   **NetworkPolicy 网络隔离**：强制隔离数据层（Redis, Postgres），规定只有携带特定 label 的 API/Worker Pod 才能连接数据库端口，限制了内网横向攻击风险。
*   **最小特权安全上下文**：Pod 运行属性指定了 `runAsNonRoot: true`、`runAsUser: 1001`。
*   **系统调用沙箱**：绑定系统默认安全规则 `RuntimeDefault` 的 seccompProfile，对核心操作系统调用进行安全收紧。
*   **API Token 屏蔽**：通过 `automountServiceAccountToken: false` 拒绝挂载集群 ServiceAccount Token，防范 Pod 被渗透时提权危害整个集群安全。
