# CP-23 第二增量 — 画像条目搁置/恢复/删除 + J04 纠正采纳旅程；CP-19 冻结集 SINGLE vs DUAL 对拍

- 日期：2026-09-12；CP-23（可纠正画像与信念变化）+ CP-19（双核增益对拍）；愿景 V05/V06/V11 + V03/V04/V16

## CP-23 交付：属主对画像条目的完整控制

1. `PortraitClaimControlService`（suppress/restore/delete）：
   - **搁置**：ACTIVE 理解 → SUPPRESSED（版本+1）——从可纠正画像视图与 Aurora 每轮上下文（两者都只读 ACTIVE）同时消失，行保留供审计
   - **恢复**：仅 SUPPRESSED 可恢复（版本+1）
   - **删除**：软删 DELETED（版本+1），不可重复删除
   - 属主作用域：外来 id 一律"找不到"，绝不触碰他人行；每次转换写 PORTRAIT_CLAIM 审计行（target_id/oldValue/newValue/reason）——刻意不经 corrections confirm 流程（那会 fabricate 新画像内容）
2. API：`POST /api/portrait/claims/{id}/suppress`、`POST .../restore`、`DELETE /api/portrait/claims/{id}`（body 可带 reason）
3. **测试（PortraitClaimControlTest 3/3）**：搁置后视图与 ACTIVE 池双清空+审计行精确（含跨属主负测、重复搁置负测、恢复往返）；删除后全表面消失+不可二次删+审计留存；**J04 旅程**——今日经真实 corrections 流程纠正自我理解，"明日"画像视图只剩 USER_CORRECTION 权威的新值，旧推断被取代出视野，ACTIVE 池唯一

## CP-19 交付：冻结集 SINGLE vs DUAL 对拍 harness

`CnCommercialBankDualVsSingleAblationTest`（仅 development 分裂，遵守 split_policy；held_out 保持封存）：
1. 每个冻结场景同输入跑两条路径：单遍（恰一次 AURORA_CHAT）与双核（PLAN+SPEAKER 起步，critic 至多一次），断言模块序列契约与双路径结构有效回复
2. 路由分布如实记录：development 分裂刻意以简单轮次为主（风险/红队家族在其它分裂），复杂度轴 81/81 全 SINGLE——**这是对的内容预算**；组合 adaptive 决策（预算腿）分离出 1 个 DUAL 场景，断言非退化
3. 报告落盘 `target/cn-commercial-eval/dual-vs-single-ablation-report.json`（逐场景模块序列/两路径延迟/kernelRoute/adaptiveDual/observableIssues）

## 诚实边界

- 脚本化确定性客户端无法伪造内容质量增益——SINGLE vs DUAL 的**内容**差分需要真实 provider，是明确记录的后续项；本轮交付的是可复现的对拍 harness、模块契约与路由事实
- development 分裂简单轮次主导使 DUAL 样本稀少（1/81）——需要专门的预算压力 fixture（长输入/多部分/风险密集）才能有效测量差分，记入 next_action
- 全量回归 **1578/1578** 通过（新增 4 测试），无 schema 变更

## 状态

- CP-23: IN_PROGRESS（四态视图+属主控制+J04 采纳完成；前端搁置/删除 UI 与信念变化时间线后续）
- CP-19: IN_PROGRESS（对拍 harness 完成；真实 provider 增益评分与预算压力 fixture 后续）
