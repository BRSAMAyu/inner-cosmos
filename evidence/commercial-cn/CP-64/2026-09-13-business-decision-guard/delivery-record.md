# CP-64 商业决策护栏（检查点 43，后台 agent 交付）

## 交付物

### 1. `docs/commercialization/launch/business-decision.ledger.yml`

1 条 DRAFT（bd-2026q4-h1h3）：H1/H2/H3 三假设（蓝图 §2.1 原文表述）各带
statement / evidence_metrics（只引 CP-03 字典键；三假设合计覆盖 K1/K2/K3 全部三键）/
counterexamples: null / budget_impact: null；decision ∈ {FOCUS, SCALE, REPOSITION,
STOP, UNDECIDED}（当前 UNDECIDED）；status PENDING；头注明"**BUSINESS_VALIDATED 不
作为本台账的可写状态存在**"。

### 2. 契约 `BusinessDecisionContractTest` 2/2

字段/枚举校验；evidence_metrics 只认 CP-03 字典键；PENDING 时反例/预算必须 null
（没有证据就没有决策）；UNDECIDED 不得有 conclusion；书面决策必须有结论/复审日/
反例/预算；**BUSINESS_VALIDATED 只允许出现在注释（头注至少一次）、绝不作为字段值**
——蓝图 L909"商业不成立可关闭验证任务但 BUSINESS_VALIDATED 仍不得通过"的结构化：
agent 可以跑完验证，永远不能写商业成立。

## 回归

后端全量 **1672/1672 绿**，web 750/750。

## 诚实边界（operator 门禁）

真实留存/付费/退款/渠道/安全容量数据（依赖 CP-47/55/56/59 真实交易与队列）、
创始人+独立顾问的决策判断、书面决策回填——全部 operator 事实。
