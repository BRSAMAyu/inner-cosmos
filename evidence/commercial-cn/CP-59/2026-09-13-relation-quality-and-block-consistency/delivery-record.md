# CP-59 关系质量指标 + 拉黑全触达一致性 + 社区健康回顾（检查点 43，主线程交付）

## 交付物

### 1. `RelationQualityReport`（扩展 CommercialMetricQueryService/Impl）

- 口径（顺序无关可人工核对）：分母=锚周活跃慢信线程（CONNECTED_REAL_SEND 按线程
  聚合）；**回轮深度 = min(A 侧发送数, B 侧发送数)**——单侧轰炸（5 连发）永远不
  构成回轮（不以热度催回复的结构化落地）；达标 = ≥3 轮
- **Wilson 95% CI**（线程为单元）；**骚扰举报率并列**（与 G-SAFE 同源：live 事件 +
  匿名化 rollup，两份报告对"什么算事件"永不打架）
- 契约 `RelationQualityMetricTest`：合成样本精确可核对——4 线程（3:3 达标/3:2/1:1/
  0:5 单侧）→ **active=4, bidirectional=3, qualified=1**，CI 包住点估计，事件经
  真实 MetricEventService 落库（HUMAN 账户守卫、事件键毫秒去重语义均真实走过）

### 2. 拉黑全触达一致性（含两处真实产品缺口修复）

契约 `BlockConsistencyContractTest`：A 拉黑 B 后逐路径断言——好友申请 FORBIDDEN
（双向、措辞不泄露"是拉黑"）、群邀请 FORBIDDEN、发现列表双向不可见、慢信过滤/
共鸣体对话共用同一 BlockRelation 真值、解除后恢复。

**评审发现并修复两处真实缺口**（此前不存在）：
1. `discoverPeople` **不排除拉黑对象**——发现列表会把拉黑双方互相推给对方
2. `listGroupMessages` **不过滤拉黑发送者**——共享群里仍能看到已拉黑者消息
   两处已按全触达一致修复（`blockedCounterpartIds` 双向过滤），契约测试锁定。

### 3. 社区健康回顾台账 + 契约

`docs/commercialization/operations/community-health-review.yml`：回轮深度 share 与
举报率**成对快照** + ModerationCase SLA + 申诉复核；数值全部 null 待真实运行回填；
status ∈ {PENDING, IN_PROGRESS}——**无 agent 可写的"健康"终态**。契约 1/1。

## 回归

后端全量 **1672/1672 绿**（本包 +3 与两处产品修复的回归），web 750/750（未触及）。

## 诚实边界（operator 门禁）

90 日成熟关系样本、双方分别确认价值与自主性、主持人培训与容量——CP-54B/运营
真人门禁；指标数值回填待真实流量。
