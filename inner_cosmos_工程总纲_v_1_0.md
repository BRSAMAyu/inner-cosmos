# Inner Cosmos 内宇宙：AI 自我共鸣与慢社交平台工程总纲 v1.0

> 本文档是 Inner Cosmos / 内宇宙 项目的工程纲领文件。后续 Codex、Claude Code 或其他开发 Agent 均以本文档为最高优先级工程依据。  
> 目标不是生成一个普通 AI 聊天网站，而是搭建一个可运行、可扩展、能满足 Java 课程作业要求，并具备未来多端产品化潜力的 AI Agent 原型系统。

---

## 0. 项目背景与课程硬性要求

### 0.1 课程要求摘要

本项目是 Java 课程大作业，必须满足：

1. 基于 Java 语言开发一个 AI Agent 应用网站。
2. 使用 MVC 架构。
3. 至少包含前端、后端、数据库。
4. 后台调用大模型 API，例如 DeepSeek、Qwen、Kimi 等。
5. 代码量大于 10000 行。
6. 至少使用 3 种设计模式。
7. 提交内容包括：
   - 可执行软件及源代码。
   - 软件安装说明书。
   - 软件功能说明书。
   - 软件设计文档，必须包括系统架构图、类图及其说明、设计模式说明。
   - AI 交互记录。
8. 网页或功能数量大于 10。
9. 综合评价关注：实用性、创新性、有趣性、AI 功能丰富性；尤其不能只是简单转发用户输入给大模型。

### 0.2 项目交付目标

本项目必须同时满足两种目标：

#### 课程交付目标

- 能在老师本地环境中成功运行。
- 默认不依赖真实大模型 API，也能演示完整流程。
- 页面数量、功能数量、代码量、设计模式、文档内容均满足评分要求。
- 代码结构清晰，MVC 分层明确。
- 有完整 AI 调用日志，能证明 AI 功能不是简单 API 转发。

#### 产品原型目标

- 保留“内宇宙”的核心灵魂：朋友式主动对话、自我记录、思维整理、情感重力记忆、授权共鸣体、慢信件连接。
- 为未来多端推广保留扩展接口。
- 不做心理诊断，不替代心理咨询，但提供温柔的自我观察与现实连接支持。

---

## 1. 项目最终定义

### 1.1 项目名称

**Inner Cosmos 内宇宙**

### 1.2 副标题

**基于 Java Web 与大模型 API 的 AI 自我共鸣与慢社交平台**

### 1.3 一句话定义

Inner Cosmos 是一个通过朋友式 AI Agent 对话帮助用户自然记录内心、整理思维、沉淀情感重力记忆，并在用户授权下生成“共鸣体”数字回声，最终通过慢信件机制引导真实连接的 AI 自我探索平台。

### 1.4 核心原则

> AI 是镜子，不是医生；AI 是桥梁，不是真人的替代品。

### 1.5 项目不做什么

本系统明确不做以下内容：

- 不做心理诊断。
- 不做疾病识别。
- 不宣称治疗抑郁、焦虑或其他心理疾病。
- 不替代心理咨询师、医生、热线或现实支持系统。
- 不把用户原始日记暴露给其他用户或其他用户的 Agent。
- 不让 Agent 后台无限自动社交。
- 不持久化存储用户原始语音文件。
- 不做即时陌生人聊天产品。

### 1.6 项目做什么

本系统做以下内容：

- 让用户像和朋友聊天一样自然记录每日经历与感受。
- 支持文字输入与语音转写输入。
- AI Agent 主动追问、澄清、整理，而不是让用户独自写日记。
- 将原始倾诉转化为事件、情绪、认知碎片、待办、关系提及和长期主题。
- 引入情感重力机制，让重要记忆在“记忆星空”中显现。
- 在用户授权下，将部分抽象、脱敏后的记忆转化为“共鸣体胶囊”。
- 其他用户可以与共鸣体有限交流，若产生共鸣，再通过慢信件联系真实用户。
- 系统内置哲学/文学/艺术类种子共鸣体，用于冷启动与演示。
- 通过安全过滤、节律保护、支持资源页保护用户边界。

---

## 2. V1 版本边界

### 2.1 V1 必须实现的核心闭环

V1 必须实现以下完整链路：

```text
用户文字 / 语音倾诉
 ↓
Aurora 朋友式主动追问
 ↓
保存 P0 原始对话文本与语音元数据
 ↓
用户结束今日对话 / 系统触发整理
 ↓
生成 P1 记忆卡片、情绪轨迹、思维碎片、待办事项
 ↓
计算 emotional_gravity 情感重力
 ↓
在记忆星空中可视化
 ↓
Aurora 主动建议将部分高重力记忆编织成共鸣体
 ↓
用户授权
 ↓
生成 P2 共鸣体胶囊
 ↓
星海广场展示用户共鸣体 + 种子 NPC 共鸣体
 ↓
其他用户与共鸣体有限轮次对话
 ↓
深聊后引导慢信
 ↓
慢信件进入投递、送达、阅读、回复、拒绝、拉黑状态流
```

### 2.2 V1 必做模块

| 模块 | 必做功能 |
|---|---|
| 用户系统 | 注册、登录、退出、用户中心、Session 校验 |
| Aurora 对话 | 文字输入、语音转写入口、SSE 流式回复、主动追问 |
| P0 原始记录 | 保存对话 Session、Message、语音元数据，不保存原始音频 |
| P1 记忆系统 | MemoryCard、ThoughtFragment、EmotionTrace、TodoItem |
| 情感重力 | 非线性 gravity 计算、时间衰减、重要性标记 |
| 记忆星空 | ECharts 展示记忆卡片大小、亮度、类别 |
| 思维碎纸机 | 输入混乱文本，提取核心诉求与认知碎片 |
| 共鸣体 | 用户授权生成 EchoCapsule，支持脱敏与边界控制 |
| 星海广场 | 展示真实用户共鸣体和系统种子共鸣体 |
| 共鸣体对话 | 每日有限轮次，超限引导慢信 |
| 慢信件 | 写信、投递、送达、阅读、回复、拒绝、拉黑 |
| 安全边界 | 危险词过滤、节律保护、支持资源页 |
| AI 日志 | 保存 prompt、response、模型、耗时、状态、模块 |
| 管理后台 | 用户、共鸣体、信件、AI 日志、举报、系统配置 |
| AI 开发记录 | 提交 AI 交互记录，可放入 docs 或后台页面展示 |

