# Inner Cosmos 单会话持续 Goal 模式执行协议

> 文档性质：L1-EXEC 持续执行协议；补充 `goal-objective.md`、`12` 与 `16`
> 状态：AUTHORITATIVE
> 生效日期：2026-07-15
> 目标：在同一个顶层 Codex 会话中持续实现、验证、修复和收敛完全体，不再以每个提交或工作包结束会话

## 0. “单会话”是什么意思

单会话指同一个用户可见 Goal 任务持续存在，直到达到本文的终止条件。它允许并鼓励：

- 在会话内部连续执行许多实现批次、提交、测试、审查和返工。
- 上下文自动压缩后从持久化状态继续，不把压缩当作新项目或停止理由。
- 在权限允许时使用多个 Agent、独立 worktree 和并行实验，但主 Agent 始终维护唯一总目标和事实账本。
- 在 commentary 中定期报告进展；内部里程碑不得用 final answer 把任务交还给用户。

“不要停”不会扩大外部权限。Agent 仍不得伪造密钥轮换、专家审阅、真实用户反馈、商店签名、AWS 账户操作或法律批准。

## 1. 唯一终止条件

主会话只能在以下两种状态之一结束。

### 1.1 `COMPLETE`

- `docs/goal/complete-product-acceptance.yml` 的全部必需项均为 `PASS`。
- G0—G9 全部关闭，证据来自当前 commit 和对应环境。
- 五场产品战役均完成真实体验、AI 质量、工程、部署和独立验收。
- 不存在隐藏 P0/P1、未登记数据风险或用 Mock/静态页面冒充完成的能力。

### 1.2 `RELEASE_CANDIDATE_BLOCKED_BY_HUMAN_GATE`

只有同时满足以下条件才允许结束：

- 所有 Agent 能自主完成的代码、数据迁移、UIUX、AI runtime、评测脚手架、测试、文档、IaC 和演练资产均已完成。
- 所有技术阻塞都已解决；剩余项严格属于 `goal-objective.md` 定义的人类权限、真实设备、真实账号、法律/专家/用户审阅。
- 每个剩余门禁都有一页不含秘密的可执行清单、预期输入、验证方法、失败回退和负责角色。
- 仓库处于可复现发布候选状态，而不是用“需要人类”掩盖尚未实现的功能。

某个 Provider 密钥、设备或 AWS 账户不可用时，Agent 必须先继续所有不依赖它的工作；不得因单一门禁提前结束整个会话。

## 2. 会话启动与恢复

每次启动、自动续跑或上下文压缩恢复后，严格执行：

1. 阅读 `goal-objective.md`、`对齐文档/README.md`、本文、`16` 和 `docs/goal/single-session-state.yml`。
2. 检查当前分支、HEAD、工作树、运行进程、最近提交、账本和 evidence。
3. 未提交工作优先视为正在进行的资产；确定所有者和意图，继续完成或隔离，绝不覆盖。
4. 重新验证最可能漂移的事实，不机械相信旧摘要。
5. 从机器状态文件的 active front 继续；若已完成或失效，则按本文的优先队列自动选择下一项。

主 Agent 必须在每个检查点更新 `docs/goal/single-session-state.yml`，使上下文压缩、进程中断或 Agent 交接都不会丢失方向。

## 3. 永续执行循环

```text
读取当前事实与单会话状态
  → 收口已有未提交工作
  → 选择最高价值、无外部门禁的产品差距
  → 更新 Experience Contract / acceptance mapping
  → 实现一个可体验批次
  → focused tests + 场景 smoke + AI eval
  → 自审并修复 P0/P1/P2
  → 到达风险检查点时运行跨模块/全量门
  → 生成 evidence、更新账本、提交
  → 更新 single-session-state
  → 不发送 final，立即进入下一差距
```

每次提交只是恢复点，不是任务完成。Builder evidence 只是候选证据，不是战役终点。

## 4. 自主优先级裁决

所有待办按以下顺序选择；同级优先能形成完整用户时刻的工作。

1. 当前工作树中已经开始但尚未提交的实现，避免并行半成品堆积。
2. P0/P1 安全、数据损坏、所有权、生产时间语义和迁移阻断。
3. 阻断核心 Experience Contract 的体验或 AI 质量问题。
4. 把已存在的后端能力整合为五空间、跨端、动态而连贯的真实产品。
5. 建立真实 Provider、纵向 trajectory、pairwise 和失败分析，证明效果而非代码数量。
6. 完成 Campaign B—E 的 Experience Contract、盲体验和独立验收。
7. 关闭 OpenAPI、模块边界、数据权利、运行角色、观测、恢复等生产工程缺口。
8. 收口 Academy EKS、local-complete、commercial-sg、最终演示与追溯。
9. 最后处理不影响体验、风险或验收的内部洁癖。

不得因为基础设施更容易自动验收，就长期推迟 AI 效果与 UIUX；也不得为了视觉演示跳过会造成数据错误或无法部署的工程问题。

## 5. 从当前状态出发的产品战役队列

### Front 0 — 先收口正在进行的 Campaign A 体验评测

- 保留并完成当前未提交的 Living Aurora scheduler 浏览器旅程、连续体验评分器和 Provider pairwise v3。
- 修复审查发现的移动 API Base 信任校验，以及 Capacitor 与生产 API 的真实认证合同。
- 自动化能完成的全部到位后，把真实 Provider、双人盲评、APNs/FCM 和设备收据移入明确的人类门禁清单。

### Front 1 — 统一产品体验，而不是继续扩张单页

