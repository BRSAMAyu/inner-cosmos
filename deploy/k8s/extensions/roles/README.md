# CP-38 工作负载角色（可编码部分）

同一不可变镜像，角色由 `INNER_COSMOS_RUNTIME_ROLE` 选择——与蓝图"模块化单体的 api/worker/scheduler/migration 角色化"一致。应用侧机制已存在：`RuntimeSchedulingConfiguration` 只在 all/worker/scheduler 启用调度；`SchedulerLeaseConfiguration` 提供 Redis 租约。每个角色目录是 base 的 kustomize 派生（nameSuffix 隔离，独立 Deployment/HPA/PDB 生命周期）。

| 角色 | 值 | 副本 | 关键差异 |
|---|---|---|---|
| api | api | 3 | 面向用户 HTTP/SSE；不跑后台任务；grace 45s（SSE 排空） |
| ai | worker | 2 | Provider 调用池；2Gi 内存；grace 90s（在途轮次收尾） |
| worker | worker | 2 | 结算/事件/outbox；幂等 handler+租约下多副本安全 |
| scheduler | scheduler | 1 | 时间驱动任务；单副本默认；租约是正确性机制 |

探针沿用 base 的三段式（startup/readiness 查 readiness 组含依赖；liveness 只查进程内组——外部抖动摘流量不重启）。优雅终止 = preStop sleep（base）+ 角色 grace + 租约过期。

**验收映射（蓝图 §CP-38）**：多副本争抢/租约过期/重复事件/节点排空/预算封顶需真实集群演练（operator 门禁，CP-50A）；DB 连接池总和 ≤ 容量需要真实 PG 参数核验。本目录是那些演练执行的清单。
