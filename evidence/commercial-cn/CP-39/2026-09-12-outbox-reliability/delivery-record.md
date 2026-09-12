# CP-39 首个可编码增量 — outbox handler 登记/幂等/毒消息与未知事件负测（检查点 30）

1. **登记缺口（真实发现）**：`data.retracted.v1` 由 writer 写出但**无注册 handler**——outbox 模式下撤回通知会在 5 次重试后落 DEAD（撤回事件恰恰不能死于队列）。新增 `DataRetractedProjectionHandler`：v1 schema 校验（缺字段/坏 JSON/错版本=毒消息，响亮失败）+ inbox 回执（幂等锚点）
2. **H2 可移植合同 `OutboxReliabilityContractTest` 3/3**：writer↔handler 登记完备（每个 EVENT_TYPE 常量有 handler；身份字段非空）；重复注册构造期即拒；dedup 键 append 幂等
3. **真实 Postgres 生命周期 `PostgresOutboxReliabilityTest`（Docker 门控）**：SKIP LOCKED 认领、inbox 回执幂等重投、毒载荷与未知类型耗尽重试入 DEAD、replayDead 复活——H2 无法证明这些语义（SKIP LOCKED 是该组件存在的理由），故不做弱化孪生
4. **顺带修复（审计发现）**：`JdbcOutboxRepository.append` 的 `CAST(? AS jsonb)` 与 `ON CONFLICT` 从未在 H2 上运行过——按方言分支（PG 原样 / H2 MySQL 模式 INSERT IGNORE，语义同为唯一键去重）；Postgres 测试加"镜像本地可解析"快速跳过门（注册表被墙时跳过而非挂 2 分钟）
5. 全量回归 **1593/1593 绿，2 个 Docker 门控跳过**（baseline 已本地验证通过）

## 状态
- CP-39: IN_PROGRESS（handler 登记/幂等/毒消息负测落地；真实 PG 空库升级/旧库升级/N-N-1 并行属 operator 环境演练）
