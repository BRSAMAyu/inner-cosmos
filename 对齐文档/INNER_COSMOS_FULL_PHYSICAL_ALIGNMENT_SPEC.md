# Inner Cosmos (内宇宙) — 物理源码真相与全域 AI 对齐规格书 (Master Repository Physical AI Alignment Spec)

> **文档版本**: `v6.0.0-PHYSICAL-PERSISTENT-ALIGNMENT-SPEC`
> **持久化路径**: `docs/INNER_COSMOS_AI_ALIGNMENT_SPEC.md` 与 `对齐文档/INNER_COSMOS_FULL_PHYSICAL_ALIGNMENT_SPEC.md`
> **适用对象**: 全量 LLM / AI Coding Agent / Autonomous Subagent / 架构决策引擎
> **基线准则**: 本规格书 100% 抽取自项目 Git 源码真相（包含 Java 21 后端、Flyway `V1__`~`V33__` 迁移 SQL、React 19+TypeScript 前端及 K8s/Docker 编排清单），绝不依赖任何虚假占位符或过时文档。任何接手本项目的 AI Agent 必须严格以此作为推理与代码设计的权威准则。

---

## 1. 物理工程结构与多角色架构全貌 (Physical Topology & Architecture)

### 1.1 物理目录分布
* **后端核心服务**: Java 21, Spring Boot 3.5.x, MyBatis-Plus (`src/main/java/com/innercosmos/`)
* **数据库 Flyway 迁移**: PostgreSQL 16 + pgvector (`src/main/resources/db/migration/postgresql/`)，包含 33 个增量 SQL 文件。
* **前端 AppShell**: React 19 + TypeScript + Vite + Vitest (`web/src/`)，编译输出托管至 `src/main/resources/static/app/aurora/`。
* **云原生 Kubernetes 编排**: Gateway API, Kustomize 基础包与 Overlay (`deploy/k8s/base/` 与 `deploy/k8s/overlays/academy-eks/`)。
* **容器化本地环境**: Docker Compose 生产级模拟 (`deploy/compose/local-complete.yml`)。

### 1.2 运行时五大解耦角色 (Runtime Roles)
1. `innercosmos-api`: REST API 与 Aurora SSE 流式长连接接入节点。无状态，具备零丢包优雅排水能力。
2. `innercosmos-ai-worker`: 专门消费后台 AI 任务（LLM 异步调用、Embedding 计算、记忆卡片与画像提取）。
3. `innercosmos-event-worker`: 消费 PostgreSQL Outbox 与 Redis Stream 消息，更新记忆星空与共鸣体索引。
4. `innercosmos-scheduler`: 挂载分布式租约 (Lease) 的单例/低副本任务节点，执行慢信定时投递 (`LetterDeliveryJob`) 与夜间记忆重力结算 (`NightlyMemorySettlementJob`)。
5. `innercosmos-migration`: 初始化 Job，在服务 Pod 启动前自动跑通 Flyway `V1`~`V33` SQL 迁移。

---

## 2. Kubernetes 与云原生架构深度展开 (Kubernetes & Cloud-Native Deep Dive)

> **设计核心**: 将云原生基础设施（K8s 拓扑、探针隔离、网络策略、流量排水、弹性扩缩）与 Inner Cosmos 心理陪伴、长记忆计算、SSE 流式打字与数据隔离业务深度相连。

