# Inner Cosmos 云原生课堂展示 Runbook

> **最新现场顺序**：5 分钟真实观众体验 → H1 → KEDA → 可观测性。OTel 从观众入场前
> 就开始采集；分屏布局、逐幕命令和讲解词以
> [`LIVE-SHOWCASE-CUE-CARD.md`](LIVE-SHOWCASE-CUE-CARD.md) 为准。

## 三幕 Hero 快速模式（优先）

课堂需要在最短时间证明核心能力时，只演示以下三幕，不再穿插 APK、Argo、Kyverno 或
临时造数命令：

```powershell
# 课前：只读检查，失败即停止
.\scripts\demo\run-three-hero-showcase.ps1 -Scene Preflight

# 离场全链路彩排入口；正式现场不要使用 All
.\scripts\demo\run-three-hero-showcase.ps1 -Scene All -HoldViews
```

正式现场以 [`LIVE-SHOWCASE-CUE-CARD.md`](LIVE-SHOWCASE-CUE-CARD.md) 的冻结协议为唯一入口：
H1 使用专用硬故障脚本，H2 与 H3 分别在两个 PowerShell 窗口运行。这样 H2 的 backlog
排空与缩容能在 H3 期间继续展示，并避免 `All` 中旧的普通 Pod 删除场景替代当前 H1
跨 Pod 续写合同。

三幕的唯一观察重点：

1. **跨 Pod 连续性**：`stream=STARTED` → 删除承载流的 Pod → `history=RESTORED`。
2. **KEDA 业务压力弹性**：outbox backlog 上升时 worker `1 → N`，随后 backlog 降到 0，
   并且 `duplicate_receipts=0`。
3. **可观测性**：同一 Jaeger trace 同时出现 `inner-cosmos-api` 与
   `inner-cosmos-worker`，最后显示 `forbidden_tags=0`。

建议讲解时间为 30 秒 + 45 秒 + 30 秒；KEDA 的 cooldown 与脚本清理由机器继续完成，
不占用口头展示时间。实测记录、时间线和边界见
[`../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/summary.md`](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/summary.md)。

默认连续性场景使用普通 Pod 删除，证明现场用户体验且避免节点级危险操作。直接 JVM
`SIGKILL` 后由另一 Pod 快速接管并把同一 durable turn 完成到 `COMPLETED` 已在
`CN-ZERO-LOSS-DRAIN-003` 真实验证；课堂不再临时进入 kind 节点杀 PID。

> 目标：用一条不会混淆证据的课堂叙事证明两件事——真实用户确实可以通过
> Inner Cosmos 产生连接；Kubernetes/CNCF 能力确实保护了这条产品链路。
>
> 本文不重复实现已经冻结的 W3。它把现有 manifest、运行证据和安全操作收敛成
> 可复验展示入口。产品公网运行仍以 [`DEMO-RUNBOOK.md`](DEMO-RUNBOOK.md) 为准。

## 1. 先明确：课堂上是两套环境、一个产品叙事

| 段落 | 环境 | 现场承担的证明 | 当前真实边界 |
|---|---|---|---|
| 真实产品段 | Windows + Docker Compose + Cloudflare Quick Tunnel | 真实注册、真实 LLM、真实 embedding/TTS、记忆、共鸣体、匹配、慢信和同学间连接 | 单个 Spring Boot App 容器；不是 Kubernetes；30 人端到端容量尚未验收 |
| 云原生段 | 预先准备的本地 kind 展柜 | Pod 故障恢复、KEDA、OpenTelemetry/Jaeger、Prometheus/Grafana、Argo Rollouts、Kyverno | 使用真实 Postgres/Redis 和真实应用代码，但 W3 kind profile 的 AI 是 Mock；证明运行语义，不证明模型质量 |
| 云上佐证 | AWS Academy EKS（当次会话可用时） | Gateway、双节点拓扑、HPA、Pod 恢复、同一镜像/角色模型的可移植性 | 临时 `us-east-1` 教学账户、静态存储；不等于新加坡生产、多 AZ 数据库或商业灾备 |

不要把 Windows 产品段称作 Kubernetes，也不要把 kind 的 Mock AI 称作真实模型。
最有说服力的说法是：

> Windows 完整体证明用户价值；同一领域系统在 Kubernetes 展柜中被拆成 API、
> worker、scheduler 和 migration 角色，随后通过故障、积压和发布实验解释
> Kubernetes 为什么能保护这种体验。

## 2. 课前硬门：不要在课堂上临时搭集群

### 2.1 Windows 真实产品

```powershell
.\scripts\demo\run-public-demo.ps1
.\scripts\demo\status-public-demo.ps1
```

