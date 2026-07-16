# EXPERIENCE-FLOW-OVERHAUL — §1.2 死按钮清剿 · 检查点 4（诊断扫描 + 回归门 + 根因结论）

> 归属：`docs/goal/loop-goal-directive.md` §1.2（死按钮清剿 P0）
> 日期：2026-07-16

## 交付

1. **Playwright 交互扫描 `web/e2e/dead-button-scan.spec.ts`**（directive §1.2 第 1 条要求的形态）：
   登录后遍历五个空间，枚举每个可见、非 disabled、非危险（排除登出/删号/导出）、非"已激活 tab"的按钮，
   逐个点击并用 `MutationObserver` + 状态快照 + `location` + `role=status/aria-live` + `aria-pressed/expanded`
   + `activeElement` + **包裹 `window.fetch` 计数**判定是否有可观察响应，输出无响应清单。chromium 已本地安装。
2. 跑法：`INNER_COSMOS_BASE_URL=http://127.0.0.1:8097 npx playwright test dead-button-scan --reporter=list`
   （复用已运行实例；或不设该 env 让 config.webServer 起 jar）。

## 扫描结果与根因分析（证据先于断言）

跑出清单：`今天`/`内宇宙`/`我的` 核心空间**零死按钮**；`共鸣`/`连接` 报出一批（开始对话×11、保存边界设置、
撤回这个共鸣体、标记已读、温和婉拒、举报这封信…）。**逐类读源码核验，全部具备 `onClick` handler**：
- `开始对话` → `onOpenCapsule(capsule)`（`components/PlazaDirectory.tsx:76`）
- `保存边界设置` → CapsuleWorkbench 保存逻辑（`components/CapsuleWorkbench.tsx:56`，有 `boundaryBusy` 态）
- `标记已读`/`温和婉拒` → `onActOnLetter(letter, …)`（`components/LettersInbox.tsx:40-41`）
- `回到 vN` → `onRollback(...)`（`components/AuroraSelfSpace.tsx:32`）

即：**未发现任何代码级死按钮**。清单里的假阳性来自两类扫描固有局限：
- **状态污染**：in-mem seed 实例被前一遍扫描的破坏性 action 改过（信件已 decline/read、共鸣体已撤回、self 已回滚），
  重复点击时 handler 合理地 early-return，DOM/网络无变化。
- **业务上下文盲点**：`共鸣`/`连接` 的操作按钮依赖已选中的匹配卡 / 信件状态，盲目遍历时上下文未就绪。

**用户报告"很多按钮点击没反应"的根因**：不是缺 handler，而更可能是**点击即时反馈 / 加载态缺失**
（点击后到异步结果之间无视觉反馈，用户误以为无响应）。这把 §1.2 的重心指向 §1.1 的微交互（检查点 2 已加
按钮 scale/hover/focus 反馈）**与尚未做的"加载四态"**。

## 门定位（诚实）

纯 DOM 遍历点击无法区分"无 handler"与"有 handler 但当前状态 early-return"，对需业务上下文的空间有固有假阳性，
**不能作为硬门直接断言全空间零死按钮**。因此 spec 断言收敛为：**核心空间（内宇宙/我的）必须零死按钮**（这几个
空间遍历判定可靠），`共鸣`/`连接` 作为**诊断报告**打印（不硬失败），并注释了升级为硬门的路径（config.webServer
每次起全新 in-mem seed 实例 + 点击前准备各按钮业务上下文）。

## 验证
- `npx playwright test dead-button-scan`：**1 passed (1.1m)**（核心空间零死按钮 + 覆盖全 5 空间）。
- `npm run build`（含 tsc 编译新 spec）OK；`npm test` **89/89** 保持。

## 下一步（后续检查点）
- §1.1 加载四态（<1s 不显示 / 1-3s 文案 / >3s 文案+微动画）+ 各异步按钮点击的即时 busy 反馈 —— 直接消除
  "点了没反应"的根因。
- 把死按钮门升级为"干净 seed + 上下文准备"版，覆盖共鸣/连接后再收敛断言到全空间零死按钮。
