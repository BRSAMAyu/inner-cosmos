# Provider credential rotation checklist

状态：`BLOCKED — HUMAN ACTION REQUIRED`

不得在本文件、Issue、提交信息、聊天或日志中记录旧值、新值、前后缀或可还原信息。

- [ ] 由授权人员在 GLM Provider 控制台吊销历史暴露凭据并创建替代凭据。
- [ ] 由授权人员在 MiniMax Provider 控制台吊销历史暴露凭据并创建替代凭据。
- [ ] 由授权人员在 MiMo/ASR Provider 控制台吊销历史暴露凭据并创建替代凭据。
- [ ] 检查其他曾复用通用 `LLM_API_KEY` 的 Provider，并逐一吊销/轮换。
- [ ] 把新凭据仅写入批准的 Secret Manager；不得写入 Git、镜像层或普通日志。
- [ ] 在 Provider 审计日志中确认旧凭据已不可用，并记录执行人、Provider、时间和工单号。
- [ ] 由非执行者复核生产部署只读取 Secret Manager，且启动日志只显示 configured/not configured。

签字记录（不得含凭据）：

| Provider | Executed by | Executed at | Ticket | Reviewer | Status |
|---|---|---|---|---|---|
| GLM |  |  |  |  | PENDING |
| MiniMax |  |  |  |  | PENDING |
| MiMo/ASR |  |  |  |  | PENDING |
| Other/shared |  |  |  |  | PENDING |
