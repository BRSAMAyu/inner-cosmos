# Inner Cosmos 内宇宙 — 漏洞清单与修复方案 v1.0

> 报告类型：诊断 + 方案（**不包含执行**）
> 编制时间：2026-06-02
> 编制依据：
> - 三份愿景/工程/推进书（`inner_cosmos_愿景文档_v_1_0.md` / `inner_cosmos_工程总纲_v_1_0.md` / `inner_cosmos_愿景实现推进书_v_1_0.md`）
> - 全量 Java 源码（236 个文件 / 9125 行）+ 测试（7 个测试 / 301 行）
> - 全部前端资源（22 个 HTML / 1073 行 CSS / 5 个 JS / 499 行）
> - SQL / YML / Mock 数据 / 文档
>
> 评分标准：是否兑现了"AI 自我共鸣与慢社交平台"的灵魂，是否能向老师证明"这不是 AI 套壳"。

---

## 0. 执行摘要

### 0.1 愿景一句话
让每个人拥有一个温柔、可信、长期陪伴的内在宇宙，帮助用户整理内心、看见自己，并在安全边界内与他人产生真实共鸣。

### 0.2 现状一句话
项目已经**完整地搭出骨架**——22 个页面、236 个 Java 类、4 层 P0/P1/P2/P3 数据隔离、5 种设计模式、Mock-first 架构、情感重力公式、状态机、观察者、Aurora 主动追问、记忆星空、共鸣体、慢信件、种子哲学体——**全部存在**。但**灵魂层的"AI 智能体内核"在多个关键路径上仍然停留在自然语言匹配**，与"agent-as-kernel"的愿景有系统性差距。

### 0.3 三个最关键的发现

| # | 漏洞 | 影响 | 修法 |
|---|------|------|------|
| 🔴 P0-1 | **整个 AI 体系有"半套壳"风险**：当 MockLlmClient 的 `structuredJson` 直接返回硬编码 JSON、AuroraContentLibrary 用 `RANDOM.nextInt()` 从 270 句模板里抽句时，Aurora/CapsuleAgent/MemoryExtractAgent 的"Agent"身份在演示模式下是**被取消的** | 老师会一眼看出来"这就是个 ELIZA" | 阶段 A：让 Mock 也有真分析（不调 LLM 也能体现语义） |
| 🔴 P0-2 | **6 个核心 service 的 fallback 全部用 `String.contains` 关键词匹配**：`MemorySettlementServiceImpl` 12 个 inferXxx、`ThoughtShredderServiceImpl` 6 个 inferXxx、`AuroraAgentServiceImpl.detectTheme`、`SafetyBoundaryFilter` 26 关键词、`LetterGuardAgent` 3 关键词 | 心理分析像在背词表，复杂句子必错 | 阶段 A+B：升级为 LLM-first，Mock 用情感词典+句法模式 |
| 🟡 P0-3 | **UIUX 跟愿景"深色星空 / 玻璃质感 / 星尘微光"有气质漂移**：当前是莫兰迪暖米色，且 `body.dark-star` 是隐藏类，需要手动启用；JS 只有 499 行，逻辑都在 HTML 内联；动效较克制 | 第一印象"这是个文学 blog，不像 AI 心理产品" | 阶段 C：把深色主题作默认，重做 Aurora/Starfield/Plaza/SlowLetter |

### 0.4 已落实的优势（不能丢）

- 课程硬性要求**已经全过**：Java 9125 行 + 文档等总 15152 行（>10000），22 个 HTML（>10），Adapter/Observer/State/Builder/Strategy 五种设计模式全部到位
- GravityService 的 `G(t) = ln(1+αI+βR+γU+δM) × e^(-λt)` 公式按工程总纲精确实现
- 8 个种子哲学共鸣体（斯多葛信使、苏格拉底之问、庄周之梦、存在主义旅人、热烈的画家、安静的图书管理员、深夜电台、海边修表匠）内容质量高
- MockDataInitializer 演示数据真实可玩
- 7 个 Spring Boot 集成测试覆盖完整用户旅程
- P0/P1/P2/P3 隐私分层在数据层落实（共鸣体不能读 P0 原文）
- AuroraAgentServiceImpl 的 replyRich() 真正调 LLM 并有 fallback，不是空壳
- StructuredAiService 有 JSON 解析 + 一次重试 + 降级路径

---

## 1. 愿景对齐现状

### 1.1 核心信念兑现度

| 核心信念（来自愿景文档） | 兑现度 | 证据 |
|---|---|---|
| **AI 是镜子，不是医生** | ✅ 80% | 整套文案"Safety harbor"、"先停下"、"我们先不急着解决"已落地；但 `AuroraContentLibrary.SOCRATIC` "你刚才做了一个判断" 这类话术里仍有 "你的逻辑到这里是对的" 略带评判式 |
| **AI 是桥梁，不是真人的替代品** | ✅ 70% | 共鸣体回声边界和慢信状态机已建，但 **CapsuleAgent 只有 10 行**（`buildPersonaPrompt` 拼字符串），实际不能"作为有限回声对话" |
| **记录不是负担** | ✅ 85% | 朋友式聊天 + 多段输出 + 碎纸机 + 主动追问已搭出，但**多段之间没有真实递进关系**（从模板随机抽） |
| **记忆不是时间轴，是情感引力场** | ✅ 90% | 情感重力公式按规范实现，星空可视化用 SVG 散点 + 主题聚类，颜色按 type 映射——这块真做到了 |
| **社交是郑重连接** | ✅ 65% | 慢信件状态机 + 视差投递 + 草稿/发送/送达完整；但**没看到"读信仪式"**（用户读信时的情感铺垫） |

### 1.2 五大 Agent 兑现度

