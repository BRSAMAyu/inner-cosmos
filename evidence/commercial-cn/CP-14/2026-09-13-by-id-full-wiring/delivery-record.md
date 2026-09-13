# CP-14 敏感数据边界 by-id 全量接线 — 第二增量

- 日期：2026-09-13；实施：后台实现 agent（controller/ 与 service/privacy/ 文件域），主线程整合回归
- 前置：2026-09-06 首增量（统一守卫 + tombstone + CapsuleServiceImpl/MemoryServiceImpl 接线）
- 愿景：V05（数据边界）、V07、V10、V20（防复活贯穿）

## 交付内容

### 1. 接线矩阵（controller/ 全量扫描，按 id 读 P1/P2 资产的端点逐一处置）

本次新接线 **17 个端点**（此前仅有零散 owner 相等判断或完全无门禁）：

| 端点组 | 资产 | Purpose |
|---|---|---|
| `GET /api/memory/starfield/{id}/detail`、`POST /api/memory/cards/{id}/importance`、`POST /api/memory/cards/{id}/archive` | P1 MemoryCard | OWNER_READ |
| `POST /api/thought-shredder/{id}/settle`、`DELETE /api/thought-shredder/{id}` | P1 MemoryCard(SHREDDER) | OWNER_READ |
| `POST /api/todos/{id}/status`、`PUT/DELETE /api/todos/{id}`、`POST /api/todos/{id}/split` | P1 TodoItem（新 SUBJECT_TODO） | OWNER_READ |
| `GET /api/capsule/{id}/genome-history`、`GET /api/capsule/{id}/data-use-grants`、`POST .../{grantId}/revoke` | P2 EchoCapsule | OWNER_READ |
| `POST/GET /api/capsule/{id}/sandbox/{respond,feedback}`、`GET .../fidelity` | P2 EchoCapsule | OWNER_READ |
| `GET /api/persona-chat/capsule/{id}/active-session` | P2（访客读） | **CAPSULE_RUNTIME（新增 purpose）** |
| `GET /api/persona-chat/quota?capsuleId` | P2（访客读） | **CAPSULE_RUNTIME** |

**接线前发现的真实漏洞（两处）**：
- `GET /api/persona-chat/quota` 完全无门禁——任意 capsule id 探测可泄露他人 capsule 的 `conversationLimitPerDay` 配置；
- `GET /api/persona-chat/capsule/{id}/active-session` 无 tombstone、无账户 fail-closed（备胎复活洞）。

### 2. 守卫本体（service/privacy/）

- `Purpose` 枚举新增 `CAPSULE_RUNTIME`（访客运行时读 P2）；
- `SUBJECT_TODO = "TODO"` 常量（P1 待办）；
- `SensitiveDataBoundaryServiceImpl`：构造器新增 `TodoItemMapper`；CAPSULE 分支按 purpose 分流——owner 恒过；CAPSULE_RUNTIME 非访客须 `isPublic=true && visibilityStatus=PUBLIC`，否则 FORBIDDEN；tombstone 永远最先判（NOT_FOUND 无存在性暗示）；
- 画像缓存键契约 `portrait:<userId>:<grantVersion>` 写入接口 Javadoc。**如实说明：仓库当前不存在画像缓存实现**（service/portrait 为逐请求 DB 读），故仅落结构契约注释，未做实际接线。

### 3. 不接线项（附理由，矩阵留档）

- CapsuleController 其余 by-id 端点：全部经 `CapsuleServiceImpl.getOwnedCapsule`（守卫已在服务层生效）；
- DialogController by-id：P0 原始对话，超出 P1/P2 范围，均有 owner 校验；
- LetterController：P3 慢信，sender/recipient 双向校验在；
- 派生画像类（Belief/ClaimCandidate/UserCorrection/Portrait）：user_id 作用域查询，无 by-id 越权面；
- AdminController：守卫接口注释明确 admin 路由刻意分离；
- `EchoResonanceController /{capsuleId}/landed`：非读路径，双门在；其无 tombstone 检查登记为 CP-31 后续项。

## 测试证据

- 新增 `SensitiveBoundaryByIdWiringTest` 8/8：P1 记忆卡/碎纸机/待办跨用户负测（B→A 资产 401）、P2 owner 端点 7 项负测 + owner 正控、CAPSULE_RUNTIME 三态（公开 200/私有 403/不存在 404）、owner 私有 capsule quota 200、tombstone 后访客运行时读 404、tombstone 后 owner 星图详情 404；
- 受影响面回归 28/28（SensitiveBoundaryAndAntiResurrectionTest、PersonaChatQuotaControllerTest、MemoryLifecycleControllerTest、PersonaChatServiceImplReportBlockTest）；
- 主线程整合全量回归：**1711 tests, 0 failures, 0 errors, 2 Docker-gated skips，BUILD SUCCESS**（基线 1686 + 本批 8 + CP-17 16 + CP-24 1）；
- 前端 750/750，tsc 干净（本批无前端改动，防回归确认）。

## 诚实边界

- 画像缓存授权版本键：契约注释落地，实现待画像缓存真实引入时接线（当前无缓存即无失效问题）；
- `EchoResonanceController` tombstone 缺口移交 CP-31；
- P0/P3 by-id 端点不在本批范围（矩阵中已逐项附既有防御位置）。
