# CP-32 匹配三模式召回排序与可解释共同点 — 首个工程增量

- 日期：2026-09-13；实施：后台实现 agent（ResonanceMatch/CapsuleService 匹配域），主线程整合回归
- 愿景：V11（匹配可解释——每个候选说得出为什么，不玄学）、V12

## 交付内容

### 1. 三模式信号分类器（`ResonanceModeAssessor`，纯函数）

全部基于既有可计算信号，无新数据依赖：
- **SIMILAR（相似）**：每共同主题域 +0.34；画像印证 +0.15；语义相近（既有 semanticSignal ≥0.05，沿用 SEMANTIC_REASON_THRESHOLD 惯例）+0.20；封顶 1.0；
- **COMPLEMENTARY（互补）**：定向桥接「查看者压力主题 × 对方供给主题」，每桥 +0.30 封顶 0.60；桥接对原样沿用既有 GROWTH_EDGE 三组方向（任务压力→希望期待、情绪承压→认知探索、自我评价→关系牵动），仅在「查看者有压力 ∧ 对方有供给 ∧ 查看者没有该供给」时成立，无桥即如实 0；
- **UNEXPECTED（意外）**：前提主题域完全不重合；情绪痕迹重合 +0.40；记录时段相近（夜间 22:00–05:59/日间 06:00–21:59，查看者主导桶 ≥2 条严格过半且对方共鸣体 createdAt 同桶）+0.30；封顶 0.55。

主导模式取最大者（同分 SIMILAR>COMPLEMENTARY>UNEXPECTED）；偏好激活时模式得分接管 relevance/排序（与遗留公式同构、同 0.99 封顶），resonant 放宽为「遗留共鸣 ∨ 模式信号>0」——互补/意外候选从匿名补充位升级为带标签候选。**偏好为 null/NONE 时逐字节保持遗留公式**（测试守卫 0.15 无地板值不变）。三层硬过滤（屏蔽/同意/可见性）零改动。

### 2. 解释字段（`ResonanceMatchExplanationVO` 进 VO/响应 map）

- `reasons` 全部含真实计数、可从构造信号复原（「共同主题：任务压力（你的记忆中出现3次）」「互补方向：你的『任务压力』主题 × 对方的『希望期待』主题」「跨域信号：你的记忆多在夜间形成（3/4条），对方共鸣体也创建于夜间」）；
- 时间信号只写「创建于」单时间戳，不夸大为对方长期节律；
- `confidence`：SIMILAR 有共同主题 sufficient/仅画像语义 weak；COMPLEMENTARY ≥2 桥 sufficient/1 桥 weak；UNEXPECTED 恒 weak；
- 三档全 0 → mode=null、reasons 空、**confidence=insufficient_signal**（不编造理由）；指定偏好时排序用偏好模式得分但标签/三档得分恒报真实主导模式全量透出供核对；
- 解释只回显查看者自己的 P1 信号与对方公开信号，无越权面；
- API：`/api/plaza/matches?mode=BALANCED|SIMILAR|COMPLEMENTARY|UNEXPECTED`（非法值 400）。

## 发现的既有缺陷（如实，未改）

`capsuleThemeProfile` 对单个共鸣体只 merge 一次，家族频次恒 ≤1，`min(userFreq, capFreq)` 退化为存在性判断——themeOverlap 实际等于共同家族数 ×0.18，代码注释宣称的频次加权空转（不影响正确性，登记后续批次决定是否修）。

## 测试证据

- 新增 `ResonanceMatchThreeModeTest` 11/11（模式标签正确、reasons 与构造信号一致、insufficient_signal 冷启动与弱信号两路、三模式排序稳定性、遗留公式逐字节守卫）；
- 匹配链路回归 40/40（CapsuleMatchingTest 22 + PrecisionRecallTest 7）+ 链路回归 48/48（ApplicationFlowTest、CuratedDemoCapsuleJourney、ResonanceLineNegative 等）；
- 主线程全量整合回归：**backend 1813/1813（2 Docker-gated skips）**。

## 诚实边界

- 前端消费未接（本批纯后端，任务明确；解释字段已进 VO，登记后续）；
- UNEXPECTED 未引入情绪节律曲线（EmotionTrace 时序基线）——跨用户时序聚合超出「既有匹配信号推导」边界，现有两信号已可工作，如实留作后续。
