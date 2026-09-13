# CP-33 收件方读回执选择 + 跨时区/到期未到负测 — 首个工程增量

- 日期：2026-09-13；实施：后台实现 agent（慢信实体/服务/控制器 + LettersInbox 域），主线程补跨界缺口（前端接线、导出掩码、详情折叠）并整合回归
- 愿景：V05（收件方的已读信息是收件方的隐私）、V13

## 交付内容（agent O）

### 1. 回执偏好（绿地，全仓此前无 readReceipt）

- V48 迁移：`tb_slow_letter.receipt_policy VARCHAR(16) NOT NULL DEFAULT 'NEVER'` + schema.sql H2 孪生 + 幂等 ALTER；
- `PATCH /api/letters/{id}/receipt-policy`：仅收件人可改（大小写归一化；寄件人 403、第三方 401、非法值 400）；
- **判定时机**：视图构建时按当前策略评估（`applySenderReceiptMask` 单一权威在 service）——切换即时生效，readAt 永不伪造；
- **寄件人视角**（outbox/GET /{id}/thread 己发信）：NEVER（默认含旧行 NULL）→ READ 显示 DELIVERED、readAt 剥离、receiptPolicy 剥离（寄件人不可查询偏好）；ALWAYS → 如实 READ+readAt；REPLIED 不隐藏（回信本身即披露）但 NEVER 下不披露已读时刻；
- **收件人视角**：inbox/GET /{id} 永远见真实状态。

### 2. 未到送达不可读 + 跨时区口径

- 收件人 GET /{id} 在 DRAFT/SENT/FLYING → 400 LETTER_STATE_INVALID；thread 视图整体剔除未达信；READ 转换仅 DELIVERED→READ 且 DELIVERED 只能由调度器推进；
- 送达判定全链路 UTC 口径（persisted UTC LocalDateTime vs job UTC now），测试机 JVM 为 Asia/Shanghai 下仍稳定。

### 3. 前端

`LettersInbox.tsx` 收件箱回执开关（可选 prop 惯例，未传不渲染）+ 7 个 vitest；文案诚实（关=「对方不会收到已读回执」明示）。

## 主线程补齐的跨界缺口（agent O 如实上报后修复）

1. **前端接线**：api.ts `setLetterReceiptPolicy`（PATCH）+ `useConnectionsAndLetters.setReceiptPolicy/isReceiptPolicyBusy`（useBusyKeys 按信 id；翻转后三投影整体刷新保持掩码一致）+ AuroraApp 传参——开关在真实 App 点亮；
2. **导出绕过掩码（真实隐私缺陷）**：`UserDataExportService` 导出 slowLettersSent 原始实体 + `originalStatus`，泄露 READ 态与回执偏好——`applySenderReceiptMask(record, letter)` 落进导出（NEVER→READ 显 DELIVERED、readAt 置空、receiptPolicy 删除、DECLINED/BLOCKED→CLOSED、originalStatus 字段废除，掩码后的 status 是包内唯一 status）；`ExportSenderReceiptMaskTest` 5/5 + 导出导入往返回归 2/2；
3. **寄件人详情不折叠（一致性缺口）**：GET /{id} 寄件人视图对 DECLINED/BLOCKED 不折叠（outbox 折叠 CLOSED，详情泄露婉拒/屏蔽区别）——折叠收进 `applySenderReceiptMask` 统一助手（幂等；outbox VO 不重复掩码既有注释不变）；补 `senderDetailFoldsDeclinedAndBlockedToClosed`（收件人视图保持真实态）；
4. **前端 outbox 投影错配（既有前后端类型漂移，agent O 发现后主线程修复）**：`GET /api/letters/outbox` 实际返回脱敏 VO（`senderStatus`、正文不回显）而 api.ts 标成 `SlowLetter[]`、组件按 `letter.status/letterBody` 消费——drafts 恒空、仪式条 stage 恒 0。修复：VO 为 DRAFT 行附 `letterBody`（寄件人自己的草稿面，无隐私问题）；api.ts 定义 `SlowLetterOutboxRow`；hook 状态换型且动作后改 `refreshLetters()`（不再把实体行塞进投影列表）；LettersInbox outbox/drafts 渲染全面切 `senderStatus`（CLOSED 折叠标签、归档集合改 READ/REPLIED/CLOSED、已寄信显示 statusExplanation 而非正文）；`onActOnLetter` 放宽为按 `{id}`。测试夹具同步换投影形状，hook 动作测试改为断言「转移调用 + 刷新后权威态」。

## 测试证据

- agent 新增：SlowLetterReceiptPolicyServiceImplTest 17（+1 主线程折叠 = 18）、LetterReceiptPolicyControllerTest 6、LettersInbox 7 vitest；
- 慢信全套回归 95/95；主线程新增 ExportSenderReceiptMaskTest 5/5；
- 主线程全量回归：**backend 1767/1767（2 Docker-gated skips）+ web 766/766 + tsc clean**；
- 整合时修复：PostgresFlywayBaselineTest 迁移计数 47→49、v20 链 28→30（V48/V49 落地后的基线更新）；RenewalReminderContractTest 固定基号跨 JVM 运行残留行导致幂等防重复误杀新 fixture（改 run-unique 基号，注释写明根因）。

## 诚实边界

- 全局回执偏好入口未做（按信粒度已闭环；全局入口需 user_profile 改动，登记后续）；
- 服务端 vendor push 推送文案的回执语义未涉（与 CP-26 同一登记项）。
