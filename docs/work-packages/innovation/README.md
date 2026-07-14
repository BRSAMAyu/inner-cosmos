# Inner Cosmos Innovation Work Package Registry

> Authority: `对齐文档/08-Aurora生命感与共鸣智能创新架构.md`  
> Purpose: 将 Aurora、记忆、画像、共鸣体、心理 Skill 与 Wow Demo 拆成可被 Coding Agent 领取的实现单元。  
> Status: Registry PROPOSED；`INNO-EVAL-001` 已 IMPLEMENTED、待独立复核，其余包按依赖逐包进入 READY。
> Concurrency: 同时最多 4 个 Builder，且 Owned Paths 不重叠。

---

## 1. 执行规则

每个 Agent 开始前必须：

1. 读取 `AGENTS.md`、`06`、`07`、`08` 和本文件；
2. 检查当前 branch、HEAD、worktree 和 dirty state；
3. 领取唯一 Package，填写 owner、reviewer、base SHA、owned paths；
4. 先记录现状行为与评测基线，再修改代码；
5. Builder 不能独立把自己的包标记为 VERIFIED；
6. 不得删除现有 proactive、ALIVE、多消息、Self、Constitution、Emergence、画像或动态共鸣体能力；
7. 所有 AI 行为变化必须报告质量、延迟、模型调用和成本；
8. 效果下降时优先保留可切换旧路径，不得用“架构更整洁”掩盖体验退化。

状态：

```text
PROPOSED → READY → CLAIMED → IMPLEMENTED → EVALUATED → VERIFIED → INTEGRATED
                         ↘ FAILED       ↘ REJECTED
```

---

## 2. 依赖与阶段

| Phase | Package | 名称 | 核心依赖 |
|---|---|---|---|
| I0 | INNO-EVAL-001 | Innovation Evaluation Harness | W0 |
| I1 | INNO-CONV-001 | Conversation Event 与 Turn Plan | EVAL |
| I1 | INNO-MEM-001 | Memory Lifecycle V2 Schema/Policy | EVAL、DB ADR |
| I1 | INNO-PORTRAIT-001 | Longitudinal User Model V2 | EVAL、MEM |
| I1 | INNO-VECTOR-001 | Multi-space Embedding & Hybrid Retrieval | MEM、PORTRAIT、PG PoC |
| I2 | INNO-CONV-002 | Interruption/Cancellation/Replanning | CONV-001 |
| I2 | INNO-TIME-001 | Temporal Cognition & Wake Intent | CONV-001、MEM |
| I2 | INNO-AURORA-001 | Dual Kernel Orchestration | CONV-001、MEM、PORTRAIT |
| I3 | INNO-SELF-001 | Self Genome & Emergence V2 | AURORA、EVAL |
| I3 | INNO-CAPSULE-001 | Capsule Genome Compiler | MEM、PORTRAIT、VECTOR、EVAL |
| I4 | INNO-CAPSULE-002 | Resonance Agent Runtime | CAPSULE-001、CONV-002 |
| I4 | INNO-SIM-001 | High-fidelity Simulator Evaluation | CAPSULE-002、EVAL |
| I4 | INNO-MATCH-001 | Multi-objective Resonance Matching | VECTOR、CAPSULE-001 |
| I4 | INNO-PSY-001 | Psychology Skill Runtime | AURORA、EVAL |
| I4 | INNO-PSY-002 | First Psychology Skills | PSY-001 |
| I4 | INNO-STAR-001 | Starfield Data & Visualization V2 | MEM、PORTRAIT、SELF |
| I5 | INNO-CLOUD-001 | Innovation Workloads on Kubernetes | TIME、AURORA、CAPSULE |
| I5 | INNO-DEMO-001 | Aurora Alive × Resonance Wow Demo | 所有展示路径 |

---

## 3. Package Definitions

### INNO-EVAL-001 — Innovation Evaluation Harness

**Status**：`IMPLEMENTED`（Builder 自测完成；`VERIFIED` 需要不同 reviewer）
**Evidence**：`evidence/innovation/INNO-EVAL-001/`

**Objective**：在重构前冻结 Aurora 与共鸣体真实行为，并建立可重复比较的质量基线。

**Owned Paths**：`ai-lab/evals/**`、`src/test/**/innovation/**`、`docs/research/**`；不改生产生成逻辑。

**Tasks**：

- 建立脱敏 Aurora 多轮集、主动触达场景、打断场景；
- 建立 3—5 个经授权测试 persona 的 train/dev/held-out trajectory；
- 实现 naturalness、felt-understanding、timing、interruption、self-continuity、persona-fidelity、style、behavior、privacy 指标；
- 支持 single-prompt、current-production、candidate-system 的 A/B；
- 保存模型、Prompt、温度、随机种子、成本和延迟。

