# CP-51 监管材料与提审证据结构化落盘（检查点 39，多 agent 协作）

## 交付物

### 1. CP-51A 法定程序台账 `docs/commercialization/regulatory/legal-procedures.ledger.yml`

蓝图 §5.1 法律与申报矩阵 **12 项逐行落盘**（电信主体/APP备案/生成式AI评估/已备案模型
应用登记/拟人化备案/社交评估/个保/等保审计/AI标识/未成年人反诈网暴/自动续费/非医疗
边界）。每条：applicable_judgment（忠实蓝图适用判断）、required_state（放行条件）、
depends_on（工作包映射）、allowed_scope（**程序未达前允许开放的功能范围**——51A
单独记录允许的用户规模/功能/地域/收费与否）、receipt/signature（operator-only，
初始一律 null）。

### 2. CP-51B 提审台账 `submissions.ledger.yml`

六渠道提审事实登记（与 store-submissions 六 checklist 渠道 slug 一致）：
review_outcome ∈ {PENDING, SUBMITTED, REJECTED, PASSED}；**PASSED 必须带官方
receipt（提交截图不算完成）**；REJECTED 必须带理由且 blocks_launch 保持 true
（未通过渠道保持关闭）；与 51A 互不晋升。

### 3. 独立审查（后台 agent，逐行核对）

- 12/12 事项映射、12/12 depends_on 与蓝图工作包列**完全一致**、六渠道三方一致
- 抓出并已修复 8 处：注释计数 11→12；等保行 required_state 丢失"**专业意见和
  可核验证据**"（唯一实质语义弱化）；两处开放列举"等"字丢失；telecom 占位
  allowed_scope 落实为具体范围；三处强化性增补补注来源

### 4. 契约 `RegulatoryLedgerContractTest` 2/2

无 APPROVED 态（法定程序终态只能由 operator 以外部回执落位）；receipt/signature
必须保持空白直到 operator 回填；PASSED 需 receipt、REJECTED 需理由且保持阻断；
渠道集合精确等于六渠道。

## 回归

后端 **1638/1638 绿**（本包 +2），2 个 Docker 门控跳过；Web 750/750，tsc 干净。

## 诚实边界（operator 门禁）

真实申报、回执、签字、逐渠道提交与审核结果——全部外部事实；本台账是证据落盘
位置与结构约束，不是"已合规"的证明。
