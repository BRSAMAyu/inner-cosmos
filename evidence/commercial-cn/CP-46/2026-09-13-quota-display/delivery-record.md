# CP-46 透明配额余量展示（检查点 37）

蓝图要求：配额按已批准能力计量、**显示剩余与重置时间**。本增量把检查点 27 的
ProviderSpendGuard 日预算与检查点 33 的权益周期窗口合成用户可见的配额面。

## 交付物

### 1. `ProviderSpendGuard.DailyQuota` 视图

`dailyQuota(userId)`：当日已用次数/次数上限、已用 tokens/token 上限、剩余次数、
**重置时刻**（guard 自身时钟区的下一个零点——与计数器翻日同一锚点，不会出现
"显示的重置时刻"与"实际翻日时刻"两套口径）。

### 2. `GET /api/me/quotas`（会话认证，QuotaController）

- `quotas[]`：`ai.deep_daily_budget`，basis=DAILY，used/limit/remaining/usedTokens/
  limitTokens/resetsAt
- `subscriptionWindows[]`：付费能力窗口（memory.extended_horizon 等），basis=
  SUBSCRIPTION_PERIOD，state/resetsAt(=权益周期末)/autoRenew/cancelAtPeriodEnd
- `neverPayGated[]`：EntitlementGates.NEVER_PAID_GATED 原样返回——作为**长期承诺**
  展示（安全/纠正/导出删除永不付费解锁），不是可消耗的配额行

### 3. 契约测试 `QuotaDisplayContractTest` 2/2

- 日配额记账准确（record 后 used/remaining 正确）且 resetsAt 精确等于 guard 时区
  次日零点（固定时钟断言到分钟）
- 端点（MockHttpSession + 真实用户行过安全层）：DAILY 窗口字段齐全、订阅窗口
  ACTIVE 且带 resetsAt、neverPayGated 数组在响应中、两目录不相交

## 回归

后端 **1631/1631 绿**（+2），2 个 Docker 门控跳过。

## 诚实边界

- 计数器是 per-pod 内存口径（多 pod 全 fleet 上限是 CP-37/38 的 Redis 版本，注释
  已声明该语义为下近似）；前端 UI 消费该端点为下一前端批次
