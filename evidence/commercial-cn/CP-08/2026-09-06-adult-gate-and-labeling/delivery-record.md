# CP-08 AI标识、成人准入与安全政策 — 交付记录（首个工程增量）

- 日期：2026-09-06；阶段 S1；依赖 CP-06（路线设计已关闭）/CP-07（同意合同部分，分析授权开关已由 CP-03 预埋）
- 愿景：V01、V04、V13
- current_sha：随本检查点提交

## 已交付（代码+测试）

1. **成人准入门槛**（`inner-cosmos.adult-gate.required`）
   - 商业 profile（prod/local-complete/commercial-sg；commercial-cn 同规则）默认 **true**，fail-closed；dev/test 默认 false 保持内部工程可用性
   - 注册需出生日期（ISO）+ 成人确认；上海时区计龄；**申报未满 18 一律拒绝（无论 profile）**
   - 内部合成账号（SYSTEM/DEMO/SANDBOX/SYNTHETIC/SHOWCASE）非服务用户，跳过门槛；种子账号显式声明成年
   - V37 迁移：`tb_user.birth_date/age_gate_method`（SELF_DECLARED → CP-13 升级 VERIFIED_ID）
2. **未成年拦截状态与申诉链**
   - `MINOR_RESTRICTED` 状态 + `tb_minor_appeal`（PENDING/ACCEPTED/REJECTED）
   - `MinorProtectionService`：assertAdultAccess / flagMinor / appeal / resolve；ACCEPTED 立即恢复误判成人
   - Aurora 三个入口（/message、/message-rich、/stream-stage）守卫；AdminController 旗标/申诉列表/复核接口
3. **AI 生成标识（第一层）**
   - `AuroraReplyVO.aiGenerated=true`（所有 AI 回复显式标记）；/message 包同步携带
   - 标识规范矩阵（文本/语音/共鸣体/导出）在政策剧本中定义，传播侧随 CP-27/31
4. **安全政策操作剧本 v1**：身份提示、连续使用/依赖风险分级、误入处置、联系人与极端情境值班剧本、非医疗词库、投诉时限、政策版本回退

## 测试证据

- `AdultGateAndMinorProtectionTest` 4/4：17岁364天拒绝/18岁0天通过/缺 DOB 拒绝/格式错拒绝/未确认拒绝；拦截→申诉→接受恢复；重复申诉冲突；拒绝后保持受限；aiGenerated 契约
- 基线计数已按 V37 更新（37 迁移/93 表/86 身份列/18 v20）——见 Postgres 基线测试
- 全量回归结果见检查点提交说明

## 边界与恢复

- 未含：使用时长累计提醒（CP-11/26）、语音/导出/分享侧标识（CP-27/31）、真实身份核验升级（CP-13）、专业审阅签字（外部门）
- 回滚：V37 为纯增量列/表；控制器守卫与门槛可通过配置关闭；无破坏性迁移

## 状态

- status: IN_PROGRESS（首个工程增量 IMPLEMENTED；政策 v1 待专业审阅）
- next_action: 心理专家+法务审阅剧本 v1；CP-09 前端 aiGenerated 角标
