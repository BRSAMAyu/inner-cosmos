# 30 分钟答辩大纲：从容器编排到语义可靠性

> 日期：2026-07-26 · 时长：30 分钟**纯宣讲，不含提问环节** · 听众：云原生 / Kubernetes 课程为主
> 讲者：4 人（1 名整合者 + 3 名链路主讲）· demo：**三条链路全部现场实跑**（kind 集群）
>
> 本文是讲解内容的权威大纲。所有"可以声称"与"不可声称"的边界都已对照
> `evidence/w3/` 与 `src/main` 核实。**做幻灯片前先读第五节（事实台账）和第十一节（禁止说法）。**

---

## 一、Topic

**《从容器编排到语义可靠性：先进 AI Agent 的云原生运行范式》**

副标题：以 Inner Cosmos 的长期对话、异步记忆与安全发布为实验场

**标题页必须有的定义行**（否则"语义"会被听成"模型输出的语义质量"）：

> 本文的"语义"指**业务语义与领域不变量**（business semantics / domain invariants），
> 不是自然语言语义。

备选问题式标题：《Kubernetes 编排容器，谁来编排业务不变量？》

**开场措辞注意**：可以说"生成 Deployment、HPA 和监控配置是容易的，难的是……"，
但**不要提 Coding Agent**——那会在第一分钟主动邀请"那你们自己写了多少"这个问题。

---

## 二、主命题

Kubernetes 的控制循环是**有意 domain-agnostic** 的。因此把领域不变量暴露给这些控制循环，
是**设计上应由应用承担的职责，而不是 Kubernetes 的缺陷**。

我们不主张"Kubernetes 不适合 AI"。我们主张：

> 传统 Web 工作负载下常见的 Kubernetes 运维模型，在先进 AI Agent 上出现**四种语义失配**；
> 工程工作的实质，是把 AI 的连续性、质量、积压与因果关系，翻译成控制面能够观察、决策和验证的**契约**。

### 四种语义失配 → 四个契约

| 常见运维抽象 | AI Agent 的真实情况 | 需要升级为 | 讲者 | 本项目证据 |
|---|---|---|---|---|
| 请求生命周期短，失败后可重新请求 | 一次 Aurora turn 是长连接、有中间状态、有成本与副作用；朴素重试会重复模型调用与记忆写入 | **连续性契约** | 组员 1 | `CN-ZERO-LOSS-DRAIN-001/002` |
| Pod 活着，服务就健康 | Prompt、解析率、fallback、TTFT 可能已退化，而 JVM / HTTP / liveness 全绿 | **语义健康契约** | 你 | `CN-PROGRESSIVE-DELIVERY-001` + `CN-POLICY-AS-CODE-001` |
| CPU / QPS 可以代表负载 | 真正影响用户的是 memory / profile / capsule projection 的 backlog 与 oldest age | **工作压力契约** | 组员 2 | `CN-EVENT-DRIVEN-AUTOSCALING-001` |
| 单服务指标足以解释问题 | 一次 turn 横跨 HTTP、Provider、检索、outbox、Worker、投影与 WakeIntent | **因果可观测契约** | 组员 3 | `CN-OTEL-SEMANTIC-TRACE-001` |

**收口句：** Kubernetes 提供通用控制循环；AI 应用必须把自己的业务不变量显式暴露给这些控制循环。

---

## 三、开场 3 分钟：Inner Cosmos 产品与 AI 系统速览

**目的**：让同学感到"这个 AI 系统和应用层很厉害"，同时让四种失配的前提**具体化**——
不用抽象列举四个属性，看完图它们自己掉出来。

**纪律**：3 分钟 = **最多 3 张图**。答辩节奏下塞 6 张必崩。这一段**不做任何实时点击**。

**现成素材**：组员那份 `docs/presentation/inner-cosmos-25min-defense.html` 里已有这三张的原型
（`从倾诉到连接：一条连续、可撤回的业务链`、`Aurora：从回复生成器到可恢复 Agent Runtime`、
`不是"记得越多"，而是理解可被检查`、`Echo Capsule · 共鸣体`）。**直接抬过来，不要重做。**