```text
                               ┌──────────────────────────────────────────┐
                               │   Kubernetes Gateway API / Ingress       │
                               └────────────────────┬─────────────────────┘
                                                    │ (Port 8080 Public Traffic)
                                                    ▼
                                ┌───────────────────────────────────────┐
                                │   Deployment: inner-cosmos-api        │
                                │   - Replicas: 2 (HPA: 2~4)            │
                                │   - Security: Non-Root (UID 1001)     │
                                │   - Probes: Readiness/Liveness (8090) │
                                └───────────────────┬───────────────────┘
                                                    │ (SKIP LOCKED / Leases)
                        ┌───────────────────────────┼───────────────────────────┐
                        ▼                           ▼                           ▼
       ┌─────────────────────────┐     ┌─────────────────────────┐     ┌─────────────────────────┐
       │ PostgreSQL 16 + pgvector│     │ Redis 7 Cluster         │     │ LLM Provider Gateway    │
       │ (StatefulSet + PV/PVC)  │     │ (TLS + Sessions + Stream│     │ (Gemini/GLM/MiniMax)    │
       └─────────────────────────┘     └─────────────────────────┘     └─────────────────────────┘
```

### 2.1 API 工作负载 Pod 声明 (`deploy/k8s/base/app-deployment.yml`)
* **多层安全上下文 (Security Context)**:
  ```yaml
  securityContext:
    runAsNonRoot: true
    runAsUser: 1001
    runAsGroup: 1001
    fsGroup: 1001
    seccompProfile:
      type: RuntimeDefault
  ```
  主容器与初始化容器统一开启 `readOnlyRootFilesystem: true`，拒绝容器内文件篡改。为 embedded Tomcat 与临时文件挂载 `emptyDir` 目录 (`/tmp` 与 `/var/log/inner-cosmos`)；显式移除所有 Linux Capabilities (`capabilities.drop: ["ALL"]`)。

* **初始化容器: Schema 版本屏障 (`wait-for-schema-version`)**:
  在应用容器启动前，首先启动基于 `pgvector/pgvector:0.8.1-pg16` 镜像的初始化容器，运行 SQL 轮询检查 `flyway_schema_history` 表：
  ```bash
  until [ "$(psql -h inner-cosmos-postgres -U inner_cosmos -d inner_cosmos -Atc \
    "SELECT CASE WHEN EXISTS (SELECT 1 FROM flyway_schema_history WHERE NOT success) THEN -1 \
     ELSE COALESCE(MAX(version::integer),0) END FROM flyway_schema_history WHERE success" 2>/dev/null)" \
    -eq "$INNER_COSMOS_EXPECTED_SCHEMA_VERSION" ]; do sleep 2; done
  ```
  确保只有在 `INNER_COSMOS_EXPECTED_SCHEMA_VERSION` (如 33) 物理迁移完全成功后，主 API Pod 才允许启动，彻底解决多副本滚更时的 Schema 不一致问题。

* **物理探针与管理端口隔离 (Probe & Port Isolation)**:
  * 公网服务端口为 `8080`；Actuator 管理与探针端口设定为独立的 `8090` (仅内部可达，不暴露至 Ingress/Gateway API)。
  * **`startupProbe` & `readinessProbe`**: 请求 `/actuator/health/readiness` (Port 8090)。包含应用本身、PostgreSQL 及 Redis 健康检查，确保依赖就绪后才接流量。
  * **`livenessProbe`**: 物理隔离，**仅请求 `/actuator/health/liveness`** (仅检查 JVM 进程内部状态)。绝不将 DB 或第三方 AI Provider 放入 Liveness 探针中，防止因瞬时网络抖动引发整个 K8s 集群的级联 Pod 重启风暴。

* **拓扑分布约束 (Topology Spread Constraints)**:
  ```yaml
  topologySpreadConstraints:
    - maxSkew: 1
      topologyKey: kubernetes.io/hostname
      whenUnsatisfiable: ScheduleAnyway
      labelSelector:
        matchLabels:
          app.kubernetes.io/name: inner-cosmos
          app.kubernetes.io/component: api
  ```
  强行将 Pod 均匀打散在不同 K8s 节点上，实现节点级的容灾避险。

### 2.2 Hero H1: Aurora 零丢包优雅排水与 Fencing 恢复 (Zero-Loss SSE Drain)
1. **优雅终止 (`preStop` & `terminationGracePeriodSeconds`)**:
   在 Pod 接收到 `SIGTERM` 信号时，先触发 `preStop` 钩子运行 `sleep 15`，触发 Readiness 探针置为 `false`。K8s Endpoints 与 Gateway API 立即切断新流量。
   设置 `terminationGracePeriodSeconds: 45`（或 60 秒），允许现有 SSE 长连接推流完毕。
