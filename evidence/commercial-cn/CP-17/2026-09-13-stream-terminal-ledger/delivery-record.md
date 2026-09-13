# CP-17 SSE 流终态台账回写 — 第三增量（首增量残余缺口闭合）

- 日期：2026-09-13；实施：主线程
- 前置：2026-09-13 第二增量（网关预算/并发/deadline）在诚实边界中登记的残余——「emitter onError 台账回写缺失：流式中断在台账中不可见」
- 愿景：V15（透明度——真实 Provider 出站的每一次调用，包括中断的流，都可审计）

## 缺口

`ConsentEnforcingLlmClient.streamChat` 打开流时记 `STREAM_OPENED` 后再无终态记录：中途断掉的流与健康的流在台账里最后一行都是 STREAM_OPENED，无法区分。

## 交付

### 1. `ai/gateway/StreamOutcomeLedger.java`（新）

单流终态记账器：`open()` 记 STREAM_OPENED 并返回该流的台账；`completed()/failed(t)/timedOut()` 以 CAS 守卫——**首终态胜出**（容器在 error/timeout 后也会跑 onCompletion，绝不产生矛盾的「completed」尾记录）；每流恰好一条终态；记账本身 try-catch（台账故障不得反噬为流的二次故障）；`failed(null)` 落 `STREAM_FAILED:Unknown`（降级仍诚实）。

### 2. `ConsentEnforcingLlmClient.streamChat` 接线

打开后注册三个终态回调（`onError→failed`、`onTimeout→timedOut`、`onCompletion→completed`）；无 ledger 的旧式构造不注册不伪造；同步失败路径维持既有 `FAILED:<ExceptionSimpleName>`。

### 3. 过程中发现并纠正的设计错误（记录为工程证据）

初版按「Spring 回调单槽、注册即覆盖」的旧语义写了反射链式注册——测试立刻暴露 **StackOverflowError**：反编译 spring-webmvc 6.1.15 证实回调字段是 `private final` **复合体**（内含回调集合，`onError(Consumer)` 是往复合体里加），把复合体读作「前回调」再包进自己的 lambda 造成自引用递归。结论：Spring 6 下简单注册天然与 delegate 自注册的回调**共存**，链式注册既不必要也有害——已回退为简单注册，复合体共存性由测试 `aDelegateTerminalCallbackIsChainedNotDropped` 验证（delegate 先注册 onError，守卫再注册，触发复合体两者都跑）。

## 测试证据

- `StreamOutcomeLedgerTest` 4/4：健康开→完；中断 failed→晚到 completed 被压；timeout/failed 互斥先到先记（两条流的记录序）；null 失败降级 Unknown。
- `ConsentEnforcingStreamLedgerTest` 6/6：三个终态回调确实注册在 emitter 上（读 Spring final 复合体字段断言）；容器式驱动复合体 error 回调 → STREAM_FAILED 且晚到 completion 不双记；completion → STREAM_COMPLETED；delegate 自注册回调共存不被丢；consent 拒绝 → FAILED:BusinessException 且不触达 delegate；无 ledger 构造照常出流、零伪造。
- 受影响面回归 19/19：VoiceSkillGatewayTest、MiniMaxLlmClientStreamFallbackTest、AuroraStageRoutingLlmClientTest、PromptLanguageLlmClientTest、FailoverLlmClientTest、ConsentCenterTest。
- 测试驱动方式如实说明：单测通过反射取 Spring 的 final 复合回调对象并直接调用（容器在生产中调用的正是该复合体）；未起真实 servlet 容器。

## 诚实边界

- STREAM_TIMEOUT 分支的语义由 StreamOutcomeLedgerTest 直接覆盖；端到端容器超时路径未在测试中真实触发（MockAsyncContext 不模拟超时），注册形状与 error 完全对称。
- Aurora 外层对浏览器的 SSE（`AuroraAgentServiceImpl.stream`）不属于 Provider 出站，不进 GatewayCallLedger——其 provider 调用经 `callObserved` 已有完整记账，本批不重复。
