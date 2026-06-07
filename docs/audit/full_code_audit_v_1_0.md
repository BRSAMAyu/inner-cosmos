# Inner Cosmos 深度代码审查报告

> 审查日期：2026-06-06
> 范围：Java 后端（30 Controller + 31 Service） + 前端（29 HTML + 9 JS + 2 CSS）
> 方法：API wrapper 死代码扫描、JS lib 引入交叉对比、HTML 内联 fetch 与 wrapper 一致性核对、UI 控件事件挂钩完整性
> 目的：在不动编译、测试可过现状下，找出"看似做了但没接上"或"有接口没前端"的真实漏洞

---

## TL;DR

经过 4 轮独立工具验证（grep / Python 死代码扫描 / Python lib 引入矩阵 / 内联 fetch 抓取），发现：

| 等级 | 数量 | 描述 |
|---|---:|---|
| P0 致命（用户立刻能感受到） | 7 | 历史消息丢失、主动呼唤完全没接、4 页面无昼夜感等 |
| P1 重要（功能挂空挡） | 11 | Capsule 无法编辑、Memory 重要度无法调、3 个新页面没接全部 API 等 |
| P2 设计过度（死代码不影响使用） | 17 | 11 个 API wrapper 完全没人调 + 6 个 wrapper 因直连 fetch 而死 |
| P3 产品缺口（前后端都缺） | 4 | 无 Capsule 编辑页 / 无 A/B 报告页 / 无 Token 面板 / 无 Prompt 版本管理页 |

**编译和测试都干净**，但用户层"能点不能工作"和"看起来有按钮点了没反应"是主要问题。

---

## P0 致命：用户立刻能感受到的功能缺陷

### P0-1. Aurora 对话历史消息全没（每次新建 session）

**位置**：`pages/aurora-chat.html:177-184`

**问题**：
```js
async function init() {
  await IC.ensureDemoLogin();
  buildModes();
  const r = await API.createSession({ title: "Aurora 对话", sessionType: "AURORA_CHAT" });
  if (r.success && r.data) sid = r.data.id;
  await loadAiStatus();
  await proactiveGreeting(true);
}
```

`init()` 每次进入页面都 `createSession` 新建会话，**没有读 URL `?sessionId=` 也没有调 `sessionMessages` 拉历史**。
用户昨天和 Aurora 谈过的心事、形成的"线索"，**今天打开页面全空**。

**API 现状**：
- `API.createSession` ✅ 用了
- `API.sessionMessages` ❌ 死代码（grep 0 匹配）
- `API.finishSession` ✅ 用了

**修复方向**：
1. 改为读 URL `?sessionId=`，有则 `loadHistory(sid)` 否则新建
2. 进入页面立刻 `await sessionMessages(sid)` 把历史 bubble 渲染出来
3. 顺手在 init 后调 `auroraMemoryContext(sid)` 把"记忆上下文"做更主动的拉取

**用户能感受到**：他打开 Aurora 像"失忆朋友"，昨天的事他完全不知道。

---

### P0-2. Capsule 对话历史全没（同样问题）

**位置**：`pages/capsule-chat.html:105, 181`

**问题**：
```js
const r = await API.createPersonaChat(Number(capsuleId));  // 每次新建
...
const r = await API.personaChatMessage(sid, text);
```

同样模式：每次进入 capsule-chat 都新建 `personaChatSession`，**历史对话全没**。

**API 现状**：
- `API.createPersonaChat` ✅
- `API.personaChatMessage` ✅
- `API.personaChatMessages` ❌ 死代码（grep 0 匹配）

**用户能感受到**：和某个共鸣体谈过 5 轮了，关掉浏览器再打开，**又从 0 开始**。

---

### P0-3. Aurora 主动呼唤 B5 完全没接 Dashboard

**位置**：`pages/dashboard.html`（全文 grep `auroraProactiveCheck` 0 匹配）

**问题**：我之前加了 `AuroraProactiveController` + `AuroraProactiveService`（后端 67 行有 Quiet Hours 检查），`api.js` 也有 `auroraProactiveCheck` / `auroraProactiveDismiss` wrapper，**但 Dashboard 没有调用，Dashboard 也就没有"主动问候气泡"功能**。

**用户能感受到**：用户自己说"Aurora 是不是朋友？朋友会主动打招呼"——**Dashboard 上完全没体现**。

