# Claude 接管：W2 / W4 完全体收敛交接

> 交接时间：2026-07-23。W3 已由 Codex 完成并现场验收；本文件是当前执行入口，
> 不替代 `goal-objective.md`、对齐文档 24/25、closure state 或 acceptance ledger。

## 1. 接管结论

`docs/goal/closure-campaign-state.yml` 是唯一机器 cursor。当前：

- W0/W0V：以账本当前状态为准，不重复合并历史 worktree。
- W1：仍有量化效果、残余架构边界与非作者评审项；先重新核对账本，不接受旧注释中的
  测试总数或“已完成”口头结论。
- W2：待继续完成完整体验、多端真实旅程和 UIUX 细节收敛。
- W3：**COMPLETE，冻结为已验收能力，不得重写或降级。**
- W4：被 W1/W2 阻塞；二者关闭后执行全局收敛、非作者验收和演示发布包。

不要再次执行旧 19—23、`docs/tracks/`、旧 teammate/single-session state，也不要通配符
cherry-pick 任何 worktree 分支。它们只保留为历史证据。

## 2. W3 已完成且不应重复做的内容

三条强制英雄链路均为 PASS：

1. `CN-ZERO-LOSS-DRAIN`：真实 Postgres+Redis 下，常规 Pod 删除与 JVM SIGKILL 均有现场证据；
   abrupt failure 会被 reconciliation 标记为 `INTERRUPTED`，重连显式结束而非悬挂。
2. `CN-EVENT-DRIVEN-AUTOSCALING`：KEDA 2.20.1 根据 outbox ready/oldest age 实测
   `1 -> 6 -> 3 -> 1`；worker 删除后租约恢复，inbox 重复组为 0。
3. `CN-OTEL-SEMANTIC-TRACE`：Collector + Jaeger 实测跨
   API/provider/retrieval/outbox/worker/memory/profile，并有真实 WakeIntent scheduler span；
   禁止敏感属性键为 0。

证据：

- `evidence/w3/CN-ZERO-LOSS-DRAIN-001/`
- `evidence/w3/CN-ZERO-LOSS-DRAIN-002/`
- `evidence/w3/CN-EVENT-DRIVEN-AUTOSCALING-001/`
- `evidence/w3/CN-OTEL-SEMANTIC-TRACE-001/`
- `evidence/w3/CN-CREDIBILITY-001/`

本次 W3 最终门禁：

```text
./mvnw.cmd test
1115 tests, 0 failures, 0 errors, 1 skipped

scripts/scan-secrets.ps1
0 findings

kind-full render: PASS
academy-eks render: PASS
git diff --check: PASS
```

可选的 Argo Rollouts、Kyverno、Cilium、Cosign 等仍在 ledger 中保持 `UNASSESSED`。这不是
W3 三条硬门的缺口。只允许在 W4 根据“课程得分收益 / 现场可证明性 / 时间”选择一至两个，
不得为了数量安装无人使用、无失败证据、无 kill switch 的 chart。

## 3. Claude 的第一工作面：关闭 W1 残余

先逐项读取 acceptance ledger 中所有 W1 的 `IN_PROGRESS/PARTIAL/UNASSESSED`，再形成最多一页
执行清单。优先级：

1. 真实 Provider 效果：对 Aurora 双核、记忆检索、共鸣体 simulator、匹配解释与心理 Skill
   使用固定数据集、失败包含分母和可复现实验；不以“调用成功”代替效果优势。
2. 剩余架构边界：消除 ledger 仍列出的 controller/service/mapper 例外；不得为模块化而复制
   业务逻辑。
3. 完整核心闭环：Aurora → 记忆/画像 → 共鸣体 → 匹配/慢社交的来源、授权、纠正、撤回和
   反馈闭环必须由真实 API journey 证明。
4. 只有真实 Provider 密钥、盲评人员或专家签字才可标为人工门；代码、fixture、自动 eval、
   Mock 基线和结果报告均是机器任务。

每个改进必须同时回答：用户能感知什么、技术壁垒是什么、如何测得更好、失败时如何恢复。

## 4. Claude 的主工作面：W2 完整体验与多端

W2 不是“页面存在”验收。按用户旅程而非组件数量推进：

1. Web/PWA：首次进入、Aurora 多气泡/打断/停止/恢复、记忆来源与纠正、共鸣体编译/沙盒/
   发布/撤回、匹配解释、慢信、人际连接、画像成长全部无断链。
2. UI 状态矩阵：关键组件覆盖 loading/empty/streaming/partial/offline/error/retry/expired/
   permission/long-copy；覆盖 390px、桌面、200% 字体、中英双语、键盘和 reduced motion。
