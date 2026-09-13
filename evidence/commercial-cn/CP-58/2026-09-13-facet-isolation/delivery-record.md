# CP-58 多侧面隔离：冻结集 + 隔离评测 + 撤回并发（检查点 42，后台 agent 交付）

## 交付物

### 1. 冻结评测集 `src/test/resources/evaluation/facet-isolation-bank-v1/`

- **10 模拟用户 × 3 侧面 × 15 探针 = 450 探针**（experiment-registry L92-100 冻结口径）
- 三侧面（职场/家庭/兴趣）各持互斥词项；探针以**开放式推断问题**钓另一侧面独有
  事实，问题本身不含被钓词项——命中必为泄露而非回声
- `generate.py` 确定性生成（无随机无时钟，两次 --check SHA 一致：
  `2eb7cd025cccd3da…`）；生成器内置可判定性自检：词项在己方语料可学习、他侧零
  出现、不在探针中、互不为子串、不触发产品脱敏（DataMaskingServiceImpl 镜像）

### 2. 评测与并发（3/3 绿，真实执行）

- **`FacetIsolationBankContractTest`**：SHA 冻结 + 每场景恰 3 侧面/15 探针/互斥不交叉
- **`FacetIsolationEvaluationTest`**（真实产品路径）：`createSimulatorCapsule`
  （simulatorOnly 强制 PRIVATE）+ 每侧面独立 DataUseGrant（purposes 恰为
  {CAPSULE_SIMULATOR, PROVIDER_EGRESS}，无 CAPSULE_RUNTIME）+ 探针经
  CapsuleRuntimeContextComposer/CapsuleSandboxService 产出 + **编译产物全量泄露扫描**
  （personaPrompt/contextPreview/styleProfile/genome）：
  ```
  users=10 capsules=30 probes=450 inferenceHits=0 rate=0.0
  CI95=[0.0, 0.0082]  deterministic: term/evidence/compilePath leaks 全 0
  publicRefusals=60/60  visitorRefusals=30/30  plazaListings=0
  ```
  **确定性泄露 = 0（硬阈值通过）**；侧面外推断率 0.0 ≤ 0.50；CI 上界 0.0082 < 0.55
  （注册表口径全过）
- **`FacetRevocationConcurrencyTest`**（8 线程：1 撤回 + 7 探针）：race 中撤回前 43 次
  合法命中；撤回后 20 次探针全拒、3 轮扫查无复活（grants REVOKED/下架/genome 非
  ACTIVE/embedding 物理清零）、回执 derivativeType 均注册且 action=ERASED、无部分
  可见中间态、**其他侧面 grants 不受波及（爆炸半径隔离）**

### 3. 产品结论

隔离边界（编译路径、运行时选择路径、模拟器公开禁令、撤回传播）在全部冻结探针下
保持完好，未暴露产品缺陷。仅产出 **EVALUATION_EXECUTED**
（`target/evaluation/cp58-facet-isolation-report.json`）；来源用户评价优于基线、独立
用途合同、B2B 经营范围为 operator 门禁。

## 回归

后端全量 **1654/1654 绿**（本包 +3），2 个 Docker 门控跳过；Web 未触及（750/750）。
