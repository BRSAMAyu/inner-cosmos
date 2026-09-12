# CP-40 首个可编码增量 — Provider 每日花费护栏（成本维度）；CP-50 压测 harness（可编码前体）

- 日期：2026-09-12；CP-40（可观测性、成本与用户影响）+ CP-50（真实压力，harness 前体）；检查点 27
- 台账勘误：蓝图编号中压测是 CP-50（非 49），可观测/成本是 CP-40（非 38）；CP-49 为供应链，CP-38 为工作负载角色隔离——本记录按蓝图真实编号归档

## CP-40：ProviderSpendGuard（成本护栏）

1. **问题**：`ApiRateLimitFilter` 只限请求频率，不限花费——一次 Aurora 双核轮次是 3-5 次 provider 调用，请求限流无法封顶每日成本
2. **语义**：`tryAcquire(userId, module)` 在 provider 调用**之前**执行——预算耗尽后不再产生任何新花费；调用数与估算 token（`TokenEstimateUtils.estimate`，脚本感知）双独立硬顶；超限抛 `AI_SPEND_EXCEEDED`（专用 ErrorCode），用户消息明确"本地功能不受影响，明天自动恢复"（非告警式）
3. **接线**：`StructuredAiService.callObserved` 唯一咽喉——仅 REMOTE 组调用计费（mock/本地回退不烧钱）；guard 为可选注入，直接构造的测试不受影响
4. **计量**：`ai.spend.decisions{outcome=allowed|denied|recorded,dimension}`、`ai.spend.tokens{module}`（MeterRegistry 可选）；Asia/Shanghai 日锚点与指标管线一致；>1024 用户时机会性清理过期日计数
5. **测试（ProviderSpendGuardTest 5/5）**：预算内通过并计数/调用数耗尽快速失败（专用码+诚实消息+他人不受连带）/token 预算独立停/上海日历跨日重置/disabled 是诚实旁路（不计数）
6. **诚实边界**：计数为单 Pod 内存（多 Pod Redis 版属 CP-37/38 真实环境门）——单 Pod 下它是舰队限额的下近似，绝不会让用户在单 Pod 上超出意图预算；无正文日志，无稳定公开用户 ID 入指标
7. 后端全量回归 **1589/1589** 绿（guard 默认预算 400 调用/40 万 token，对测试不可见）

## CP-50：压测 harness（可编码前体）

`scripts/loadtest/aurora-journey.k6.js` + README：
- 旅程混合：登录（CSRF-first）→ 建会话 → Aurora 消息（权威非流式）→ 拉消息 → 结算 → 每迭代一次 SSE 探测（10s 超时，长连 200 并发腿属 staging）
- 阈值草案：`aurora_journey_failures`（旅程失败 SLI，绝不静默绿）<10、http_req_failed<1%、轮次 p95<8s（staging 首轮实测后冻结，CP-04 纪律）
- 本地 smoke 命令可用（node --check 语法验证）；正式 CP-50A（国内 staging、200 并发 SSE/2 倍峰值/≥24h 浸泡/故障注入）为 operator 门禁，本文件即那些运行执行的同一 harness
- README 提醒压测账号注意 ProviderSpendGuard 预算配置

## 状态

- CP-40: IN_PROGRESS（成本护栏落地；OTel 贯穿/看板/告警触达后续）
- CP-50: IN_PROGRESS（harness 前体落地；真实 staging 运行为 operator 门禁）
