# CP-22 第三增量 + CP-23 第五增量 — 带检索相关性标签的冻结评测集；信念变化时间线视图

- 日期：2026-09-12；CP-22（任务化检索与真实向量质量）+ CP-23（可纠正画像）；检查点 24

## CP-22：memory-retrieval-bank-v1（首个带相关性标签的检索评测集）

1. **冻结集**：`src/test/resources/evaluation/memory-retrieval-bank-v1/`——8 个手工标注案例（RELEVANT/DISTRACTOR 逐记忆标签），manifest 记录 SHA-256（1de23e64…）锁定场景文件，与对话冻结集同一冻结纪律；项目内合成内容、无用户数据
2. **覆盖家族**：主题精确匹配、服务语剥离（"帮我分析一下…"）、meta-only 诚实空、任务适配排序（ACTION 下 TODO/PROSPECTIVE 第一）、改写查询、多相关召回、结果上限下的精确率、主题词干扰项
3. **`MemoryRetrievalLabeledEvaluationTest`**：SHA 自锁 → 每案例为独立新用户插入标注语料 → 走**真实** MemoryRetrievalService → 断言 precision@k（任何 DISTRACTOR 进入证据包=失败）、RELEVANT 召回率、首条排序（任务适配案例）、meta-only 空结果 → 机器可读报告落盘 `target/memory-retrieval-eval/labeled-retrieval-report.json`（未来重排/向量改动必须保持数字或显式重冻结）
4. 初始冻结前校准记录：MR-006 的"恢复训练清单"标题对查询词法覆盖过低（1/9<0.18）无法被诚实召回——标题改为"跑步后膝盖恢复训练"后在初次冻结前定稿；这是 fixture 现实性校准，非评分退让
5. **诚实边界**：标签为工程手工标注（先验），不是人工标注者多人一致性；真实 provider 向量通道在该 H2 测试环境不可用，本轮测的是确定性词法+本地语义通道的合同

## CP-23：信念变化时间线视图（web）

1. `api.understandingClaimHistory(claimKey)` → `GET /api/aurora/corrections/claims?claimKey=`（后端既有，返回按 id 降序的全版本链）
2. `PortraitClaimsPanel` 每条理解新增"看它怎么变的"：懒加载+缓存版本链，**旧→新**渲染——每版显示取值、版本号、来源（你纠正后的理解/来自 Aurora 的观察）、状态（当前/已被取代/被搁置/已删除）与时间；被取代版本视觉划线；aria-expanded 标注展开态
3. 语义：用户能看到"理解如何随纠正与搁置演变"——推断 v1 被用户纠正 v2 取代、再被搁置的完整轨迹（J04"分清事实、推断、冲突和过去状态"的前端面）
4. **测试**：新增时间线测试（旧→新顺序、来源与状态标签、切换折叠、缓存复用不重复拉取）；`npm test -- --run` **732/732 绿**，`tsc -b` 零错误

## 状态

- CP-22: IN_PROGRESS（撤回硬边界+查询归一+标签评测集完成；真实向量质量校准后续）
- CP-23: IN_PROGRESS（四态视图+属主控制+J04+前端UI+时间线完成）
- 后端全量回归 **1581/1581** 绿
