# EXPERIENCE §1.1.5 · 加载态铺开到主异步动作按钮（checkpoint 7）

承接 checkpoint 6（加载四态原语 + AccountSettings + 首屏），把已验证的 `AsyncButton` 铺开到
各空间最高频、此前**仅禁用无忙碌反馈**的主异步动作按钮——尤其 checkpoint 4 死按钮扫描点名的
"共鸣/连接"空间（其"点了没反应"根因正是缺少异步等待反馈）。

## 转换清单（`<button disabled={busy}>` → `<AsyncButton busy busyText>`）

- **ResonanceNetwork.tsx**（共鸣空间，三个核心动作）：
  - 进入有限但自然的对话 → busyText「正在进入」
  - 发送这一轮（persona 对话）→ busyText「正在发送」（保留 `!personaDraft.trim() || quota.exhausted` 禁用）
  - 让慢信启程 → busyText「正在寄出」（保留标题/正文非空禁用）
- **UnderstandingCorrection.tsx**（内宇宙空间）：
  - 预览会改变什么 → busyText「正在分析」（保留 `!newValue.trim()` 禁用）
  - 确认，这是更准确的我 → busyText「正在保存」
- **CapsuleWorkbench.tsx**（共鸣体工作台）：先看严格脱敏预览「正在脱敏」/ 编译为私密版本「正在编译」/
  用当前选择生成新版本「正在生成新版本」/ 看看它会怎么说「正在生成」/ 确认并发布当前版本「正在发布」/
  暂停公开「正在暂停」/ 撤回这个共鸣体「正在撤回」。
- **PeopleDiscovery.tsx**（连接空间）：想认识 ta → busyText「正在邀请」。
- **PlazaDirectory.tsx**（回声广场）：开始对话 → busyText「正在打开」。

`AsyncButton` 的 `disabled` 与 `busy` 分离传入（内部 `disabled={disabled || busy}`），完整保留了每个
按钮原有的附加禁用条件；默认 `type="button"`，不改变可访问名（idle/非 busy 时仍是原标签）。

## 未转换（有意保留，理由）

- MemoryStarfield「正在追溯…/正在撤回…」、PsychologySkillStudio（`skillBusy?text.busy:text.start`）、
  UnderstandingCorrection 退休「退休中…」：**已有内联忙碌文案**，反馈已在，仅缺三档时序，价值低，留待统一。
- PortraitView 校准按钮：点击即乐观关闭校准面板（`setCalibratingDim(null)`），忙碌态无从显示。
- 策略切换/匹配卡片/沙盒评分等 toggle/导航类按钮：非请求-响应型异步动作，加忙碌文案语义不当。

## 验证

- `npm test`：**101/101 通过**（各被改组件的既有单元测试——ResonanceNetwork/CapsuleWorkbench/
  UnderstandingCorrection/PeopleDiscovery/PlazaDirectory——全部保持绿，证明可访问名与禁用行为无回归）。
- `npm run build`：tsc + vite OK，`assets/app.js` 332.75kB。
- 可视化：`AsyncButton` 的三档时序/暖色/微动画已在 checkpoint 6 通过内联生产 CSS 的样张 + getComputedStyle
  完成浏览器验证（见同目录 checkpoint.md）；本 checkpoint 复用同一原语，属低风险机械铺开，按 17号协议 §7
  风险分层以"回归测试全绿 + checkpoint 6 视觉证据"为验证依据，未对每颗按钮重复截图（截图工具在无限 CSS
  动画页仍超时的环境 flakiness 依旧）。

## 下一步

- 剩余低价值一致性项：把已有内联忙碌文案的按钮（MemoryStarfield/PsychologySkillStudio/退休）统一到
  AsyncButton 的三档时序；AuroraSelfSpace、ClaimCandidateReview 的动作按钮。
- 然后进入 next_machine_actions 的空/错/恢复三态系统化补齐，以及 var() token 化 + 亮色/莫兰迪 + 本地字体。
