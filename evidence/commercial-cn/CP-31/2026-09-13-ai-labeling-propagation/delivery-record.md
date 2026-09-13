# CP-31 分享/导出链的 AI 生成标识传播 — 首个工程增量

- 日期：2026-09-13；实施：后台实现 agent（controller/vo/export 域），主线程整合回归
- 愿景：V09/V15（透明度——AI 参与生成的内容在每个外流面可识别）

## 交付内容

### 1. 外流面矩阵（agent 逐端点盘点，grep 证实此前仅 Aurora 链路有标识）

| 外流面 | 处置 |
|---|---|
| `GET /api/capsule/{id}`、`/my`、create/visibility/context 响应 | 整胶囊载荷增量标注（统一 `CapsuleAiLabeling.labeled()` 出口） |
| `GET /{id}/context-preview` | payload 级标注；`authorizedMemories` 出处混合（AI 抽取/owner 纠正/导入）不单一标注，note 写明 |
| `GET /api/plaza/capsules`、`/matches` | 公开投影显式 `aiGenerated=false` + 空 `aiGeneratedFields`（「已查、无 AI 字段」而非缺省沉默） |
| `POST /api/persona-chat/message` + `GET /session/{id}/messages` | 新 `PersonaChatMessageVO`：`senderType=CAPSULE → aiGenerated=true`（与 AuroraReplyVO 同名同义），VISITOR→false |
| 导出包 echoCapsules 记录 | 五项标注键在 section digest 计算前注入（完整性覆盖标注）；importer 按键读取，往返回归绿 |
| 复制/分享 | grep 证实无 copy/duplicate/share 端点（清单所设场景不存在）；share-card.schema.md 补 `aiGeneratedFields` 契约字段与「不得整体谎标」条款 |
| CapsuleSandboxVO / user-mirror/preview / CapsuleSyncController | 域外或无法诚实区分出处——矩阵如实列出待后续，拒绝启发式谎标 |

### 2. 标识粒度（按实际写路径核实，纠正了清单的错误假设）

清单称 contextPreviewJson/styleProfileJson「由 capsuleAgent 编译」——**grep 证实不实**：实际由规则编译器生成（无 LLM）。故落三级标注（比清单要求更严格）：
- `aiGeneratedFields`：仅 personaPrompt（USER_CAPSULE 唯一写入路径是 LLM；SEED 平台手写模板零 AI 声明；遗留空行不标 true）；
- `systemCompiledFields`：contextPreview/styleProfile（规则编译）；
- `ownerWrittenFields`：pseudonym/intro/publicTags/ownerContextNote——测试负向断言绝不入 AI 列表。

已披露偏差：非 prod demo 种子（seed-enabled）手写 USER_CAPSULE 会按规则标 AI；行级 provenance 未持久化，修正需 schema 列（登记后续）。

### 3. 缓存排查（如实）

`src/main` 全仓无 @Cacheable/Caffeine/CacheManager/spring.cache，相关服务无 ConcurrentHashMap 式派生视图缓存——**无缓存即无失效面，不伪造失效测试**。持久化派生物只读确认既有覆盖：归档 retire 匹配向量+收据、用途撤回/记忆生命周期/用户纠正均 retire、recompile 旧行 content_hash 不匹配不再计分。

## 测试证据

- 新增 `CapsuleAiGeneratedLabelingTest` 6/6（公开列表/详情/语境预览/访客 chat 回复+历史/导出包；负向：owner 手写字段与 SEED 模板不被谎标）；
- agent 8 批次回归 104 项 0 失败；
- 主线程全量整合回归：**backend 1822/1822（2 Docker-gated skips）+ web 767/767 + tsc clean**；
- 整合时修复两处 agent 交付的边缘问题：①share-card.schema.md 被重写为 CRLF 致契约测试行锚定失配（归一化回 LF）；②发现并拆除 `RelationQualityMetricTest` 的**时区定时炸弹**（测试用 UTC 时钟推周而 store 用 Asia/Shanghai 锚定——每周日 16:00–24:00 UTC 必炸，与本周日上海午夜后首炸吻合；测试周推导改为与 store 同区，HEAD 上可复现、修复后绿）。

## 诚实边界

- CapsuleSandboxVO、user-mirror/preview（LLM 分支与模板分支无法在 controller 层诚实区分）、CapsuleSyncController 待后续批次（需 service 层出处信号）；
- 前端未消费新标注字段（后端纯增量，不破坏现有前端类型；前端展示登记后续）；
- demo 种子标注偏差与行级 provenance 见上。
