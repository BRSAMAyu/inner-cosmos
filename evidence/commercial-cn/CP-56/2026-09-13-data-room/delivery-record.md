# CP-56 投资人/导师数据室骨架（检查点 40，后台 agent 交付）

## 交付物（`docs/commercialization/investors/`）

### 1. `data-room.yml` —— 蓝图 §6.4 九层数据室骨架，30 个条目

公司/股权/IP×3、用户研究×4（含失败样本附录与合成演示账户条目）、产品与竞品证据×3、
队列留存×3、订单与退款×3、单位经济与现金计划×3、合规与供应商合同×3、架构/安全/
事故×3、团队能力与资金里程碑×5（含融资用途、下一轮里程碑、未关闭事项登记）。
每项 item_id/layer/title/status/evidence/source_or_pending。

**7 个数字类项**（研究分母、bottom-up 可达市场、D30 留存、K1/K2、退款率、每任务
成本、贡献利润）额外带 definition/date/denominator/verdict/value——全部
`verdict: PENDING_VALIDATION` 且 `value/date/denominator: null`：**市场输入未取得
就标待验证，不填精确 TAM**（蓝图 CP-56 验收逐字）。

### 2. `README.md`

九层授权矩阵（谁可读哪层）、**P0 禁入规则**（投资人对 P0 无读取权，仅授权脱敏或
明确合成演示账户）、数字四要素纪律（定义/日期/分母/来源）、"材料完成≠融资成功"、
coding agent 与 operator 的填写分工。

### 3. 契约 `DataRoomContractTest` 5/5（真实执行绿）

九层齐全非空 + item_id 唯一；status ∈ {PENDING, IN_PROGRESS}（**无完成态**）且
evidence 必须为 null；数字项 verdict 契约（VERIFIED 需四要素齐全，
PENDING_VALIDATION 时 value 必须 null，三层各至少一个数字项）；title 禁含
"对话原文/倾诉内容/原始对话/原始倾诉"（P0 禁入的文本面）；团队层含下一轮里程碑
与融资用途。

## 回归

后端 **1645/1645 绿**（本包 +5，含 CP-52 +2）；Web 未触及（沿用 750/750）。

## 诚实边界（operator 门禁）

真实数字回填（带分母与来源）、数据室分层授权系统接线、路演——operator 事实；
本骨架保证的是"没有溯源就没有数字"。
