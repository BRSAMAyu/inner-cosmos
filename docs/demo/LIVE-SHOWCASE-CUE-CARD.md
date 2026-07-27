# Inner Cosmos 现场展示 Cue Card

## 一句话叙事

> 先让真实观众使用产品，再故障注入，再用业务压力扩缩容，最后回到从第一分钟就持续
> 采集的 trace 和指标解释刚才发生了什么。

这不是四套互不相关的 Demo。OTel Collector、Jaeger、Prometheus 和 Grafana 在观众入场前
启动，贯穿真实产品体验、H1 和 KEDA；最后一幕只是把早已采集的数据展示出来。

## 课前启动（观众入场前）

所有命令先进入仓库：

```powershell
Set-Location -LiteralPath 'D:\code\inner cosmos'
```

先清理可能残留的固定端口，再执行完整预检：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\stop-live-showcase.ps1'
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-three-hero-showcase.ps1' `
  -Scene Preflight
```

启动固定的观测与展示入口：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\start-live-showcase.ps1'
```

看到 `LIVE_SHOWCASE_READY` 后，启动公网真机 Demo，并从第一条请求开始导出 trace：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-public-demo.ps1' `
  -EnableLiveObservability `
  -SkipApkBuild
```

`-SkipApkBuild` 是本轮五分钟网页真机演示的快速路径，不编译 APK。

提前打开四个浏览器标签：

