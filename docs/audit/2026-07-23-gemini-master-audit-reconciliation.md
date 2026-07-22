# Gemini Master Audit 独立复核与修复合同

> 复核日期：2026-07-23
> 复核基线：`7ed34e9`（开始复核时的 `main`）
> 上游材料：`.agents/orchestrator/master_audit_report.md`
> 性质：当前执行权威 24 的强制事实附件；上游报告是发现线索，不是可直接执行的修复规格。

## 1. 结论

Gemini 报告提出的 36 项中：

- **28 项确认存在**；
- **6 项部分成立或因果/严重度表述不准确**；
- **1 项与另一项完全重复**（2.2 = 1.1）；
- **1 项不成立**（2.3：外键列已经由首列唯一复合索引覆盖）。

所以不能接受报告的“36 Definite / 100% verified”声明，也不能机械实施其精确修法。原执行方案已经覆盖并发、隐私、SSE 恢复、慢信状态和 UI 状态矩阵，但覆盖粒度偏原则化；本文件把真实缺陷升级为 `W0V-VERIFIED-AUDIT-CLOSURE`，未关闭前不得把 W1/W2/W3 宣称完成。

状态词：`CONFIRMED` = 当前代码可直接证明；`PARTIAL` = 风险存在但报告事实、因果、范围或修法不准确；`DUPLICATE` = 不独立计数；`FALSE` = 当前代码反证。

## 2. 逐项裁决

### R1：架构、状态机与规格

| ID | 裁决 | 当前事实 | 正确修复合同 |
|---|---|---|---|
| 1.1 | CONFIRMED / P0 | `LetterDeliveryJob` 先读状态再 `updateById`，可覆盖并发 block/状态变更。 | 用 `UPDATE ... WHERE id=? AND status=?` 原子认领；状态日志与更新在同一短事务；0 行表示竞争失败且不得重试覆盖。加入 scheduler-vs-user 并发测试。 |
| 1.2 | CONFIRMED / P1 | 到期 `SENT→FLYING` 后，同一 tick 又将全部 `FLYING→DELIVERED`。 | 明确 departure/arrival 语义：发送后进入可见 `FLYING`，仅 `estimated_arrival_at <= now` 才投递；注入 `Clock`，测试飞行中、到期、重跑和多 scheduler。 |
| 1.3 | CONFIRMED / P2 | 公开 `/deliver` 始终被 service 拒绝，是死合同。 | 删除公开端点、OpenAPI 与客户端残留；投递只允许内部 scheduler/worker 能力，不把管理员作为隐式绕过。 |
| 1.4 | CONFIRMED / P0 | owner 配置的 `maxConversationTurns` 未参与运行时 enforcement。 | 不把会话上限错误折算成每日上限；分别原子执行“单 session 最大轮次”和“每日反滥用额度”，并定义 owner/visitor、重试、并发 turn 的语义。 |
| 1.5 | CONFIRMED / P0 | event listener 的 null fallback 为 30 天，nightly fallback 为 `createdAt`，重力会跳变。 | 抽取唯一 `GravityTimePolicy`，统一锚点、时钟、舍入和衰减版本；后台只写计算字段，用户 importance 不得被旧快照覆盖。 |
| 1.6 | CONFIRMED / P1 | 两个 `@Async` listener 无依赖，重力可能早于新记忆创建。 | **禁止只加 `@Order`**，它不保证异步完成顺序。用 durable projection dependency/receipt，或单编排 consumer 先 extraction 后 affected-memory gravity；重放幂等。 |
| 1.7 | PARTIAL / P1 | 时间源确实混用且 3 分钟硬编码；报告把两个互不比较的时钟直接解释为“偏移 8 小时”并不严谨。 | 持久化时间统一 `Instant/UTC`，业务显示/日额度按用户 IANA timezone，全部通过注入 `Clock`；parallax 由可解释策略计算。不要全局硬编码上海时区。 |
| 1.8 | CONFIRMED / P1 | 无 owner-scoped draft edit；回复父信状态依赖前端追加调用。 | 增加带 version/ETag 的 draft PATCH；compose/send 使用 idempotency key。父信只在回复真正发送成功后原子或可靠事件转为 `REPLIED`，创建草稿不能提前改变父状态。 |

