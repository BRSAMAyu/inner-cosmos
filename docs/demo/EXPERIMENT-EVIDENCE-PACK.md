# Inner Cosmos 实验、证据与现场兜底包

版本：2026-07-28  
代码基线：以本证据包所在提交为准（历史冻结结果仍保留各自 source commit）
当前课堂实验环境：`kind-kubedeploy / inner-cosmos-w3`  
当前实验 Provider：`gemini / gemini-3.6-flash`，Mock fallback 关闭，memory embedding 关闭

## 一页结论

| 项目 | 当前证据等级 | 可以说什么 | 不能说什么 |
|---|---|---|---|
| H1 跨 Pod 续写 | **现场 PASS，另有多轮历史复验** | 强删正在生成的 API Pod 后，同一 turn 最终 `COMPLETED`，原消息和回复气泡仍在，API 回到 `2/2` | 不能外推成多地域、多可用区容灾 |
| H2 KEDA 弹性 | **历史连续两次 + 本轮新鲜 PASS** | 业务 outbox backlog 驱动 worker `1 → 3 → 6`；本轮 `39.222s` 达到 `6/3`，1200 条任务无重复消费并清理归零 | 不能外推成 AWS 生产容量或成本最优 |
| H3 可观测性 | **历史连续两次 + 本轮新鲜 PASS** | 新 trace 串起 Gemini、Aurora turn、memory、outbox、worker projections；请求 `19.568s`，Provider `13.974s`，隐私禁用标签 `0` | trace 不能证明回答语义更好；Jaeger 当前为内存后端 |
| 公网产品 Demo | **最新 Gemini 构建自检 PASS，已冻结离线画面** | 三个隔离故事、Aurora、记忆、共鸣体、慢信/连接的实际页面存在并可走核心路径 | Quick Tunnel 是临时入口，不是长期域名 SLA |
| Aurora 回复更好 | **48 对正式材料已生成，三人盲评待完成** | 同模型 Direct/Full 两路均 48/48 完成，失败与 fallback 为 0；可进入人评 | 机器可靠性不能替代人类偏好，当前不能说“显著优于基线” |
| 中英双语更好 | **可靠性与语言门禁 PASS，人评待完成** | 中文 24/24、英文 24/24 Full Aurora 均完成并匹配目标语言 | 目前不能说中英主观质量等价或优于其他产品 |
| 30 人并发 | **连续两次新鲜 PASS** | 本机公网 Demo 两轮均完成 30/30 Gemini Aurora、50/50 隔离 Sandbox、30/30 discovery/好友环，HTTP 429 为 0 | 只能声称本机、当时网络与 Provider 配额下的课堂突发结果，不是任意生产负载承诺 |

证据等级统一使用：

- **现场 PASS**：本机真实运行、原始日志和截图均保留。
- **历史 PASS**：真实运行过，但不是本次课堂前最后一次新鲜运行。
- **先导/结构证据**：能验证机制或帮助发现问题，不能替代最终人类质量结论。
- **待验证**：只有方案、脚本或功能，没有满足主张的结果。

## H1：跨 Pod 会话与生成接管

### 要支撑的主张

> 一个正在承载 Aurora 生成的 API Pod 被强制删除后，客户端可能短暂断联，但同一 durable turn 会被其他 Pod 接管；原用户消息、已提交 Aurora 气泡和会话历史不丢失，也不会被重复提交。

### 2026-07-28 新鲜结果

- 基线：API `2/2`，最后 turn `39`。
- 脚本锁定正在生成的 turn `40` 和精确 lease owner Pod。
- 实际故障：`kubectl delete pod ... --grace-period=0 --force --wait=false`。
- 状态轨迹：`2/2 → 1/2 → 2/2`，turn `GENERATING → COMPLETED`。
- 最终门禁：`H1_LIVE_PASS turn=40 final_status=COMPLETED api=2/2`。
- 恢复后的客户端：一条原用户消息和两条 Aurora 气泡仍同时可见。

历史上更严格的直接 JVM `SIGKILL` 复验还测得：

- durable turn 在 `16.677s` 内完成；
- 经幸存 Service 的 replay 在 `1.546s` 内完成；
- 最终仍为 1 条用户消息、2 条 distinct Aurora bubbles。

### 最关键的证据

