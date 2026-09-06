# CP-07 隐私影响评估与同意产品合同 — 交付记录（PIA+规格）

- 日期：2026-09-06；阶段 S1；依赖 CP-05（草稿就绪）
- 愿景：V05、V07、V13

## 交付

- `docs/commercialization/privacy/cp07-pia-and-consent-contract-spec.md`：
  - 按隐私分层（P0–P3+账户/支付/遥测/安全）的数据清单与 PIPL 敏感分类
  - 8 个用途代码的同意矩阵（机制/撤回效果/版本化），显式禁止"训练/改进"用途
  - 6 项差距（G1 Provider 出站同意为最高优先）与下轮编码规格（tb_consent_record/同意中心 API/出站核验/重同意触发/J01 渐进同意）
  - PIA 风险登记与缓解映射

## 边界

- 法务复核与版本生效：外部门；G1/G2 编码为下一增量；语音单独同意与 CP-27 联动
- test_command: 不适用（文档）；回滚=删除文件

## 状态

- status: IN_PROGRESS；next_action: 按规格实现同意合同代码

---

# 增量 2：同意中心编码（同日）

## 交付（G1+G2 关闭）

1. **V38 迁移 + H2 孪生**：`tb_consent_record`（user_id+purpose_code 唯一、status/version/granted_at/revoked_at/evidence_source）
2. **用途注册表** `ConsentPurpose`：7 个用途（REQUIRED/OPTIONAL_ASK/OPTIONAL/SENSITIVE/MANAGED_ELSEWHERE 分组、默认决策、撤回效果文案、版本 PV-2026-09 常量）
3. **同意中心服务**：list/decide/effective/assertProviderEgress
   - CORE_SERVICE 不可拒绝（指引注销）；CAPSULE_COMPILE 只读呈现（由 DataUseGrant 逐条管理）
   - ANALYTICS 与 tb_analysis_consent **双向双写**（CP-03 门即时生效）
   - 出站守卫只约束 HUMAN 账户；合成/评测/无主调用通过；拒绝必须响亮（新 ErrorCode CONSENT_REQUIRED），不静默降级
4. **API**：`GET /api/me/consents`、`POST /api/me/consents/{purpose}`（body grant）、`GET /api/me/consents/summary`
5. **网关接线** `ConsentEnforcingLlmClient`：包装真实 Provider 链（含 failover）；**仅当 Provider 已解析出凭证时包装**——空 Key 的 dev 配置在调用期回落本地 Mock（无出站）不包装，工程环境无需同意记录即可工作；forceMock 请求跳过
6. 测试 `ConsentCenterTest` 5/5：默认矩阵、决策与约束、egress 守卫（人类/合成/无主/授权后）、ANALYTICS 双写进出指标管线、装饰器"拒绝即不触达委托 + forceMock 豁免"

## 基线

- 迁移 38 / 表 94 / 身份列 87 / v20→38 计 19（Postgres 基线测试通过）

## 剩余边界

- 前端同意中心页（web）随 CP-09/10；VOICE_PROCESSING 同意位在 CP-27 启用语音前接线；重同意（版本变更 STALE）触发器待首个版本变更时实现；控制器 MockMvc 冒烟随 web 联动补
- status: 仍 IN_PROGRESS（核心合同已实现；J01 渐进同意 UI 与语音接线待后续包）