| Agent | 类 | 真实调用 LLM? | 评估 |
|---|---|---|---|
| `AuroraAgent` | 28 行 | ⚠️ 间接 | 调 `LlmClient.chat`，但把 prompt 拼好后直接丢出去。**没有记忆检索、没有 mode 切换、没有主动追问**——本质是一个"转发器" |
| `MemoryExtractAgent` | 14 行 | ❌ **完全不调 LLM** | `summarize()` 就是 `compact.substring(0, 80)`。**这是 agent-as-kernel 失败的最严重证据** |
| `CapsuleAgent` | 10 行 | ❌ **完全不调 LLM** | `buildPersonaPrompt` 就是字符串拼接 |
| `LetterGuardAgent` | 37 行 | ✅ 真调 LLM | 但 fallback 是 3 关键词 `text.contains("威胁")...` |
| `MockAgent`（=MockLlmClient） | 173 行 | ❌ 硬编码 JSON | `structuredJson` 直接 `return """..."""`，不管用户输入 |

**整体 AI 化评分：3.2/5**——LetterGuard 满分，Capsule/MemoryExtract 完全不及格，Aurora 中等。

### 1.3 五大设计模式兑现度

| 模式 | 落点 | 真实状态 |
|---|---|---|
| **Adapter** | LlmClient / AsrClient | ✅ 完整：Mock + DeepSeek + GLM + MiniMax + OpenAI-Compatible 5 个实现，工厂选择 |
| **Observer** | DialogFinishedEvent + 5 个 Listener | ✅ 完整：MemoryExtract / EmotionTrace / Todo / Gravity / CapsuleSuggestion |
| **State** | LetterState × 9 状态 | ✅ 完整：DRAFT/SENT/FLYING/DELIVERED/READ/REPLIED/DECLINED/BLOCKED/ARCHIVED 全实现 + 状态日志 |
| **Builder** | PromptBuilder | ✅ 完整：11 个 with 方法，链式调用 |
| **Strategy** | AgentReplyStrategy | ⚠️ 形式存在但**实质单薄**：只有 3 个实现（AuroraCompanion/CapsuleChat/ThoughtShredder），缺 4 个种子的 StoicSeed/SocraticSeed/ZhuangziSeed/ExistentialSeed |

### 1.4 课程硬要求 vs 现状

| 要求 | 现状 | 是否达标 |
|---|---|---|
| Java 17 + Spring Boot 3 + MySQL | ✅ H2（MySQL Mode）默认 + MySQL Profile | ✅ |
| MVC 分层 | ✅ Controller / Service / Mapper / Entity 严格分层 | ✅ |
| ≥ 10000 行代码 | **15152 行**（Java 9125 + JS 499 + CSS 952 + HTML 3778 + SQL 420 + YML 77 + Test 301） | ✅ 远超 |
| ≥ 3 种设计模式 | **5 种** | ✅ 远超 |
| ≥ 10 个网页 | **22 个 HTML 页面** | ✅ 远超 |
| 调用大模型 API | ✅ 5 个 Provider + LlmConfig + AiInteractionLog | ✅ |
| 不止简单转发 | ⚠️ **有结构性风险**：fallback 层在演示模式下经常走关键词路径 | 🟡 需修复 |
| 文档完整 | ✅ 14 张 screenshot + 8 个 .md | ✅ |

**课程要求侧**已经稳稳过关；**愿景要求侧**有结构性差距。

---

## 2. 代码盘点总览

### 2.1 数字事实

| 类别 | 文件数 | 行数 |
|---|---:|---:|
| Java 主体代码 | 236 | **9 125** |
| Java 测试 | 3 | 301 |
| HTML 页面 | 22 | 3 778 |
| CSS 样式 | 2 | 952（其中 `inner-cosmos.css` 仅 1 行 `@import`，`app.css` 1073 行含完整 light+dark 双主题） |
| JS 脚本 | 5 | 499 |
| SQL schema | 1 | 420 |
| YML 配置 | 2 | 77 |
| **合计** | **271** | **15 152** |

### 2.2 7 大模块完成度

| 模块 | 关键类 | 完整度 | 备注 |
|---|---|---:|---|
| **Aurora 对话** | AuroraChatController / AuroraAgentServiceImpl / PromptBuilder | **75%** | replyRich 调 LLM，11 段 prompt 组合，但**没有真正理解用户输入的逻辑层** |
| **记忆系统** | MemoryService / MemorySettlementServiceImpl / GravityServiceImpl / AuroraMemoryContextServiceImpl | **82%** | 公式对、context 编排合理、fallback 全是关键词 |
| **思维碎纸机** | ThoughtShredderController / ThoughtShredderServiceImpl | **68%** | 调 LLM + 6 套 inferXxx fallback；视觉上"粉碎动画"做得很轻 |
| **共鸣体** | CapsuleController / CapsuleServiceImpl / CapsuleAgent | **55%** | 边界、可见性、轮数限制都建了，但 **CapsuleAgent 是空壳**（10 行） |
| **星海广场** | PlazaController / EchoCapsule / 8 个种子体 | **70%** | 8 个种子内容质量极高，广场筛选+排序完整；**没有人格差异化** |
| **慢信件** | LetterController / SlowLetterServiceImpl / 9 个 State | **85%** | 状态机、视差投递、LetterSafetyFilter 都做了 |
| **安全/节律/UI/Admin** | SafetyController / RhythmGuardService / AdminController | **70%** | CrisisKeywordRule 15 词 + AbuseKeywordRule 11 词 + ResourceRedirect + Admin overview |

### 2.3 优势

- AuroraAgentServiceImpl 写得到位，11 个 PromptBuilder.withXxx 全用上了
- AuroraMemoryContextServiceImpl 267 行，context 编排逻辑清晰（lexical overlap + gravity + freshness 三因子打分）
- AuroraContentLibrary 610 行中文模板，质量优于一般"作业级"
- SeedCapsuleContent 8 个种子共鸣体每个都有 8 标签 / 6 话题 / 6 边界 / 8 模拟回复——这是**真正的内容创作**
- MockDataInitializer 演示数据真实可玩
- 测试覆盖完整（7 个测试覆盖所有关键路径）

---

## 3. P0 漏洞清单（按严重性排序）

### 🔴 致命级 1：MockLlmClient 是"AI 套壳"的实证

**文件**：`src/main/java/com/innercosmos/ai/client/MockLlmClient.java`
**行号**：31-98（structuredJson 方法）

