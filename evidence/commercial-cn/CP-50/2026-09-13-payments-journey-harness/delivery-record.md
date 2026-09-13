# CP-50B 支付/权益旅程压测 harness（检查点 38）

## 交付物

`scripts/loadtest/payments-journey.k6.js` —— CP-50B 的可编码前体，专测 CP-45/46
管线在负载下的行为：

- **旅程**：登录（CSRF-first）→ `POST /api/payments/orders` 服务端下单 →
  `POST /api/payments/callbacks/wechatpay` **实时 HMAC 签名**的沙箱回调（k6 crypto
  模块，时间戳取当前 epoch，签名覆盖 timestamp.body）→ `GET /api/me/entitlements`
  统一权益快照 → `GET /api/me/quotas` 透明配额（断言 resetsAt 在场）
- **诚实开关**（压测同时验证验证管线）：默认 `SANDBOX_CONFIGURED=false` 把未配置
  渠道的 fail-closed 拒收（401/403）计为正确；未配置渠道上出现任何 200 记入
  零容忍计数器 `payments_fail_closed_violations`（阈值 `count==0`）——负载下若
  验签/商户门失效会立刻炸红而不是静默变绿。operator 注入沙箱凭据后切
  `SANDBOX_CONFIGURED=true` 转正向验收口径
- **显式 SLI**：`payments_journey_failures` 计数器、`payments_callback_latency`
  （p95<2s 草案）、`http_req_failed<1%`
- 与既有 `aurora-journey.k6.js` 同纪律：真实 200 并发 SSE/故障注入/≥24h 浸泡是
  CP-50A/50B operator 门禁运行，本文件即那些运行执行的 harness

## 验证

- 两份 k6 脚本（新 payments-journey + 既有 aurora-journey）经 node 解析器语法
  校验通过（k6 未安装本机，实跑属 staging 门禁）
- README 补充用法与诚实开关说明

## 诚实边界

- 本机无 k6/无沙箱凭据——脚本执行与阈值标定发生在真实 staging（operator 门禁）；
  阈值是先验草案，冻结于首轮实测后（CP-04 纪律）
