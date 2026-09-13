# CP-46 续费前显著提醒排程 + 前端配额 UI（检查点 39）

清偿 CP-46 未清 next_action 的两半。

## 1. 续费前显著提醒排程（主线程）

- **`RenewalReminderService`**：扫描 auto_renew=true 且 period_end 在 N 天（默认 3，
  配置 `inner-cosmos.payments.renewal-reminder.{enabled,days}`，application.yml 显式
  落档）内的 ACTIVE/TRIAL/GRACE 权益；通过通知管线（含推送 outbox fan-out）发
  **每计费周期恰好一条**的显著提醒——通知幂等键内嵌周期末日期，重扫/换 Pod 永不
  双发；文案点名续费日期、原价、随时取消且取消后当前周期仍可用（取消入口永不缺席）
- **`RenewalReminderJob`**：每日 09:00 本地 cron，仅 scheduler/all 角色运行
- **契约 `RenewalReminderContractTest` 2/2**：窗口内 auto-renew 权益恰得一条提醒
  （重扫不叠加）；已取消（不再扣费）不提醒、远期不提醒；文案含"自动续费"与"取消"

## 2. 前端配额 UI（后台 agent，web/ 域独立交付）

- `web/src/api.ts`：QuotaOverview 类型 + `api.quotas()`（走现有 request 封装）
- `web/src/components/QuotaPanel.tsx`：双语（zh-CN/en-SG）；每日配额（已用/上限/
  剩余/进度条/**UTC 重置时间本地化**——无 Z 后缀 ISO 先补 Z 再转本地，避免时区
  错位）；订阅窗口（状态/重置/自动续费标记）；**neverPayGated 承诺文案在错误态
  依然可见**（产品原则不依赖服务端数据）+ 可展开能力清单；空态/错误态/重试
- 挂载：me 空间 account 标签（账户级权益语义），PortraitClaimsPanel 同型
  挂载后单次加载守卫（失败不循环重试）
- 测试 `QuotaPanel.test.tsx` **8 用例**全绿（渲染/空态/错误态/en-SG/resetTimeLabel
  三单元用例）；全量 **web 750/750**，tsc 干净

## 回归

后端 1638/1638 绿（+2）；Web 742→**750/750** 绿（+8），tsc 零错误。