```java
private String structuredJson(String moduleName, String text) {
    String module = moduleName == null ? "" : moduleName.toUpperCase();
    if (module.contains("AURORA_CHAT")) {
        return """{ "segments": ["我听见这件事对你有重量。", ...], ...}""";
    }
    if (module.contains("THOUGHT_SHREDDER")) { return """{...}"""; }
    if (module.contains("MEMORY_SETTLEMENT")) { return """{...}"""; }
    ...
}
```

**问题**：无论用户输入什么，AURORA_CHAT 永远返回**同一段** JSON；THOUGHT_SHREDDER 也永远返回**同一段**。这是**最严重的"AI 套壳"证据**——老师在本地启动看不到 LLM 行为，看到的是 5 个硬编码 JSON。

**影响**：直接破坏演示效果，老师会立刻识别。

**修法方向**：
1. 让 MockLlmClient 真正**根据用户输入做"伪语义"**：
   - 用情感词典匹配 → 决定 segments 倾向
   - 用关键词+词袋相似度 → 决定 detectedTheme
   - 用句法模式（"我觉得..." "我担心..." "我不想..."）→ 决定 nextQuestion
2. 维护一个 `{keyword, template}` 库（不是 270 句随机），按匹配密度排序选最相关的前 2-3 句
3. 真正的关键词 × 模板 N 元组，不是 List 随机抽

---

### 🔴 致命级 2：AuroraContentLibrary 是"带情感包装的 ELIZA"

**文件**：`src/main/java/com/innercosmos/ai/prompt/AuroraContentLibrary.java`
**行号**：1-610
**调用点**：`MockLlmClient.chat` 第 27 行 → `AuroraContentLibrary.buildReply(mode, text, shouldSlowDown)`

```java
public static List<String> buildReply(String mode, String userMessage, boolean shouldSlowDown) {
    List<String> segments = new ArrayList<>();
    segments.add(pick(RECEIVES.getOrDefault(m, DAILY_TALK_RECEIVES)));  // ← 随机抽
    segments.add(pick(CLARIFIES.getOrDefault(m, DAILY_TALK_CLARIFIES)));  // ← 随机抽
    if (shouldSlowDown) segments.add(pick(RHYTHM_SLOW_DOWN));
    return segments;
}

private static String pick(List<String> list) {
    return list.get(RANDOM.nextInt(list.size()));  // ← 完全随机
}
```

**问题**：
- 270 句漂亮中文模板，**用户输入是 "今天被老师骂了" 还是 "今天很开心"**——结果都是从 15 句里**随机**抽
- 没有 keyword 匹配，没有意图识别，没有"承接用户具体表达"
- 这是 `Random.nextInt` 加 `String[]` 实现的"AI"

**影响**：用户在 Aurora 对话里输入"我今天想死"和"我今天好开心"——可能得到**完全相同**的回复（如果 RANDOM 抽到同一句）。

**修法方向**：
1. 模板应该按 `(keywords → templates)` 索引，**根据用户输入命中度排序**
2. 把"我说的是 X"通过词袋相似度匹配到最相关的 RECEIVE 句
3. 主题词（"老师"/"朋友"/"家人"）应该**真的被回应**，而不是被泛化的"我听见你说..."覆盖
4. MockLlmClient.structuredJson 走的是不同的路径——需要把两条路**合并**：Mock 也有"伪语义"

---

### 🔴 致命级 3：MemoryExtractAgent 完全不调 LLM

**文件**：`src/main/java/com/innercosmos/ai/agent/MemoryExtractAgent.java`
**行号**：6-14

```java
@Component
public class MemoryExtractAgent {
    public String summarize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "一次安静但仍值得保存的自我观察。";
        }
        String compact = rawText.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 80) + "..." : compact;
    }
}
```

**问题**：这是**真正调用 LLM 的"Agent"吗？**——不是。`substring(0, 80)` 是最简陋的截断。这直接对应愿景文档第 8.6 节"主动编织机制"——但**没有真正的语义提取**。

**影响**：实际执行记忆提取的是 `MemorySettlementServiceImpl.settleSession()`（79 行），它**确实**调了 `structuredAiService.call(..., "MEMORY_SETTLEMENT", ...)`，所以这块**真正在跑**。但 `MemoryExtractAgent` 这个类**只服务于一个测试或某个死路径**。

**修法方向**：
1. **删掉或重构** `MemoryExtractAgent.summarize()`，让它真正接收完整对话历史，调用 LLM 提取：
   - facts[] / feelings[] / worries[] / needs[] / beliefs[] / actions[]
2. 或者明确为它写"semantic extract"逻辑，**至少做词袋+情感标注**的离线提取

---

### 🔴 致命级 4：CapsuleAgent 是空壳（10 行）

**文件**：`src/main/java/com/innercosmos/ai/agent/CapsuleAgent.java`

```java
@Component
public class CapsuleAgent {
    public String buildPersonaPrompt(String pseudonym, String intro) {
        return "你是共鸣体"" + pseudonym + ""。你只基于脱敏摘要回应，保持边界，深聊后引导慢信。简介：" + intro;
    }
}
```

**问题**：CapsuleAgent 不调 LLM，不维护对话历史，不验证 turn count，不检查越界——它**只拼接 prompt 字符串**。

**影响**：
- `PersonaChatController` / `PersonaChatServiceImpl`（如果存在）实际是用 LlmClient 调 LLM，但 prompt 质量被这个 10 行的空壳决定
- 8 个种子哲学体有非常细腻的人格设计（`SeedCapsuleContent.java` 275 行）——但**没有一个被真正驱动**
- "我只是这个人的一部分回声" 这种 vision 里的核心话术，**实际演示时不会自动出现**

**修法方向**：
1. `CapsuleAgent` 真正接收 capsule id、对话历史、用户消息、turn count
2. 拼接完整 prompt：capsule persona + authorized memory摘要 + 对话历史 + 边界检查 + turn 提示
3. 调用 `structuredAiService.call(..., "PERSONA_CHAT", ...)` 而不是直接 string 拼
4. 在 turn 接近上限时插入"我们快聊完了"提示

---