### 2.3 V1 暂不实现

V1 不实现以下功能，避免架构泥潭：

- 真实音频文件持久化。
- 复杂音频情绪识别模型。
- 向量数据库。
- Agent 后台自主社交。
- 多人实时聊天。
- 移动端 App。
- 复杂推荐算法。
- 完整心理测评。
- 真实心理诊断。
- 自动心理危机判断。

---

## 3. 用户体验总设计

### 3.1 体验气质

整体体验应当是：

- 温柔。
- 克制。
- 深邃。
- 安全。
- 有仪式感。
- 有“内在宇宙”的视觉隐喻。
- 不过度治疗化。
- 不过度拟人化。
- 不把 AI 塑造成唯一精神支柱。

### 3.2 核心 UI 风格

建议视觉：

- 深色星空背景。
- 低饱和蓝紫色、灰蓝色、柔和金色点缀。
- 卡片式玻璃质感。
- 细线、微光、星尘、轨道、慢动画。
- 避免花哨霓虹，避免过度赛博朋克。
- UI 应接近 Telegram / 高级写作工具 / 心灵空间，而不是游戏商城。

### 3.3 核心交互入口

#### 入口一：今日和 Aurora 聊聊

用户不需要自己写日记，而是进入一个朋友式聊天页面。

示例交互：

```text
用户：今天其实挺烦的，感觉自己什么都没做好，下午还跟别人有点不愉快。

Aurora：我听到两个部分：一个是你觉得自己没做好，另一个是和别人相处时有不舒服。我们先不急着总结，你想先讲哪一个？
```

#### 入口二：思维碎纸机

用户可以把混乱、愤怒、焦虑、碎碎念一次性倒进去，点击“粉碎并沉淀”。

系统行为：

1. 原始文本作为 P0 私密输入保存或标记为可销毁文本。
2. AI 提取出核心诉求、关键情绪、隐藏需求。
3. P1 层只保留理性化、脱敏化、可沉淀的认知金句或 MemoryCard。
4. 前端展示卡片粉碎、星尘沉淀的仪式感动画。

#### 入口三：记忆星空

用户看到自己长期沉淀出的记忆宇宙：

- 高情感重力记忆显示为大星体。
- 低情感重力记忆显示为微弱星尘。
- 近期触发记忆更明亮。
- 久未触发记忆自然暗淡。
- 用户可点击星体查看对应 MemoryCard。

#### 入口四：星海广场

展示两类共鸣体：

1. 用户授权生成的真实共鸣体。
2. 系统初始化生成的哲学/文学/艺术种子共鸣体。

用户可以与共鸣体有限轮次对话。深聊超过限制后，共鸣体应引导用户写慢信，而不是继续无限提供情绪价值。

#### 入口五：慢信箱

慢信件不是即时聊天，而是郑重书信：

- 写信。
- 投递中。
- 送达。
- 已读。
- 已回复。
- 拒绝。
- 拉黑。
- 归档。

可以加入“宇宙距离 / 投递延迟”机制，但 V1 可用简单延迟字段模拟。

---

## 4. 系统总体架构

### 4.1 架构原则

1. MVC 分层明确。
2. 默认可离线 Mock 演示。
3. AI Client 可替换。
4. ASR Client 可替换。
5. 原始私密数据和社交公开数据物理隔离。
6. 用户触发式 Agent 社交，避免后台无限烧 Token。
7. 事件驱动整理，不阻塞用户聊天。
8. 多页面物理存在，满足课程评审对网页数量的直觉。
9. 前端尽量轻量，避免 Node 构建复杂度。

### 4.2 技术栈

#### 后端

```text
Java 17
Spring Boot 3.x
Spring MVC
Spring Validation
MyBatis-Plus
MySQL 8.x
Jackson
SseEmitter
Spring ApplicationEvent
ThreadPoolTaskExecutor
Session / Interceptor
```

#### 可选增强

```text
Redis：短期上下文缓存、限流、热点数据缓存
如果 Redis 不可用，必须提供 InMemoryFallbackCache
```

#### 前端

```text
多物理 HTML 页面
Vue 3 CDN / 本地 vendor 版
Tailwind CSS CDN / 本地 vendor 版
ECharts 本地 vendor 版
原生 Fetch API
EventSource 接收 SSE
localStorage 做轻量页面间缓存
```

交付包中建议将 Vue、Tailwind、ECharts 放到：

```text
src/main/resources/static/vendor/
```

避免老师电脑断网导致页面无法加载。

#### 大模型

```text
默认模式：mock
远程模式：deepseek / qwen / kimi / openai-compatible
```

必须通过 Adapter 封装，不允许业务层直接依赖某一家 API。

#### ASR

V1 只做轻量方案：

```text
浏览器语音识别 / Mock ASR / 可选远程 ASR
```

后端不存储原始音频，只保存转写文本与语音元数据。

---

## 5. 数据分层模型：P0 / P1 / P2 / P3

### 5.1 P0：原始私密输入层

P0 是用户最私密的数据层。

包含：

- 用户原始聊天文本。
- Aurora 原始回复。
- 语音转写文本。
- 语音元数据。
- 思维碎纸机原始输入。

原则：

- P0 默认只供用户本人和本人 Aurora Agent 使用。
- P0 永不直接进入社交层。
- P0 不暴露给其他用户的 Agent。
- P0 不用于星海广场展示。
- P0 可被整理成 P1，但 P1 必须是总结化、结构化结果。

### 5.2 P1：结构化记忆层

P1 是系统从 P0 中提取出的结构化自我记忆。

包含：

- MemoryCard：记忆卡片。
- EmotionTrace：情绪轨迹。
- ThoughtFragment：认知碎片。
- EventCard：事件卡。
- TodoItem：待办事项。
- RelationMention：关系人物提及。
- MemoryTheme：长期主题。

原则：

- P1 是内宇宙的核心。
- Aurora 后续回复优先检索高情感重力 P1 记忆。
- P1 可视化为记忆星空。
- P1 不能默认公开。
- 用户可选择部分 P1 经过脱敏后进入 P2。

