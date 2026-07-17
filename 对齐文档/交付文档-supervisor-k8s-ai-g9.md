# 交付文档 · 分支 `feat/supervisor-k8s-ai-g9`

> 更新日期：2026-07-18 · 分支 HEAD：`4917178` · 基线：`ab30974`（main）
> **用途**：给组员看清这条分支做完了什么、还剩什么；也可**直接作为 prompt 交给 AI（Claude Code）继续开发**。
> 接手 AI 请先读本文件末尾的「§7 给接手 AI 的执行指令」。

---

## 0. 一句话现状

这条分支把项目从"骨架能跑、效果没证明、只有纸面 k8s"推进到：**应用已真实部署在 AWS EKS 上跑（含 PostgreSQL/Redis/多副本/HTTPS 网关）、接了真实大模型 + 真实向量语义检索、k8s 运维（可观测/HPA/韧性/备份）都在真集群上实测过、并把 3 个旧功能移植进了新界面**。终态目标是 `RELEASE_CANDIDATE`（COMPLETE 需要操作者本人的人类门禁）。

---

## 1. 怎么运行 / 怎么访问

### 本地（免密钥，最快）
```bash
./mvnw spring-boot:run          # 需要 JDK 21 + JAVA_HOME
# 打开 http://localhost:8080/app/aurora/  （dev 档：H2 + Mock AI，无需任何密钥）
```
一键 Docker（新加的免密钥 compose）：
```bash
docker compose -f deploy/compose/dev.yml up --build
```
接真实大模型（key 只经环境变量注入，绝不入库）：
```bash
LLM_PROVIDER=deepseek LLM_API_KEY=sk-... docker compose -f deploy/compose/dev.yml up --build
```

### 线上（真实 AWS EKS，可给组员做真人测试）
- 地址（HTTPS，自签证书，浏览器会警告→点「高级/继续前往」）：
  `https://a60f3ce14c2e146f1bc5804884788d36-1144038221.us-east-1.elb.amazonaws.com/app/aurora/`
- 特性：共享 PostgreSQL（持久）+ 共享 Redis（会话）+ 2 副本 + 真实 DeepSeek + 真实 GLM 向量检索。多人可注册/写信/共鸣，数据一致不丢。
- **注意**：这是 AWS Academy Learner Lab，凭据/集群会**定时过期**；集群一停网址失效；重建 Gateway 后 ELB 地址会变。测完省额度：`kubectl delete -k deploy/k8s/overlays/eks-prod`。

### 验证基线（接手前先抽验）
```bash
./mvnw test                       # 后端 836 全绿（含 Testcontainers，需要 Docker）
cd web && npm ci && npm test      # 前端 123 全绿
kubectl kustomize deploy/k8s/base && kubectl kustomize deploy/k8s/overlays/academy-eks   # 离线 build
./scripts/scan-secrets.sh         # 密钥扫描必须干净（macOS 无 pwsh 时用这个）
```

---

## 2. 本分支已完成的工作（14 个提交）

### A. 基础与可信度（`e5b081c`）
- 新增**免密钥 dev compose** `deploy/compose/dev.yml`（原来两个 compose 都强制真密钥、开箱即失败）。
- 修 **Dockerfile 可移植性**：`dependency:go-offline` 改为 best-effort（原来在受限网络下拉 flyway→google-cloud-storage 依赖失败，导致镜像根本构建不出来）。
- 新增便携式密钥扫描 `scripts/scan-secrets.sh`（本机无 PowerShell）。
- 修正**假证据**：`ACADEMY-LIVE-001` 里 HPA 写的是 `2..6`，但清单实际一直是 `2..4`（git 历史核实）——已改回真实值。

### B. k8s 运维（督导问题1）—— 本地 kind 实测（`6acd19e`）
- `deploy/k8s/observability/`：Prometheus + Grafana **as-code**（抓 `/actuator/prometheus` + 4 条告警规则 + App/JVM 仪表盘）。
- `deploy/k8s/overlays/kind-dev/`：让真实镜像能在 kind 上独立跑（dev 档，零密钥）。
- 实测：HPA 压测 2→4→2；韧性演练（删 Pod 恢复 / 零停机滚动更新 / 节点驱逐 + PDB）；PostgreSQL 备份 CronJob + 真实恢复演练。
- 证据：`evidence/g8/OPS-OBSERVABILITY-001|HPA-LOAD-001|OPS-RESILIENCE-001|BACKUP-RESTORE-001/`。

