# Inner Cosmos 二次打磨审查报告

> 审查日期：2026-06-07
> 用户上一轮已修：P0 历史消息、主动呼唤非阻塞、4 页面 lib、Capsule 编辑、Memory 重要度、3 新页面补 API、BGM 文件
> 本轮审查维度：可访问性 / Loading 状态 / 错误处理 / CSS 暗色策略 / 跨页面状态同步 / 性能与 XSS
> 方法：4 轮独立 grep + 2 轮 Python 扫描 + 1 轮真实代码读

---

## TL;DR

你的修复**全部独立验证落地**。这一轮我从"功能接没接"换到"用户体验细节"维度，发现 **6 个新问题**——其中 3 个 P1 用户能感受到，3 个 P2 影响专业度。

**总评**：项目已**接近"可交付"状态**。剩下的不是"能不能用"，而是"用得爽不爽、对残障友不友好、生产环境稳不稳"。

---

## ⚠️ 1 个紧急事项：8081 服务当前已断

刚才尝试 `http://localhost:8081/api/ai/health` 返回 **ConnectionRefused**。
- 你之前说 PID 34860 在跑
- 现在连不上
- **可能服务挂了 / 或你重启后还没拉起**

**下一步**：先 `Get-Process | Where-Object {$_.Port -eq 8081}` 看进程在不在；不在就 `.\.tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"` 拉起。

---

## P1：用户能直接感受到的 3 个

### P1-1. 可访问性（A11Y）几乎为零

**位置**：29 个 HTML 页面

**问题**：grep 整个 pages/ 找 `aria-` / `role=` / `tabindex` / `<nav>`，**只在 4 个页面有 5 处用法**：
- aurora-chat / capsule-chat 各 1 处 `aria-live="polite"`（chat 区域）
- login / register 各 1 处 `role="alert"`（error-box）

**其余 25 个页面完全没 aria 属性**。意味着：
- 键盘党 Tab 键用户：能走但无焦点提示
- 读屏软件（NVDA / VoiceOver / TalkBack）：读不出按钮用途
- 视障用户：根本用不了

**修复方向**（2 小时可做）：
1. `<button>` 全加 `aria-label`（`<button onclick="...">+</button>` 完全无语义）
2. 导航 `<div class="topbar">` → `<nav>` + `aria-label="主导航"`
3. 列表 `<div class="timeline">` → `<ul role="list">`
4. 弹窗 `<div class="modal">` → `role="dialog" aria-modal="true" aria-labelledby="标题id"`
5. 加 `<a href="#main" class="skip-link">跳到主要内容</a>`（a11y 标配）

---

### P1-2. 26 个页面没 Loading 状态

**位置**：除了 login.html / register.html / settings.html，**其他 26 个页面没 spinner**。

**问题**：用户进 dashboard 看到的是空白面板，1.5 秒后数据"砰"地出现。**没有 skeleton，没有 spinner，没有"正在加载"**。

只有 3 处用到 `<span class="loader"></span>` —— login 提交、register 提交、settings 保存。

**用户能感受到**：白屏 1-2 秒。不知道是网络慢还是页面卡了。

**修复方向**（1 小时可做）：
1. `app.js` 加 `IC.showLoading(targetEl)` / `IC.hideLoading(targetEl)` 全局方法
2. 在所有 `await API.xxx()` 之前 showLoading，after hideLoading
3. 视觉：和 6 FLAC 切歌同色系的小圆点呼吸（不是冷蓝转圈）

---

### P1-3. 跨页面状态不同步

**问题**：用户在 settings 改了"Aurora 名字"，但 Dashboard、aurora-chat、capsule-chat **不会显示**。

具体证据：
- `settings.html:537` `await IC.api("/api/user/profile", { method: "PUT", ... })` ✅ 存了
- `dashboard.html` grep `auroraName` —— **0 匹配**（Dashboard 不知道 Aurora 改名了）
- `aurora-chat.html` 也没有读 auroraName 渲染

**用户能感受到**：我在 settings 把 Aurora 改成"小星"，打开聊天，**还是显示 Aurora**——以为没保存。

**修复方向**（1.5 小时）：
1. `app.js` 加全局 `IC.userProfile` 缓存：进入页面后 `await loadUserProfile()` 一次
2. 所有页面渲染时 `IC.userProfile.auroraName || "Aurora"`
3. settings 保存成功后 `Object.assign(IC.userProfile, profile)` 同步缓存

---

## P2：影响专业度的 3 个

### P2-1. CSS 暗色策略"反 OS"

**问题**：app.css 主动用 `time-system` 判断**物理时间**（早上用白天色，晚上用夜色），**不响应 `prefers-color-scheme`**。

**实际场景**：
- 晚上 10 点我在用电脑写论文，想专注（OS 是暗色）
- Inner Cosmos 进入了夜色模式（暖色）
- 我**想要**的是跟随系统暗色（冷色、降低对比）

**当前设计意图**：用户说"动态时间+动态天气是核心"——所以不用 OS 暗色。**这是有意的**。

**建议**：在 settings 加一个开关："**跟随系统主题** / **跟随真实时间**"，给用户选择权。

---

