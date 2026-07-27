# Aurora 单会话长上下文合同

状态：已实现代码合同；真实 Provider 质量仍需按
`docs/superpowers/specs/2026-07-27-aurora-living-intelligence-recovery-design.md`
的 Phase 0、影子评测和停止门验证。

## 作用域

- 保留一个当前会话从开场到当前轮的完整消息历史。
- 历史以稳定的 `system -> conversationHistory -> per-turn state -> current user message`
  顺序发送，便于 Provider 前缀缓存。
- 长窗口只是对话保真层，不代替 `DeliberationPlan`、任务状态、打断增量或当前轮规划。
- 不跨会话自动拼接原始对话；长期信息仍走授权、相关性和隐私门。

## 预算

产品输入硬上限默认是 200,000 estimated tokens。实际输入上限为：

```text
min(
  AURORA_CONTEXT_HARD_MAX_INPUT_TOKENS,
  selected_provider_context_window
    - AURORA_CONTEXT_OUTPUT_RESERVE_TOKENS
    - AURORA_CONTEXT_SAFETY_MARGIN_TOKENS
)
```

默认 Provider 窗口：

| Provider | 默认窗口 |
|---|---:|
| DeepSeek V4 | 1,000,000 |
| GLM 4.7 | 200,000 |
| MiMo 2.5 | 1,000,000 |
| MiniMax 文本系列 | 204,800 |
| Gemini 3.5/3.6 | 1,048,576 |
| 未知 Provider | 128,000 |

这些值都可通过 `*_CONTEXT_WINDOW_TOKENS` 环境变量覆盖。模型或账号实际能力变化时，
必须先更新对应 Provider 配置；不能因为产品上限是 200K 就假设 Provider 一定支持。

## 超限行为

低于安全上限时，所有历史消息按原始顺序完整保留，不再有固定 6/10 条限制，也不做
140/180 字截断。

超过上限时执行确定性边界：

1. 保留会话开场；
2. 保留含明确更正、约定、截止时间或 `《作品名》` 的抽取式关键锚点；
3. 保留最近对话；
4. 插入 `【会话上下文裁剪边界】`，明确记录省略条数；
5. `conversationContextBudget` 记录 Provider、模型、预算、估算量与是否裁剪，不记录原文。

这不是语义摘要，也不会伪称完整历史。需要跨越 200K 后继续高保真时，应使用经评测的
会话摘要/状态机制；不得静默删除消息或用更长 Prompt 代替可执行计划。

## 证据边界

当前 token 计算是保守估算，不是各 Provider 的官方 tokenizer。运行时应以后续 Provider
返回的 `usage` 为校准依据。Mock 测试只能证明顺序、预算和裁剪协议，不能证明真实模型
在 200K 附近仍保持理解质量。
