# CP-21 belief recalculate 乐观锁 — 第五增量（CP-21 登记项全部闭环）

- 日期：2026-09-15；实施：后台 agent（belief 域 + V54）

## 交付

- V54：`tb_belief_pattern` 加 `version INT NOT NULL DEFAULT 1`（PG 回填 + schema.sql H2 孪生幂等 ALTER；基线 53→54、v20 链 34→35）；
- `recalculateStrength` 接 `expectedVersion`（可选 query param，对齐 portrait 契约）：null=legacy 兼容；不符/竞态 → ErrorCode.CONFLICT（预检先拦 + 原子条件 UPDATE `WHERE id+user_id+version` 双保险，无 pin 的竞争写入同样不静默覆盖）；成功 → 重算 + version+1 一次 UPDATE 原子完成；extract 新行 born version=1，confirm 追加语义不搅动 version（测试固化）；
- `BeliefEditEndpointLockStatusTest` 从负测层（last-call-wins 文档化）改造为真锁 4 用例。

## 测试

BeliefEditEndpointLockStatusTest 4/4 + belief 域回归 26/26；PG 基线类 5/5（本机 Docker 缺失全部 @Testcontainers 跳过——V54 SQL 与 V48 同形单语句，真库执行属部署门禁）。

## 诚实边界

agent 全量跑被并行批次 mid-edit churn 打断（StructuredAiService/ChannelCallbackController 等非其域文件的编译中间态）——其域内已全绿，全量集成回归由主线程在所有 agent 落地后统一执行；web 端 expectedVersion 接线未做（legacy null 通道保证现状，登记前端批）。