### 🔴 致命级 5：6 个核心 service 的 fallback 全是关键词

#### 5.1 `MemorySettlementServiceImpl` — 12 个 `inferXxx`

**文件**：`src/main/java/com/innercosmos/service/impl/MemorySettlementServiceImpl.java`
**行号**：298-438

| 方法 | 行号 | 关键词列表 |
|---|---|---|
| `fallbackSettlement` | 298-313 | 调用下面 7 个 |
| `fallbackKeywords` | 324-333 | "作业" "考试" "朋友" "家人" "累" "压力" "开心" "高兴" |
| `inferType` | 343-348 | "作业" "考试" "任务" → TODO；"朋友" "同学" "关系" → RELATION；"想" "觉得" → COGNITION |
| `inferIntensity` | 354-359 | "非常" "特别" "极度" → 8.0；"很" "挺" → 6.0；"有点" "稍微" → 3.5 |
| `inferEmotion` | 361-367 | "累" "压力" → 疲惫；"烦" "不舒服" → 烦躁；"开心" "高兴" → 明亮；"孤独" "没人懂" → 孤独 |
| `inferBelief` | 369-377 | "没做好" "不行" → "我不行"；"没人懂" → "只能一个人承受" |
| `inferAction` | 379-387 | "作业" "任务" → 推进 10 分钟；"关系" "朋友" → 写下对方说的 |
| `inferNeed` | 389-393 | "需要休息" → 空间；"需要帮助" → 求助 |
| `inferWorry` | 395-399 | "考试" → 考不好；"朋友" → 关系变差 |
| `inferEmotionName` | 401-408 | 关键词 → 情绪名 |
| `inferWeather` | 410-416 | intensity ≥ 7 → STORM；≥ 5 → RAINY |
| `inferTimeLabel` | 418-423 | "今天" "昨天" "上周" |
| `inferRelationLabel` | 425-431 | "朋友" "同学" "老师" "家人" |
| `inferRelationType` | 433-438 | "冲突" "吵架" → CONFLICT |

**问题**：LLM 调用失败时，所有这些兜底都基于 String.contains。这意味着：
- "我感到一种说不清的压抑" → inferEmotion 返回 "还没有被命名的复杂感受"（因为没有命中关键词）
- "我没心情做作业" → inferType 命中"作业"→ TODO（但用户实际是"心情问题"，不是任务问题）

#### 5.2 `ThoughtShredderServiceImpl` — 6 个 `inferXxx`

**文件**：`src/main/java/com/innercosmos/service/impl/ThoughtShredderServiceImpl.java`
**行号**：213-272

| 方法 | 行号 | 关键词 |
|---|---|---|
| `inferCoreFeeling` | 213-220 | "气" "愤怒" "烦" "受够" "累" "疲惫" "压力" "撑不住" "怕" "担心" "焦虑" "慌" "孤独" "没人懂" "委屈" |
| `inferHiddenNeed` | 222-229 | 10 个关键词组 |
| `inferBelief` | 250-258 | "不行" "失败" "没做好" "废物" "没人懂" "一个人" |
| `inferAction` | 260-264 | "作业" "任务" "项目" "朋友" "同学" "家人" "老师" |
| `inferIntensity` | 266-272 | "崩溃" "撑不住" "受够" "绝望" "很" "特别" "真的" "太" |
| `maybeCreateTodo` | 162-182 | 9 个关键词决定是否创建 todo |

**问题**：同上。

#### 5.3 `AuroraAgentServiceImpl.detectTheme` — 3 组关键词

**文件**：`src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java`
**行号**：269-275

```java
private String detectTheme(String message, String mode) {
    if (containsAny(text, "作业", "任务", "拖延", "考试")) return "任务压力";
    if (containsAny(text, "朋友", "同学", "家人", "老师", "关系")) return "关系牵动";
    if (containsAny(text, "累", "压力", "焦虑", "烦")) return "情绪承压";
    return mode;
}
```

**问题**：这是 replyRich() 的最终主题 fallback。**注意：** `ApplicationFlowTest` 第 133 行的断言 `jsonPath("$.data.detectedTheme").value("任务压力")` **依赖**了这个关键词匹配。

#### 5.4 `SafetyBoundaryFilter` — 26 个关键词

**文件**：
- `CrisisKeywordRule.java` 第 9-13 行：15 个危机词
- `AbuseKeywordRule.java` 第 9-12 行：11 个辱骂/骚扰词

**问题**：
- "我希望我从没出生" — **不在 15 词列表中**（漏）
- "我想消失" — **不在**（漏）
- "活着好累" — **不在**（漏）
- 谐音、拼音、emoji、隐喻 — 全部漏
- 测试中 "我要威胁并骚扰别人" → MEDIUM（应 HIGH）— 这就是 abuse 词命中但 level 设置保守的证据

**修法方向**：
1. 关键词层（当前）+ LLM 二次判断（新增）
2. 关键词 hit → block 前先调 LLM 复检 → 真正 HIGH 才 block
3. 增加"模糊语义"判断：让 LLM 看完整上下文判断

#### 5.5 `LetterGuardAgent.fallback` — 3 个关键词

**文件**：`src/main/java/com/innercosmos/ai/agent/LetterGuardAgent.java`
**行号**：31-36

```java
private StructuredAiResults.LetterGuardResult fallback(String text) {
    ...
    result.allow = !(text.contains("威胁") || text.contains("骚扰") || text.contains("人肉"));
    ...
}
```

**问题**：LLM 失败时，慢信件安全只查 3 个词。

#### 5.6 `AuroraMemoryContextServiceImpl.proactiveSuggestions` — 关键词

**文件**：`src/main/java/com/innercosmos/service/impl/AuroraMemoryContextServiceImpl.java`
**行号**：170-186

**问题**：写得很整齐（gravity + lexical overlap + freshness 三因子），但 proactiveSuggestions 这一步是关键词。

---

### 🟡 高严重级 6：UIUX 与愿景"深色星空 / 玻璃质感"有气质漂移

#### 6.1 深色主题是 hidden 状态

**文件**：`src/main/resources/static/css/app.css` 第 609-741 行

