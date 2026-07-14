# Inner Cosmos — AI 自我共鸣与慢社交平台

> 本文档供 AI 编程助手阅读。项目主要文档和注释使用中文，因此本文件以中文为主。

> **完全体目标模式提示**：本文主体描述当前/历史工程基线，不是最终产品规格。执行长期产品化目标前，必须依次阅读 [`goal-objective.md`](goal-objective.md) 与 [`对齐文档/README.md`](对齐文档/README.md)。目标、架构或体验发生冲突时，按对齐文档的权威层级裁决；不得以本文件中的 V0.1 技术栈和功能限制覆盖完全体目标。

---

## 项目概览

Inner Cosmos（内宇宙）是一个基于 Java Web 的 **AI 自我共鸣与慢社交平台原型**。核心闭环为：

1. 用户向 AI 伴侣 **Aurora** 倾诉（文字/语音模拟）。
2. 对话结束后自动沉淀为 **MemoryCard（记忆卡片）**、情绪痕迹与待办。
3. 记忆卡片经 **情感重力** 计算后汇入 **记忆星空**。
4. 用户可基于脱敏记忆创建 **EchoCapsule（共鸣体）**，并投放到 **星海广场**。
5. 其他用户可与共鸣体进行 **有限轮次对话**，并可发送 **SlowLetter（慢信）**。

当前版本为第一轮工程骨架（V0.1.0），**默认使用 Mock LLM**，可在无 API Key 的情况下完整演示核心闭环。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 / JVM | Java 17（兼容 Java 21） |
| 框架 | Spring Boot 3.3.6、Spring MVC、Spring Validation |
| 数据访问 | MyBatis-Plus 3.5.9 |
| 数据库（默认） | H2 In-Memory（`MODE=MySQL`） |
| 数据库（生产） | MySQL 8（通过 `mysql` Profile 切换） |
| 前端 | 物理 HTML 页面（无前端构建工具）+ 原生 JS/CSS |
| 构建工具 | Apache Maven 3.9.9 |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc |
| 异步 / 调度 | Spring `@Async` + `@Scheduled` |
| LLM 对接 | 适配器模式，支持 Mock / MiniMax / DeepSeek / GLM / OpenAI-Compatible |
| 语音（Mock） | GLM ASR Mock |

---

## 项目结构

```text
src/main/java/com/innercosmos/
├── InnerCosmosApplication.java      # Spring Boot 入口，启用 Scheduling
├── ai/
│   ├── agent/                       # AI Agent 定义（Aurora、Capsule、LetterGuard、MemoryExtract）
│   ├── client/                      # LLM 适配器（LlmClient 接口及各类实现）
│   ├── prompt/                      # PromptBuilder、模板注册、结构化输出解析
│   ├── strategy/                    # AgentReplyStrategy（Aurora、Capsule、ThoughtShredder）
│   └── structured/                  # 结构化 AI 结果与服务
├── asr/                             # 语音识别客户端（Mock / GLM）
├── common/                          # 公共常量、通用枚举、统一响应包装 ApiResponse
├── config/                          # 配置类（LLM、MyBatis-Plus、WebMvc、线程池、Mock 数据初始化）
├── controller/                      # Spring MVC REST API（共 20+ 个控制器）
├── dto/                             # 请求参数 DTO
├── entity/                          # 数据库实体（约 30 个）
├── event/                           # 对话结束事件与监听者（Observer 模式）
├── exception/                       # 业务异常与全局异常处理器
├── letterstate/                     # 慢信状态模式（State 模式）
├── mapper/                          # MyBatis-Plus Mapper 接口
├── safety/                          # 安全边界过滤器与规则
├── scheduler/                       # 定时任务（信件投递、夜间记忆结算）
├── service/                         # 服务接口
│   └── impl/                        # 服务实现
├── util/                            # 工具类（数据脱敏、JSON、Token 估算等）
└── vo/                              # 视图对象 / 响应 VO

src/main/resources/
├── application.yml                  # 默认配置（H2、LLM Mock）
├── application-mysql.yml            # MySQL 配置 Profile
├── schema.sql                       # 建表脚本（H2/MySQL 通用）
└── static/                          # 前端静态资源
    ├── css/                         # app.css、inner-cosmos.css
    ├── js/                          # api.js、app.js、auth.js、common.js、ui.js
    ├── index.html
    └── pages/                       # 18 个物理 HTML 页面

src/test/java/com/innercosmos/
├── ApplicationFlowTest.java         # 核心集成测试（MockMvc 端到端）
├── ai/client/MiniMaxLlmClientTest.java
└── ai/prompt/StructuredOutputParserTest.java

scripts/
├── run-dev.ps1                      # 自动下载 Maven 并启动 Spring Boot
├── compile.ps1                      # 编译（默认跳过测试）
└── count-lines.ps1

.tools/
└── apache-maven-3.9.9/              # 项目内置 Maven（Windows 环境）
```

---

## 构建与运行命令

### 使用项目内置 Maven（Windows 推荐）

