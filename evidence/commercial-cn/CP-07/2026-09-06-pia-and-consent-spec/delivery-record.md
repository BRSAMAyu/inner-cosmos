# CP-07 隐私影响评估与同意产品合同 — 交付记录（PIA+规格）

- 日期：2026-09-06；阶段 S1；依赖 CP-05（草稿就绪）
- 愿景：V05、V07、V13

## 交付

- `docs/commercialization/privacy/cp07-pia-and-consent-contract-spec.md`：
  - 按隐私分层（P0–P3+账户/支付/遥测/安全）的数据清单与 PIPL 敏感分类
  - 8 个用途代码的同意矩阵（机制/撤回效果/版本化），显式禁止"训练/改进"用途
  - 6 项差距（G1 Provider 出站同意为最高优先）与下轮编码规格（tb_consent_record/同意中心 API/出站核验/重同意触发/J01 渐进同意）
  - PIA 风险登记与缓解映射

## 边界

- 法务复核与版本生效：外部门；G1/G2 编码为下一增量；语音单独同意与 CP-27 联动
- test_command: 不适用（文档）；回滚=删除文件

## 状态

- status: IN_PROGRESS；next_action: 按规格实现同意合同代码
