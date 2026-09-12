# CP-21 第二增量 — 合并/冲突/并发编辑硬边界

- 日期：2026-09-12；CP-21（记忆生命周期与来源图）；愿景 V05/V06/V07
- 前置增量：来源图回放（见 CP-20/2026-09-06-crisis-continuity/delivery-record.md）

## 问题（三条静默损坏路径）

审计 `MemoryLifecycleServiceImpl` 发现三条并发/状态路径都会静默损坏记忆权威库：

1. **并发编辑丢更新**：UPDATE 等操作没有版本校验——两个编辑都基于 v1，先后落地都写 v2，后写者静默覆盖前写者的内容，版本链看不出发生过冲突
2. **终态可复活**：FORGOTTEN 记忆仍可被 UPDATE/MERGE/SUPERSEDE——对已忘记的行写回标题/内容（读取层 tombstone 仍拦截，但权威库内容被改写、操作台账留下复活痕迹）
3. **链分叉**：已 SUPERSEDED 的来源可再次 MERGE/UPDATE——同一记忆出现两个"当前版本"继任者，来源图回放歧义
4. **回退覆盖新变更**：rollback 无 intervening-edit 检查——回退旧操作会把 before 快照直接盖在之后的编辑上

## 交付

1. `MemoryOperationCommand` 新增 `expectedVersion`（乐观并发锚点；null=旧行为，兼容结算任务等无冲突面调用方；保留 9 参兼容构造器）
2. `assertNoConflict` 冲突门（execute 内、写库前）：
   - FORGOTTEN 来源 → CONFLICT"已被忘记，不能再被修改或合并"（终态不可复活）
   - UPDATE/MERGE/SPLIT/REINFORCE/CONTRADICT/SUPERSEDE 作用于 SUPERSEDED 来源 → CONFLICT"请对合并后的新版本操作"（链分叉防护）
   - expectedVersion 与当前行不符 → CONFLICT"已有新的变更（当前版本 X，你基于 Y），请刷新后再试"（绝不静默 last-writer-wins）
3. `rollback` 前置版本核对：受影响行当前版本 ≠ 操作完成时版本 → CONFLICT"直接回退会覆盖"；MERGE/SPLIT 来源版本 +1 但不入 after 快照的语义已按操作类型区分，合法回退不受影响

## 测试（MemoryLifecycleConflictTest 5/5）

1. 过期 expectedVersion → CONFLICT 且中间编辑完好（版本/内容断言）；基于当前版本重放成功（冲突解决=刷新，不是死路）
2. 无版本锚点的旧式编辑照常工作（向后兼容）
3. FORGOTTEN 终态：UPDATE/MERGE/SUPERSEDE 全拒，行保持脱敏形态
4. 已合并来源不可二次合并/编辑（链完整：单一继任者、来源 SUPERSEDED 指向它）
5. 行已前行的回退被拒且新内容幸存；无中间编辑的回退照常成功

全量回归 **1569/1569** 通过（新增 5 测试），1 个既有 Docker 门控跳过；无 schema 变更（PG 基线不变）。

## 诚实边界

- 乐观并发以单行版本比对实现，未引入分布式锁；跨行多键并发（同时 MERGE 两对不相交记忆）语义仍为各自独立成功——正确，因为操作面不相交
- 前端"刷新后重试"的冲突 UI（展示冲突差异）属 CP-11 记忆工作台后续
- 增量抽取与重复事件去重语义（next_action 另一项）未在本轮展开

## 状态

- CP-21: IN_PROGRESS（回放+冲突硬边界完成；增量抽取/去重语义与前端冲突 UI 后续）