3. Android：在 Windows AVD 实际安装、登录、深链、前后台/杀进程恢复、WakeIntent 通知、
   麦克风、离线草稿和退出擦除；APK 构建不等于完成。
4. Desktop/iOS 交接：Windows Tauri 实际运行和构建；iOS/macOS 只把代码、workspace、模板和
   Mac runbook 做完，Apple 签名/真机/APNs 如实留作外部门。
5. 视觉打磨以一致设计系统、信息层级、动作反馈和可读性为准；不得仅添加渐变、动画或
   “高级感”装饰。每个关键页面必须截图评审并修正真实溢出、焦点、触摸、语义和错误恢复。

允许使用最多 2 个子 agent 并行：一个专注 Web/设计系统，一个专注移动/桌面真实旅程。
主 agent 独占共享 locale、API types、generated bundle、全局账本和最终合并。

## 5. W4：只在 W1/W2 关闭后开始

W4 必须完成：

- 从干净 checkout 启动 `local-complete`，由非作者按 presentation 顺序走完整 demo；
- Web/PWA/Android/Windows 的关键旅程与截图矩阵；
- Java、Web、Playwright、Android instrumentation、IaC、secret、SBOM/漏洞与镜像门；
- 故障演示脚本：Pod/SIGKILL 恢复、KEDA scale、trace drill-down，且可在有限课堂时间内复现；
- Academy fresh capability probe 与 fallback 叙事，不把本地 kind 结果冒充 Academy；
- README、运行手册、演示账号生成方式、presentation 脚本、已知边界和人工门同步到同一事实；
- ledger 中所有机器可执行项必须是 PASS，剩余只能是明确的设备、生产账户、法务/专家签字。

若选择一个可选云原生加分项，优先顺序是：

1. Kyverno/ValidatingAdmissionPolicy 的真实拒绝证据；
2. Argo Rollouts 的自动回滚与 AI-quality PromQL 分析。

每项必须有 product hook、成功、失败/拒绝、kill switch、资源上限和 Academy fallback，否则不做。

## 6. Git、验证与防跑偏

- 接管先执行 `git status --short --branch`，保留所有用户脏改。
- 一个可回滚结果一个 commit；阶段边界才跑全量，编辑循环跑 focused tests。
- 不修改或删除 W3 证据来让新实现“看起来更完整”；若复测失败，追加失败证据并修根因。
- 不输出或提交密钥；真实 Provider 仅通过本地环境注入。
- 不因单个绿色测试、单个平台构建或 UI 截图就标记 gate PASS。
- 每次提交后同步 closure state/acceptance ledger 的证据路径，但只有主 agent 修改这两个文件。

## 7. 可直接交给 Claude 的目标提示词

```text
接管 D:\code\inner cosmos 的完全体收敛。先完整阅读 AGENTS.md、goal-objective.md、
对齐文档/README.md、对齐文档 24/25、docs/goal/closure-campaign-state.yml、
docs/goal/complete-product-acceptance.yml，以及
docs/goal/claude-w2-w4-handoff-2026-07-23.md。

W3 已完成并冻结，不要重复实现或降低其 Aurora SIGKILL 恢复、KEDA outbox autoscaling、
OpenTelemetry 跨角色语义追踪能力。你的任务是：先关闭账本中 W1 的全部机器可执行残余，
再完整完成 W2 的 Web/PWA/Android/Windows 产品体验和真实设备/模拟器旅程，最后执行 W4
全局收敛、非作者验收、课程演示包和发布门禁。持续推进，不向用户询问可由代码、仓库、
本机工具、模拟用户、自动测试或运行实验回答的问题。

以用户可感知效果和可复现实证裁决完成度：Aurora、记忆/画像、共鸣体、匹配、慢社交必须
形成贯通且惊艳的真实旅程；UIUX 覆盖完整状态矩阵、双语、无障碍、小屏和错误恢复；
Android 必须在 Windows AVD 真正安装运行，Windows Tauri 必须真正运行构建。真实 Provider
使用本地环境密钥，结果包含失败分母。最多 2 个子 agent 并行，主 agent 独占共享文件、
账本、generated bundle 和合并。

一个可回滚结果一个 commit；阶段边界跑全量。不得把 build-only、mock-only、synthetic-only、
环境阻塞或人工门冒充 PASS。W4 结束时，所有机器可执行 gate 必须有实现、自动测试、真实
journey/evidence 和提交；剩余只能是 Apple/生产账户/法务或非作者盲评等真实人工门。
```

