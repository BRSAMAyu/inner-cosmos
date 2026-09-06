# CP-03 指标、事件与数据字典 — 交付记录

- 日期：2026-09-06；阶段 S1；依赖 CP-02（判据已冻结部分）/CP-07（同意开关已预埋，完整契约随 CP-07）
- 愿景：V16、V17、V18
- current_sha：见台账登记（CP-01 提交 2f0b61e3 之后的工作区；本次随检查点提交）

## 交付物

1. **事件存储**（双端一致）：
   - PostgreSQL `V36__commercial_metric_events.sql`；H2 `schema.sql` 追加孪生表
   - `tb_commercial_metric_event`：UTC 时间 + 上海 ISO 周锚 + 自然键唯一（幂等）+ props 白名单 + dim_a/dim_b
   - `tb_analysis_consent`：用户级分析授权开关（默认 GRANTED v1；CP-07 替换为完整同意合同）
   - `tb_commercial_metric_rollup`：注销聚合回填（历史分母不缩水；K2 原队列保留）
2. **写入管线** `MetricEventServiceImpl`：
   - 测试账号隔离（仅 HUMAN+ACTIVE）；分析授权门；P0 props 白名单（违例抛错、值截断 200）
   - `anonymizeUser`：行删除 + 聚合回填 + K2_ACTIVATION 队列保留
3. **发射点（全部服务端确认事实）**：
   - `CommercialMetricListener` ← DialogFinishedEvent（原子 FINISHED，AFTER_COMMIT）
   - `SlowLetterServiceImpl` SENT 成功分支 → CONNECTED_REAL_SEND
   - `SafetyServiceImpl.record` → SAFETY_INCIDENT（等级/类型，无正文）
   - `DataRetractionReceiptServiceImpl.record` → RIGHTS_ACTION_COMPLETED
   - `ValueFeedbackController`（POST /api/metrics/value-feedback）→ 用户显式价值确认
4. **读模型** `CommercialMetricQueryServiceImpl`：K1 私密/连接、K2 队列、G-SAFE、G-TRUST 周报（成熟度标记）
5. **数据字典**：`docs/commercialization/ledger/metric-dictionary.md`（事件注册表 + 计算契约 + owner + 回填/快照纪律）
6. **验收测试**：`CommercialMetricFunnelTest`（3/3 绿）——人工可核对合成样本覆盖：

| 验收要求 | 测试证据 |
|---|---|
| 重复 | 同自然键二次写入返回 null、仅一行 |
| 离线/迟到 | 事件锚由业务时间计算（跨周边界用例显式断言） |
| 跨周 | 2026-09-06T16:05Z → 上海 2026-09-07 / 2026-W37 |
| 注销 | anonymizeUser 后 K1 分母、G-SAFE 核心会话、K2 队列规模不变；行全部离场；G-TRUST ANONYMIZED 桶 + affected 保留 |
| 退款 | PAYMENT_CAPTURED + REFUND_SETTLED 入账锚定业务周（真实发射器随 CP-45） |
| 未回应 | 有对话无确认的用户计入分母、不计入分子 |
| 测试账号隔离 | SANDBOX/DEMO/SYSTEM/不存在用户一律不入账 |
| 分析授权 | DECLINED 用户事件不入账 |
| 业务总账对齐分母 | coreSessions 与事件行数一致（合成样本 7 会话=7） |

- test_command：`./mvnw test -Dtest=CommercialMetricFunnelTest`（本次 3/3 通过；全量回归另见检查点提交）
- rollback：删除新增文件 + 回退三处挂钩（SlowLetter/Safety/Retraction 各一段）+ V36/schema 追加块；无数据迁移风险（新表可空置）
- 边界：K3 真实收入侧与成本账本、J01“查看/处理结果”激活变体、周快照物化分别随 CP-45/CP-47、CP-10、CP-40 落地（字典 §3 已登记）

## 状态

- status: IMPLEMENTED（合成样本验收通过；等待独立复核与后续包联动）
- next_action: CP-04 评测场景集冻结；CP-07 同意合同接管默认授权