- [H1 原始终端日志](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/h1-live-final-run-2026-07-28.txt)
- [恢复后的用户端](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/h1-client-restored-history-2026-07-28.png)
- [恢复仪表盘](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/h1-recovery-dashboard-2026-07-28.png)
- [历史 SIGKILL 量化结果](../../evidence/w3/CN-ZERO-LOSS-DRAIN-003/summary.md)

### 现场只讲 35–45 秒

1. 先指出用户消息已经发出，终端已经锁定“真正承载该 turn 的 Pod”。
2. 强删 Pod，指一下 API `2/2 → 1/2` 和客户端“恢复中”。
3. 最后只看三件事：turn=`COMPLETED`、API=`2/2`、原消息和回复都在。
4. 一句话机制：Redis 保存游标与会话，PostgreSQL 是 transcript truth，lease + fencing 防止双写。

## H2：按业务压力进行 KEDA 弹性伸缩

### 要支撑的主张

> 我们不是看到 CPU 高才盲目扩容；系统把 durable outbox backlog 暴露为隐私安全的业务指标，KEDA 根据待处理业务量增加 worker，并保持 exactly-once inbox 收据。

### 2026-07-28 历史连续两次 + 本轮新鲜结果

| 复验 | 达到 worker desired/available | 用时 | 40 秒机器门禁 | 45 秒讲解预算 |
|---|---:|---:|---:|---:|
| Run 1 | `6/3` | `25.872s` | PASS | PASS |
| Run 2 | `6/3` | `33.287s` | PASS | PASS |
| Fresh rerun | `6/3` | `39.222s` | PASS | PASS |

第二次完整收尾：

- worker 目标轨迹：`1 → 3 → 6`；
- `1200/1200` events 最终发布；
- `1200/1200` inbox receipts；
- `duplicate_receipts=0`；
- `synthetic_rows=0`；
- worker 恢复 baseline `1/1`。

### 最关键的证据

- [KEDA 六小时实测曲线](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/h2-keda-dashboard-6h-2026-07-28.png)
- [最终三场景结果 JSON](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/final-results-2026-07-28.json)
- [本轮 H2/H3 新鲜复验](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/fresh-rerun-2026-07-28.md)
- [KEDA 机制与早期完整扩缩容证据](../../evidence/w3/CN-EVENT-DRIVEN-AUTOSCALING-001/summary.md)

### 现场只讲 35–45 秒

1. 先指出黄色 worker 线是 1，绿色 backlog 突然升高。
2. 只等待 scale-out，看到目标到 6 就结束现场等待。
3. 同时说：绿色 backlog 随 worker 增加而下降；缩容有稳定窗口，稍后 H3/Grafana 仍能看到完整曲线。
4. 终端只需要露出：`H2_PRESENTER_READY`、`duplicate_receipts=0`、cleanup PASS。

## H3：端到端 OpenTelemetry 可解释轨迹

### 要支撑的主张

> 我们可以从一个用户的 Aurora 请求追到真实 Gemini Provider，再追到 dialog finish、outbox consumer、memory projection 和 profile projection，并量化时间花在了哪里，同时不把正文、prompt、用户 ID 或 SQL 放进 trace 标签。

### 2026-07-28 历史连续两次 + 本轮新鲜结果

- 最终 trace：`89d2d4740f9e7cefa75b279aef0305cc`。
- PASS 时要求并验证 `21` 个应用 spans，跨 `inner-cosmos-api`、`inner-cosmos-worker` 两个服务。
- 清理请求继续沿用同一外部 trace context 后，Jaeger 页面累计显示 `31` spans。
- client end-to-end：`13.890s`。
- traced HTTP request：`13.8387s`。
- Provider：`8.733s`，占请求 `63.1%`。
- memory retrieve：`1.0ms`。
- platform overhead：`5.1047s`。
- worker consume：`5.1076s`。
- memory projection：`3.6013s`。
- profile projection：`1.4847s`。
- `forbidden_tags=0`。
- 场景脚本总时长：`28.561s`，低于 60 秒门禁。

本轮再次复验的 trace 为 `34f2a2dcf9b0c39fcdebbc68c93c3324`：应用
spans `21`、跨 API/worker 两个服务，client end-to-end `19.605s`，
traced request `19.5677s`，Provider `13.9736s`（`71.4%`），
worker consume `8.8677s`，隐私禁用标签 `0`，场景总时长 `36.041s`。