2. **断线重连与 Fencing Token 接管**:
   若推流过程中连接强行中断，前端带上 `Last-Event-ID` 携带 Offset 请求新 Pod。新 Pod 的 `ConversationTurnTakeoverService` 领用数据库单调自增 `lease_token` (Fencing Authority)，接管未完成的 Turn 并恢复打字流，彻底解决长连接中断体验断层问题。

### 2.3 Hero H2: 业务指标驱动弹性扩缩容 (KEDA & SKIP LOCKED Autoscaling)
* 引入 **KEDA (Kubernetes Event-driven Autoscaling)** 监控 PostgreSQL `tb_outbox_event` 表中 `status = 'PENDING'` 的记录数与最长等待时间 `oldest_age`。
* 动态触发 `innercosmos-event-worker` 与 `innercosmos-ai-worker` 副本从 2 扩展至 10。
* 多 Worker 副本通过 `FOR UPDATE SKIP LOCKED` 竞争行锁，保证无锁竞争的高吞吐处理；任务清空后自动平滑缩容。

### 2.4 Hero H3: 端到端语义分布式链路追踪 (OTel Semantic Tracing)
* 接入 OpenTelemetry SDK / Collector，利用 **W3C Trace Context** 透传 `traceparent` 与 `tracestate`，贯穿 `HTTP -> SSE -> Redis Stream -> Outbox -> AI Worker -> LLM Provider -> Vector DB`。
* **隐私安全防护**: Trace Attribute 强制过滤所有 P0 原始文本，仅保留不可逆哈希 `anon_user_hash` 和 `prompt_version`。

### 2.5 云原生网络安全与资源限制
* **网络策略 (`deploy/k8s/base/app-network-policy.yml`)**:
  * Ingress: 仅允许访问 8080 (业务) 与 8090 (管理)。
  * Egress: 仅允许 DNS(53), Postgres(5432), Redis(6379) 以及外部 HTTPS Provider (443)。
  * **硬性安全黑洞**: 显式拒绝访问云厂商元数据地址 `169.254.169.254/32`，杜绝 SSRF 窃取 Cloud Credentials。
* **Pod 干扰预算 (`app-pdb.yml`)**: `minAvailable: 1`，保证自愿维护时不中断服务。
* **弹性扩缩容 (`app-hpa.yml`)**: 2 ~ 4 副本，目标 CPU 利用率 70%，包含 300 秒的缩容平滑窗口 (Scale-down Stabilization)。

### 2.6 13 云原生扩展展柜 (Enterprise Showcase Gallery E1–E13)
1. **E1 渐进式发布 (Argo Rollouts / Flagger)**: Canary 灰度发布，以首字延迟 (TTFT) 与 AI Parse 成功率作为 Gate 自动 回滚。
2. **E2 GitOps 声明式运维 (ArgoCD)**: 按 Sync Waves 顺序部署：`CRD` -> `Migration Job` -> `Workloads` -> `Smoke Test`。
3. **E3 策略即代码 (Kyverno + Cosign)**: 校验容器 Non-root、禁用 hostPath、校验 Cosign 镜像签名。
4. **E4 eBPF 网络安全 (Cilium & Hubble)**: Namespace 级 Default-Deny，Hubble 可视化拓扑。
5. **E5 凭据自动化 (cert-manager & ESO)**: 自动签发 TLS，定时从 AWS Secrets Manager 轮换 Key 并重载 Pod。
6. **E6 运行时威胁检测 (Falco)**: 监控容器内非法 Shell 执行与端口异常。
7. **E7 混沌工程 (Chaos Mesh / Litmus)**: 注入网络延迟与 429 频控，验证 Outbox 自愈与 SSE 重连。
8. **E8 容灾备份 (Velero + WAL Archiving)**: K8s 资源与 PostgreSQL WAL 增量备份与清洁恢复。
9. **E9 AI 成本 FinOps (OpenCost + Token Metric)**: 统计 Pod 算力与 Token 消耗，算出单次 Turn 综合云成本。
10. **E10 动态功能开关 (OpenFeature)**: 动态切断非核心 AI 策略或降级向量精度。
11. **E11 工作负载身份 (SPIFFE/SPIRE)**: 签发短寿命 mTLS SVID 身份凭证。
12. **E12 云原生批量 Job (Indexed Jobs)**: 记忆全量重算与 Embedding Backfill 并行 Job 分片。
13. **E13 传输层适配 (Dapr Integration)**: 抽象 Pub/Sub 与 Secret，实现跨云部署零代码修改。

