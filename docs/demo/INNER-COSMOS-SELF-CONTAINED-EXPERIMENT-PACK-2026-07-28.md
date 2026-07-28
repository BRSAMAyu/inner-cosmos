# Inner Cosmos 自包含实验结果素材包

版本日期：2026-07-28
汇总提交：`main@a0605081`
用途：直接交给其他 AI 或演示文稿制作者，用于生成课堂 presentation、讲稿、图表和答辩材料。
证据原则：正文已经包含理解实验所需的核心方法、数字、失败记录和结论边界；不需要访问图片、JSON、日志或仓库中的其他文件。

---

## 0. 使用规则

### 可以直接使用的证据等级

- **现场真实 PASS**：在真实本机运行环境中执行，保留了原始日志或机器结果。
- **连续复现 PASS**：相同条件下至少连续两次通过。
- **机制 PASS**：证明实现满足确定性合同，但不等于人类主观体验更好。
- **人工门禁未完成**：机器可靠性已通过，但主观质量结论必须等待独立盲评。

### Presentation 中必须遵守的边界

可以说：

> 我们在单节点本地 kind 集群和 Windows 笔记本公网 Demo 环境中，对故障恢复、业务指标扩容、端到端追踪、30 人突发访问、记忆治理、主动式策略和中英双语可靠性进行了真实复验。

不能替换成：

> 我们已经证明系统具备多地域生产级高可用、任意规模并发能力，或者 Aurora 的语义质量已经显著优于 Gemini、ChatGPT 等系统。

本包中的数字证明的是明确限定环境下的运行事实。生产 SLA、跨地域容灾、成本最优性、人格保真度和人类偏好仍需各自独立实验。

---

## 1. 一页总览

| 实验 | 环境 | 结果 | 最重要数字 | 当前可支撑的主张 |
|---|---|---|---|---|
| H1 跨 Pod 连续生成 | local kind、2 个 API Pod、PostgreSQL、Redis、Gemini | PASS | API `2/2 → 1/2 → 2/2`；同一 turn 最终 `COMPLETED`；用户消息 1 条、Aurora 气泡 2 条仍可见 | 强删正在生成的 Pod 后，durable turn、历史消息和已提交回复可以恢复 |
| H2 KEDA 业务弹性 | local kind、KEDA、Prometheus、durable outbox | 连续复现 + 新鲜复跑 PASS | Worker desired `1 → 3 → 6`；三次观测分别为 `25.872s / 33.287s / 39.222s`；1200/1200 回执；重复 0 | 系统能按真实业务 backlog 而非仅 CPU 扩容，并保持收据唯一 |
| H3 OpenTelemetry | local kind、OTel Collector、Jaeger、真实 Gemini | 连续复现 + 新鲜复跑 PASS | 2 个服务、21 个应用 Span；Provider 占请求 `63.1%` 或 `71.4%`；禁用隐私标签 0 | 一条用户请求可追踪到 Provider、记忆检索、outbox 和 worker projection |
| 公网 30 人 × 50 Sandbox | Windows 笔记本、Cloudflare Quick Tunnel、Gemini | 两次正式连续 PASS，最终代码 smoke PASS | 30/30 Aurora；HTTP 429 为 0；50/50 唯一 Sandbox；好友环 30/30；正式两轮 Aurora p95 `17.897s / 12.848s` | 当前笔记本和 Provider 配额下可承载课堂级 30 人同时体验，并隔离 Demo Session |
| 记忆检索 | H2 内存；PostgreSQL 16 + Redis 7.4 | PASS | 180 次调用；p95 `204.23ms / 315.95ms`；超时、预算越界、泄漏、漏召回均为 0 | 记忆检索在测试负载下具有有界延迟和用户隔离 |
| 记忆纠正与撤回 | authority-aware 与 naive baseline 对照 | 2/2 PASS | 旧记忆不再返回；naive baseline 如预期暴露旧记忆和已撤回记忆 | 系统不是简单堆积历史文本，而是尊重纠正和遗忘权 |
| 主动式 Aurora 门控 | quiet-hours、long-gap、preference-change | 3/3 PASS | 安静时段 LLM/推送/调度均为 0；其余场景各只触发 1 次 | 主动式能力受时机、偏好和节制策略约束 |
| 共鸣体 Genome | 编译、检索、真实 Gemini 旅程 | 机制 PASS | 动态机制 5/5；groundedness 泄漏 0；运行时选择准确率 1.0；Gemini 6/6 | 共鸣体机制能选择授权证据并保持身份边界 |
| Gemini 中英双语 | 同一模型 Direct 与 Full Aurora，48 个冻结配对 | 可靠性 PASS；人工质量门禁待完成 | 两路均 48/48；Full Aurora fallback 0；中英语言匹配 48/48；Full p95 `9.847s` | Aurora 中英文链路可靠，语言遵从已通过；不能据此声称语义质量更好 |
| CPU HPA | local kind、真实 CPU 压力 | PASS | CPU `149%/70%`；API `2 → 4 → 2`；恢复后 `8%/70%` | 除 KEDA 外，通用 CPU 弹性也真实工作 |
| Argo Rollouts | local kind、4 Pods、真实 Prometheus gate | PASS | `25% → 50% → 100%`；坏 revision 约 60 秒自动 abort；稳定 4 Pods 持续服务 | 渐进式发布和错误版本隔离机制真实触发 |
| Kyverno Policy-as-Code | local kind admission | PASS | root、无资源限制、`:latest` 三类违规均拒绝；合规 Pod 运行 | 安全规则在调度前强制执行，而非只写在文档中 |

