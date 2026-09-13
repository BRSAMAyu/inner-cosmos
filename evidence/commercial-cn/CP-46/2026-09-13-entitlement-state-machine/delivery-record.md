# CP-46 统一服务端权益状态机（检查点 33）

蓝图 CP-46（订阅、IAP 与透明配额）的核心可编码件：统一服务端权益状态机与渠道原始
状态的分离，落在 V44 迁移 + 服务 + 契约测试上。建立在检查点 32 的渠道事件面与
检查点 31 的账本内核之上。

## 交付物

### 1. 表结构（V44 迁移，Postgres 实机验证 + H2 孪生）

- **`tb_entitlement`**：每 (user, product) 一行跨渠道统一权益；state ∈
  {PENDING_PAYMENT, TRIAL, ACTIVE, GRACE_PERIOD, CANCELLED, EXPIRED, REVOKED}；
  period_start/end（UTC）、auto_renew、cancel_at_period_end；UNIQUE(user_id, product_id)
- **`tb_entitlement_event`**：转换审计 + **通知去重**（channel_notification_id 唯一——
  重复渠道通知 ack 既有行，绝不二次转换；并发插入竞态由唯一约束兜底）

### 2. `EntitlementStateService(+Impl)` 状态机

- **支付驱动**：`onPaymentSucceeded` 幂等激活/续期——续期从 max(period_end, occurredAt)
  起算（**已付时间不重叠**）；试用/待定转换从当刻起；EXPIRED/REVOKED 上新支付 =
  重新购买，全新周期；`onRefundSucceeded` → **REVOKED 粘性撤销**（退款后撤销）
- **渠道订阅通知驱动**：PURCHASE_PENDING（零宽占位周期，未付分文不欠）→ TRIAL（14 天
  沙箱契约）→ RENEWED → GRACE_ENTERED → CANCELLED（**保留到周期末**，不收回已付时间，
  auto_renew 关）→ EXPIRED → RESTORED（恢复购买：有效周期复活；死周期恢复不送时间；
  **退款撤销的行拒绝恢复**——CONFLICT，只有新支付能复活）；未知/未来渠道状态 fail-closed
  拒绝（无转换无审计行）；对不存在权益的 RENEWED/GRACE/CANCEL/EXPIRED/RESTORE 拒绝而非
  凭空发明历史
- **时区/月末**：续期月历在 Asia/Shanghai 上做——1 月 31 日周期续到 2 月 28 日，
  绝不漂到 3 月（契约测试逐日断言）
- **用户取消**：`cancelByUser` 每权益一次幂等（user:cancel:{id} 去重键），保留已付周期，
  取消入口永不缺席
- **惰性到期**：snapshot() 把过期未续期的非撤销行转为 EXPIRED，绝不把陈旧周期继续
  报成有权益
- **`EntitlementGates` 目录**：`PAID_GATED`（AI 深度预算/记忆窗口等容量舒适项）与
  `NEVER_PAID_GATED`（危机拦截/边界控制/画像纠正/记忆搁置删除/导出/删除/注销/基础工单）
  两个集合，契约测试强制不相交——**安全/纠正/导出删除永不付费解锁**是结构保证不是口号
- **用户面**：`GET /api/me/entitlements`（跨端统一视图，period_end 即配额窗口重置时间）、
  `POST /api/me/entitlements/{productId}/cancel`；控制器只消费服务端事实，
  从不接受客户端宣告的支付成功

### 3. 契约测试 `EntitlementStateMachineContractTest` 8/8

激活+堆叠续期（Jan 1→Feb 1→Mar 1）；月末钳制（Jan 31→Feb 28→Mar 28）；支付与订阅
通知双重去重（audit 恰 1 行、周期不二次延长）；退款粘性撤销+恢复拒绝+重购买复活+
无权益退款不发明行；渠道全生命周期七态归一；恢复购买（活周期 ACTIVE/死周期 EXPIRED）；
用户取消保留已付时间+惰性到期+NOT_FOUND；永不付费解锁目录不相交

## 实机验证

- Postgres 基线：**44 迁移 / 103 表 / 96 身份列 / v20 链 25**（PostgresFlywayBaselineTest
  4/4，PostgresApplicationSmokeTest 1/1 实跑）
- 后端 **1620/1620 绿**（+8），2 个 Docker 门控跳过；Web **742/742 绿**，tsc 干净

## 诚实边界（operator 人工门禁）

- IAP 渠道（App Store / 国内安卓商店）的服务器通知接线需要真实渠道账号与合同；
  真实渠道试用/宽限期长度来自渠道 payload，当前 14 天试用是文档化的沙箱契约
- 按能力计量的配额余量展示（剩余次数）与 CP-17 配额体系合流、续费前显著提醒的
  排程通知为后续批次