---

## 3. 数据库物理 Schema 与 Flyway 迁移全览 (Database Schema V1–V33)

经过对全量 33 个 Flyway 脚本的物理审计，系统表结构可划分为 8 大物理模块：

### 3.1 80+ 物理表全景划分
1. **身份账号**: `tb_user`, `tb_user_profile`, `tb_user_identity` (OIDC 登录认证 `V2`)
2. **社交关系**: `tb_friend_relation`, `tb_social_group`, `tb_social_group_member`, `tb_block_relation`
3. **Aurora 对话核心**: `tb_dialog_session`, `tb_dialog_message`, `tb_conversation_turn`, `tb_turn_plan`, `tb_message_bubble`, `tb_conversation_event`, `tb_generation_attempt`, `tb_turn_generation_request` (`V33`), `tb_turn_deliberation_snapshot` (`V33`)
4. **记忆碎片与重力**: `tb_memory_card`, `tb_thought_fragment`, `tb_emotion_trace`, `tb_todo_item`, `tb_event_card`, `tb_relation_mention`, `tb_memory_theme`, `tb_emotion_timeline`, `tb_belief_pattern`, `tb_user_portrait`, `tb_memory_link` (`V8`), `tb_memory_operation` (`V8`), `tb_memory_projection_receipt` (`V9`), `tb_memory_embedding` (`V10`)
5. **共鸣体生态**: `tb_echo_capsule`, `tb_capsule_boundary`, `tb_capsule_usage_quota`, `tb_capsule_genome_version` (`V11`), `tb_capsule_sandbox_feedback` (`V12`), `tb_capsule_embedding` (`V18`), `tb_capsule_landing` (`V30`)
6. **慢信与私聊**: `tb_slow_letter`, `tb_letter_status_log`, `tb_letter_thread`, `tb_persona_chat_session`, `tb_persona_chat_message`, `tb_live_chat_session` (`V29`)
7. **任务与异步事件**: `tb_outbox_event`, `tb_inbox_receipt`, `tb_wake_intent`, `tb_private_timer`
8. **自我模型演进**: `tb_aurora_self_model`, `tb_aurora_self_profile`, `tb_emergence_proposal`, `tb_emergence_evaluation`

### 3.2 pgvector 向量表与 1536 维定义 (`V10`, `V18`)
* **`tb_memory_embedding` (`V10`)**: 存储 1536 维向量 `embedding_vector vector(1536)`，搭配 JSON 文本 `embedding_json` 兜底；依赖 `uk_memory_embedding_version` (包含 `memory_id, model_name, model_version, source_version, task_scope`) 实现多模型版本共存。
* **`tb_capsule_embedding` (`V18`)**: 存储共鸣体匹配向量 `embedding_vector vector(1536)`，采用 `content_hash VARCHAR(64)` 计算内容摘要，约束为 `uk_capsule_embedding_version`。

