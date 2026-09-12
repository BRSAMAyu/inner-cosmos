# CP-43/44 提审清单骨架 + CP-47 支付对账契约（检查点 31）

## CP-43/44（商店材料清单骨架）

1. `docs/commercialization/store-submissions/`：6 渠道结构化 checklist（web-pwa/apple-cn/xiaomi/oppo/huawei/vivo-others，共 30 项），逐项含 category/requirement/evidence_required/blocks_submission/status/evidence/approved_by
2. **结构合同 `StoreSubmissionChecklistContractTest` 1/1**（随测试套件强制）：必填字段齐全、id 唯一、status ∈ {PENDING, IN_PROGRESS, WAIVED(须 approved_by)}、**不存在 PASS 状态**——蓝图"不得凭经验填 PASS"结构性落地，审批只能以 evidence 官方回执由 operator 填入
3. 诚实边界：真实组织账户/资质/回执为 operator 人工门禁，本骨架是证据落盘位置而非审批替代

## CP-47（支付对账契约，无需真实渠道）

1. **`PaymentCallbackVerifier`**：HMAC-SHA256（时间戳进入签名载荷——截获签名不能重放到不同 body）、±5min 新鲜窗防重放、常数时间比较、**fail-closed**（密钥未配置=全部拒绝）；主构造器 @Autowired，包内测试构造器可注入时钟
2. **`tb_payment_event` 追加式账本**（V43 迁移 + H2 孪生）：provider_event_id 唯一幂等（重复回调 ack 既有行绝不重复入账，并发插入竞态由 DuplicateKey 兜底）；**净值对账与到达顺序无关**（先退款后支付 / 先支付后退款同净）；金额漂移 → 整单 DISPUTED（"对账异常暂停扩张"可见化），绝不静默吸收
3. **`PaymentReconciliationContractTest` 4/4**：验签正向+密钥空白拒+篡改 body/密钥拒+过期时间戳拒（时间戳在签名内不可重写）+畸形输入不开门；重复回调单次入账；乱序两方向同净；不匹配对账 DISPUTED
4. Postgres 基线 **实机验证**：43 迁移 / 101 表 / 94 身份列 / v20 链 24（基线计数同步更新）；全量回归 **1598/1598 绿**（新增 5 测试），2 个 Docker 门控跳过
5. 诚实边界：真实渠道验签格式（微信/支付宝各自的 header/证书体系）在 CP-45/46 渠道对接时适配——本轮交付的是可验证的验签/幂等/对账内核与契约

## 状态
- CP-43: IN_PROGRESS（Web/PWA checklist 骨架；真机矩阵与签名更新验证后续）
- CP-44: IN_PROGRESS（六渠道清单骨架；身份冻结/渠道合同/HarmonyOS PoC 为 operator 门禁）
- CP-47: IN_PROGRESS（验签/幂等/乱序/对账内核；真实渠道适配与结算周期抽样后续）