---

## 2. H1：强删 Pod 后，Aurora 仍然完成同一条消息

### 2.1 要回答的问题

当一个 API Pod 正在承载 Aurora 生成时，如果它被强制删除：

1. 用户已经发出的消息会不会丢失？
2. 当前生成会不会永久卡死？
3. 新 Pod 是否能继续同一个 durable turn？
4. 是否会出现重复提交或两份互相冲突的回复？

### 2.2 实验环境

- Kubernetes context：`kind-kubedeploy`
- Namespace：`inner-cosmos-w3`
- API：2 个副本
- 持久层：PostgreSQL 16
- 活跃会话、游标与 lease：Redis
- Provider：Gemini `gemini-3.6-flash`
- Provider fallback：关闭
- Memory embedding：关闭

### 2.3 故障注入

脚本首先等待下一条真实 Aurora turn，读取 generation lease，从而锁定**真正承载这一 turn 的 Pod**，再执行：

```text
kubectl -n inner-cosmos-w3 delete pod <exact-serving-pod> --grace-period=0 --force --wait=false
```

这不是随机删除一个无关 Pod。锁定信息为：

```text
turn=40
pod=inner-cosmos-api-5db84ddb9-dmlx9
fault=forced-pod-delete-zero-grace
```

### 2.4 实际状态时间线

```text
实验就绪：
H1 LIVE DEMO ARMED
baseline_turn_id=39
api=2/2

故障注入：
TARGET_LOCKED turn=40
FAULT_INJECTED=forced_pod_delete_zero_grace

恢复过程：
api=1/2  target=deleted  turn=GENERATING
api=1/2  target=deleted  turn=GENERATING
api=2/2  target=deleted  turn=GENERATING
api=2/2  target=deleted  turn=COMPLETED

最终门禁：
H1_LIVE_PASS turn=40 final_status=COMPLETED api=2/2
```

### 2.5 客户端数据结果

| 检查项 | 结果 |
|---|---:|
| 原用户消息 | 1 条，恢复后仍可见 |
| Aurora 已提交气泡 | 2 条，恢复后仍可见 |
| Durable turn 状态 | `COMPLETED` |
| API 副本 | 恢复到 `2/2` |
| 用户历史 | 未丢失 |

另一次更严格的历史测量直接向 JVM 注入 `SIGKILL`：

| 指标 | 结果 |
|---|---:|
| Durable turn 完成 | `16.677s` |
| 经 Service replay 完成 | `1.546s` |
| 用户消息 | 1 条 |
| 不同 Aurora 气泡 | 2 条 |

### 2.6 机制解释

```text
PostgreSQL：对话 transcript 与 durable turn 的事实源
Redis：活跃会话、流式游标、generation lease
Lease + fencing token：新 Pod 接管时防止旧 Pod 恢复后双写
客户端 replay：重新连接后从持久化游标继续展示
```

### 2.7 结论

本实验支持：

> 在单节点 local kind 的多 Pod 环境中，强删真正承载 Aurora 生成的 API Pod 后，同一 durable turn 最终完成；原用户消息和已提交 Aurora 气泡仍可见，API Deployment 恢复到两个 Ready 副本。

本实验不证明：

- 多地域或多可用区容灾；
- 整个 Kubernetes 节点或数据库同时消失时仍零损失；
- Gemini 回复语义质量更好。

---

## 3. H2：KEDA 按业务 backlog 扩容，而不是只盯 CPU

### 3.1 要回答的问题

当待处理的 durable outbox 业务事件突然增加时，系统能否：

1. 自动增加 worker；
2. 在 45 秒课堂演示预算内出现明确扩容；
3. 把事件全部处理；
4. 避免重复消费；
5. 实验后恢复干净基线？

### 3.2 指标与机制

应用只向 Prometheus 暴露隐私安全的队列指标：

```text
inner_cosmos_outbox_ready
inner_cosmos_outbox_oldest_ready_age_seconds
inner_cosmos_outbox_dead
```

指标不包含正文、用户 ID、event ID 或 aggregate ID。KEDA 根据待处理数量和最老事件年龄驱动 worker 扩容。

### 3.3 三次观测结果

| 复验 | 达到的 desired / available | 达到门禁用时 | 40 秒机器门禁 | 45 秒演示预算 |
|---|---:|---:|---:|---:|
| Frozen Run 1 | `6 / 3` | `25.872s` | PASS | PASS |
| Frozen Run 2 | `6 / 3` | `33.287s` | PASS | PASS |
| Supplemental fresh rerun | `6 / 3` | `39.222s` | PASS | PASS |

适合直接绘制柱状图的数据：

```csv
run,seconds,presenter_limit_seconds
Frozen Run 1,25.872,45
Frozen Run 2,33.287,45
Fresh rerun,39.222,45
```

Worker desired 状态：

```text
1 → 3 → 6
```

最终数据一致性：

| 指标 | 结果 |
|---|---:|
| 注入业务事件 | 1200 |
| 最终发布事件 | 1200/1200 |
| Inbox receipts | 1200/1200 |
| Duplicate receipts | 0 |
| 清理后 synthetic rows | 0 |
| 清理后 worker baseline | 1 |

一次更大规模的历史 rehearsal 使用 3000 个事件，ready backlog 轨迹为：

```text
3000 → 2875 → 975 → 725 → 450 → 150 → 0
```

