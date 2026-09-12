# CP-27/28/17 首个工程增量 — 语音同意门、高风险技能暂停、网关白名单与调用 manifest

- 日期：2026-09-06；CP-27（语音与隐私）+ CP-28（非诊断心理支持技能）+ CP-17（国内全链路模型网关）首个增量
- 愿景：V03/V08/V14（CP-27）、V09/V16（CP-28）、V03/V15/V16（CP-17）

## CP-27 交付

- **VOICE_PROCESSING 同意门**（真实 ASR 出站路径 `/api/asr/transcribe`）：HUMAN 账户默认 DECLINED → CONSENT_REQUIRED（文案：单独同意+可继续用文字）；授予后真实路径放行；**mock 路径保持本地无门**。无音频持久化（控制器不落库，转写即弃）——保留策略：短保存、不用于训练、无声音克隆（声明+结构保证）
- 真实 ASR/TTS 供应商、打断/来电、首音时延单列：外部门/CP-27 后续窗口

## CP-28 交付

- **高风险上下文暂停技能建议**：durable 风险态 ELEVATED 时 `suggest()` 返回 null——不推荐任何反思练习，流转支持路径（蓝图"高风险上下文不调用可能激化情绪的练习"）；建议本身保持可拒绝（客户端契约既有）
- 专家审阅/中文可用性/量表管制：外部门（心理专家）+CP-04 红队回放

## CP-17 交付

1. **出站域名白名单** `GatewayEgressGuard`：真实 Provider base URL 在装配期校验，未知/不可解析/空白主机 fail-closed（启动失败，绝无未批准出站路由）；默认覆盖现有五家+回环；商用以合同主机集覆写（`inner-cosmos.gateway.allowed-egress-hosts`）
2. **调用 manifest** `GatewayCallLedger`（200 条环形）：每次真实链路调用绑定 userId/模块/Provider/用途（AI_PROVIDER_EGRESS）/数据等级（USER_CONTENT）/区域（CN）/合同版本占位 + 结果（OK/STREAM_OPENED/FAILED:…）；**拒答（同意未授予）同样入账**——可审计的出站事实基线，CP-40 持久化管道接管
3. LlmConfig 装配期对全部已配置 Key 的 Provider 校验域名；ConsentEnforcingLlmClient 携带 ledger 与 Provider 标签

## 测试（VoiceSkillGatewayTest 3/3）

语音门（默认拒+授予通+mock 无门）/ELEVATED 暂停建议/白名单三拒两通+manifest 拒答与成功两条记录（user/module/provider/purpose/region 断言）。

## 边界

- 合同版本占位"pending-per-provider"——供应商合同落定后填实（CP-17 台账）；预算/并发/deadline 统一治理与 429/流中断负测随 CP-17 后续+CP-38
- 无 schema 变更（基线 42/100/93/23 不变）

## 状态

- CP-27/28/17: IN_PROGRESS（首个增量 IMPLEMENTED）