### 5.3 P2：授权共鸣体胶囊层

P2 是用户主动授权后生成的数字表达体。

包含：

- EchoCapsule。
- CapsuleTag。
- CapsuleBoundary。
- CapsuleEnergy。
- AuthorizedMemoryRef。

原则：

- P2 只能由用户授权生成。
- P2 不包含原始日记文本。
- P2 不包含具体真实姓名、地址、精确时间、联系方式等敏感信息。
- P2 是“数字回声”，不是用户本人。
- P2 只允许有限轮次对话。

### 5.4 P3：慢社交与公开交互层

P3 是真实用户之间间接连接的社交层。

包含：

- PersonaChatSession。
- PersonaChatMessage。
- SlowLetter。
- LetterStatusLog。
- BlockRelation。
- ReportRecord。

原则：

- 其他用户先与共鸣体有限交流。
- 若产生兴趣，再写慢信给真实用户。
- 真实用户可回信、拒绝、忽略、拉黑、举报。
- 不做即时陌生人聊天。
- 共鸣体是连接引导，不是真人替代。

---

## 6. 核心领域模型

### 6.1 User

用户基础实体。

字段建议：

```text
id
username
passwordHash
nickname
avatarUrl
email
role
status
createdAt
updatedAt
lastLoginAt
```

### 6.2 UserProfile

用户个人配置。

字段建议：

```text
id
userId
auroraName
auroraTone
preferredInputType
socialReachabilityStatus  // GREEN / YELLOW / RED / CLOSED
bio
createdAt
updatedAt
```

### 6.3 DialogSession

一次 Aurora 对话。

字段建议：

```text
id
userId
title
sessionType       // AURORA_CHAT / THOUGHT_SHREDDER / PERSONA_CHAT
status            // ACTIVE / FINISHED / ARCHIVED
summaryAnchor     // 滑动窗口压缩出的语义锚点
messageCount
tokenEstimate
startedAt
endedAt
createdAt
updatedAt
```

### 6.4 DialogMessage

对话消息。

字段建议：

```text
id
sessionId
userId
speaker           // USER / AURORA / SYSTEM / CAPSULE
textContent
inputType         // TEXT / VOICE / MOCK
audioDurationSec
speechRate
pauseCount
longPauseCount
emotionHint
safetyLevel
createdAt
```

注意：不保存原始音频文件路径。

### 6.5 MemoryCard

P1 层核心记忆卡片。

字段建议：

```text
id
userId
sourceSessionId
title
summary
memoryType        // EVENT / EMOTION / COGNITION / RELATION / VALUE / TODO / OTHER
emotionTags       // JSON string
keywordTags       // JSON string
peopleTags        // JSON string
intensityScore    // I，情绪强度
recurrenceCount   // R，复现次数
userImportance    // U，用户手动重要性
triggerCount      // M，近期触发次数
emotionalGravity  // G(t)，最终情感重力
lastTouchedAt
visibilityLevel   // PRIVATE / CANDIDATE / AUTHORIZED / PUBLIC_ABSTRACT
status            // ACTIVE / ARCHIVED / DELETED
createdAt
updatedAt
```

### 6.6 ThoughtFragment

认知碎片。

字段建议：

```text
id
userId
memoryCardId
fragmentType      // FACT / FEELING / WORRY / NEED / BELIEF / ACTION
rawExcerpt
aiAnalysis
reframeText
createdAt
updatedAt
```

### 6.7 EmotionTrace

情绪轨迹。

字段建议：

```text
id
userId
sourceSessionId
emotionName
emotionScore
weatherType       // SUNNY / CLOUDY / RAINY / STORM / FOGGY
triggerScene
recordDate
createdAt
```

### 6.8 TodoItem

从对话中提取出的待办。

字段建议：

```text
id
userId
sourceMemoryCardId
taskName
description
priority
status            // TODO / DOING / DONE / CANCELLED
deadline
createdAt
updatedAt
```

### 6.9 EchoCapsule

共鸣体胶囊。

字段建议：

```text
id
ownerUserId        // 系统种子胶囊可为空或为 system user
capsuleType        // USER_CAPSULE / SEED_CAPSULE
pseudonym
intro
personaPrompt
publicTags         // JSON string
authorizedMemoryIds // JSON string or mapping table
echoEnergy
freshnessScore
conversationLimitPerDay
visibilityStatus   // PUBLIC / PRIVATE / HIDDEN / ARCHIVED
isPublic
lastMemoryUpdateAt
createdAt
updatedAt
```

### 6.10 CapsuleBoundary

共鸣体边界。

字段建议：

```text
id
capsuleId
allowTopics        // JSON
blockedTopics      // JSON
maxConversationTurns
allowLetterRequest
privacyLevel       // STRICT / NORMAL / OPEN_ABSTRACT
createdAt
updatedAt
```

### 6.11 PersonaChatSession

用户与共鸣体的会话。

字段建议：

```text
id
visitorUserId
capsuleId
status            // ACTIVE / LIMITED / LETTER_GUIDED / CLOSED
turnCount
dailyLimit
createdAt
updatedAt
```

### 6.12 PersonaChatMessage

共鸣体聊天消息。

字段建议：

```text
id
sessionId
senderType        // VISITOR / CAPSULE / SYSTEM
textContent
createdAt
```

### 6.13 SlowLetter

慢信件。

字段建议：

```text
id
senderUserId
receiverUserId
receiverCapsuleId
title
letterBody
status            // DRAFT / SENT / FLYING / DELIVERED / READ / REPLIED / DECLINED / BLOCKED / ARCHIVED
parallaxDistance
estimatedArrivalAt
sentAt
deliveredAt
readAt
repliedAt
createdAt
updatedAt
```

### 6.14 LetterStatusLog

信件状态流转日志。

字段建议：

```text
id
letterId
fromStatus
toStatus
operatorUserId
reason
createdAt
```

### 6.15 AiInteractionLog

AI 调用日志，必须实现。

字段建议：

```text
id
userId
moduleName        // AURORA_CHAT / MEMORY_EXTRACT / CAPSULE_CHAT / LETTER_FILTER / MOCK
provider          // MOCK / DEEPSEEK / QWEN / KIMI
modelName
requestPrompt
responseText
requestJson
responseJson
success
errorMessage
latencyMs
tokenInputEstimate
tokenOutputEstimate
createdAt
```

