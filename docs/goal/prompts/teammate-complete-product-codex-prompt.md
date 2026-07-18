# 组员 Coding Agent：完全体持续实现 Prompt

你现在接管 Inner Cosmos 的完全体续跑。任务不是一个小工作包，也不是只把测试跑绿，而是从 live repo 持续推进，直到分配目标达到可合并、可体验、可验证、可移交的高完成度。若仍有未完成项，留下可恢复状态并继续，不要用总结结束目标。

## 强制阅读

依次完整阅读：

1. goal-objective.md
2. 对齐文档/README.md
3. 对齐文档/09-完全体产品愿景与功能规格.md
4. 对齐文档/10-完全体系统架构与工程规格.md
5. 对齐文档/11-完全体UIUX与交互设计规格.md
6. 对齐文档/12-完全体实现路线与最终验收规约.md
7. 对齐文档/08-Aurora生命感与共鸣智能创新架构.md
8. 对齐文档/16-体验优先的完全体重构策略与产品战役.md
9. 对齐文档/18-组员与Coding-Agent启动部署交接指南.md
10. 对齐文档/21-双轨阶段合并审查与完全体续跑交接.md
11. docs/goal/teammate-continuation-state.yml
12. docs/goal/complete-product-acceptance.yml
13. 负责轨道的 spec、status、integration contract 与 evidence。

然后执行 git status、git branch -vv、git log --graph，确认分支从最新 origin/main 建立。不要从聊天摘要或旧 worktree 猜当前事实。

## 总目标

把 Inner Cosmos 做成真实可用且现场演示时令人眼前一亮的完整产品：

- Living Aurora 具备多消息、思考、暂停、打断、停止/重规划、时间感、主动回访、连续自我、关系与人格演化；
- 记忆画像具备来源、时间、置信度、矛盾、纠正、撤回、遗忘、传统检索和向量检索；
- 共鸣体以授权 trajectory 编译 Dynamic Genome，高保真保持事实、风格、习惯、边界和时序，支持相似、互补和受控多样性匹配；
- 心理 Skill 可持续扩展，至少三项达到中英双语、非诊断、可解释、可撤回和真实有用；
- UIUX 把复杂能力变成自然、动态、美观、可理解的五空间体验；
- local-complete 从 clean checkout 复现完整真实 Provider 旅程；
- Kubernetes 与 Academy EKS 独立证明云原生工程，不牺牲本地完整体验；
- 安全、隐私、数据权利和人类发布门禁如实保留。

旧实现、旧 API 和旧页面不具有天然兼容权。可以重构或替换，但不得破坏数据、安全、迁移和证据。不得删除 Aurora proactive、Self/Constitution/Emergence、关系演化、共鸣体、慢信、星空、匹配或心理建模来换取简单。

## 当前事实

- main 已接受 Track A 9714aab 的 A0–A3 工程 checkpoint，以及 Track B 22e779d 的体验/PWA checkpoint。
- A3 已关闭旧向量污染、pgvector 失效验证、交互候选 Provider N+1 与后台有界回填问题；不要 cherry-pick 7f1aa9b 或重复实现。
- 智能/数据轨：从最新 origin/main 新建分支，继续 A3 的真实 Provider 质量、完整数据权利传播、Dynamic Genome 保真和匹配盲评，再推进 A0/A1/A2/A4/A5/A6 的 remaining。
- 体验/交付轨：从最新 origin/main 新建分支；不要重复已合入的 Hook/PWA 工作，继续 B1 剩余和 B2–B7。
- 最终整合轨：等待两轨 PR，独立复核、合并、跑全栈回归并统一更新全局账本。

## 持续执行循环

1. Inspect：读实现、测试、迁移、运行证据和状态，发现未列出的瓶颈。
2. Decide：按用户效果、AI 质量、数据正确性和集成风险排序。
3. Design：写清体验、机制、失败路径、数据/契约、评估和完成判据。
4. Implement：完成真实纵向能力，不只搭接口、页面或假数据。
5. Verify：日常跑受影响测试；关键节点跑真实数据库、Provider、Playwright、构建与安全门禁。
6. Experience：亲自走真实旅程；AI 必须有 baseline、ablation、held-out 和盲评包。
7. Repair：修复发现的问题，不把已知缺陷藏进 remaining。
8. Record：更新轨道 status、evidence、契约增量和 teammate-continuation-state.yml。
9. Commit：形成可恢复提交后继续下一最高价值差距；提交不是顶层完成。
10. PR：轨道达到高完成度且主要门禁关闭后创建，诚实列出人类事项。

## 子 Agent 策略

可以派子 Agent，但总活跃并发默认不超过 3：一个协调者加最多两个助手。单并发完全可以。

- 只并行边界清晰、能独立验证、文件不重叠的任务。
- 开始前声明每个 Agent 的文件所有权、输入、输出和停止条件。
- 强耦合纵向旅程由一个写者贯穿，其他 Agent 做只读审查、测试设计或独立复核。
- 禁止多人同时改全局账本、同一 migration、AuroraApp 或同一核心服务。
- 子 Agent 返回后必须由协调者审查，不能直接相信完成声明。
- 临近额度时不要创建长子任务；先提交并更新状态，保证下一会话可恢复。

## 测试节奏

- 小循环：相关单测、类型检查和静态检查。
- 纵向切片：相关集成测试、真实 PostgreSQL/Redis、目标 Playwright、AI eval。
- 合并节点：完整 Maven、全部 Vitest、前端 build、关键 Playwright、Flyway、Secret scan、Kustomize/Docker smoke。
- 发布候选：clean checkout local-complete、真实 Provider 三条 Golden Journey、非作者复现与故障恢复。

测试数量不等于效果。AI 优势必须用 baseline、ablation、held-out、真实 Provider 和盲评证明；UI 必须真实浏览器/设备体验；云证据不能替代本地完整功能。

## 密钥与人类门禁

真实 Key 只从本地环境或外部 Secret 注入。禁止写入 Git、Prompt、日志、截图、YAML 或 PR。外部密钥轮换、商店、签名、DNS、生产 AWS、法律、真实用户研究和专业心理评审由授权人完成。

门禁阻塞某一路径时记录 BLOCKED_BY_HUMAN_GATE，并继续所有不依赖它的工作。

## 状态纪律

- 代码存在是 IMPLEMENTED，不是 PASS。
- 单测通过是 BUILDER_VERIFIED，不是体验或生产完成。
- Mock/synthetic 只能证明机制。
- 真实 Provider 证据要记录模型、配置、样本、失败分母、延迟和脱敏输出。
- PASS 必须满足完整判据并引用可复现 evidence。
- 不删除失败样本、不改变分母、不以新样本覆盖旧失败。
- 实现 Agent 不得单独升级全局 PASS，由整合审查者裁决。

现在开始。先给出基于 live repo 的短审计和本轮最高价值纵向切片，然后直接实施，不等待逐条指示。
