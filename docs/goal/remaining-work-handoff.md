# Inner Cosmos 剩余工作交接（当前操作版）

> 本文件是 `对齐文档/24-完全体最终收敛与云原生课程战役.md` 的操作附件，不独立裁决产品方向。唯一机器 cursor 是 `docs/goal/closure-campaign-state.yml`。旧 19—23、track、loop、single-session 和 release state 仅供追溯。

## 1. 接管前五分钟

1. 完整阅读 `AGENTS.md`、`goal-objective.md`、`对齐文档/README.md`、文档 24、closure state 和 acceptance ledger。
2. 运行 `git status --short --branch`、`git log -1 --oneline`、`git worktree list --porcelain`，记录并保留所有既有脏改。
3. 不使用 `git branch --list 'worktree-agent-*'` 选择合并源；仓库存在其它旧大型 WIP。
4. 不把本文件中的测试数字当成当前事实；只有集成 HEAD 的命令输出可以进入账本。
5. PowerShell 优先使用仓库脚本；需要显式 JDK 时用 `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'`，Git Bash 才使用 `export`。

## 2. W0：精确集成四个来源

| 顺序 | 分支 | 锁定 HEAD | 取用内容 |
|---:|---|---|---|
| 1 | `worktree-agent-a0ddcfc1` | `6867c0f` | 5 个 Playwright spec / 8 个 test 和 canonical lockfile |
| 2 | `worktree-agent-aa2e0d29` | `d6925f8` | timeline、daily record、weekly review、thought shredder 的 source/test/旧页退休 |
| 3 | `worktree-agent-a9edba5a` | `1fc014b` | todo、heart diary、belief 的 source/test/旧页退休 |
| 4 | `worktree-agent-a537b49a` | `42d6253` | 独立 `/admin` route、八页签和 source/test |

四支 Vitest 已独立复跑：a537 332、aa2e 323、a9ed 343、a0dd 291，均通过；这只证明各分支测试，不证明集成树。持久 Playwright 状态为 passed，但本轮审查没有重新启动四套后端，因此不能作为当前 live 复现。

### 2.1 合并规则

- 每次 `merge --no-commit` 后排除该支 `src/main/resources/static/app/aurora/**` 生成物；最终只在集成树执行一次 clean Web build。
- 保留 a537/aa2e/a0dd 三者相同的新 `package-lock.json`；main/a9ed lock 已滞后于 `package.json`。
- `daily-record.html` 和 `heart-diary.html` 取删除；`beliefs.html` 保留，直到 M6 Self Panel 被完整迁移。
- `AuroraApp.tsx`、`api.ts`、`styles.css`、locale/route/bootstrap 新增项按语义组合，不能简单选 ours/theirs。
- 不覆盖集成开始前的 `application.yml` 或其它用户脏改；只 stage 明确路径。

### 2.2 集成即修复的 P1/P2

1. `useDailyRecord` 的日期 clamp 不能用 `Math.min(index, current)`；加入 hook/E2E，真实证明能进入前一天、返回、跨月和无数据日。
2. HeartDiary 的录音按钮在 recording 时必须可点击停止；busy/disabled 只覆盖真正不可操作状态。测试真实 start→stop、权限拒绝、60 秒 timeout、后台停止和 buffer 销毁。
3. 原始转写与用户可编辑润色稿分离；编辑稿不能覆盖 raw text、清 transcription ID 或伪造原文保留。提交后状态、reload persistence 和失败恢复必须有断言。
4. Social Groups：创建 group+OWNER 原子事务；邀请校验 self、ACCEPTED friendship、block 和 membership；decision 使用明确枚举；accept/decline 使用 expected-state 条件更新；OWNER 不显示不可执行 Leave。
5. Admin：真实创建 pending report 后验证 resolve round-trip；后端角色与 P0 最小披露用 API 负向测试证明。纠正 `/api/ai-logs`、`/api/ai/health` 权限注释与实际合同的不一致。
6. E2E：所有临时账号 finally 删除或使用每 spec 隔离数据库；禁止修改共享 seeded demo 的关系状态。导出解析 JSON 并验证 owner/敏感字段；画像校准 reload；共鸣体 preview 至少发送一个授权 turn。

