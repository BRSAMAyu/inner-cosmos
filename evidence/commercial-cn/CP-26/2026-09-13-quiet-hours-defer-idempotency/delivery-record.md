# CP-26 J07 静默窗/改期/重复领取负测与锁屏脱敏 — 首个工程增量

- 日期：2026-09-13；实施：后台实现 agent（WakeIntent 服务域 + web 通知渲染点），主线程整合回归与测试隔离加固
- 愿景：V04（主动联系不打扰）、V14（通知不泄露私密内容到锁屏）

## 交付内容

### 1. 静默窗（quiet hours）

- `WakeIntentQuietHoursPolicy`（新）：平台窗口（`inner-cosmos.wake.quiet-hours.{enabled,start,end}`，默认 **enabled:false** 不改现行为，有专门测试断言默认值）∪ 用户 `UserProfile.quietHours`，取并集、结束时刻取更晚者；跨午夜窗口（22:00–07:00）正确计算次日/当日结束；resolver 的 sleep/todo/focus 边界无结束时刻可算时保守 +15min 重探；窗口吞没整个 latest_at 时保留既有「边界一次投递」（`latest_window_boundary`），不让约定无声过期。

### 2. DEFERRED 状态机（修正既有缺陷）

既有 `delay()` 把 preferred_at 改写为 +15min（用户约定时间被覆盖且状态不可见）——**真实缺陷，本次修正**：`defer()` 落 `status=DEFERRED + deferred_until=窗口结束 + outcome=DELAY + outcome_reason=boundary:<cause>`，preferred_at 不改写；defer_until 到点且窗口仍合法时被 claimDue 重领；终态 FIRED/CANCELLED/EXPIRED/SUPERSEDED 全部可见落库，无静默丢弃。全链路（claimDue/expirePastDue/listActive/supersede/cancel/reschedule/feedback）纳入 DEFERRED。

### 3. 改期负测

既有条件 UPDATE 实现保留，负测补齐：旧时间点 claimDue 为空、重复改期幂等（一行同窗口）、改期清 deferred_until。

### 4. 重复领取幂等

claim/finish/defer 全部 `WHERE status=... AND claim_token=?` 条件更新（无先查后写）；并发双投递恰一成功、通知恰一条（CountDownLatch 双线程竞速负测断言 `deliveredByA XOR deliveredByB`）。

### 5. 锁屏通知脱敏

`WakeLockScreenPrivacy.tsx`（新）：默认**开**（未设置/存储抛错/非法值一律视为开，fail-closed）；开时锁屏只显示「Aurora 想起你」/ "Aurora is thinking of you"，不显示 reasonForUser（内含用户自述约定内容）；MeSpace「Aurora 主动联系」卡内开关；`useAuroraSession.ts` 3 处唤醒通知 body 经 `lockScreenWakeNotice` 脱敏（推送文案渲染点在此 hook）。

### 6. 迁移与 H2 孪生

V49（`deferred_until` 列 + DEFERRED 状态约束 + 索引；初编 V48 与并行批次撞号，按纪律让号为 V49）；schema.sql 同列 + 幂等 ALTER。

### 7. 主线程整合时修复的测试隔离问题

`WakeIntentQuietWindowAndIdempotencyTest` 最初全局计数 tb_notification，全套并发下被其他缓存上下文的调度器写入污染（expected 1 was 2）——改为按 fixture 用户隔离计数，语义不变。

## 测试证据

- 新增：WakeIntentQuietHoursPolicyTest 9/9、WakeIntentQuietWindowAndIdempotencyTest 5/5、WakeIntentDeliveryJobTest +3 静默窗负测；
- agent 域内回归 65/65（WakeIntent 全部 + ApplicationFlowTest + AuroraNaturalActionService + AliveDecisionEngineTimezone + TrackA + CrisisContinuity + MainlandVendorPush + RuntimeRoleJobBean）；
- 前端：WakeLockScreenPrivacy 10/10，tsc 干净；
- 主线程全量回归：**backend 1767/1767（2 Docker-gated skips）+ web 766/766 + tsc clean**。

## 诚实边界

- `WakeIntentVO` 未暴露 deferredUntil（status=DEFERRED 已可见；调度细节不进 VO）；
- 服务端 vendor push（enqueueWakeIntent）文案未脱敏——PushDeliveryService 不在本批文件域，登记下一批（与 CP-41 联动）；
- 静默窗默认关闭（平台层）：用户级 quietHours 是主通道，平台窗口是运营兜底，两者并集。
