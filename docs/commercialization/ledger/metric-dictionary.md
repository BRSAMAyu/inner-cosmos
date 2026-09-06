# CP-03 指标数据字典（commercial-cn）

> 权威定义在蓝图 §4.1；本字典把每项指标落到**事件、SQL/计算契约、owner、成熟与回填规则**。
> 事件表：`tb_commercial_metric_event`（UTC 存储 + Asia/Shanghai 周锚 + 自然键幂等 + props 白名单）。
> 查询实现：`CommercialMetricQueryServiceImpl`；测试基线：`CommercialMetricFunnelTest`（人工可核对合成样本）。

## 0. 全局不变量（写入时强制）

| 不变量 | 实现位置 |
|---|---|
| 仅服务端确认事实入账 | 事件只在原子状态迁移成功后发射（FINISHED/SENT/insert 成功） |
| 测试账号隔离 | `MetricEventServiceImpl.record`：仅 `accountKind=HUMAN ∧ status=ACTIVE` 入账 |
| UTC 存储 + 上海周锚 | `occurred_at_utc`(UTC) + `anchor_week`/`anchor_day`(Asia/Shanghai ISO) |
| 幂等去重 | `event_key = metric|user|context|occurredAtMillis` 唯一约束 |
| 无 P0 正文 | `MetricCode.allowedProps` 白名单 + 值标量截断 200 字符，违例抛异常 |
| 分析授权 | 用户级 `tb_analysis_consent`，DECLINED 则用户路径事件不入账 |
| 注销语义 | 行全部删除 + `tb_commercial_metric_rollup` 聚合计数保历史分母；K2 原队列保留（`K2_ACTIVATION` 伪指标） |

## 1. 事件注册表

| metric_code | pathway | 发射点（服务端） | context | 关键 props（白名单） |
|---|---|---|---|---|
| PRIVATE_DIALOG_COMPLETED | PRIVATE | `CommercialMetricListener` ← DialogFinishedEvent（原子 FINISHED） | DIALOG_SESSION | sessionId, mode |
| VALUE_CONFIRMED_PRIVATE | PRIVATE | `ValueFeedbackController`（用户显式确认） | — | scope |
| CONNECTED_REAL_SEND | CONNECTED | `SlowLetterServiceImpl.transition` SENT 成功分支 | LETTER_THREAD | threadId, toUserId, toCapsuleId |
| VALUE_CONFIRMED_CONNECTED | CONNECTED | 同上 controller（CONNECTION scope） | LETTER_THREAD | threadId |
| SAFETY_INCIDENT | PLATFORM | `SafetyServiceImpl.record`（事件入库后） | DIALOG_SESSION | safetyEventId, triggerScene；dimA=风险等级, dimB=风险类型 |
| RIGHTS_ACTION_COMPLETED | PLATFORM | `DataRetractionReceiptServiceImpl.record` | subjectType | receiptId, subjectType, affectedCount；dimA=动作 |
| PAYMENT_CAPTURED | PLATFORM | CP-45 接入（契约就绪） | ORDER | orderId, channel, amountCents, currency |
| REFUND_SETTLED | PLATFORM | CP-45 接入／回填 | ORDER | orderId, channel, amountCents, currency |

## 2. 指标计算契约

### K1 每周确认价值用户数／率
- **私密路径**（`k1Weekly(week).privateNumerator/privateDenominator`）
  - 分母 = 锚周内 ≥1 次 PRIVATE_DIALOG_COMPLETED 的去重用户 + rollup(同指标@周)
  - 分子 = 同周有 VALUE_CONFIRMED_PRIVATE **且** 私密核心使用落在 ≥2 个不同 anchor_day
  - “另一天”限定同周；非回应不当满意（未确认为分母而非负例）
  - 成熟：锚周结束后即成熟（私密值不迟到）
- **连接路径**（`connectedQualifiedExchanges/confirmedExchanges`）
  - 合格往来 = 同一 LETTER_THREAD 锚周内 ≥2 个不同真人发送者
  - 确认 = 双方在锚周起点 +14 天内对该 thread 各有 VALUE_CONFIRMED_CONNECTED
  - 成熟：锚周结束 +14 天；未成熟周只报暂定值
- **owner**：数据工程＋产品；每周产品会审。

### K2 D30 窗口价值留存率
- 激活 t0 = 用户首个 PRIVATE_DIALOG_COMPLETED 的 anchor_day；激活 = 完成真实对话并主动结束（现状即 FINISHED 语义；“查看/处理结果含明确不保存”由 J01 收口后补充事件）。
- 窗口 = t0+28..t0+34 内存在核心价值行为（PRIVATE_DIALOG_COMPLETED 或 VALUE_CONFIRMED_PRIVATE）
- 分母 = 观察窗已成熟的全部激活用户（按激活周分队列）；删号用户经 `K2_ACTIVATION` rollup 永久保留在原队列
- 成熟：t0+34 已过；产品每两周决定扩大/重做
- **owner**：数据工程＋产品。

### K3 每月贡献利润（**待 CP-45**）
- 契约：净收入 = Σ PAYMENT_CAPTURED − Σ REFUND_SETTLED（不含税、扣渠道费）− 全部用户模型/语音/可变云/审核客服直接成本；流水/订单/权益/账单对账
- 事件与幂等管线已就绪（含迟到回填：锚由业务时间而非入库时间决定）；真实发射器与成本侧账本随 CP-45/47 落地
- **owner**：财务＋数据工程。

### G-SAFE 安全与自主性（守门，不可抵消）
- `gSafeWeekly(week)`：SAFETY_INCIDENT 按 dimA(等级)/dimB(类型) 计数；每千核心会话分母 = 该周 PRIVATE_DIALOG_COMPLETED 行数 + rollup
- 高危（数据越权/生命健康）→ 立即停功能，不与增长抵消（运营规则，不在本表实现）
- **owner**：安全运营。

### G-TRUST 权利与交易可靠性（守门，不可抵消）
- `gTrustWeekly(week)`：RIGHTS_ACTION_COMPLETED 按 dimA(动作) 计数 + affectedCount 总和；注销用户计入 `ANONYMIZED` 桶但 affected 总数保留
- 超期/重复扣款/删除失败 SLA 判定随 CP-15（24h/7d/30d 承诺窗）与 CP-45（支付对账）接入
- **owner**：数据工程＋客服。

## 3. 版本、回填与快照纪律

- 字典版本 v1（2026-09-06，对应蓝图 §4.1 原文）。改口径必须升版本并在台账登记，不追结果移动门槛。
- 迟到事件：锚字段由业务发生时间计算，入库时间不影响归属周。
- 回填：REFUND_SETTLED 等更正事件按新 event_key 追加，不 UPDATE 旧行；`ingest_source=BACKFILL` 通道保留给 CP-45 批量重建。
- 历史稳定性：注销走 rollup；对外只比较成熟周，修订保留快照（快照机制随 CP-40 物化）。
- 已知边界：K1 连接路径在“一方注销”后按存活者重算合格往来（历史逐 thread 精确快照随 CP-40 落地）；K2 的“查看/处理结果”激活变体待 J01 权限问题定稿。