### C. AI 深度与效果（督导问题2）（`e596606`、`793091e`）
- **双核 vs 单核** 用真实 DeepSeek 跑了 pairwise（`ai-lab real-pairwise`，11 条轨迹，真实调用）。**诚实结论**：词法指标上双核未优于单核，真正结论需人类盲评（这是人类门禁）。证据 `evidence/innovation/INNO-EVAL-003/`。
- **语义检索**：先离线证明语义>词法（recall +100pts，`INNO-INNER-008`），再在**线上真实 GLM embedding-3 + pgvector**验证（重建 indexed=2/failed=0，pgvector 余弦 `<=>` 返回 sim 1.0/0.9556）。证据 `INNO-INNER-009`。

### D. 工程可靠性 G2（`9f41c87`）
- 事务性 **outbox → 幂等消费 → 重试 → 死信(DEAD) → 重放** 全链路，真实 PostgreSQL(Testcontainers) 验证；新增 `JdbcOutboxRepository.replayDead()`。证据 `evidence/g2/EVENT-RELIABLE-001/`。

### E. 真实 EKS 部署（督导问题1 的最强证据）（`38fd43d`、`a172a3d`、`edf6b18`）
- 先用 dev 档把应用部署到真 EKS（`eks-dev`），再上**完整生产栈** `deploy/k8s/overlays/eks-prod`（prod 档：TLS PostgreSQL + TLS Redis + 迁移 Job + API/worker/scheduler + HTTPS 网关）。
- **修了一个真实的生产阻塞 bug**：14 个 H2/MySQL 模式的 `SchemaMx*Initializer` 在 Postgres 上因 `AUTO_INCREMENT` 语法崩溃（它们只按 flyway.enabled 判断，而 prod 要求 app 关 flyway）——加了"仅 H2 运行"的门控（`@ConditionalOnExpression` 判断驱动是 org.h2.Driver）。全量 836 测试仍绿。
- 修了 ELB 跨可用区负载均衡（否则外网访问间歇性超时）。
- 证据：`evidence/g8/EKS-LIVE-002/`、`EKS-PROD-001/`。

### F. G9 演示（`4e7a930`）
- 8–12 分钟可复现 demo 脚本：`evidence/g9/FINAL-DEMO-001/demo-script.md`（覆盖 产品差异化 / AI 深度 / 数据血缘 / k8s 运维 四要素）。
- 追溯快照：`evidence/g9/TRACEABILITY-SNAPSHOT-2026-07-17.md`。

### G. 旧功能移植进新界面（`3754c1d`、`387ad44`、`799c259`、`4917178`）
- **关系温度 + 时间线**：`web/src/components/RelationsView.tsx` + `/api/relation/*` client。
- **信件草稿页(可寄出) + 往来(线程)视图**：扩展 `LettersInbox` + `/api/letters/threads`。
- **语音输入**：`AuroraConversation` 里 🎤 录音 → `/api/asr/transcribe` → 填入输入框。
- 前端 123 测试全绿；已构建并部署到线上 `eks-prod2` 镜像。

---

## 3. 已知行为与坑（不是 bug，别误判）
1. **胶囊边界编辑**：正确调用方式是话题用**逗号分隔字符串** + `maxConversationTurns` + `If-Match` 版本头；UI 发的是对的。
2. **慢信延迟约 3 分钟投递**（设计如此：已寄出→飞行中→已抵达，调度器每 60s 推进）。demo 演"已寄出/飞行中"或等 3–4 分钟。
3. **语音转写目前是 Mock**（录音/上传/转写链路是真的），接真实 ASR key 才是真转写。
4. 线上是**自签证书**，浏览器首次告警属正常。
5. **Learner Lab 凭据/集群会过期**；密钥都在 k8s Secret / `~/.aws` / 环境变量里，**从不入库**。

---

## 4. 待完成清单（机器可做，按优先级）

- **[高] 真人盲评闭环准备**：`ai-lab` 已能一键出 A/B 盲评 CSV（`evidence/innovation/INNO-EVAL-003/blind-human-pairwise.csv`），需要 ≥2 名评审打分才能把 G4 双核/共鸣体质量判 PASS（打分本身是人类门禁）。
- **[高] G9 收官**：`FINAL-E2E`（Playwright 五空间核心旅程自动化）、录一遍 8–12 分钟 demo（由非作者从干净环境走一遍）、`FINAL-TRACEABILITY` 完整追溯矩阵。
- **[中] G2 剩余**：`ARCH-MODULES`（Spring Modulith 需要先定义领域模块边界，现在是分层结构，属设计决策，不是 drop-in）；`API-CONTRACT` 多 Pod SSE soak + 游标分页。
- **[中] 可观测/韧性搬到真 EKS**：把 `deploy/k8s/observability` 部署到 EKS；在 EKS 上做节点驱逐演练；接 OpenTelemetry trace。
- **[中] EKS-IAC**：写 `terraform/`（新加坡区域最小权限 EKS）到 `validate`/`plan` 级别（真实 `apply` 是人类门禁）。本机需先装 terraform/opentofu。
- **[中] AI 深度剩余**：claim 抽取下一切片；FORGET 传播到 prompt 缓存/sync-queue/analytics/备份；接真实 ASR key。
- **[中] CI 加固**：`.github/workflows` 加 SBOM/SAST/依赖与容器扫描门禁。
- **[低] 社交剩余**：关系"群组"；旧页彻底下线（移植验证后再删）。

