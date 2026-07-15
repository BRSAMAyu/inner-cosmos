# AWS Academy EKS 实验集群连接与共享配置指南

> 文档性质：L3 操作指南，不覆盖 [`14-AWS-Academy-EKS约束与双轨部署架构.md`](14-AWS-Academy-EKS约束与双轨部署架构.md) 的能力边界。
> 安全原则：仓库中不记录账户号、集群名、API endpoint、安全组名称/ID、节点名、负载均衡器 hostname、临时凭据或 kubeconfig。

## 1. 环境边界

AWS Academy Learner Lab 是固定 `us-east-1`、短会话、临时身份和受限 IAM 的教学环境。每次 Lab 会话都必须重新发现能力并运行脱敏 preflight；上一次会话的成功结果不能当作当前事实。

当前权威边界见 `14` 与 `docs/goal/aws-academy-capabilities.yml`：

- EKS 由课程预置，名称和 endpoint 只从环境变量或运行时发现。
- 未证明 Fargate 可用；不得把“EKS on EC2”扩写为“EC2/Fargate 均可用”。
- Gateway API/Envoy Gateway 与 `Service type=LoadBalancer` 是已见课程路径，但具体控制器和生成的 AWS LB 类型必须从对象 status、annotations 和 AWS 资源实时确认。
- `LoadBalancer` Service 不自动证明一定是 ALB、NLB 或公网可达，也不证明操作员有权修改底层安全组。
- 当前没有 StorageClass/EBS CSI 的可靠动态卷路径；Academy PostgreSQL 只能使用明确标注可重建的静态 hostPath PV。
- 人类 LabRole 的 SQS 能力不等于 Pod Workload Identity；Academy 工作负载不得依赖 SQS、IRSA 或注入人的临时 AWS 密钥。

## 2. 本地临时配置

以下值只放在当前终端或 Git 忽略的本地环境文件中：

```powershell
$env:AWS_REGION = 'us-east-1'
$env:EKS_CLUSTER_NAME = '<runtime-discovered-cluster-name>'
$env:K8S_NAMESPACE = 'inner-cosmos-academy'
$env:ECR_REGISTRY = '<runtime-discovered-registry>'
$env:ECR_REPOSITORY = 'inner-cosmos'
```

从 Academy 控制台取得的 `AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY` 与 `AWS_SESSION_TOKEN` 只注入当前进程；不得写入仓库、文档、Deployment、ConfigMap、生成后的 Secret YAML 或长期共享文件。

## 3. 连接与发现

凭据注入后，用变量更新 kubeconfig。命令输出可能包含账户和 endpoint，不能复制到可提交 evidence：

```powershell
aws sts get-caller-identity | Out-Null
aws eks update-kubeconfig --name $env:EKS_CLUSTER_NAME --region $env:AWS_REGION | Out-Null
kubectl get --raw=/readyz | Out-Null
```

只在本地查看需要的资源；共享证据只记录脱敏状态和计数：

```powershell
kubectl get nodes
kubectl get gatewayclass
kubectl get storageclass
kubectl get csidriver
kubectl get --raw=/apis/metrics.k8s.io/v1beta1/nodes
```

正式部署前运行仓库提供的 preflight。它必须区分人类身份与无凭据 Pod、清理所有临时资源，并且只保存脱敏结果：

```powershell
.\scripts\academy\preflight.ps1 -Mode Live
```

## 4. 镜像与 namespace

- 使用同一 commit 构建的不可变镜像 digest；Academy 不维护业务分叉镜像。
- ECR 仓库只在已授权且 preflight 证明可用时创建；脚本默认只检查，不自行扩大 AWS 状态。
- namespace 从 `K8S_NAMESPACE` 注入，不在 Git 中记录实际集群标识。
- Academy overlay 只创建课程允许的工作负载，不创建 EKS/VPC/IAM/RDS/ElastiCache。

镜像拉取策略使用 `IfNotPresent` 或部署脚本明确设置的策略。`Always` 不是 EKS 的普遍安全要求；不可变 digest 已经确保内容身份，重复拉取策略应按课程网络和演示需要选择。

## 5. 配置与 Secret

Git 中只提交 Secret 引用和键名，绝不提交 `data`/`stringData` 值。部署脚本从操作员当前环境创建短期 Secret，并在演示结束后删除：

```powershell
kubectl -n $env:K8S_NAMESPACE create secret generic inner-cosmos-runtime `
  --from-literal=SPRING_DATASOURCE_PASSWORD=$env:DB_PASSWORD `
  --from-literal=REDIS_PASSWORD=$env:REDIS_PASSWORD `
  --from-literal=LLM_API_KEY=$env:LLM_API_KEY `
  --dry-run=client -o yaml | kubectl apply -f -
```

不要把上述命令的 YAML 输出重定向到仓库。数据库、Redis 和应用 TLS 证书同样由本地脚本生成或注入为短期 Secret。

## 6. Gateway 与外部访问

优先使用课程已安装的 GatewayClass，并通过环境变量指定允许绑定的 Gateway 名称/namespace。创建 HTTPRoute 后必须检查：

```powershell
kubectl -n $env:K8S_NAMESPACE get httproute inner-cosmos -o yaml
kubectl get gateway -A
kubectl get service -A
```

只有 `HTTPRoute.status.parents[*].conditions` 明确 `Accepted=True` 且 `ResolvedRefs=True`，才能声称路由已被控制器接受。外部地址、LB 类型、是否公网、TLS 和安全组行为必须分别验证；不得从 `type: LoadBalancer` 推断为 ALB/NLB 或推断安全组修改权限。

若入口需要额外安全组规则或共享 Gateway 变更，这属于环境所有者权限门禁，应停止该外部变更并请求授权；不要自行修改真实共享基础设施。

## 7. Academy 数据和运行语义

- PostgreSQL：单副本静态 hostPath PV，只证明 Pod 重启与同节点绑定，不证明节点替换耐久性；演示数据必须可重建。
- Redis：集群内单实例短状态，只保存 session、rate limit、lease 和可丢失流状态；Redis 重启允许重新登录，PostgreSQL 仍是领域事实来源。
- 事件：Academy 使用 PostgreSQL JDBC outbox 与幂等 inbox，不调用 SQS；商业 `commercial-sg` 才使用 SQS/DLQ adapter。
- LLM：真实 Provider 只使用当次部署注入的低额度演示密钥，并先验证 Pod DNS/TLS/egress；未通过时保留产品能力但将真实 Provider 演示标记为外部门禁。

## 8. 共享与清理清单

可共享：Git SHA、镜像 digest、逻辑 profile、资源种类、脱敏计数、probe PASS/FAIL、清理状态、已知限制。

不可共享：账户号、ARN、endpoint、节点名、队列 URL、LB hostname、安全组、kubeconfig、token、证书私钥、Provider key、完整命令输出。

会话结束前执行：

1. 删除临时 probe Pod/Job/Queue。
2. 删除当次演示创建的运行 Secret。
3. 确认 preflight `cleanup=PASS`。
4. 导出允许保留的非敏感 demo 数据；静态 PV 不作为备份。
5. 记录 Academy 证据只适用于当前会话，不外推为 `commercial-sg` 生产证据。