当前 React 已承载大量功能，但 `AuroraApp.tsx` 仍是千行级单组件和单一长页面。这证明能力可连接，不代表五空间产品完成。下一步必须：

- 建立 Today/Aurora、Inner Cosmos、Resonance、Letters/Connections、Me/Controls 五空间路由与 AppShell。
- 将对话、Self、记忆、心理 Skill、共鸣体和慢信拆为领域组件、状态模型和可测试旅程。
- 重新设计跨空间过渡、层级、移动导航、空/错/等待/恢复状态和统一设计系统。
- 逐旅程替换旧物理页面，消除互相竞争的产品入口；不机械保留旧 UI。

### Front 2 — Campaign B：Living Inner Cosmos

- 建立独立 Experience Contract 和连续用户场景。
- 把 claim、provenance、纠正、版本、检索、星空、下游传播和删除做成用户能理解的闭环。
- 用真实/标注数据评测 claim precision、retrieval relevance、冲突、遗忘和传播，而非只通过 CRUD 测试。

### Front 3 — Campaign C：Resonance Network

- 建立 Experience Contract；完成 Capsule planner/retriever/speaker/critic/reranker，而不仅是 Genome 表与沙盒。
- 以长 Prompt baseline 做真实保真、泄露、风格、边界和纵向稳定性盲测。
- 完成 simulator 资产与隔离数据合同；把匹配、对话、慢信、双向连接和撤回做成一条连贯旅程。

### Front 4 — Campaign D：Psychology Skills & Trust

- 将三个首批 Skill 从确定性原型推进到真实内容质量、风险场景、引用、版本和双语体验。
- 完成专家审阅包、危机/不适用/拒绝/撤回旅程和新加坡本地资源；专家签字仍是人类门禁。

### Front 5 — Campaign E 与 G2/G8/G9 收口

- 完成 PWA/Capacitor 真实认证、离线、Push、深链、语音和设备体验。
- 关闭 OpenAPI、可执行模块边界、运行角色、可靠事件、观测、备份恢复、canary/rollback。
- 保持 local-complete 完整体验与 Academy EKS 云原生证据同一 commit；完成商业新加坡 IaC 候选。
- 构建最终 8—12 分钟演示、全产品 E2E、非作者可用性和最终追溯矩阵。

这些 Front 不是固定瀑布；依赖允许时可并行，但主 Agent 必须保证至少一个用户可感知 Front 持续推进。

## 6. 工作树、并行与提交纪律

- 开始前列出 dirty 文件并判断所属 Front；不同 Agent 使用独立 worktree，禁止共享未提交文件所有权。
- 主工作树已有修改时，优先完成它；需要审查稳定 commit 时使用 detached worktree。
- 每个提交应是可恢复的完整检查点，可包含一个战役的多个紧密相关批次；不再为了追求微小提交把体验拆碎。
- 只 stage 自己负责的文件，提交前检查 cached diff；不清理、不回滚其他 Agent 的工作。
- 每个检查点更新 evidence、acceptance ledger 和 single-session state，但不得把未独立验收项升级为 `PASS`。

## 7. 验证频率

沿用 `16` 的风险分层，不在每个小改动重复全部 700+ 测试：

- 日常批次：类型/编译、focused tests、目标场景 smoke、小型固定 AI eval。
- 跨域接口、schema、身份、安全、调度、迁移：增加相应集成、并发、负向和故障测试。
- Experience Contract 首次贯通或准备组员体验：选定 Playwright、真实 Provider eval、视觉/a11y/性能检查。
- 战役候选、依赖/基线/生产架构变化、合并多个批次、发布候选：运行全量 Maven、前端、AI lab、Playwright、扫描、生产 smoke 和相应 K8s 验证。

若全量门失败，修复并重新运行受影响门；不得通过降低断言、删除测试或把失败改成 known limitation 继续前进。

## 8. 阻塞处理

技术阻塞出现时：

1. 收集准确错误和最小复现。
2. 尝试至少三类合理方案或验证替代路径，而不是重复同一命令。
3. 将局部阻塞登记到状态文件，继续其他无依赖 Front。
4. 只有当所有剩余工作都依赖同一个不可获得的人类输入/外部状态，才允许进入终止审计。

测试慢、任务大、设计困难、暂时缺文档或需要研究都不是停止理由。

## 9. 状态汇报规则

- commentary：简短报告当前 Front、刚取得的证据、发现的风险和下一步；不等待用户逐项确认。
- checkpoint：提交、更新机器状态，继续执行；不发送“本轮完成，请给下一轮任务”。
- final：只在 `COMPLETE` 或 `RELEASE_CANDIDATE_BLOCKED_BY_HUMAN_GATE` 时发送，并包含当前 HEAD、全量证据、剩余人类清单和真实状态。
- 用户中途提出新要求时，将其并入当前总目标；只有明确覆盖原目标时才改变执行方向。

## 10. 防止“永远忙碌但不完成”

持续会话不等于无限加功能。每个 Front 必须绑定验收项和删除条件：

- 能关闭现有 acceptance gap、P0/P1 或关键体验债务才进入主队列。
- 新技术或新功能必须证明它比完成既有战役更有价值。
- 每个战役达到目标后冻结范围，转向独立验收和剩余 gate，不继续无边界美化。
- 每周或每 5—10 个检查点执行一次 completion audit，合并重复工作，删除无价值兼容层和过期待办。

单会话 Goal 的成功标准不是“Codex 一直运行”，而是它不再依赖人类微观调度，能够持续把实现广度收敛为一个真正完整、可体验、可验证、可部署的产品。