### 图 1（1 分钟）· 全链路架构图 —— 这张图一图两用

```text
五空间 AppShell → API → Aurora Runtime（context assembly → 检索 → Provider Gateway）
              → transactional outbox → Worker → memory / profile / 共鸣体 projection
              → WakeIntent 主动回访
```

**这张图同时是后面三条 demo 的地图。** 组员 1/2/3 每人开讲时**回指这张图**说"我们现在在这条线的
这一段"。这样应用层与云原生不是被一句话缝起来的，而是字面上同一张图的不同段落——这是全场衔接
最省力也最有效的手段。

### 图 2（1 分钟）· 不是"记得越多"，而是理解可被检查

记忆带 provenance、用户可纠正。

> 我们不追求记得更多，而追求**理解可以被检查和纠正**。

这是与"套了聊天框的 RAG"最硬的区别。

### 图 3（1 分钟）· Echo Capsule 共鸣体 → 慢连接

用户模型在**明确授权**下编译成有界 Echo Capsule → 共鸣匹配 → 再决定是否成为真实人际连接。

> 先低风险地理解，再决定是否成为人际连接。

### 收尾 20 秒：把四个属性挂到刚看过的东西上

- 那条 SSE 是**数十秒长连接**；
- 一次 turn 有**模型成本 + 持久时间线 + 记忆副作用**，不能朴素重放；
- 对话结束后 memory / profile / 共鸣体是**异步派生**；
- prompt、模型、策略**有版本**，不崩溃也可能变笨。

> 它不是一个套了聊天界面的普通 Web 应用，而是同时具有长生命周期状态、不可朴素重放副作用、
> 异步派生状态和版本语义的 AI Agent 系统。

### 产品的"活"感由 H1 免费提供

前面不要放实时点击——太贵。**组员 1 的 demo 开头本身就是真实的 Aurora 多气泡逐字输出**：
让他起流后**先让它跑 8–10 秒不动手**，说一句"这就是用户此刻看到的 Aurora"，再开始杀 Pod。
零额外时间成本，而且这个产品瞬间出现在云原生环节里，比放在开头更能证明"产品和编排是一件事"。

---

## 四、五层状态责任模型（在 20:40 作为**综合结论**交付，不要放在开场）

这张表是从三条链路里**推导出来的结论**，不是前提。放在开场等于要求听众抱着抽象表格熬 11 分钟才
看到证据；放在三条链路之后，它从"设置"变成"回报"。

注意：**客户端只持有游标，不能独立恢复 token；恢复是多方契约。**

| 状态层 | 权威位置 | 恢复参与者 | 关键机制 |
|---|---|---|---|
| 实时展示状态 | Redis live buffer + 客户端 cursor | 客户端与 API **共同**恢复 | event ID、`Last-Event-ID`、去重、terminal event |
| Turn 权威状态 | PostgreSQL timeline | API + Recovery Job | turn / bubble / event 持久化、孤儿对账、`INTERRUPTED` |
| 长期派生状态 | PostgreSQL outbox / inbox | Worker | lease、`SKIP LOCKED`、幂等、projection receipt |
| 语义版本状态 | 镜像、Prompt / 模型 / 策略版本 | 发布控制面 + 应用质量信号 | progressive delivery、quality gate、rollback |
| 部署安全状态 | Admission policy / CI | Kubernetes 控制面 | Kyverno、digest、non-root、resources、kill switch |

**最深的一句：**

> Kubernetes 不应该被要求"自动保住一切"；成熟的 AI 云原生系统应明确**每一层状态的权威位置、
> 恢复责任、终止语义和证据**。

---

## 五、已核实的事实台账（幻灯片只能写这一列）

以下全部对照 `evidence/w3/` 与 `src/main` 核实。

