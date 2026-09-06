# CP-16 应用威胁模型 v1（commercial-cn）

> 状态：v1（2026-09-06）。STRIDE 视角按蓝图九类威胁组织；"现有缓解"逐条对应代码事实与
> 自动负测；缺口登记 owner 与放行条件。独立渗透测试是外部门（清单随 CP-63A 提审准备）。

## 1. 威胁清单与缓解矩阵

| # | 威胁 | 攻击面 | 现有缓解（代码事实） | 自动负测 | 缺口/owner |
|---|---|---|---|---|---|
| T1 账号接管 | 口令填充/弱口令/会话劫持 | /api/v1/auth/login、session | BCrypt 存储；登录错误统一"用户名或密码不正确"（不区分用户不存在/密码错）；Spring Security 会话；OIDC PKCE（移动） | `SecurityThreatModelNegativeTest`: 错误用户 vs 错密码消息一致；冻结账户登录 FORBIDDEN | MFA（管理员+高危操作）随 CP-16 后续；真实短信通道 BLOCKED_EXTERNAL（CP-13） |
| T2 越权（IDOR） | 任意对象读/写 | 全部 /api/** | 会话/信件/记忆/共鸣体逐域属主校验（M-001/1.8 等）；CP-14 统一守卫（状态 fail-closed + tombstone + 属主） | CP-14 矩阵 5/5 + 既有 AuroraChatOwnership/CapsuleP1P2 边界测试 | by-id 全量走守卫（CP-14 next） |
| T3 XSS/CSRF | 存储型注入/跨站写 | 全部写接口 | Spring Security CSRF（prod 强制，ProductionStartupGuard 校验）；React 默认转义；UGC 文本 ugc-text 处理（web） | 负测：csrf-enabled=true 时无 token 写请求 403 | CSP 头与富文本净化深化随 CP-11/16 后续 |
| T4 SSRF/prompt 工具注入 | 模型工具/外呼 URL/提示注入 | LLM 网关、信件正文 | Provider 白名单路由（未知路由拒绝）；PiiCredentialDetector 硬拦凭据（信件）；红队银行 32 项（CP-04，注入 12 项）覆盖模型侧 | 负测：信件含密码样式内容 → SAFETY_BLOCKED/PII 拒绝 | 工具调用面收敛（无任意 URL 工具——保持不新增）；红队回放随 CP-17 |
| T5 上传/ZIP 炸弹 | 头像/语音/导入 | 上传接口 | 头像 URL 外链式（无任意文件上传）；音频经 ASR 流（大小/时长限制在 CP-27） | 现无任意上传面（记录性缓解） | CP-27 语音上传扫描+大小上限 |
| T6 枚举 | 用户名/手机号枚举 | 注册/登录/找回 | 登录统一消息；注册重名提示（注册枚举属可接受面，登录不可） | 负测：不存在用户登录与错密码响应一致 | 找回流程上线时补统一文案（CP-13 后续） |
| T7 批量爬取 | 公开共鸣体/广场遍历 | 公开读接口 | Redis 限流（429+Retry-After）；公开面经脱敏（DataMasking）；ACADEMY 反爬观察 | 既有限流测试（TestRateLimitConfig 路径） | 生产限流阈值调参随 CP-37 |
| T8 供应链 | 依赖/镜像/秘钥 | 构建/发布 | SBOM+Trivy+Cosign+provenance（CI）；密钥全环境注入；secret 扫描脚本（SEC-CURRENT-TREE PASS 史） | CI 门禁（历史证据）+ CP-49 复验 | 依赖告警响应 SLA 随 CP-49 |
| T9 管理员滥权 | 内部人读 P0/滥操作 | /api/admin/** | requireAdmin 独立路由；管理面默认不返回 P0 原文；CP-13 冻结/旗标全部审计（tb_account_security_event） | 负测：USER 会话访问 admin 接口 403 | 管理面最小内容访问+二次审批随 CP-36/48 |

## 2. 分层控制（蓝图要求映射）

- MFA：管理员 MFA 随大陆身份通道（外部门后）；当前管理员=独立账户+审计
- 短权限：会话/设备可整体撤销（CP-13 revokeAllDevices）；JWT/OIDC 回调负测随 CP-41/42 真机
- 审计：安全事件/账户安全/撤回回执三线留痕（CP-03/08/13/15）
- 密钥轮换：KMS 路径随 CP-37；轮换程序语义已定（HG-SECRET-ROTATION→CP-16 登记）
- 上传扫描/限流：见 T5/T7
- 生产不暴露 demo/debug/原始 AI 日志：ProductionStartupGuard 强制 CSRF 等；prod 禁种子数据（SEED_ENABLED 默认关）；原始 AI 日志仅内网 AiLog 管理面

## 3. 高危例外流程

任何高危缺口要发布须登记：期限/owner/补偿措施；不因可用性回滚安全拦截（蓝图恢复条款）。

## 4. 状态

- status: IN_PROGRESS（v1 模型+自动负测落地；MFA/上传扫描/渗透为后续）
- next_action: 独立渗透清单（CP-63A 前）；管理员 MFA 方案随身份通道
