# CP-09 J01 引导期渐进同意与权限理解题 — 增量

- 日期：2026-09-14；实施：后台实现 agent（OnboardingGuide/newUserJourney 前端域）
- 愿景：V10（同意是知情的——理解题证明用户真的懂拒绝/撤回的后果）

## 交付（agent W）

1. **渐进同意**：引导三步（aurora/resonance/voice）各插同意卡（AI_PROVIDER_EGRESS/PUBLIC_DISCOVERABLE/VOICE_PROCESSING 按钮，走既有 decideConsent 后重读服务端真值；CAPSULE_COMPILE 为 MANAGED_ELSEWHERE 只出题不设按钮并注明）；同意/暂不均为合法记录——**题才是硬门，不是同意**（避免引导期胁迫式同意）。
2. **理解题 ×3**（正确答案逐字对应 ConsentPurpose 注册表的 withdrawalEffect 语义，newUserJourney.test 逐题校验）：拒 egress 后果 / 撤共鸣体授权后果 / 拒语音后果。所在步继续需答对；完成引导需 3 题全对（圆点跳步漏洞封堵——末步按钮 disabled+「还剩 N 道」）；错答教育性反馈+「重看说明」聚焦。
3. **无跳过捷径**：原「跳过引导」改「稍后再说」（只关闭不完成，下次仍出现）；中英文案对偶。

## 诚实边界

- AuroraApp.tsx 不在域内——组件以可选 prop 默认直连既有端点接入，挂载点零改动即生效；
- 同意决策不做硬性强制（拒绝合法，缺省延后到运行时 ConsentRequestDialog）。

## 测试

OnboardingGuide 15/15、newUserJourney 14/14；全量 web 828/828 + tsc clean。
