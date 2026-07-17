# 完全体持续推进指令（Operator Directive v2 — 2026-07-16）

> 性质：操作者（项目所有者）下达的持续执行指令，与 `goal-objective.md`、`对齐文档/17-单会话持续Goal模式执行协议.md`（下称"17号协议"）配合使用，冲突时以两者为准、本文件补充执行细节。
> 按 17号协议 §9：本指令并入当前总目标；主 Agent 应在第一个检查点把本路线图同步进 `docs/goal/single-session-state.yml` 的优先队列，并把本文件提交入库（当前可能仍是未跟踪状态）。
> 读者：通过 /loop 持续运行的 Claude Code 主 Agent，以及**任何中途接手的 Agent**（接手协议见 §6）。

---

## 0. 你是谁、你要交付什么

你是本项目的持续自主开发者。最终交付物只有一个：**符合 `goal-objective.md` 全部结果契约的超高完成度完全体产品**——不只是体验层漂亮，而是产品、AI 质量、数据权利、工程、部署、可运维每个方面都收敛到"完善"。每一轮 loop 不是一次任务，而是同一目标的下一个检查点。唯一合法终态见 17号协议 §1（`COMPLETE` 或 `RELEASE_CANDIDATE_BLOCKED_BY_HUMAN_GATE`）；"本轮做完了""测试是绿的""上下文被压缩了"都不是停止理由。

每轮启动严格执行 17号协议 §2 恢复流程：读权威文档 → 检查 HEAD/工作树/未提交资产 → 重验最易漂移的事实 → 从 `single-session-state.yml` 的 active front 继续。

## 1. 阶段化主线（按序推进，允许受限并行）

> 并行规则（17号协议 §5）：任何时刻必须至少有一个**用户可感知**的 Front 在推进；阶段可以重叠，但宣布某阶段完成必须满足其退出标准并有证据。
> 人类门禁项（真实密钥/真机/专家/法务/真实用户）不阻塞主线：为其准备好一页式可执行清单后登记跳过，继续机器可做的一切。

### Phase 0 — 体验层重造（当前最高优先级，P0）

操作者对现有 UI/UX 不满意，且已核实为规范违反（不是主观偏好）：

**0-A 视觉系统重造。** `web/src/styles.css` 当前是冷色深绿/蓝黑主题（背景 `#0d1412`、冷蓝绿强调、Inter 无衬线），直接违反 `对齐文档/11`（禁"冷蓝霓虹、纯黑高对比 AI 控制台"，夜间主背景应为暖褐 `#211A18`，"不允许随意换成冷科技主题"）与根目录 `UIUXdesign`（完整设计方案：温暖流动色系、七时段、五母题、字体、动效、验收清单）。整改要求（`UIUXdesign` + doc-11 为准，token 语义冲突时 doc-11 优先）：

1. **Design token 层**（CSS Variables + `@layer`）：白昼莫兰迪（米白 `#F7F2EC` 基底）、暮色系、夜色暖褐系（`#211A18`/`#2A1F18`）；禁纯黑、禁冷蓝、禁高饱和霓虹；所有组件一律走 token，全库清零硬编码色值。
2. **七时段时间感知**：dawn/morning/noon/evening/dusk/night/deep-night 平滑 lerp 过渡不跳变；用户可锁定主题；`prefers-reduced-motion` 完全降级。
3. **字体**：中文标题霞鹜文楷、正文思源宋体、代码 JetBrains Mono、英文 Cormorant Garamond；字体本地打包（不依赖外部 CDN，注意 CSP 与离线），带系统 fallback 与字重子集化（性能）。
4. **五母题**：流动（4 条统一 easing，禁 ease/linear）、呼吸（4-8s 微缩放）、星尘（背景粒子，移动端降级）、涟漪（点击反馈）、透光（玻璃面板）。
5. **微交互与状态**：按钮点击 scale(0.97)→反弹→涟漪；hover 反馈；加载态永不用 spinner（<1s 无、1-3s 文案、>3s 文案+微动画）；每个界面空/错/等待/恢复四态齐全。
6. **验收**（每项要浏览器截图存 `evidence/`，至少覆盖日间+夜间两时段）：暗色不冷（色相在暖侧）；WCAG 对比度达标；Lighthouse 性能 >85；移动端断点正常。

