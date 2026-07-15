# Campaign A — Living Aurora Experience Contract

状态：`ACTIVE / IN_PROGRESS`  
建立日期：2026-07-15  
裁决权威：`goal-objective.md` → `对齐文档/README.md` → `对齐文档/16-体验优先的完全体重构策略与产品战役.md`

## 用户可验收场景

1. **被听见并可改口**：用户倾诉时 Aurora 以有作用区分的多气泡回应；用户可随时停止、补充或打断。Aurora 不重复开场，而是显式重规划；断线后从耐久时间线恢复，不丢失已经说出或听见的内容。
2. **自然约定回来**：用户用“今晚十点”“明早通勤后”等自然表达协商回来时间、原因和要续接的问题。界面展示用户本地时区、可改期/取消；DST 缺口或歧义不被静默猜测。
3. **只在仍相关时履约**：投递前重新判断原约定是否仍相关、已被新对话解决或被新约定替代。相关则真实生成通知；不相关则过期或替代并留下可解释审计，不能只靠调度状态冒充履约。
4. **从通知继续同一件事**：通知说明 Aurora 为什么回来，深链直接打开原上下文和未解决问题。用户可选“正合适 / 晚一点 / 不再提醒这类事”，反馈真实改变本次 intent 与后续主动策略。
5. **看得见但受控的成长**：Aurora 可从互动形成 Self 候选，但先显示变化、证据、反证、沙盒差异和回滚目标；只有通过 Constitution/安全/连续性评测且用户确认后才激活，并可通过新版本前向回滚。

## Experience film

Day 1 21:40，用户说“明天汇报让我害怕”，Aurora 先接住情绪，再分两条消息澄清最担心的环节。用户在第二条生成中打断：“先别分析，我只想确认这种害怕可以被接住。”界面从 `speaking` 变为 `listening/replanning`，已提交气泡保留；网络中断后从耐久时间线恢复。用户说“明早通勤结束后问我是否愿意拆第一步”，Aurora 复述原因、时间窗口和续接点；若时间落入 DST gap/overlap，要求用户确认明确时刻。

Day 2 08:50，投递前 runtime 读取原话题、新对话和反馈历史。若用户凌晨已完成汇报准备，intent 标为已解决而不打扰；否则生成带原因的站内通知。用户点击后直接进入原会话的续接锚点，选择“晚一点”，自然改到午休后。12:45 Aurora 再判断仍相关后回来；用户选“正合适”，反馈提高类似约定的可信度，而非泛化为固定主动预算。

Day 3，Aurora 从多次互动形成“先接住、再拆行动”的表达偏好候选。用户看到证据和反证，运行回放评测并确认激活 v2；若体验不对，可选择回到 v1，系统创建可审计的新版本而不改写历史。Provider 失败时 UI 明示降级且保持 stop/interrupt/恢复可用；安全冲突时优先安全，不删除 Self、关系或主动性语义。

## Baseline、目标差异与指标

| 维度 | 当前 baseline | 战役目标与验收指标 |
|---|---|---|
| 对话编排 | typed SSE、多气泡、stop/interrupt、耐久恢复已贯通 | 核心 E2E 100%；断线/打断不重复用户消息和已提交气泡；首个可停止状态可观察 |
| 时间认知 | WakeIntent、租约、时区/DST 修复；仍有固定一小时入口 | 自然时间解析集覆盖中英文与 DST；临投递相关/替代判断有可复现标注集 |
| 履约续接 | 站内通知基础，无完整深链与反馈学习 | 通知→原上下文→反馈闭环 E2E 100%；真实投递审计可关联 intent、decision、notification、continuation |
| Self/Emergence | 候选、评测、同意激活、前向回滚已形成首闭环 | 越权/Constitution fail-closed；版本链可回放；非实现者能说出“她学会了什么、为何、如何撤回” |
| AI 效果 | Mock 可复现，已有 runtime 基础 | 至少一个真实 Provider；普通聊天 vs 完整 runtime pairwise 盲测，报告偏好、失败类型、成本与模型身份 |
| 体验 | React Aurora 空间已可运行，仍偏功能面板 | 盲体验者无需说明完成场景 1—5；窄屏、键盘、screen reader、reduced motion 有等价路径 |

## 能力图

```text
User/Aurora UI state
  ├─ typed SSE choreography ─ durable turn timeline ─ interrupt/replan/replay
  ├─ natural time negotiation ─ WakeIntent ─ lease worker ─ relevance/supersession
  │                                                   └─ notification ─ deep link ─ feedback
  └─ visible Self change ─ emergence proposal ─ dual-core replay evaluation
                                      └─ Constitution/safety/consent ─ version/rollback

Context assembler: current topic + unresolved question + selected memory/profile + relationship + Self
Runtime: understanding/planning core → expression/relationship core → optional critic/repair
Evidence: migration + focused tests + browser E2E + provider pairwise + delivery audit
Deployment: local-complete first; Academy EKS preserves domain semantics without unverified services
```

## 资产裁决与数据迁移

- 可替换：物理 Aurora HTML、旧的固定一小时 UI、旧 SSE payload/内部单核编排、只展示实体的管理式面板。
- 必须迁移/保留：用户、P0 对话、turn/bubble/event 时间线、WakeIntent 与投递审计、Notification、用户 IANA 时区、关系状态、Self 模型/候选/版本/评测、反馈与同意记录。
- 不得以战役重构删除或降级 Aurora proactive、Self/Constitution/Emergence、人格/关系演化、用户心理建模、共鸣体动态人格、慢信/星空/匹配领域模型。
- API 可演进，但跨版本必须有迁移、所有权负向测试和可回滚证据；生产历史迁移校验和变化必须显式处理。

## 风险、人工门禁与非目标

- 高风险：错误时区/DST、重复或疲劳投递、旧事件误触发、跨用户深链、Self 漂移、Constitution 绕过、真实 Provider 数据外发与成本失控。
- 人工门禁：外部 Provider 密钥/付费调用授权、真实 Push 凭据与设备权限、生产部署/迁移执行、独立盲体验与高风险心理安全评审。
- 自动停止条件：安全修复若只能通过删除现有创新能力实现；不可逆数据迁移无恢复演练；所有权边界无法证明；真实模型会发送未经授权的 P0 数据。
- 非目标：在本战役内重做星空/匹配/慢信主体体验；用更多基础设施表代替用户闭环；以 Mock 结果声称真实模型偏好；以通知记录存在声称 Aurora 已正确回来。

## 当前检查点

- 已有：typed SSE、多气泡、stop/interrupt、durable replay；WakeIntent/租约/通知基础；用户 IANA 时区与 DST 严格处理；Self proposal→evaluation→consent→version→forward rollback；四条真实浏览器旅程。
- 下一闭环：自然时间协商 → 临投递相关性/替代 → 真实通知与深链 → 三态反馈策略；随后接入真实 Provider pairwise 和组员盲体验。
- `AURORA-TEMPORAL` 与 `AURORA-SELF` 在上述证据和独立验收完成前保持 `IN_PROGRESS`。
