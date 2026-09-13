# CP-15 撤回派生资产清单与逐条清理 — 第二增量

- 日期：2026-09-13；实施：后台实现 agent（event/reliable + retraction 清理域），主线程整合（V53 迁移 + schema 孪生 + 基线）
- 愿景：V20（撤回真正生效——派生资产逐条处置，不可召回的如实说不可召回）

## 交付内容

### 1. 五类派生资产清单（grep 证据矩阵，落盘 docs/commercialization/ledger/derivative-asset-retraction-inventory.yml + 契约测试）

| 资产类 | 真实落点 | 撤回动作 | 状态 |
|---|---|---|---|
| 缓存 | 无长驻缓存面（@Cacheable 零命中；Redis 仅 TTL 瞬态：live event 15m/stage 1m/幂等/限流/会话） | 无物可失效 | NOT_APPLICABLE（如实） |
| 对象存储 | 无此资产面（OSS/S3/MinIO 零命中；转写仅文本列；导出包从不内联 blob） | 无删除目标 | NOT_APPLICABLE（如实） |
| 导出包 | 内存构建一次性 HTTP 响应，无 tb_export* 表无落盘 | 服务端无可失效副本 | NOT_APPLICABLE（如实） |
| 推送副本 | tb_push_delivery.title/body/deep_link | **DELETE 未投递（PENDING/RETRY）**；DELIVERED 已出站不可召回（如实标注） | IMPLEMENTED |
| Provider 副本 | 出站 LLM 内容 + tb_ai_interaction_log（无主体映射列） | 出站即不可召回；靠事前管控（PROVIDER_EGRESS/LOCAL_ONLY/最小化） | NOT_APPLICABLE（不编造召回动作） |
| 匹配向量（第六步） | tb_capsule_embedding | 复用既有 retireForCapsule 重断言（CAPSULE 直取；GRANT 经 consumer_id 解析；MEMORY 如实不复处置——属主路径已同步 retire） | IMPLEMENTED（复用） |

### 2. data.retracted.v1 消费者扩展（勿重复实现消费者骨架——只加逐条清理动作）

`DataRetractedProjectionHandler` → `RetractionDerivativeCleanupServiceImpl.cleanForRetraction`：逐资产独立 try（失败不阻断其他资产）；每动作 REQUIRES_NEW 落一行 `tb_retraction_cleanup_result`（UNIQUE(outbox_event_id, asset_key)，SUCCESS/FAILED/NOT_APPLICABLE+原因，失败行不随外层回滚消失）；任一 FAILED 则 handler 抛出走 outbox 重试；幂等（settled 跳过、FAILED 重试原行 UPDATE、DELETE 天然幂等）。

### 3. 主线程整合

agent 域限制未落正式迁移——补 `V53__retraction_cleanup_results.sql`（PG）+ schema.sql H2 孪生（executor 的懒建 IF NOT EXISTS 保留为双保险）；Flyway 基线 51→53、v20 链 32→34、表 107→109、identity 100→102。

## 测试证据

- 新增 `DataRetractedDerivativeCleanupTest` 5/5（五资产动作/如实标注、失败注入+愈合、重放幂等、MEMORY 主体不复处置、GRANT 解析）+ 清单契约测试 1/1（evidence 路径逐一校验存在）；
- agent 域内回归 28/28（1 Docker 门禁跳过）+ 邻接 7/7 + ApplicationFlowTest 8/8；
- 主线程全量整合回归：**backend 1837/1837（2 Docker-gated skips）+ web 783/783 + tsc clean**。

## 诚实边界

- 推送清理按 user 扫未投递行——v1 载荷无 wake_intent↔主体链接列，宁可少发不外送已撤回内容；DELIVERED/设备令牌不动；
- tb_ai_interaction_log 不随主体撤回删除（无主体映射列且属 P0 对话层，仅账户删除清理）；
- PG 方言 DDL 语义在 H2 全真表验证；真库执行属部署门禁。