**0-B 死按钮清剿。** 操作者报告"很多按钮点击没有反应"：

1. 写 Playwright 交互扫描（`web/e2e/`）：登录后遍历 AppShell 每个空间，枚举所有可交互元素，逐个触发并断言"有可观察响应"（状态变化/导航/网络请求/aria-live/可见反馈之一），输出无响应清单。
2. 逐项处理：修实现、补后端、或删除不该存在的占位控件——**不允许留下点了没反应的控件**。
3. 扫描固化为回归门，证据入 `evidence/`。

**退出标准**：token 化完成且全组件应用新视觉；死按钮清单归零且回归门常绿；两时段截图证据齐全；`npm test` + `npm run build` + Java 全量门绿。

### Phase 1 — 消灭双轨制 + 五空间真实齐平（P0 后立即，可与 Phase 0 尾部并行）

台账 UX-SHELL/UX-LEGACY 已确认 13 个遗留静态页面拥有 AppShell 缺失的**真实能力**：语音输入、信件草稿/归档/线程视图、关系温度/健康/时间线、群组、画像部分功能、胶囊创建向导的完整字段等（完整清单见台账 remaining 字段）。

- 节奏：移植一个旅程 → 用 Phase 0 新设计系统实现（不复刻旧样式）→ 浏览器验证 + 截图 → 下线对应旧页 → 提交。
- 同时收口 G3 其余项：UX-CONTRACTS（生成式 API client 全覆盖）、UX-QUALITY（Playwright/视觉回归/a11y/i18n/性能预算跑进核心路由）。
- 双语（zh-CN/en-SG）与 WCAG 2.2 AA、reduced-motion 验收（台账 UX-COMPLETE）在移植过程中同步做，不留到最后。

**退出标准**：`static/pages/` 仅剩确需保留的入口（登录/注册等），无任何与 AppShell 竞争的旅程；UX-COMPLETE 从 UNASSESSED 推进到有证据的 IN_PROGRESS/PASS；核心路由 Playwright 常绿。

### Phase 2 — Campaign B/C/D 的机器可做深度（AI 与数据质量）

**Campaign B（Living Inner Cosmos / G5）**，未完成项：

- claim 抽取管线补全：实体/时间/关系归一化、语义冲突检测与不确定性标注（Slice 4）；抽取触发的规模化与性能。
- 评测数据集扩容：纠错/检索 9-10 例、遗忘 7 例、冲突 4 例 → 各扩到能支撑质量声明的规模（≥30-50 例，含难例与反例），保持标注质量。
- **FORGET 传播剩余四面**：prompt 缓存、sync-queue/事件积压、analytics、备份生命周期中被遗忘记忆的清除审计与修复（台账已登记未审计——这是隐私承诺完整性问题，优先级高）。
- DataUseGrant 从胶囊/模拟器扩展为跨域同意中心（替代 consentScope 字符串约定）。

**Campaign C（Resonance Network / G6）**，未完成项：

- Genome IR：带出处的 claims/values/style/habits/scenes/temporalState/tensions/unknowns 中间表示（当前 v2/v3 仍是结构脚手架）。
- 真实保真/泄露评分：`evaluationJson` 从结构清单升级为可解释的 fidelity/leakage score；critic pass 进入胶囊运行时（planner/retriever/speaker/critic/reranker 补全）。
- 长 Prompt baseline 对比 harness：Mock 下可执行、真实 Provider 到位即插即用；对抗反问与纵向漂移评测场景。
- embedding/用户向量匹配：脚手架与接口先行（Provider 门禁到位后启用），词法匹配保留为 fallback。

**Campaign D（Psychology Skills / G7）**，未完成项：

- 三个 Skill 从确定性原型推进到真实内容质量：引用/证据元数据/风险场景进产品界面；危机/不适用/拒绝/撤回四条旅程完整可走。
- 新加坡本地资源与完整双语旅程；专家审阅**打包**（材料一页式清单，签字留人类门禁）。

**退出标准**：上述每项要么 PASS（有证据），要么剩余部分严格是登记在案的 Provider/专家门禁。

### Phase 3 — 工程收口（G2 / G8 机器可做部分）