### R2：并发、事务与存储

| ID | 裁决 | 当前事实 | 正确修复合同 |
|---|---|---|---|
| 2.1 | CONFIRMED / P0 | `MemoryCard.versionNo` 没有形成乐观锁；gravity 与用户 importance 都以整实体 `updateById`。 | 优先改为字段级 conditional update + version bump；若采用 MyBatis `@Version`，必须迁移/default/backfill 并审计所有写路径。用户编辑优先，后台冲突后重新计算，不能覆盖。 |
| 2.2 | DUPLICATE | 与 1.1 是同一根因和同一路径。 | 只用一个工作项和一组并发证据关闭，避免重复修补。 |
| 2.3 | FALSE | V10 的唯一索引以 `memory_id` 开头；V18 的唯一索引以 `capsule_id` 开头，已是外键查找可用的 leading B-tree。 | 不添加重复索引。用 `pg_indexes` 与 `EXPLAIN` 留证；只有真实 workload 证明现有复合索引不适配时再增索引。报告的 PostgreSQL “page lock 导致死锁”表述也不准确。 |
| 2.4 | CONFIRMED / P0 | `PersonaChatServiceImpl.reply()` 在 `@Transactional` 内调用外部 LLM。 | 三阶段状态机：短事务 reserve quota/turn intent → 事务外 provider call → 新短事务按 expected state finalize；支持幂等、补偿、取消、block/撤权复核和失败恢复。 |
| 2.5 | PARTIAL / P2 | 当前无 HNSW，规模增长后可能退化；但小数据精确扫描不等于当前漏洞。 | 先构造代表性数据并 `EXPLAIN (ANALYZE, BUFFERS)`；达到阈值后以新 Flyway 版本（**不是已占用的 V21**）创建按模型/有效状态设计的 HNSW，并测 recall、P95、build/memory 成本和回退。 |
| 2.6 | CONFIRMED / P1 | Java 与 `vector(1536)` schema 都固定维度，单改字符串无法升级模型。 | 将维度作为“embedding model version contract”校验并 fail fast；模型换维度要新列/表或 expand-contract migration、回填、双读/切换、重建索引。不可只按 `vector.length` 动态拼 SQL。 |
| 2.7 | CONFIRMED / P1 | Redis `XADD` 与 `EXPIRE` 是两个命令，崩溃窗口可留下无 TTL key。 | 用 Lua 原子执行 add/trim/expire，或经验证的事务方案；测试首次创建、既有 TTL、脚本失败、Redis 重启与并发 publish。 |
| 2.8 | PARTIAL / P3 | 清理只在后续 stage/consume 触发，但实现有 1024 上限且仅 Redis-disabled/dev 路径，报告夸大为无界泄漏。 | 用注入 `Clock` + 定时清理/TTL cache，shutdown 清空；验证长期 idle 后过期。不得把它列成生产 P0。 |

### R3：AI 安全与 P0—P3 数据边界