同一轮 worker：

```text
1 → 3 → 6 → 3 → 1
```

3000 个事件全部 `PUBLISHED`，duplicate receipt 为 0，3000 条合成事件和收据随后全部清理。

### 3.4 结论

本实验支持：

> 在单节点 local kind 中，真实 durable business backlog 能驱动 KEDA 将 worker 从 1 个扩到 6 个 desired 副本，三次观测都在 45 秒内达到展示门禁；1200 个事件全部产生唯一回执，重复为 0，实验后恢复为一个 worker 和零合成数据。

本实验不证明：

- AWS/EKS 生产容量；
- 多节点调度能力；
- 无限扩容；
- 成本最优性；
- 所有业务操作都具备 exactly-once 语义。这里直接验证的是本实验 consumer inbox receipt 合同。

---

## 4. H3：一条用户消息的端到端 OpenTelemetry 轨迹

### 4.1 要回答的问题

当用户向 Aurora 发消息并触发后续处理时，我们能否回答：

- 时间主要花在模型还是平台？
- API 和异步 worker 是否属于同一因果轨迹？
- 记忆检索、Provider 调用、outbox、memory projection、profile projection 是否可见？
- Trace 是否把用户正文、prompt、用户 ID 或 SQL 暴露为标签？

### 4.2 Trace 合同

一条通过门禁的 Trace 必须包含：

```text
HTTP Aurora request
└── aurora.turn
    ├── inner.cosmos.memory.retrieve
    ├── inner.cosmos.ai.provider
    └── dialog finish / durable outbox
        └── inner.cosmos.outbox.consume
            ├── inner.cosmos.projection.memory
            └── inner.cosmos.projection.profile
```

涉及两个运行角色：

```text
inner-cosmos-api
inner-cosmos-worker
```

### 4.3 Frozen closure run

Trace ID：

```text
89d2d4740f9e7cefa75b279aef0305cc
```

| 指标 | 结果 |
|---|---:|
| 场景总时长 | `28.561s` |
| Client end-to-end | `13.890s` |
| Traced HTTP request | `13.8387s` |
| Memory retrieve | `1.0ms` |
| Gemini Provider | `8.733s` |
| Provider 占 request | `63.1%` |
| Platform overhead | `5.1047s` |
| Worker consume | `5.1076s` |
| Memory projection | `3.6013s` |
| Profile projection | `1.4847s` |
| 服务数量 | 2 |
| 应用 Span | 21 |
| Trace depth | 4 |
| Forbidden privacy tags | 0 |

请求时延绘图数据：

```csv
component,milliseconds
Gemini Provider,8733.0
Platform overhead,5104.7
```

### 4.4 Supplemental fresh rerun

Trace ID：

```text
34f2a2dcf9b0c39fcdebbc68c93c3324
```

| 指标 | 结果 |
|---|---:|
| 场景总时长 | `36.041s` |
| Client end-to-end | `19.605s` |
| Traced HTTP request | `19.5677s` |
| Memory retrieve | `6.5ms` |
| Gemini Provider | `13.9736s` |
| Provider 占 request | `71.4%` |
| Platform overhead | `5.5876s` |
| Worker consume | `8.8677s` |
| Memory projection | `4.5513s` |
| Profile projection | `4.3016s` |
| 服务数量 | 2 |
| 应用 Span | 21 |
| Forbidden privacy tags | 0 |
| 测试账号清理 | PASS |

### 4.5 隐私标签检查

机器扫描要求以下属性不得出现在导出的 Span 标签中：

```text
user.id
enduser.id
message.content
gen_ai.prompt
gen_ai.completion
db.statement
http.request.body
url.query
userId
message
prompt
content
```

两次最终结果均为：

```text
forbidden_tags=0
```

### 4.6 Jaeger “Incomplete” 的解释

演示脚本从外部客户端注入 W3C `traceparent`，但该外部客户端根 Span 本身没有导出。因此 Jaeger 可能显示 `Incomplete`。这不表示 21 个要求的应用 Span 丢失；脚本独立检查每一个 API、Aurora、Provider、memory、outbox 和 worker projection Span，缺少任意一项都会 FAIL。

一次截图中显示 31 个 Span，是因为通过门禁后，测试账号清理请求继续复用了同一个 trace context。实验正式门禁仍以清理前要求的 21 个应用 Span 为准。

### 4.7 结论

本实验支持：

> 一个真实 Gemini-backed Aurora 请求可以沿 W3C context 串联 API 与异步 worker，并把请求时间拆解为 Provider、平台、记忆检索和 projection 阶段；所检查的正文、prompt、用户身份和 SQL 隐私标签均未导出。

本实验不证明：

- 回复语义更好；
- Jaeger 数据已具备生产级持久化与访问控制；
- 未列入禁用属性合同的所有潜在隐私风险都不存在。

---

## 5. 公网 30 人并发与 50 个 Demo Sandbox 隔离

### 5.1 要回答的问题

课堂现场 30 位观众同时注册、校准和向 Aurora 发消息，同时 50 个浏览器 Session 进入预设故事时：

- 是否全部命中真实 Gemini？
- 是否因为人为限流出现 429？
- 不同观众是否会共享并破坏同一个 Demo 身份？
- 30 位用户是否能够相互发现并形成好友环？
- 临时测试数据能否全部清理？

### 5.2 运行环境