- **G2 契约**：v1 之外公共域纳入 OpenAPI；一致游标分页；多 Pod SSE soak（延迟/心跳百分位记录 + Redis Stream consumer-group 重投递）；Spring Modulith 可执行模块边界测试（台账 ARCH-MODULES 仍 UNASSESSED）。
- **可靠事件**：事务性 outbox → 幂等消费 → 重试 → DLQ 重放全链路验证（本地/Testcontainers 可做；SQS 语义用可替换抽象，academy 用 JDBC outbox 证据）。
- **运行时角色**：API/AI worker/event worker/scheduler/migration 独立运行与伸缩的验证（compose/kind 可做）。
- **可观测性**：OpenTelemetry 接入、dashboards/alerts as code（Prometheus/Grafana 已有 prometheus.yml 起点）、AI 成本/质量信号、runbook 编写并由**非作者 Agent** 演练。
- **韧性**：kind/local 上演练 rolling update、pod/node 故障、DB 备份恢复、canary/rollback 脚本化；记录 SLO/RPO/RTO 声明。
- **commercial-sg IaC**：Terraform 候选（最小权限、新加坡区域），验证到 plan/validate 级别（真实账户 apply 是人类门禁）。
- **移动端机器可做**：原生 OIDC/PKCE 客户端、API-origin 信任校验、深链在模拟器验证；真机推送/签名/商店留人类门禁清单。

**退出标准**：G2 全项、G8 的 ACADEMY-RELIABILITY/LOCAL-COMPLETE/OPS-OBSERVABILITY/OPS-RESILIENCE 有证据关闭或明确剩余=人类门禁；EKS-IAC 达到可 plan 的候选状态。

### Phase 4 — G9 收官与终审

- 全产品 E2E：Web/PWA/移动容器三面核心旅程自动化通过。
- **8-12 分钟可复现演示**：脚本 + 素材 + 一键环境（产品差异化、AI 深度、数据血缘、K8s 运维四要素齐全）。
- 运维手册被非作者 Agent 独立走一遍（发布/回滚/事故/Provider 故障/数据权利/灾备）。
- **追溯矩阵**：台账每个必需项 → 当前可复现证据的映射表，无隐藏 P0/P1。
- 完成度终审（见 §5）后，才允许宣布终态。

## 2. 横切任务（不属于任何单一阶段，持续做）

1. 清理 `src/main/java` 25 处 TODO/FIXME：修复、登记为验收差距、或删除过期注释，逐个决策。
2. 每个新增读写路径 owner-scope 复查（IDOR 是本项目明确风险）。
3. 台账所有 `remaining` 字段每 5-10 个检查点重新对账一次，防止文档与实现漂移（本项目历史上多次发现声明超前于实现）。
4. Aurora 质量评测（ai-lab）harness 保持可执行并随功能扩展数据集；真实 Provider 凭证一旦由操作者注入，**立即优先**跑完积压的 pairwise/盲测/校准评测——这是解锁最多验收项的单点事件。

## 3. 不变的工作纪律（每轮适用）

- **证据先于断言**：UI 改动必须真实浏览器验证 + 截图入 `evidence/`；后端 focused tests 起步、风险检查点跑全量。当前基线 Java 835/835、web 67/67 必须保持绿，只增不减。
- **每个检查点**：提交（描述性 message，含 Co-Authored-By trailer）→ 更新 `evidence/` → 更新台账与 `single-session-state.yml`（格式见 §6.3）→ 推送 main（禁 force-push）→ 立即进入下一差距。
- 推送前 `pwsh scripts/scan-secrets.ps1` 必须干净；密钥只经环境变量注入，绝不落盘/入库/进日志。
- 不得降低断言、删测试、把失败改成 known limitation 来关门槛；不得把未独立验收项标 PASS；台账要求"非作者评审"处可用独立 fresh-context Agent 执行并注明，但**真实人类**门禁（专家/真实用户/真机/法务）不可用 Agent 冒充。
- 技术阻塞按 17号协议 §8：≥3 类方案尝试 → 登记 → 切换无依赖 Front，不空转、不重复同一命令。
- **本机环境事实**（2026-07-16 核实）：JDK 21 于 `D:\download\jdk-21.0.11.10-hotspot`；Node.js v24 已装于 `C:\Program Files\nodejs` 但**不在 Git-Bash 默认 PATH**——Bash 里先 `export PATH="/c/Program Files/nodejs:$PATH"`；用 npm（勿用 pnpm，未安装）；Docker 可用，Redis 容器 `inner-cosmos-redis`（6379）供健康检查/集成测试，若停了 `docker start inner-cosmos-redis`；本机 80/443 被 kind 集群占用。