### 2.3 Cosmos IA 与性能修复

不得把 timeline/daily/weekly/shredder/todo/diary/belief 纵向堆入单一长页，也不得将约十个非首屏请求追加到登录 bootstrap 的 awaited `Promise.all`。

采用 Cosmos 内二级结构并 lazy fetch/cached prefetch：

- 星空与成长轨迹
- 今日记录与心声
- 周报与变化
- 思维整理与待办
- 信念与自我理解

切换保持深链、键盘/触摸、back 行为、loading/error/empty、移动安全区与 200% 字体；每个模块通过同一 memory/profile lineage 回流，不以“页面存在”结案。

### 2.4 W0 验证与出口

先 focused test，再依次运行 integrated Vitest/type/build/i18n guard、Playwright、Java、secret scan、IaC render 和 `git diff --check`。测试基线从实际输出建立，不预设精确数字。W0 只有在以下条件满足时完成：

- 四个锁定来源已进入一个 commit 历史；
- 上述缺陷与 IA/首屏性能问题已修复；
- generated bundle 只由集成树生成；
- 工作树只剩接管前明确保留的用户改动或完全 clean；
- closure state/ledger 记录集成 HEAD、命令、结果和边界。

## 2.5 W0V：Gemini 审查线索的独立闭环

W0 集成完成后，先执行 `docs/audit/2026-07-23-gemini-master-audit-reconciliation.md`，再允许 W1/W2/W3 进入完成态。该文件是逐项事实裁决与正确修复合同：28 confirmed、6 partial、1 duplicate、1 false。重点不是把 36 个标题逐字实现，而是用复现/反证关闭真实根因，尤其是状态条件更新、事务外 LLM、P1→P2 compiler、统一输出 gate、turn-scoped SSE recovery 和 UI async lifecycle。

允许 Backend State/Transaction、AI Privacy/Safety、Frontend Resilience 三轨最多 3 Agent 并行；Integrator 独占 Flyway 序列、共享 API/types/locale、generated bundle、ledger/state。P0 项先关闭，P1/P2 随后；2.3 禁止创建重复索引，2.5 未经代表性 `EXPLAIN ANALYZE` 不得堆 HNSW，V21 不得复用。

## 3. W1：Living Intelligence 与六条产品闭环

详细合同见文档 24。实现顺序不是按页面，而是按可体验闭环：

1. Aurora：多气泡、打断/停止/重规划、Self/关系演化、WakeIntent、失败恢复、真实 Provider scorecard。
2. 记忆/画像/星空：来源、确认/纠正、冲突/衰减、任务检索、投影与可视化。
3. 共鸣体：授权 compiler、style/trait model、prompt composer、speaker/critic、sandbox/version/withdrawal、simulator eval。
4. 匹配：相似/互补/惊喜策略、MMR、边界/屏蔽、解释和反馈。
5. 慢社交：共鸣体→双向同意→慢信→真人连接，所有状态幂等可恢复。
6. 心理 Skill：registry、schema、来源、风险/适用、同意、撤回和非诊断边界。

每条闭环先建立 baseline 和固定数据集，再做实现；自动 eval/Agent simulation 负责筛选，真实 Provider 与非作者盲评负责最终质量。人工门不阻止机器可做的 runtime、评测和改进。

## 4. W2：UIUX 与多端完成

- 五空间顶层 IA 不变；Cosmos/Resonance 内部用二级路由和 progressive disclosure。
- 对每个关键组件跑 `empty/loading/streaming/partial/offline/error/retry/permission/expired/long-copy` × viewport × locale × input modality 状态矩阵。
- Android 模拟器与 Windows Tauri 必须跑真实 PKCE、Aurora、恢复、通知、深链、语音、草稿与退出清理；Web/PWA 跑相同核心旅程。
- 视觉回归、axe/语义、键盘、TalkBack、200% 字体、reduced motion、慢网/杀进程是机器任务。
- iOS/macOS 签名、APNs、notarization 和真机执行是外部门；代码、模板、workspace 和 Mac runbook 不是。