### 6.16 SafetyEvent

安全事件。

字段建议：

```text
id
userId
sessionId
messageId
riskType          // CRISIS_KEYWORD / ABUSE / SPAM / OVERUSE / BOUNDARY
riskLevel         // LOW / MEDIUM / HIGH
matchedRule
handledAction     // BLOCKED / REDIRECTED / WARNED / RESOURCE_PAGE
createdAt
```

### 6.17 ReportRecord

举报记录。

字段建议：

```text
id
reporterUserId
targetType        // CAPSULE / LETTER / MESSAGE / USER
targetId
reason
status            // PENDING / REVIEWED / RESOLVED / REJECTED
createdAt
updatedAt
```

---

## 7. 情感重力算法

### 7.1 设计目标

情感重力用于判断哪些记忆更值得在内宇宙中被看见、被检索、被编织。

它必须解决：

- 避免单次极端事件撑爆 UI。
- 避免复现次数无限线性增长。
- 让旧记忆随时间自然暗淡。
- 允许用户手动标记重要记忆。
- 允许反复触发的主题重新变亮。

### 7.2 V1 公式

使用非线性饱和 + 时间衰减：

```text
G(t) = ln(1 + αI + βR + γU + δM) × e^(-λt)
```

含义：

| 参数 | 含义 |
|---|---|
| I | emotion intensity，情绪强度，0~10 |
| R | recurrence count，复现次数 |
| U | user importance，用户手动重要性，0~10 |
| M | recent trigger count，近期触发次数 |
| t | 距离 lastTouchedAt 的天数 |
| λ | 时间衰减系数，建议 0.03~0.08 |

默认权重：

```text
α = 0.40
β = 0.25
γ = 0.25
δ = 0.10
λ = 0.05
```

### 7.3 Java 实现示意

```java
public double calculateGravity(double intensity,
                               int recurrenceCount,
                               double userImportance,
                               int triggerCount,
                               long daysSinceLastTouched) {
    double alpha = 0.40;
    double beta = 0.25;
    double gamma = 0.25;
    double delta = 0.10;
    double lambda = 0.05;

    double base = alpha * intensity
                + beta * recurrenceCount
                + gamma * userImportance
                + delta * triggerCount;

    return Math.log(1 + Math.max(base, 0))
            * Math.exp(-lambda * Math.max(daysSinceLastTouched, 0));
}
```

### 7.4 前端展示映射

```text
星体大小 = normalize(gravity)
星体亮度 = freshnessScore
星体颜色 = memoryType / emotionTags
星体位置 = 可先随机稳定布局，后续再做聚类布局
```

V1 不要求复杂物理模拟，使用 ECharts scatter / graph 即可。

---

## 8. 共鸣体机制

### 8.1 共鸣体定义

共鸣体不是用户本人，而是：

> 基于用户授权信息生成的、有限的、脱敏的、可衰减的数字回声。

### 8.2 共鸣体类型

```text
USER_CAPSULE：真实用户授权生成
SEED_CAPSULE：系统内置种子共鸣体
```

### 8.3 共鸣体边界

共鸣体只能访问：

```text
EchoCapsule.personaPrompt
EchoCapsule.publicTags
CapsuleBoundary.allowTopics
用户授权后的抽象 MemoryCard 摘要
```

共鸣体不能访问：

```text
DialogMessage 原文
ThoughtFragment rawExcerpt
用户真实邮箱
用户真实身份
用户私密日记
未授权 MemoryCard
```

### 8.4 回声衰减机制

共鸣体活跃度不代表用户价值，而是代表授权信息的新鲜度。

字段：

```text
echoEnergy
freshnessScore
lastMemoryUpdateAt
visibilityStatus
```

计算建议：

```text
echoEnergy = baseEnergy × freshnessFactor × activityFactor
```

展示：

| 状态 | 展示 |
|---|---|
| 高能量 | 明亮星体，优先推荐 |
| 中能量 | 柔和发光 |
| 低能量 | 安静漂浮，推荐权重下降 |
| 隐藏 | 不在星海广场出现 |

### 8.5 对话限制

V1 建议：

```text
每个访问者与同一共鸣体每日最多 5 轮对话。
```

超过后，共鸣体应回复：

```text
我只是这个人的一部分回声，不能替他完整回答这个问题。
如果你真的想继续了解，也许可以写一封慢信给他。
```

### 8.6 主动编织机制

当某张 MemoryCard 的 gravity 超过阈值，Aurora 可以建议：

```text
我注意到你最近反复提到了“面对权威时的压抑感”。我把它整理成了一颗记忆星星。你愿意把它经过脱敏后放入星海吗？也许有人会因此感到不孤单。
```

用户点击：

```text
放入星海
暂时只留给自己
以后再说
```

若选择放入星海：

1. DataMaskingService 脱敏。
2. CapsuleService 更新 EchoCapsule。
3. 记录授权日志。

---

## 9. 慢信件机制

### 9.1 设计原则

慢信件不是即时聊天，而是郑重连接。

它的目标：

- 避免陌生人即时骚扰。
- 给表达留出时间。
- 让用户认真写下自己想说的话。
- 通过状态流保护接收者。

### 9.2 信件状态机

状态：

```text
DRAFT      草稿
SENT       已发送
FLYING     投递中
DELIVERED  已送达
READ       已读
REPLIED    已回复
DECLINED   已拒绝
BLOCKED    已拉黑
ARCHIVED   已归档
```

允许流转：

```text
DRAFT -> SENT
SENT -> FLYING
FLYING -> DELIVERED
DELIVERED -> READ
READ -> REPLIED
READ -> DECLINED
DELIVERED -> DECLINED
ANY -> BLOCKED
READ / REPLIED / DECLINED -> ARCHIVED
```

### 9.3 视差投递机制

V1 可实现轻量版：

```text
parallaxDistance = 根据双方标签重合度、情感主题相似度计算出的象征距离
estimatedArrivalAt = sentAt + delayHours
```

规则可简化：

```text
高匹配：2 小时后送达
中匹配：8 小时后送达
低匹配但存在一个强共鸣点：24 小时后送达
```

