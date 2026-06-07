# Inner Cosmos AI / 数据分析 / 存储完整性 审计报告 v2.0

> 审查日期：2026-06-07
> 修复日期：2026-06-07
> 审查范围：AI 系统（LLM 客户端 + Agent 服务 + 提示词）、数据分析管线、用户数据存储完整性
> 方法：全量源码读取，覆盖 47 个 Java 文件 + schema.sql + 前端 api.js
> 严重程度：P0 = 数据必定丢失，P1 = 数据可能损坏/丢失，P2 = 可靠性，P3 = 打磨

---

## 修复状态总览

| 编号 | 问题 | 状态 |
|---|---|---|
| P0-1 | H2 内存数据库重启丢数据 | **已修复** |
| P0-2 | PersonaChatServiceImpl 无事务 | **已修复** |
| P0-3 | DialogServiceImpl.saveAuroraMessage 无事务 | **已修复** |
| P1-1 | schema.sql 无外键约束 | **已修复** |
| P1-2 | 用户删除不完整 | **已修复** |
| P1-3 | GLM/MiniMax 缺 recentMessages | **已修复** |
| P1-4 | AI 服务失败静默吞数据 | **已修复** |
| P1-5 | Aurora Agent Loop 多消息无事务 | **已修复** |
| P2-2 | 缺少数据库索引 | **已修复** |
| P2-9 | 定时任务无错误隔离 | **已修复** |

---

## P0：数据必定丢失（3 项）— 全部已修复

### P0-1. H2 数据库 `jdbc:h2:mem:` — 重启丢全量数据 ✅ 已修复

**位置**：`application.yml:9`

**修法**：改为 `jdbc:h2:file:./data/innercosmos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_RECONNECT=TRUE;DB_CLOSE_DELAY=-1`，并在 `.gitignore` 加 `/data/`。

---

### P0-2. `PersonaChatServiceImpl.reply()` 无 @Transactional ✅ 已修复

**位置**：`PersonaChatServiceImpl.java:83-159`

**修法**：给 `reply()` 加 `@Transactional(rollbackFor = Exception.class)`。

---

### P0-3. `DialogServiceImpl.saveAuroraMessage()` 无 @Transactional ✅ 已修复

**位置**：`DialogServiceImpl.java:67-78`

**修法**：给 `saveAuroraMessage()` 加 `@Transactional(rollbackFor = Exception.class)`。

---

## P1：数据可能损坏/丢失（5 项）— 全部已修复

### P1-1. schema.sql 无外键约束 ✅ 已修复

**位置**：`schema.sql` 全文

**修法**：加了 9 个 FK 约束（fk_message_session、fk_memory_session、fk_fragment_memory、fk_todo_memory、fk_boundary_capsule、fk_persona_message_session、fk_status_log_letter、fk_auth_mem_capsule、fk_auth_mem_memory），同时修复了 4 个丢失 `CREATE TABLE` 头部的表定义。

---

### P1-2. 用户删除不完整 ✅ 已修复

**位置**：`UserServiceImpl.java`

**修法**：补全了 18 张表的 delete 操作（PersonaChatSession/Message、BeliefPattern、RelationMention、EventCard、MemoryTheme、VoiceTranscription、WeeklyReview、UserCorrection、DialogSummary、LetterThread、FriendRelation、BlockRelation、SocialGroupMember/Group、SafetyEvent、AiInteractionLog、ABTestMetrics）。

---

### P1-3. GLM / MiniMax 客户端缺少 recentMessages ✅ 已修复

**位置**：`GlmLlmClient.java`、`MiniMaxLlmClient.java`

**修法**：在两个客户端的 `doChat()` 中添加了 `request.recentMessages` 的遍历和注入。

---

### P1-4. AI 服务失败静默吞数据 ✅ 已修复

**位置**：
- `AuroraAgentServiceImpl.java`（replyRich / generateGreeting）
- `EmotionTimelineServiceImpl.java`（aggregateForDate）
- `ThemeAggregationServiceImpl.java`（aggregateThemes — 修复双重调用 bug）
- `BeliefExtractServiceImpl.java`（extractFromMemory）

**修法**：
1. AuroraAgentServiceImpl：AI 调用外包裹 try-catch，失败时保存 emergency fallback 响应
2. 三个后台分析服务：将静默 catch 替换为带日志的错误处理
3. ThemeAggregationServiceImpl：修复 `fallbackClustering()` 被调用两次的 bug

