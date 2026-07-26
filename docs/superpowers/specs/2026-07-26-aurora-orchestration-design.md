# Aurora 编排重设计 — 分级治理 + 流式交付

日期:2026-07-26
状态:设计已确认,待实现计划
关联:`对齐文档/24-完全体最终收敛与云原生课程战役.md`、`docs/goal/closure-campaign-state.yml`

---

## 0. 实现状态(2026-07-26 21:58 后核实)

本设计成稿期间,`963a0b1 fix: harden Aurora streaming and response quality` 已独立闭合了其中
最严重的两项。以下状态经实际运行测试核实,非推断:

| 项 | 状态 | 证据 |
|---|---|---|
| §1.1 线程池死锁 | **已闭合** | 三池分离(`aiExecutor` / `taskExecutor` / `sseExecutor`)+ `queue-capacity=0` 使池可真正扩容;`stream()` 两个入口(`:656`、`:668`)已改用 `streamExecutor`;`ThreadPoolIsolationTest` 1/1 绿 |
| §1.2 质量门摧毁正常回复 | **已闭合** | `AuroraRuntimeQualityRegressionTest` 3/3 绿,断言 `repaired()==false` 且 `criticIssues()` 非空 —— 即"打点但不改写";第 4 条气泡截断而非拼接 |
| §8.5 无界 Map | **已闭合** | `AuroraDualKernelRuntime:327-334` 已有 TTL + 容量上限驱逐 |
| §1.3 `Thread.sleep` 延迟 | 未动 | `sleep(30)` 仍在 `:1787`,`sleep(220)` 仍在 `:762` |
| §3 `reasoning_effort` | 未做 | 全代码库无下发 |
| §3 模型默认 | 未改 | `application.yml:181` 仍为 `deepseek-v4-flash` |
| §8.3 `plannerFallbackUsed` | 未修 | pipeline 分支(`:268`)仍硬编码 `false` |

**结论:阻塞级问题已清除,剩余工作全部是"把体验做快"和"让深核真的在工作"。**
第 1 节保留原始诊断作为设计依据的记录;已闭合项标注见上表,不必重做。

---

## 1. 为什么要重设计

三个已实测确认的问题,不是推测:

### 1.1 四个并发对话即锁死线程池(阻塞级)— 已于 963a0b1 闭合

`aiExecutor` 为 core=4 / max=20 / queue=100(`ThreadPoolConfig.java:15`、`application.yml:63-67`)。
`ThreadPoolExecutor` 在队列未满前不会扩容超过 core,因此实际只有 4 个线程。

`stream()` 在同一个池上嵌套并阻塞等待:

| 层 | 位置 | 阻塞方式 |
|---|---|---|
| SSE 整轮 | `AuroraAgentServiceImpl:660` `aiExecutor.execute` | — |
| `produceReply` | `:712` `supplyAsync(..., aiExecutor)` | `:736` `deepReply.join()`,**无超时** |
| 前台 ack | `:1238` `supplyAsync(..., aiExecutor)` | `:1241` `get(2400ms)`,有界 |
| 后台 planner | `AuroraDualKernelRuntime:242` | `:419` `get(8s)`,有界 |

用相同池参数与相同嵌套结构实测:

```
concurrentTurns=4  finishedWithin10s=false  completed=0
poolActive=4  queued=4  poolSize=4
```

4 个线程全部阻塞在自己排在队列中的子任务上,队列仅 4 项(远未到 100),池不扩容 → 永久死锁,直到 `SseEmitter` 120s 超时。
现有 1269 个测试无一能发现此问题;`closure-campaign-state.yml` 中"30-user burst"仍在 `remaining_machine_work`,该路径从未被并发压过。

### 1.2 确定性质量门在摧毁正常回复 — 已于 963a0b1 闭合

`AuroraDualKernelRuntime.qualityIssues()`(`:506-680`)为约 40 条硬编码中文子串/正则。命中后
`deterministicQualityRepair()` 将**整条回复替换为约 10 句固定文案之一**。

以 4 条无害、贴切的回复实测,3 条被摧毁:

| speaker 输出 | 判定 | 实际交付 |
|---|---|---|
| 「我在这里看到两个不同的目标在打架…」 | `generic_companion_cliche` | 「这句话里还有一处没有展开…」 |
| 「那种松下来的感觉来得很自然,不用刻意维持。」 | `generic_companion_cliche` | 同上(同一句) |
| 「你这几天的节奏挺理性的,先别打乱它。」 | `unearned_comparative_praise` | 同上(同一句) |