| ID | 裁决 | 当前事实 | 正确修复合同 |
|---|---|---|---|
| 3.1 | CONFIRMED / P0 | capsule create 把 memory title/summary 直接交给 persona generation；genome/context 只有有限 contact masking。 | 建立目的绑定的 P1→P2 compiler：provider 调用和持久化前先做授权选择、实体/PII/secret 清除与最小化；源引用单存，公开 runtime 只读 P2-safe features。提供 owner preview、撤回传播和对抗泄漏测试。 |
| 3.2 | PARTIAL / P0 | 当前 persona chat **没有直接注入 owner P0 对话**，并只选择授权 evidence；但 3.1 形成的未充分净化 persona/P1 派生物仍可能被诱导泄漏。 | 作为 3.1 的下游威胁关闭：最小权限 evidence selector、canary、egress DLP/grounding critic、撤权即时失效。不得把不存在的 P0 raw-history 注入当作事实。 |
| 3.3 | CONFIRMED / P1 | 慢信有安全过滤但没有 PII/credential policy。 | 不要静默改写私人信件；客户端/服务端检测并提示，默认给出脱敏预览，凭据/高危标识硬阻断，其它 PII 需显式确认并记录最小 consent receipt。 |
| 3.4 | CONFIRMED / P0 | `PromptBuilder.withUserInput` 使用用户可复现的文本 delimiter。 | 优先使用 provider-native system/user role；旧拼接路径使用结构化 JSON/envelope 且用户内容永远作为 data。**禁止关键词删除式 sanitize**，它既可绕过又会破坏真实表达。加入 delimiter/instruction/homoglyph adversarial eval。 |
| 3.5 | CONFIRMED / P0 | persona 主要依赖软 prompt 约束，缺少完整的输入—检索—输出强制链。 | 在 prompt 外实施 capability/evidence allowlist、purpose check、output leakage/grounding critic 和安全替代回复；禁止把 owner raw data 放入模型上下文。 |
| 3.6 | PARTIAL / P1 | ThoughtShredder 已通过 `StructuredAiService`/`JsonUtils` 序列化 context，并非报告所称手写 JSON；`MemoryExtractAgent` 仍把 raw text `.formatted()` 到 instruction。 | 只修真实路径：MemoryExtract 去掉重复 raw interpolation，统一 instruction + serialized context；测试引号、delimiter、超长文本和恶意 schema 片段。 |
| 3.7 | CONFIRMED / P0 | crisis/abuse 规则以 literal `contains` 为主，零宽字符可绕过。 | 共享 Unicode NFKC/default-ignorable normalization，谨慎处理空白/标点/同形字符；原文不进入普通日志。建立中英变体、误报和多通道测试。 |
| 3.8 | PARTIAL / P0 | 报告称“危险 token 已逐个发出”不成立：当前先得到完整 reply、调用 `sanitizeLlmOutput`，再切片为 SSE；但现有输出检查覆盖面过窄。 | 对 POST/SSE 共用完整响应的 policy/DLP/quality gate，失败时在任何 bubble/token 发布前替换或拒绝，并记录 policy version。未来若改真 provider-token streaming，再引入缓冲增量审核。 |
| 3.9 | CONFIRMED / P1 | safety check 以单条输入为主，无明确滚动风险状态。 | 建立 session-scoped、可衰减、可解释的多轮 risk state；不做诊断、不把 P0 放入 metrics；测试渐进表达、否定语境、引用他人、恢复与升级资源。 |

### R4：前端、SSE 与 UX 容错

| ID | 裁决 | 当前事实 | 正确修复合同 |
|---|---|---|---|
| 4.1 | CONFIRMED / P0 | `recover()` 无 turn-scoped cancel/epoch，旧 turn 可在新 turn 中调用全局 `finishTurn()`。 | 每 turn 使用 generation + `AbortController`；每次异步写前核对 epoch/turn；stop/new send/logout/unmount 取消旧恢复；reducer 保持幂等。 |
| 4.2 | CONFIRMED / P0 | clean EOF 无 terminal 时 `streamAurora` 正常返回，UI 不会可靠结束/恢复。 | stream 返回 terminal contract；EOF-without-terminal 进入同 turn 的有界 timeline recovery，超限显示可重试错误。禁止无条件 finally 清除可能已开始的新 turn。 |
| 4.3 | CONFIRMED / P0 | browser EventSource 与 bearer loop 都可能在失效认证下持续重连。 | 收敛到可读 HTTP status 的 fetch-SSE；refresh 最多一次，指数退避+jitter+上限/circuit，offline/background/logout 立即暂停/中止。原生 EventSource 无法按报告建议直接读取 401。 |
| 4.4 | CONFIRMED / P1 | relation/thread/group loader 没有 request epoch，慢响应可覆盖当前选择。 | 每资源 AbortController/sequence key；只有最新 selection 可提交；明确 idle/loading/success-empty/error。 |
| 4.5 | CONFIRMED / P1 | create-draft 后 send 失败，重试会再次 create。 | 客户端保存 draft id + idempotency key；失败时继续发送/编辑原 draft。后端提供幂等 compose-and-send 或明确 resumable draft contract。 |
| 4.6 | CONFIRMED / P2 | transcription promise 在 unmount 后仍可 set state/改外部 draft。 | unmount abort + mounted/generation guard；停止 recorder、释放 media tracks/buffer；完成回调只写当前 component generation。 |
| 4.7 | CONFIRMED / P1 | `main.tsx` 没有 ErrorBoundary。 | 顶层 fatal boundary + 每个 product space 局部 boundary；提供保留草稿的重试/重载，遥测必须脱敏；对渲染异常做组件/E2E。 |
| 4.8 | CONFIRMED / P1 | 多个 social action 使用普通 button，无 per-resource busy guard。 | per-resource action state + disabled/aria-busy + backend idempotency/expected-state；debounce 不能替代服务端合同。 |
| 4.9 | CONFIRMED / P1 | `threadLetters.length === 0` 同时表示 loading 和真实空列表。 | hook 暴露显式 loader state；UI 分开 loading、empty、error、success，快速切换时结合 4.4。 |
| 4.10 | CONFIRMED / P1 | password/delete form 在异步结果前同步关闭并清空。 | callback 改为返回 Promise/result；只在成功后清空，失败保留输入并内联聚焦错误；delete 成功后再清理本地状态。 |
| 4.11 | CONFIRMED / P2 | 多个用户生成文本容器仅 `pre-wrap`/普通段落，没有通用任意断行保护。 | 为 UGC primitive 统一 `overflow-wrap:anywhere; word-break:break-word; min-width:0`，覆盖 URL/CJK/emoji；做 320/390px、200% 字体 visual test。 |