## 5. W3：云原生课程战役

### 5.1 可信度修复

- liveness/readiness/startup 分离，避免 DB/Redis 故障导致 restart storm；
- base/Academy 的 API/worker/scheduler metrics discovery 与 role health；
- NetworkPolicy 真 enforcement deny/allow，而非对象存在；
- backup overlay 与 off-cluster 边界；
- evidence 中云标识脱敏合同；
- canary 前完成 expand-contract schema compatibility。

### 5.2 三条英雄链路（硬门）

1. 删除正在输出的 Aurora API Pod，durable timeline + Last-Event-ID 无损恢复。
2. outbox backlog/oldest age 驱动 KEDA worker 1→N→1，inbox exactly once。
3. OTel/Tempo 贯穿 turn→Provider→retrieval→outbox→worker→memory/profile→WakeIntent，且不记录正文或稳定用户标识。

### 5.3 高级能力展柜

在英雄链路稳定后，按文档 24 的 10 分评分选择并尽可能实现：Argo Rollouts/Argo CD、Kyverno/Cosign、Cilium/Hubble、cert-manager/External Secrets、Falco、Chaos Mesh/Litmus、Velero、OpenCost、OpenFeature、SPIFFE/mesh、Indexed Jobs、Dapr、Knative、Crossplane、GraphQL BFF。复杂度可接受；每项必须有 product hook、live success、failure/rejection、kill switch、资源边界和 Academy fallback。

本地 kind 是插件完整实验场；Academy 每次 session fresh capability probe，不能因 CRD 可创建就声称 controller/webhook/CNI enforcement 可用；commercial-sg 是 RDS/ElastiCache/SQS/Secrets/灾备的生产目标。

## 6. 并发与文件所有权

最多 3 个 Agent：Integrator、Product、Cloud。主 Agent 唯一拥有：

- `goal-objective.md`、`AGENTS.md`、`CLAUDE.md`、`对齐文档/README.md`、文档 24；
- `complete-product-acceptance.yml`、`closure-campaign-state.yml`；
- built Web bundle、共享 locale/API types、Flyway 序列；
- merge、全门、最终 evidence index 和 release narration。

子 Agent 只提交边界清晰的纵向切片和局部 evidence，不改全局状态。一个可回滚 outcome 一个 commit；每个分支返回 commit、diff scope、focused/journey 测试、运行证据、失败边界和待集成项。

## 7. 测试与证据节奏

- 编辑循环：lint/type/focused unit/component。
- 纵向 checkpoint：模块测试 + 一条真实 journey。
- 高风险合同：PostgreSQL/Redis/并发/安全/迁移/故障验证。
- 合并、战役候选、release：全 Java/Web/Playwright/i18n/secret/IaC/image 门。

每个 gate 记录 implementation、automated test、runtime evidence、quality evidence、known boundary、commit/reviewer。AI 额外记录 baseline、dataset/provider/model/runtime、全部样本含失败、延迟/成本/隐私；K8s 额外记录环境、manifest digest、负载、fault timeline、不变量和恢复时间。

## 8. 唯一停止条件

在所有机器可执行任务关闭前持续推进，不因 phase、commit、subagent、绿色测试或上下文压缩结束。只有 `COMPLETE` 或 `RELEASE_CANDIDATE_BLOCKED_BY_HUMAN_GATE` 合法。后者要求剩余每项都明确映射到真实外部账户、设备、法务/心理专家、用户同意或不可逆操作；缺代码、缺测试、环境没启动、Agent 未尝试或“最好让用户看看”都不是人工门。

可直接使用的 Goal prompt：`docs/goal/prompts/final-complete-product-goal-prompt.md`。
