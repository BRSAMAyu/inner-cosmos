# CP-12 首个可编码增量 — WCAG 2.2 AA 自动化合同（键盘/焦点/读屏/动效/文本缩放）；CP-19 预算压力 fixture

- 日期：2026-09-12；CP-12（非作者可用性与体验封板，可编码切片）+ CP-19（预算压力 fixture）
- 蓝图依据：§J 目录前置条款——无障碍目标 WCAG 2.2 AA（键盘/焦点、读屏、文本缩放、减少动态效果、对比度、错误识别一起验证）

## CP-12 交付（检查点 23）

1. **ConsentRequestDialog 焦点契约**（J01 最关键的决策面）：
   - 打开时焦点移入对话框面板本身（tabIndex=-1）——不预选"同意"也不预选"暂不"（同意必须是用户自己的动作，不给键盘默认项）
   - Escape 关闭=不授权（不触发 grant）；busy 期间 Escape 被守卫
   - 关闭后焦点还给打开它的元素（`previouslyFocused.restore`）
2. **读屏命名**：`PortraitClaimsPanel` 理由输入框加 aria-label（placeholder 不构成标签）；合同测试扫描全部按钮必须有非空可访问名
3. **`NonAuthorUsabilityContract.test.tsx` 8/8**：
   - 焦点进入对话框且不预选任一选择；Escape=dismiss 非 grant、busy 守卫、焦点归还
   - 三个新表面（画像面板/开场连续性卡/同意对话框）全部交互控件具名；理由输入框有真标签
   - 样式表静态合同：`prefers-reduced-motion` 显式覆盖存在；`:focus-visible` 存在；根字号未被固定 px 钉死（文本缩放可存活）
4. **诚实边界**：200% 缩放真机、三运营商弱网矩阵、对比度实测、非团队用户无讲解研究（15 人 ≥13 完成）是 CP-12A 人工研究门，本轮只交付可自动化的工程合同

## CP-19 交付：预算压力 fixture（检查点 23）

`KernelBudgetStressFixtureTest` 2/2（仓库内合成内容，确定性，无 provider 调用）：
- 七类压力轴各带期望内核：长叙述(≥160字)/明确分析请求/多部分(双问号)/困境(累赘类,升 DUAL 非支持流)/危机(即使叠加全部复杂度信号也走 SUPPORT_FLOW)/简单对照×2
- 断言①：确定性路由器逐轴精确命中期望内核
- 断言②：组合 adaptive 决策（预算腿 OR 复杂度腿）为每个压力轴挣得慢内核、简单轮次保持快路径；每个 fixture 再跑双核生成路径验证模块契约与结构有效回复
- 补齐冻结集对拍（检查点 21）的空缺：development 分裂简单轮次主导无法测量的路由差分，现在有了可测的压力轴目录

## 状态与测试

- 前端 `npm test -- --run` **731/731 绿**（新增 8），`tsc -b` 零错误；后端 KernelBudgetStressFixtureTest 2/2
- CP-12: IN_PROGRESS（自动化 a11y 合同落地；CP-12A 真人无讲解研究与真机矩阵为人工门）
- CP-19: IN_PROGRESS（纯函数路由+生产接线+冻结集对拍+预算压力 fixture 完成；真实 provider 内容增益评分后续）

## 第五增量（同日）：令牌级对比度自动化审计（检查点 25）

1. **`web/src/ContrastTokenAudit.test.ts`（3/3）**：从 styles.css 解析设计令牌——默认暖夜 `:root` 与**合并后生效的** `:root[data-theme="day"]` 双块（CSS 级联后者覆盖）——按 WCAG 2.x 相对亮度公式计算 11 组文字承载配对（正文/次要/元信息/危险文本 × 画布与浮层；on-accent-strong × 三种强调底；on-plum-strong × plum），断言全部 ≥4.5:1；非十六进制令牌值（var()/渐变）进入配对立即报错
2. **审计发现并修复 8 处 day 主题真实 AA 违规**：text-muted 4.32 / text-faint 2.92 / danger 3.69+4.00 / on-accent-strong×3（2.74–3.93，Morandi 中调强调底配浅字） / on-plum 4.29——修复全部为令牌级：`--text-muted→#5c6360`、`--text-faint→#5f6560`、`--danger→#96504a`、`--on-accent-strong→#100b05`（中调强调底改配深字，与夜主题同一策略）、`--accent-plum→#6d5f68`（白字 5.61:1）；夜主题 11 组全部原生通过
3. 令牌级而非渲染 DOM 级是刻意的：令牌是所有未来组件继承的合同，回归在任何组件带上它之前就失败
4. web 全量 **735/735 绿**（新增 3），tsc 零错误

## CP-22：标签集 v1.1.0 扩充（同检查点 25）

1. 新增四案例（v1.0.0 八案例不变，manifest 升 1.1.0 + change_log + 新 SHA 1a226a7f…）：
   - **negation_topic_correction**（MR-009）："不是膝盖疼，是脚踝扭伤"——被否定的膝盖记忆不得凭查询中出现的否定词进入（词法命中 0.11<0.18）
   - **negation_service_boundary**（MR-010）："先别给建议，我只想说说加班的事"——用户拒绝建议，TODO 建议类记忆不得进入
   - **time_preference_ordering**（MR-011）：同等词法相关下，3 天前 vs 160 天前的复盘——新近者必须排第一（新鲜度信号，schema 新增 `lastTouchedAtDaysAgo` 字段支持记忆老化）
   - **time_window_exclusion**（MR-012）："最近"时间窗外（700 天前）的同主题弱重叠旧伤记录不得进入
2. 案例文本在 v1.1.0 冻结前做了可发现性校准（相关记忆需有足够词法覆盖才能被诚实召回；v1.0.0 案例零改动），manifest change_log 如实记录
3. `MemoryRetrievalLabeledEvaluationTest` 保持绿（12 案例全过）；后端全量回归 **1581/1581** 绿
