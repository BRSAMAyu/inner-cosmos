# CP-35 群治理（禁言/移交/解散/历史可见范围/审核容量门）— 首个工程增量

- 日期：2026-09-13；实施：后台实现 agent（SocialService 域 + V50），主线程整合回归
- 愿景：V12（群是慢社交的容器，治理规则明确且 fail-closed）、V13

## 交付内容（语义选型均在代码注释与接口 javadoc 写明）

1. **禁言**：群主（本批主持人=OWNER，不引入无生命周期的 MODERATOR 角色）可禁言成员，正整数分钟或 null（至手动解除）；被禁言者发消息 403 带剩余分钟或「需群主手动解除」，消息不落库（测试断言行数不变）；期满惰性自愈（发送时判定，注入 Clock，无后台任务）；不能禁言群主/自己；非主持人 403、非成员 404。
2. **移交**：仅群主；@Transactional 三条条件 UPDATE（新群主升格并顺手解禁 → 旧群主降 MEMBER → 群表换 owner）任一竞态败即 CONFLICT 回滚——不可能双群主/无主群；移交非 ACTIVE 成员 400。
3. **解散**：仅群主；群置 DISSOLVED + 全部成员行（ACTIVE+PENDING）翻 REMOVED；此后 listGroups 剔除（双保险过滤），messages/members/reports/reviews 一切走 requireUsableGroup 的端点 409「群已解散」；消息行留审计但无任何可读路径。
4. **历史可见范围**：普通成员以 `member.joined_at` 为界仅见加入后的消息（SQL 级 `created_at >= joined_at`；joined_at 接受邀请时打点，复活行重打）；**群主不受限（选定规则，注释写明）**；joined_at 缺失回退邀请时刻（更严格 fail-closed）。既有缺陷修正：`member.createdAt` 语义是邀请时刻而非加入时刻，独立 joined_at 列修正之，否则晚接受邀请的人会看到入群前消息。
5. **审核容量门**：举报进 `tb_group_review_ledger`（PENDING→RESOLVED/DISMISSED，仅群主条件 UPDATE 裁决，同举报人同消息去重）；每群 PENDING 上限默认 20（`inner-cosmos.social.group-review-pending-capacity`，env 可调）；**超限显式拒绝 409 并如实写明当前上限、不落库**（不静默丢也不无界放行）；插入后同事务二次复核并发挤入即回滚；被禁言成员仍可举报（禁言封嘴不封安全阀）。

## 迁移与 H2 孪生

V50（群 status+CHECK、member 治理列、台账表；与并行批次 V51 无撞号）；schema.sql 内联新列/新表 + 幂等 ALTER（沿 tb_wake_intent 惯例）。时间源口径如实说明：mute/resolved_at 走注入 UTC Clock；joined_at 特意用与 message.created_at 同源同区的 LocalDateTime.now()（SQL 比较不跑区）。

## 测试证据

- 新增 `GroupGovernanceContractTest` 6/6（@SpringBootTest + MockMvc，真 H2 真 SQL，容量压到 2 验证超限拒绝）+ `SocialServiceImplTest` 扩 21 个 CP-35 单测（含 Clock 期满自愈）；
- agent 域内回归 78/78（ApplicationFlowTest 8、SocialConnection/GroupController 各 6、SocialServiceImplTest 48、ExitSilenceAndModeration 3、BlockConsistency 1）；
- 主线程全量整合回归：**backend 1813/1813（2 Docker-gated skips）**。

## 诚实边界

- 前端（web/）未接新治理端点（agent 文件域排除，登记后续）；
- MODERATOR 独立角色未引入（无任命/罢免生命周期会成为死词汇）；
- PG V50 未在本机跑真 Flyway（本机 flyway.enabled=false，H2/test 环境）；语法与 V49 对拍，真库验证属部署门禁。
