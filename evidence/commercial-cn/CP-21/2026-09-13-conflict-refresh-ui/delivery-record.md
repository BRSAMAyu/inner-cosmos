# CP-21 前端冲突刷新 UI（乐观并发 409 呈现）— 增量

- 日期：2026-09-13；实施：后台实现 agent（web 组件/hook 域），主线程整合
- 愿景：V06（纠正与演化不互相覆盖——他人先更新时如实告知并引导刷新）

## 交付内容

### 1. 统一冲突识别通道

`api.ts` 新增 `VERSION_CONFLICT_CODE`/`isVersionConflictError()`——只按 `code === "CONFLICT"` 判定（该通道已被 LetterController expectedVersion、CapsuleController If-Match 边界使用），不解析中文消息，BAD_REQUEST 永不误报。

### 2. 两个编辑面的冲突呈现

- `PortraitClaimsPanel.tsx`：per-claim 冲突提示条「他人在你之前更新了这条内容」+「查看最新」（重拉全视图）+「知道了」；**suppress/delete 仅在动作真正落库后才清本地草稿/确认框**（冲突时输入的文字保留，用户可对着最新状态重做——不提供静默丢弃）；
- `BeliefGallery.tsx` + `useBeliefGallery.ts`：可选 conflict/onRefreshConflict/onDismissConflict props，仅 409 置位（普通错误仍走 setStatus），刷新重拉 list+contradictions；
- `AuroraApp.tsx` 最小接线（14 行）：handler 去 `void` 前缀返回 Promise、portrait claim 动作 setStatus 后 rethrow——不接线则冲突永远到不了组件（父层吞错），已避开并行改动段落并声明。

### 3. 前向兼容的如实说明

Belief/Portrait 编辑端点现状尚无 expectedVersion 乐观锁（claim 动作冲突现以 400+状态守卫呈现）——本批交付统一 409 码通道 + 前端呈现，后端端点接入 expectedVersion 后即完整生效（登记后续）。

## 测试证据

- 前端新增冲突用例（AuroraOpeningContinuity 9、PortraitClaimsPanel 12、BeliefGallery 10、useBeliefGallery 11 含冲突场景：409→提示出现、点刷新→数据更新、非 409 不误报）；
- 全量 web **783/783** 绿、tsc clean；主线程全量后端 1837/1837。

## 诚实边界

- 后端 Belief/Portrait 端点的 expectedVersion 乐观锁未加（服务层不在本批文件域）——见上「前向兼容」说明；
- 冲突提示为组件级（不全局广播）——同一用户多标签页的冲突在切回该视图时可见。