必须再用蜂窝网络完成一次注册和 Aurora 回复。真实 Provider 必须
`LLM_ALLOW_FALLBACK=false`；出现 401、429 或超时时应展示真实失败，不允许切回 Mock
冒充成功。

### 2.2 kind 云原生展柜

先做完全离线、无集群写操作的检查：

```powershell
.\scripts\demo\show-cloud-native-status.ps1 -Mode Offline
```

它会渲染 base、Academy、kind-full、observability、KEDA、Rollouts 和 Kyverno
Kustomize，并确认五组 W3 证据存在。

集群已经由操作者预先搭好后，再使用精确 context 防误操作：

```powershell
$expected = "kind-kubedeploy" # 以本机真实 context 为准，不要盲抄
.\scripts\demo\show-cloud-native-status.ps1 `
  -Mode Status -ExpectedContext $expected -Namespace inner-cosmos-w3
```

`Status` 只读，不会 apply、patch、delete 或安装控制器。若 context 不完全一致会直接
拒绝执行。课堂前应确认：

- `inner-cosmos-api` 至少 2 个 Ready 副本；
- Postgres、Redis、worker、scheduler Ready；
- Prometheus/Grafana、OTel Collector/Jaeger Ready；
- KEDA `ScaledObject` 为 Ready；
- Argo Rollouts 和 Kyverno 已经预装并演练；
- Docker Desktop 为 kind 和 Windows 公网 Demo 预留了足够 CPU/内存。

当前仓库没有把“创建 kind、安装 KEDA/Argo/Kyverno、造积压、清理”伪装成一个成熟的
一键脚本。现有 W3 是真实运行证据，但重新搭建仍需要按 manifest 注释和 evidence 中
的固定版本操作。最终彩排必须在课堂前完成，课堂现场只做短小、可回退的实验。

## 3. 推荐 12 分钟现场顺序

### 0:00–5:00：真实用户从陌生到连接

在 Windows 公网入口完成：

1. 两名同学注册不同账号，昵称和轻量校准不同；
2. 对比 Aurora 第一轮真实回复，强调输入语言、近况和偏好不同；
3. 完成 5–8 分钟交流后结束会话，展示记忆/画像正在沉淀；
4. 生成并预览自己的共鸣体，先与自己的共鸣体对话和纠正；
5. 发布后访问另一位同学的共鸣体，进行匹配并发送慢信。

不要等待所有异步衍生物才继续讲。PostgreSQL 是事实源；worker/scheduler 在后台生成
投影和 embedding。若向量尚未完成，产品仍可使用受边界约束的词法/结构化信号，但
不能把这种降级说成“向量已经算完”。

### 5:00–6:15：打开 Grafana，说明系统正在处理什么

提前打开端口转发：

```powershell
kubectl -n observability port-forward svc/grafana 3000:3000
kubectl -n observability port-forward svc/prometheus 9090:9090
```

Grafana 的匿名 `Viewer` 可以直接查看五张答辩看板，但不能进入 `Explore`。若要从
exemplar 跳转到 Trace，演示前应使用本地 kind 展柜的 Grafana 管理员会话登录；若不希望
在投影画面暴露登录过程，则直接使用下方独立 Jaeger 页面。这个限制只影响交互入口，不影响
Grafana 通过已配置 datasource 查询 Jaeger。

展示：

- `/d/inner-cosmos-defense`：四个契约总览，API/worker、5xx、SSE、outbox 与 Aurora
  p95 同屏；
- `/d/inner-cosmos-recovery`：普通 Pod 删除专用，观察 Ready `2→1→2`、逐 Pod
  heartbeat、Pod phase、active/replay SSE 与 5xx；
- `/d/inner-cosmos-ai`：Provider 调用、错误、fallback、延迟与明确标为 `estimated`
  的 token 量；
- `/d/inner-cosmos-product`：Aurora → 记忆/画像 → 共鸣体/匹配 → 慢信/连接的低基数
  HTTP 聚合；
- `/d/inner-cosmos-events`：outbox ready/oldest/dead 与 worker 副本联动画面。

Prometheus 在本地课堂展柜以 5 秒间隔采样，Pod 状态由 kube-state-metrics 提供；Grafana
同时注册 Jaeger datasource，带 trace exemplar 的延迟样本可以在同一界面下钻。实时
token 值仍是 `prompt/response length / 2` 的粗估，面板和指标名都明确包含
`estimated`；不得把它称作 Provider 账单用量或真实成本。Capsule 匹配质量、embedding
召回率和完整 AI 语义发布门仍没有实时生产指标，不能因看板变多而声称 OPS
可观测性已经全部关闭。

### 6:15–7:30：Jaeger 追踪 Aurora 到异步画像

```powershell
kubectl -n inner-cosmos-w3 port-forward svc/inner-cosmos-jaeger 16686:16686
```

在 `http://localhost:16686` 选择：