```powershell
# 编译（默认跳过测试）
.\scripts\compile.ps1

# 启动开发服务器（H2 默认）
.\scripts\run-dev.ps1

# 切换到 MySQL 模式
.\scripts\run-dev.ps1 -Profile mysql
```

### 使用系统 Maven

```bash
# 编译
mvn compile -DskipTests

# 运行（H2）
mvn spring-boot:run

# 运行（MySQL）
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### 测试

```bash
# 运行全部测试
mvn test

# 仅运行集成测试（需要 H2 内存数据库支撑）
mvn test -Dtest=ApplicationFlowTest
```

入口地址：`http://localhost:8080/pages/index.html`

---

## 代码风格与约定

### 包与分层

- **controller**：仅处理 HTTP 边界，调用 service，返回 `ApiResponse<T>`。
- **service / service.impl**：接口与实现分离。复杂业务在 `impl` 中编排。
- **mapper**：MyBatis-Plus `BaseMapper<T>`，无 XML，纯注解/Java 配置。
- **entity**：字段使用驼峰命名，与数据库 `snake_case` 通过 `map-underscore-to-camel-case` 自动映射。
- **dto / vo**：请求与响应对象分离。

### 命名规范

- 数据库表前缀：`tb_`（如 `tb_user`、`tb_memory_card`）。
- 实体类与表名直接对应，无额外 `@TableName` 时默认按驼峰转下划线。
- 主键统一为 `BIGINT AUTO_INCREMENT`。
- 所有表保留 `created_at`、`updated_at` 字段。
- JSON 类字段在 V1 使用 `TEXT` 存储，以降低 MySQL 版本兼容风险。

### 会话与权限

- 基于 **HttpSession** 的登录状态，key 为 `LOGIN_USER_ID`（见 `Constants.SESSION_USER_KEY`）。
- 控制器继承 `BaseController`，通过 `currentUserId(session)` 获取当前用户，未登录抛 `BusinessException`。
- 角色分为 `ADMIN` 与 `USER`。

### 统一响应

