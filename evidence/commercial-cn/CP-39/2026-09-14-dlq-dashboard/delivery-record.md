# CP-39 Outbox DLQ admin 看板与重放 — 增量

- 日期：2026-09-14；实施：后台实现 agent（event/reliable + AdminController 域）
- 愿景：V15（毒/未知事件可见可处置，不静默堆积）

## 交付（agent X）

1. `GET /api/admin/outbox/dead?limit=&offset=`：DEAD 行列表（eventId/eventType/payload 200 字摘要/attempts/lastError/时间；id DESC；空态如实为空；outbox 关闭时 enabled:false 空页——AdminController 以 ObjectProvider 懒取，未动 schema）；
2. `POST /api/admin/outbox/dead/{eventId}/replay`：单事件重放，UPDATE 以 status='DEAD' 为守卫——重复重放 409（附当前状态）、未知 404、均不静默重置；重放只重新入队，副作用去重由 tb_inbox_receipt 保证；
3. **顺手修复真 bug**：`JdbcOutboxRepository.retry()` 的 `INTERVAL '1 millisecond'` 在 H2 无法解析（实测 Cannot parse "INTERVAL" constant）——方言孪生（Postgres 分支字节不变，H2 用 DATEADD）；并在本机真实 PostgreSQL 跑 JdbcOutboxRepositoryIntegrationTest 坐实 PG 分支不回归；
4. admin 隔离走既有 requireAdmin 模式（非 admin 401，与全部 /api/admin 端点一致）。

## 诚实边界

- claim→DEAD 全链路含 SKIP LOCKED 为 Postgres 专属——Docker 门禁测试所有物；H2 测试以真实 retry() 失败路径驱动 DEAD（注释言明）；
- CP-40 前端页面未做（web 域，本批 API 先行）；
- AdminActionLog 审计接入未做（归属不明未擅动）。

## 测试

OutboxDeadLetterDashboardTest 2/2（真实空态/字段/分页/replay 行级断言/二次 409/未知 404）；event/reliable 回归 27/27（1 Docker 门禁 skip）；全量 backend 1844/1844。
