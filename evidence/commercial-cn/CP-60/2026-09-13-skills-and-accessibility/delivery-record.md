# CP-60A/C 技能资产扩容 + 可达性矩阵（检查点 43，后台 agent 交付 + 主线程接线）

## CP-60A：技能 3 → 6（注册表冻结口径"至少 6 项"达成）

### 1. 三项新技能资产（蓝图 L873 点名，结构逐字段照既有范本）

| 技能 | 主题 | evidence（DOI 经 Crossref 逐一验证） |
|---|---|---|
| relationship-perspective | 从三个位置看这段关系 | Galinsky & Moskowitz 2000 (10.1037/0022-3514.78.4.708)；Pronin et al. 2004 (10.1037/0033-295X.111.3.781) |
| support-preference-mapper | 记下什么支持真的有用 | Collins & Feeney 2000 (10.1037/0022-3514.78.6.1053)；Rosenberg 2003（专著） |
| cognition-pattern-reflector | 认出一个反复出现的念头 | Beck 1976（专著）；Hofmann et al. 2012 (10.1007/s10608-012-9476-1) |

全部 L1 / SUGGEST_ONLY / EXPLICIT_CONSENT / 仅 current-run-input / 零工具 / 完整
retentionChoices–fallback–escalation；limitations 双语注明"证据与文案待双专家复核"。

### 2. 停用门（蓝图 L875 恢复条款）

`SkillDisarmamentTest`（真实 Spring 上下文）：禁用技能后新运行被拒且不留新 run 行；
记忆卡列表/星空渲染/检索包前后逐字节相等；历史 run 完整保留审计；兄弟技能不受
影响（单独撤回）。

### 3. 专家签字台账（"双专家通过"的结构面）

`skill-expert-review.ledger.yml`：6 技能 × 2 专家 = 12 行，verdict 全 PENDING、
receipt 全 null——签字是 operator 门禁；契约 `SkillExpertReviewContractTest` 2/2。

### 4. 主线程接线（agent 移交事项）

- 注册表计数断言 3→6（Controller/Release 两个测试）；Release 测试改为按 id 过滤
  断言（位置断言在 6 项下漂移）
- `PsychologySkillServiceImpl.evaluate()` 补三个新技能分支（双语 summary/alternative/
  smallAction，输入键与技能 requiredInputs 一致）——消除 null 占位渲染

## CP-60C：可达性矩阵结构

`cp60c-accessibility-matrix.yml`：J01–J12 × 五端（web/android/ios/windows/harmonyos）
× 四维度（读屏/键盘/对比度/弱网）= 60 单元格全 NOT_STARTED、evidence 全 null、无
完成态汇总字段；契约 `AccessibilityMatrixContractTest` 3/3（AUTOMATED_PASS 必须带
证据，当前无一单元格可宣称）。

## 回归

后端全量 **1672/1672 绿**（agent 10 用例 + 主线程接线回归），web 750/750。

## 诚实边界（operator 门禁）

双专家签字（CP-60A 关门条件）；HarmonyOS 原生工程/真机/渠道（CP-60B 全为 operator，
不从 APK 推断）；真人读屏/弱网真机/独立研究（CP-60C 关门条件）。