1. 公网 `Web App` 地址——给台下真实用户。
2. [H1 连续性面板](http://127.0.0.1:3000/d/inner-cosmos-recovery/continuity-contract-c2b7-pod-recovery-live?orgId=1&refresh=2s)。
3. [KEDA 工作压力面板](http://127.0.0.1:3000/d/inner-cosmos-events/work-pressure-contract-c2b7-outbox-and-keda?orgId=1&refresh=5s)。
4. [Jaeger Search](http://127.0.0.1:16686/search)。

## 0:00–5:00：真机产品 Demo

- 投影公网 Web App 或二维码。
- 台下用手机注册、进入 Aurora、发送消息、探索记忆/共鸣/连接。
- 此时 OTel 已经在采集，服务名固定为 `inner-cosmos-public-demo`，课堂采样率为 100%。
- 不要此时打断用户去讲 dashboard；只说一句：“这些真实交互正在进入我们的 telemetry
  pipeline，最后会回来看。”

边界：Windows 公网实例和 kind 展柜是两套运行环境。Jaeger 可以同时接收两者的 trace；
Prometheus/Grafana 的 Kubernetes 面板只描述 kind 展柜，不把 Windows 单实例伪装成 Pod。

## 5:00–7:00：H1——用户视角 + 系统视角

### 用户视角

在 [kind H1 客户端](http://127.0.0.1:8081/app/aurora/) 登录预演账号并准备发送消息。
投影可以二分屏：

- 左侧 60%：Aurora 客户端；
- 右侧 40%：H1 Grafana 面板。

硬故障时客户端会显示三步连续性卡片：

1. 检测到连接中断；
2. 从持久化时间线恢复；
3. 消息与会话完整。

### 系统视角

在终端执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-h1-live-demo.ps1' `
  -FaultMode HardCrash
```

看到 `H1 LIVE DEMO ARMED` 后再在客户端发送一条 Aurora 消息。脚本会从持久化 generation
lease 精确识别真正承载这次生成的 Pod，然后把即将执行的完整命令打印出来：

```text
kubectl -n inner-cosmos-w3 delete pod <active-pod> --grace-period=0 --force --wait=false
```

这是对精确目标 Pod 的 0 秒 grace 强制删除，比普通 Pod 删除更严格，也更适合展示客户端恢复路径。
kind 展柜配置了 5 秒 H1 定位窗口，只用于让脚本稳定读到真实 generation lease 并锁定承载它的
Pod；故障、接管、fencing、持久化与客户端恢复路径仍全部真实执行，不是预制结果。
本地 `kubectl port-forward` 本质上也会绑定某个 Pod；强删后脚本会自动把 8081 展示入口重新
挂到存活实例，模拟课堂外部负载均衡器的稳定入口，并打印 `CLIENT_INGRESS_REATTACHED`。
脚本随后持续显示：

- `available / desired` API；
- 目标容器 restart count；
- durable turn 状态；
- 最终 `H1_LIVE_PASS ... COMPLETED api=2/2`。

如果老师明确要求“删除 Pod”而不是“强杀运行时”，使用：

```powershell
.\scripts\demo\run-h1-live-demo.ps1 -FaultMode GracefulDelete
```

必须准确解释：普通 `kubectl delete pod` 会触发 readiness、preStop 和 termination grace，
理想结果是用户完全看不到报错；要展示“断联 → 恢复”，必须用不经过 graceful drain 的
硬故障。把两者混讲反而会削弱工程可信度。

讲解句：

> Redis 保存 Session、cursor 和短期 live buffer；PostgreSQL 保存 transcript、turn、
> generation request 和 fencing token。新实例只能拿到更高 fencing token，旧实例即使
> 复活也不能重复提交。

## 7:00–10:00：KEDA——按用户等待扩容

切到 KEDA 面板，然后执行独立场景：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-three-hero-showcase.ps1' `
  -Scene Keda `
  -HoldViews
```

脚本只把“扩容被观测到”作为 H2 的终点，40 秒硬门，不等待所有任务排空。只盯三项：

- Ready events：注入后立即上升；
- Worker replicas：从 `1/1` 扩到至少 `3/3`，上限为 `6`；
- 终端出现 `KEDA_SCALE_OUT_PASS elapsed_ms=...`，证明扩容在 45 秒预算内完成。

讲解句：

> 我们不按 CPU 扩容，而按用户真正等待的 durable work 数量和最老等待时间扩容。多
> Worker 用 lease/`SKIP LOCKED` 竞争，inbox uniqueness 保证副作用只提交一次。

`-HoldViews` 会让这个 H2 终端停住，但 worker 和 KEDA 控制循环继续运行。不要按 Enter：
积压排空和按 HPA 稳定窗口策略自然缩容留给 H3 面板回看。H3 结束后才回到这个窗口按 Enter，
脚本会删除 synthetic rows、恢复 worker 基线，并以 `keda_cleanup=PASS` 收尾。全量 drain 与
零重复 receipt 属于预演/离场验收，不占现场 45 秒。

## 10:00–12:00：可观测性——回看刚才真实发生的事

先展示台下真实交互：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\show-live-observability.ps1'
```

预期：

- `AUDIENCE_TRACE_STATUS=READY`；
- service=`inner-cosmos-public-demo`；
- 最近一小时 trace/spans 数量；
- `forbidden_privacy_tags=0`；
- 最新 trace 的 Jaeger 直达地址。

再展示两条互补的真实业务 trace：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\demo\run-three-hero-showcase.ps1' `
  -Scene Observability `
  -HoldViews
```

终端首先打印一条真实 Aurora 业务请求的时延瀑布：

- `client_end_to_end_ms`：用户从发送到拿到回复的感知时延；
- `traced_request_ms`：服务端 HTTP 关键路径；
- `inner.cosmos.memory.retrieve`：记忆上下文检索；
- `inner.cosmos.ai.provider`：模型生成，真实 Provider 下通常是主要耗时段；demo/mock 以现场数字为准；
- `aurora.turn` 只是完成标记，不能误讲成整轮时延。

随后打开第二条 trace，指出 API finish、outbox consume、memory projection 和 profile
projection 如何跨 API/Worker Pod 延续。两条 trace 都执行敏感标签扫描并要求结果为 0。
慢信发送与定时投递目前没有传播为同一条 trace，本轮不把它包装成“完整生命周期”。

此时切回 KEDA 面板，用 `from=now-15m` 回看 backlog 下降和 worker 按稳定窗口策略回落；不承诺
严格在某一秒缩容。H3 看完先在 H3 窗口按 Enter，再回 H2 窗口按 Enter完成清理。

## 屏幕设计

- 真机阶段：产品 100%，不展示终端噪音。
- H1：产品 60% + Grafana 40%；命令执行时短暂切到终端。
- KEDA：Grafana 70% + 终端 30%。
- Observability：Jaeger 70% + 终端摘要 30%。

终端字号至少 20，窗口预先清屏。不要现场手敲长命令；脚本会先打印实际执行的
`kubectl` 命令，组员逐段解释 `namespace → target Pod → container → signal`，再执行。

## 收尾

```powershell
.\scripts\demo\stop-public-demo.ps1
.\scripts\demo\stop-live-showcase.ps1
```

不要在结束时使用 `-DeleteData`，除非明确决定删除本轮观众产生的数据。
