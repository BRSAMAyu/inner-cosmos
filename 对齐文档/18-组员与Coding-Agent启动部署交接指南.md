# Inner Cosmos 组员与 Coding Agent 启动部署交接指南

> 文档性质：面向组员和 Coding Agent 的可执行交接手册
>
> 状态：ACTIVE / OPERATIONAL
> 权威边界：本文件只负责“怎样安全、快速、可验证地跑起来”；产品目标与部署能力裁决仍以 `goal-objective.md`、本目录 `README.md`、`10`、`14`、`17` 和机器验收账本为准。

## 0. 先给接管者的结论

Inner Cosmos 目前有三条明确而不混淆的运行路径：

1. `dev/demo`：最快看到产品，H2 + Mock，适合代码理解和日常开发。
2. `local-complete`：PostgreSQL/pgvector + TLS Redis + 真实 Provider + OIDC，承担完整产品与 AI 效果验收。
3. `academy-eks`：部署同一应用制品到 AWS Academy 预置 EKS，证明多角色、探针、PDB/HPA、Gateway、调度租约和 JDBC outbox 等 Kubernetes 语义；它不是新加坡生产环境。

`commercial-sg` 仍是目标架构和验收线，目前不能让组员或 Agent 把它描述为已经可部署的生产完成态。Terraform、托管 RDS/Redis/SQS/S3、灾备、正式 DNS/TLS、法律与所有者门禁仍需按验收账本逐项关闭。

## 1. Coding Agent 最短接管提示

把下面这段作为部署 Agent 的第一条任务说明；不要附带任何密钥：

```text
你负责在不改写产品语义、不泄露密钥的前提下启动或部署 Inner Cosmos。
先完整阅读 AGENTS.md、goal-objective.md、对齐文档/README.md、
对齐文档/14-AWS-Academy-EKS约束与双轨部署架构.md、本交接文档、
docs/goal/complete-product-acceptance.yml 和 docs/goal/single-session-state.yml。
随后检查实时 HEAD、工作树、Docker/Java/Node/AWS/kubectl 工具、运行进程和 evidence。
先执行当前树与 HEAD 历史的脱敏扫描。根据目标选择 dev、local-complete 或 academy-eks，
严格运行该 profile 的 preflight、启动、健康检查和场景 smoke；只报告脱敏结果。
禁止把 AWS/LLM/OIDC/数据库凭据写进仓库、日志、聊天、manifest、ConfigMap 或生成 evidence。
Academy 中禁止把人的四小时 AWS 凭据注入 Pod；Pod 事件路径必须使用 JDBC outbox。
遇到外部门禁时登记并继续可执行验证，不得用 Mock、本地结果或截图冒充未完成的真实环境证据。
```

## 2. 每次启动都要执行的共同预检

### 2.1 读取与仓库事实

```powershell
git status -sb
git log -5 --oneline --decorate
git remote -v
.\scripts\scan-secrets.ps1
.\scripts\scan-secrets.ps1 -History
```

预期：工作树意图明确；两次扫描均为 0 finding。`-History` 默认只扫描当前 `HEAD` 可达历史，适合发布前验证；安全审计本机所有 refs 时可显式使用 `-History -AllRefs`，但旧的未发布实验分支可能需要单独净化，不能被自动 force-push。

### 2.2 工具最低要求

| 路径 | 必需工具 |
|---|---|
| `dev/demo` | JDK 21；仓库 Maven Wrapper |
| React | Node.js 22+、npm |
| `local-complete` | 上述工具 + Docker Engine/Compose + OpenSSL |
| `academy-eks` | Docker、AWS CLI v2、kubectl（支持 `kubectl kustomize`）、OpenSSL、有效 Learner Lab 会话 |
| Android | Android SDK/JDK 21；`web/android/gradlew.bat` |
| iOS | macOS/Xcode/签名账号（人类/设备门禁） |

```powershell
java -version
.\mvnw.cmd -version
docker version
kubectl version --client
aws --version
```

不要在共享日志中运行会输出账户号、集群 endpoint、token、完整 kubeconfig 或 Secret 内容的命令。需要确认身份时，把输出丢弃，只检查退出码。

## 3. 路径 A：最快本地开发（H2 + Mock）

这是接管代码、修复 UI/后端和运行 focused tests 的默认路径。

```powershell
.\mvnw.cmd spring-boot:run
```

也可让脚本在本地准备固定 Maven 3.9.9，并使用不冲突端口：

```powershell
.\scripts\run-dev.ps1 -Port 8081
```

打开：

- Maven 默认端口：`http://localhost:8080/app/aurora/`
- `run-dev.ps1` 默认端口：`http://localhost:8081/app/aurora/`
- 健康检查：`/actuator/health`

