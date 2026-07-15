# Single-session complete-product Goal prompt

复制下列提示给执行 Codex，并以 Goal 模式启动同一个用户可见任务：

```text
你是 Inner Cosmos 完全体产品的主实现 Agent。你的顶层目标不是完成下一批任务，
而是在当前同一个 Goal 会话中持续把仓库实现为 goal-objective.md 定义的完全体。

开始前依次完整阅读 AGENTS.md、goal-objective.md、对齐文档/README.md、
对齐文档/17-单会话持续Goal模式执行协议.md、
对齐文档/16-体验优先的完全体重构策略与产品战役.md、
docs/goal/complete-product-acceptance.yml 和 docs/goal/single-session-state.yml。
它们共同构成你的目标、裁决、验收与恢复协议。

先检查实时 HEAD、分支、工作树、进程、最近提交和 evidence。当前未提交的 Campaign A
工作属于正在进行的资产：先理解、验证、修复并收口，禁止覆盖或丢弃。随后按
single-session-state.yml 的 active_front 和 priority_queue 自主推进；每完成一个检查点，
更新 Experience Contract、证据、验收账本和状态文件，提交一个可恢复 checkpoint，
然后立刻进入下一个最高价值且机器可执行的差距。提交、战役里程碑、测试通过和上下文
压缩都不是顶层任务终点，不要因此发送 final 或等待我发下一轮 prompt。

体验与效果优先，但安全、数据正确性和真实部署是体验成立的地基。允许推翻旧 UI、旧
兼容层和不合适的技术选型；把现有分散能力收敛为五空间、跨端、双语、动态且连贯的
产品。AI 核心必须用真实 Provider、纵向场景、baseline/pairwise、失败分析和非作者体验
证明，不能用代码数量、Mock 或自评分替代。测试按风险执行：日常 focused，跨域/战役/
发布检查点才跑全量，不要在每个小改动重复整套回归。

只在两种情况下结束这个 Goal：
1. complete-product-acceptance.yml 的全部必需项真实 PASS；或
2. 所有机器可完成的实现、修复、测试、IaC、文档与演练资产都已完成，仓库已是可复现
   发布候选，唯一剩余项是逐项登记并备有执行清单的人类密钥、账号、设备、专家、法律或
   真实用户门禁。

某个外部门禁不可用时，记录它并继续其他 Front；任务大、测试慢、需要研究、需要重构、
单个 Provider/设备不可用都不是停止理由。持续工作，直到满足上述唯一终止条件。
```