### 3.3 MyBatis-Plus Mapper 核心查询
* **`MemoryEmbeddingMapper.java`**: 执行复杂联结查询 `selectMissingMemoryIds`，自动过滤 `consent_scope NOT IN ('LOCAL_ONLY', 'NO_EXTERNAL_PROCESSING')`，排查缺失向量的记忆卡片。
* **`CapsuleSyncQueueMapper.java`**: 执行 `findRetryable` 逻辑，结合 `next_retry_at` 指数退避与 `attempt_count` 完成重试调度。

---

## 4. AI 模型网关与双核多阶段运行时 (AI System & Dual-Kernel Runtime)

### 4.1 `GeminiLlmClient.java` 原生 HTTP 契约
* 使用 Java 21 `java.net.http.HttpClient` 封装原生 Gemini GenerateContent API (`models/{model}:generateContent`)。
* 在 `generationConfig` 中物理透传 `thinkingConfig`:
  ```java
  generation.put("thinkingConfig", Map.of(
      "thinkingLevel", Boolean.TRUE.equals(request.thinkingEnabled)
          ? normalizeThinkingLevel(request.reasoningEffort, thinkingLevel)
          : "minimal"));
  ```
  限定合法值为 `minimal`, `low`, `medium`, `high`。当网络异常且 `allowFallback=true` 时，平滑降级至 `MockLlmClient` 并记录 `AiLogService`。

### 4.2 `AuroraStageRoutingLlmClient.java` 4 阶段时间路由
将 Aurora 思维链路解耦为独立的 `StageProfile`:
1. `AURORA_FOREGROUND_*`: 快响应 (`thinking=false`, `reasoningEffort=minimal`, `temp=0.25`, `maxTokens=256`)。
2. `AURORA_SPEAKER_*` / `AURORA_INNER_VOICE_*`: 演讲与心声 (`thinking=true`, `reasoningEffort=medium`, `temp=0.82`, `maxTokens=6144`)。
3. `AURORA_PLAN_*`: 前置规划 (`thinking=true`, `reasoningEffort=high`, `temp=0.10`, `maxTokens=8192`)。
4. `AURORA_CRITIC_*`: 价值观审查 (`thinking=true`, `reasoningEffort=high`, `temp=0.05`, `maxTokens=2048`)。

### 4.3 提示词防逃逸与泄露防线 (`PromptBuilder.java` & `PromptLeakageGuard.java`)
* **防角色伪造注入**: 在 `PromptBuilder.withUserInput` 中，用户输入统一进行 JSON Stringify 转义为 `{"userMessage": "..."}` 放入上下文，杜绝用户通过伪造 `=== 系统指令 ===` 逃逸。
* **输出泄露拦截 (`PromptLeakageGuard.java`)**: 监控模型输出，若包含 `contextBuildManifest`, `nextQuestion`, `speakCount`, `只返回 JSON` 等内部凭据，立即触发泄露拦截并强行修复。

---

## 5. 对话编排、租约锁与 SSE 流式机制 (Choreography & Streaming)

### 5.1 跨 Pod Fencing Token 租约接管 (`ConversationTurnTakeoverService.java`)
* 当 Pod 发生故障或流中断，接管节点调用 `claimGenerationLease` / `claimDeliveryLease` 抢占单调自增 `lease_token` (Fencing Authority)。
* 若 Plan 未生成，基于 `tb_turn_generation_request` 恢复 Context 重新激发生成；若 Plan 已生成，凭借 Fencing 锁逐个重推 `PLANNED` 状态的未发 `MessageBubble`。带 `*Fenced` 前缀方法强校验租约，彻底杜绝脑裂双写。

### 5.2 打断增量补算 (`InterruptionDeltaBuilder.java`)
* 用户在回答推到一半时发送新消息打断，系统提取已送达文本生成 `deliveredSummary`，计算未竟之言，并生成 `mustNotRepeat` 约束，防止下次生成重复已发出的段落。

---

## 6. 安全防线、衰减算法与 P0-P3 隐私隔离 (Safety & Privacy Isolation)