默认 `dev` 不注入演示账号，直接注册本地用户。需要一次性演示数据时：

```powershell
$env:SEED_ENABLED = 'true'
.\scripts\run-dev.ps1 -Profile demo -Port 8081
```

结束后从当前终端清除该变量。不要把 `demo` 的 Mock 成功当成真实 Provider 质量证据。

### 3.1 React 开发

```powershell
Push-Location web
npm ci
npm test
npm run build
Pop-Location
```

生产 bundle 会写入 `src/main/resources/static/app/aurora/`，随后由 Spring Boot/JAR 托管。只跑 Vite dev server 不能替代打包 JAR 的真实浏览器验证。

### 3.2 日常验证

```powershell
# 后端按风险选 focused test；跨域或检查点再跑全量
.\mvnw.cmd -Dtest=<RelevantTestClass> test

Push-Location web
npm test
npm run build
Pop-Location
```

涉及 PostgreSQL/Redis 合同时确保 Docker Engine 正常。Testcontainers 找不到 Docker 是真实环境阻塞，不能改断言或把错误描述为 PASS。

## 4. 路径 B：local-complete 完整体

该 profile 用 TLS PostgreSQL/pgvector、TLS Redis、真实 Provider、OIDC、禁 Mock fallback 和禁 demo seed。它是产品/AI 主验收环境，不是零配置开发脚本。

### 4.1 必需的操作者输入

在当前进程或 Git 忽略的本地 secret manager 中提供：

```text
LLM_PROVIDER
LLM_API_KEY（或相应 Provider 专用变量）
OIDC_ISSUER_URI
OIDC_JWK_SET_URI
OIDC_AUDIENCE
OIDC_AUTHORIZATION_URI
OIDC_TOKEN_URI
OIDC_MOBILE_CLIENT_ID
OIDC_MOBILE_REDIRECT_URI
CORS_ALLOWED_ORIGINS
```

脚本会为当前进程生成数据库和 Redis 随机密码（若操作者未提供），但不会生成 Provider/OIDC 身份。不要把任何值复制进本文、README、Compose 或 Git 跟踪文件。

### 4.2 配置与启动

先在当前终端设置外部变量，然后：

```powershell
.\scripts\local-complete.ps1 -Action Config
.\scripts\local-complete.ps1 -Action Up
docker compose -f deploy/compose/local-complete.yml ps
```

访问 `https://localhost:8443/app/aurora/`。本地 CA 为脚本短期生成；浏览器信任行为必须在体验记录中如实说明。

### 4.3 验收与关闭

至少验证：

```powershell
docker compose -f deploy/compose/local-complete.yml ps
docker compose -f deploy/compose/local-complete.yml logs --no-color app --tail 100
```

日志只用于确认健康、Flyway、Provider/OIDC 初始化和错误；禁止把包含用户原文或环境值的完整日志提交。完成后：

```powershell
.\scripts\local-complete.ps1 -Action Down
```

除非明确要求销毁本地数据，不要给 `docker compose down` 增加 `-v`。

## 5. 路径 C：AWS Academy EKS

### 5.1 不可违反的边界

- Learner Lab 固定 `us-east-1`，单次凭据约四小时；每个新会话重新 preflight。
- 集群、账户、节点、Gateway、ECR、LB 地址均运行时发现，不进入 Git。
- 人类 LabRole 可能有 SQS 权限，但 Pod 当前没有 Workload Identity；Academy workload 使用 PostgreSQL JDBC outbox，禁止把人的临时 AWS key 放入 Pod。
- 无可靠 StorageClass/EBS CSI；PostgreSQL 是单节点静态 `hostPath` PV，只证明 Pod 重启，不证明节点替换耐久。
- Academy 不能关闭新加坡生产、商业灾备、RDS/ElastiCache/SQS 或法律门禁。

### 5.2 在当前进程注入临时凭据

从 Academy 控制台取得的三项凭据只放当前 PowerShell 进程。建议使用不回显输入或本地 Git-ignored secret helper；不要把值直接写入命令、文件或聊天。完成后验证但丢弃身份输出：

```powershell
$env:AWS_REGION = 'us-east-1'
aws sts get-caller-identity | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'AWS session is invalid or expired.' }
```

然后设置运行时发现的非敏感逻辑变量：

```powershell
$env:EKS_CLUSTER_NAME = '<current Lab cluster>'
$env:EKS_LOGICAL_ALIAS = 'academy-lab'
$env:K8S_NAMESPACE = 'inner-cosmos-academy'
$env:ECR_REPOSITORY = 'inner-cosmos'
aws eks update-kubeconfig --name $env:EKS_CLUSTER_NAME --region $env:AWS_REGION | Out-Null
kubectl get --raw=/readyz | Out-Null
```