误伤源于纯字面匹配:`contains("我在这里")`(`:531`)、`contains("很自然")`(`:530`)、
`matches(".*(挺|很|非常)(清醒|成熟|理性|…).*")`(`:597`)。同一句兜底文案会反复出现。

该规则集系从若干条评测 transcript 反推(`"不是冲你来的"`、`"这个分寸感挺好"`),属评测集过拟合。
且几乎只对中文生效:英文回复仅剩「空/超 3 条/超 300 字/记忆越权」四条通用检查。

### 1.3 最大的延迟来源是自写的 `Thread.sleep`,不是模型

`streamText`(`:1777-1786`)每 2 字符 `sleep(30)`;气泡间 `sleep(220)`(`:754`)。代码推导,精确:

| 回复规模 | 纯 sleep |
|---|---|
| 60 字 / 1 条 | 0.9s |
| 180 字 / 3 条 | 3.14s |

且完全叠加于模型时间之上 —— 回复在滴出前已全量生成并落库。
66 字/秒本身不算慢(接近真实 LLM 中文流速);问题是**滴在生成之后,而非生成之中**。

### 1.4 "双内核"在生产路径上已名不副实

`plannerExecutor` 为 `@Autowired`(required),生产恒走 `generatePipelined()`(`:114`):
speaker 使用**上一轮**的 plan;每会话**首轮**用纯确定性 `fallbackPlan()`;critic 移至轮后,
不再修补已交付内容,仅向下一轮 `responseConstraints` 注入提醒(`:286`)。
可见输出的唯一把关者是 1.2 节那套正则。

作为架构证据的 `DualKernelInformationFlowEvaluationTest` 直接 `new AuroraDualKernelRuntime(...)`
(不注入 executor),断言模块序为 `PLAN → SPEAKER → CRITIC` 且 speaker 上下文含 `responsePlan` —— 生产两者均不执行。

---

## 2. 设计原则

1. **跨轮流水线中,理解核只能是顾问,不能是控制器。** 同轮控制权必须另建,不可指望深核。
2. **治理分级与速度分级共用同一个路由器。** 安全的轮次要快,要紧的轮次要稳。
3. **延迟通过重叠消除,不通过砍功能。** sleep 与生成重叠即免费。
4. **深核开 high effort 是零延迟成本的** —— 它完全在关键路径之外。这是跨轮流水线的真实优势。
5. **判官与被判者不用同一模型家族。** 同模型对自身输出有系统性盲区。
6. **顺序写进 schema。** 字段顺序由我们控制,可用它把"需要早知道的信息"提前。

---

## 3. 模型与推理配置

| 内核 | 模型 | thinking | effort | 位置 |
|---|---|---|---|---|
| FOREGROUND(ack + state) | DeepSeek V4 Pro | disabled | — | 关键路径 |
| SPEAKER | DeepSeek V4 Pro | disabled | — | 关键路径 |
| JUDGE(仅 L2) | GLM 或 MiniMax(**异构**) | disabled | — | 关键路径 |
| DEEP KERNEL | DeepSeek V4 Pro | enabled | high | 关键路径外 |

配置改动:`DEEPSEEK_MODEL=deepseek-v4-pro`(现默认 `deepseek-v4-flash`,`application.yml:181`)。

`reasoning_effort` 目前**完全未下发**。需在 `LlmRequest` 增字段,并在
`DeepSeekLlmClient.requestBody()`(`:237-251`,已正确下发 `{"thinking":{"type":...}}`)补
`reasoning_effort`。仅在 `thinkingEnabled=true` 时下发。

---

## 4. 每轮编排

### 4.1 L0 / L1(预估 >85% 轮次)

```
用户消息
  │
  ├─① FOREGROUND(流式, 非思考, ~384 tok)
  │     schema 顺序: state 字段在前, ack 在后
  │     t≈0.4s  state 闭合 → 解析 → 立即发 SPEAKER
  │     t=0.4→1.2s  ack 字符边到达边转发(真流式, 零额外 sleep)
  │
  ├─② SPEAKER(流式, 非思考)
  │     ← state(本轮) + guidance(上轮深核)
  │     schema 顺序: referencedMemoryIds / speakCount 在前, segments 居中, 元数据在后
  │     segments[0] 闭合 → 过硬拦 → 开始滴, 同时 segments[1] 仍在生成
  │
  ├─③ 硬拦层(纯本地, 语言无关, 逐气泡可判)
  │     空回复 / 单条 >300 字 / 超 3 条(截断) / 记忆 ID 越权(剥离越权 ID)
  │
  ├─④ 风格遥测(纯本地) —— 仅打点,不改写
  │
  └─⑤ DEEP KERNEL(thinking=high, 关键路径外)
        ← 本轮用户消息 + speaker 输出 + state + 上轮 guidance + 长期上下文
        → 下一轮 guidance,写入 backgroundPlans

provider 调用 / 轮 = 3
```

