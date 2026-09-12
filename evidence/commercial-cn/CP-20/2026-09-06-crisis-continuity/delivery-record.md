# CP-20/21/26 首个工程增量 — 危机连续性运行时、来源图回放、主动关心同意门

- 日期：2026-09-06；CP-20（风险连续性与危机处置运行时）+ CP-21（记忆生命周期与来源图）+ CP-26（时间理解与可控主动关心）首个增量
- 愿景：V04/V09/V13（CP-20）、V05/V06/V07（CP-21）、V04/V08/V14（CP-26）

## CP-20 交付

1. **V41 + H2 孪生**：`tb_user_risk_state`（跨会话/跨 Pod 持久风险态，显式衰减）+ `tb_crisis_intervention`（处置台账：等级/动作/责任人/结果/升级/最小披露）
2. `CrisisContinuityService`：
   - **跨会话连续**：会话内滚动视图留在 SessionRiskAggregator；用户级持久分数跨会话/跨 Pod 累积，24h 半衰期，**读时衰减**（陈旧 WATCH 不会永久滞留）
   - **语境化**：复用会话聚合器的否定/过去时（×0.2）与第三人转述（归零）规则——"以前曾经…现在已经不会"与"朋友说他不想活"不抬升本人分数
   - **升级路径**：NONE→WATCH(≥0.5)→ELEVATED(≥1.5)，跨阈值写台账（RESOURCES_SHOWN/WATCH_ESCALATED/GENTLE_CHECK_IN）
   - **强制分支**：HIGH 危机命中 → 立即 EMERGENCY_PROTOCOL 台账行（升级路径写明人工跟进责任人与联系人缺失替代路径；最小披露边界：不自动外呼、不泄露完整对话），**且不抬升累积等级**（单次词汇不给用户贴永久标签）
   - 属主透明：`GET /api/safety/me/status`（等级+温和解释+支持资源常可见，明确"不是诊断"）
3. 接线：`SafetyServiceImpl.record` 同事务喂入（每条 SafetyEvent 都进入连续性视图）

## CP-21 交付

- `MemoryProvenanceService.replay(userId, memoryId)`：对话会话 → 记忆卡（版本链 MemoryOperation）→ 下游派生（授权共鸣体编译 DataUseGrant / 检索向量）完整来源图回放，含解释文案
- 边界：跨用户 UNAUTHORIZED；撤回（tombstone）后回放 NOT_FOUND（CP-15 联动）

## CP-26 交付

- `WakeIntentServiceImpl`：**PROACTIVE_CARE 同意门**——未同意时拒绝新预约（CONSENT_REQUIRED + 指引同意中心）；同意后可预约；撤回后再次拒绝（退出是持久的，蓝图 §5.2"不能由主动任务再次强行开启"）

## 测试（CrisisContinuityAndProvenanceAndProactiveTest 4/4）

跨会话累积/衰减读时重算/否定与第三人语境化/HIGH→EMERGENCY_PROTOCOL（升级与最小披露文案断言）/属主状态；来源图全链+跨用户+撤回不可回放；主动关心同意门三态（拒/允/撤）。

## 诚实边界

- **Docker 引擎本轮不可用**：Postgres 基线测试（41 迁移/99 表/92 身份列/v20 链 22）本轮跳过未执行；计数更新沿用 V38–V40 已验证的同一机械模式，H2 孪生 schema 由全部 H2 测试覆盖。Docker 恢复后应复跑 `PostgresFlywayBaselineTest`（已登记 next_action）
- 紧急联系人真实外呼/夜班值班/专业审阅案例冻结：外部门（CP-08 审阅 + CP-48 值班）
- 衰减用整小时粒度（24h 半衰期），更细粒度随 CP-40 观测精化

## 状态

- CP-20: IN_PROGRESS（运行时落地；专业审阅/值班演练后续）
- CP-21: IN_PROGRESS（回放落地；合并/冲突/并发编辑负测与增量抽取语义后续）
- CP-26: IN_PROGRESS（同意门落地；静默窗/改期/锁屏脱敏/重复领取负测随 web 联动）