Jaeger 截图上的 `Incomplete` 不是“应用 span 丢了”。演示脚本从客户端注入一个 W3C
`traceparent`，但这个外部客户端根 span 本身不导出，所以 Jaeger 提示缺少外部父节点。脚本会独立检查上述所有应用 span，任何一个缺失都会 FAIL。现场应主动解释这一点，不要等导师质疑。

### 最关键的证据

- [Jaeger 实际 trace](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/h3-jaeger-trace-89d2d47-2026-07-28.png)
- [最终三场景结果 JSON](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/final-results-2026-07-28.json)
- [本轮 H2/H3 新鲜复验](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/fresh-rerun-2026-07-28.md)
- [OTel 合同与隐私扫描说明](../../evidence/w3/CN-OTEL-SEMANTIC-TRACE-001/summary.md)

### 现场只讲 35–45 秒

1. 先指 2 Services、Depth 4 和 Aurora HTTP 根操作。
2. 展开 `aurora.turn`，指出 memory 和真实 Gemini provider。
3. 指橙色 worker consumer 与两个 projection。
4. 用一句数字收尾：“13.89 秒里，8.733 秒在模型，系统开销 5.105 秒；禁用隐私标签为 0。”

## 三个实验的一分钟离线兜底

如果现场 Kubernetes、投屏、Wi-Fi 或 Provider 临时失败，不要临时修改系统。打开：

[HERO-EXPERIMENT-OFFLINE-BACKUP.html](HERO-EXPERIMENT-OFFLINE-BACKUP.html)

或在仓库根目录执行：

```powershell
Start-Process (Resolve-Path '.\docs\demo\HERO-EXPERIMENT-OFFLINE-BACKUP.html')
```

然后按右方向键：

1. H1 用户端 + Grafana：说“原消息、两条回复仍在；API 回到 2/2；5xx 为 0”。
2. H2 Grafana：说“两次 scale-out 是 25.872 秒和 33.287 秒；1200 条任务、0 重复”。
3. H3 Jaeger：说“2 services、depth 4、13.890 秒；Provider 占 63.1%；隐私禁用标签 0”。
4. 最后一页主动声明证据边界。

透明话术：

> 当前现场环境出现了临时网络/集群故障。下面不是模拟动画，而是同一代码基线在
> 2026-07-28 两次连续复验中冻结的原始日志、Grafana 和 Jaeger 结果；可复现脚本与机器可读结果也一并保留。

## 产品 Demo 的四级兜底

### A. 正常入口

- 当前 Quick Tunnel：`https://participating-beverly-susan-saint.trycloudflare.com/app/aurora/`
- 当前已验证第三方短链：**无**。CleanURI 返回的新短链现场复核为 HTTP 404，
  is.gd/TinyURL 拒绝 `trycloudflare.com`，所以不把失效短链交给观众。
- 演示当天必须先运行 `.\scripts\demo\status-public-demo.ps1`，再用手机蜂窝网络验证
  Quick Tunnel；现场优先投二维码，避免听众输入随机长域名。

Quick Tunnel 在重启后会变化，不能把它当永久域名。要得到真正稳定、简短的入口，
仍需 Cloudflare Named Tunnel + 自有短域名；现场二维码应在最终 Preflight 后生成。

### B. 隧道坏、应用仍在

- 主讲电脑直接打开 `http://127.0.0.1:8082/app/aurora/`。
- 听众停止自由访问，改为投屏演示一个预置故事和一个新用户核心路径。
- 明确说明“公网入口故障，产品本机服务仍健康”，不要把本机访问伪装成公网成功。

### C. Provider 慢或失败

- 不现场切 Mock；Mock fallback 已关闭。
- 先展示预置故事中已经沉淀的记忆、共鸣体、慢信和连接。
- 再打开 H3 冻结 trace，说明最近一次真实 Gemini 调用的时延分解。
- 如果要演示输入，只发一条短而具体的已彩排消息，不连续重试制造更大排队。

### D. 整套运行环境不可用

使用离线 HTML 和这些真实产品页面：

- [三个 lived-in story](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/public-demo-lived-in-stories-2026-07-28.png)
- [Aurora 对话页](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/public-demo-aurora-story-2026-07-28.png)
- [记忆与内宇宙](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/public-demo-memory-cosmos-2026-07-28.png)
- [共鸣体](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/public-demo-resonance-2026-07-28.png)
- [慢信与连接](../../evidence/w3/CN-THREE-HERO-SHOWCASE-001/public-demo-connections-2026-07-28.png)