```text
服务器：Windows 笔记本
公网入口：Cloudflare Quick Tunnel
Provider：GEMINI
Model：gemini-3.6-flash
Runtime route：single-pass.v1
```

`single-pass.v1` 是简单首次对话的预期自适应路径。本实验验证容量、隔离和链路可靠性，不验证 planner/critic 的语义收益。

### 5.3 两次正式连续 clean passes

| 指标 | run-07 | run-08 |
|---|---:|---:|
| 状态 | PASS | PASS |
| 真实 Gemini Aurora | 30/30 | 30/30 |
| HTTP 429 | 0 | 0 |
| Critic fallback | 0 | 0 |
| 注册 p50 | `868.66ms` | `836.72ms` |
| 注册 p95 | `1080.48ms` | `942.31ms` |
| 校准 p50 | `442.93ms` | `386.35ms` |
| 校准 p95 | `721.60ms` | `639.92ms` |
| Aurora p50 | `7.302s` | `7.392s` |
| Aurora p95 | `17.897s` | `12.848s` |
| Aurora p99 | `21.070s` | `14.568s` |
| 完整轨迹 p95 | `19.541s` | `14.497s` |
| Sandbox 进入 | 50/50 | 50/50 |
| 唯一 Sandbox owner | 50/50 | 50/50 |
| Sandbox 清理失败 | 0 | 0 |
| Social discovery | 30/30 | 30/30 |
| 接受好友环 | 30/30 | 30/30 |
| Nobody left alone | true | true |
| 临时普通账号删除 | 30/30 | 30/30 |
| Stage failures / failures | 0 / 0 | 0 / 0 |

适合直接绘图的数据：

```csv
run,aurora_p50_seconds,aurora_p95_seconds,full_journey_p95_seconds,http_429
run-07,7.302,17.897,19.541,0
run-08,7.392,12.848,14.497,0
```

### 5.4 最终代码 smoke

在重试修复后的最终代码、无其他实验重叠的条件下执行：

| 指标 | run-09 |
|---|---:|
| 状态 | `FINAL_CODE_SMOKE PASS` |
| Gemini Aurora | 30/30 |
| HTTP 429 | 0 |
| Aurora p50 / p95 / p99 | `4.073s / 7.186s / 9.207s` |
| 完整轨迹 p95 | `8.779s` |
| Sandbox / 唯一 owner | `50/50 / 50/50` |
| Sandbox 清理失败 | 0 |
| Discovery / 好友环 | `30/30 / 30/30` |
| 临时账号删除 | 30/30 |
| Critic fallback | 0 |
| Stage / business failure | `0 / 0` |

run-09 证明最终 retry 代码没有引入容量、隔离或社交回归；正式的连续容量证据仍然是 run-07 和 run-08。

### 5.5 失败台账与修复价值

失败没有从分母中删除：

| 轮次 | 结果 | 暴露的问题 |
|---|---|---|
| run-01 / run-02 | 环境失败 | 受限执行环境无法使用 Windows TLS 凭据，业务请求未开始 |
| run-03 | 有效 FAIL | Tunnel 命中旧 WSL 8080 运行时；Provider 为旧 DeepSeek；50 个 Session 只得到 3 个共享身份 |
| run-04 / run-05 | 有效 FAIL | `DEMO_UNLIMITED_USAGE_ENABLED` 是未被限流器读取的死配置；同一公网 IP 撞 login bucket，Sandbox 仅成功 10/50、5/50 |
| run-06 | PASS，但非正式容量复现 | 与另一项双语实验有短暂并发，标记为 `CONCURRENT_EXTRA_LOAD_PASS` |
| run-07 / run-08 | 正式 PASS | 修复后连续、干净、同条件复现 |
| run-09 | 最终代码 smoke PASS | 证明后续 retry 修复没有造成容量和隔离回归 |

关键修复：

1. 启动脚本现在确认新容器**实际发布指定端口**，不能把旧运行时的健康状态当成新版本成功。
2. 非 `prod` 且明确开启课堂无限使用时，只旁路应用额度过滤器。
3. 认证、授权、CSRF、隐私、幂等和危机安全链仍保留。
4. `prod` 即使误配 unlimited 开关也仍强制限流。

### 5.6 结论

本实验支持：

> 在这台 Windows 笔记本、当时网络和 Gemini 配额下，两次连续 clean run 均完成 30/30 个真实注册、校准和 Aurora 回合；50/50 Demo Session 获得不同 owner，30/30 用户完成发现与好友环，429、fallback、stage failure 和清理失败均为 0。

本实验不证明：

- 商业生产容量或 SLA；
- 任意 50 人、任意消息长度、任意 Provider 配额下都成功；
- 数据库、Redis、线程池和总成本已经完成生产级峰值规划；
- Aurora 的语义质量优于基线。

---

## 6. 记忆系统：检索性能、隔离、纠正和遗忘

### 6.1 并发检索实验

实验负载：

- 10 个虚拟用户；
- 每个用户 200 条噪声记忆以及正确性数据；
- 并发 24；
- 共 180 次调用。

| 拓扑 | 调用 | 吞吐 | p50 | p95 | p99 | 最大 | 超时 | 预算越界 | 禁用记忆泄漏 | 相关记忆漏召回 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| H2 内存 | 180 | `209.18 calls/s` | `97.27ms` | `204.23ms` | `215.25ms` | `218.52ms` | 0 | 0 | 0 | 0 |
| PostgreSQL 16 + Redis 7.4 | 180 | `202.10 calls/s` | `80.61ms` | `315.95ms` | `318.02ms` | `318.30ms` | 0 | 0 | 0 | 0 |