## 3. 原方案覆盖情况

原方案不是完全遗漏，而是“方向覆盖、验收不够具体”：

- 文档 24 已要求慢信幂等可恢复、Aurora SSE 恢复、P0—P3 授权 compiler、UI 全状态矩阵、PostgreSQL/Redis 并发和高风险合同测试；
- `remaining-work-handoff.md` 已要求长文本、录音、失败恢复、共鸣体授权和慢社交状态机；
- acceptance ledger 已有 API concurrency、resumable SSE、data rights、capsule safety 与 UX complete。

缺口在于没有把这些 36 个线索逐项绑定到复现与反证，Coding Agent 可能在宏观工作包完成后仍漏掉竞态。因此从现在起，本文件的有效项是 W0 之后的强制 `W0V`，并成为 W1/W2/W3 的共同前置条件。

## 4. W0V 实施顺序

1. **P0 数据/用户意图正确性**：1.1/2.2、1.4、1.5/2.1、2.4、3.1/3.2、3.4/3.5、3.7/3.8、4.1—4.3。
2. **P1 状态与恢复**：1.2、1.6—1.8、2.6/2.7、3.3/3.6/3.9、4.4/4.5/4.7—4.10。
3. **P2/P3 性能与打磨**：2.5 以负载证据决定索引、2.8、4.6、4.11；2.3 只保留反证测试，不写重复迁移。
4. 每批先写 failing regression/concurrency/adversarial test，再修复，再跑 focused journey；共享 Flyway、API types、locale、bundle 只能由 Integrator 串行合入。

建议并行不超过 3：Backend State/Transaction、AI Privacy/Safety、Frontend Resilience。三者不得同时改共享 ledger、Flyway 序列或 generated bundle。

## 5. W0V 硬验收

- 本文件 36 行均有 `closed` 或 `rejected-with-reproducible-counterevidence` receipt；不能用“代码看起来已改”关闭。
- 并发项必须有 barrier/latch/Testcontainers 或可重复故障注入，证明用户 block/edit 不被覆盖、LLM RPC 期间不持有 DB transaction、Redis key 一定有 TTL。
- AI 项必须有固定 adversarial corpus，记录总样本与失败；不得把 P0/P1 原文、prompt、密钥或稳定用户标识写入报告。
- SSE/UI 项必须覆盖旧 turn 与新 turn 交错、clean EOF、401、offline、unmount、空数据、快速切换、双击和失败重试。
- pgvector 只有在 representative dataset 的计划与实测支持时才创建 ANN index；迁移版本从当前 V21 之后分配。
- 通过 focused gates 后再跑当前集成 HEAD 的 Java/Web/Playwright/secret/IaC 全门，并更新 acceptance ledger、closure state、evidence 与 commit。
