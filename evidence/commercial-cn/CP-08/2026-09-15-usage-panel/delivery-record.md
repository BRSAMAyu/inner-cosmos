# CP-08 使用时长前端呈现 — 第二增量（登记项闭环）

- 日期：2026-09-15；实施：后台 agent（web 域）

## 交付

`UsageTodayPanel`（account 面、QuotaPanel 之下）：主行「今天约 X 分钟」+轮次+「按已完成对话轮次的时长累计，只是记录，不做评判」；reminderDue 时 role="status"（非 alert）原文显示后端 reminderNote 不改措辞；失败内联错误+重试不伪造 0。落点选型：account 面而非会话页细条——细条每次进会话重复出现违背「平静呈现一次」。

## 测试

UsageTodayPanel 3/3；全量 web 869/869。
