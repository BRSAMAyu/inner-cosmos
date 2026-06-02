# Inner Cosmos — 修复执行清单 v1.0

> 本文件是 `gap_analysis_v_1_0.md` 的执行版。
> 每一项可单独勾选、可分批完成、每项完成后写一行 commit message 风格的备注。

---

## 阶段 A：让 Agent 真正"内核化" 🕐 3-5 天 🔴 最高

### A1. 重构 MockLlmClient — 伪语义而非硬编码
- [ ] 创建 `ai/lexicon/ChineseSentimentLexicon.java`（~500 词情感词典）
- [ ] 创建 `ai/lexicon/ChineseStopwords.java`
- [ ] 创建 `ai/lexicon/ChineseIntensifiers.java`
- [ ] 创建 `ai/semantic/PseudoSemanticAnalyzer.java`（词袋+主题提取+意图分类）
- [ ] 重构 `MockLlmClient.structuredJson()`：根据 input 动态生成 JSON
- [ ] 测试："今天被老师骂了" vs "今天很开心" 输出不同

### A2. 升级 AuroraContentLibrary — 关键词 → 模板
- [ ] 给 270 个模板打 `tags: List<String>` 标签
- [ ] 实现 `pickByRelevance(mode, userInput, topK)`：词袋相似度排序
- [ ] 替换 `pick(RANDOM.nextInt)` 调用
- [ ] 测试："老师骂我了" 应返回含"权威/评价/委屈"的模板

### A3. 升级所有 fallback 路径（关键词 → 情感词典 + 句法模式）
- [ ] 升级 `MemorySettlementServiceImpl` 12 个 inferXxx：用情感词典替代 containsAny
- [ ] 升级 `ThoughtShredderServiceImpl` 6 个 inferXxx：同上
- [ ] 升级 `AuroraAgentServiceImpl.detectTheme`：用 lexicon 替代 containsAny
- [ ] 升级 `AuroraMemoryContextServiceImpl.proactiveSuggestions`
- [ ] 验证 ApplicationFlowTest 仍然通过

### A4. 升级 SafetyBoundaryFilter — 关键词 + LLM 异步复检
- [ ] `SafetyMatch` 增加 `llmReview: bool` 字段
- [ ] MEDIUM 级别触发异步 LLM 复检
- [ ] 复检结果写入 SafetyEvent
- [ ] 测试 "我希望我从没出生" → HIGH
- [ ] 测试 "活着好累" → HIGH
- [ ] 把"威胁" "骚扰" 升级到 HIGH（"我要威胁并骚扰别人"应 HIGH）

### A5. PromptBuilder 中文化
- [ ] `withSystemBoundary()` 输出中文版
- [ ] `withOutputSchema()` 输出中文版
- [ ] yml 开关 `llm.prompt-language: zh-CN | en-US`
- [ ] Prompt 整体可读性测试

---

## 阶段 B：补强心理分析能力 🕐 3-5 天 🟡 高

### B1. 主题聚类（用 LLM 替代关键词）
- [ ] 重构 `ThemeAggregationServiceImpl`：用 LLM 聚类当前活跃主题
- [ ] 主题命名、摘要都用 LLM
- [ ] Dashboard 新增"主题云"卡片
- [ ] 记忆详情页显示"所属主题"链接

### B2. 信念识别（BELIEF extraction）
- [ ] 在 `MemoryExtractAgent` 增加 belief extraction
- [ ] 加强 ThoughtFragment.BELIEF 类型
- [ ] Memory Detail 新增"信念光环"展示
- [ ] 识别"如果 X 那 Y" / "我总是..." / "我总觉得..." 模式

### B3. 关系网络
- [ ] Aurora 主动识别"你提到了老师"
- [ ] Echo Capsule 页新增"关系温度图"
- [ ] 长期关系模式识别（同一人物跨多次对话）

### B4. 情绪时间线（ECharts 或 SVG 折线）
- [ ] 后端 `/api/emotion/timeline?range=7d` 30d
- [ ] Daily Record 页显示本周/本月情绪曲线
- [ ] 关键日标注（高 gravity 记忆出现日）

---

## 阶段 C：UIUX 全面升级 🕐 3-5 天 🟡 高

### C1. 把 dark-star 设为默认
- [ ] 全局 `<body class="dark-star">` 默认
- [ ] 提供晨/夜切换（localStorage 持久化）
- [ ] 首页 hero 在两种主题下重新优化
- [ ] 测试所有 22 个页面在 dark-star 下都好看