## 当前已经有证据的其他实验

这些可以用于答辩加分，但不建议挤占 H1/H2/H3 的主舞台：

| 实验 | 已有数字 | 证据边界 |
|---|---|---|
| CPU HPA | fortio `c=100`，CPU `149%/70%`，API `2 → 4 → 2`，恢复后 `8%/70%` | local kind，不是生产容量 |
| Argo Rollouts | 25% → 50% → 100% 健康发布；坏 readiness revision 在约 60s 自动 abort，稳定 4 Pods 持续服务 | 本地 H2，不证明共享 PostgreSQL expand-contract |
| Kyverno Policy-as-Code | root、无资源限制、`:latest` 三类违规 Pod 均被 Admission 拒绝；合规 Pod 运行 | local kind admission 证据 |
| 依赖故障的探针语义 | PostgreSQL 停约 2 分钟，readiness 503、liveness UP、无新增重启；恢复约 15s 后 Ready | 单节点 kind |
| 记忆检索并发 | 10 虚拟用户、180 calls、24 threads；in-memory `209.18 calls/s / p95 204.23ms`，PostgreSQL 16 + Redis 7.4 `202.10 calls/s / p95 315.95ms`；timeout、budget violation、leak、miss 均为 0 | 本机测试负载，不是生产容量 |
| 记忆 authority 消融 | correction/withdrawal 2/2 通过；naive baseline 按预期暴露陈旧/已撤回记忆 | 小规模机制实验，不是用户长期效果研究 |
| 主动式决策门禁 | quiet-hours、long-gap、preference-change 3/3 通过 | 证明策略合同，不证明用户主观帮助率 |
| 共鸣体 genome 机制 | 动态机制 5/5；4 个 groundedness 场景泄漏 0；6 个运行时检索场景选择准确率 1.0 | 真实 Gemini 人物回复 6/6 成功，但尚无人类 fidelity 盲评 |
| 确定性双内核合同 | 16 个合成场景中 dual `16/16`，single `4/16` | 只证明合同检查，不证明人类更喜欢 |

对应证据：

- [HPA](../../evidence/g8/HPA-LOAD-001/summary.md)
- [Argo Rollouts](../../evidence/w3/CN-PROGRESSIVE-DELIVERY-001/run-log.txt)
- [Kyverno](../../evidence/w3/CN-POLICY-AS-CODE-001/run-log.txt)
- [探针与 NetworkPolicy](../../evidence/w3/CN-CREDIBILITY-001/summary.md)
- [内存检索并发](../../evidence/innovation/INNO-INNER-010/retrieval-load-2026-07-23.md)
- [本轮记忆、主动式与共鸣体机制复验](../../evidence/experiments/P1-MECHANISM-RERUN-2026-07-28/summary.md)
- [双内核合同](../../evidence/track-a/A0-quality-laboratory/runtime-ablation-report.json)

## 质量实验：机器门禁已完成，人工盲评待完成

### P0-1：Aurora 回复质量——同模型、盲化、配对 A/B

目的：回答“Inner Cosmos 的编排是否比直接调用同一个 Gemini 更好”，而不是拿不同模型混在一起比较。

设计：

- 固定 `gemini-3.6-flash`、temperature、语言、用户状态和安全配置。
- A：同 Provider 的单次直接回复基线。
- B：完整 Aurora，但分三组消融：
  1. full vs direct；
  2. memory on vs off；
  3. dual-kernel on vs off。
- 24 个语义场景 × 中文/英文两种自然撰写版本 = 48 个 paired items。
- 场景覆盖：倾诉、矛盾、被纠正、长间隔回归、时间感知、拒绝被带偏、行动请求、打断、危机安全降级、隐私撤回。
- 每个 pair 顺序随机、模型身份隐藏；至少 3 名独立评分者。
- 评分维度：被理解感、具体性、自然度、边界/立场、记忆正确性、行动帮助；另做 overall A/B/tie。

主终点：

- 48 个 pair 的多数票 win rate；
- 95% Wilson 或 bootstrap 区间；
- 预注册通过门槛：至少 `32/48` 个 pair 判 B 胜，且 95% 区间下界高于 `0.50`；
- safety、unauthorized recall、stale-after-cancel 不得回归；完整失败样本保留。