- `inner-cosmos-api`
- `inner-cosmos-worker`
- `inner-cosmos-scheduler`

一条已验证的链路应包含：

```text
HTTP / Aurora turn
  -> inner.cosmos.ai.provider
  -> inner.cosmos.memory.retrieve

finish dialog
  -> JDBC outbox traceparent
  -> inner.cosmos.outbox.consume
  -> inner.cosmos.projection.memory
  -> inner.cosmos.projection.profile
```

另有 scheduler 的 `inner.cosmos.wake-intent.deliver`。

必须准确说明：当前没有一条 span 树完整串起
`Aurora → memory embedding → capsule embedding → matching → slow letter`。embedding 是
60 秒轮询的 scheduler Job，matching 也尚未加入专用 Observation；现场可以展示
Aurora→检索→outbox→记忆/画像的真实跨角色 Trace，不能把缺失段口头补上。

Aurora single/adaptive/dual-kernel 的可证明调用差异、API/Prometheus/OTel 字段与措辞
边界见 [`DUAL-KERNEL-EVIDENCE.md`](DUAL-KERNEL-EVIDENCE.md)。当前 OTel 只有 runtime
级别证据，没有 planner/speaker/critic 独立子 span。

隐私亮点值得明确讲：Collector 会再次删除 `user.id`、正文、prompt、completion、
DB statement、HTTP body 和 query，W3 证据扫描结果为 0 个禁止字段。

### 7:30–8:45：删除 Pod，证明 Aurora 不绑定单个进程

先在独立终端观察：

```powershell
kubectl -n inner-cosmos-w3 get pods `
  -l app.kubernetes.io/component=api -w
```

在浏览器进行一次多气泡对话，然后删除一个 API Pod：

```powershell
$pod = kubectl -n inner-cosmos-w3 get pod `
  -l app.kubernetes.io/component=api `
  -o jsonpath='{.items[0].metadata.name}'
kubectl -n inner-cosmos-w3 delete pod $pod
```

预期：

- Service 仍有另一个 Ready API；
- Deployment 自动补回副本；
- Redis Session 仍有效；
- 正常 Pod 删除通过 readiness、`preStop`、45 秒 graceful termination 完成当前 SSE；
- 真正 SIGKILL 时，scheduler 会把孤儿 turn 标为 `INTERRUPTED`，重连收到
  `replay.completed`，不会无限转圈。

现场看板中 active SSE 是当前连接数，不代表 HTTP 连接能迁移到另一 Pod。普通删除依靠
graceful drain 让当前流在原 Pod 上结束；硬 SIGKILL 则依靠客户端 cursor、Redis live
buffer、PostgreSQL timeline 与 Recovery Job 在重连后恢复/结算。两种实验必须分开讲。

课堂只做普通 Pod 删除。SIGKILL 已有真实证据，不应临场进入 kind 节点杀宿主 PID。
兜底材料：

- `evidence/w3/CN-ZERO-LOSS-DRAIN-001/summary.md`
- `evidence/w3/CN-ZERO-LOSS-DRAIN-002/proof.md`

### 8:45–9:45：KEDA 解释业务指标弹性

```powershell
kubectl -n inner-cosmos-w3 get scaledobject,hpa,deploy -w
```

KEDA 使用的是业务积压数量和最老任务年龄，而不是 API CPU：

- idle：worker 1；
- 积压上升：最多扩至 6；
- 多 worker 使用 lease/`SKIP LOCKED` 竞争；
- inbox receipt 保证副作用 exactly once；
- 清空后按 cooldown 缩回 1；
- Prometheus/KEDA 不可用时保留 1 个 worker fallback。

仓库已有真实 `1 → 6 → 3 → 1`、杀 worker 后 lease 重领、0 重复 inbox 的证据：
`evidence/w3/CN-EVENT-DRIVEN-AUTOSCALING-001/summary.md`。

目前没有安全的一键“造 30 名真实用户积压”脚本。若课前没有用隔离测试数据完成
rehearsal，课堂上只展示已准备好的指标曲线和 evidence，不要临时直接向生产 Demo
数据库插入 outbox 行。

### 9:45–10:45：Argo Rollouts 自动止损

Argo 展柜在隔离 namespace `inner-cosmos-rollouts`，不是 Windows 产品实例。先展示状态：

```powershell
kubectl -n inner-cosmos-rollouts get rollout,analysisrun,rs,pod
```

已验证的发布顺序是 `25% → Prometheus analysis → 50% → pause → 100%`。坏版本 readiness
失败超过 60 秒会 `progressDeadlineAbort`，坏 ReplicaSet 缩到 0，stable 继续服务。

课堂若要复演，只能在确认是隔离 kind context 后修改该 Rollout，并在结尾恢复：

```powershell
kubectl -n inner-cosmos-rollouts patch rollout inner-cosmos-api `
  --type=json `
  -p='[{"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe/httpGet/path","value":"/actuator/health/never-exists"}]'