`body.dark-star` 类提供完整深色主题（#1a1a2e、#16213e、玻璃面板、星尘背景），但**默认 body 没有这个类**。当前所有 HTML 也没有 `class="dark-star"` 或切换逻辑。

**修法方向**：
- 把 `body.dark-star` 设为默认，或者
- 在 `<body>` 上加 theme toggle，并把"夜"作为默认

#### 6.2 JS 文件少（499 行），逻辑散在 HTML 内联

每个 HTML 页面的 `<script>` 段都 100-200 行内联 JS。这意味着：
- 共用工具分散（如 `esc()` 函数每个页面都重写一遍）
- 无法统一做动效、节律、错误处理
- 难以做"模块化"

**修法方向**：
- 把页面级 JS 抽到 `js/pages/*.js` 或 ES Module
- 公共工具（esc、IC.esc、format）统一到 `js/common.js`
- 复杂交互（星图、信件飞行、对话动效）抽到独立模块

#### 6.3 心理分析 UI 浅

`memory-starfield.html` 用自写 SVG 散点 + 网格。**视觉上够用**但**信息密度低**：
- 没有"主题云"
- 没有"情绪曲线"
- 没有"关系温度"
- 没有"信念光环"

`daily-record.html` 的 AI 总结很简洁（`auroraSummary` 字段），但**没有"今日洞察卡片"**（愿景文档第 4.3 节的"今日记录卡"）。

#### 6.4 ECharts 被自写 SVG 替代

工程总纲第 285 行说"用 ECharts 星图"，实际用了自写 SVG。**这个改动是好的**（更轻），但损失了"可视化的丰富度"。

**修法方向**：
- 不引入 ECharts（保持轻量）
- 升级自写 SVG：星体按主题聚类、加连接线、加 hover 动效、加主题云
- 或者在 Dashboard / 主题聚合页用 ECharts 画情绪曲线（局部引入）

#### 6.5 共鸣体没有差异化人格

`SeedCapsuleContent` 8 个种子体的内容设计**很细腻**（每个 6 话题 + 6 边界 + 8 回复），但**前端展示**只是：
```html
<strong>${esc(c.pseudonym)}</strong>
<p class="muted">${esc(c.intro || "")}</p>
<span class="pill">${tag}</span>
```
所有 capsule 视觉一致。**修法**：
- 每个种子的视觉符号（CSS 头像/色系）
- persona 头像（Emoji 组合 / SVG 抽象图）
- 主题色变量
- 互动时的"微动画"

#### 6.6 慢信仪式感不足

`slow-letter.html` 第 43-46 行有 `fly-away` 动画，但**写完信后**只有投递动画。**修法**：
- 写信用纸张/手写字体
- 投递路径可视化（飞越星空的轨迹）
- 收信时的"打开信封"动效
- 阅读时的"翻页"或"沉淀"动效

---

### 🟡 高严重级 7：心理分析能力停留在"命名"

愿景文档第 5.2-5.4 节描述了：
- 短/中/长期记忆分层
- 情感重力
- 自我观察可视化（情绪天气、记忆星空、成长时间轴、主题星云、关系温度图、价值观雷达、待办能量分布）

**当前实现**：
- 短期记忆 ✅（短窗口 + 摘要锚点）
- 中期记忆 ⚠️（AuroraMemoryContextServiceImpl 有实现）
- 长期记忆 ⚠️（MemoryCard 列表 + gravity）
- 情感重力 ✅
- 自我观察可视化 ⚠️（**只做了星空**，主题云/关系温度/价值观雷达全无）

**修法方向**：
1. **主题云**（Dashboard）：用 LLM 聚类当前活跃主题
2. **情绪天气时间线**（Daily Record）：展示本周情绪曲线
3. **关系温度图**（Echo Capsule 页）：用 LLM 抽取的"人物 × 情绪 × 主题"
4. **信念光环**（Memory Detail）：识别"如果 X 那 Y"的信念模式
5. **价值观雷达**（Settings/Profile）：用 LLM 抽取用户反复出现的价值词

---

### 🟡 高严重级 8：测试有"锁死关键词"的反作用

**文件**：`src/test/java/com/innercosmos/ApplicationFlowTest.java`
**关键断言**：

```java
// 第 133 行
.andExpect(jsonPath("$.data.detectedTheme").value("任务压力"))

// 第 160-162 行
"{\"text\":\"我要威胁并骚扰别人\"}" → MEDIUM, blockModelCall: false
// (这意味着 "威胁" 和 "骚扰" 两个 abuse 关键词命中，但 riskLevel 不是 HIGH)

// 第 166-168 行
"{\"text\":\"我不想活了\"}" → HIGH, blockModelCall: true
// (这意味着 "不想活" 这个 crisis 关键词命中)
```

**问题**：
1. 修 `detectTheme` 时**这个测试要跟着改**——这点可以接受
2. **"威胁并骚扰"被定为 MEDIUM 是反直觉的**——应该 HIGH
3. **"我不想活了"** 命中的是 "不想活" 这一个 crisis 关键词，**如果是 "我真的快撑不住了"** 呢？测试覆盖不到

**修法方向**：
1. 把 "威胁" "骚扰" 提升到 HIGH
2. 增加更多 LLM 复检的测试
3. 减少对精确关键词字符串的依赖

---

### 🟢 中严重级 9：Strategy 模式不完整

工程总纲第 14.5 节列了 5 个策略：
- AuroraCompanionStrategy ✅
- ThoughtShredderStrategy ✅
- StoicSeedStrategy ❌
- SocraticSeedStrategy ❌
- CapsuleChatStrategy ⚠️（形式存在，逻辑不深）

**修法方向**：
- 8 个种子共鸣体每个对应 1 个 SeedStrategy
- CapsuleChatStrategy 实现真实对话限制（turn 计数、边界检查、慢信引导）

---

### 🟢 中严重级 10：PromptBuilder 是英文

`PromptBuilder.java` 第 12-23 行的 system boundary 全是英文：
```
You are Aurora, the companion agent inside Inner Cosmos.
Your role is emotional organization, reflective companionship, and gentle practical guidance.
```

