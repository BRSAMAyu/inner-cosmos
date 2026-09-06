# CP-09/10 web 增量 — 同意中心页、AI 角标与 J01 渐进同意

- 日期：2026-09-06；阶段 S1；CP-09（设计系统与信息架构）+ CP-10（首次价值、设置与权限体验）首个工程增量；依赖 CP-07 同意中心 API（72193f15）
- 愿景：V01、V02、V07

## 交付

1. **API 层**（`web/src/api.ts`）
   - `ApiCodeError`（保留后端业务错误码）+ `isConsentRequiredError`；request() 在 envelope 带 code 时抛出
   - `ConsentView` 类型 + `api.consents()` / `api.decideConsent(purpose, grant)`
2. **同意中心页**（`ConsentCenterPanel.tsx`，双语）
   - 按注册表分组顺序呈现（必需/按需征求/可选/敏感/托管）；逐项同意/撤回（忙时锁定兄弟行）；必需项说明唯一退出（注销）；托管项指引回对应功能；版本页脚
   - 挂载于"我—数据"页签，紧邻数据权利回执面板
3. **AI 生成角标**（`AuroraConversation.tsx`）
   - 每个 Aurora 回复气泡在说话人标签旁固定"AI 生成/AI-generated"角标；用户气泡永不带角标（标识办法显式提示层，前端落地）
4. **J01 渐进同意**（`ConsentRequestDialog.tsx` + `useAuroraSession` + `AuroraApp`）
   - 发送被后端以 CONSENT_REQUIRED 拒绝时：草稿保留、状态栏显示后端解释、弹出逐项同意对话框（用途说明/撤回后果/"未同意仍可用什么"）
   - "同意并继续"→ 记录 AI_PROVIDER_EGRESS 授权 → 刷新同意视图 → 提示重发；"暂不"→ 关闭，本地功能不受影响；无静默降级
5. 样式（styles.css：consent-center/consent-request/ai-generated-badge，沿用现有暗色衬线体系）

## 测试

- 新增 `ConsentCenterPanel.test.tsx`（6）、`ConsentRequestDialog.test.tsx`（5）、AuroraConversation 角标用例（1）：分组顺序/只读约束/双向决策/忙锁/双语/角标位置
- **web 全量 708/708 绿（95 文件）**；`tsc -b` 干净；`npm run build` 生产 bundle 已写入 Spring 静态资源
- 后端回归：见检查点提交说明（本增量无 Java 源码改动）

## 边界

- J01 完整验收（15 名非作者用户 13/15 无讲解、首价值≤10 分钟、权限理解≥90%）属 CP-12A 现场研究；VOICE_PROCESSING 对话框接线随 CP-27；onboarding 引导整合随 CP-10 后续
- rollback：还原 web 源 6 文件 + 重构建即可；无 schema/后端变更

## 状态

- CP-09/CP-10: IN_PROGRESS（本增量 IMPLEMENTED）
- next_action: CP-10 首次价值旅程的 onboarding 整合与 J01 权限理解题；CP-11 全状态打磨
