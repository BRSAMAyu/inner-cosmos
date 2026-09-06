# CP-02 首发细分与问题验证 — 交付记录（访谈工具包）

- 日期：2026-09-06；阶段 S0；依赖 CP-01（已提交 2f0b61e3）
- 愿景：V01、V18

## 本候选版本交付

1. 访谈协议与 H1–H3 先验判据冻结：`docs/commercialization/research/cp02-problem-interview-protocol.md`
   - 三细分（SEG-A 毕业入职 / SEG-B 职业转换 / SEG-C 异地生活变化）各 10 人、≥5 非熟人
   - 行为回溯脚本（11 问，无假设性意愿题、无概念测试）
   - 招募/退出/酬谢/同意规则；去标识编码模板含反例强制登记
   - 细分选择规则与两个默认核心任务（蓝图 §2.1 的先验口径）
2. 判据在访谈前冻结，符合 CP-04 效果接受纪律与蓝图 §8 执行顺序（先写对照判据）。

## 边界与状态

- 访谈执行本身：BLOCKED_EXTERNAL（需招募渠道、酬谢预算、所有者授权；对公众招募前须过 CP-51A 法定程序判断——问题访谈属于研究招募，不提供服务，但仍须同意程序完备）。
- status: IN_PROGRESS（工具包 IMPLEMENTED，30 次访谈 NOT_RUN）
- test_command: 不适用（研究文档）；判据冻结可复现：`git log -- docs/commercialization/research/cp02-problem-interview-protocol.md`
- rollback: 删除本目录与协议文件即可，无代码影响。

## next_action

所有者确认招募渠道与预算后执行 30 次访谈 → 编码汇总 → 细分选择决策记录 → 关闭 OQ-01。