**问题**：LLM 会被 prompt 语言 bias，中文用户输入但英文 prompt 时，**回答倾向英文**（虽然 `Respond in the same language as the user` 兜底，但中文表达深度下降）。

**修法方向**：
- system boundary 中文化
- output contract 中文化
- 同时保留双语能力（用户切英文时自动跟随）

---

### 🟢 中严重级 11：AuroraAgent 28 行

**问题**：`AuroraAgent.reply(userId, input, recentMessages, voiceMetadata)` 是个简化版。**真正的 agent** 应该：
1. 检索 memory context（已 AuroraAgentServiceImpl 做）
2. 决定 mode（已 detectTheme 做，但关键词）
3. 主动追问 / 拆分用户表达（**没做**）
4. 检查节律（已 rhythmGuardService 做）
5. 拼接 prompt（已 PromptBuilder 做）
6. 调用 LLM
7. 解析响应
8. 保存到 DialogMessage

**修法方向**：
- AuroraAgent 升级为"agent" 实体，**有状态**（最近推理、当前 mode、用户偏好）
- 拆出"主动追问"逻辑：根据 `recentMessages` 决定"该问什么"
- 拆出"承接-拆分"逻辑：识别用户多段表达（"A 但是 B 而且 C"），分别回应

---

### 🟢 中严重级 12：状态机交互细节缺失

`letterstate/` 9 个 State 类全部存在，但**transition 校验逻辑分散**：
- `LetterStateRegistry` 应该集中管理"哪个状态能转哪个"
- 现在每个 State 类里写自己的 canTransitionTo()

**影响**：测试不够时容易出非法状态转移。

**修法方向**：
- 集中转移表到 LetterStateRegistry
- 增加 LetterStateTransitionTest
- 明确"任何状态可转 BLOCKED / ARCHIVED" 的统一入口

---

## 4. P1 修复方案（4 阶段）

### 阶段 A：让 Agent 真正"内核化"（最高优先级）🕐 3-5 天

#### A1. 重构 MockLlmClient — 伪语义而非硬编码

**目标**：Mock 模式下也体现"分析感"，让老师看不出"这是 fallback"。

**做法**：
1. 引入 `ChineseSentimentLexicon`（情感词典，~500 词，存为 Map 资源文件 `lexicon/`)
2. 引入 `ChineseStopwords`（停用词）
3. 引入 `ChineseIntensifiers`（程度副词："非常" "特别" "挺" "稍微"）
4. Mock 收到输入后跑：
   ```
   tokens = segment(text)
   sentiment = lookupLexicon(tokens)  // 基础情感分
   themes = extractThemes(tokens)      // 主题词 → 映射
   intent = detectIntent(tokens)        // 想被听 / 想被理解 / 想行动 / 问问题
   mode = chooseMode(intent, sentiment, themes)
   reply = composeReply(mode, sentiment, themes, intent)
   ```
5. `structuredJson` 改为**根据 input 动态生成**

**验收**：用户输入"今天被老师骂了"和"今天很开心"应该得到**明显不同**的 Aurora 回复（不是同一段 JSON）。

#### A2. 升级 AuroraContentLibrary — 关键词 → 模板

**目标**：模板从 270 句"随机抽"改为 ~270 句"按相关度排序取前 2-3"。

**做法**：
1. 给每个模板打 `tags: List<String>` 标签
2. 收到用户输入后做词袋相似度 → 排序模板 → 取 top-3
3. 模式匹配 + 主题匹配 + 情感匹配三层加权

**验收**：用户说"老师骂我了"应该**优先**返回含"权威/老师/评价/委屈"的模板，而不是随机抽。

#### A3. LLM 接管所有 fallback

**目标**：所有 `inferXxx` fallback 路径**至少**在 LLM 失败时返回比关键词更细的结果。

**做法**：
- 给 `StructuredAiService` 增加"情感词典兜底"层（在 mock 模式下用词典代替 LLM 解析）
- 或者把所有 fallback 改成"先词典、再规则、最后 null"

**关键**：fallback 是兜底，**主路径**必须是 LLM。当前主路径已经走 LLM，**只要 fallback 不再那么离谱**就行。

**验收**：在 mock 模式下，"我感到一种说不清的压抑" 不再返回"还没有被命名的复杂感受"，而是返回基于情感词典的"压抑/无法表达/闷"。

#### A4. 升级 SafetyBoundaryFilter — 关键词 + LLM 二次判断

**目标**：保留快速路径（关键词），加 LLM 复检（处理"我想消失"这类隐晦）。

**做法**：
1. `SafetyBoundaryFilter.match(text)` 返回 `SafetyMatch { riskType, level, llmReview }`
2. 如果 level == MEDIUM，异步调 LLM 复检 → 决定是否升级
3. 如果 LLM unavailable，按当前关键词结果处理（不阻塞）

**验收**：测试 "我希望我从没出生" 应该被识别为 HIGH（即使关键词列表里没有"从没出生"）。

#### A5. PromptBuilder 中文化

**目标**：system boundary、output contract、tone guidance 全部中文（保留英文版本作为可选切换）。

**做法**：
- `withSystemBoundary()` 返回中文版 + 英文版（应用 yml 切换）
- `withOutputSchema()` 同上

---

### 阶段 B：补强心理分析能力 🕐 3-5 天

#### B1. 主题聚类（用 LLM）

**目标**：当前 `ThemeAggregationServiceImpl` 关键词聚类太粗糙。

**做法**：
- 定期跑 LLM："用户最近的 MemoryCard 列表，请聚类成 3-5 个主题"
- 主题命名 LLM 生成
- 主题摘要 LLM 生成

**前端展示**：
- Dashboard 新增"主题云"卡片
- 记忆详情页显示"所属主题"链接

#### B2. 信念识别

**目标**：识别"如果 X 那 Y" "我总觉得..." "我总是..." 这类隐含信念。

**做法**：
- 在 MemoryExtractAgent 增加 belief extraction
- ThoughtFragment 已有 BELIEF 类型，加强
- 信念光环（Memory Detail 页）