### C2. 动效系统
- [ ] 在 `js/common.js` 暴露 `IC.motion.fadeIn/stagger/starAppear/letterFly`
- [ ] Aurora 对话：消息 stagger 入场
- [ ] 记忆星空：星体 radial grow
- [ ] 慢信投递：飞行轨迹 + 星尘拖尾
- [ ] 共鸣体对话：每次发言回声波纹

### C3. 重新设计 Aurora 对话页面
- [ ] 顶部 Aurora 状态（图标 + 当前状态）
- [ ] 中央对话区（更宽敞）
- [ ] 底部输入 + 模式 + 语音（一行）
- [ ] 左侧记忆镜头（已有）
- [ ] 右侧实时模式提示
- [ ] 模式切换平滑动画

### C4. 记忆星空主题聚类可视化
- [ ] 后端：聚类 MemoryCard 主题
- [ ] 前端：force-directed layout 或 D3 cluster
- [ ] 主题云悬浮在右侧
- [ ] hover 显示主题

### C5. 共鸣体页面人格差异化
- [ ] 每个 seed 一个 SVG 头像
- [ ] 主题色变量 `--capsule-color`
- [ ] 互动时 persona 头像呼吸动效
- [ ] 8 个种子的"独立气质" 视觉

### C6. 慢信仪式感
- [ ] 信纸用 SVG/CSS 模拟手写
- [ ] 投递时飞行轨迹
- [ ] 收信"打开信封"动效
- [ ] 阅读时"沉淀"动效

### C7. Dashboard 升级
- [ ] 今日情绪天气
- [ ] 最近对话摘要
- [ ] 高重力主题卡
- [ ] 今日待办
- [ ] 共鸣体状态
- [ ] 未读慢信
- [ ] 推荐共鸣体

---

## 阶段 D：补强 AI 工程深度 🕐 2-3 天 🟢 中

### D1. AuroraAgent 真正升级
- [ ] 接收更多上下文（mode/memory/emotion/profile）
- [ ] 内部状态对象
- [ ] 输出前 self-check（"我刚才是不是诊断了？")

### D2. 多 Agent 协作链
- [ ] Aurora → MemoryExtract → CapsuleSuggestion 真实串联
- [ ] listener 里真正调用
- [ ] AiInteractionLog 记录完整 reasoning chain

### D3. Prompt 版本管理
- [ ] 存储 prompt 变更历史
- [ ] 启动时检测 drift
- [ ] Admin 页可看 prompt 版本

### D4. Token 估算 + 成本控制
- [ ] 每用户每日 token 限额（admin 可配）
- [ ] 超限温柔提示
- [ ] 远程 LLM 自动回退 Mock

### D5. A/B 测试框架
- [ ] Admin 后台选 "A/B 模式"：50% mock + 50% remote
- [ ] 比较 latency / fallback rate

### D6. Strategy 模式补全
- [ ] 8 个种子体每个 1 个 SeedStrategy
- [ ] CapsuleChatStrategy 实现真实 turn 计数 + 边界检查

---

## 阶段顺序与依赖

```
A1 → A2 → A3 → A4 → A5   (顺序)
         ↓
         B1 → B2 → B3 → B4   (B 依赖 A)
         ↓
         C1 → C2 C3 C4 C5 C6 C7   (C1 是基础)
         ↓
         D1 → D2 → D3 → D4 → D5 → D6
```

**可并行**：
- A 在改后端时，C 可以改前端
- B 在做产品功能时，D 在做工程基建

**关键里程碑**：
- A 完成后：Mock 模式不再是"硬编码 JSON"
- A4 完成后：安全过滤能识别隐晦内容
- C1 完成后：UI 整体气质切换
- D2 完成后：多 Agent 真正串联

---

## 单项提交风格建议

每完成一个子项，写一行 commit：
```
[STAGE-A1] 引入情感词典 PseudoSemanticAnalyzer, 让 Mock 也有真分析
[STAGE-A2] AuroraContentLibrary 改为按相关度取句
[STAGE-C1] dark-star 设为默认主题
```

---

## 完成时检查清单

- [ ] 所有 P0 致命级漏洞修复
- [ ] Mock 模式下 Aurora 回复对不同输入明显不同
- [ ] 22 个页面在 dark-star 主题下都好看
- [ ] 7 个 ApplicationFlowTest 全部通过
- [ ] 课程要求全部满足（代码量/页面/模式/文档）
- [ ] AiInteractionLog 覆盖所有 AI 调用
- [ ] 多 Agent 协作链在 listener 中真实跑通
- [ ] 心理分析页面（情绪时间线/主题云/关系温度）上线
