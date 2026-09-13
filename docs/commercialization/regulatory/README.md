# CP-51 监管材料与逐商店提审台账

本目录是 CP-51 的结构化落盘，契约测试
`src/test/java/com/innercosmos/regulatory/RegulatoryLedgerContractTest.java` 锁定结构诚实性。

## 文件

| 文件 | 内容 | 关键契约 |
|------|------|----------|
| `legal-procedures.ledger.yml` | CP-51A：§5.1 全部 12 项法定程序（电信主体、备案、生成式AI评估、应用登记、拟人化备案、社交评估、个保、等保审计、AI标识、未成年人/反诈/网暴、自动续费、非医疗边界） | status ∈ {PENDING, IN_PROGRESS}，**无 APPROVED 态**；receipt/signature 只能由 operator 以外部凭证回填；每行有 allowed_scope（程序未达前允许开放的功能范围） |
| `submissions.ledger.yml` | CP-51B：六渠道提审事实登记 | review_outcome：PASSED 必须带官方 receipt（提交截图不算）；REJECTED 必须带 rejection_reason；blocks_launch 直到 PASSED |

## 与相邻工作包的边界

- **CP-43/44 商店材料 checklist**（`docs/commercialization/store-submissions/`）：管
  "材料是否备齐"；本目录管"程序/提交的事实状态"。互不晋升。
- **CP-52 总演练**：Go/No-Go 逐渠道读本表的 blocks_launch 与 review_outcome。
- **CP-48 值班台账**：minor-protection 程序的 required_state 引用 CP-48 演练证据。

## 谁能填什么

- **coding agent**：结构、适用判断草稿、依赖映射、契约测试；status 最多 PENDING →
  IN_PROGRESS。
- **operator/法务**：真实申报后的回执、签字、提交时间、审核结果。没有外部凭证，
  任何行都不会出现"通过"——这既是纪律也是契约测试断言的事实。
