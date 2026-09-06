# CP-01 权威迁移与真实能力台账 — 交付记录

- 日期：2026-09-06
- 执行者：Coding Agent（集成负责人角色）
- 阶段：S0
- 依赖：无
- 愿景：V01、V16、V19

## 基线冻结

- 本地 HEAD：`add4b241338c47dd1ccbafa2880f7b484adb07b0`（= origin/main，无分叉）
- 冻结时脏改（全部为文档，无产品代码改动）：
  - `M docs/AI_ALIGNMENT_TECHNICAL_REFERENCE.md`
  - `M docs/README.md`
  - `M docs/presentation/inner-cosmos-cloud-native-defense-v4-continuity-aligned组员H1改进版.html`
  - `M 对齐文档/README.md`
  - `?? docs/commercialization/`（商业化文档集，本轮交付物）

## 交付物

1. **商业台账**：`docs/commercialization/ledger/commercial-cn-ledger.yml`
   - 64 个主工作包（CP-01–CP-64，含 owner/stage/depends_on/vision_ids/status/evidence_dir）
   - 13 个阶段子门（CP-12A/B、50A/B、51A/B、54A/B、60A/B/C、63A/B，含依赖与关闭范围）
   - 74 行旧账迁移映射（69 验收项 + 5 人工门）
   - 4 项未决问题（OQ-01–04）
2. **旧账映射**：每行含 `old_id / old_gate / disposition / targets / note`
   - disposition 分布：REVERIFY 46、KEEP 12、OUT_OF_SCOPE 9、REPLACED 7
3. **旧入口指针**：`docs/goal/complete-product-acceptance.yml` 与
   `docs/goal/closure-campaign-state.yml` 顶部追加只读注释，指向新台账；旧 PASS 状态一律不改写。
4. **台账使用说明与 ADR 索引**：`docs/commercialization/ledger/README.md`

## 验收自查（可复现命令）

| 验收要求 | 结果 | 复现方式 |
|---|---|---|
| 每旧项都有去向 | 74/74 有 disposition 与 targets | `python -c "import yaml; d=yaml.safe_load(open('docs/commercialization/ledger/commercial-cn-ledger.yml',encoding='utf-8')); assert len(d['migration']['items'])==74"` |
| 每 CP 有 owner／依赖／验收 | owner 64/64、执行 64/64、验收 64/64、恢复 64/64（蓝图内）+ 台账 depends_on 64/64 | `grep -c '^#### CP-' 蓝图` = 64；`grep -c '负责人：' = 64` |
| 旧新状态不互相覆盖 | 旧账本仅加注释，零内容改写；台账全部 NOT_STARTED 起步 | `git diff docs/goal/complete-product-acceptance.yml` 仅含顶部注释块 |
| 无未解释孤项 | 迁移表键集 = 旧账 69 项 ∪ 5 人工门（脚本断言通过） | gen_ledger.py 内 `assert set(M) == ...` |
| 新发现登记为子任务 | OQ-01–04（细分、主云、主体许可、支付渠道） | 台账 `open_questions:` 节 |

## 领域覆盖抽查

蓝图 9 节 A–P 十六个分组对应产品体验／AI／合规／商店／国内云／支付／运营／增长／融资准备全部在台账中（见台账 `work_packages`）。

## 状态

- status: IMPLEMENTED（自查通过；独立复核待 CP-64/CP-61 评审人抽查）
- next_action: CP-02 访谈工具包与判据冻结（外部访谈本身 BLOCKED_EXTERNAL）

## 回滚

仅删除本目录与台账新文件、还原旧账本顶部注释即可；不涉及任何历史证据删除。
