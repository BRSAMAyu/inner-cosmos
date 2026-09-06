# CP-16/24/33 首个工程增量 — 威胁模型与负测、星空编码开关、寄件回执隐私

- 日期：2026-09-06；CP-16（应用安全与威胁模型）+ CP-24（星空复盘闭环）+ CP-33（慢信节律）首个增量
- 愿景：V02/V05/V06（CP-24）、V12/V13（CP-33）、V07/V13/V15（CP-16）

## CP-16 交付

1. **威胁模型 v1**（`docs/commercialization/security/cp16-threat-model.md`）：九类威胁（账号接管/越权/XSS-CSRF/SSRF-prompt注入/上传/枚举/爬取/供应链/管理员滥权）× 攻击面/现有缓解（逐条代码事实）/自动负测/缺口 owner；分层控制映射（MFA/短权限/审计/轮换/上传/限流）；高危例外流程（期限/owner/补偿）
2. **自动负测** `SecurityThreatModelNegativeTest` 4/4：
   - T1/T6：登录错误消息不可区分（不存在用户 vs 错密码完全一致）；冻结账户登录 FORBIDDEN
   - T3：csrf-enabled=true 下无 token 的状态变更 POST → 403（正向控制：带 token 的登录成功证明 403 是缺 token 分支）
   - T9：普通用户访问 /api/admin/users → 4xx 拒绝（无管理数据返回）
   - T4：信件通道凭据外泄硬拦（SafetyBlockedException）

## CP-24 交付

- `starfield/v2?emotionEncoding=false`：情绪编码关闭时星体大小统一为中性常量（gravity 全部 0.5），图例相应说明
- 图例**始终**携带"不是心理评分或医学判断"声明（蓝图"不将情感重力视作心理评分"）
- 开启时 gravity 差异化（≥2 种尺寸）、关闭时仅 1 种——测试双向断言；等价列表两模式同规模（低端回退）

## CP-33 交付

- `SlowLetterOutboxVO`：寄件视图不再回显正文；**DECLINED 与 BLOCKED 统一折叠为 CLOSED**（发送方永远无法分辨"被拒绝"还是"被屏蔽"，状态解释文案也一致）
- 诚实状态保持诚实：SENT/FLYING 展示承诺到达时刻（scheduledArrivalAt）；测试经真实调度两拍（SENT→FLYING→DELIVERED，UTC 时间语义）驱动到 DECLINED/BLOCKED
- 已读回执的收件方选择开关为下一增量（与 J11 联动）

## 测试

- `SecurityThreatModelNegativeTest` 4/4 + `StarfieldEncodingAndLetterReceiptPrivacyTest` 2/2
- 调试中发现并尊重两个既有正确行为：BLOCKED 会建立屏蔽关系（新信被拒）；信件管线时间戳为 UTC

## 状态

- CP-16: IN_PROGRESS（模型+负测落地；MFA/上传扫描/渗透清单后续）
- CP-24: IN_PROGRESS（编码开关+图例声明；J05/J06 现场验收属 CP-12A）
- CP-33: IN_PROGRESS（回执隐私；读回执选择/跨时区负测随 web 联动）

## 边界

- 独立渗透测试、管理员 MFA、语音上传扫描：外部门/后续包；无 schema 变更（回滚=还原代码）