课程演示可提供“立即推进状态”的管理员按钮。

### 9.4 信件安全过滤

所有慢信件在送达真人前必须经过 LetterSafetyFilter。

过滤内容：

- 辱骂。
- 色情骚扰。
- 威胁。
- 广告。
- 过度索取联系方式。
- 诱导转账。
- 明显恶意试探。

未通过时：

- 不送达真人。
- 写入 SafetyEvent。
- 给发送者温和提示修改。

---

## 10. 安全与心理边界

### 10.1 产品免责声明

系统首页、注册页、支持资源页应出现简洁说明：

```text
Inner Cosmos 不是医疗或心理咨询服务。它提供的是自我记录、思维整理、情绪表达和社交共鸣支持。当你持续处于强烈痛苦、失控或现实功能受损时，请优先联系现实中可信任的人或专业支持。
```

### 10.2 高风险词硬拦截

后端必须实现 SafetyBoundaryFilter。

目标：

- 不让高风险内容直接交给大模型自由发挥。
- 不做诊断。
- 在必要时引导现实支持。

处理流程：

```text
用户输入
 ↓
SafetyBoundaryFilter
 ↓
若低风险：继续普通 Agent 流程
若中风险：普通回复 + 温和支持资源提示
若高风险：中断普通 Agent，进入支持资源页面 / 固定安全回复
```

固定回复不要使用冷冰冰语气，应温和、现实导向。

### 10.3 节律保护

如果用户连续对话时间过长或消息数量过多：

```text
触发 SessionDurationLimiter / TokenBudgetLimiter
```

Aurora 不应假装“自己累了”，而应透明引导：

```text
我们已经聊了很久了。为了让这些内容真正沉淀下来，我建议你先暂停一下。我会帮你整理今天的重要内容，你可以喝点水、走一走，稍后再回来。
```

### 10.4 社交可达状态

用户可以设置：

```text
GREEN：愿意接收新信件
YELLOW：只接收高匹配信件
RED：暂不接收信件，只允许别人和共鸣体对话
CLOSED：隐藏共鸣体，不参与星海广场
```

---

## 11. AI Agent 设计

### 11.1 Agent 列表

V1 中可以实现以下 Agent：

| Agent | 职责 |
|---|---|
| AuroraAgent | 朋友式主动对话，陪用户自然记录 |
| MemoryExtractAgent | 从 P0 对话中提取 P1 记忆 |
| ThoughtShredderAgent | 将混乱文本提取为核心诉求 |
| CapsuleAgent | 与访问者进行有限共鸣体对话 |
| LetterGuardAgent | 辅助判断慢信件是否越界 |
| MockAgent | 本地模拟大模型，保证离线运行 |

### 11.2 Aurora 回复原则

Aurora 应该：

- 像朋友一样温柔，但不假装真人。
- 主动追问，但不逼迫用户。
- 承认不确定性。
- 帮用户整理，而不是替用户下结论。
- 不诊断。
- 不贴人格标签。
- 不把用户推向依赖。

### 11.3 Prompt 组装原则

Aurora Prompt 应包含：

```text
系统角色边界
安全边界
最近 3 轮原始对话
历史摘要锚点 summaryAnchor
高重力 MemoryCard 摘要
本轮用户输入
语音元数据提示
输出要求
```

### 11.4 滑动窗口 + 语义锚点

避免上下文无限膨胀。

规则：

```text
只保留最近 3 轮原始对话。
更早内容压缩成 summaryAnchor。
高重力 MemoryCard 只取前 3~5 条摘要。
```

---

## 12. 大模型与 Mock 模式

### 12.1 必须支持 Mock 模式

`application.yml`：

```yaml
llm:
  mode: mock        # mock / remote
  provider: mock    # mock / deepseek / qwen / kimi
  api-key: ""
  base-url: ""
  model: "mock-inner-cosmos"
```

默认交付必须使用：

```text
llm.mode = mock
```

原因：

- 老师本地可能断网。
- 老师没有 API Key。
- 避免运行失败。
- 避免 API 成本。

### 12.2 LlmClient 接口

```java
public interface LlmClient {
    String chat(LlmRequest request);
    SseEmitter streamChat(LlmRequest request);
}
```

实现：

```text
MockLlmClient
DeepSeekLlmClient
QwenLlmClient
KimiLlmClient
```

### 12.3 Mock 行为要求

Mock 不是简单返回固定文本，应根据关键词模拟：

| 用户输入 | Mock 回复倾向 |
|---|---|
| 烦、累、压力 | 温柔追问压力来源 |
| 不想做、拖延 | 帮助拆分事件与待办 |
| 孤独、没人懂 | 引导表达具体场景 |
| 高兴、开心 | 帮助记录积极事件 |
| 作业、考试 | 提取 TodoItem |
| 关系冲突 | 提取 RelationMention |

Mock 也要写入 AiInteractionLog。

---

## 13. ASR 与语音元数据

### 13.1 原则

- 不保存原始音频。
- 语音只是输入方式，不是心理判断依据。
- 元数据只是辅助追问线索。

### 13.2 保存字段

```text
textContent
inputType = VOICE
audioDurationSec
speechRate
pauseCount
longPauseCount
emotionHint
```

### 13.3 Prompt 注入方式

不要写：

```text
用户很悲伤。
```

应该写：

```text
系统观察到：本段语音中存在较长停顿，用户语速偏慢。请在回应时保持温和，并避免直接判断用户情绪。
```

### 13.4 V1 实现

V1 可以采用：

- 浏览器 Web Speech API。
- 文本输入模拟语音转写。
- Mock ASR 返回固定元数据。
- 可选远程 ASR Adapter。

---

## 14. 设计模式落点

V1 至少实现 5 种，但文档中重点说明前 3 种。

### 14.1 适配器模式 Adapter

用途：封装不同 LLM / ASR。

```text
LlmClient
├── MockLlmClient
├── DeepSeekLlmClient
├── QwenLlmClient
└── KimiLlmClient

AsrClient
├── MockAsrClient
├── BrowserAsrAdapter
└── RemoteAsrClient
```

价值：业务层不依赖具体厂商，便于离线演示和未来扩展。

### 14.2 观察者模式 Observer

用途：对话结束后触发多个整理任务。

