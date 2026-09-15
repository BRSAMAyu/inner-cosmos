# CP-45 价格版本表 + 订单过期策略 — 第三增量（§2-21，支付域最后一块 codable 核心）

- 日期：2026-09-15；实施：后台 agent（payments 域 + V55）

## 交付

1. **不可变价格版本**（`tb_price_version`，V55 + H2 孪生）：读取=当前 ACTIVE 版本；改价=单事务 retire 全部 ACTIVE→插入新 ACTIVE（version=max+1），旧行金额永不原地更新；并发双改价撞 `(product_id, version)` 唯一键显式抛给操作者不静默覆盖；PG partial unique index（单 ACTIVE 硬保证），H2 孪生由服务层事务保证（差异如实注释）；首个订单惰性播种 version 1（沙箱 2500 分，可审计版本行）；下单固化 priceVersionId+版本金额+币种，回调金额校验按订单固化值。
2. **订单过期**（惰性+扫描双路径，幂等）：创建写 expiresAt（默认 PT2H 可配，存量行 NULL 不过期）；find() 惰性条件翻转 CREATED→EXPIRED（终态）；PaymentOrderExpiryJob 每 5 分钟批量扫描（runtime-role 模式）；**晚到回调如实拒**：签名完全有效但订单已 EXPIRED → REJECTED_EXPIRED（HTTP 410 FAIL ack，渠道停止重试）、不记成功、不授权益、不入净额，但记 EXPIRED_ORDER_CALLBACK/REJECTED ledger 行对账可见；重复回调 provider_event_id 幂等。
3. 净额口径收紧：orderNetCents 只计 PAYMENT_SUCCEEDED/REFUND_SUCCEEDED，信息性事件永不入净额。
4. 改价只暴露服务方法（changePrice）不加匿名端点——真实定价与持牌渠道属 operator 门禁。

## 测试

PriceVersionContractTest 3/3 + OrderExpiryContractTest 5/5（不可变追溯/过期拒收 fail-closed+幂等+净额不动/未过期正常链/2 线程并发改价各自锁版本）+ payments 域回归 39/39；基线计数更新（迁移 55、表 110、identity 103、链 36）。

## 诚实边界

PG 基线测试本机无 Docker 未实跑（计数按 V55 结构更新，待 Docker 环境）；CLOSED 状态既有语义未动；发票流（发票抬头/开票）属持牌渠道对接 operator 门禁。