**Acceptance**：

- 同一配置重复运行可得到稳定统计；
- held-out 数据不进入 Persona Compiler；
- 至少含人类盲评导入格式和多 Judge ensemble；
- 产出 `docs/research/innovation-evaluation-spec.yml` 所要求的报告。

---

### INNO-CONV-001 — Conversation Event 与 Turn Plan

**Objective**：把当前 `segments[]` 升级为持久、可回放的 Turn Plan 和 bubble lifecycle。

**Owned Paths**：新 `conversation` 领域模块、对话 DTO/API、迁移、对应测试；避免同时重构 Memory。

**Tasks**：

- 新建 turn、turn_plan、message_bubble、conversation_event、generation_attempt；
- 当前 Aurora 输出适配为 Turn Plan，不先改变表现；
- SSE 使用事件 ID 和明确事件类型；
- 保存 bubble purpose、状态、顺序、planned/sent/cancelled 时间；
- 提供 turn timeline 查询用于 Debug UI。

**Acceptance**：

- 现有 1—3 气泡体验不退化；
- 所有气泡状态可回放；
- 重试不会重复 committed bubble；
- 双副本下同一 turn 只有一个有效 plan commit。

---

### INNO-MEM-001 — Memory Lifecycle V2

**Objective**：建立可更新、冲突、巩固、遗忘和回滚的记忆权威。

**Owned Paths**：新 `memory-v2` 模块、PoC schema/ADR、迁移和测试；不得一次迁移全部旧表。

**Tasks**：

- 定义 memory item/version/evidence/relation/operation；
- 实现 ADD/UPDATE/MERGE/SPLIT/REINFORCE/DECAY/CONTRADICT/SUPERSEDE/ARCHIVE/FORGET/NO_OP；
- 建立双时间、用户权威、sensitivity、purpose 和 consent；
- 用旧 MemoryCard 作为 adapter 输入；
- 实现 memory policy replay 和操作审计。

**Acceptance**：

- 冲突事实不被静默覆盖；
- 用户纠正成为最高权威；
- 删除后关系、全文和向量索引无残留；
- 对相同事件重复消费无重复 memory；
- 当前核心旅程继续可用。

---

### INNO-PORTRAIT-001 — Longitudinal User Model V2

**Objective**：将 10 维 Portrait 升级为有证据、时间化、多视图的用户模型。

**Tasks**：

- 定义 observation、hypothesis、counter-evidence、profile view/version；
- 支持 identity/interests/values/communication/emotion/cognition/motivation/relationship/support/time/growth/simulation 维度族；
- 每次更新先找反证，避免单轮抖动；
- 生成 Aurora、匹配、共鸣体、可视化不同视图；
- 用户可见视图支持纠正和禁用。

**Acceptance**：

- 单次极端表达不永久改变稳定画像；
- 所有推断可追溯 evidence；
- 相互矛盾的场景性特征可以共存；
- held-out 画像质量优于当前 10 维 baseline。

---

### INNO-VECTOR-001 — Multi-space Embedding & Hybrid Retrieval

**Objective**：验证 PostgreSQL + pgvector 对多画像空间、记忆和匹配的支撑。

**Tasks**：

- 建立多 embedding type/version；
- 实现 FTS + ANN + metadata filter + rerank；
- 建立 interest/value/style/emotion/support/relationship/state/growth/trajectory 向量；
- 对比 single vector 与 multi-space；
- 支持 embedding model 双版本重建。

**Acceptance**：

- 数据权限过滤在数据库召回阶段生效；
- 多空间在至少两个任务上优于单向量；
- 报告 recall、nDCG、p95、成本和索引大小；
- 不同 embedding model 的向量不会混算。

---

### INNO-CONV-002 — Interruption, Cancellation & Replanning

**Objective**：让用户可以在 Aurora 思考或说话时自然打断、停止和改题。

**Tasks**：

- 引入 cancellation token 和 turn ownership；
- 实现 partial delivery、pending bubble cancel、discarded attempt；
- 新消息触发 Replan；
- UI 支持停止、插话和产品级 thinking state；
- 建立 race/concurrency/failure tests。

**Acceptance**：

- 用户停止后不再出现旧 turn 的后续 bubble；
- Provider 无法取消时，完成结果也不得落库或发送；
- 新 turn 知道旧 turn 已说和未说内容；
- 打断被视为正常事件，体验自然且无错误提示。

---

### INNO-TIME-001 — Temporal Cognition & Wake Intent

