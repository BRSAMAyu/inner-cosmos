# CP-13 国内身份与多设备账户安全 — 交付记录（首个工程增量）

- 日期：2026-09-06；阶段 S1；依赖 CP-05（草稿）/CP-07（同意中心已落地）
- 愿景：V01、V07、V14
- current_sha：随本检查点提交

## 交付（代码+测试）

1. **V39 迁移 + H2 孪生**：`tb_identity_verification`（provider 引用唯一、PENDING/VERIFIED/REJECTED/EXPIRED、过期与消费时间戳；不存证件图/原始码）与 `tb_account_security_event`（审计流）
2. **核验通道抽象** `AgeVerificationProvider` + `SandboxAgeVerificationProvider`（dev/test 全链可测：BIRTH:/MINOR: 应答编码结果）；**fail-closed**：配置非沙箱通道而无合同 Provider → FORBIDDEN"不允许降级为弱验证"（prod profile 已声明 operator-sms 占位，通道合同落地前实名核验保持关闭）
3. **VERIFIED_ID 升级链** `IdentityVerificationService`：
   - initiate：单活跃挑战（旧的 PENDING 立即 EXPIRED）；已 VERIFIED_ID 拒绝重复核验
   - confirm：一次性消费（重放→CONFLICT）；跨用户探测引用→NOT_FOUND 不泄露存在性（不串号）；过期→EXPIRED+CONFLICT
   - 核验成年 → `birthDate` 以核验值覆盖自报值、`ageGateMethod=VERIFIED_ID` + 审计
   - **核验未成年 → 直连 CP-08 拦截**（MINOR_RESTRICTED + 成人服务停止），绝不升级
   - 拒答 → REJECTED + 原因可见；全程审计（AGE_CHALLENGE_ISSUED/UPGRADED/REJECTED/MINOR_INTERCEPT）
4. **多设备账户安全** `AccountSecurityService`：
   - `revokeAllDevices`：撤销全部设备注册（token 哈希/密文清空）+ 审计
   - `freeze`/`unfreeze`（ACTIVE↔FROZEN 条件迁移；冻结即登录被拒；MINOR_RESTRICTED 不走此路，由申诉链处理）+ 审计
   - `snapshot`：状态/账户类型/年龄门槛等级/活跃设备数/最近安全事件
5. **API**：`/api/me/identity/age-verification`（POST 发起、POST confirm、GET 状态）、`/api/me/security/snapshot`、`/api/me/security/revoke-devices`、Admin `users/{id}/freeze|unfreeze`

## 测试证据（IdentityVerificationAndAccountSecurityTest 7/7）

成年升级（方法+生日覆盖+历史）/单次消费+跨账户防护/过期拒绝+再发起作废旧挑战/未成年联动拦截/错答拒绝/fail-closed 通道/撤销设备+冻结登录阻断+解冻恢复+审计行——全部通过。基线 39 迁移/96 表/89 身份列/v20 链 20 更新并通过。

## 边界（诚实声明）

- 真实通道（运营商短信含回收号处理、支付宝认证、微信/Apple 第三方登录、账号合并/找回）为 BLOCKED_EXTERNAL：须合同核验的供应商与真实凭证；接入前 fail-closed 已验证
- OIDC/PKCE 复用现状（AUTH-OIDC 项）；深链/注销后旧 token 回调负测随 CP-41/42 真机窗口
- rollback：V39 纯增量表；服务/接口可整体摘除，无破坏性变更

## 状态

- status: IN_PROGRESS（首个增量 IMPLEMENTED）
- next_action: 通道合同核验后接真实 Provider；微信/Apple 登录与账号合并随 CP-51B 商店包
