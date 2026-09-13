# CP-62 停服运行手册 + 只读模式开关（检查点 44，主线程交付）

## 交付物

### 1. `ReadOnlyModeFilter`（`inner-cosmos.readonly-mode.enabled`，默认 false 不注册）

蓝图 L894"先保只读/导出/退款与安全义务，暂停新收费"的技术能力：
- **义务路径放行**：全部 GET/HEAD/OPTIONS、登录（用户须能登录才能导出）、安全资源、
  数据权利路径（撤回/删除是义务不是新内容）、导出/权益查看/取消、**支付渠道回调**
  （停服期间渠道仍在动钱，事实必须继续入账本）、通知
- **新收费与新内容拒绝**：其余写请求 503 `READONLY_MODE`，文案点名"本地数据、导出
  与数据权利不受影响"；开关由 operator 在停服演练/真实事件时打开（CP-52 暂停注册
  开关家族，绝不静默默认）
- 契约 `ReadOnlyModeContractTest`（真实 MockMvc）：登录/安全资源/广场浏览照常；
  下单 503（文案含"导出"）；数据权利写**永不**被只读拦截；**渠道回调穿过过滤器**
  落到商户门自己的 403（证明过滤器没吃掉它）

### 2. `sunset-playbook.yml` 七情景 × 完整决策链

90 日低现金/供应商退出/创始人不可用/云冻结/证书过期/并购/停服，每条
trigger→首 24 小时→只读步骤→退款批次→联系渠道→法定留存→最终销毁→演练状态
（全 null，operator 事实）；停服路径显式保留 导出/数据权利/退款 且退出不绑购买。
契约 `SunsetPlaybookContractTest` 1/1（七情景齐全、无完成态、义务保留断言）。

### 3. K3 支付指标断链修复（审计发现，顺手关闭）

收尾审计发现 `PAYMENT_CAPTURED/REFUND_SETTLED` 在 MetricCode 枚举中**零发射点**
（CP-03 台账声称的"CP-45 接入支付事件"是断链）。已在 `ChannelCallbackIngestService`
接受路径接线：每笔已接受支付/退款事实同发 K3 指标事件（orderId/channel/amountCents/
currency props，CP-03 字典口径），贡献利润报表与支付账本永不各说各话。

### 4. CP-58 并发测试稳定性修复（全量负载下的真事件）

`FacetRevocationConcurrencyTest` 在 8 线程+全量套件负载下出现过一次"partial
visibility"假阳性：读侧两次独立连接的非可重复读（grant 读后提交、capsule 读前快照）。
**两次运行中承重不变量全部成立**（successAfterAnyRevoke=0、postRefusals=20）；代码
核verify撤回是单 @Transactional（grant+下架+genome+向量+回执原子提交）。修复：
违规观测加一次**有界复读**——真窗口在复读中存活，过期快照不存活；检测力保留，
假阳性消除。连跑两次稳定绿。

## 回归

后端全量 **1686/1686 绿**（1672→1686，+14），2 个 Docker 门控跳过；Web 750/750，
tsc 干净。