**修复方向**：
1. 在 `dashboard.html` 顶部加 `<div id="auroraProactive"></div>`
2. 进入页面后 `await auroraProactiveCheck()` 拉是否有未读问候
3. 渲染气泡（带 "刚才" / "1 小时前" 时间和 dismiss 按钮）
4. 用户点 dismiss 调 `auroraProactiveDismiss(id)` 后端标记已读

---

### P0-4. 4 个页面缺核心视觉 lib（无时间感 / 无天气感）

**位置**（grep 0 匹配 `time-system.js` / `weather-system.js`）：

| 页面 | 缺什么 | 影响 |
|---|---|---|
| `heart-diary.html` | time-system, weather-system | 日记本没有"今天是深夜""外面在下雨"的流动感 |
| `memory-starfield.html` | time-system, weather-system | 星空没有"白天星淡 / 夜晚星浓"的昼夜感 |
| `timeline.html` | time-system, weather-system | 时间线没有"现在的光照在回忆上"的暗示 |
| `todo.html` | time-system, weather-system | 待办没有"夜晚"和"雨天"的状态调整 |

这是和"AI 是镜子""动态时间""动态天气"三大产品定位直接冲突的。

**用户能感受到**：她设计语言最自豪的"动态时间 + 动态天气"在 4 个最常用的页面**没生效**。

**修复**：4 个页面 `<script>` 区补 2 行 src 即可，1 分钟的事。

---

### P0-5. Memory 卡片重要度无法调整

**位置**：`pages/memory-starfield.html`（`updateMemoryImportance` 0 匹配）

**问题**：`pages/memory-starfield.html:152` 有"归档"按钮（`onclick="archiveCard(...)"`），但**没有"调整重要度"按钮**。

但产品逻辑：`tb_memory_card` 有 `importance` 字段（1-5），`updateMemoryImportance` 后端写好，前端 wrapper 也写好，**就是没人调**。

**用户能感受到**：他说"这张卡很重要"——但系统**永远不知道**。情感重力计算因此也不准（`emotionalGravity = intensity * recurrence^0.7 * importance * (1+triggers*0.1)`，**importance 永远是默认值**）。

---

### P0-6. Capsule 边界 / 上下文无法编辑（产品缺口）

**位置**：`pages/capsule-create.html:299-315` 一次性 createCapsule；`pages/capsule-detail.html:160` 只能改 visibility

**问题**：capsule 创建时一次性塞了 `allowTopics / blockedTopics / contextPreviewJson / ownerContextNote / standInEnabled / realContactPolicy`——**创建后想改，没入口**。

`api.js` 有 `updateCapsuleBoundary` / `updateCapsuleContext` wrapper 写好，`capsuleBoundary`（读取）能调，**但没有编辑页面**。

**用户能感受到**：他创建共鸣体时"不代回声"，过几天改主意想开了，**改不了**。

---

### P0-7. 情绪时间线 / 关系温度 / 信念画廊缺关键交互

**问题**：
- `pages/emotion-timeline.html` 用了 `emotionTrend/Patterns/Stability`，**没用 `emotionToday` / `emotionRange`** —— 时间线没"今日/7天/30天"切换
- `pages/relations.html` 用了 `relationList/Health/Stats`，**没用 `relationHighEmotion` / `relationTimeline`** —— 缺"高情绪关系"+"关系时间线"两块
- `pages/beliefs.html` 用了 `beliefList/Strong/Contradictions`，**没用 `beliefByCategory`** —— 缺"按分类筛选"

**用户能感受到**：我新做的 3 个新页面**只接了 50% 的 API**，关键切换器失效。

---

## P1 重要：功能挂空挡

### P1-1. Aurora 没用真正的 SSE 流式

**位置**：`pages/aurora-chat.html:219` `await fetch("/api/aurora/message/rich", { method: "POST" ... })`

后端 `/api/aurora/stream` 是 SSE（Server-Sent Events）接口，但前端用了 **`/api/aurora/message/rich` POST 一次性返回**——**不是流式**。

`API.auroraStream` 是死代码。**"打字机"动效靠 `setTimeout` 模拟**（line 119: `setTimeout(() => addBubble(...), index * 520)`），不是真流式。