### 4.2 L2(高风险,预估 <15% 轮次)

speaker 全量生成后缓冲,不逐气泡出门:

```
② SPEAKER 全量
  → JUDGE(异构模型, 非思考, 小 schema, ~1.5s)
       输入:state + speaker 全量输出 + 本轮用户消息
       输出:{ pass, violations[], revisionInstruction }
     ├─ pass  → ③ → 一次性交付
     └─ 违规 → SPEAKER 重生 1 次(带 revisionInstruction)
                 ├─ pass  → 交付
                 └─ 再违规 → 删除违规字段(nextQuestion / smallStep)后交付
                            保留原句,绝不替换为模板
```

L2 选择了缓冲交付,因此 judge 的时间是**完全叠加**的,不与任何气泡播放重叠
(speaker 约在 2.6s 完成,ack 早已播完)。L2 总时长:

- pass:≈2.6s + 1.5s ≈ **4.1s**
- 违规并重生一次:再加约 1.4s ≈ **5.5s**

这是刻意的取舍:高风险轮次慢 1.5–3 秒换取"可重写全部气泡"的能力。

### 4.3 L3(危机)

行为不变:`blockModelCall` 时完全不调模型,仅发一个 safety 事件。

---

## 5. 分级路由

`DualKernelBudgetPolicy` 已存在(176 行、纯函数、可单测、复用 `CrisisKeywordRule` +
`DistressSignalDetector`、输出可读 `reasons`),但:

1. 只有两档 `SINGLE_PASS` / `DUAL_KERNEL`;
2. **生产未启用** —— `application.yml:189` 为 `runtime: dual`,而 `shouldUseDualKernelForTurn()`
   仅在 `adaptive` 分支调用 `budgetPolicy.decide()`。

改动:枚举扩为 `L0 / L1 / L2 / L3`;语义从"选哪条 runtime"改为"治理与速度强度";生产启用。

| 档 | 触发 | 治理 | 交付 |
|---|---|---|---|
| L0 | score < 2 | 硬拦 + 遥测 | 激进流式 |
| L1 | score 2–3 | 同 L0,深核 effort=high 优先调度 | 激进流式 |
| L2 | score ≥ 3,或 `readinessForAdvice == 0`,或 `resistance == "active"`,或 `hardConstraint` 非空 | + 异构 judge,允许重生 1 次 | 缓冲后一次性交付 |
| L3 | `CrisisKeywordRule` 命中 | 不调模型 | 单个 safety 事件 |

---

## 6. 状态词汇

FOREGROUND 输出(state 在前,ack 在后):

```json
{
  "helpSeekingMode": "vent | understand | decide | act",
  "readinessForAdvice": 0,
  "resistance": "none | mild | active",
  "unmetNeed": "用户此刻最需要被怎样对待,一句话",
  "hardConstraint": "本轮绝不能做的事,一句话或空字符串",
  "ack": "一句立即可见的短回应"
}
```

取代原先从正则反推的 `{isCorrection, rejectsAdvice, quietDisclosure, singleActionOnly}`:

- **可泛化** —— 无需为每种新的边界表达增加规则;
- **语言无关** —— 直接解决 1.2 节的"英文几乎无把关";
- `readinessForAdvice=0` 天然覆盖「只想说出来」与「先别给建议」,并覆盖它们覆盖不到的情形。

---

## 7. 冷启动

开场问候(`AuroraAgentServiceImpl:1001` 的 `greetingGrounding` 路径)生成时,并行异步跑一次深核:
输入为长期画像 + 关系状态 + 上次会话摘要 + 引力记忆,输出"本次会话开场策略"写入 `backgroundPlans`。
用户敲完第一条消息时(通常 10–30s 后)guidance 已在位,零感知延迟。

`fallbackPlan()` 从"伪装成 plan"改为**显式缺省**:预热未完成时不传 `dialogueGuidance`,而是告知
speaker「本轮无跨轮策略,贴着用户说」。诚实优于伪造。

---

## 8. 前置修复

### 8.1 SSE 外层移出 `aiExecutor` — **已闭合,无需实现**

