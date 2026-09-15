# CP-26 服务端 vendor push 文案脱敏 — 第三增量（登记项闭环）

- 日期：2026-09-15；实施：后台 agent（push 边界域）

## 交付

脱敏落在 `PushDeliveryServiceImpl.enqueueWakeIntent`（vendor push 边界，fail-closed——任何调用方入队自动脱敏，无需各调用点记忆义务）：
- title=`Aurora`；body 按设备 locale（en-* → "Aurora is thinking of you"，缺失/其余回落 zh-CN「Aurora 想起你」）——与前端 `LOCK_SCREEN_WAKE_COPY` 逐字一致；
- `reasonForUser/content` 只进 App 内通知与 tb_wake_intent，推送行（tb_push_delivery.title/body）只承载中性唤起；deep_link 不改写；已有投递行不回溯；
- 配置 `inner-cosmos.wake.push-sanitized` 默认 **true**（隐私优先，与前端锁屏脱敏默认开一致）；置 false 为运维显式选择；UserProfile 无推送脱敏偏好位（如实），schema 冻结未建列，平台级配置替代并在 yml 注释写明。

## 测试

WakePushCopySanitizationTest/OffTest 2/2（推送行不含自述原文片段断言 + 配置关真实属性注入验证 + deep_link 逐字不变 + App 内通知保留全文）；推送/唤醒域回归 36/36。

## 诚实边界

用户级推送脱敏偏好列待 schema 解冻后接（与 quietHours 同表可并）；vendor push 真机投递验证属渠道合同 operator 门禁。