顺序正确性基准另有 10 个案例：

```text
micro Recall@3 = 1.0
macro Recall@3 = 1.0
MRR = 1.0
禁用记忆泄漏 = 0
预算越界 = 0
p95 = 24.4444ms
```

Redis 与应用上下文同时运行，但当前检索服务本身走数据库读路径，因此不能把延迟改善归因于 Redis。

### 6.2 Authority-aware memory 对照实验

场景 1：用户纠正旧信息。

```text
authority-aware：返回纠正后的 ACTIVE 记忆，不返回 SUPERSEDED 旧记忆
naive lexical baseline：暴露旧记忆
```

场景 2：用户主动撤回数据。

```text
authority-aware：不返回 FORGOTTEN 记忆
naive lexical baseline：暴露已撤回记忆
```

结果：

```text
correction / withdrawal = 2/2 PASS
unexpectedFailureLedger = []
```

### 6.3 结论

本实验支持：

> 记忆系统不仅检索相似文本，还执行 owner 隔离、状态 authority、用户纠正和遗忘合同；在本次 180 调用负载中没有超时、预算越界、泄漏或漏召回。

本实验不证明：

- 真实用户多年数据下仍保持相同指标；
- 语义 embedding 的收益。本轮 Demo 明确关闭 embedding；
- Redis 缓存让检索更快；
- 2 个 authority 案例可以代表大规模长期记忆研究。

---

## 7. 主动式 Aurora：节制比“多发消息”更重要

### 7.1 三个真实引擎场景

| 场景 | 预期行为 | 实际结果 |
|---|---|---|
| Quiet hours | 不应在安静时段打扰 | LLM 调用 0、推送 0、调度 0 |
| Long-gap return | 久未出现时只温和问候一次 | 只产生 1 次 check-in，无连续推送 |
| Preference changed | 用户明确要求更多陪伴后才增加回访 | 产生 1 次计划回访 |

结果：

```text
3/3 PASS
unexpectedFailureLedger = []
```

### 7.2 结论

本实验支持：

> Aurora 的主动式机制不是无条件推送，而是在 LLM 调用前执行 quiet-hours、间隔和用户偏好门控。

本实验不证明：

- 用户主观认为提醒时机恰到好处；
- 主动式一定提升留存、幸福感或任务完成率；
- 3 个确定性场景等价于长期真实用户试验。

---

## 8. 共鸣体 Genome：动态人格不是静态长 Prompt

### 8.1 已完成的机器机制实验

| 子实验 | 结果 |
|---|---:|
| 动态 Genome 机制断言 | 5/5 |
| 编译 groundedness 案例 | 4 个 |
| Ungrounded citation | 0 |
| 被排除记忆泄漏 | 0 |
| 运行时检索案例 | 6 个 |
| 意图准确率 | 1.0 |
| 选择准确率 | 1.0 |
| Evidence leak | 0 |
| Unsupported fallback accuracy | 1.0 |
| 相关聚焦测试 | 49/49 |

对照观察：

```text
动态 Genome：
- 排除未授权外部记忆
- 保留结构化身份披露 invariant
- 根据当前意图选择相关人格证据

静态拼接 baseline：
- 会包含外部文本
- 没有结构化身份披露 invariant
```

### 8.2 真实 Gemini 在线旅程

三个预设共鸣体，每个回答两个相同问题：

| 指标 | 结果 |
|---|---:|
| 总旅程 | 6 |
| Provider 成功 | 6/6 |
| Fallback | 0 |
| 旅程错误 | 0 |
| 最小延迟 | `1.393s` |
| 中位延迟 | `2.008s` |
| 平均延迟 | `1.938s` |
| 最大延迟 | `2.477s` |

运行配置：

```text
Provider = Gemini
Model = gemini-3.6-flash
Provider fallback = disabled
Embedding = disabled
```

### 8.3 结论

本实验支持：

> 共鸣体编译和运行时检索能够排除未授权素材、保持身份披露边界，并根据意图选择结构化人格证据；三个预设共鸣体的六次真实 Gemini 旅程全部完成。

本实验不证明：

- 人类能够显著识别三个共鸣体；
- 共鸣体与真实 owner 的人格保真度已经通过；
- 任意新用户、任意数据量都能编译出同样质量；
- 回复优于静态 Prompt。该主张仍需要盲评。

---

## 9. Gemini 中英双语与 Direct-vs-Full Aurora

### 9.1 实验设计

- 冻结 48 个 paired items；
- 中文 `24`，英文 `24`；
- 两路均固定 `gemini-3.6-flash`；
- Direct：直接调用同一 Provider；
- Full Aurora：经过 Aurora 当前完整回复路径；
- 保留 failure-inclusive ledger；
- 人类盲评前不打开 unblinding key。

冻结身份：

```text
Evidence ID = INNO-EVAL-GEMINI-BILINGUAL-006
Dataset SHA-256 = 96f12ab65ef2dae218a563e0fb32f202fe12fc3b06c4c6f9617a45433f44f3ad
Ledger SHA-256 = 27bcee03222011fedc3b76ff45afe82f5669674868b0b01a3b4bc7158117131a
```

### 9.2 最终机器可靠性结果

