# Aurora dual-kernel：课堂可证明的事实与边界

这份说明用于回答一个很容易被说大的问题：Aurora 的 dual-kernel / multi-core 到底比
single-pass 多做了什么？

结论先说：

> 当前 dual-kernel 不会额外读取一种 single-pass 看不到的传感器，也不会自行执行
> TODO、慢信或外部工具。它对同一份已组装上下文增加了一个显式规划核，把规划结果交给
> 表达核，并在高风险或确定性规则发现问题时增加有界 critic。因此可以证明“信息经过了
> 更完整、可检查的决策链”，不能仅凭架构名称声称“回复质量一定更高”。

## 1. 同一输入下的真实调用差异

| 维度 | single-pass | adaptive 选择 dual-kernel |
|---|---|---|
| 原始信息来源 | 相同的 turnContext | 相同的 turnContext |
| Provider 主调用 | `AURORA_CHAT_*` | `AURORA_PLAN_*` → `AURORA_SPEAKER_*` |
| 显式计划 | 无独立计划对象 | `userIntent`、`emotionalNeed`、`relationshipMove`、约束、气泡目的、允许记忆 ID、不确定性 |
| critic | 无独立 critic | `needsCritic` 或确定性 gate 命中时，读取 plan + candidate + observableIssues |
| 动作 | 回复中的 `smallStep` / `featureTarget` 建议 | 同样是建议，但受到显式 plan/critic 链约束 |
| 工具执行 receipt | 无 | 无 |

“主动感知”发生在两种 runtime 之前：`AgentContextAssembler`、记忆检索、画像、关系、
当前状态、时间天气和用户校准共同组成 turnContext。dual-kernel 的优势是把这些输入压成
可检查的关系动作与约束，再交给表达核；它没有多查询一次数据库，也没有新感知源。

`adaptive` 也不是第三种生成器。它只是每轮预算路由：简单轮次进入 single-pass，风险、
歧义、打断、相关记忆或深对话达到阈值时进入 dual-kernel。

## 2. 离线、可重复验收

```bash
./mvnw test -Dtest=DualKernelInformationFlowEvaluationTest
```

报告：

```text
target/track-a-eval/dual-kernel-information-flow-report.json
```

测试给 forced-single 与 adaptive-dual 完全相同的输入，并故意固定相同的最终文字、
`smallStep` 与 `featureTarget`。PASS 只证明：

1. 两条路径最初拿到的原始字段一致；
2. single 只调用一次 `AURORA_CHAT_*`；
3. adaptive 对该歧义 + 打断 + 连续性输入给出可解释的 dual 预算理由；
4. dual 真实调用 plan、speaker、critic；
5. speaker 收到 plan，critic 收到同一 plan、candidate、userInput 与 observableIssues；
6. planner/speaker/critic 没有 fallback；
7. 生产用 Micrometer meter 和 Observation 能按 runtime 区分 single 与 dual，且不写入
   用户正文、user ID 或 memory ID。

这不是盲评，也不调用真实 Provider，所以不能从 PASS 推导“dual 的语言更自然”。
真实模型质量仍需相同 Provider 的重复 pairwise 与独立盲评。

## 3. API、Prometheus 与 OTel 能看到什么

### API

POST `/api/v1/aurora/message-rich` 的证据分成两块：

- `aiState`：`provider`、`model`、`mode`、`apiKeyConfigured`、`fallbackAllowed`；
- `agentLoop`：`runtime`、`relationshipMove`、`criticRepaired`、`criticIssues`、
  `stageLatenciesMs`、planner/speaker/critic fallback 标记。

所以真实模型和多核是两个正交命题：

```text
aiState.provider != mock && aiState.fallbackAllowed == false
agentLoop.runtime == dual-kernel.v1
agentLoop.plannerFallbackUsed == false
agentLoop.speakerFallbackUsed == false
```

只展示 `aiState.provider=deepseek` 不能证明 plan/speaker/critic 跑过；只展示
`agentLoop.runtime=dual-kernel.v1` 也不能证明没有 Provider fallback。

当前 SSE `meta` 会带 `runtime`、`relationshipMove`、critic 与 fallback 标记，但没有
序列化 `stageLatenciesMs`。需要逐阶段延迟证据时使用 POST 响应；当前服务端指标只有
端到端耗时，不要说 SSE 或 Prometheus 已展示全部阶段耗时。

### Prometheus

生产 meter 已按 `runtime` 分开：

```promql
sum by (runtime) (increase(aurora_turn_count_total[10m]))
```

```promql
sum by (runtime) (rate(aurora_turn_latency_seconds_sum[5m]))
/
sum by (runtime) (rate(aurora_turn_latency_seconds_count[5m]))
```

这能回答两种 runtime 各跑了多少轮、平均端到端延迟多少；不能回答 planner 比 speaker
慢多少，因为当前没有逐阶段 Prometheus timer。

### OpenTelemetry / Jaeger

当前生产 Trace 中：

- `aurora.turn` completion Observation 带 `runtime`、provider、mode、fallback、
  memory_referenced 与粗粒度 duration bucket；
- `inner.cosmos.ai.provider` 包住实际 Provider/runtime 调用，但只带 provider 和 mode。

因此在 HTTP tracing bridge 正常时，Jaeger 能在同一请求 Trace 中看到 runtime 标记与
Provider 调用；当前没有独立的 `planner`、`speaker`、`critic` 子 span，也没有把 turn ID
写成 span attribute，不能把一个 Provider span 口头解释成三段 Trace。离线验收记录的
module 调用链是结构证据，不是生产逐阶段 OTel 证据。

## 4. Presentation 建议措辞

可以说：

> 对相同上下文，简单轮次可以只花一次模型调用；需要处理歧义、打断、连续记忆或风险时，
> adaptive 会进入 dual-kernel，把理解与关系动作形成结构化计划，再交给表达核；必要时
> critic 检查计划、候选和确定性问题。

不要说：

- “多核天然拥有更多用户信息”；
- “三个 Agent 在独立思考后投票”；
- “critic 保证回答正确”；
- “featureTarget 已经执行了产品动作”；
- “OTel 已逐段显示 planner/speaker/critic”；
- “离线脚本证明 dual 的情商或语言质量更高”。

这套边界反而是更强的课程证据：我们能够区分输入感知、预算路由、结构化决策、可见回复、
动作建议、真实副作用和可观测性，而不是把它们混成一个“AI 更聪明”的口号。
