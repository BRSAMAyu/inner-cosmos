# CP-14/15 首个工程增量 — 统一敏感数据边界守卫与撤回防复活

- 日期：2026-09-06；CP-14（全域授权与敏感数据边界）+ CP-15（撤回、删除与灾备防复活）首个增量
- 依赖：CP-07 同意中心（已落地）、CP-13 账户状态（已落地）
- 愿景：V05、V07、V10（+V20 防复活）

## CP-14 交付

1. **读路径审计**（`docs/commercialization/security/cp14-read-path-audit.md`）：P0–P3 逐层读取面 × owner/purpose/consent 强制现状（引用既有测试与守卫），缺口显式登记（VOICE 执行点、CAPSULE_RUNTIME purpose、缓存键版本化）
2. **统一守卫** `SensitiveDataBoundaryService.assertReadable(subjectType, subjectId, requester, purpose)`：
   - 请求者状态 fail-closed（FROZEN / MINOR_RESTRICTED → FORBIDDEN）
   - tombstone 优先 → NOT_FOUND（不泄露存在性）
   - OWNER_READ 严格属主 → UNAUTHORIZED；管理身份不放大消费路径权限
3. **接线**：`CapsuleServiceImpl.getOwnedCapsule`（守卫全量）；`MemoryServiceImpl.listCards`（tombstone 过滤）

## CP-15 交付

1. **V40 迁移 + H2 孪生**：`tb_retraction_tombstone`（subject 唯一、行 id 即单调权利水位；独立于业务表的生命周期是 DR 契约）
2. `RetractionTombstoneService`：record（幂等）/ isBlocked（读时判定）/ blockedIds（列表过滤）/ watermark / **catchUpFromWatermark**（从独立台账证明的水位重放，把备份带回的业务行重新置为 FORGOTTEN 语义）
3. **主体死亡路径挂钩**（修正后语义）：tombstone 只在**主体本身**被撤回时写入——`MemoryLifecycleServiceImpl` 的 owner-forget 分支（同事务）；receipt 保持纯审计，因为派生物清理（匹配向量退役/共鸣体降级 NEEDS_REVIEW）不终止主体的属主可读性。此语义由既有闭环旅程测试回归验证（记忆纠正后共鸣体属主视图仍 200/NEEDS_REVIEW）
4. 防复活语义：备份复活行（业务状态被还原为 ACTIVE）在守卫处仍 NOT_FOUND；晚到消费/生成同样被拒

## 测试证据（SensitiveBoundaryAndAntiResurrectionTest 5/5）

- 越权矩阵：属主通过 / 他账户 UNAUTHORIZED / 匿名 UNAUTHORIZED / 冻结与未成年拦截 FORBIDDEN（本人数据也拒）
- 备份复活：撤回→守卫 NOT_FOUND→业务行被还原 ACTIVE→仍 NOT_FOUND；共鸣体同样
- 水位追平：撤回 A→记水位→撤回 B→两行还原→catchUp(水位) 重放 B（FORGOTTEN）且 A 靠 tombstone 读时拦截
- 列表过滤：撤回行不出现在记忆列表，健康行保留

## 诚实边界

- tombstone 表当前与业务同库；生产 DR 要求它随独立权利台账保留并可独立恢复（CP-50B 演练验收，蓝图原文"仅恢复旧备份里的旧 tombstone 表不算通过"）——已在台账登记
- data.retracted.v1 outbox 幂等消费者的完整资产清单（缓存/对象/导出/排队推送/Provider 副本）为 CP-15 下一增量；供应商副本清除能力须入合同

## 状态

- CP-14: IN_PROGRESS（守卫+矩阵+接线）；CP-15: IN_PROGRESS（tombstone+水位+防复活链）
- next_action: by-id 全量接线与 CAPSULE_RUNTIME purpose；资产清单与 outbox 消费者