| 指标 | Direct Gemini | Full Aurora |
|---|---:|---:|
| 成功 | 48/48 | 48/48 |
| Fallback | 0 | 0 |
| Business failure | 不适用 | 0 |
| 可见语言匹配 | 48/48 | 48/48 |
| 中文完成 | 24/24 | 24/24 |
| 英文完成 | 24/24 | 24/24 |
| p50 | `8.349s` | `3.695s` |
| p95 | `13.880s` | `9.847s` |
| max | `16.965s` | `15.148s` |

Full Aurora 自动检查：

```text
advice-boundary warnings = 0
meta-AI phrase warnings = 0
```

时延绘图数据：

```csv
path,p50_seconds,p95_seconds,max_seconds
Direct Gemini,8.349,13.880,16.965
Full Aurora,3.695,9.847,15.148
```

这些时延是本轮测量结果，不应解释成 Full Aurora 在所有请求中必然比 Direct 更快。

### 9.3 失败驱动的修复链

| Evidence run | 结果 | 暴露的问题 |
|---|---|---|
| `-002` | FAIL | Full Aurora 英文语言匹配 0/24；巨大中文系统 envelope 压过最新用户语言 |
| `-003` | INVALID | 与另一负载实验并发，受污染后主动排除 |
| `-004` | 45/48 | 去重逻辑擦除了合法的近似重复回复，之后错误显示 Provider recovery 模板 |
| `-005` | 原 3 例恢复，但出现 4 个结构化 fallback | 重试只观察异常，不观察返回值中的 fallback 状态 |
| `-006` | 48/48 PASS | 修复语言合同、合法回复保留和 value-level retry 后，fallback 与业务失败均为 0 |

没有删除任何失败轮次，也没有从原始分母中移除失败样本。

### 9.4 人工盲评门禁

当前状态：

```text
reliability = PASS
language adherence = PASS
effectiveness_claim = false
status = HUMAN_REVIEW_PENDING
```

必须由 3 名独立中英双语 reviewer 完成盲评，再解盲。预注册偏好主张要求：

```text
Full Aurora 至少赢得 60% 的非平局 pairs
并且 Wilson 95% 置信区间下界 > 0.50
并且没有关键维度退化
```

在此之前不能说：

- “Aurora 已被证明优于 Direct Gemini”；
- “中文和英文的主观质量完全等价”；
- “Aurora 优于 ChatGPT、DeepSeek 或其他产品”。

当前可以说：

> 在同一 Gemini 3.6 Flash 模型和 48 个冻结中英配对上，Direct 与 Full Aurora 均 48/48 完成；Full Aurora 没有 fallback 或业务失败，中英文语言匹配均为 24/24。主观偏好仍等待三人盲评。

---

## 10. 补充云原生证据

这些结果适合放在 presentation 的“工程深度”或答辩备用页，不建议挤占 H1/H2/H3 主舞台。

### 10.1 CPU HPA：真实负载扩缩容

方法：

```text
fortio
concurrency = 100
duration = 240s
HPA contract = 2..4 replicas @ CPU 70%
```

结果：

```text
负载期 CPU：149% / 70%
API replicas：2 → 4
停止负载后：4 → 3 → 2
恢复后 CPU：8% / 70%
```

支持主张：

> HPA 并非只安装了对象；在真实 CPU 压力下完成了 `2 → 4 → 2` 的完整周期。

边界：local kind，不是 AWS 生产容量。

### 10.2 Argo Rollouts：好版本渐进发布，坏版本自动中止

健康 revision：

```text
25% canary
→ Prometheus analysis gate
→ 50%
→ pause 15s
→ 100%
→ Healthy
```

坏 revision：

```text
readiness 指向不存在路径
→ canary 0/1 Ready
→ 稳定 revision 4/4 持续服务
→ 约 60s progress deadline
→ auto abort
→ bad ReplicaSet 1 → 0
```

真实 Prometheus AnalysisRun：

```text
3 次测量全部 Successful
values = [5], [5], [4]
```

支持主张：

> 发布控制器通过真实 Prometheus gate 推进健康版本；错误 readiness revision 自动 abort，稳定 4 Pods 持续服务。

边界：本轮 canary 使用每 Pod H2，没有证明两个版本与共享 PostgreSQL 的 expand-contract 兼容。

### 10.3 Kyverno：安全策略在 Admission 阶段执行

| 输入 | 结果 |
|---|---|
| 合规 Pod | Admission 通过并运行 |
| `runAsUser: 0` | 拒绝 |
| 缺少 requests/limits | 拒绝 |
| 使用 `:latest` | 拒绝 |
| Deployment 模板中 root | 由 autogen rule 拒绝 |

Kill-switch 复验：

```text
删除 disallow-latest policy
→ :latest Pod 被允许
→ 删除测试 Pod并恢复 policy
→ 同一违规再次被拒绝
```

Webhook：

```text
failurePolicy = Fail
```

支持主张：

> 三类关键运行时规则不是文档建议，而是在 Pod 调度前由 fail-closed admission webhook 强制执行。

### 10.4 依赖故障下正确区分 readiness 与 liveness

故障：

```text
PostgreSQL StatefulSet scale 1 → 0
持续约 2 分钟
```

结果：

```text
readiness = HTTP 503
liveness = UP
API Pods 从 Service 摘除
故障期间新增重启 = 0
PostgreSQL 恢复后约 15s，两个 API Pods 回到 Ready
```

支持主张：

> 数据库短时故障会停止接收新流量，但不会触发无意义的 JVM 重启风暴。

### 10.5 NetworkPolicy 真实 deny/allow

