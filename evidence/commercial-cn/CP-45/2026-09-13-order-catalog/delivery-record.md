# CP-45 服务端订单目录与金额校验 + 订单→权益联动（检查点 34）

关闭检查点 32 沙箱适配层留下的两个真实缺口：

1. **金额无锚**：适配层验证签名但不验证金额——一个签名正确的任意金额回调会被
   当作事实入账。蓝图要求"服务端校验订单/金额"，现在订单目录是唯一权威。
2. **扣款未解锁**：渠道事件与权益状态机（检查点 33）之间没有接线。

## 交付物

### 1. 订单目录（V45 迁移，Postgres 实机验证 + H2 孪生）

- **`tb_payment_order`**：order_id 唯一、user_id、product_id、channel、
  expected_amount_cents（正数约束）、currency CNY、status CREATED/CLOSED。
  **支付状态不在这里**——只在追加式账本里，订单行是回调校验的权威锚点
- **`PaymentOrderService`**：订单只能由服务端创建（`POST /api/payments/orders`，
  会话认证），目录价单一来源（pro.monthly ¥25.00 沙箱定价）；订单号
  `IC{UTC时间戳}-{8hex}`；未知商品 BAD_REQUEST

### 2. ingest 管线加严（decode → 商户 → 验签 → 状态映射 → **订单校验** → 入账 → 权益）

- **未知订单 / 渠道不符**（支付宝回调认领微信订单）→ REJECTED_ORDER（404），
  零入账零授予——没有可对账的锚点就拒绝，绝不发明
- **金额漂移**（签名正确但金额 ≠ 订单期望）→ REJECTED_AMOUNT（422）+
  **账本记 DISPUTED 事实**（渠道确实动钱了——漂移可见化，绝不静默吸收，也绝不
  确认为干净支付）
- **超额退款**（退款 > 订单累计净收款）→ REJECTED_AMOUNT + DISPUTED
- 新增 `PaymentLedgerService.recordDisputed`（同 provider_event_id 幂等）

### 3. 订单→权益联动（CP-45/46 闭环）

- 支付成功 → `order.userId` 自动获得 `order.productId` 权益（ACTIVE，
  堆叠续期/月历钳制全继承状态机语义）——扣款未解锁在服务端闭合
- **全额退款（净收款归零）→ REVOKED 撤销**；部分退款保留权益（按比例降级是
  明示的产品决策，不做静默默认）
- 重复回调端到端幂等（账本唯一键 + 权益审计唯一键双保险）

### 4. 契约测试

- `ChannelCallbackSandboxContractTest` 13→**15/15**：新增订单目录权威矩阵
  （未知订单拒/金额漂移 DISPUTED 无授予/渠道错配拒/超额退款 DISPUTED 权益不撤）
  与全额退款撤销/部分退款保留
- 已有正路径全部改为先建订单再回调（金额来自订单），适配层测试同步反映真实管线

## 实机验证

- Postgres 基线：**45 迁移 / 104 表 / 97 身份列 / v20 链 26**
  （PostgresFlywayBaselineTest 4/4 + PostgresApplicationSmokeTest 1/1 实跑）
- 后端 **1622/1622 绿**（+2），2 个 Docker 门控跳过；Web **742/742 绿**，tsc 干净

## 诚实边界（operator 人工门禁）

- 真实渠道下单/预创建（微信下单 API、支付宝预创建）需要持牌服务商合同与商户
  证书——本订单事实是它们的锚点，不是替代
- 价格版本表（多币种/分层定价/调价历史）、订单过期与关闭策略、发票流为后续批次