```text
DialogFinishedEvent
├── MemoryExtractListener
├── EmotionTraceListener
├── TodoExtractListener
├── GravityRecalculateListener
└── CapsuleSuggestionListener
```

价值：聊天体验不被耗时整理阻塞。

### 14.3 状态模式 State

用途：慢信件状态流转。

```text
LetterState
├── DraftState
├── SentState
├── FlyingState
├── DeliveredState
├── ReadState
├── RepliedState
├── DeclinedState
├── BlockedState
└── ArchivedState
```

价值：避免在 Service 中写大量 if-else，清晰表达业务生命周期。

### 14.4 建造者模式 Builder

用途：构造复杂 Prompt。

```text
PromptBuilder
.withSystemBoundary()
.withRecentMessages()
.withSummaryAnchor()
.withGravityMemories()
.withVoiceMetadata()
.withOutputSchema()
.build()
```

### 14.5 策略模式 Strategy

用途：不同 Agent / 共鸣体 / 哲学种子胶囊有不同回应策略。

```text
AgentReplyStrategy
├── AuroraCompanionStrategy
├── ThoughtShredderStrategy
├── StoicSeedStrategy
├── SocraticSeedStrategy
└── CapsuleChatStrategy
```

---

## 15. 后端包结构

建议包名：

```text
com.innercosmos
```

结构：

```text
com.innercosmos
├── InnerCosmosApplication.java
│
├── config
│   ├── WebMvcConfig.java
│   ├── MybatisPlusConfig.java
│   ├── ThreadPoolConfig.java
│   ├── LlmConfig.java
│   └── MockDataInitializer.java
│
├── common
│   ├── ApiResponse.java
│   ├── PageResult.java
│   ├── ErrorCode.java
│   └── Constants.java
│
├── exception
│   ├── GlobalExceptionHandler.java
│   ├── BusinessException.java
│   ├── SafetyBlockedException.java
│   ├── LetterStateException.java
│   └── AiProviderException.java
│
├── entity
│   ├── User.java
│   ├── UserProfile.java
│   ├── DialogSession.java
│   ├── DialogMessage.java
│   ├── MemoryCard.java
│   ├── ThoughtFragment.java
│   ├── EmotionTrace.java
│   ├── TodoItem.java
│   ├── EchoCapsule.java
│   ├── CapsuleBoundary.java
│   ├── PersonaChatSession.java
│   ├── PersonaChatMessage.java
│   ├── SlowLetter.java
│   ├── LetterStatusLog.java
│   ├── AiInteractionLog.java
│   ├── SafetyEvent.java
│   └── ReportRecord.java
│
├── mapper
│   ├── UserMapper.java
│   ├── DialogSessionMapper.java
│   ├── DialogMessageMapper.java
│   ├── MemoryCardMapper.java
│   ├── EchoCapsuleMapper.java
│   ├── SlowLetterMapper.java
│   └── AiInteractionLogMapper.java
│
├── dto
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── ChatRequest.java
│   ├── MemoryExtractRequest.java
│   ├── CapsuleCreateRequest.java
│   ├── LetterCreateRequest.java
│   └── SafetyCheckRequest.java
│
├── vo
│   ├── UserProfileVO.java
│   ├── ChatMessageVO.java
│   ├── MemoryCardVO.java
│   ├── StarfieldVO.java
│   ├── EchoCapsuleVO.java
│   ├── SlowLetterVO.java
│   └── AiLogVO.java
│
├── controller
│   ├── AuthController.java
│   ├── UserController.java
│   ├── AuroraChatController.java
│   ├── MemoryController.java
│   ├── ThoughtShredderController.java
│   ├── CapsuleController.java
│   ├── PlazaController.java
│   ├── LetterController.java
│   ├── SafetyController.java
│   ├── AiLogController.java
│   └── AdminController.java
│
├── service
│   ├── UserService.java
│   ├── AuroraAgentService.java
│   ├── DialogService.java
│   ├── MemoryService.java
│   ├── GravityService.java
│   ├── ThoughtShredderService.java
│   ├── CapsuleService.java
│   ├── PersonaChatService.java
│   ├── SlowLetterService.java
│   ├── SafetyService.java
│   ├── AiLogService.java
│   └── AdminService.java
│
├── service.impl
│   └── ...
│
├── ai
│   ├── client
│   │   ├── LlmClient.java
│   │   ├── MockLlmClient.java
│   │   ├── DeepSeekLlmClient.java
│   │   └── QwenLlmClient.java
│   ├── prompt
│   │   ├── PromptBuilder.java
│   │   ├── PromptTemplate.java
│   │   └── PromptTemplateRegistry.java
│   ├── agent
│   │   ├── AuroraAgent.java
│   │   ├── MemoryExtractAgent.java
│   │   ├── CapsuleAgent.java
│   │   └── LetterGuardAgent.java
│   └── strategy
│       ├── AgentReplyStrategy.java
│       ├── AuroraCompanionStrategy.java
│       ├── ThoughtShredderStrategy.java
│       └── CapsuleChatStrategy.java
│
├── event
│   ├── DialogFinishedEvent.java
│   ├── MemoryExtractListener.java
│   ├── EmotionTraceListener.java
│   ├── TodoExtractListener.java
│   ├── GravityRecalculateListener.java
│   └── CapsuleSuggestionListener.java
│
├── letterstate
│   ├── LetterState.java
│   ├── DraftState.java
│   ├── SentState.java
│   ├── FlyingState.java
│   ├── DeliveredState.java
│   ├── ReadState.java
│   ├── RepliedState.java
│   ├── DeclinedState.java
│   ├── BlockedState.java
│   └── ArchivedState.java
│
├── safety
│   ├── SafetyBoundaryFilter.java
│   ├── CrisisKeywordRule.java
│   ├── AbuseKeywordRule.java
│   ├── SessionDurationLimiter.java
│   ├── TokenBudgetLimiter.java
│   └── ResourceRedirectService.java
│
├── util
│   ├── JsonUtils.java
│   ├── DateTimeUtils.java
│   ├── TextSanitizer.java
│   ├── DataMaskingUtils.java
│   └── TokenEstimateUtils.java
│
└── scheduler
    ├── NightlyMemorySettlementJob.java
    └── LetterDeliveryJob.java
```

