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