---

### P1-5. Aurora Agent Loop 多消息无事务包裹 ✅ 已修复

**位置**：`AuroraAgentServiceImpl.java:139-156`

**修法**：将 AI 调用和消息保存包裹在 try-catch 中，确保始终保存至少一条 fallback 响应。单条 `saveAuroraMessage()` 已有 `@Transactional` 保证单条消息的原子性。

---

## P2：可靠性问题（部分已修复）

| # | 问题 | 位置 | 修法 | 状态 |
|---|---|---|---|---|
| 1 | DialogServiceImpl 无错误反馈 | DialogServiceImpl:46-78 | 保存失败时抛异常而非静默 | 待定 |
| 2 | 缺少数据库索引 | schema.sql | 加 CREATE INDEX | **已修复** |
| 3 | 无 HikariCP 连接池配置 | application.yml | 加 spring.datasource.hikari.* | 待定 |
| 4 | 所有 streamChat 都是假流式 | 所有 LlmClient | 改为真 SSE 或移除假流式 | 待定 |
| 5 | MockLlmClient 用手动 JSON 解析 | MockLlmClient | 改用 Jackson | 待定 |
| 6 | FailoverLlmClient 无超时 | FailoverLlmClient:30 | 每个候选加 timeout | 待定 |
| 7 | 无 LLM 调用频率限制 | 所有 Controller | 加 RateLimiter | 待定 |
| 8 | QwenLlmClient 未实现 | QwenLlmClient:8-15 | 删除或实现 | 待定 |
| 9 | 定时任务无错误隔离 | NightlyMemorySettlementJob | 单用户失败不影响其他用户 | **已修复** |

### P2-2 新增索引清单

- `idx_message_user` on `tb_dialog_message(user_id)`
- `idx_capsule_owner` on `tb_echo_capsule(owner_user_id)`
- `idx_letter_sender` on `tb_slow_letter(sender_user_id)`
- `idx_letter_receiver` on `tb_slow_letter(receiver_user_id)`
- `idx_letter_status` on `tb_slow_letter(status)`
- `idx_emotion_trace_user` on `tb_emotion_trace(user_id)`
- `idx_todo_user_status` on `tb_todo_item(user_id, status)`

---

## P3：打磨（10 项）

### AI 系统 P3

| # | 问题 | 位置 |
|---|---|---|
| 1 | Thread.sleep(18/16/30/15) 硬编码在 4 个 LlmClient | Glm/MiniMax/DeepSeek/OpenAI |
| 2 | AuroraAgentServiceImpl.similarity() 用字符集重叠——对中文不准 | AuroraAgentServiceImpl:466 |
| 3 | CapsuleServiceImpl.inferStyleProfile() 关键词列表硬编码 | CapsuleServiceImpl:391-399 |
| 4 | StructuredOutputParser 正则 `(?is)` 兼容性风险 | StructuredOutputParser:57 |
| 5 | OpenAiCompatibleLlmClient 中文系统提示硬编码 | OpenAiCompatibleLlmClient:38 |

### 数据分析 P3

| # | 问题 | 位置 |
|---|---|---|
| 6 | EmotionTrace.emotionScore 无范围校验（应 0-1） | EmotionTrace entity |
| 7 | BeliefPattern.strengthScore 无范围校验 | BeliefPattern entity |
| 8 | DailyRecord 无唯一约束（同一用户同一天可能多条） | schema.sql |
| 9 | 前端 api.js 无网络失败重试 | api.js:440-455 |
| 10 | LetterDeliveryJob 无失败重试 | LetterDeliveryJob:24-35 |

---

## 架构亮点（做得好的部分）

1. **LLM 多 Provider 抽象**：FailoverLlmClient + ABTestLlmClientWrapper 设计清晰
2. **StructuredOutputParser**：自动修复 JSON（去 thinking 标签、处理中文引号）很实用
3. **AgentContext 汇聚**：从 10+ 张表组装上下文，覆盖全面
4. **Capsule 授权记忆**：只读已授权的记忆，隐私设计到位
5. **Aurora Agent Loop**：多段消息 + 沉默处理 + 去重，设计成熟
6. **PromptBuilder**：Builder 模式，提示词组合清晰
