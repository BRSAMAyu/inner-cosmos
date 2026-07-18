# Track A Goal Prompt

在 `D:\code\inner cosmos` 先同步负责人指定的最新 `main`，确认当前 HEAD 包含最低交接提交 `7e0688308292cd9d41552c98f576d25681738be0`，并与 Track B 使用完全相同的 HEAD；把 `git rev-parse HEAD` 写入 Track A 状态/契约文件后创建并工作在 `codex/track-a-living-intelligence`。先完整阅读 `AGENTS.md`、`goal-objective.md`、`对齐文档/README.md`、`对齐文档/19-双轨并行完全体收敛与交接计划.md`、`对齐文档/20-当前状态重对账与完全体差距基线.md` 与 `docs/tracks/TRACK-A-LIVING-INTELLIGENCE.md`，再检查当前代码、证据和真实运行状态。`97500a3` 只是实现审查基线，不是建分支点。

你的唯一目标是完整实现 Track A：把 Living Aurora、长期记忆/用户画像、动态高保真共鸣体与画像向量匹配、至少三项心理 Skill、全派生数据权利和 AI 可观测真正做强，并通过可复现的离线评测、真实 Provider 冻结集和失败样例迭代证明效果。不要只“验证优势不存在”；如果效果不够好，持续分析并改进 runtime、harness、检索、Genome、critic/rerank，直到达到任务书的效果门槛或只剩真实人工评审。不得删除或保守降级 proactive、Self/Constitution/Emergence、关系演化、多消息/打断/停止等创新能力。

严格遵守 Track A 文件所有权，不编辑 `web/**`、`deploy/**` 或全局账本；前端契约变化写入 `docs/goal/tracks/track-a-contract-deltas.yml`。密钥只允许环境变量/本机忽略文件注入，绝不进入 Git、日志、截图或 evidence。采用持续 Goal 循环推进，在每个可恢复纵向 checkpoint 提交 Git，更新 `track-a-status.yml` 和 `evidence/track-a/`；小步运行相关测试，阶段和 PR 前运行任务书规定的完整门禁。允许根据真实发现重构或调整方案，但必须记录用户价值、证据、回滚和跨轨影响。

不要在一次提交、一次绿测、上下文压缩或普通困难处结束。直到所有机器可执行 Track A 验收完成、工作树干净、证据与 PR 描述齐备，或只剩不可伪造的人工权限/专业评审门禁时，才输出最终交接结果并创建/推送 PR（若账号权限允许；否则给出精确命令和阻塞）。
