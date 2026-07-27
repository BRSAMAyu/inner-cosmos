# Aurora 鲜活智能与恢复实现证据（2026-07-27）

权威设计：`docs/superpowers/specs/2026-07-27-aurora-living-intelligence-recovery-design.md`

## 已实现

- 当前轮 `DeliberationPlan v2`：规划核在说话核前完成，包含话题、用户状态、
  Aurora 立场、记忆决策、回应计划、打断计划和安全契约；旧的 next-turn pipeline
  只保留为显式回滚开关。
- 当前会话长上下文：产品硬上限 200k input tokens，并按实际 provider 窗口、输出预留
  和安全余量取更小值；稳定前缀、开场锚点、纠正/承诺/作品锚点和最近尾部优先保留，
  截断时写入显式边界。
- 记忆语义准入：不再把原始余弦分数与词法分数直接 `max`；按
  provider/model/version/locale 精确匹配校准，使用绝对阈值和 top1/top2 margin，
  缺失或版本不匹配时 fail closed。
- 打断续接：只把已提交或 `deliveredChars` 已送达前缀写入 interruption delta；
  未发送正文和取消 bubble remainder 不进入共同事实或下轮模型上下文。
- Pod/断网恢复：turn 具有 generation/delivery lease 和单调 fencing token；
  已持久化正文按 `deliveredChars` 精确续送；首个权威正文字符前崩溃时，接管 Pod
  依据受权限保护的消息引用在原 turn 重新生成，不重复用户消息或 turn。
- 客户端恢复：持久化 `Last-Event-ID`，刷新/断网后按 durable timeline 重放；
  旧请求恢复结果不能覆盖新回合或 logout。
- 安全体验：普通绝望表达不会因三次累计直接升为 HIGH；只有明确当前高危信号进入
  persistent crisis state。卡片低干扰、可折叠且保持可达性。
- 国际化：`zh-CN/CN` 与 `en-SG/SG` 使用结构化资源目录。新加坡包含 999、995、
  SOS 1767 与 WhatsApp 9151 1767。
- 来源可信度：foreground acknowledgement 不写入 Aurora transcript；终态披露实际
  provider/model、配置 provider、foreground source、planner 状态、fallback 原因和
  阶段耗时。Mock 标为演示模式，真实 provider 不可用时标为基础回应。
- 时间上下文：结构化 timezone/locale/localDateTime/client hint status 进入模型；
  上一次 Aurora 消息从服务端持久化时间计算相对间隔，不信任客户端时钟。

## 机器验证

- Java 21 production compile：通过。
- 当前轮规划、长上下文、记忆准入：61 项聚焦测试通过；后续上下文/时间接线复验
  16/16 通过。
- 安全：`SafetyControllerTest` 22/22、`SessionRiskAggregatorTest` 7/7、
  `SafetyServiceSessionEscalationTest` 7/7、`SafetyServiceTest` 23/23。
- 接管与打断：`ConversationChoreographyIntegrationTest` 15/15、
  `ConversationTurnTakeoverServiceTest` 1/1、`InterruptionDeltaBuilderTest` 2/2、
  `ConversationTurnRecoveryJobTest` 2/2。
- PostgreSQL 16 + pgvector Testcontainers：33 条 Flyway 迁移，4/4 基线测试通过。
- 前端 TypeScript：通过；Vitest 全量 88 files / 663 tests 全部通过。
- Vite production build：通过。
- `git diff --check`：无内容错误，仅 Windows LF/CRLF 提示。

## 证据边界

- Provider 已经生成但尚未持久化、且用户尚未看到的内部 token 无法逐 token 恢复；
  系统在同一 turn 重生成，不能声称 provider 原生续传。
- 一次 Maven 全量运行期间共享 `target/classes` 被并行工作清空，造成大量基础类
  `NoClassDefFoundError`；该次结果属于构建目录并发污染，不作为产品回归证据。
  聚焦测试、PostgreSQL 基线和前端全量均在干净的串行窗口重新验证。
- 真实 EKS 删除 Pod、真实 Redis、多设备和真实模型长会话仍需要在部署环境执行
  chaos drill；单元/集成测试证明接管协议，但不替代现场基础设施验收。
