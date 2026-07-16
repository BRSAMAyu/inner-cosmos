# Inner Cosmos（内宇宙）

[English README](README.md) · [组员与 Coding Agent 部署交接](对齐文档/18-组员与Coding-Agent启动部署交接指南.md) · [完全体总目标](goal-objective.md) · [机器验收账本](docs/goal/complete-product-acceptance.yml)

Inner Cosmos 是一个以长期自我理解和慢社交为核心的 AI 原生产品。Aurora 将自然对话逐步沉淀为可追溯、可纠正、可撤回的记忆、画像、关系、情绪与目标模型；用户在明确授权后，可以把其中一部分编译成有边界的 Echo Capsule（共鸣体），先形成理解与共鸣，再决定是否接近真人。

本仓库仍处于完全体持续实现阶段，不是已经完成商业发布的产品。当前已具备五空间 React AppShell、Aurora SSE 编排与打断/重规划、耐久 WakeIntent、记忆来源与纠正传播、数据驱动星空、版本化 Genome 编译脚手架、共鸣发现与对话、慢信、心理 Skill、Capacitor 移动工程，以及本地完整体和 AWS Academy EKS 双轨部署资产。真实 Provider 的完整质量证明、双语/a11y/真实设备覆盖、生产运维与灾备、独立体验评审和若干人类发布门禁仍未关闭；实时状态以验收账本为准。

## 核心差异

- **Living Aurora**：多消息计划、暂停、打断、停止/重规划、断线恢复、主动回访、关系状态以及版本化 Self/Constitution/Emergence 基础。
- **Living Inner Cosmos**：记忆和画像保留来源、置信度、时间、生命周期和纠正历史；用户能理解系统为什么这样判断。
- **Resonance Network**：授权记忆编译为有版本、有边界的共鸣体，支持沙盒、反馈、公开发现、可解释匹配、共鸣体对话与慢信。
- **有边界的心理能力**：Skill 具备版本、用途、同意、证据元数据和风险路径，不冒充诊断或治疗。
- **跨端与云原生**：React PWA、Capacitor Android/iOS，以及同一 Java 制品的 API/Worker/Scheduler/Migration 多运行角色。
- **不混淆部署证据**：`local-complete` 保证完整产品语义；`academy-eks` 证明教学账户允许的 Kubernetes 能力；`commercial-sg` 是尚待关闭门禁的新加坡生产目标。

## 当前技术基线

| 层 | 实时基线 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.14、Spring MVC/SSE、Spring Security、MyBatis-Plus |
| 数据 | PostgreSQL 16 + pgvector + Flyway 为生产事实源；Redis 承载 session/限流/租约；H2 仅用于零配置开发和聚焦测试 |
| 前端 | React 19、TypeScript、Vite、Vitest、Playwright；仅在 AppShell 尚未达到功能等价处保留旧物理页面 |
| 移动 | Capacitor 8、Android/iOS 工程、安全存储、网络、生命周期、深链与 Push 基础 |
| AI | Mock、GLM、MiniMax、DeepSeek、MiMo/ASR、OpenAI-compatible Provider gateway；生产禁止静默回退 Mock |
| 交付 | Docker、Compose、Kustomize、Gateway API、多运行角色 K8s、SBOM、镜像签名与安全门 |

## 最快本地启动

要求：JDK 21。仓库 Maven Wrapper 固定 Maven 3.9.9；只有修改 React 时才需要 Node.js 22+。

```powershell
git clone https://github.com/BRSAMAyu/inner-cosmos.git
cd inner-cosmos
.\mvnw.cmd spring-boot:run
```

macOS/Linux：

```bash
./mvnw spring-boot:run
```