## 4. 每轮 loop 的标准循环

```text
恢复现场（17号协议 §2 + 本文件 §6 接手协议）
  → 收口未提交工作
  → 选差距：Phase 0/1 未达退出标准前优先体验层；之后按阶段序 + state 文件队列
  → 实现一个可体验批次（用户可感知 > 内部洁癖）
  → 验证（focused tests + 浏览器实测 + 截图）
  → 自审修复 P0/P1
  → 证据 + 台账 + 状态文件（§6.3 交接快照）+ 提交推送
  → 不发 final，继续下一差距；仅 17号协议 §1 终态或 loop 被外部停止时收尾
```

## 5. 完成度终审（宣布终态前的强制程序）

1. 以 **fresh-context 独立 Agent** 重做一次完整审计：全量门（Java + web + Playwright + ai-lab + secret scan + kustomize build）、台账逐项对账、隐藏 P0/P1 扫描、`UIUXdesign` §12 验收清单逐条核对。
2. 每个剩余人类门禁：一页式清单（预期输入、验证方法、失败回退、负责角色），不含任何秘密。
3. 在真实浏览器完整走一遍五空间核心旅程并录屏/截图，作为终审证据。
4. 全部通过才允许按 17号协议 §1 宣布终态；任何一项不过，回到对应 Phase。

## 6. Agent 接手协议（任何新 Agent / 压缩恢复 / 换会话都适用）

### 6.1 接手时（按序，不可跳步）

1. 读 `CLAUDE.md` → 本文件 → 17号协议 → `docs/goal/single-session-state.yml`（active front + 最近 checkpoints）。
2. `git status` + `git log --oneline -10`：未提交改动一律视为前任 Agent 的在制资产——判断意图后**继续完成或原样保留**，绝不丢弃/覆盖/revert；不确定归属时先 `git stash list`、查 state 文件的 active front 描述。
3. **抽验前任声明**：至少重跑一项前任声称绿的门（如受影响模块的 focused tests + 一次浏览器冒烟），不机械相信摘要；发现声明与实现不符，先修正 state 文件再继续。
4. 确认运行环境（§3 本机事实）：服务是否在跑、Redis 容器、Node PATH。
5. 从 active front 的 `next_machine_actions` 第一条继续；若已失效，按 §1 阶段序重选。

### 6.2 并行与所有权

多 Agent 并行时遵守 17号协议 §6：独立 worktree、只 stage 自己负责的文件、提交前查 cached diff、不清理他人工作。主 Agent 唯一持有 state 文件写权。

### 6.3 交接快照（每个检查点写入 state 文件，为下一任准备）

`single-session-state.yml` 的 active_front 必须始终包含，且**写给陌生人看**（不依赖会话内上下文）：

```yaml
active_front:
  id: <阶段/战役标识>
  state: <一句话现状>
  done_this_checkpoint: [<带文件路径/测试名的具体完成项>]
  next_machine_actions:   # 每条都要具体到"新 Agent 不需要提问就能开工"
    - "<动词开头，含目标文件/命令/验收方式>"
  blockers: [<登记的门禁或技术阻塞，含证据路径>]
  verify_commands: [<新 Agent 抽验用的最小命令集>]
```

### 6.4 心跳与漂移防护

- 每 5-10 个检查点做一次 completion audit（17号协议 §10）：合并重复、删无价值兼容层、对账台账 remaining 字段。
- 若发现 loop 长时间在同一 front 打转（3 轮无实质差距关闭），强制切换到 §1 中下一个可推进项并登记原因。

## 7. 完成度的定义

操作者要的是"超高完成度"：打开产品 30 秒后用户感觉在看"一场安静流动的内在宇宙"，而不是一个工程 demo（`UIUXdesign` §14）；同时台账每个必需项要么 PASS 要么剩余=登记在案的人类门禁。每轮结束前自问：这一轮的改动，一个真实用户能感知到吗？台账离终态又近了哪一格？如果连续多轮两个答案都是"否"，回到 §1 重新对照优先级。