**Objective**：将轮询型 proactive 升级为具备时间推理、未来意图和持久唤醒的系统。

**Tasks**：

- PrivateTimer adapter → Wake Intent；
- 解析明确/模糊/周期时间；
- 管理 earliest/preferred/latest、前置/取消条件和时区；
- 队列化 due-window dispatch 与 lease claim；
- 主动决策输出 push/wait/reschedule/silence；
- 用户主动性、陪伴程度和场景偏好可调。

**Acceptance**：

- 无全用户 90 秒轮询；
- Pod/Worker 重启不丢 Wake Intent；
- 同一意图不重复发送；
- 时间、理由代码、证据和 outcome 可追踪；
- Demo 场景能在预定窗口自然唤醒。

---

### INNO-AURORA-001 — Dual Kernel Orchestration

**Objective**：在现有 Aurora 路径上加入 Fast Social Kernel 与 Slow Reflective Kernel。

**Tasks**：

- 结构化 Perception、Interaction State、Support Plan；
- Fast Kernel 保持低延迟，Slow Kernel 负责高质量规划；
- Evidence Pack、Skill、Self、User、Relationship 作为 typed inputs；
- Demo Quality 模式生成多个候选并 Critic/Rerank；
- 会话后异步 consolidation。

**Acceptance**：

- 简单对话首条消息延迟不因 Slow Kernel 明显恶化；
- 复杂场景相对 single-call baseline 在盲评显著胜出；
- 每阶段 trace、模型、成本、attempt 可见；
- Slow Kernel 失败时 Fast Kernel 有真实可辨识降级，而非伪装成功。

---

### INNO-SELF-001 — Self Genome & Emergence V2

**Objective**：统一 Self Profile、Constitution、Model、Statement、Reflection 为可演化自我系统。

**Tasks**：

- 建立 Self Genome 分层和版本；
- 迁移现有 Self 数据，不丢失；
- 实现 experience → reflection → proposal → sandbox → judge → accept/reject；
- 运行 persona consistency、安全与体验评测；
- 支持版本 diff、灰度和回滚；
- 设计用户可感知的成长表现。

**Acceptance**：

- 演化有证据且不随机漂移；
- Constitution 改动使用更高门槛；
- 长对话 persona fidelity 不低于旧版本；
- 用户能在 Demo 中感知具体变化，而非只看后台字段。

---

### INNO-CAPSULE-001 — Capsule Genome Compiler

**Objective**：从授权 trajectory 编译结构化、高拟真的 Capsule Genome。

**Tasks**：

- 实现 Evidence Extractor、Style Distiller、Behavior Policy Miner、Persona Architect、Example Curator；
- 创建 ContextBuildManifest 和 Genome versions；
- 训练数据/held-out trajectory 隔离；
- A/B single prompt、long prompt、structured genome、compiler ensemble；
- 增量 sync 使用 Genome diff，避免每次整体重写。

**Acceptance**：

- structured genome 在 persona/style/behavior 至少两项优于当前路径；
- held-out trajectory 无泄漏；
- 每个 Genome 字段可追溯授权 evidence；
- 编译失败不覆盖当前可用版本；
- 可回滚到任一已发布 Genome。

---

### INNO-CAPSULE-002 — Resonance Agent Runtime

**Objective**：用 Planner/Speaker/Critic/Reranker 在多轮中运行 Capsule Genome。

**Tasks**：

- scenario-conditioned retrieval；
- Capsule state、visitor model、relationship state；
- Persona anchor refresh 与 drift detector；
- n-best generation 和多 Critic；
- 宽松 quota，移除僵硬固定轮数；
- 来源用户与访客数据隔离。

**Acceptance**：

- 长对话无明显 role confusion/echoing；
- 未授权事实泄露率为 0；
- 真人盲评胜过当前 CapsuleAgent；
- 访客打断、停止和恢复语义与 Aurora 一致。

---

### INNO-SIM-001 — Simulator Product & Fidelity Evaluation

**Objective**：把共鸣体能力验证为可服务其他 Agent/软件测试的高拟真 Simulator 资产。

**Tasks**：

- 实现 persona scenario API；
- 轨迹回放、行为预测和对话模拟；
- PersonaGym/BehaviorChain 风格任务；
- 来源用户、熟人和第三方盲评；
- 生成 simulator card：数据、适用范围、 fidelity 与限制。

**Acceptance**：

- 在 held-out 行为预测和真人识别上超过 generic persona baseline；
- 可控制 trait/scenario/state，但不破坏 identity fidelity；
- 输出可供外部测试 harness 调用；
- 不暴露来源用户原始数据。

---

### INNO-MATCH-001 — Multi-objective Resonance Matching

