# CP-57 九十日轨迹冻结集与长程检索评测（检查点 41，后台 agent 交付）

## 交付物

### 1. 冻结评测集 `src/test/resources/evaluation/ninety-day-trajectory-bank-v1/`

- **210 条轨迹**（注册表冻结口径 ≥200），3 族各 70：repeated_correction
  （CONTRADICT/SUPERSEDE 2-3 代纠正链）、multi_person_relations（多人区分+遗忘）、
  life_migration（TODO→ARCHIVE→HABIT）；共 731 探针（451 FACT_RECALL / 140
  CORRECTION_PREFERENCE / 140 WITHDRAWN_ZERO），每条跨度 92-94 模拟日
- `generate.py` **确定性生成**（无随机数无时钟，两次重跑+三次 --check SHA 一致：
  `692277f7…`）；生成器内置检索准入门的 Python 镜像自检（expected 词法分 ≥0.35、
  干扰记忆 <0.18）——fixture 不要求产品做不到的事
- `manifest.json` 冻结（scenario_count/scenario_sha256/families/change_log），结构
  与 memory-retrieval-bank-v1 一致

### 2. 契约 + 评测执行器（2/2 绿，真实执行）

- `NinetyDayTrajectoryBankContractTest`：SHA-256 冻结断言 + 结构/纠正链合法性
- `NinetyDayTrajectoryEvaluationTest`（@SpringBootTest H2，941 次真实检索调用）：

| 指标 | 系统 | 简单基线（recency top-3） |
|---|---|---|
| 事实准确率 | **591/591 = 1.000**，CI95 [0.9938,1.0] | 38/591 = 0.064 |
| 纠正优先率（阈值 100%） | **140/140** | 38/174 |
| 撤回零返回/复活（阈值 0） | **140/140 空、0 复活** | 0/140 空 |
| 跨用户污染（蜜罐双向） | **0** | — |

早/晚探针（≤30 vs >30 日）均 1.0。基线保留与差距 (+93.6pp) 是蓝图 L850"保留
旧模型/简单检索基线"的落实。运行产物 `target/evaluation/cp57-ninety-day-trajectory-report.json`。

### 3. 两态纪律

仅产出 **EVALUATION_EXECUTED**（合成冻结集回归），不构成 CAPABILITY_ACCEPTED；
实际 90 日成熟用户队列证据与真人随访是 operator 门禁（蓝图 L850"不用时间戳模拟
代替真人随访"）。产品检索实现在本冻结集上无缺陷暴露，评测作为长期回归保护留存。

## 回归

后端全量 **1649/1649 绿**（本包 +2），2 个 Docker 门控跳过；Web 750/750（未触及）。
