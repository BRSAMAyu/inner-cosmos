# CP-31 user-mirror 出处信号 — 第三增量（登记项闭环）

- 日期：2026-09-15；实施：后台 agent（user-mirror 域）
- 愿景：V09/V15（AI 参与生成的内容在预览链也可识别）

## 交付

`CapsulePreviewVO` 增 `aiGenerated`（controller 零改动经 VO 原样透出——正是登记项拒绝的「controller 层启发式谎标」的反面）：
- **LLM 分支 → true**：有记忆时 personaPromptDraft 来自 `capsuleAgent.generateUserPersona`（真实 provider RPC，失败即抛无模板替身）——赋值紧贴成功返回点，到达即证明；dev mock provider 同为 true（走的就是模型调用路径，与 provider 是否 mock 无关）；
- **空记忆模板分支 → false**：buildPersonaPrompt 本地字符串拼接零模型调用；
- **preview-from-memory → false**：纯规则链路（DataMaskingServiceImpl 两个返回点显式置位）；同链路全部生产者逐一核查（PersonaChatServiceImpl:550 的 buildPersonaPrompt 属回合 prompt 非 preview，不在链路）。

## 测试

UserMirrorPreviewProvenanceTest 3/3（LLM 分支 true/模板分支 false 锚定模板特征/preview-from-memory 两路 false/既有字段存活）；CapsuleP1P2PrivacyBoundary 6/6 + CapsuleAiGeneratedLabeling 6/6 + DataMasking 集成 2/2 + ApplicationFlow 8/8 回归绿。

## 诚实边界

行级 provenance schema 列（每条 persona 内容的生成路径持久化）仍登记（schema 决策）。