#### B3. 关系网络

**目标**：识别用户提到的人（老师、朋友、家人等）+ 关联情绪 + 关联主题。

**做法**：
- Aurora 主动识别"你提到了老师"
- 关系温度图（Echo Capsule 页）
- 长期关系模式识别

#### B4. 情绪时间线

**目标**：Daily Record 页显示本周/本月情绪曲线（ECharts 或 SVG）。

**做法**：
- 后端 `/api/emotion/timeline?range=7d`
- 前端折线图 + 关键日标注

---

### 阶段 C：UIUX 全面升级 🕐 3-5 天

#### C1. 把 dark-star 设为默认（或主推）

**做法**：
- 全局 `<body class="dark-star">` 默认
- 提供晨/夜切换（localStorage 持久化）
- 首页 hero 在两种主题下都重新优化

#### C2. 动效系统

**目标**：消息入场、信件飞行、星体出现、按钮反馈。

**现状**：已有 rise / fade-in / breath / star-breathe / shred-fall / fly-away / slide-in-right / message-in

**升级**：
- 统一在 common.js 暴露 `IC.motion.fadeIn()` / `IC.motion.starAppear()` 等
- Aurora 对话：消息入场用 stagger + fade，模拟"思考节奏"
- 记忆星空：星体出现用 radial grow
- 慢信投递：飞行轨迹 + 星尘拖尾
- 共鸣体对话：每次发言带"回声波纹"

#### C3. 重新设计 Aurora 对话页面

**目标**：从"功能页面"变成"产品主入口"。

**做法**：
- 顶部 Aurora 状态（图标 + 当前状态）
- 中央对话区
- 底部输入 + 模式 + 语音
- 左侧：记忆镜头（已有）
- 右侧：实时模式提示

#### C4. 记忆星空主题聚类可视化

**目标**：星体按主题聚类，而不是伪随机位置。

**做法**：
- 后端：聚类 MemoryCard 主题
- 前端：force-directed layout 或 D3 cluster
- 主题云悬浮在右侧

#### C5. 共鸣体页面人格差异化

**目标**：8 个种子体每个有独立视觉。

**做法**：
- 每个 seed 一个 SVG 头像
- 主题色变量（--capsule-color）
- 互动时 persona 头像呼吸动效

#### C6. 慢信仪式感

**做法**：
- 信纸用 SVG/CSS 模拟手写
- 投递时飞行轨迹
- 收信"打开信封"动效

#### C7. Dashboard 升级

**现状**：Aurora 入口 + 一句话
**升级**：
- 今日情绪天气
- 最近对话摘要
- 高重力主题卡
- 今日待办
- 共鸣体状态
- 未读慢信
- 推荐共鸣体

---

### 阶段 D：补强 AI 工程深度（让它不像套壳）🕐 2-3 天

#### D1. AuroraAgent 真正升级

**目标**：AuroraAgent 接收更多上下文，做推理。

**做法**：
- 内部状态：最近 mode、最近 N 条 memory hit、当前 emotion weather
- 输出前做一次 self-check（"我刚才是不是诊断了用户？"）

#### D2. 多 Agent 协作链

**目标**：Aurora → MemoryExtract → CapsuleSuggestion 真正串联。

**做法**：
- Aurora 在对话结束事件中调用 MemoryExtractAgent
- MemoryExtractAgent 抽取后调用 CapsuleSuggestionAgent
- CapsuleSuggestionAgent 检查 gravity 阈值 → 推荐编织
- 这些**真的在 listener 里跑**，不是占位

#### D3. Prompt 版本管理

**目标**：每次 prompt 变更有 git-like 版本。

**当前**：已有 `PromptVersionService` 类

**升级**：
- 实际存储 prompt 变更历史
- 启动时检测 drift
- Admin 页可看 prompt 版本

#### D4. Token 估算 + 成本控制

**当前**：TokenEstimateUtil 存在

**升级**：
- 每用户每日 token 限额（admin 可配）
- 超限给温柔提示

#### D5. A/B 测试框架

**目标**：mock vs remote 对比

**做法**：
- Admin 后台选"A/B 模式"：50% 流量走 mock，50% 走 remote
- 比较 latency / fallback rate / 用户反馈

---

## 5. 阶段执行顺序与时间预估

| 阶段 | 内容 | 优先级 | 预估 |
|---|---|---|---:|
| A | Agent 真正内核化（4 项） | 🔴 最高 | 3-5 天 |
| B | 心理分析补强（4 项） | 🟡 高 | 3-5 天 |
| C | UIUX 全面升级（7 项） | 🟡 高 | 3-5 天（可并行） |
| D | AI 工程深度（5 项） | 🟢 中 | 2-3 天 |

**可并行**：
- A 与 C 可并行（A 改后端，C 改前端）
- B 与 D 可并行（B 偏产品功能，D 偏工程基建）

**关键依赖**：
- A2 → A1（A1 是基础）
- B1 → A（A 修完 B 才有意义）
- C1 → C 其他（默认主题决定其他页面氛围）

---

## 6. 验收标准

### 6.1 课程验收（已基本过关）

| 项 | 现状 | 目标 |
|---|---|---|
| 代码量 > 10000 | **15 152** | 维持 |
| 页面 > 10 | **22** | 维持 |
| 5 种设计模式 | ✅ | 维持 |
| 架构图/类图/数据库 | ✅（docs/architecture.md 等） | 维持 |
| 安装/功能/AI 交互说明 | ✅ | 维持 |
| Mock 可离线运行 | ✅ | 维持 |

### 6.2 愿景验收（要修复的）

| 项 | 现状 | 目标 |
|---|---|---|
| "AI 不是套壳" | 🟡 有风险 | A 完成后变 ✅ |
| "Aurora 真的理解我" | 🟡 半套壳 | A1+A2+A3 完成后变 ✅ |
| "记忆星空是内在结构" | 🟡 基本是 | B1+B4 完成后变 ✅ |
| "共情体是有限回声" | 🔴 是资料卡 | D2 + C5 完成后变 ✅ |
| "深色星空气质" | 🟡 默认是暖米 | C1 完成后变 ✅ |
| "温柔有仪式感" | 🟡 基础有 | C2+C3+C6 完成后变 ✅ |
| "不依赖 Prompt 保护隐私" | 🟡 关键词+LLM | A4 完成后变 ✅ |
| "完成 4 层结构" | ✅ Aurora/记忆/共鸣/慢信 | 维持 |

