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

## 第三增量（同日）：重复事件去重与版本归并（检查点 20）

1. `MemoryRecurrenceMatcher`（`service/memory`，纯函数）：字符二元组余弦判定"同一事件的重述"；阈值 0.60 校准于两个观测簇——不同事件 <0.3、重述事件（含改写）≥0.6，两侧留边距（错误合并会改写记忆身份，从严）
2. `MemoryServiceImpl.extractFromSession` 结算管线接线（仅当本会话尚无卡片时）：
   - **重述折叠**：命中既有 CURRENT 记忆 → 不新建并行 ACTIVE 卡；recurrenceCount/triggerCount +1、versionNo +1、按新频次重算情感重力、lastTouchedAt 更新——同一事件始终是一个带版本的对象
   - **审计链**：写 REINFORCE 操作行，reasonCode=DUPLICATE_EVENT_DEDUP、evidenceRefs=AURORA_SESSION:重述会话、actorType=SYSTEM——版本链可解释"为什么这次版本+1"
   - **递归边界**：只写会话键控的情绪轨迹（幂等）；不重复堆叠片段/待办资产（它们属于原始抽取）
   - **候选门**：仅 CURRENT 且非 tombstone 的记忆可吸收重述——FORGOTTEN 是终态（强化=复活）；备份复活为 ACTIVE 的已撤回行同样不可吸收（tombstone 读取时拦截）
3. **测试（MemoryRecurrenceDedupTest 5/5）**：重述折叠为单卡（计数/版本/审计操作断言）；不同事件保持分离（计数=1）；FORGOTTEN+备份复活行不吸收重述（重述成为真正的新卡，被撤回行计数不变）；同会话重复结算仍恰好一张卡（M-008 幂等保留）；匹配器阈值确定性与空输入安全
4. 全量回归 **1574/1574** 通过（新增 5 测试），无 schema 变更

## 状态

- CP-21: IN_PROGRESS（回放+冲突硬边界+重述去重完成；前端冲突刷新 UI 与真实 LLM 摘要下的阈值校准后续）
