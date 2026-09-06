# 商业台账使用说明（commercial-cn-ledger）

> 权威执行账本：[`commercial-cn-ledger.yml`](commercial-cn-ledger.yml)。
> 由蓝图 [CP-01](../01-中国大陆商业化执行蓝图.md#work-packages) 于 2026-09-06 建立，基线冻结于 `add4b241`。

## 状态机

工程包与子门使用：`NOT_STARTED / IN_PROGRESS / IMPLEMENTED / NOT_RUN / VERIFIED / FAILED / BLOCKED_EXTERNAL`。
合规程序附加：`DRAFT / SUBMITTED / REMEDIATION_REQUIRED / 程序完成`，不得与工程 PASS 混用。

## 更新规则

1. 每次执行 CP 包前，把该包 `status` 改为 `IN_PROGRESS`，并附 `current_sha`。
2. 证据落在 `evidence/commercial-cn/CP-xx/日期-候选版本/`，台账里登记 `evidence_dir` 已有默认值。
3. 交付记录必须包含蓝图第 8 节统一交付合同字段（test_command/numerator/denominator/rollback 等）。
4. 完成自查后置 `IMPLEMENTED`；只有可复现证据 + 独立复核才可置 `VERIFIED`。
5. 旧账（`docs/goal/complete-product-acceptance.yml`）永不改写内容；迁移去向只在本台账 `migration` 节维护。

## ADR 索引

既有 ADR（`docs/adr/`，历史有效）：

- 0002 PostgreSQL 为事实源 → 大陆环境由 CP-37 重验（托管 RDS + pgvector）
- 0003 ArchUnit 分层边界而非 Spring Modulith → 继续有效，CP-38 在其上分池

待写 ADR（按蓝图触发）：

| ADR | 触发包 | 决策内容 |
|---|---|---|
| ADR-CN-001 | CP-37 | 主云 ACK vs TKE PoC 结论与组件映射 |
| ADR-CN-002 | CP-17 | 国内模型网关 Provider 组合与路由策略 |
| ADR-CN-003 | CP-45/46 | 支付渠道组合（微信/支付宝/IAP）与账本设计 |
| ADR-CN-004 | CP-44 | HarmonyOS 原生渠道 go/no-go |
| ADR-CN-005 | CP-39 | 大陆事件传输（MQ 选型）与 SQS 路线解耦 |

## 证据索引约定

`evidence/commercial-cn/CP-xx/日期-主题/` 内固定放 `delivery-record.md`（合同字段），
测试输出、截图、签字扫描等以其本名存放。私密研究原始数据一律入受控境内存储，Git 只放脱敏摘要与内容哈希。
