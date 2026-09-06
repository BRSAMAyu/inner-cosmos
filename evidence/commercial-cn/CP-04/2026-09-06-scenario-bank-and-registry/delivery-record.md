# CP-04 对标、AI评测与实验治理 — 交付记录（场景银行与注册表）

- 日期：2026-09-06；阶段 S1；依赖 CP-02（判据冻结部分）
- 愿景：V03、V10、V11、V16
- current_sha：随本检查点提交

## 交付物

1. **冻结场景银行** `src/test/resources/evaluation/cn-commercial-bank-v1/`
   - `scenarios.jsonl`：248 项
     - 核心 204（否定30 / 反讽25 / 关系变化30 / 含蓄拒绝25 / 长期纠错35 / 停用回归20 / 方言夹杂20 / 长尾19）——满足"至少200个中文长期/纠错场景"并覆盖蓝图点名的否定、反讽、关系变化、含蓄拒绝、注入、停用、方言夹杂、长尾
     - 红队 32（危机12 / 隐私注入12 / 同意边界8）
     - J01–J12 旅程任务rubric 12 项（现场分母由 CP-12/52/61 记录）
   - `manifest.json`：内容 SHA-256 冻结 + 家族/切分计数 + 切分策略 + 禁入数据声明 + 单一合成用户隔离
   - 生成器 `ai-lab/evals/datasets/generate_cn_bank_v1.py`（seed=20260906，确定性；生成器与种子同为冻结物）
2. **CI 合同测试** `CnCommercialBankContractTest`：哈希冻结校验、≥200 核心、家族齐全、ID/标题唯一、红队与旅程必须 held_out、来源不得声称真实用户数据。本测试已抓出并修复两轮标题碰撞缺陷（真实证据，见 git 历史）。
3. **实验注册表（先验冻结）** `docs/commercialization/evaluation/experiment-registry.yml`
   - CP-19/22/23/25/28/30/32/57/58/59/60A 共 11 项能力逐项预注册：主终点、最小有意义改善/非劣界、失败分母、区间方法、安全硬阈值、样本量与效能计算
   - EVALUATION_EXECUTED 与 CAPABILITY_ACCEPTED 两态强制分离；对标基线台账（BASE-SINGLE-KERNEL 等 5 项）
   - 模型/提示/路由/成本/失败入账规则（运行目录 evidence/commercial-cn/CP-04/runs/）

## 验收对照

| 蓝图要求 | 证据 |
|---|---|
| ≥200 中文长期/纠错场景 | 204 核心（manifest.counts_check.core_minimum_200=true） |
| 隐私与危机红队集 | 32 项红队（12危机+12注入越权+8同意边界） |
| 分训练/开发/最终保留集 | train/development/held_out 确定性切分；红队与旅程全部 held_out；合同测试锁定 |
| 按用户隔离 | 全部合成单用户轨迹；prohibited_data 显式列出 P0 等 |
| J01–J12 任务 | 12 项 rubric（阈值引蓝图 §3.2） |
| 否定/反讽/关系变化/含蓄拒绝/注入/停用/方言夹杂/长尾覆盖 | 8 核心家族一一对应（注入另在红队 12 项） |
| 先验主终点和效能计算可查 | 注册表 11 项含样本量/效能推导 |
| EVALUATION_EXECUTED ≠ CAPABILITY_ACCEPTED | 注册表 effect_states + 各能力 status 字段 |
| 不删样本或调 rubric 追绿 | 注册表纪律节（冻结后不得修改） |

- test_command：`./mvnw test -Dtest=CnCommercialBankContractTest`（通过）
- rollback：删除银行目录/生成器/测试/注册表；无运行时影响（资源仅供评测）
- 边界：真实 Provider 运行、盲评双评审执行、单核基线实测 → 依赖大陆 Provider 接入（CP-17）与真实运行环境；对标同设备实测按注册表窗口执行

## 状态

- status: IMPLEMENTED（资产冻结与治理就绪；运行执行 NOT_STARTED）
- next_action: CP-17 网关接入后跑 BASE-SINGLE-KERNEL 基线与首轮红队回放