**人类门禁（Agent 不可冒充，只能备好一页式清单）**：真实 provider 盲评人评、`HG-SECRET-ROTATION`（**请轮换本次用过的 DeepSeek/GLM/AWS 凭据**）、`HG-PRODUCTION-ACCOUNTS`（真实 AWS apply/真机签名）、`HG-PRIVACY-LEGAL`、`HG-PSYCHOLOGY-REVIEW`、`HG-REAL-USERS`。

---

## 5. 关键文件索引
- 权威账本：`docs/goal/complete-product-acceptance.yml`（G0–G9 逐项状态，**唯一真相源**）。
- 恢复状态/交接快照：`docs/goal/single-session-state.yml`（本会话每个检查点的 `next_machine_actions`，写给陌生人看）。
- 本分支新增部署物：`deploy/compose/dev.yml`、`deploy/k8s/observability/`、`deploy/k8s/overlays/{kind-dev,eks-dev,eks-prod}/`、`deploy/k8s/backup/pg-backup.yaml`、`deploy/eks/Dockerfile.runtime`。
- 证据：`evidence/g8/*`、`evidence/g2/EVENT-RELIABLE-001/`、`evidence/innovation/INNO-EVAL-003`、`INNO-INNER-008/009`、`evidence/g9/*`。
- 前端新组件：`web/src/components/RelationsView.tsx`、扩展的 `LettersInbox.tsx`、`AuroraConversation.tsx`。

---

## 6. 环境与凭据（非敏感说明）
- 本机（macOS/arm64）：JDK 21、Node 24/npm、kind、kubectl 已装；Docker Desktop 按需开；**无 terraform、无 pnpm、无 pwsh**（用 npm + `scripts/scan-secrets.sh`）。
- 所有 provider/AWS 凭据**仅经环境变量 / k8s Secret / `~/.aws` 注入，绝不入库**；推送前跑 `scripts/scan-secrets.sh`。

---

## 7. 给接手 AI 的执行指令（可直接作为 prompt）

> 你是本项目的持续自主开发者，接手分支 `feat/supervisor-k8s-ai-g9`。请按下述步骤继续，直到账本必需项 PASS 或只剩人类门禁：
>
> 1. **恢复现场**：依次读 `CLAUDE.md` → `goal-objective.md` → `docs/goal/loop-goal-directive.md` → `docs/goal/single-session-state.yml`（看 `supervisor_track_*` / `*_checkpoint` 的 `next_machine_actions`）→ `docs/goal/complete-product-acceptance.yml`。再 `git status` + `git log --oneline -15` 核实现状，**不要轻信摘要**。
> 2. **抽验基线**：跑 `./mvnw test`（Docker 开）、`cd web && npm test`、`kubectl kustomize deploy/k8s/base`、`./scripts/scan-secrets.sh`，确认全绿再动手。
> 3. **选下一个差距**：优先本文件 §4「高」项。每次只做一个可验证批次。
> 4. **工作规范**：证据先于断言（跑命令/驱动界面看到输出再说"完成"）；每个检查点→描述性提交（结尾带 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`）→更新 `evidence/` + 账本 + `single-session-state.yml`→推送 `origin feat/supervisor-k8s-ai-g9`（禁 force-push）。
> 5. **密钥规范**：只经环境变量/Secret 注入，绝不写进任何被 git 跟踪的文件、日志、证据;推送前 `scripts/scan-secrets.sh` 必须干净。
> 6. **诚实**：不虚标、不降断言、不把失败改成 known limitation 关门;人类门禁项只备一页式清单并登记，不冒充完成。
> 7. **提交信息用 `-F 消息文件`**（本仓库用内联多行 `-m` 偶发卡住;用 `git commit -F <file>` 稳定）。
>
> 具体机器可做的下一步建议：先做 `FINAL-E2E`（Playwright 覆盖 注册→对话→记忆/纠正→编译胶囊→共鸣/写信 主线）与完整 `FINAL-TRACEABILITY` 矩阵;然后把可观测/韧性搬到真实 EKS。
