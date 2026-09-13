# CP-07 版本化重同意触发器 — 增量

- 日期：2026-09-13；实施：主线程（与 CP-15/CP-18/21 并行，文件域不相交）
- 愿景：V10（同意是知情的——条款版本变了，旧同意不能静默沿用）

## 缺口与交付

`ConsentPurpose.CURRENT_VERSION` 注释早已预告 "bumping this triggers re-consent flows later"，`ConsentRecord.version` 也记录了决策时版本——但 `effective()` 只看 status 不看 version：版本提升后，按旧条款文本给出的授权会**静默继续生效**。

`ConsentCenterServiceImpl` 补版本重同意门：

1. **stale 判定**：`row.version != CURRENT_VERSION` 即 stale（schema 的 version 列 NOT NULL，不存在 null 行——用约束测试坐实）；
2. **effective()**：stale 记录不再计授权——回落到该用途的**无决策默认值**：AI_PROVIDER_EGRESS（默认 NOT_GRANTED）→ 出站被拦 + assertProviderEgress 抛 CONSENT_REQUIRED（fail-closed）；CORE_SERVICE（REQUIRED 默认 GRANTED）→ 核心环路永不因版本升级断裂；ANALYTICS（默认 GRANTED）→ 回到默认态而非沿用旧授权；
3. **list()**：ConsentView.source=RE_CONSENT_REQUIRED 显式提示需重确认（version 字段恒报当前注册表版本——UI 看到的是现行条款）；无记录行仍是 DEFAULT 不误报；
4. **decide()**：重新决策即按 CURRENT_VERSION 盖章，授权恢复、提示消除。

## 测试证据

新增 `ConsentVersionedReConsentTest` 5/5：
- 新鲜授权带当前版本生效；
- 旧版本 GRANT → egress fail-closed + RE_CONSENT_REQUIRED + 重同意恢复；
- version 列 NOT NULL（想置 null 被约束拒绝——「无版本行」在 schema 上不可能存在）；
- CORE_SERVICE 版本升级不中断服务 + 可选发现用途回落 declined 默认；
- 旧版本 DECLINE 同样提示重确认（记录行不得冒充现行决策），无记录行不误报。

回归：ConsentCenterTest 5/5、ConsentEnforcingStreamLedgerTest 6/6、VoiceSkillGatewayTest 3/3、ApplicationFlowTest 8/8；全量 **backend 1837/1837**。

## 诚实边界

- ANALYTICS 的 stale GRANT 回落到默认 GRANTED（平台对无决策用户适用同一默认）——指标门读的双写表 outcome 一致；若未来 ANALYTICS 默认改为 DECLINED，stale 行为自动更保守；
- 版本提升的操作流程（条款文本变更 + CURRENT_VERSION bump + 公告）是运营动作，本批交付的是版本变化后的**强制语义**；
- 重同意提示的专门 UI 呈现（除 source 字段外）未做——前端消费 RE_CONSENT_REQUIRED 登记后续。