kubectl -n inner-cosmos-rollouts get rollout,rs,pod -w

# 结束后回到仓库声明的安全状态
kubectl apply -k deploy/k8s/extensions/rollouts
```

边界：当前 analysis 是 Prometheus 存活信号，不是完整的 Aurora 语义质量门；Rollout
展柜使用 H2 + Mock，且没有证明 stable/canary 共享 PostgreSQL 时的 expand-contract
兼容。可以讲自动回滚机制，不能讲“AI 质量自动回滚已经完整生产化”。

### 10:45–11:30：Kyverno 在调度前拒绝风险

先展示当前策略：

```powershell
kubectl get clusterpolicy
```

用 server-side dry-run 触发 admission，避免创建垃圾 Pod：

```powershell
kubectl apply --dry-run=server `
  -f evidence/w3/CN-POLICY-AS-CODE-001/violating-root.yaml
kubectl apply --dry-run=server `
  -f evidence/w3/CN-POLICY-AS-CODE-001/violating-no-resources.yaml
kubectl apply --dry-run=server `
  -f evidence/w3/CN-POLICY-AS-CODE-001/violating-latest.yaml
```

预期分别拒绝 root、无 resources 和 `:latest`。这能说明资源弹性为何可信，也说明承载
心理/关系数据的工作负载不能绕过基本安全边界。

边界：现有三条策略没有验证 Cosign 签名，也不是完整 production policy 集；Academy
能否安装 webhook 必须由当次权限 probe 决定。

### 11:30–12:00：用限制收尾，体现工程判断

一句话收束：

> 我们没有把所有东西都塞进 Kubernetes。Windows 完整体以让 30 位同学获得真实
> 产品体验为目标；Kubernetes 展柜则用故障、积压、Trace、渐进发布和策略拒绝证明：
> 当这类长期陪伴与连接系统扩大时，我们知道怎样保持连续性、一致性和可解释性。

## 4. 30 人瞬时并发：当前是门禁，不是已经通过

现有容量证据包括：

- 24 线程、10 用户、180 次 H2 记忆检索；
- 24 线程、10 用户、两轮合计 360 次真实 PostgreSQL + Redis 记忆检索；
- kind HPA `2 → 4 → 2`；
- EKS HPA `2 → 4`；
- KEDA worker `1 → 6 → 3 → 1`。

这些不能推出“30 人同时使用真实 LLM 全功能已经通过”。当前 Windows 公网 Demo 只有
一个 App 容器；AI executor 为 `4/8 threads + queue 100`，数据库池和真实 Provider
额度/并发也会形成瓶颈。课堂前必须另做一次不含真实隐私数据的 30 账户彩排，至少同时
覆盖：

1. 注册、昵称与轻量校准；
2. 每人一次真实 Aurora 主动开场和一次对话，Mock/fallback 为 0；
3. 结束会话后的 outbox、记忆与画像物化；
4. embedding 生成、共鸣体编译/发布；
5. 30 人互相发现或匹配，至少一封慢信，且没有人落入“无候选”死路；
6. 0 跨账号数据、0 重复副作用、0 丢信、0 非预期 5xx；
7. 记录 Provider 429/timeout、TTFT/完整回复 p50/p95/p99、DB pool、AI queue、
   outbox oldest age、embedding remaining、CPU/内存和隧道错误；
8. 同一配置至少连续通过两轮。

在该 rehearsal 通过之前，课堂措辞只能是：

> 架构和子链路具备并发、幂等和弹性证据；30 人真实 Provider 端到端容量验收仍在
> presentation readiness gate 中。

不能把调高 rate limit 当成容量证明。Provider 配额、Windows CPU/内存、Cloudflare
Quick Tunnel 和单 App JVM 都必须进入同一份报告。

### 4.1 Windows 30 人真实 burst 验收入口

在公网 Demo 已经通过 `status-public-demo.ps1` 后，用 PowerShell 7 执行：

```powershell
.\scripts\demo\test-30-user-burst.ps1 `
  -Origin "https://本次地址.trycloudflare.com"
