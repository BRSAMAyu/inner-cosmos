# CN-LIVE-SHOWCASE-002

日期：2026-07-27
环境：Windows + Docker Desktop + kind `kind-kubedeploy`
结论：H1、KEDA、跨角色 trace 三个 Hero 场景均在本机真实执行通过；公网网页 Demo 已接入同一 OTel/Jaeger。

## H1：强制删除精确承载 Pod

- 实验 turn：`26`
- 目标 Pod：`inner-cosmos-api-f5798684-fgrq9`
- lease owner 包含目标 Pod 身份与 generation UUID。
- 故障命令：`kubectl -n inner-cosmos-w3 delete pod <target> --grace-period=0 --force --wait=false`
- 本地展示入口自动重新挂载：`CLIENT_INGRESS_REATTACHED`
- 系统收尾：`H1_LIVE_PASS turn=26 final_status=COMPLETED api=2/2`
- 浏览器实际观察：
  1. “连接短暂中断，正在安全恢复”
  2. “从持久化时间线恢复”
  3. “恢复完成，消息与会话都没有丢失”
- 强删前的完整历史、当前用户消息和接管后 Aurora 回复同时可见。

## KEDA：按 durable work 压力扩缩容

- 合成 durable work：3000
- Worker 实际轨迹：`1 -> 3 -> 6`
- backlog：2850 逐步下降到 0
- 收尾：`duplicate_receipts=0`
- 清理：`keda_cleanup=PASS worker_baseline=1 synthetic_rows=0`

## OpenTelemetry / Jaeger

- kind 新鲜跨角色 trace：`8fa72f551979a288bf717ba2f4298283`
- 服务：`inner-cosmos-api, inner-cosmos-worker`
- spans：8
- 禁止隐私标签：0
- 公网网页实例服务名：`inner-cosmos-public-demo`
- 公网实例最近一小时检查：40 traces / 72 spans
- 公网实例禁止隐私标签：0
- `show-live-observability.ps1` 输出：`AUDIENCE_TRACE_STATUS=READY`

## 证据边界

- 公网 Demo 使用临时 Cloudflare Quick Tunnel；URL 每次启动可能变化，不写入本证据。
- kind 使用 Mock LLM，验证的是云原生连续性、持久化、fencing、扩缩容和 telemetry 合同，不是远程模型质量。
- H1 的 5 秒定位窗只让演示脚本稳定抓取真实 generation lease；故障、接管、写入与客户端恢复均为真实路径。
- Windows 公网实例与 kind 是两套运行环境；Jaeger 同时接收两者 trace，Kubernetes Grafana 面板只描述 kind。
