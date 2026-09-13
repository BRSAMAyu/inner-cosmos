# CP-56 投资人/导师数据室

本目录是 CP-56（投资人/导师数据室与商业叙事）的结构化骨架，对应商业化执行蓝图
§6.4「投资人应看到的公司，而不是项目演示」与 CP-56 工作包。契约测试
`src/test/java/com/innercosmos/investors/DataRoomContractTest.java` 锁定结构诚实性。

## 文件

| 文件 | 内容 | 关键契约 |
|------|------|----------|
| `data-room.yml` | 九层数据室骨架（30 项文档/数字条目） | status ∈ {PENDING, IN_PROGRESS}，**无 DONE/READY、无任何"融资成功"态**；evidence 在 status 推进前必须为 null；数字项 verdict=VERIFIED 需 definition/date/denominator/来源四项齐备，PENDING_VALIDATION 时 value 必须为 null |

## 九个分层与授权矩阵

| 分层（layer id） | 内容 | 谁可读 |
|------------------|------|--------|
| `corporate-equity-ip` | 公司/股权/IP | 仅签署 NDA 的投资人；部分原件现场查看不带走 |
| `user-research` | 用户研究（脱敏层） | NDA 投资人与授权导师/评委；只见授权脱敏摘要 |
| `product-competitive-evidence` | 产品与竞品证据 | 签署基础保密的导师/评委/投资人 |
| `cohort-retention` | 队列留存 | NDA 投资人；分母、日期、失访随附 |
| `orders-refunds` | 订单与退款 | 仅 NDA 投资人；由财务自渠道对账单回填 |
| `unit-economics-cash-plan` | 单位经济与现金计划 | 仅 NDA 投资人 |
| `compliance-vendor-contracts` | 合规与供应商合同 | NDA 投资人；数据室只索引台账状态 |
| `architecture-security-incidents` | 架构/安全/事故 | NDA 投资人；事故与未关闭门如实披露 |
| `team-funding-milestones` | 团队能力与资金里程碑（含融资用途、下一轮里程碑、未关闭事项） | NDA 投资人与授权导师 |

授权分层是硬边界：读者身份由 operator 登记（见 `synthetic-demo-accounts` 条目），
任何分层越权读取都不允许；本 README 不构成对任何特定读者的授权。

## P0 禁入规则

- **P0 私密对话原文与可重新识别的人格数据不进入任何分层**；投资人对 P0 无读取权，
  这是前提而非可谈判项。
- 对外演示与体验一律使用**明确标注合成的演示账户**或**授权脱敏材料**；
  导师、评委、投资人均同此规则。
- 用户研究只提供授权脱敏摘要；研究分母、日期、失访与失败样本随数据室如实披露，
  不用删失样本美化结果。

## 数字纪律

每个对外数字必须有**定义、日期、分母、来源**四要素（对应数字条目的
definition/date/denominator/source_or_pending）。市场输入未取得时 verdict 保持
`PENDING_VALIDATION` 且 value 为 null——**标"待验证"，不填精确 TAM/SAM**。
可核验证据附录按蓝图 §6.4 组织，市场模型是 bottom-up（目标细分 × 实际可达渠道 ×
验证转化），不以全部社交用户作 SAM。

## 材料完成 ≠ 融资成功

- 本目录**不存在**"融资成功""DONE"之类的终态：status 只有 PENDING / IN_PROGRESS，
  材料完成只是材料完成。
- **未获融资不等于产品失败**；路演结构参考投资机构公开框架，不构成融资保证。
- 发现材料错误时及时修订版本并通知已接收方（CP-56 恢复条款）。

## 谁能填什么

- **coding agent**：结构、字段、来源/待验证说明、契约测试；status 最多
  PENDING → IN_PROGRESS。
- **operator（创始人/财务/研究负责人/法务）**：真实证据指针（evidence）、数字的
  date/denominator/value、verdict 晋升为 VERIFIED、授权登记。没有外部材料，
  任何条目都不会出现证据或完成态——这既是纪律也是契约测试断言的事实。