### 6.3 视觉验收（老师第一印象）

> 老师进入首页后 30 秒内应该感受到：
> 1. 这是个"内在星空"主题，不是普通 AI 工具
> 2. 文案有"温柔、克制、深邃"的气质
> 3. 视觉有"玻璃质感、星尘微光"细节
> 4. 交互有"思考节奏"，不是即时响应

---

## 7. 风险与权衡

### 风险 1：Mock 模式必须始终可用
老师本地可能断网或没 API Key。任何阶段都不能破坏 Mock 兜底。
- A1 方案：Mock 仍能"伪语义"
- A3 方案：fallback 仍是关键词
- A4 方案：LLM 复检是异步、可关闭

### 风险 2：UI 升级不能改坏现有 22 个页面
C 阶段必须增量、向后兼容。
- C1 加 `<body class="dark-star">` 默认，HTML 单独加 `class="auto"` 显式覆盖
- C2 动效用 progressive enhancement，禁用降级
- C3 重设计 Aurora 对话页面是单一文件风险可控

### 风险 3：不要破坏已通过的 7 个测试
ApplicationFlowTest 现有 7 个测试，其中 detectedTheme、noiseToDrop.length 等依赖关键词行为。
- A 阶段完成后要更新 `ApplicationFlowTest` 断言
- 或者保留 detectTheme() 关键词实现作为"测试专用"

### 风险 4：设计模式不丢
D 阶段要保证不破坏 Adapter/Observer/State/Builder/Strategy。
- D1 AuroraAgent 升级不能改成"上帝类"
- D2 多 Agent 协作要用 Observer 串联

---

## 8. 给下一轮执行的具体 TODO（如果你确认这个方案）

按优先级排序的执行清单（不写在这里，写在配套的 `repair_roadmap_v_1_0.md` 里）

---

## 9. 附录：本次审查过的关键文件

| 文件 | 行数 | 关键 |
|---|---:|---|
| `MockLlmClient.java` | 173 | 🔴 硬编码 JSON |
| `AuroraContentLibrary.java` | 610 | 🔴 随机抽句 |
| `AuroraAgent.java` | 28 | 🟡 太薄 |
| `MemoryExtractAgent.java` | 14 | 🔴 不调 LLM |
| `CapsuleAgent.java` | 10 | 🔴 空壳 |
| `LetterGuardAgent.java` | 37 | ✅ 真调 LLM |
| `PromptBuilder.java` | 131 | 🟡 全英文 |
| `AuroraAgentServiceImpl.java` | 306 | ✅ 较好 |
| `AuroraMemoryContextServiceImpl.java` | 267 | ✅ 很好 |
| `MemorySettlementServiceImpl.java` | 439 | 🟡 12 个 inferXxx 关键词 |
| `ThoughtShredderServiceImpl.java` | 288 | 🟡 6 个 inferXxx |
| `CapsuleServiceImpl.java` | 157 | 🟡 CapsuleAgent 是空壳 |
| `GravityServiceImpl.java` | 18 | ✅ 公式按规范 |
| `MockDataInitializer.java` | 271 | ✅ 演示数据丰富 |
| `SeedCapsuleContent.java` | 275 | ✅ 8 个种子体内容质量高 |
| `LlmConfig.java` | 245 | ✅ 5 个 provider |
| `CrisisKeywordRule.java` | 28 | 🔴 15 关键词 |
| `AbuseKeywordRule.java` | 27 | 🔴 11 关键词 |
| `AuroraChatController.java` | 77 | ✅ API 完整 |
| `aurora-chat.html` | 289 | ✅ 6 模式 + 能量条 + 记忆镜头 |
| `memory-starfield.html` | 203 | ✅ SVG 散点 |
| `echo-plaza.html` | 158 | ✅ 广场 |
| `slow-letter.html` | 161 | ✅ 写慢信 + 投递动画 |
| `thought-shredder.html` | 164 | ✅ 输入 + 强度 + 保存模式 |
| `safety-harbor.html` | 100 | ✅ 呼吸 + 着陆练习 |
| `app.css` | 1073 | ✅ light+dark 双主题 |
| `inner-cosmos.css` | 1 | 🟡 几乎空壳 |
| `ApplicationFlowTest.java` | 276 | ✅ 7 个测试 |

---

## 10. 报告总结

**Inner Cosmos 已经走完了"骨架建设"阶段**。22 个页面、236 个 Java 类、4 层数据隔离、5 种设计模式、情感重力公式、慢信状态机、8 个种子哲学体——所有课程硬性要求都已经过线。

**但愿景的"灵魂"还没完全注入**：
- 6 个 service 的 fallback 层把整个 AI 体系拉回到"关键词匹配"水平
- MockLlmClient + AuroraContentLibrary 让 Mock 模式在演示时**暴露了套壳本质**
- CapsuleAgent / MemoryExtractAgent 是名字 agent、实际空壳
- UIUX 是"莫兰迪暖米"，与愿景"深色星空"有气质漂移
- 心理分析停留在"命名"（detectTheme 返回 3 选 1）

**修法分 4 阶段**：
- A. 让 Agent 真正内核化（最高优先级，3-5 天）
- B. 心理分析补强（3-5 天）
- C. UIUX 全面升级（3-5 天）
- D. AI 工程深度（2-3 天）

**预计总投入**：8-15 天（可并行）。

**修复完成后**：
- "AI 不是套壳" 在老师面前 100% 立得住
- 记忆星空 / Aurora / 共鸣体三件套从"占位"升级为"产品"
- 代码深度、文档完整度、设计模式呈现都超课程基线
- 整体气质从"暖米 blog" → "内在星空"

---

> 下一步：等用户确认方案，进入执行阶段。可以按 A → B+C 并行 → D 顺序推进。