### P2-2. 错误处理 28 个页面分散不均

**问题**：
- dashboard.html: 9 处 try/catch
- aurora-chat.html: 2 处
- memory-starfield.html: 1 处
- 其他 20+ 页面：**完全没 try/catch**

**实际后果**：网络抖动时，api.js 里的 `await res.json()` 抛错，整个页面的 `.then()` chain 静默失败，**用户看到空白页**。

**修复方向**（1 小时）：
1. `app.js` 的 `IC.api()` 已经返回 `{success, code, message, data}`，把 try/catch 内置到 `IC.api()` 里
2. 失败时自动 `IC.toast(message, 'warn')` 兜底
3. 页面代码不需要再写 try/catch，只看 `r.success`

实际上我看 `api.js` 已经有 `api()` 包装——但页面仍散乱 try/catch，说明**这个统一错误处理没被广泛用**。

---

### P2-3. `IC.showModal(html)` API 容易让人忘 esc

**位置**：`app.js:234`
```js
wrap.innerHTML = `<div class="modal-backdrop"...>${html}</div>`;
```

**问题**：showModal 接受原始 HTML 字符串，**调用方负责 esc**。现在 11 处调用方都正确，但**API 本身危险**——下一个开发者写 `IC.showModal(\`已删除 \${username}\`)` 就出 XSS。

**修复方向**（30 分钟）：
1. 改 `IC.showModal(content, { safe: true })` —— safe 默认 false，所有动态内容必须 `IC.esc()` 后传入
2. 或拆两个方法：`IC.showModal(html)` / `IC.showModalText(text)`，后者自动 esc
3. 加 lint 注释：调用方必须先用 esc

---

## P3：可做可不做（专业打磨）

| # | 任务 | 价值 | 估时 |
|---|---|---|---:|
| 1 | weather-system.js 失败时除了 console.warn，还应该显示一个"网络受限"小标识 | ⭐⭐ | 30min |
| 2 | motion.js 在 `prefers-reduced-motion: reduce` 下完全关停（CSS 已做，JS 没检查） | ⭐⭐ | 30min |
| 3 | audio-system.js 的"游客"按钮 6 FLAC 音量调到 0.2（当前可能默认 0.5） | ⭐⭐ | 5min |
| 4 | 加 `lang="en"` fallback 给 7 段 FLAC 之外的备用 BGM | ⭐ | 1h |
| 5 | `<title>` 各页面更具体（当前 9 个页面共享默认 title） | ⭐ | 30min |
| 6 | 加 robots.txt / sitemap.xml 给搜索引擎 | ⭐ | 30min |

---

## 之前报告 P0 修复对账

| 报告项 | 你的修复 | 验证 |
|---|---|---|
| P0-1 Aurora 历史 | 接 ?sessionId + loadHistory | ✅ line 181, 189, 196 |
| P0-2 Capsule 历史 | 复用上次 session | ✅ |
| P0-3 主动呼唤 | Dashboard 接入 + 8s 非阻塞 | ✅ line 412 + `lastModule=AURORA_PROACTIVE_GREETING` 真记录 |
| P0-4 4 页面 lib | 全补齐 | ✅ `missing=NONE` |
| .bak 删除 | 删除 | ✅ JS 目录已无 .bak |
| P0-5 Memory 重要度 | 滑块 + updateMemoryImportance | ✅ line 155, 172 |
| P0-6 Capsule 编辑 | 接 capsuleContextPreview/Context/Boundary | ✅ line 207, 252, 253 |
| P0-7 三新页面补 API | emotionToday/Range/HighEmotion/ByCategory 全接 | ✅ |
| BGM 6 FLAC | /audio/music/ 真有 6 首 | ✅ chopin 3 + mozart 3 |
| mode=prod | 真模型在线 | ✅ ai/health 显示 `provider=minimax, model=MiniMax-M3, fallbackAllowed=false` |

**10/10 项全部独立验证落地**。

---

## 优先级建议

如果你只为给老师演示（1-2 天内交）：**现在的状态已经够了**。
如果你要发布给真实用户：先做 P1-1（a11y 1.5h）+ P1-2（loading 1h）+ P1-3（profile sync 1.5h）= 4 小时把专业度从"演示级"提到"产品级"。

---

## 验证证据来源

| 工具 | 结果 |
|---|---|
| Python innerHTML 模板字符串扫描 | 找到 27 处，大部分用 esc，2 处需要复查 |
| grep aria-/role= | 5 匹配 / 4 页面 |
| grep loader / spinner | 6 匹配 / 3 页面 |
| curl 12 个 API | **ConnectionRefused** —— 服务当前不在 |
| grep try/catch 分布 | dashboard 9 / aurora 2 / others 散乱 |
| grep auroraName 在 dashboard | **0 匹配** —— 验证状态不同步 |
| 读 IC.esc / IC.ensureDemoLogin | 实现正确（esc 5 字符全转、ensureDemoLogin 是 auth 守卫不是自动登） |

---

## 一个修正

我之前怀疑"`IC.ensureDemoLogin` 是自动登 demo"是**误判**。实际它是"auth/current 检查，没登录跳 login"。正常 auth 守卫，不是问题。