```text
带 inner-cosmos allow label 的 probe：
PostgreSQL 5432 = open
Redis 6379 = open

无 allow label 的 probe：
PostgreSQL 5432 = timeout
Redis 6379 = timeout
```

这个实验还暴露并修复了 backup Job 缺少 allow label 的真实集成问题。修复后：

```text
backup Job = Complete 1/1
duration = 5s
pg_dump size = 247462 bytes
```

支持主张：

> NetworkPolicy 在当前 CNI 上真实执行 silent drop，并通过正负对照验证；安全加固造成的备份回归也被实验发现和修复。

---

## 11. 测试与验证总账

本轮直接相关的机器验证：

| 验证 | 结果 |
|---|---:|
| P1 机制测试 | 56 项，失败 0、错误 0、跳过 0 |
| 最终聚焦 Java 测试 | 33 项，失败 0、错误 0、跳过 0 |
| 双语 harness fixture test | 2/2 PASS |
| 前端生产构建 | PASS |
| Java 21 Docker package | PASS |
| H2 fresh rerun | PASS |
| H3 fresh rerun | PASS |
| Public Demo runtime self-test | PASS |
| 最终代码 30/50 smoke | PASS |

重要限制：

- 没有声称完整 Maven 全套回归通过；
- 完整 `AuroraEmergenceTest` 中仍有一个旧断言依赖过时的 fallback 文案，新增修复对应的聚焦测试通过；
- 这不会被隐藏或改写成“所有测试全部通过”。

---

## 12. 最值得制作的 Presentation 图表

### 图 1：H1 故障恢复时间线

```text
2/2 Ready
   ↓ 删除精确 serving Pod
1/2 Ready + turn GENERATING
   ↓ 新 Pod 启动、lease 接管、replay
2/2 Ready + turn COMPLETED
   ↓
原消息 1 条 + Aurora 气泡 2 条仍在
```

推荐标题：

> Pod dies. The conversation does not.

### 图 2：H2 三次扩容用时

```csv
run,scale_out_seconds,45s_budget
Run 1,25.872,45
Run 2,33.287,45
Fresh rerun,39.222,45
```

推荐标题：

> Business backlog drives capacity in under 45 seconds

辅助数字：

```text
worker 1 → 3 → 6
1200 receipts
0 duplicates
0 synthetic rows after cleanup
```

### 图 3：H3 请求时延拆解

Frozen run：

```csv
component,milliseconds,share_of_request
Gemini Provider,8733.0,63.1%
Platform overhead,5104.7,36.9%
```

Fresh rerun：

```csv
component,milliseconds,share_of_request
Gemini Provider,13973.6,71.4%
Platform overhead,5587.6,28.6%
```

推荐标题：

> One user turn, explainable across API, Gemini and asynchronous projections

### 图 4：30 人正式两轮延迟

```csv
run,Aurora_p50_s,Aurora_p95_s,journey_p95_s,success,429
run-07,7.302,17.897,19.541,30/30,0
run-08,7.392,12.848,14.497,30/30,0
```

推荐标题：

> Two clean 30-user public rehearsals, zero throttling

旁注：

```text
50/50 isolated Demo Sessions
30/30 discovery
30/30 friend ring
```

### 图 5：Authority-aware memory 与 naive baseline

| 场景 | Naive retrieval | Authority-aware retrieval |
|---|---|---|
| 用户纠正旧事实 | 返回旧事实 | 只返回纠正后的 ACTIVE 事实 |
| 用户撤回记忆 | 返回已撤回内容 | 不返回 FORGOTTEN 内容 |

推荐标题：

> Memory that can be corrected and forgotten

### 图 6：中英双语可靠性

```csv
path,success,language_match,fallback,p50_s,p95_s
Direct Gemini,48/48,48/48,0,8.349,13.880
Full Aurora,48/48,48/48,0,3.695,9.847
```

推荐标题：

> Reliability and language adherence pass; preference remains human-gated

图中必须加脚注：

> Machine reliability result only. Human semantic preference review is pending.

---

## 13. 建议的课堂论证结构

### 主舞台：三层可靠性

1. **Experience reliability — H1**
   Pod 被删除时，用户消息和会话仍继续。
2. **Resource reliability — H2**
   业务 backlog 增加时，worker 自动扩容且无重复收据。
3. **Semantic/operational explainability — H3**
   一条消息的模型耗时、平台耗时和异步投影都可以追踪，且隐私标签受控。

可以用一句总论串联：

> We move from keeping resources alive, to preserving semantic continuity, and finally to making the whole path explainable.

### 第二层：证明课堂 Demo 不是单人脚本

- 两次 30 人正式公网复现；
- 50 个 Demo Session 身份隔离；
- 0 个 429；
- 30 人全部完成发现与好友环；
- 最终代码 smoke 无 fallback 或业务失败。

### 第三层：证明 AI 产品不是普通聊天壳

- 记忆可纠正、可撤回；
- 主动式在安静时段主动不说话；
- 共鸣体只使用授权、grounded 的人格证据；
- 中英文完整链路均 24/24，通过可靠性门禁；
- 人类偏好尚未完成，主动承认证据边界。

---

## 14. 主张矩阵：可以说什么，不能说什么

