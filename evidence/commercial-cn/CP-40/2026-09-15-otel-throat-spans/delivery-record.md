# CP-40 OTel 关键咽喉插桩 — 增量（§2-19）

- 日期：2026-09-15；实施：后台 agent（ai/structured + 检索域）
- 愿景：V15（AI 调用与记忆检索可观测——span 在咽喉层覆盖全部调用方）

## 交付

1. **`inner.cosmos.ai.structured.call`**（callObserved 咽喉，Micrometer Observation，行为/顺序不变仅包壳）：module（bounded token 卫生同 OutboxTraceContext）、provider（调用方 preferredProvider 归一，未给→unspecified 不猜实际 leg）、leg（实际 assignedGroup）、outcome（沿用 CallStatus 枚举 + 新增 REFUSED——GATEWAY_BUSY/AI_SPEND_EXCEEDED 拒绝路径 span 诚实闭合，附 refusal 错误码）、失败附 error.type（异常类简名，**不挂异常 message**——provider 错误体可能回显请求内容）。
2. **`inner.cosmos.memory.retrieval`**（检索咽喉）：task/top_k(1-20)/hits(0-20)/outcome；与调用方级既有 span 刻意区分（咽喉版覆盖在线 Aurora/公开 API/评测全部调用方并嵌套为其子 span）。
3. **隐私决策（写进 javadoc）**：两处 span **完全不带 userId**（连存在性布尔也不带——沿用 AiTurnObservation「never the user id」与 OutboxTraceContext 既有纪律）；检索 span 不带查询原文/记忆 id/标题/consent scope——测试用 marker 字符串全属性值扫描证明 P0 内容零泄漏。
4. registry 均 @Autowired(required=false)，null→NOOP 零开销（手搓构造测试不受影响）；application.yml/pom 零改动（W3 期 tracing 配置与依赖已就位）。

## 测试

StructuredAiServiceObservationTest 5/5 + MemoryRetrievalObservationTest 4/4（成功属性/leg 与 provider 归一/FAILED+error.type/REFUSED 传播下闭合/FALLBACK 与 hits=0 诚实路径/隐私扫描）+ 回归 37/37（BadOutput/GatewayGovernance/AiTurn/AgentContextAssembler/RelevanceGate/ConsentBoundary/SemanticDefault）+ ApplicationFlowTest 8/8（上下文注入接线）。

## 诚实边界

- 验证到 micrometer TestObservationRegistry 内存捕获层（即 OTel bridge 转换层）；真实 OTLP collector 导出属部署面 operator 门禁（OTLP_TRACING_ENDPOINT 默认关）；
- streamChat 流式路径不经 callObserved 咽喉未插桩（SSE 面既有 inner.cosmos.sse.connection.duration metric；流式 span 需按 chunk 心跳设计，记录在案）。