所有 REST API 返回统一格式（`ApiResponse<T>`）：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": { ... }
}
```

### 异常处理

- `GlobalExceptionHandler`（`@RestControllerAdvice`）捕获：
  - `BusinessException` → 业务错误码
  - `MethodArgumentNotValidException` → 参数校验错误
  - `Exception` → 通用内部错误

---

## 核心模块说明

### 1. Aurora 对话（`controller/AuroraChatController`）

- 支持普通消息与 SSE 流式接口（`/api/aurora/message`、`/api/aurora/stream`）。
- 支持富输入（语音元数据、模式选择：`COMPANION` / `ACTION_SPLIT` / `SOCRATIC` / `BEDTIME`）。
- 对话内容写入 `tb_dialog_message`，仅用户本人可见（P0 隐私层）。

### 2. 记忆系统（`service/MemoryService`、`service/MemorySettlementService`）

- 对话结束后触发 `DialogFinishedEvent`，监听者自动：
  - 抽取 `MemoryCard`
  - 生成 `ThoughtFragment`（事实、感受、信念、行动）
  - 记录 `EmotionTrace`
  - 提取 `TodoItem`
  - 计算/更新 **情感重力**（`GravityService`）
- 记忆星空接口（`/api/memory/starfield`）将 MemoryCard 渲染为星体数据。

### 3. 共鸣体与星海广场（`service/CapsuleService`）

- 用户可基于脱敏记忆创建 `EchoCapsule`（共鸣体）。
- 系统内置 **8 个种子共鸣体**（`SeedCapsuleContent`），启动时由 `MockDataInitializer` 自动写入。
- 共鸣体可设置边界（`CapsuleBoundary`）：允许话题、屏蔽话题、最大轮次、是否允许慢信。

### 4. 慢信（`service/SlowLetterService`、`letterstate/*`）

- 状态模式管理生命周期：`DRAFT` → `SENT` → `FLYING` → `DELIVERED` → `READ` → `REPLIED` / `DECLINED` / `BLOCKED` → `ARCHIVED`。
- `LetterDeliveryJob` 每分钟扫描到期信件并改为 `DELIVERED`。
- 发送时可设置 **视差距离（parallaxDistance）**，模拟延迟到达。

### 5. 思维碎纸机（`service/ThoughtShredderService`）

- 用户一次性输入混乱文本，系统提取认知碎片并生成记忆卡片，原始文本不长期暴露。

### 6. 安全边界（`safety/*`、`service/SafetyService`）

- `SafetyBoundaryFilter` 按规则链检查文本。
- 规则包括：`CrisisKeywordRule`（高危自伤）、`AbuseKeywordRule`（骚扰/威胁）。
- 命中后根据等级决定：记录事件、阻断 AI 调用、引导至安全资源页。
- **产品定位**：不做心理诊断，不替代医生、咨询师或热线。

### 7. 管理后台（`controller/AdminController`、`service/AdminService`）

- 查看用户列表、共鸣体列表、举报记录、AI 交互日志、运营概览。
- 默认不展示用户 P0 原始私密内容。

---

## 设计模式落点

| 模式 | 落点 |
|------|------|
| Adapter | `LlmClient` / `AsrClient` 对接多种提供商 |
| Observer | `DialogFinishedEvent` + Listener（记忆、情绪、待办、重力、共鸣体建议） |
| State | `LetterState` 接口与 `DraftState`、`SentState`、`DeliveredState` 等实现 |
| Builder | `PromptBuilder` 组合系统边界、最近消息、记忆与输出要求 |
| Strategy | `AgentReplyStrategy` 区分 Aurora、碎纸机、共鸣体回应策略 |

---

## 数据库与数据分层

### 数据隐私分层

- **P0（原始对话）**：`tb_dialog_session`、`tb_dialog_message`。仅用户本人与 Aurora 可见，不进入星海广场。
- **P1（结构化记忆）**：`tb_memory_card`、`tb_thought_fragment`、`tb_emotion_trace`、`tb_todo_item`。
- **P2（共鸣体）**：`tb_echo_capsule`、`tb_capsule_boundary`。共鸣体只能读取授权后的抽象信息。
- **P3（社交）**：`tb_persona_chat_session`、`tb_persona_chat_message`、`tb_slow_letter`。

### 默认数据源

- **H2**：`application.yml`，内存模式，`MODE=MySQL`，启动时自动执行 `schema.sql`。
- **MySQL**：`application-mysql.yml`，需提前创建数据库：
  ```sql
  CREATE DATABASE inner_cosmos DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

---

## LLM 配置

配置前缀 `llm`，支持环境变量注入：

| 环境变量 | 说明 |
|----------|------|
| `LLM_MODE` | `dev`（默认，允许 fallback）/ `prod` |
| `LLM_PROVIDER` | `mock`（默认）/ `minimax` / `deepseek` / `glm` / `openai-compatible` |
| `LLM_API_KEY` | 通用 API Key |
| `LLM_ALLOW_FALLBACK` | 是否允许失败时回退到 Mock（默认 `true`，生产模式强制关闭） |
| `MINIMAX_API_KEY` | MiniMax 专用 Key |
| `DEEPSEEK_API_KEY` | DeepSeek 专用 Key |
| `GLM_API_KEY` | GLM 专用 Key |
| `GLM_ASR_API_KEY` | GLM 语音转写 Key |

默认启动时无需任何 Key，全部走 `MockLlmClient`，返回预设的哲学式回应。

---

## 测试策略

- **单元测试**：`StructuredOutputParserTest`、`MiniMaxLlmClientTest`。
- **集成测试**：`ApplicationFlowTest` 使用 `@SpringBootTest` + `MockMvc`，覆盖完整用户旅程：
  1. 登录 → 创建对话 → Aurora 消息 → 结束对话 → 记忆沉淀 → 记忆星空
  2. 星海广场 → 共鸣体对话 → 慢信起草与发送
  3. 共鸣体创建与可见性切换
  4. 富回复、思维碎纸机、安全审查
  5. 主题聚合、星空详情、LLM Fallback 与 AI Health

---

## 安全与隐私注意事项

1. **会话安全**：当前使用 HttpSession，生产环境建议配置 Spring Security 或 JWT。
2. **密码存储**：`UserServiceImpl` 使用 `BCryptPasswordEncoder` 加密密码。
3. **数据脱敏**：`DataMaskingService` 对 P0 原始内容进行脱敏，防止共鸣体越权读取。
4. **安全拦截**：`SafetyBoundaryFilter` 对危机关键词进行拦截，高危内容阻断模型调用并记录 `SafetyEvent`。
5. **慢信边界**：`LetterSafetyFilter` 在信件发送前执行内容审查。
6. **管理员隔离**：Admin 接口默认不返回用户原始对话内容。

---

## 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `admin123` | ADMIN |
| `demo` | `demo123` | USER |

`MockDataInitializer` 在启动时自动创建这两个账号，并为 `demo` 用户注入演示数据（记忆卡片、情绪痕迹、每日记录、慢信）。

---

## 定时任务

| 任务 | 触发频率 | 说明 |
|------|----------|------|
| `LetterDeliveryJob` | 每 60 秒 | 将到达时间的 `SENT` 慢信标记为 `DELIVERED` |
| `NightlyMemorySettlementJob` | 每天凌晨 2:00 | 全量用户情感重力重算 + 主题聚合更新 |

---

## 开发环境提示

- 项目主要在 **Windows** 环境下开发，脚本为 PowerShell（`.ps1`）。
- 若系统无 Maven，优先使用 `.\scripts\run-dev.ps1`，它会自动从 Apache 归档下载 Maven 到 `.tools/`。
- 前端为纯静态 HTML，无需 npm/yarn，直接由 Spring Boot 的 `static` 资源目录托管。
- H2 Console 默认关闭，开发时可在 `application.yml` 中将 `h2.console.enabled` 设为 `true`。