**问题**：当后端响应慢（4-10 秒），用户看到的是"输入框变 disabled + typing 文字显示"，**没有 Aurora 正在思考 + 慢慢打字的真实流动感**。

**修复方向**：改用 `EventSource` 监听 `/api/aurora/stream`，每收到一个 chunk 就 append。

---

### P1-2. 慢信缺"以信回信"和"信件对话"

**位置**：`pages/slow-letter.html`

**问题**：
- `API.letterReply`（普通文字回信）用了
- `API.letterReplyWithLetter` ❌ 死代码 —— "用一封慢信回另一封慢信"功能没接
- `API.letterThreads` ❌ 死代码 —— "同一对人的所有信件"会话视图没接
- `API.letterDeliver` ❌ 死代码 —— 管理员手动投递按钮没接

**用户能感受到**：慢信系统最有"文学感"的两块功能没用上。

---

### P1-3. 管理员缺"模型配置更新"入口

**位置**：`pages/admin.html`

**问题**：`API.adminModelConfig` ✅ 用了（读取），`API.adminUpdateModelConfig` ❌ 死代码——**没法在前端改 AI 模型配置**。

---

### P1-4. Aurora 主动呼唤 dismiss 没接

**位置**：（同 P0-3）

`API.auroraProactiveDismiss` 是死代码，Dashboard 即便加了气泡也 dismiss 不了。

---

### P1-5. ASR 后端能力写好但前端用 mock

**位置**：`pages/heart-diary.html:193, 365, 424`

**问题**：
- `API.diaryTranscribeAudio` ✅ 用了
- `API.diaryTranscribe` ✅ 用了
- `API.asrMockTranscribe` / `API.asrTranscribeFile` ❌ 死代码

但反过来看：heart-diary 是用 mock 还是真 MiMo ASR？**没真验证过**（因为没启服务）。可能 mock 走通就完事了。

---

### P1-6. Memory 手动触发提取没接

`API.memoryExtract` ❌ 死代码——用户希望"我想立刻把这段对话抽成记忆"——**没按钮**。完全依赖 `finishSession` 流程触发。

---

### P1-7. Safety 主动 preflight 没接

`API.safetyCheck` ❌ 死代码——用户发信前应该 preflight 检测，但完全依赖后端拦截。**前端没"发送前提示"**。

---

### P1-8. Heart-diary AI 分析日记没接

`API.diaryAnalyze` ❌ 死代码——日记本应该有"分析今天的情绪轨迹"，**没接**。

---

### P1-9. 我加的 3 个 Controller 没对应的前端

| 后端 Controller | 用途 | 前端 |
|---|---|---|
| `TokenEstimationController` | 算 token 用量、预测 | 0 页面调用 |
| `PromptVersionController` | Prompt 版本管理 | 0 页面调用 |
| `AuroraProactiveController` | 主动呼唤 | P0-3 已列 |

**用户能感受到**：他说"有 AI 智能体"——但 Token 用量看不到、Prompt 怎么演化的看不到、主动问候看不到（**最重要的那块**）。

---

### P1-10. ABTest 后端能跑无前端报告

`API.abtestAssign` / `API.abtestStats` / `API.abtestActive` 中只有 `abtestStats` 和 `abtestActive` 用了——**`abtestAssign` 是死代码**（其实 assign 应该是后端自动派发，前端不需要调），但**没有 A/B 报告页面**给老师/管理员看。

---

### P1-11. Login 直连 fetch，wrapper 死代码

`pages/login.html` 用 `IC.api("/api/auth/login", ...)` 直连，**`API.login` wrapper 死代码**。功能 OK，但 wrapper 设计过度。

---

## P2 设计过度（死代码，不影响使用）

**共 17 个 API wrapper 没人调**：

```
abtestAssign, adminUpdateModelConfig, asrMockTranscribe, asrTranscribeFile,
auroraMemoryContext, auroraMessage, auroraMessageRich, auroraModes,
auroraProactiveDismiss, auroraStream, capsuleContextPreview, current,
deleteAccount, exportData, login, memoryExtract, personaChatMessages,
previewUserMirrorCapsule, relationHighEmotion, relationTimeline, safetyCheck,
sessionMessages, tokenDailyUsage, tokenEstimate, tokenForecast,
updateCapsuleBoundary, updateCapsuleContext, updateMemoryImportance,
updateProfile, userProfile, auroraSettle ✅不算（aurora-chat 用了）
```