**Objective**：实现 Mirror/Complement/Growth/Safe Contrast/Serendipity/Moment/Dialogue Fit。

**Tasks**：

- 多空间召回和策略化权重；
- reciprocal 与 conversation-quality 预测；
- LLM Top-N rerank；
- 多样性重排和解释；
- 模拟与真人反馈闭环。

**Acceptance**：

- 每种策略返回可区分结果；
- 用户能理解匹配原因；
- 相比 cosine-only baseline，盲评共鸣/互补/意外指标提升；
- 隐私与授权过滤召回前生效。

---

### INNO-PSY-001 — Psychology Skill Runtime

**Objective**：建立 Aurora/用户均可调用的版本化心理能力运行时。

**Tasks**：

- Skill descriptor、registry、discovery、activation；
- 数据 scope、工具权限、schema、timeout、cost；
- sandbox 与 Capability Gateway；
- agent invocation 和 explicit user invocation；
- eval → review → publish 生命周期。

**Acceptance**：

- 未授权 Skill 不能读取记忆；
- 输出经过 schema 和 claim policy；
- 调用可重放、可禁用、可回滚；
- 新增 Skill 不修改 Aurora Core Prompt。

---

### INNO-PSY-002 — First Psychology Skills

**Objective**：落地首批有理论依据、可体验、可评测的 Skill。

首批建议：价值澄清、心理灵活性、关系视角转换、决策冲突、支持偏好发现。

**Acceptance**：

- 每个 Skill 有理论资料、输入/输出、禁用场景和 eval；
- 用户显式调用和 Aurora 自动调用都可演示；
- 不产生诊断性结论；
- 对比无 Skill baseline，在相关体验指标上提升。

---

### INNO-STAR-001 — Starfield V2

**Objective**：将记忆、画像、关系、Self 与未来意图映射为可探索的动态星空。

**Tasks**：数据 projection API、时间演化、星座聚类、关系边、矛盾/修订动画、创建 Capsule facet、匹配解释入口。

**Acceptance**：

- 所有视觉编码有数据含义；
- 用户可从星体追溯 Evidence 和变化；
- 60fps 目标设备体验；
- 可在 Demo 中展示一条完整变化轨迹。

---

### INNO-CLOUD-001 — Innovation Workloads on Kubernetes

**Objective**：让双核、Wake、Compiler、Simulator 与 Skill 成为真实可恢复工作负载。

**Acceptance**：

- Fast/Slow/Compiler/Eval 按不同指标扩缩容；
- Wake 与编译任务在 Worker 被删后恢复；
- 一条 trace 覆盖对话、慢核、记忆、Wake、Capsule；
- 成本 dashboard 区分模型/阶段/用户场景；
- 故障不会重复最终 bubble、主动消息或 Genome 发布。

---

### INNO-DEMO-001 — Aurora Alive × Resonance Wow Demo

**Objective**：实现 `08 §2` 的完整十分钟叙事。

**Acceptance**：

- 真实时间唤醒；
- 多消息与用户打断；
- 双核与 Self 演化可解释展示；
- 星空呈现 trajectory；
- 至少两种匹配策略；
- 高拟真共鸣体长对话；
- Pod/Worker 故障恢复；
- 盲评与技术 dashboard；
- 全程不依赖手工改数据库制造效果。

---

## 4. Stop Conditions

- 重构导致现有核心体验或测试退化但无 A/B 证据；
- 训练/编译数据泄漏到 held-out；
- 为追求拟真读取未授权用户数据；
- 将 LLM 自评当成唯一效果证据；
- 把更长 Prompt 当成 Capsule Compiler 完成；
- 用单向量替代多视图画像而无实验；
- 时间任务在多副本下重复发送；
- 用户打断后旧消息继续出现；
- Emergence 无证据直接改 Constitution；
- Psychology Skill 输出诊断性确定结论；
- 为架构整洁删除用户已经喜欢的生命感细节。

---

## 5. Human Review Questions

每个 Phase 结束后团队必须实际体验并回答：

1. Aurora 是否更像一个具体、持续存在的她？
2. 多消息、等待和被打断是否自然，而非刻意演示？
3. 主动联系是否令人惊喜和被在意，而非模板提醒？
4. Self 演化是否真实影响行为？
5. 画像是否准确到令人惊喜，同时没有被误解或被监视感？
6. 共鸣体是否能在未见场景下仍像来源用户？
7. 不同共鸣体是否明显不同？
8. 匹配是否产生了相似、互补和意外三种不同价值？
9. 心理 Skill 是否明显优于普通聊天？
10. 技术复杂度是否转化为用户可感知的体验？