---

## 16. 前端页面清单

为满足“网页数量 > 10”，必须存在多个物理 HTML 文件。

建议目录：

```text
src/main/resources/static/pages/
```

页面：

| 文件 | 页面 | 功能 |
|---|---|---|
| index.html | 首页 | 项目介绍、进入内宇宙 |
| login.html | 登录页 | 登录 |
| register.html | 注册页 | 注册 |
| dashboard.html | 宇宙核心大屏 | 今日状态、记忆概览、入口 |
| aurora-chat.html | Aurora 今日对话 | SSE 流式聊天、语音输入 |
| thought-shredder.html | 思维碎纸机 | 混乱输入、粉碎沉淀 |
| daily-record.html | 今日记录卡 | 查看 AI 整理结果 |
| memory-starfield.html | 记忆星空 | ECharts 星图 |
| todo.html | 待办清单 | AI 提取 Todo |
| timeline.html | 成长时间轴 | 重要记忆时间线 |
| capsule-create.html | 共鸣体编织 | 授权生成共鸣体 |
| echo-plaza.html | 星海广场 | 浏览共鸣体与种子 NPC |
| capsule-chat.html | 共鸣体对话 | 有限轮次聊天 |
| slow-letter.html | 写慢信 | 写信、投递 |
| inbox.html | 慢信箱 | 收信、回信、拒绝、拉黑 |
| safety-harbor.html | 安全避风港 | 支持资源页 |
| ai-log.html | AI 交互日志 | 展示调用记录 |
| admin.html | 管理后台 | 用户、日志、举报管理 |

每个页面要有差异化 UI，不要只是同一个模板换标题。

---

## 17. API 设计草案

### 17.1 Auth

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/current
```

### 17.2 Aurora Chat

```text
POST /api/dialog/session/create
GET  /api/dialog/session/{id}
GET  /api/dialog/session/{id}/messages
POST /api/dialog/session/{id}/finish
GET  /api/aurora/stream?sessionId=xxx&message=xxx
POST /api/aurora/message
```

SSE 推荐：

```text
GET /api/aurora/stream
```

返回事件：

```text
event: token
data: {"content":"..."}

event: done
data: {"messageId":123}

event: error
data: {"message":"..."}
```

### 17.3 Memory

```text
POST /api/memory/extract/{sessionId}
GET  /api/memory/cards
GET  /api/memory/cards/{id}
POST /api/memory/cards/{id}/importance
POST /api/memory/cards/{id}/recalculate-gravity
GET  /api/memory/starfield
```

### 17.4 Thought Shredder

```text
POST /api/thought-shredder/process
GET  /api/thought-shredder/history
```

### 17.5 Capsule

```text
GET  /api/capsule/my
POST /api/capsule/create-from-memory
POST /api/capsule/{id}/update-boundary
POST /api/capsule/{id}/visibility
GET  /api/plaza/capsules
GET  /api/plaza/capsules/{id}
```

### 17.6 Persona Chat

```text
POST /api/persona-chat/session/create
GET  /api/persona-chat/session/{id}/messages
GET  /api/persona-chat/stream?sessionId=xxx&message=xxx
```

### 17.7 Slow Letter

```text
POST /api/letters/draft
POST /api/letters/{id}/send
GET  /api/letters/inbox
GET  /api/letters/outbox
GET  /api/letters/{id}
POST /api/letters/{id}/read
POST /api/letters/{id}/reply
POST /api/letters/{id}/decline
POST /api/letters/{id}/block
POST /api/letters/{id}/archive
```

### 17.8 Safety / Admin / Logs

```text
GET  /api/safety/resources
GET  /api/ai-logs
GET  /api/admin/users
GET  /api/admin/capsules
GET  /api/admin/reports
POST /api/admin/reports/{id}/resolve
```

---

## 18. 数据库表建议

V1 建议最少 17 张表：

```text
tb_user
tb_user_profile

tb_dialog_session
tb_dialog_message

tb_memory_card
tb_thought_fragment
tb_emotion_trace
tb_todo_item

tb_echo_capsule
tb_capsule_boundary
tb_persona_chat_session
tb_persona_chat_message