不要把生成的 kubeconfig 复制进仓库。

### 5.3 离线合同与实时能力探针

```powershell
.\scripts\academy\validate-manifests.ps1
.\scripts\academy\preflight.ps1 -Mode Offline
.\scripts\academy\preflight.ps1 -Mode Live
```

`validate-manifests.ps1` 默认是真正离线的 Kustomize/安全合同检查；只有当前 kubeconfig 已完成认证时，才额外运行 `-ClusterSchemaDryRun` 做 API discovery 支撑的 schema dry-run。实时 preflight 会：验证工具、EKS/节点、Gateway/Storage/Metrics、分别探测人类 SQS 与无凭据 Pod 身份，并清理临时 namespace/queue。只提交它生成的脱敏 JSON；必须看到 `SensitiveIdentifiersRecorded=False` 和 `cleanup=PASS`。

若 probe 发现的能力与 `14` 不同，fail closed，更新脱敏能力矩阵后再决定适配，不要凭经验继续。

### 5.4 构建并推送不可变 ECR 镜像

优先复用已授权的 ECR repository。创建 repository 是外部 AWS 写操作，只能在课程权限和组员授权允许时执行。

```powershell
$sha = (git rev-parse --short=12 HEAD).Trim()
$account = (aws sts get-caller-identity --query Account --output text).Trim()
$env:ECR_REGISTRY = "$account.dkr.ecr.$env:AWS_REGION.amazonaws.com"
aws ecr describe-repositories --repository-names $env:ECR_REPOSITORY --region $env:AWS_REGION | Out-Null
aws ecr get-login-password --region $env:AWS_REGION |
  docker login --username AWS --password-stdin $env:ECR_REGISTRY | Out-Null
docker build -t "inner-cosmos:$sha" .
docker tag "inner-cosmos:$sha" "$env:ECR_REGISTRY/$env:ECR_REPOSITORY:$sha"
docker push "$env:ECR_REGISTRY/$env:ECR_REPOSITORY:$sha"
$digest = (aws ecr describe-images --repository-name $env:ECR_REPOSITORY `
  --image-ids "imageTag=$sha" --query 'imageDetails[0].imageDigest' --output text).Trim()
