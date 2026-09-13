# CP-17 网关预算/并发/deadline 治理与 429 负测 — 首个工程增量

- 日期：2026-09-13；实施：后台实现 agent（ai/gateway、ai/observability、ai/structured、application.yml 文件域），主线程整合回归
- 愿景：V09（AI 服务连续可用——预算与并发治理是其在供给侧的反面）、V11（成本护栏）

## 交付内容

### 1. 模型合同版本（`ai/gateway/ModelContractVersion.java`）

封闭枚举：`GLM_DIR1`、`GLM_DIR2`、`GLM_TTS1`（占位常量）；`forProvider()` 按渠道推导；`isRegistered()` 对占位值返回 false。**诚实边界**：在算法备案/深度合成登记落地（操作者门禁）前，所有远端渠道的合同状态实际为 `PENDING_CN_FILING`/`UNREGISTERED`，不伪造注册态。

### 2. 网关调用治理器（`ai/gateway/GatewayCallGovernor.java`）

- **全局并发闸**：Semaphore，`inner-cosmos.ai.gateway.max-concurrency=16`（默认）；
- **超时执行**：`runWithDeadline` —— 守护线程池 + `ContextPropagatingTaskDecorator`（保 MDC/安全上下文传播），`call-deadline-ms=120000`；超时抛 `GatewayDeadlineExceededException`（marker：`gateway deadline exceeded`）且不吞中断；
- 获取并发闸失败 → `ErrorCode.GATEWAY_BUSY`（HTTP 429 语义，新增错误码）。

### 3. 接线（`StructuredAiService.callObserved`，仅 REMOTE 调用）

预算（ProviderSpendGuard，既有）→ 并发闸 → deadline 的三层串行；`GATEWAY_BUSY` 在 try 块之前抛出，传播为可重试错误而非被压平进 fallback；lease 在 finally 释放；MOCK 桶完全绕过（本地演示不受闸约束）。`GatewayCallLedger.contractVersion` 由 String 收紧为枚举。`ProviderSpendGuard` 新增 `recordedCallsToday()` 只读接口。

### 4. 429/deadline 负测（`StructuredAiServiceGatewayGovernanceTest` 6/6）

并发闸耗尽 → GATEWAY_BUSY 且 `recordedCallsToday()==0`（fail-closed：未执行不计费）；deadline 超时 → 异常且零计费；中断传播为 interrupt 状态不吞；GATEWAY_BUSY 不被 fallback 压平；共享预算但闸释放后重试可成功；MOCK 通道绕过全部闸。

## 测试证据

- 新增 16 tests：GatewayCallGovernorTest 6/6、GatewayCallLedgerContractVersionTest 4/4、StructuredAiServiceGatewayGovernanceTest 6/6；
- 回归：ProviderSpendGuardTest 5/5、StructuredAiServiceBadOutputTest 8/8、VoiceSkillGatewayTest 3/3；
- 主线程整合全量回归：**1711 tests, 0 failures, 2 Docker-gated skips，BUILD SUCCESS**。

## 诚实边界（残余缺口，登记下一批）

- **SSE streamChat 路径未治理**：Aurora 流式对话仍无并发闸/deadline/预算接线——本批有意不动 `streamChat`（流式语义下 semaphore 泄漏与 deadline 语义需要单独设计：按 chunk 心跳续租而非整调用租约）；
- **emitter onError 台账回写缺失**：`ai/client/` 中 SSE emitter 错误路径未写 GatewayCallLedger，流式中断在台账中不可见——已登记为下一批候选；
- 模型合同注册态：等待操作者完成真实备案/登记后切换枚举占位值（人工门禁，agent 不写 APPROVED/REGISTERED 态）。
