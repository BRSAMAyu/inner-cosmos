# CP-48 客服、退款与用户沟通 — 值班/升级运行手册骨架

本目录是 CP-48 的结构化落盘：值班台账、升级路径、工单分类、演练矩阵四份 YAML，
外加一个强制结构契约的测试（`src/test/java/com/innercosmos/operations/OnCallRunbookContractTest.java`）。

## 文件

| 文件 | 内容 | 硬契约 |
|------|------|--------|
| `oncall-rotation.yml` | 值班台账（危机/一般两套时段） | CRISIS 与 GENERAL 必须并存且分开；GENERAL 必须公示真实服务时间；CRISIS 必须挂安全资源入口；角色必须可被升级路径引用 |
| `escalation-paths.yml` | 按严重级的升级路径 | 每级有 ack/update 时限；阶梯 after_minutes 严格递增；阶梯角色必须在值班台账可排班 |
| `ticket-categories.yml` | 工单分类（账单/退款/权利/封禁申诉/安全） | 每类挂一个已定义的严重级；默认 MINIMAL_METADATA；AUTHORIZED_SENSITIVE 必须给出授权程序 |
| `drill-scenarios.yml` | 演练矩阵（错误收费/扣款未解锁/退订误解/删除卡住/误封/骚扰/停服） | covers 必须引用真实工单分类（或保留字 platform_outage）；owner 必须是可排班角色；last_drilled 只能由 operator 回填 |

## 谁能填什么（诚实边界）

- **coding agent / 结构维护**：角色、时段、时限、分类、演练脚本、契约测试。可以把
  `status` 从 `PENDING` 推进到 `IN_PROGRESS`（表示结构已在建设），仅此而已。
- **operator / 真实运营**：真实姓名与联系方式在外部排班系统落位后，把值班 slot 的
  `status` 推进并在 `evidence/commercial-cn/CP-48/` 回填排班证据；演练跑完后回填
  `last_drilled` 与演练证据。**不存在 FILLED/PASS 之类的伪就位状态**——结构与商店提审
  清单（CP-43/44）同一纪律：批准/就位只能来自外部真实凭证，不能来自仓库里的字段翻转。

## 与其他工作包的挂钩

- **CP-20 危机干预**：`SEV1-safety` 与 `drill_safety_night`（月度夜班演练）联动危机台账。
- **CP-36 内容安全**：`harassment_report` 的授权调阅程序走内容安全分层队列。
- **CP-15/CP-50A 删除权利**：`drill_deletion_stuck` 必须核对独立撤回日志已确认水位，
  水位未知不开放相关资产。
- **CP-47 对账**：`SEV2-payment` 的 on_timeout 内置“DISPUTED 订单先暂停该渠道收款”。
- **CP-50B 停服**：`drill_service_outage` 是 CP-50B 真实故障演练的客服侧半场。

## 过载预案（蓝图恢复规则的结构化表述）

排队过载时，顺序是：先限量获客（暂停增长投放）→ 开通人工补偿通道 → 绝不承诺
不存在的 24 小时人工服务。危机通道与一般客服互不顶替：危机时段短缺只能补危机值守，
不允许拿一般客服时段充数。