`963a0b1` 采用的方案比本设计原提的虚拟线程更保守,且更好:

- `aiExecutor`(8/32/**queue=0**)、`taskExecutor`(4/20/queue=100,仅遗留 `@Async`)、
  `sseExecutor`(8/64/**queue=0**)三池分离;
- `queue-capacity=0` 是关键:`SynchronousQueue` 让池在需要时立刻扩容到 max,
  而非"队列满之前不扩容"—— 直接消除了原始死锁的根因,而不只是把线程数调大;
- `CallerRunsPolicy` 保留:超出 max 时在调用者线程上跑,退化为串行而非死锁;
- `stream()` 的两个入口(`:656` safety 分支、`:668` 主分支)均已改用 `streamExecutor`。

不再需要虚拟线程改造。原提案作废。

### 8.2 深核预算必须先量,不能拍

`StructuredAiService:208-221` 现为 `maxTokens=2048 / timeout=30s`,而其注释已记录 v4-flash 曾把
1K token 全花在 `reasoning_content`、`finish_reason=length`、JSON 未产出。开 `effort=high` 后此失败
几乎必然复现,后果是静默走 `fallbackPlan()`,仅留一条 `[BAD_AI_OUTPUT]` 日志。

先跑 3 次真实调用量出 pro 在 high 下的 reasoning token 分布,再定 `maxTokens`(预估 8–16K)与
`timeoutMs`(预估 60–90s)。

### 8.3 `plannerFallbackUsed` 现为假信号

pipeline 路径在 `AuroraDualKernelRuntime:251` 硬编码传 `false`。必须改为真值并出指标,
否则"深核在工作"无法证明,只能相信。

### 8.4 删除死文案与风格类正则 — **已闭合**

`963a0b1` 已实现"打点但不改写",并已去掉气泡拼接。`AuroraRuntimeQualityRegressionTest` 3/3 绿,
断言正是本设计要的行为:命中风格启发式时 `repaired()==false`、`criticIssues()` 非空、原句原样交付;
第 4 条气泡被丢弃而非并入第 3 条。

保留在本节的仅为设计意图记录:

删除 `deterministicQualityRepair()` 的 10 句固定文案,及 `qualityIssues()` 中的风格类规则。
保留 4 条硬拦(空回复、单条超长、超 3 条、记忆 ID 越权)。

同时删除 `enforceContextualBubbleCadence()` 与 `foldSurplusBubbles()`:

- 前者用 `input.length() <= 24 → 1 条` 这类输入长度启发式覆盖模型判断(`:465`);
- 后者把多余段落用空格**合并进最后一条**,产生塞着问号的连读句 —— 现有测试自己断言的期望值就是
  `"那家小店今天没有踩雷。 你点了什么？ 看来可以先记住它。"`。这与"微信式多气泡"的产品设计相反。

气泡数改由 prompt 引导模型自行决定(`PromptBuilder` 与 speaker instruction 中已有相应措辞);
硬拦层对超 3 条的处理是**丢弃第 4 条及以后,不合并**。

### 8.5 无界 Map — **已闭合**

`AuroraDualKernelRuntime:327-334` 已实现基于 `updatedAtEpochMs` 的 TTL 驱逐 + 容量上限驱逐
(`BACKGROUND_PLAN_TTL_MS` / `MAX_BACKGROUND_PLANS`)。无需再做。

### 8.6 drip 参数外置

`sleep(30)/2字`、`sleep(220)/气泡间` 改为可配置。真流式之后其作用从"制造打字感"变为
"真实流速不足时补足节奏感",需在真机上调,不应继续硬编码。

---

## 9. 组件拆分

`AuroraDualKernelRuntime` 现为 860 行,承担 6 项职责。拆为:

| 单元 | 职责 | 依赖 |
|---|---|---|
| `AuroraForegroundKernel` | 一次流式调用产出 state + ack | `StructuredAiService` |
| `TurnStateSignals`(record) | typed state,内核间唯一接口 | 无 |
| `AuroraSpeakerKernel` | 只把 state + guidance 表达成话 | `StructuredAiService` |
| `IncrementalJsonExtractor` | 从 SSE 流增量提取已闭合的字符串字段 | 无 |
| `AuroraDeepKernel` | 跨轮 guidance + 会话预热 + plan 存储与驱逐 | `StructuredAiService` |
| `AuroraTurnJudge` | L2 专用异构裁判 | 具名 `LlmClient` |
| `OutputIntegrityGate` | 仅 4 条硬拦,逐气泡可判 | 无 |
| `StyleTelemetry` | 风格违规打点 | `MeterRegistry` |
| `DualKernelBudgetPolicy`(改) | L0–L3 分级,生产启用 | 现有安全词表 |
| `AuroraTurnOrchestrator` | 只编排,不含业务规则 | 上述全部 |

每个单元可脱离 Spring 单测;`AuroraDualKernelRuntime` 退化为 orchestrator(预估 <200 行)。

---

## 10. 时延预算

模型时间为按配置超时的估计;sleep 时间为代码推导的精确值。

| 阶段 | 今天 | 修订后 |
|---|---|---|
| 首字(ack) | ≈1.0s | **≈0.4s** |
| 真实回复首字 | ≈2.5s | **≈1.2s** |
| 3 条 / 180 字播完(L0/L1) | ≈5.6s | **≈3.0s** |
| L2 全量交付(pass) | ≈5.6s | ≈4.1s |
| L2 全量交付(重生一次) | ≈5.6s | ≈5.5s |
| provider 调用 / 轮 | 3 | 3(L2 为 4) |

**分档时机约束:** L2 的判定依赖 `state`,而 `state` 在 t≈0.4s 到达;`segments[0]` 最早在
t≈1.2s 闭合。因此"流式还是缓冲"的决定必须在这 0.8s 窗口内做完,且**必须在第一条气泡出门之前**。
`DualKernelBudgetPolicy` 的 score 部分不依赖模型,可在 t=0 先算;state 部分在 0.4s 补齐后合并出档位。

5.6s → 3.0s,不减任何内核。全部来自"让 sleep 与生成重叠"。

---

## 11. 验证策略

| 类型 | 内容 | 性质 |
|---|---|---|
| 回归 | 16 并发 SSE 轮次必须全部完成 | 硬门 |
| 契约 | FOREGROUND / SPEAKER / JUDGE 三个 schema 的流式解析与降级 | 硬门 |
| 增量解析 | `IncrementalJsonExtractor` 对截断流、转义引号、嵌套的行为 | 硬门 |
| 路由 | L0–L3 分档(扩展现有 `DualKernelBudgetPolicyTest`) | 硬门 |
| **反向** | 一批正常、贴切的中英文回复必须**不触发**硬拦 —— 量化 1.2 节的 3/4 误伤 | 硬门 |
| 架构证据 | 改写 `DualKernelInformationFlowEvaluationTest` 以断言**真实生产路径** | 硬门 |
| 真 provider | 深核在 high effort 下的**成功率**(非 fallback 率)、reasoning token 分布 | 人工门 |
| 效果 | 离线扫 `tb_dialog_message`:按 `helpSeekingMode` 分组算下一轮的 Δ长度 / Δ新信息 / Δ情绪强度 | 证据,非门 |

最后一项是非自评的效果指标,补上"现有评测门只证明信息流经过了,不证明质量更好"这一空洞,
且零运行时开销。它复用现有 `EmotionSpectrumDeriver` / `EmotionBaselineService`。

---

## 12. 已知风险与取舍

1. **气泡出门后不可收回。** L0/L1 逐气泡过闸意味着第二条违规时只能删除第二条,不能重写第一条。
   接受此风险:4 条硬拦均逐条可判,风格问题按设计仅打点;需要"看到全貌再决定"的轮次由 L2 缓冲承担。
2. **guidance 在话题切换时是噪音。** speaker instruction 已规定「当前用户的新表达永远优先」,
   但模型无法可靠判断"该忽略"。这是跨轮流水线的固有代价。
3. **深核成功率未知。** 8.2 未完成前,整套设计的"理解"部分可能实际由确定性兜底承担。
   这是实现顺序上的第一优先。
4. **异构 judge 引入第二个 provider 依赖。** 需确认 GLM/MiniMax 密钥在 demo 环境可用;
   不可用时 L2 降级为 L1 行为(仅打点),而非阻塞。

---

## 13. 不做的事

- 不实例化 8 核黑板架构:每轮 provider 调用会从 3 升至 8–10,并放大 1.1 节的饥饿。
- 不在运行时做 Future Trajectory Simulator:其价值以第 11 节的离线效果指标兑现。
- 不做多 agent 投票:同模型误差高度相关,且票数无法定位失败维度。
- 不改动已达标的部分:单一外显人格、内核间 typed schema(`StructuredAiResults` 严格 JSON)、
  `ClaimAuthority` 的记忆权威分级 —— 后者已严于同类系统的 uncertainty ledger 设计。
