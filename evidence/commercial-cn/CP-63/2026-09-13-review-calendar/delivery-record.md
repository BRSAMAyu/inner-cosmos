# CP-63 复核日历 + 证据有效期引擎（检查点 43，后台 agent 交付）

## 交付物

### 1. `docs/commercialization/security/review-calendar.ledger.yml`

13 条四类齐全：QUARTERLY_INTERNAL_AUDIT×4 / MODEL_CHANGE_CHECK×4 / REGULATORY_ANNUAL×3 /
INCIDENT_TRIGGERED×2。字段 review_id/kind/scope/owner/effective_from/last_verified_at/
revalidate_due_at/status/receipt/evidence_path——last_verified_at、receipt、evidence_path
全 null（独立核验事实只能由 operator 以外部回执回填）。

**63A/63B 分离**（蓝图 L328）：7 条 63A 条目只登记 S3 首发基线 scope；6 条 63B 条目
scope 显式覆盖 CP-57/58/59/60 新增暴露面且全部 scope 描述唯一——63B 不得用 63A 覆盖
新增暴露面。头注三条诚实规则：无 VERIFIED 可写状态 / 超期不得沿用旧 PASS / 63A·63B
互不覆盖。

### 2. 有效期引擎（`ReviewCalendarContractTest.effectiveStatus(entry, asOf)`，asOf 注入不读钟）

| last_verified_at | revalidate_due_at | asOf vs due | 结果 |
|---|---|---|---|
| null | 任意 | 任意 | 条目 status（无旧 PASS 可沿用，不标 EXPIRED） |
| 非空 | null | 任意 | 条目 status（未登记有效期，不臆造过期） |
| 非空 | 非空 | asOf ≤ due | 条目 status（有效期内） |
| 非空 | 非空 | asOf > due（含次日边界） | **EXPIRED** |

蓝图 L901"超过法定或内部有效期不能自动沿用旧 PASS"的机械化；EXPIRED 阻断契约：
远期评估输出只能是自身 status 或 EXPIRED，绝无任何 PASS 形态；台账原文任何字段值
位置无 PASS 形态状态。agent 自述其 scope 缩写（缺 CP- 前缀）被自己的 63B 断言拦下
后修正——契约被证明有效。

### 3. 契约 4/4（经与 Maven 同源 classpath 真实执行）

结构 / 判定表（三种主情形+当日/次日边界+无期限）/ 63A·63B scope 分离 / EXPIRED 阻断。

## 回归

后端全量 **1672/1672 绿**（集成后 mvnw 复核），web 750/750。

## 诚实边界（operator 门禁）

独立渗透/抓包/红队执行与回执、整改复验——外部专业方；日历与引擎是结构与机器
校验面，不是"已复核"的证明。