### 6.1 `SessionRiskAggregator.java` 指数时间衰减与语法纠偏
1. **时间指数衰减算法**:
   半衰期设定为 10 分钟 (`HALF_LIFE = 10 mins`)，风险积分随时间自动衰减：
   $$\text{Score}_{\text{new}} = \text{Score}_{\text{old}} \times 0.5^{\frac{\Delta t}{10\text{mins}}}$$
2. **第三方引述与过去时态纠偏**:
   * 第三方引述 (`THIRD_PARTY_QUOTE`，如 "他说", "she said")：风险权重**归零 (0.0)**。
   * 否定/过去时 (`NEGATION_OR_PAST`，如 "曾经", "不再")：权重**削弱至 20%** (`weight * 0.2`)。
3. **防误杀**: 积分达到阈值 (`>= 1.0`) 仅触发温和确认 (`GENTLE_CHECK_IN`)，**绝不自动升级为 HIGH 阻塞**。

### 6.2 PII 慢信两级门禁 (`PiiCredentialDetector.java`)
* **HARD-BLOCK (硬阻断)**: 匹配密码、API Key、身份证、银行卡号时直接拒发，无覆盖按钮。
* **SOFT-CONFIRM (软确认)**: 匹配电话、邮箱、住址时要求二次确认，系统仅记录分类标识 (`PHONE,EMAIL`)，**绝不落库 PII 明文**。

### 6.3 P0-P3 数据隐私隔离
* **P0 (`tb_dialog_message`)**: 仅用户本人与 Aurora 读写，严禁暴露给共鸣体或外部训练；
* **P2 (共鸣体编译)**: 在 `DataMaskingServiceImpl.previewFromMemory` 中，若记忆卡片 `consentScope` 为 `LOCAL_ONLY`, `NO_EXTERNAL_PROCESSING`, `SIMULATOR_AUTHORIZED`，物理强行剔除，禁止进入公域。

---

## 7. React 19 前端 AppShell 物理组件 (React 19 Frontend)

1. **`AuroraInnerVoiceAside.tsx` (余响组件)**: 渲染 "AURORA · 余响" 独立通道，支持 `AMBIENT` / `ON_DEMAND` 模式，使用 `InlineAudioPlayer` 播发独立 TTS 音频。
2. **`AuroraRuntimeDisclosure.tsx` (真实性披露组件)**: 真实暴露 `REAL_MODEL`, `DEMO_MODE`, `BASIC_RESPONSE`, `UNKNOWN` 状态，公开毫秒级各阶段耗时 (`stageLatenciesMs`)。
3. **`AuroraThinkingState.tsx` (思维外化组件)**: 承载 `understanding` -> `composing` -> `speaking` 三阶段状态，支持 `onStop` 用户中途打断。
4. **`MemoryStarfield.tsx` (星空记忆组件)**: 几何布局函数 `layoutMemoryStars` 支持 TIME / THEME / PEOPLE 视角的 2D 坐标算术，使用 `timeCollisionOffsets` 解决相近时间结点的坐标叠加问题。

---

## 8. AI Agent 物理推理与决策铁律 (Hard Execution Rules)

1. **坚持物理代码真相**: 本规格书已持久化在工作区仓库中，后续任何 AI 的推理、分析与代码修改必须严格以此及仓库代码为准。
2. **严守 P0 隐私戒律**: 任何新增代码绝不能将 P0 `tb_dialog_message` 内容写入日志、OpenTelemetry Span 属性或暴露给共鸣体匹配。
3. **保持分布式并发正确性**: 修改对话与异步队列逻辑时，必须遵循 `ConversationTurnTakeoverService` 的 Fencing Token 租约机制与 Outbox `SKIP LOCKED` 行锁控制。

---
*本 Master 规格书已物理持久化写入代码库 `docs/INNER_COSMOS_AI_ALIGNMENT_SPEC.md` 与 `对齐文档/INNER_COSMOS_FULL_PHYSICAL_ALIGNMENT_SPEC.md`。*