| 推荐说法 | 禁止偷换 |
|---|---|
| “强删正在生成的 Pod 后，同一 durable turn 完成，历史未丢失。” | “实现了跨地域零故障。” |
| “三次 local kind 观测均在 45 秒内达到 KEDA scale-out 门禁。” | “生产环境会无限、瞬时扩容。” |
| “1200 个收据全部存在，duplicate receipt 为 0。” | “系统所有操作都严格 exactly-once。” |
| “一条 Trace 串联 API、Gemini、memory、outbox 和 worker projection。” | “可观测性证明了回复质量更高。” |
| “两次 clean run 均完成 30/30 真实 Gemini Aurora。” | “系统已具备任意规模生产容量。” |
| “50/50 Demo Session 获得唯一 owner。” | “整个系统已通过完整多租户安全审计。” |
| “记忆纠正与撤回的 2 个机制案例通过。” | “长期记忆已经通过大规模用户研究。” |
| “三个主动式门控场景 3/3 通过。” | “用户一定喜欢主动提醒。” |
| “Full Aurora 中英文均 24/24 完成，fallback 为 0。” | “Aurora 已证明优于 Gemini/ChatGPT/DeepSeek。” |
| “共鸣体 6/6 真实 Gemini 旅程完成。” | “人格 fidelity 和 distinctiveness 已被人类证明。” |
| “坏 canary 约 60 秒自动 abort，稳定 4 Pods 持续服务。” | “共享生产数据库的双版本 schema 已完全证明。” |

---

## 15. 尚未完成、不能伪装成成果的实验

以下内容有实现或评估框架，但当前没有足够结果支撑强主张：

1. **Aurora 是否比 Direct Gemini 更受人喜欢**
   需要三位独立双语 reviewer 完成 48 对盲评。
2. **中英文主观质量是否等价**
   机器语言匹配通过，但需计算盲评后的 Aurora benefit 差异。
3. **共鸣体人格 fidelity / distinctiveness**
   需要人物来源识别、owner likeness、跨 10 轮稳定性和隐私泄漏盲评。
4. **ASR/TTS 中英双语质量**
   功能启用不等于 CER、WER、MOS、首包时延等实验已完成。
5. **生产级 50 人以上容量或 SLA**
   当前是课堂笔记本与 Quick Tunnel 证据。
6. **多地域、多 AZ 容灾**
   H1 是单节点 kind 中的跨 Pod 接管。
7. **生产级 Trace 留存与治理**
   当前 Jaeger 使用本地演示存储。
8. **共享 PostgreSQL 的双版本 expand-contract**
   Argo Rollouts 已证明发布和回滚机制，尚未证明共享数据库 schema 兼容。

---

## 16. 复现入口

这些命令仅作为方法透明度材料；理解本包结果不依赖实际运行它们。

### 三个 Hero 场景

```powershell
Set-Location -LiteralPath 'D:\code\inner cosmos'

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-three-hero-showcase.ps1' `
  -Scene Preflight

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-three-hero-showcase.ps1' `
  -Scene All `
  -HoldViews
```

### 单独 H1

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-h1-live-demo.ps1' `
  -FaultMode HardCrash
```

### 30 人 × 50 Sandbox

```powershell
.\.tools\pwsh-7.6.3\pwsh.exe -NoProfile `
  -File '.\scripts\demo\test-30-user-burst.ps1' `
  -Origin 'https://当天预检通过的公网地址' `
  -UserCount 30 `
  -ThrottleLimit 30 `
  -SandboxEntryUsers 50 `
  -SandboxEntryThrottleLimit 50
```

### P1 机制实验

```powershell
$tests = 'com.innercosmos.evaluation.MemoryRetrievalLoadTest,com.innercosmos.evaluation.TrackAMemoryAuthorityAblationEvaluationTest,com.innercosmos.ai.proactive.TrackAProactiveDecisionEvaluationTest,com.innercosmos.evaluation.TrackACapsuleGenomeAblationEvaluationTest'

.\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dtest=$tests" test
```

---

## 17. 给下一个 AI 的直接任务提示

可以把以下文字与本文件一起交给制作 presentation 的 AI：

> 请只使用本实验包正文中的数据，不假设能够访问任何外部文件、图片或仓库。制作一套以 H1 跨 Pod 连续性、H2 KEDA 业务弹性、H3 OpenTelemetry 可解释轨迹为主线的课堂 presentation。所有图表必须使用包内 CSV 或表格数字重建，不虚构截图。把 30 人并发、记忆 authority、主动式门控、共鸣体 Genome 和中英双语可靠性作为支撑证据。每项主张都保留环境和边界；不得宣称多地域生产高可用、任意规模容量、Aurora 语义优于 Gemini/ChatGPT，或共鸣体人格已通过人类验证。把失败台账作为工程成熟度证据，而不是删除失败。

---

## 18. 最终结论

现有证据最有力量的并不是“我们安装了很多云原生工具”，而是三项与产品体验直接连接的因果链：

1. **Pod 故障不会自动变成用户历史丢失。**
2. **业务积压会自动变成可观测、可验证的 worker 扩容。**
3. **一条真实 Aurora 消息可以被拆解、追踪和进行隐私标签检查。**

再结合两次 30 人公网复现、记忆纠正/遗忘、主动式节制、共鸣体授权证据选择和中英双语 48/48 可靠性结果，可以诚实地支持：

> Inner Cosmos 不只是一个聊天页面，而是一套把 AI 体验连续性、业务弹性、可观测性、记忆治理和多用户隔离连接在一起的可运行系统。

当前最重要的人类门禁仍是 Aurora 质量与共鸣体人格盲评。在这些评分完成之前，可靠性已经有数据，主观优越性仍保持为待验证假设。
