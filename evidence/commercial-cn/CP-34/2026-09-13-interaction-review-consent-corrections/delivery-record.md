# CP-34 关系互动回顾（替代温度分）+ 双方同意纠错状态机 — 首个工程增量

- 日期：2026-09-13；实施：主线程（与后台 agent 的 CP-35/CP-32 并行）
- 愿景：V06（不评判用户的关系——产品只呈现痕迹，不打分）、V13

## 交付内容

### 1. 关系互动回顾替代温度分

**被替换物**：`GET /api/relation/health` 返回的 `healthScore`——积极情绪占比 ×1.5 放大的评判分，且无数据时伪造 0.5 中位分。这正是愿景禁止的「关系好坏评判」。

**替换物**：`GET /api/relation/review?label=&weeks=4`（`RelationInteractionReviewVO`）——只数真实发生的事：
- `mentionCount`：窗口内该关系在用户记忆中的提及次数（窗口过滤 SQL 级）；
- `weeksActive`：有提及的不同 ISO 周数（节律，非评判）；
- `emotionSpectrum`：情绪标签 → 真实出现次数（按次数降序）；
- `recentTriggers`：最近 3 条真实触发摘要（新到旧）；
- 空窗口 → 诚实空回顾（0/0/空/空），**没有任何分数字段**；窗口钳制 1–26 周。

前端全链路切换：`api.ts` `RelationReview` 类型 + `relationReview()`；hook `relationReview` 状态（保留 4.4 过期选择丢弃竞态语义，断言随行更新）；`RelationsView.tsx` 温度条 → 回顾块（计数 + 情绪谱 + 两条固定声明：「这是互动记录的回顾，不是关系好坏的评判」「数据只来自你自己的记忆卡片，不会给关系打分」）；中英双语。/health 端点与 healthScore 字段彻底移除（MockMvc 断言 404 与响应无 healthScore）。

### 2. 双方同意纠错状态机（V51 + H2 孪生）

慢信线程承载的关系理解来自双方数据，一方不能独自改写：
- `tb_relation_correction`（V51，PG 侧 FK 指向 tb_letter_thread；schema.sql H2 孪生同列）；
- `RelationCorrectionService`：`PROPOSED →(accept 仅对方) APPLIED | (reject 仅对方) REJECTED | (withdraw 仅发起人) WITHDRAWN`；
- 全部转移为条件单行 UPDATE（`WHERE status='PROPOSED'`）——竞态双裁决恰一胜者、败者得显式 CONFLICT，终态不可复活；
- API：POST /api/relation/corrections（+ incoming/outgoing/accept/reject/withdraw）；
- 负面矩阵：发起人不能自 accept（403）、第三方不能裁决（403）、非线程方不能发起（403）、空白纠错（400）、终态后再动（409）。

## 测试证据

- `RelationInteractionReviewTest` 3/3：空窗口诚实空回顾（旧代码此处伪造 0.5）；窗口内真实计数（40 天前与不同标签的行不混入；情绪谱按真实次数降序；触发摘要新到旧）；控制器出回顾 JSON 无 healthScore 且 /health 404；
- `RelationCorrectionConsentContractTest` 5/5：accept→APPLIED 双方同视；仅对方可裁决（自 accept/第三者 403）；非线程方不可发起 + 空白 400；reject 终态带理由 + 撤回仅发起人仅 PROPOSED；竞态双裁决恰一胜者显式 CONFLICT；
- 主线程全量整合回归：**backend 1813/1813（2 Docker-gated skips）+ web 767/767 + tsc clean**；
- 整合时更新 Flyway 基线（51/32 迁移、107 表、107 源表、100 identity 列、PG-only FK `fk_relation_correction_thread` 登记为例外）。

## 诚实边界

- 关系标签 → 平台连接（好友/慢信线程）的映射未建立：互动回顾基于记忆提及（RelationMention），CP-59 的信件回轮深度是平台级周报口径，两者尚未在单关系粒度打通——登记后续；
- 纠错的 `correctionField` 目前是字段名自由文本（relationLabel/threadTitle 等），APPLIED 后由拥有该字段的调用方应用——本批交付状态机与同意流，未绑定具体字段的自动应用；
- 前端纠错提案 UI 未接（后端 API 与状态机闭环，登记后续批次）。
