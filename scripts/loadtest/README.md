# CP-50 压测 harness（可编码前体）

`aurora-journey.k6.js` 是 Aurora 付费旅程的可复现负载 harness：登录（CSRF-first）→ 建会话 → 发送 Aurora 消息（权威非流式端点）→ 拉取消息 → 结算沉淀 → 每迭代一次 SSE 探测。阈值草案与 CP-50 验收口径对齐：旅程失败计数（`aurora_journey_failures`）是显式 SLI，绝不静默变绿；`http_req_failed<1%`；轮次 p95<8s（按环境调优）。

## 本地 smoke（开发部署）

```bash
k6 run -e BASE_URL=http://localhost:8080 -e USERNAME=demo -e PASSWORD=demo123 \
      -e VUS=2 -e DURATION=30s scripts/loadtest/aurora-journey.k6.js
```

## 正式 CP-50A 运行（operator 门禁）

真实国内 staging、明确任务混合、200 并发 SSE 起点 / 2 倍峰值、≥24 小时浸泡、Provider 超时/DB/Redis/Pod/AZ 故障注入——见蓝图 §CP-50。本文件即那些运行执行的同一段 harness；结果（RPO/RTO、每旅程成功/中断/恢复、失败分母）记录进 `evidence/commercial-cn/CP-50/`。

## 诚实边界

- SSE 在本 harness 中是探测性的（每迭代一次、10s 超时）；真正的 200 并发长连 SSE 腿属于 staging 运行
- 单 Pod 内存预算护栏（CP-40 ProviderSpendGuard）在压测下可能触发——压测账号请调高 `inner-cosmos.ai.spend.daily-user-call-budget` 或用多账号
- 阈值是先验草案，冻结于 staging 首轮实测之后（CP-04 纪律）

## CP-50B 前体：支付/权益旅程 harness（`payments-journey.k6.js`）

CP-50B 的可编码前体：登录（CSRF-first）→ 服务端下单（`POST /api/payments/orders`）
→ 沙箱签名微信回调（`POST /api/payments/callbacks/wechatpay`，HMAC 由 k6 crypto
实时签名）→ 统一权益快照（`GET /api/me/entitlements`）→ 透明配额
（`GET /api/me/quotas`，断言 resetsAt 在场）。专测 CP-45/46 管线的负载面：匿名
回调 ingest、账本幂等、权益/配额读路径。

```bash
k6 run -e BASE_URL=http://localhost:8080 -e USERNAME=demo -e PASSWORD=demo123       -e VUS=2 -e DURATION=30s scripts/loadtest/payments-journey.k6.js
```

**诚实开关**：默认（`SANDBOX_CONFIGURED=false`）把未配置渠道的 fail-closed 拒收
（401/403）当作**正确结果**计数，并把未配置渠道上出现 200 记为零容忍违规
（`payments_fail_closed_violations`，阈值 `count==0`）——压测同时验证验证管线
真的在守门。operator 注入沙箱凭据后加 `-e SANDBOX_CONFIGURED=true
-e PAYMENTS_SECRET=... -e PAYMENTS_MCHID=...` 切换为正向验收口径。

阈值草案：`http_req_failed<1%`、回调 p95<2s、旅程失败计数显式暴露；
真实 200 并发/故障注入/浸泡属 CP-50A/50B operator 门禁运行，结果落
`evidence/commercial-cn/CP-50/`。
