# CP-32 匹配解释字段前端消费 — 第三增量（登记项闭环）

- 日期：2026-09-15；实施：后台 agent（web 域）

## 交付

匹配卡片：模式 chip（Similar/Complementary/Unexpected，zh 用后端 label 同文）+ reasons 前 3 条（后端事实句原样克制小字）；insufficient_signal 显示「信号不足，暂不解释」且无任何模式 chip；模式文本并入 aria-label。差异说明：解释字段嵌套在 item.modeExplanation（非顶层），按代码实际消费。

## 测试

ResonanceNetwork 23/23（+2 解释用例）；全量 web 869/869。