```

默认合同：

- 同时创建 30 个随机 HUMAN 账号；
- 每个账号写入不同昵称、回应语气、主动性、反思深度和当下关注；
- 以 throttle 30 并发执行注册、校准和一轮 `/api/v1/aurora/message-rich`；
- 要求 Provider 不是 Mock、API Key 已配置、fallback 被关闭，planner/speaker 未降级；
- 输出注册/校准的 p50/p95、Aurora 总延迟的 p50/p95/p99，以及 HTTP 429 数；
- 重新登录 30 个账号，要求每个人都能发现其余 29 人；
- 创建并接受 30 条环形连接，每人至少连接前后两位同学；
- 默认删除全部测试账号，报告不保存用户名、密码、prompt 或回复正文；
- 任一用户失败、出现 429、超过默认 p95 门槛、发现不完整、有人落单或清理失败时，
  脚本以非零状态结束。

默认报告写入被 Git 忽略的：

```text
.demo-runtime/burst-30-report.json
```

默认门槛为注册 p95 15 秒、校准 p95 10 秒、Aurora 完整响应 p95 90 秒；可以在正式
压测合同确定后通过参数收紧，不应为了让失败的彩排变绿而临时放宽。排查清理逻辑时可
显式使用 `-KeepAccounts`，但课堂 readiness 的正式 PASS 必须使用默认清理。

这是一条 Windows/PowerShell 7 验收入口，不能因为脚本可解析或子链测试通过就写成
“Mac 已实测 30 人通过”。只有目标 Windows、当次真实 Provider 配额和本次公网地址
输出 `CLASSROOM_BURST_PASS` 后，才能关闭 30 人 presentation readiness gate。

该脚本有意保持为可重复、可清理的短 burst：它验证注册/校准/一轮真实 dual-kernel
Aurora/同伴发现/好友环，不替代本节上方的完整两轮 rehearsal。它尚未验证 5–8 分钟
多轮对话、finish 后沉淀、embedding、共鸣体编译/发布、匹配、慢信投递、DB pool 与
outbox 排空时间；因此单次 `CLASSROOM_BURST_PASS` 也不能被描述成“30 人全功能容量
已经通过”。

## 5. 现场失败时的 60 秒兜底

| 现场问题 | 立即切换 |
|---|---|
| Windows Provider 429/超时 | 明示真实 Provider 失败；切到预置沙箱展示长期数据，不启用 Mock |
| Quick Tunnel 断开 | 使用本机 URL 给讲台演示；台下互动暂停，不能谎称公网正常 |
| kind Pod 未 Ready | 不做 delete；展示 `CN-ZERO-LOSS-DRAIN-002/proof.md` 的时间线 |
| KEDA 没 scale | 展示 ScaledObject 配置、Prometheus backlog 曲线和 `CN-EVENT-DRIVEN-AUTOSCALING-001` |
| Jaeger 没有 fresh trace | 展示 `CN-OTEL-SEMANTIC-TRACE-001/summary.md`，说明 backend 为易失存储 |
| Argo/Kyverno controller 不可用 | 展示已经留存的 `run-log.txt`；不在课堂安装 CRD/webhook |
| Academy 会话失效 | 保留 kind 演示；说明 Learner Lab 四小时凭据与静态 PV 是教学环境约束 |

## 6. 可以说与不能说

可以说：

- Pod 普通删除和 JVM SIGKILL 恢复都在真实 kind + PostgreSQL + Redis 上验证过；
- KEDA 从真实 outbox backlog 扩到 6 个 worker，并验证 lease recovery/exactly once；
- OTel Collector + Jaeger 真实贯穿 API/outbox/worker/memory/profile/WakeIntent，且剥离敏感字段；
- HPA、PDB、rolling update、NetworkPolicy、Argo 自动 abort、Kyverno admission 均有 live evidence；
- 真实 EKS 曾验证 Gateway、双节点拓扑、HPA 和 Pod recovery。

不能说：

- Windows 公网 Demo 本身运行在 Kubernetes；
- 30 人真实 LLM 全功能并发已经通过；
- 当前已有 `Aurora → embedding → matching → slow letter` 单一完整 Trace；
- Argo 已基于 AI 质量而非健康信号完成生产自动回滚；
- Academy 静态 PV 等同于多 AZ 数据耐久；
- 已经实现 Argo CD、Tempo、Helm Chart、Cilium/Hubble、Velero 或商业新加坡生产环境。

这种边界不是削弱展示，而是云原生课程最值得讲的部分：知道一个实验究竟证明了什么，
也知道它没有证明什么。