| 项 | 核实结果 |
|---|---|
| OTel 接法 | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`。**无 Java agent，无手写 OTel SDK，`addLink` 零结果** |
| 跨异步边界机制 | outbox 行只携带 W3C `traceparent`（无 baggage、无私有数据）；worker 从该远程父起 `CONSUMER` span |
| 实测 trace | `trace=a86c0453fff1b3db621614c1702ab994`，span count 8，`forbidden attribute keys: 0`；三个 role 各自独立 service resource |
| trace 链形状 | `api\|http post /api/dialog/session/{id}/finish` → `api\|secured request` → `worker\|inner.cosmos.outbox.consume` → `worker\|projection.memory` + `worker\|projection.profile` |
| KEDA 指标 | `OutboxQueueMetrics` **只**导出 backlog count 与 oldest-ready age，不含 CPU |
| KEDA 实测 | baseline 135 ready rows；worker `1 → 6` 约 15 秒；`6 → 3 → 1` 按声明的 stabilization policy；`ScaledObject Ready=True` |
| lease 实测 | 事件置 `PROCESSING` + 20s lease → 删 worker Pod → lease 过期后另一 worker 完成 → `PUBLISHED`，**1 个 inbox receipt，0 重复** |
| **孤儿结算耗时** | `kill -9` 于 ~05:34:54 → turn 落到 `INTERRUPTED` 于 05:35:16.87，**约 22 秒**（由可配的 `stale-after` 决定，测试设 `PT20S`） |
| **重连耗时** | 结算后重连 **1.62 秒**拿到显式 `data:{"turnId":7,"lastSequence":8,"turnStatus":"INTERRUPTED"}`；文档表述为 `~1.2-1.6s` |
| **修复前行为** | `-001`：挂到 120 秒 `SseEmitter` 超时。`-002` 那次用 10 秒 curl 超时，所以挂了 8 秒。**别把 120s 安到 `-002` 上** |
| 回放诚实性 | 已缓冲的 live token **原样回放**，然后流诚实报告真实终态；**没有伪造内容，也没有丢内容** |
| Rollouts 实测 | `progressDeadlineSeconds: 60` + `progressDeadlineAbort: true` 触发 abort；`AnalysisTemplate aurora-canary-health` 查询 `count(up{namespace="inner-cosmos-rollouts"} == 1)` |
| Rollouts 关键观察 | 坏版本 Pod 始终 `0/1 Ready` → **从未进入 Service endpoints → 从未收到任何流量**，尽管 canary 权重配了 25% |
| 恢复代码 | `ConversationTurnRecoveryJob.java` 存在；`INTERRUPTED` 在 `ConversationChoreographyServiceImpl` / `ConversationTimelineController` 真实使用 |
| 测试实况 | `.\mvnw.cmd test` → 1269 total、0 断言失败、11 项 Docker/Testcontainers 环境错误、8 项环境跳过。**标注为 not full PASS** |

---

## 六、王牌：演练会证伪你的假设

**归属**：组员 1 讲**机制**，你讲**认识论**。同一个故事绝不能两个人各讲一遍。

`CN-ZERO-LOSS-DRAIN-001` 的 Test 1 **"通过"了，但通过的理由是错的**：

- 实测发现 `kubectl delete pod --force --grace-period=0` **只立刻删除 API 对象，并不绕过容器
  自身的 grace period**。kubelet 照样跑完 `terminationGracePeriodSeconds: 45` /
  `preStop: sleep 15` / graceful shutdown——故障根本没注入进去。
- 于是改用 `docker exec … kill -9 <jvm-pid>` 真正硬杀 JVM。**这一次找到了真 bug**：turn 永久
  卡在非终态 `PLANNED`，客户端重连一直挂到超时。
- 修复 `6832043`：`ConversationTurnRecoveryJob` 结算孤儿 + reconnect controller 返回显式
  terminal event。此前只有单元/组件测试覆盖，`CN-ZERO-LOSS-DRAIN-002` 在真集群重新实证。

**为什么值 1.5 分钟：**

1. 这是**推翻自己已通过的测试**的故事——研究方法论层面的可信度。
2. 找到的 bug **正好是本论点预测的失效类型**：非终态 + 缺 terminal event = 客户端永久悬挂。
3. 它证明故障注入的产出不是绿灯，而是"我们原本不知道自己有这个 bug"。

**三条链路各撞出一个真缺陷——只有整合者能横着串这三条：**

| 链路 | 演练撞出的真实缺陷 |
|---|---|
| Drain | `--force` 未绕过 grace period → 改真 SIGKILL → 发现孤儿 turn + reconnect 悬挂 |
| OTel | `kind-full` 激活 `dev,postgres`，只写在 `application-prod.yml` 的 alias 从未启用 export；overlay 改为直接绑 `MANAGEMENT_OTLP_*` |
| Rollouts | `w3-otel` 镜像的 readiness health group 列了 `redis`，首次尝试直接崩 |

> 故障演练的价值不是证明我们原来就是对的，而是**设计一个足以推翻自己的实验**。

---

## 七、时间轴（4 人 · 30 分钟）

| 时刻 | 时长 | 讲者 | 内容 |
|---|---|---|---|
| 0:00 | 0.75' | 你 | Topic + "语义"定义行 |
| 0:45 | 3' | 你 | **产品与 AI 系统速览（3 张图）** → 导出四个属性（第三节） |
| 3:45 | 2' | 你 | 四种失配 → 四个契约（属性已具体化，只提问题不展开技术） |
| 5:45 | 0.25' | 你 | 交接 H1 |
| 6:00 | **4.75'** | 组员 1 | H1 连续性契约（**开头留 8–10 秒让 Aurora 流跑**）→ 收尾说认识 1 |
| 10:45 | 3.25' | 组员 2 | H2 工作压力契约（注入合成积压 → 解除 ScaledObject 暂停）→ 收尾说认识 3 |
| 14:00 | 3' | 组员 3 | H3 因果可观测契约（KEDA 后台扩容时讲）→ 收尾说认识 4 |
| 17:00 | 0.67' | 组员 2 | KEDA 回看：`1→6`、backlog 下降、0 重复。**不等 `N→1`** |
| 17:40 | 3' | 你 | 第四契约语义健康：Kyverno 现场拒绝 + Rollouts 诚实边界 + 权重≠endpoints。含认识 2 |
| 20:40 | 2.5' | 你 | **五层状态责任模型**（作为三条链路的综合结论，第四节） |
| 23:10 | 1.5' | 你 | 王牌：演练的价值是证伪 + 三条链路各撞出的缺陷（第六节） |
| 24:40 | 2.33' | 你 | claim boundary + 主动没做什么（第十节） |
| 27:00 | 1.75' | 你 | 收口 |
| 28:45 | 1.25' | — | 弹性（某条链路卡住时吸收） |

你 17.1 分钟，组员合计 11.7 分钟。你有一个现场动作（Kyverno），不是全场唯一不碰集群的人。

---

## 八、四人分工与交接词

| 讲者 | 契约 | 负责 |
|---|---|---|
| **你** | 总框架 + 语义健康契约 | Topic、产品速览、四种失配、Rollouts/Kyverno、五层状态、方法论、边界、收口 |
| 组员 1 | 连续性契约 | H1：Drain、SIGKILL、Recovery Job、terminal event |
| 组员 2 | 工作压力契约 | H2：业务指标、KEDA、outbox、lease、端到端一次效果 |
| 组员 3 | 因果可观测契约 | H3：W3C traceparent、跨角色 trace、隐私约束 |

### 每条链路收尾的"认识"（留在各自链路，不要在 21:00 轮流回来复述）

- **认识 1（组员 1）**：零损失不是 PDB 或 graceful shutdown 的单项能力，而是 Kubernetes 生命周期、
  应用状态机、数据权威和客户端恢复协议共同形成的**组合性质**。
- **认识 2（你，在 17:40 段）**：AI 的健康是语义健康。`Pod Running` 和 `HTTP 200` 不能证明用户体验、
  安全与模型行为仍然正确。
- **认识 3（组员 2）**：扩缩应追踪**用户等待**而非机器忙碌；而扩容只增加吞吐、不自动产生正确性——
  正确性来自 outbox、lease、`SKIP LOCKED`、inbox 唯一约束和 handler 幂等。**exactly-once 是端到端
  业务效果，不是任何单一组件的开关**；外部 Provider 副作用仍需独立幂等或 receipt。
- **认识 4（组员 3）**：**可解释 ≠ 可泄露**。trace 必须保留因果，但不能泄露 P0 内容。

### 交接词（照念）

- **你 → H1**：接下来我们用三条真实实验验证这套框架。第一条先回答最直接的问题：当承载一次
  Aurora 对话的 Pod 消失，究竟什么必须继续存在？
- **H1 → H2**：连续性回答了"不能丢"，但先进 AI 系统还有第二个问题：异步工作越来越多时，
  不能让用户一直等。
- **H2 → H3**：现在系统可以恢复、也可以扩容，但如果一次用户行为跨越多个运行角色后失去因果关系，
  我们仍然无法解释它为什么慢、为什么失败。
- **H3 → H2（回看 KEDA）**：在我们查看这条因果链的同时，KEDA 的控制循环仍在后台运行。
  现在回到 Worker，看业务压力是否真正下降。
- **H2 → 你**：前三条契约解决了连续性、工作压力和因果解释。最后还剩一个问题：一个版本即使能够运行，
  也不代表它仍然值得接收用户流量。

### 操作纪律

- **每个组员操作自己那条链路**（讲自己的演练同时敲命令是自然且显得熟练的）。不要设"固定操作员"——
  那要求一个人把三条链路的命令全背下来。
- **你做计时 + 报数 + 切证据的触发者**：开着 evidence 文件，某条卡住你直接说"切证据"。
- **所有命令预先写进编号脚本或贴好标签的终端分屏，现场只按 Enter，不打字。** 台上打字是答辩翻车的
  头号原因。
- 报数要非常具体："请看左侧 SSE 输出" / "现在看 Pod 的 Ready 状态" / "请看 Worker 副本数" /
  "这里看 span 的父节点" / "请注意这个 payload 里没有用户正文" / "这一项是合成压力，但走真实处理路径"。
- **卡住立即切 evidence，不在台上排障。**

---

## 九、现场实验脚本（一条 Aurora 因果链驱动）

三条链路全部现场实跑。**Argo Rollouts 不现场等 progression**，用已有的 AnalysisRun / Prometheus
结果与回滚时间线讲解。

| 时刻 | 动作 | 评委该盯哪里 |
|---|---|---|
| 6:00 | `kubectl -n inner-cosmos-w3 get pods` | 真集群：api×2、worker、scheduler、postgres、redis |
| 6:20 | 发起带减速触发词的 Aurora turn（2 气泡，30ms/2 字符 + 220ms 气泡间停顿）；**先跑 8–10 秒不动手** | 真实 Aurora 逐字输出——这是产品瞬间 |
| 7:00 | **1a**：`kubectl delete pod <api-pod>`（优雅） | 流**没有断**，turn 跑完，`status=COMPLETED`、两 bubble `COMMITTED` |
| 8:00 | 讲 `--force` 的发现（**只讲不操作**，认识论归整合者） | "我们最初以为注入了故障，其实没有" |
| 8:40 | **1b**：`docker exec … kill -9 <jvm-pid>` 真硬杀 | 约 22 秒后 scheduler 的 recovery job 把孤儿结算为 `INTERRUPTED` |
| 9:40 | 重连（`Last-Event-ID` 与新 cursor 两种） | **1.62 秒**拿到显式 `turnStatus: INTERRUPTED`；已缓冲 token 原样回放，无伪造无丢失 |
| 10:45 | `POST /api/dialog/session/{id}/finish` → 注入合成积压 → 解除 `ScaledObject` 暂停 | **口头声明：积压是合成的，但走真实 schema / repository / worker / lease / inbox 路径** |
| 14:00 | Jaeger 打开该 trace（KEDA 后台扩容中） | `api` → `secured request` → `worker\|outbox.consume` → `projection.memory` + `projection.profile`；三 role 独立 service；属性无正文 |
| 17:00 | `kubectl get pods -l role=worker`、backlog 指标曲线 | worker `1 → 6`，backlog count 在掉 |
| 17:40 | **Kyverno 10 秒**：`kubectl apply -f violating-root.yaml`、`violating-latest.yaml` | admission 直接拒绝；再 apply `compliant-pod.yaml` 通过 |
| — | **不等 scale-down**，展示预存的 `6 → 3 → 1` 时间线 | — |

### 每条链路的 10 秒回退动作

| 链路 | 卡住时立刻切到 |
|---|---|
| Drain | `evidence/w3/CN-ZERO-LOSS-DRAIN-001/summary.md` 的 T0–T3 时间戳 + 三张 SQL 结果表 |
| SIGKILL / recovery | `evidence/w3/CN-ZERO-LOSS-DRAIN-002/proof.md` |
| KEDA | `CN-EVENT-DRIVEN-AUTOSCALING-001` 的扩缩时间线 + `ScaledObject Ready=True` |
| OTel | 预存的 `trace=a86c…b994` span 树文本 |
| Kyverno | `evidence/w3/CN-POLICY-AS-CODE-001/run-log.txt` |

### 彩排前必须就位（Pre-flight）

- [ ] kind 集群 `kubedeploy` 起来，`deploy/k8s/overlays/kind-full` 已部署，镜像已构建
- [ ] scheduler 设好 `INNER_COSMOS_AURORA_TURN_RECOVERY_STALE_AFTER=PT20S`、
      `INNER_COSMOS_AURORA_TURN_RECOVERY_POLL_DELAY_MS=5000`（仅 config，不改代码）
- [ ] overlay 已直接绑定 `MANAGEMENT_OTLP_*` / 采样 / resource attribute
      （`application-prod.yml` 的 alias 在 `dev,postgres` profile 下不生效）
- [ ] Collector + Jaeger + Prometheus 全部就绪
- [ ] KEDA 已装，`ScaledObject` 处于 **paused**
- [ ] Kyverno 已装，策略已生效
- [ ] 测试账号已注册、dialog session 已创建、CSRF token 与 cookie jar 就绪
- [ ] port-forward：目标 api pod（确定 JVM host PID）、`svc/inner-cosmos-api`、Jaeger、Prometheus
- [ ] 合成积压注入脚本就绪
- [ ] 浏览器标签页预开：Jaeger、Prometheus；终端分屏贴好标签 `H1-1`…`H3-n`
- [ ] `Last-Event-ID` 重连命令已写好在剪贴板
- [ ] **全程至少完整彩排一次**，掐表记录每条链路真实耗时

---

## 十、claim boundary（24:40，2.33 分钟）

### 三环境矩阵

| 环境 | 允许得出的结论 |
|---|---|
| `local-kind-showcase` | 证明插件、故障与业务语义**真运行**；不证明 AWS 托管生产 |
| `academy-eks` | **只**证明当次 session 的权限、容量、CNI / controller 下实测通过的能力 |
| `commercial-sg` | 当前是生产目标，不是运行结论；未获账号时保持设计或 external gate |

### 三条主动公开的边界

1. 进程被 `kill -9` 硬杀时，正在生成的那一次 turn 无法续流；能保证的是它在约 22 秒内被结算为终态
   并在重连时返回显式 terminal event，客户端不悬挂。
2. Rollouts 的自动回滚由坏 readiness + `progressDeadlineAbort` 实证；**AI 质量指标驱动的语义回滚
   尚未实证**。
3. KEDA 的积压是合成注入的（走真实 schema / worker / lease / inbox 路径）；Academy 可移植性是单独
   声明的环境边界。

### 我们主动没有做什么（挑三个讲即可）

- 不为展示云原生把模块化单体强拆成微服务
- 不为增加名词数量引入 Kafka / Dapr / Service Mesh / Knative
- 不把同节点静态 PV 备份包装成跨区域灾备
- 不让 Aurora 主 SSE 经过 scale-to-zero 或重 sidecar 路径
- Academy 无权限安装 controller 时，不伪称 KEDA / Kyverno 已运行
- 不做"每个用户一个 Pod / CRD"这类炫技

准则：

> 一个组件只有同时具备**产品连接、真实成功证据、失败证据、资源边界、kill switch 和环境 fallback**，
> 才值得进入主演示。

核心句：

> 工程成熟度不仅体现在采用什么，也体现在知道什么暂时不值得采用。

---

## 十一、禁止说法清单

现场任何人说出左列任一句，都是可被当场击穿的。

| ❌ 不要说 | ✅ 要说 |
|---|---|
| "我们用了 span link" | "单条 outbox 事件用 W3C `traceparent` 远程父传播保持父子因果；未来出现一对多 fan-out、批处理或跨长时间窗口的派生任务时，才应使用 span link，以避免伪造同步调用树" |
| "客户端在 1.2–1.6 秒内获得终止信号" | "孤儿 turn 由 recovery job 在**约 22 秒**内结算为 `INTERRUPTED`（阈值 `stale-after` 可配，测试设 20 秒）；结算后重连在 **1.62 秒**内拿到显式终止事件，而不是静默悬挂" |
| "AI 质量门触发了自动回滚" | "实证了 weighted canary + 真 Prometheus 上的 AnalysisRun + `progressDeadlineAbort` 自动回滚机制；AI 质量指标作为分析查询是设计方向" |
| "我们实现了 exactly-once" | "端到端一次效果，由 outbox + lease + `SKIP LOCKED` + inbox 唯一约束 + handler 幂等共同保证；外部 Provider 副作用仍需独立幂等或 receipt" |
| "835 个测试全部通过" | "1269 项，0 断言失败，11 项因本机无 Docker 引擎报环境错误，8 项环境跳过" |
| "KEDA 在 Academy EKS 上跑通了" | "local-kind 实证；Academy 可移植性是单独声明的环境边界" |
| "一次对话产生了这个积压" | "合成积压，走真实 schema / repository / worker / lease / inbox 路径" |
| "`kubectl delete --force` 把进程硬杀了" | "`--force` 只立即删除 API 对象，并不绕过容器自身的 grace period；真硬杀用 `kill -9`" |
| "修复前挂了 120 秒"（指 `-002`） | "`-001` 挂到 120 秒 `SseEmitter` 超时；`-002` 那次测试用 10 秒 curl 超时，挂了 8 秒" |
| "我们有跨区域灾备" | "同节点静态 PV 备份 / 恢复" |
| "Kubernetes 有三个错误假设" | "传统 Web 工作负载下常见的 Kubernetes 运维模型，在 AI Agent 上出现四种语义失配" |

---

## 十二、最终收口（27:00，1.75 分钟）

> 通过 Inner Cosmos，我们最终学到的不是如何把一个 AI 应用部署到 Kubernetes。
>
> 我们学到的是：当 AI Agent 开始拥有长对话、长期记忆、主动行为和版本化人格后，系统真正需要保护的
> 已经不只是进程，而是**用户正在经历的业务不变量**。
>
> Kubernetes 提供生命周期、调度和控制循环；应用提供状态权威、幂等、质量指标和终止语义；
> 可观测系统连接因果；策略与渐进发布限制风险。
>
> 所以，AI Agent 的云原生化，不是把模型服务装进 Pod，而是把**连续性、语义健康、业务压力、
> 因果关系和恢复责任**，变成控制面可以观察、决策和验证的契约。

---

## 十三、待定项

- 第 6:00 段是否保留 lease drill（现场时间紧则砍，用回退证据替代）
- 三张产品图从 `inner-cosmos-25min-defense.html` 抬取后是否需要统一视觉风格
- `docs/presentation/` 尚未纳入 git
