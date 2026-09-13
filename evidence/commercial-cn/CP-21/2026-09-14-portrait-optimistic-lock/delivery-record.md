# CP-21 Portrait 编辑端点乐观锁（后端半边）+ 全链接通 — 第三增量

- 日期：2026-09-14；实施：后台 agent（portrait 服务/controller + api.ts），主线程（AuroraApp 接线 + 面板行传递）
- 愿景：V06（演化不互相覆盖——409 冲突条现在有真实的后端来源）

## 交付（agent Y + 主线程）

1. **乐观锁**：`suppress/restore/delete` 三端点接受 `expectedVersion`（query param）；行版本不符 → `ErrorCode.CONFLICT`（409，正是前端 `isVersionConflictError` 唯一识别的通道）；落库改为**原子条件 UPDATE**（id+user_id+version 三守卫，rowsAffected≠1 即 409）——旧代码两次并发转换会互相覆盖只留一条 audit，现在不可能。null pin（legacy 调用）兼容不破坏。版本 token 用 `tb_understanding_claim.version` **既有列**（transition 本来就 +1），未动 schema。
2. **端点清单裁决**：portrait 三动作=编辑现有行（锁）；belief extract=创建追加（不锁）；**belief recalculate 想锁但 `tb_belief_pattern` 无版本列且本批 schema 冻结——不造假锁（不用 updated_at 凑 token），停在负测层**（`BeliefEditEndpointLockStatusTest` 文档化现状：last-call-wins）。
3. **前端全链接通（主线程）**：PortraitClaimsPanel 动作 props 从 claimId 改传整行；AuroraApp 三调用点解析行版本（字符串→整数，不可解析回落 legacy）传 `expectedVersion`；api.ts 通道（agent Y 已备）即时点亮——CP-21 冲突条从「前向兼容」变为「端到端生效」。

## 测试

PortraitClaimOptimisticLockTest 4/4（旧 pin→409 不动数据/新 pin→成功+1/legacy 兼容/并发语义）+ BeliefEditEndpointLockStatusTest 2/2 + 前端 portrait-claim-expected-version 2/2 + 面板 14/14（断言改行形状）；belief/portrait 域回归 110/110；全量 **backend 1865/1865 + web 830/830 + tsc clean**。

## 诚实边界

- belief recalculate 乐观锁待 schema 决策（加版本列）后接入；
- legacy null-pin 路径仍是 last-call-wins（向后兼容的显式选择，javadoc 写明）。