tb_slow_letter
tb_letter_status_log
tb_ai_interaction_log
tb_safety_event
tb_report_record
```

可选：

```text
tb_prompt_template
tb_model_config
tb_system_seed_capsule
```

Codex 在生成 SQL 时要注意：

- 所有表使用 `BIGINT` 主键。
- 所有表包含 `created_at`、`updated_at`。
- 重要查询字段建立索引。
- JSON 可先使用 `TEXT` 存储，降低 MySQL 版本兼容风险。
- 字符集使用 `utf8mb4`。

---

## 19. 初始化种子数据

系统启动时，使用 `CommandLineRunner` 或 SQL 初始化至少 5 个 SEED_CAPSULE。

建议：

1. **斯多葛信使**：关注可控与不可控。
2. **苏格拉底之问**：通过追问帮助用户澄清信念。
3. **庄周之梦**：提供松弛、相对化、逍遥视角。
4. **存在主义旅人**：关注自由、选择、意义创造。
5. **热烈的画家**：关注痛苦、敏感、艺术表达。

不要声称这些是真实人物本人，只说：

```text
基于公开思想与文学气质构建的哲学视角模拟体。
```

---

## 20. 安装与运行要求

### 20.1 默认运行模式

默认必须可在本地无 API Key 运行：

```text
llm.mode = mock
```

### 20.2 最小依赖

```text
JDK 17+
Maven 3.8+
MySQL 8.x
```

Redis 不作为默认必需依赖。

### 20.3 默认账号

初始化：

```text
管理员：admin / admin123
测试用户：demo / demo123
```

### 20.4 访问入口

```text
http://localhost:8080/pages/index.html
```

---

## 21. 代码量策略

目标：新增代码 12000~16000 行。

自然分布：

| 部分 | 预计行数 |
|---|---:|
| Entity / DTO / VO | 2000 |
| Mapper | 500 |
| Controller | 1500 |
| Service / Impl | 3500 |
| AI Client / Agent / Prompt | 2000 |
| Event / Safety / State | 1500 |
| HTML 页面 | 2500 |
| CSS / JS / ECharts | 2000 |
| 文档 / 配置 / SQL | 1000 |

注意：

- 可以不使用 Lombok，显式生成 getter/setter，便于老师检查。
- 可以写规范 JavaDoc。
- 不要写无意义空代码。
- 不要为了行数破坏可维护性。

---

## 22. 开发顺序建议

### Phase 1：项目骨架

Codex 优先完成：

1. Spring Boot 项目结构。
2. `application.yml`。
3. MySQL 连接。
4. MyBatis-Plus 配置。
5. 通用响应结构。
6. 用户注册登录。
7. Mock 模式。
8. 基础页面入口。

### Phase 2：P0 对话与 SSE

1. DialogSession / DialogMessage。
2. AuroraChatController。
3. MockLlmClient SSE 流式输出。
4. Chat 页面。
5. AI 日志入库。

### Phase 3：P1 记忆整理

1. DialogFinishedEvent。
2. MemoryExtractListener。
3. MemoryCard / ThoughtFragment / EmotionTrace / TodoItem。
4. GravityService。
5. 记忆星空页面。

### Phase 4：P2 共鸣体

1. EchoCapsule。
2. CapsuleBoundary。
3. DataMaskingService。
4. 种子胶囊初始化。
5. 星海广场页面。

### Phase 5：P3 慢信件

1. PersonaChat。
2. 对话轮次限制。
3. SlowLetter 状态模式。
4. Inbox / Outbox 页面。
5. LetterSafetyFilter。

### Phase 6：安全、后台、文档

1. SafetyBoundaryFilter。
2. 节律保护。
3. 支持资源页。
4. Admin 页面。
5. 安装说明书。
6. 功能说明书。
7. 设计文档。
8. AI 交互记录整理。

---

## 23. Codex 的第一轮任务边界

Codex 第一轮不要尝试实现所有细节。

Codex 应完成：

1. 项目可启动。
2. 数据库可初始化。
3. 用户可登录。
4. Mock LLM 可工作。
5. Aurora Chat SSE 可演示。
6. 对话可保存。
7. 对话结束可生成 Mock MemoryCard。
8. 记忆星空可展示 Mock / 真实数据。
9. EchoCapsule 基础 CRUD。
10. 星海广场可展示种子胶囊。
11. SlowLetter 基础状态流可跑通。
12. AI 日志可查看。

Codex 第一轮不要做：

- 复杂远程模型。
- 复杂 ASR。
- 复杂推荐算法。
- 复杂 UI 动画。
- 大量页面美化。

这些交给 Claude Code 后续填充。

---

## 24. Claude Code 后续填充方向

Claude Code 适合负责：

1. 页面视觉完善。
2. 多页面 HTML / CSS / JS 代码量扩充。
3. ECharts 图表美化。
4. Mock 回复内容丰富化。
5. Service 细节补齐。
6. 管理后台完善。
7. 文档撰写。
8. 安装说明。
9. 功能说明。
10. 设计文档中的类图、架构图 Mermaid。
11. AI 交互记录页面。

---

## 25. 验收标准

### 25.1 运行验收

- 能启动 Spring Boot。
- 能连接 MySQL。
- 能打开首页。
- 能注册登录。
- 能进入 Aurora 对话页面。
- Mock 模式下能看到流式回复。
- 能保存对话。
- 能生成记忆卡片。
- 能看到记忆星空。
- 能生成共鸣体。
- 能打开星海广场。
- 能和种子共鸣体对话。
- 能写慢信。
- 能查看 AI 日志。

### 25.2 作业验收

- 代码量大于 10000 行。
- 页面数量大于 10。
- 至少 3 种设计模式在代码和文档中明确体现。
- 有架构图。
- 有类图。
- 有数据库设计。
- 有安装说明。
- 有功能说明。
- 有 AI 交互记录。
- 默认 Mock 模式可离线演示。

### 25.3 产品验收

- 不像普通 AI 聊天网站。
- 有朋友式主动对话。
- 有记忆沉淀。
- 有情感重力可视化。
- 有授权共鸣体。
- 有慢信件。
- 有安全边界。
- 有完整世界观和体验一致性。

---

## 26. 最终共识摘要

Inner Cosmos / 内宇宙的最终共识是：

```text
它不是 AI 日记。
它不是心理治疗。
它不是陌生人即时聊天。
它不是简单大模型套壳。

它是一个 AI 自我共鸣与慢社交平台：
用户通过朋友式 Aurora Agent 自然倾诉，系统将原始表达整理为带有情感重力的记忆星空；
用户可以选择将部分脱敏记忆编织成共鸣体，让他人在安全边界内先与数字回声相遇；
如果产生真实共鸣，再通过慢信件连接真实的人。
```

项目的技术核心是：

```text
Agent + Memory + Emotional Gravity + Persona Capsule + Slow Letter + Safety Boundary + Mock-first Engineering
```

项目的工程策略是：

```text
愿景不阉割，V1 不膨胀。
先打通核心闭环，再填充页面血肉。
默认 Mock 可运行，远程大模型可切换。
隐私靠后端边界，不靠 Prompt 自觉。
共鸣体不是终点，而是真实连接的桥。
```

---

## 27. 给 Codex 的直接执行指令

Codex 读取本文档后，应优先执行以下任务：

1. 创建 Spring Boot 3 + Java 17 + MyBatis-Plus + MySQL 项目。
2. 建立本文档第 15 节定义的包结构。
3. 建立本文档第 18 节定义的核心表 SQL。
4. 实现 Mock-first 的 LlmClient Adapter。
5. 实现用户注册登录。
6. 实现 Aurora Chat 的 SSE 流式 Mock 回复。
7. 实现 DialogSession / DialogMessage 存储。
8. 实现 DialogFinishedEvent 与基础 MemoryCard 生成。
9. 实现 GravityService。
10. 初始化 5 个 SEED_CAPSULE。
11. 实现星海广场基础接口。
12. 实现 SlowLetter 基础状态流。
13. 实现 AI 日志入库与查看接口。
14. 准备至少 10 个物理 HTML 页面占位，并保证能访问。
15. 保证项目默认 Mock 模式可直接运行。

这份文档是项目最高优先级工程准则。若后续 AI Agent 的建议与本文档冲突，以本文档为准。

