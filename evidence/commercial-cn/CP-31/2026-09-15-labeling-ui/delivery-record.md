# CP-31 aiGenerated 前端消费 — 第四增量（登记项闭环）

- 日期：2026-09-15；实施：后台 agent（web 域）

## 交付

①访客聊天：CAPSULE 回复携带与 Aurora 同视觉的「AI 生成」角标（同 class），仅 aiGenerated===true 显示（缺字段旧行不标）；②CapsuleWorkbench：出处行「AI 参与生成：personaPrompt · 系统编译（非大模型）：contextPreviewJson、styleProfileJson」，SEED 如实显示「无」，载荷未带层级字段不渲染。plaza 列表/matches 的 aiGenerated:false 无自然展示位未做 UI（如实）。

## 测试

ResonanceNetwork 23/23（+4）、CapsuleWorkbench 18/18（+2）；全量 web 869/869。