$env:INNER_COSMOS_IMAGE = "$env:ECR_REGISTRY/$env:ECR_REPOSITORY@$digest"
if ($env:INNER_COSMOS_IMAGE -notmatch '@sha256:[a-f0-9]{64}$') { throw 'Immutable digest resolution failed.' }
```

账户号和 registry 只保留在当前进程，不复制到 evidence。

### 5.5 Academy 部署输入

从实时 preflight 得到并设置：

```text
ACADEMY_STORAGE_NODE
ACADEMY_GATEWAY_CLASS
ACADEMY_EDGE_DNS（没有可用外部 DNS 时保留逻辑演示域）
INNER_COSMOS_IMAGE（必须是 @sha256 digest）
```

同时在当前进程提供应用密钥：数据库/Redis 随机密码、低额度 Provider key、OIDC 参数和 `CORS_ALLOWED_ORIGINS`。部署脚本只在系统临时目录生成 `runtime.env` 与短期 TLS 证书，并通过 `kubectl create secret ... --dry-run | apply` 注入；finally 会删除本地临时材料。

先预览外部写操作：

```powershell
.\scripts\academy\deploy.ps1 -WhatIf
```

确认 namespace、节点、Gateway 和 digest 后执行：

```powershell
.\scripts\academy\deploy.ps1
```

### 5.6 部署后验收

```powershell
kubectl -n $env:K8S_NAMESPACE get deploy,statefulset,job,pod,svc,pvc,pdb,hpa
kubectl -n $env:K8S_NAMESPACE rollout status deployment/inner-cosmos-api --timeout=300s
kubectl -n $env:K8S_NAMESPACE rollout status deployment/inner-cosmos-worker --timeout=300s
kubectl -n $env:K8S_NAMESPACE rollout status deployment/inner-cosmos-scheduler --timeout=300s
kubectl -n $env:K8S_NAMESPACE get httproute inner-cosmos -o jsonpath='{.status.parents[*].conditions}'
```

只有 `Accepted=True` 且 `ResolvedRefs=True` 才能声称路由被接受；LoadBalancer/Gateway 对象存在不等于公网可达、HTTPS 正确或安全组允许。外部 smoke 至少验证 `/actuator/health`、登录/注册、一个核心领域 API 和一个可观察的角色行为。

课程可靠性证据还应逐项演练：API Pod 删除恢复、两副本 session/限流一致性、scheduler 租约不重复、JDBC outbox 幂等、PDB、HPA、rolling update 与 graceful shutdown。每项都记录 Git SHA、镜像 digest、逻辑 profile、脱敏计数和限制。

### 5.7 清理

先删除短期运行与 TLS Secret：

```powershell
.\scripts\academy\cleanup-probes.ps1 -WhatIf
.\scripts\academy\cleanup-probes.ps1
```

该脚本不会删除 namespace。删除整个 namespace、ECR 镜像或外部 Gateway 是更大范围的外部操作，必须由组员确认本次演示资产是否仍需保留后再执行。会话结束时清除当前进程的 AWS/Provider/OIDC 变量并关闭 Academy 会话。

## 6. 真实 Provider 的验证方法

代码存在、Mock 通过或作者自评分都不能证明 AI 效果。接入真实 Provider 时：

1. 固定 Provider/model/参数、场景集、Prompt/runtime 版本和 Git SHA。
2. 运行 `scripts/run-innovation-eval.ps1` 与对应 `ai-lab` pairwise 工具。
3. 对普通长 Prompt baseline 与完整 runtime/Genome 做同模型盲测。
4. 保存脱敏输出指针、分数、成本/延迟和失败分析，不保存不必要原文或密钥。
5. 独立体验者和 Provider 侧凭据/额度仍是人类门禁；不要把 Builder evidence 升格为 PASS。

## 7. 常见失败与恢复

| 现象 | 先查什么 | 正确处理 |
|---|---|---|
| Maven 拒绝构建 | `java -version` / Wrapper 版本 | 使用 JDK 21 和仓库 Maven 3.9.9，不绕过 Enforcer |
| Testcontainers 报 Docker 不可用 | `docker version` | 启动 Docker 后重跑；不能删除或跳过断言后宣称全绿 |
| `prod` 启动 fail-fast | Provider/OIDC/Postgres/Redis/TLS 变量 | 补齐外部配置；禁止开启 Mock/fallback/demo seed |
| AppShell 数据接口 500 | 当前 DB schema/Flyway/H2 M8/M9、日志 trace | 先用新库复现、备份旧库、做 schema diff；不要直接删真实数据 |
| Academy AWS session 失效 | STS 退出码、四小时期限 | 获取新会话并重新 preflight；不要把旧凭据固化 |
| Pod 无 SQS 权限 | Workload Identity 探针 | 这是 Academy 预期边界，使用 JDBC outbox；禁止注入人的 AWS key |
| PVC Pending | StorageClass/EBS CSI、静态节点标签 | 按 Academy 静态 PV 合同设置当前节点；不宣称节点故障耐久 |
| HTTPRoute 没流量 | `Accepted/ResolvedRefs`、Gateway status、LB/安全组 | 分层验证；共享基础设施变更属于所有者权限门禁 |
| Provider 超时/拒绝 | egress/DNS/TLS、额度、model id | 保留精确错误并尝试受控替代；生产不得静默回退 Mock |

## 8. 交付给组员的最小证据包

每次可复现交接至少包含：

- Git branch、commit SHA、工作树状态和镜像 digest。
- 选择的 profile 与理由。
- 当前树/HEAD 历史 secret scan 结果。
- 构建、focused/full tests、前端和 manifest/preflight 结果。
- 运行拓扑、健康检查、核心旅程 smoke 与错误/限制。
- Academy 时：能力探针时间、逻辑集群别名、资源计数和 cleanup 状态；不含账户、endpoint、节点、LB、队列 URL 或凭据。
- 未关闭门禁及下一条机器可执行动作。

不要只发“启动成功”或截图。组员/Coding Agent 必须能从上述信息在新环境重做，并清楚分辨：已验证、仅 Builder 验证、外部门禁和未开始项。

## 9. 发布前检查表

```text
[ ] HEAD/工作树/运行进程已核对
[ ] 当前树与 HEAD 历史 secret scan 均为 0
[ ] README/本交接文档与真实命令一致
[ ] 选定 profile，不混淆 local / Academy / commercial-SG 证据
[ ] Java/前端/容器/manifest 按风险通过
[ ] 生产或 local-complete 禁 Mock/fallback/demo seed
[ ] 所有密钥只在操作者本地或短期 Secret 中
[ ] Academy preflight 是本次四小时会话产生，cleanup=PASS
[ ] 镜像使用 digest，Flyway 与应用来自同一 commit
[ ] 健康检查与至少一条真实用户旅程通过
[ ] evidence 已脱敏且能复现
[ ] 验收账本和 single-session-state 已更新但未夸大 PASS
```
