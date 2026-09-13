# CP-08 使用时长累计与克制提醒 — 增量

- 日期：2026-09-14；实施：主线程
- 愿景：V04（陪伴不是黏住——自己的使用时间如实可见，超时一次温和提醒）

## 交付

1. **服务端口径**：`UsageTimeService`——按需聚合 tb_conversation_turn 的 COMPLETED/PARTIAL 轮次时长（completed−started；CANCELLED 与在途轮不计，无伪造时间）；日界锚定 Asia/Shanghai（与 CP-03/CP-59 指标同一时间纪律——测试用服务自身的锚点数学验证边界轮入当日）；方言无关（行内 Java 求和，无 SQL 方面函数）；无新表无迁移（派生视图，无撤回面）。
2. **API**：`GET /api/me/usage/today`——date/activeSeconds/turnCount/reminderAfterMinutes/reminderDue/reminderNote/basis=COMPLETED_TURN_DURATION（与配额视图同型：透明数字+口径声明）。
3. **克制提醒**：`inner-cosmos.usage.reminder-after-minutes`（默认 45，与 CP-26 静默窗同配置命名面）；到点仅一个布尔+一句平静文案（「今天和 Aurora 待了约 X 分钟。照顾好自己，星空不会走。」）——不锁死、不打分、不搞连续性机制（anti-dark-pattern）；时长文案写「约」（重叠轮按上界求和的诚实标注）。

## 诚实边界

- 使用时长不进指标库（CP-03 指标不含此项——使用时长对用户可见、不作为商业指标素材，语义已在 basis 字段写明）；
- 前端提醒呈现未做（API 先行，前端消费登记后续）；
- 重叠轮次按轮时长求和是上界口径（单人串行对话下即精确值）。

## 测试

UsageTimeServiceTest 5/5（真实在场求和且取消/在途/异日不计、阈值下安静、阈值后克制文案、无完成时间不伪造、上海锚点边界、控制器诚实视图）；全量 backend 1844/1844。
