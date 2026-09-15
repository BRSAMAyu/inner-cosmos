# CP-07 RE_CONSENT_REQUIRED 前端呈现 — 第二增量（登记项闭环）

- 日期：2026-09-15；实施：后台 agent（web 域）

## 交付

ConsentCenterPanel：source===RE_CONSENT_REQUIRED 的行显示「条款已更新，需要重新确认」徽标+说明行（记录在旧版条款下已不作为当前依据，重新确认后以版本 {version} 为准）；CONSENT_CENTER/DEFAULT 行无徽标；data-re-consent 标记；中英双语。

## 测试

ConsentCenterPanel 8/8（+2）；全量 web 869/869。