不要把 LLM-as-a-judge 当主裁判。它可以做二级诊断，但多语言开放式回答仍需要双语人类盲评。配对盲评和多指标报告分别与 [Chatbot Arena](https://arxiv.org/abs/2403.04132) 和 [HELM](https://arxiv.org/abs/2211.09110) 的方法论方向一致。

当前进度：冻结的 48 对数据、failure-inclusive ledger、三份独立盲评表和
unblinding key 已生成。最终修复后 `-006` 中 Direct Gemini 与 Full Aurora
均为 `48/48` 成功，Full Aurora fallback/business failure 为 `0`；三位评审
尚未填写，因此 `effectiveness_claim=false`。见
[最终双语可靠性结果](../../evidence/innovation/INNO-EVAL-GEMINI-BILINGUAL-006/RESULTS.md)。

### P0-2：中英双语“等价收益”实验

目的：不是证明翻译能力，而是证明同一个伴侣系统在中文和英文里都能保持理解、记忆、边界和语言自然度。

设计：

- 24 个语义等价但由双语者分别自然撰写的 CN/EN 场景，不使用逐字机器翻译作为唯一数据。
- 每个场景同时比较 direct Gemini 与 full Aurora，形成 difference-in-differences：
  `Aurora benefit = full - direct`，分别计算中文和英文。
- 硬指标：目标语言遵从率 100%；未请求 code-switch 率 0%；事实/记忆错误率、未授权记忆率、危机安全错误率。
- 人评指标：理解感、自然度、文化/语气合适度、时间意识、立场稳定性。
- 等价门槛预注册为：中英文 Aurora benefit 的差异不超过 `10` 个百分点，且两种语言各自都不劣于 direct baseline。
- 报告 bilingual reviewer agreement；不以 BLEU/ROUGE 作为开放式陪伴回复的主指标。

多语言评估中，人评与自动评估可能不一致，因此主结论应以盲化人评为准；可参考 [PARIKSHA](https://aclanthology.org/2024.emnlp-main.451/) 的多语言人类/LLM 评价比较。

当前机器门禁：Direct 与 Full 两路的中文、英文可见回复语言匹配均为
`48/48`；Full Aurora 中文 `24/24`、英文 `24/24` 完成。Full Aurora p50
`3.695s`、p95 `9.847s`；Direct p50 `8.349s`、p95 `13.880s`。这些数字只
证明本轮可靠性、语言遵从和测得时延，不证明语义质量等价；中英 Aurora
benefit 差值仍需三人盲评后计算。

### P0-3：30 人公网突发与 50 个隔离故事 Session

现有入口：

```powershell
pwsh .\scripts\demo\test-30-user-burst.ps1 `
  -Origin "https://最终的.trycloudflare.com" `
  -UserCount 30 `
  -ThrottleLimit 30 `
  -SandboxEntryUsers 50
```

它已经检查：

- 30 个真实账号完整注册/校准/Aurora 路径；
- 50 个故事入口产生 50 个唯一 SANDBOX owner，并逐个清理；
- real provider、API key configured、fallback disabled；
- HTTP 429 数量；
- 注册 p95 ≤ `15s`、校准 p95 ≤ `10s`、Aurora p95 ≤ `90s`；
- 30 人相互发现和环形连接；
- 报告落盘到 `.demo-runtime/burst-30-report.json`。

2026-07-28 修复前的 run-01 至 run-05 全部按失败保留，其中 run-04/run-05
准确暴露了一个配置缺陷：`DEMO_UNLIMITED_USAGE_ENABLED=true` 当时没有被
`ApiRateLimitFilter` 消费，同一公网 IP 会撞到 login bucket。修复后，非 prod
课堂 Demo 会旁路应用额度过滤器，但认证、授权、CSRF、隐私和危机安全链保留；
prod 即使误配该开关也仍强制限流。

正式连续 clean passes：

| 复验 | Gemini Aurora | HTTP 429 | Aurora p95 | Sandbox | discovery / 好友环 | 清理 |
|---|---:|---:|---:|---:|---:|---:|
| run-07 | `30/30` | `0` | `17.897s` | `50/50` 唯一 | `30/30 / 30/30` | `30/30` 账号、0 Sandbox 失败 |
| run-08 | `30/30` | `0` | `12.848s` | `50/50` 唯一 | `30/30 / 30/30` | `30/30` 账号、0 Sandbox 失败 |

run-06 也通过，但因与被中断的双语评测有短暂额外并发，只标记为
`CONCURRENT_EXTRA_LOAD_PASS`，不计入两次严格同条件复现。完整失败台账和最终
结果见 [公网 burst 总结](../../evidence/experiments/PUBLIC-BURST-30X50-001/summary.md)。

最终重试修复部署后又执行 run-09 `FINAL_CODE_SMOKE`：30/30 Gemini、429=0、
Aurora p95 `7.186s`、50/50 Sandbox、discovery/好友环 30/30，fallback、
stage failure、business failure 均为 0。它证明最终代码没有容量/隔离回归，
但不替代 run-07/run-08 的两次连续容量证据。

可以说“在该笔记本、该网络和该 Gemini 配额下连续两次完成 30 人课堂突发”，
不能直接说“生产可承载任意 50 人”。DB/Redis/线程池峰值和总成本仍未纳入本轮
报告。

### P1-1：纵向记忆、纠正与遗忘

- 20 个合成用户 × 5 次会话，植入事实、后来纠正、主动撤回、冲突信息和时间间隔。
- 至少 100 个 held-out queries。
- 对照：authority-aware memory vs naive recency/lexical memory。
- 指标：Recall@k、correction accuracy、selective forgetting、stale recall、unauthorized recall、time attribution。
- 硬门禁：withdrawn/unauthorized recall = `0`；所有失败保留。

### P1-2：主动式 Aurora 的帮助与打扰

- 30 个情景：quiet hours、deadline、long-gap return、preference changed、user declined reminder。
- A/B：proactive off vs policy on。
- 指标：帮助率、打扰率、错误时机率、拒绝后再次触发率、任务后续完成率。
- 关键不是“发得更多”，而是在提高 helpfulness 的同时不提高 annoyance。

### P1-3：共鸣体 fidelity、distinctiveness 与隐私

- 三个预置人物，每人 20 个 held-out questions。
- 让不知标签的评审从三个人物中识别回答来源；随机水平为 `33.3%`。
- 同时测 owner/acquaintance likeness、跨 10 轮 persona stability、visitor echoing、role confusion、隐私泄露。
- 只有 source identification 显著高于随机且 privacy leakage=`0`，才可以说共鸣体具有可区分的人格保真度。

### P1-4：中英语音 ASR/TTS

- 20 条中文 + 20 条英文录音，安静/轻噪两种条件。
- ASR：中文 CER、英文 WER、P50/P95 latency。
- TTS：首包延迟、完整生成时延、3 名双语评审 MOS、发音/韵律错误。
- 固定同一麦克风、音量、采样率和网络；TTS/ASR“能用”与“更好”必须分开报告。

### P1-5：社交消息 exactly-once

- 真实群聊、随机匹配、慢信分别注入客户端重试、网络断连和 worker Pod 删除。
- 统计发送成功率、重复消息率、漏投率、顺序错误率、恢复时延。
- 硬门禁：同一 idempotency key 的重复投递为 `0`。

## 主张措辞表

| 推荐说法 | 禁止偷换成 |
|---|---|
| “在本机 single-node kind 上，两次复验均在 45 秒内扩到 6 个目标 worker” | “生产环境会自动无限扩容” |
| “强删正在生成的 Pod 后，同一 durable turn 完成且历史未丢” | “实现了跨地域零故障” |
| “这条 trace 将 13.89 秒拆成 Provider 8.733 秒和平台开销 5.105 秒” | “可观测性证明回复更好” |
| “双内核在 16 个合成合同场景中 16/16 通过” | “用户一定更喜欢双内核” |
| “真实 GLM/MiMo/DeepSeek 先导对照已运行，但盲评未完成” | “已经证明优于单次调用” |
| “中英界面和回复路径存在，正式双语等价实验待做” | “中英质量完全一致” |

## 现场材料索引

- 总结果：`evidence/w3/CN-THREE-HERO-SHOWCASE-001/final-results-2026-07-28.json`
- 离线演示：`docs/demo/HERO-EXPERIMENT-OFFLINE-BACKUP.html`
- 三场景脚本：`scripts/demo/run-three-hero-showcase.ps1`
- H1 脚本：`scripts/demo/run-h1-live-demo.ps1`
- 30 人脚本：`scripts/demo/test-30-user-burst.ps1`
- 质量评估规格：`docs/research/innovation-evaluation-spec.yml`
- 真实 Provider 先导结果：`evidence/innovation/INNO-EVAL-002/real-provider-quality-analysis-2026-07-15.md`