打开 [http://localhost:8080/app/aurora/](http://localhost:8080/app/aurora/) 并注册本地账号。默认 `dev` 使用文件 H2 和 Mock AI，不需要任何密钥。只有明确设置 `SEED_ENABLED=true` 并启用 `demo` profile 时才注入演示数据；生产强制禁用演示 seed。

前端开发：

```powershell
Push-Location web
npm ci
npm test
npm run build
Pop-Location
```

## 真实 Provider 与密钥

真实密钥只属于操作者本地环境，禁止写入 YAML、Markdown、已提交 `.env`、Kubernetes manifest、聊天记录或 shell history。示例：

```powershell
$env:LLM_MODE = 'dev'
$env:LLM_PROVIDER = 'glm'
$env:GLM_API_KEY = (Get-Content -Raw "$HOME\.inner-cosmos\glm.key").Trim()
.\mvnw.cmd spring-boot:run
```

其他 Provider 使用 `MINIMAX_API_KEY`、`DEEPSEEK_API_KEY`、`MIMO_API_KEY`、`GLM_ASR_API_KEY` 等专用环境变量。`prod` 和 `local-complete` 在数据库、Redis、OIDC、TLS 或真实 Provider 配置缺失时 fail closed。

## 部署路径

| Profile | 用途 | 入口 | 真实性边界 |
|---|---|---|---|
| `dev` / `demo` | 快速本地开发 | `./mvnw spring-boot:run` | H2/可选 Mock，不是生产证据 |
| `local-complete` | 完整产品与真实 Provider 验收 | `scripts/local-complete.ps1` | 需要 Docker、PostgreSQL/pgvector、TLS Redis、OIDC 和本地密钥注入 |
| `academy-eks` | Learner Lab 课程 Kubernetes 证据 | `scripts/academy/preflight.ps1` + `deploy.ps1` | 固定 `us-east-1`、四小时凭据、静态 PV、Pod 无 SQS 身份，禁止把人的 AWS key 放入 Pod |
| `commercial-sg` | 新加坡商业生产目标 | 架构/IaC 验收线 | 当前不能宣称已完成；Terraform、托管服务、灾备、法律与所有者门禁仍开放 |

完整步骤、变量清单、验收命令、Academy 限制和恢复流程见 [`对齐文档/18-组员与Coding-Agent启动部署交接指南.md`](对齐文档/18-组员与Coding-Agent启动部署交接指南.md)。

## API 合同

首个稳定外部纵切面已发布为 [OpenAPI 3.1 v1](src/main/resources/static/openapi/inner-cosmos-v1.yml)，运行时地址为 `/openapi/inner-cosmos-v1.yml`。新客户端应优先使用其中覆盖的 `/api/v1` 认证、Aurora、共鸣体、慢信与 Persona 路由。核心写请求要求 `Idempotency-Key`，共鸣体边界更新还要求 `If-Match`，Aurora 恢复使用 `Last-Event-ID`。迁移期间保留旧 `/api`；在所有公共域、生成客户端、分页和跨 Pod 实时 SSE 完成前，验收账本仍诚实保持 `IN_PROGRESS`。

## 常用验证

```powershell
.\scripts\scan-secrets.ps1
.\scripts\scan-secrets.ps1 -History
.\mvnw.cmd test

Push-Location web
npm ci
npm test
npm run build
Pop-Location

.\scripts\academy\validate-manifests.ps1
.\scripts\academy\preflight.ps1 -Mode Offline
```

PostgreSQL/Redis Testcontainers 测试依赖可用的 Docker Engine。Docker 缺失只能登记为环境阻塞，不能据此把测试标记为 PASS。

## Coding Agent 接管顺序

Agent 开始编码或部署前，必须依次阅读：

1. [`AGENTS.md`](AGENTS.md)
2. [`goal-objective.md`](goal-objective.md)
3. [`对齐文档/README.md`](对齐文档/README.md)
4. [`对齐文档/17-单会话持续Goal模式执行协议.md`](对齐文档/17-单会话持续Goal模式执行协议.md)
5. [`docs/goal/complete-product-acceptance.yml`](docs/goal/complete-product-acceptance.yml)
6. [`docs/goal/single-session-state.yml`](docs/goal/single-session-state.yml)
7. 涉及启动/部署时再完整阅读部署交接文档。

随后检查实时 HEAD、分支、工作树、进程、配置和 evidence；不要只相信旧摘要。提交、测试通过或 K8s 截图只是 checkpoint，不是完全体完成证明。

## 安全原则

- 当前配置只保留环境变量引用，不提供可用的真实密钥默认值。
- `.env`、本地覆盖、kubeconfig、AWS 本地目录、私钥/证书、移动签名材料、数据库和生成证据均不得提交。
- CI 同时扫描当前树与当前分支的可达历史。
- 从最新文件删除密钥并不能清除 Git 历史；任何已暴露凭据都必须由 Provider 侧轮换，并在发布前净化可达历史。
- AWS Academy 临时凭据只能供本地 CLI 使用，不能注入 Pod，也不能作为持久配置共享。

## 目录导航

```text
src/main/java/                 后端产品与 AI runtime
src/main/resources/            配置、Flyway、构建后的 Web 资产
web/                           React PWA 与 Capacitor 移动工程
deploy/compose/                local-complete PostgreSQL/Redis/TLS
deploy/k8s/                    Kubernetes base 与 Academy overlay
scripts/                       构建、验证、本地与 Academy 操作脚本
ai-lab/                        AI 评测和 pairwise 工具
docs/goal/                     机器验收与单会话恢复状态
对齐文档/                       权威产品、架构与执行规格
evidence/                      可复现的实现与验收证据
```

## 许可证

仓库当前未声明开源许可证。在所有者添加许可证文件前，请按保留所有权利处理代码和资产。