**注意**：其中 `exportData` / `deleteAccount` / `userProfile` / `updateProfile` 在 `settings.html` 里用 `IC.api` 直连调了对应的 URL，**功能正常**，只是没用 `API.xxx` wrapper。

**纯死代码**（既无直连也无包装调用）：
- `abtestAssign`（后端自动）
- `adminUpdateModelConfig`（P1-3）
- `asrMockTranscribe` / `asrTranscribeFile`（P1-5）
- `auroraMessage` / `auroraMessageRich` / `auroraStream` / `auroraMemoryContext` / `auroraModes` / `sessionMessages`（aurora-chat 全部直连，P0-1 / P1-1）
- `auroraProactiveDismiss`（P1-4）
- `capsuleContextPreview` / `updateCapsuleBoundary` / `updateCapsuleContext`（P0-6）
- `current`（login 直连）
- `login`（login 直连）
- `memoryExtract`（P1-6）
- `personaChatMessages`（P0-2）
- `previewUserMirrorCapsule`（capsule-create 没用镜像预览）
- `relationHighEmotion` / `relationTimeline`（P0-7）
- `safetyCheck`（P1-7）
- `tokenDailyUsage` / `tokenEstimate` / `tokenForecast`（P1-9）

---

## P3 产品缺口（前后端都缺页面）

1. **无 Capsule 编辑页** —— 创建后边界/上下文无法调整（P0-6）
2. **无 A/B 报告页** —— 后端能跑，前端没 dashboard
3. **无 Token 用量管理页** —— 我加的 `TokenEstimationController` 没人用
4. **无 Prompt 版本管理页** —— 我加的 `PromptVersionController` 没人用

这 4 个"前后端都缺"是**最值得做的"产品级"补完**。

---

## 修复优先级建议（与课程提交对齐）

| 优先级 | 任务 | 价值 | 难度 | 估时 |
|---|---|---|---|---:|
| 1 | P0-4 补 4 页面 JS lib | ⭐⭐⭐⭐⭐ | 5min | 5min |
| 2 | P0-1 + P0-2 历史消息加载 | ⭐⭐⭐⭐⭐ | 30min | 0.5h |
| 3 | P0-3 主动呼唤气泡接 Dashboard | ⭐⭐⭐⭐⭐ | 30min | 0.5h |
| 4 | P0-5 Memory 重要度 UI | ⭐⭐⭐⭐ | 1h | 1h |
| 5 | P0-7 三个新页面补 API | ⭐⭐⭐ | 1h | 1h |
| 6 | P0-6 Capsule 编辑页 | ⭐⭐⭐⭐ | 2h | 2h |
| 7 | P1-1 真正 SSE 流式 | ⭐⭐⭐ | 1.5h | 1.5h |
| 8 | P3 A/B / Token / Prompt 管理页 | ⭐⭐⭐⭐ | 3h | 3h |
| 9 | P2 删除 / 重构死 wrapper | ⭐⭐ | 0.5h | 0.5h |

**总估时**：约 10 小时（一个完整工作日）

---

## 验证证据来源

| 工具 | 命令 | 结果 |
|---|---|---|
| 死代码扫描 | Python 解析 `api.js` 所有 `async xxx()` + grep 全部 HTML/JS | 38 个 wrapper 中 17 个 0 匹配 |
| 页面 lib 引入矩阵 | Python 读 29 个 HTML，对照 7 个 lib src | 4 页面缺 time-system + weather-system |
| 事件挂钩 | grep `onclick="..."` 配对 `function xxx()` 定义 | settings 4 个功能都有真实现 |
| 死路径 | grep `auroraProactive` 0 匹配 | B5 完全没接 |

---

## 备注

- **不动的部分**：mvn test 10/10 + mvn compile 0 错误 + yml key 清除 — 这三件事的成果**保持**
- **不影响课程的硬性要求**：Java 17 / Spring Boot 3 / 10000+ 行 / 5 模式 / 10+ 页面 — **仍满足**
- **本报告是"完整诊断"，不是"修复方案"**。修复方案见 P0/P1 段落，**按优先级挑 4-5 个做即可拿到 90% 的产品价值提升**

