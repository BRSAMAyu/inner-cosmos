# CP-14 读路径授权审计（P0–P3 × owner/purpose/consent_version）

> 状态：v1（2026-09-06，基于代码事实）。逐层审计消费面读取路径的强制情况，标注本增量
> 引入的统一守卫覆盖与遗留缺口（缺口→后续包）。

## 1. 审计矩阵

| 层 | 读取面 | owner 强制 | purpose/consent | 本增量状态 |
|---|---|---|---|---|
| P0 对话 | `AuroraChatController.assertOwnsSession`（M-001）/`DialogServiceImpl` 会话/消息读取 | ✅ 会话属主（原子状态迁移+所有权校验） | 对话本体=P0 仅本人；派生使用走 P1 授权 | 已有+负测（AuroraChatOwnershipTest） |
| P0 语音转写 | `VoiceTranscriptionService`（W1） | ✅ 属主 | 语音敏感→CP-07 VOICE 同意位（CP-27 接线） | 遗留：VOICE_PROCESSING 执行点 |
| P1 记忆/画像 | `MemoryServiceImpl.listCards` / by-id | ✅ user_id 过滤 | consentScope（LOCAL_ONLY/NO_EXTERNAL_PROCESSING）已在向量/掩码路径强制（VERIFIED-AI-DATA-BOUNDARY） | **本增量**：listCards 接入 tombstone 过滤（撤回内容不出现在列表） |
| P1 记忆（by-id 读取） | `SensitiveDataBoundaryService.assertReadable("MEMORY",…)` | ✅ 新统一守卫 | 守卫内置：请求者状态 fail-closed + tombstone + 属主 | **本增量**：守卫上线；by-id 全量接线随 CP-24/记忆详情 API 收口 |
| P2 共鸣体 | `CapsuleServiceImpl.getOwnedCapsule`（属主）；公开读取走 visibility+脱敏 | 属主读取 ✅；公开面=visibilityStatus+DataMasking | 编译逐记忆 DataUseGrant（版本化） | **本增量**：getOwnedCapsule 接守卫（含 tombstone）；公开面 CAPSULE_RUNTIME purpose 随 CP-30 |
| P2 沙盒/试聊 | `CapsuleSandboxService` + 边界 | ✅（既有红队测试） | 新用途须重新同意（蓝图 §5.4） | 遗留：purpose=CAPSULE_RUNTIME 进守卫（CP-30） |
| P3 慢信/社交 | `SlowLetterServiceImpl` IDOR 守卫 + blockRelation；persona chat 属主 | ✅ | LetterSafetyFilter 出站审查 | 已有+负测 |
| 管理面 | `AdminController.requireAdmin` 独立路由 | 管理员不进入消费 purpose（守卫不放大管理身份） | 默认不返回 P0 原文 | 已有；管理面最小内容访问随 CP-16 威胁模型 |
| 遥测 | CP-03 指标事件 | 不适用（无正文） | 分析同意门 + props 白名单 | 已有（本战役 CP-03） |
| 模型出站 | `ConsentEnforcingLlmClient` | 请求 userId 携带 | AI_PROVIDER_EGRESS 版本化同意 | 已有（本战役 CP-07） |

## 2. 统一守卫契约（本增量上线）

`SensitiveDataBoundaryService.assertReadable(subjectType, subjectId, requesterUserId, purpose)`：
1. **请求者状态 fail-closed**：FROZEN / MINOR_RESTRICTED → FORBIDDEN（权限无法确认即拒绝，蓝图恢复条款）
2. **tombstone 优先**：撤回内容 → NOT_FOUND（不泄露"曾经存在"；备份复活行同样被拒）
3. **属主与 purpose**：OWNER_READ 严格属主；不匹配 → UNAUTHORIZED；管理身份在此路径无放大效力

## 3. 缓存键授权版本（缺口）

Redis 会话/限流键当前不带授权版本号；撤回后旧缓存短窗复活的排查与修复随 CP-15 资产清单的"缓存"行推进（下一增量），对象/导出/排队推送清单同步建立。

## 4. 状态

- 本增量：守卫 + MEMORY/CAPSULE 两类接线 + 越权矩阵负测 + 防复活链路（CP-15）
- status: IN_PROGRESS；next_action: by-id 全量接线、CAPSULE_RUNTIME purpose、缓存键版本化
