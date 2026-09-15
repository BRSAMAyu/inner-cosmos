# CP-39/CP-40 DLQ 前端管理页 — 第二增量

- 日期：2026-09-15；实施：后台 agent（web admin 域）
- 愿景：V15（死信可见可处置——运营面闭环）

## 交付

`AdminOutboxDlq.tsx` 挂入既有 AdminConsole（第 9 tab「死信」，自取数不进 loadAll 懒加载，未动 AuroraApp）：
- `GET /api/admin/outbox/dead` 列表（事件 ID/类型/200 字摘要/重试次数/最后错误/最后尝试，分页 limit=20 上一页/下一页+计数）；
- `POST replay`：成功只说「已重新入队，当前状态 PENDING」并刷新（行离开本页）；409/404 后端原文逐字展示不刷新；401「需要管理员权限」；
- **诚实态**：`enabled:false` 显示「事件外发未启用（inner-cosmos.events.outbox.enabled=false）」不装作空队列；空队列如实为空。

api.ts append（OutboxDeadLetterRow/Page/ReplayResult 类型 + 两函数）。

## 测试

DLQ 8/8 + AdminConsole 回归 5/5 + AccountSettings 回归 37/37（合计 50 用例文件组绿）；tsc 绿。

## 诚实边界

AdminActionLog 审计接入仍未做（登记可选）；OTLP 级看板（Grafana 面板）属部署面 operator。
