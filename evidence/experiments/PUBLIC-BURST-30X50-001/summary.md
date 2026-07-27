# PUBLIC-BURST-30X50-001

## 结论

状态：**PASS（run-07 / run-08 两次连续、干净、同条件复现）**

历史 run-01 至 run-05 的失败报告全部保留，未从实验分母中删除。最终主张
只使用修复后干净运行时的 run-07 / run-08；run-06 因短暂叠加另一项
质量实验，单独标记为 `CONCURRENT_EXTRA_LOAD_PASS`。

## 最终 30 用户 × 50 Sandbox 公网证据

运行时：

```text
origin=https://participating-beverly-susan-saint.trycloudflare.com
provider=GEMINI
model=gemini-3.6-flash
runtime=single-pass.v1
```

前置门先证明：

- 公网 `/api/ai/health` 为 `gemini/gemini-3.6-flash`
- 两个独立 WebRequestSession 得到两个不同的 `sandbox-*` owner
- 两个临时 Sandbox 均能删除

### 正式连续 clean passes

| 指标 | run-07 | run-08 |
|---|---:|---:|
| 状态 | PASS | PASS |
| 真实 Gemini Aurora 完成 | 30/30 | 30/30 |
| HTTP 429 | 0 | 0 |
| Critic fallback | 0 | 0 |
| 注册 p50 / p95 | 868.66 / 1,080.48 ms | 836.72 / 942.31 ms |
| 校准 p50 / p95 | 442.93 / 721.60 ms | 386.35 / 639.92 ms |
| Aurora p50 | 7,302.17 ms | 7,392.15 ms |
| Aurora p95 | 17,896.85 ms | 12,848.04 ms |
| Aurora p99 | 21,070.08 ms | 14,568.02 ms |
| 完整轨迹 p95 | 19,540.73 ms | 14,496.87 ms |
| Sandbox 进入 | 50/50 | 50/50 |
| 唯一 Sandbox owner | 50/50 | 50/50 |
| Sandbox 清理失败 | 0 | 0 |
| Social discovery checks | 30/30 | 30/30 |
| 接受好友环连接 | 30/30 | 30/30 |
| nobodyLeftAlone | true | true |
| 临时普通账号删除 | 30/30 | 30/30 |
| stage failures / failures | 0 / 0 | 0 / 0 |

`single-pass.v1` 是当前 adaptive classroom runtime 对这批简单首次对话的
预期路由；本实验不用于证明 planner/critic 质量。它证明的是在真实
Gemini、真实注册/校准/Aurora、身份隔离和真实社交数据库写入条件下，
同一笔记本公网入口连续两轮承载 30 个并发用户与 50 个 Demo Session。

### 额外负载轮次

`run-06-report.json` 也完整 PASS：

- 30/30 Gemini Aurora
- 50/50 Sandbox、50 个唯一 owner、清理失败 0
- 30/30 discovery、30/30 好友环
- 0 个 429，30/30 临时账号删除
- Aurora p95 `6,030.54 ms`
- 完整轨迹 p95 `7,961.02 ms`

但 run-06 与 bilingual `-003` 有短暂重叠，因此仅作为
`CONCURRENT_EXTRA_LOAD_PASS`。它可以说明叠加工作负载下未破坏合同，
不能替代两次严格同条件复现。

原始正式报告：

- `run-07-report.json`
- `run-08-report.json`

### 最终 retry 代码 smoke

`run-09-final-code-smoke.json` 在 retry 修复后的最终代码、无其他负载重叠
条件下单独执行，标记为 `FINAL_CODE_SMOKE`。它用于证明最终代码没有
引入容量、隔离或社交回归，不替代 run-07/run-08 的两次连续证据。

| 指标 | run-09 FINAL_CODE_SMOKE |
|---|---:|
| 状态 | PASS |
| 真实 Gemini Aurora 完成 | 30/30 |
| HTTP 429 | 0 |
| Provider / model | GEMINI / gemini-3.6-flash |
| 注册 p50 / p95 | 603.16 / 673.62 ms |
| 校准 p50 / p95 | 414.17 / 1,187.89 ms |
| Aurora p50 / p95 / p99 | 4,073.34 / 7,186.27 / 9,206.53 ms |
| 完整轨迹 p95 | 8,779.17 ms |
| Sandbox 进入 / 唯一 owner | 50/50 / 50/50 |
| Sandbox 清理失败 | 0 |
| Social discovery / 好友环 | 30/30 / 30/30 |
| nobodyLeftAlone | true |
| 临时普通账号删除 | 30/30 |
| Critic fallback | 0 |
| stage failures / business failures | 0 / 0 |

脚本还会把 speaker fallback、deterministic emergency fallback、Mock
Provider 和未启用 fail-closed 合同判为用户轨迹失败；run-09 的 30/30
成功意味着这些失败条件均未触发。`single-pass.v1` 的
`background-planner-not-observed` 是 adaptive runtime 对简单首次对话的
预期说明，不是 fallback 或业务失败。

额外负载报告：

- `run-06-report.json`

## 正确 Gemini 运行时复验：run-04 / run-05

2026-07-28 对新 Origin
`https://manitoba-nevertheless-teeth-info.trycloudflare.com` 先执行了前置门：

- `/api/ai/health`：`gemini/gemini-3.6-flash`
- 两个独立 WebRequestSession：`2/2` 返回不同的 `sandbox-*` owner
- 两个临时 Sandbox：`2/2` 删除成功

随后使用完全相同的 `30 users + 50 sandbox sessions` 参数连续执行
run-04 和 run-05。两轮均保留为 **FAIL**：

| 指标 | run-04 | run-05 |
|---|---:|---:|
| 真实 Gemini Aurora 完成 | 30/30 | 30/30 |
| Aurora HTTP 429 | 0 | 0 |
| Provider / model | GEMINI / gemini-3.6-flash | GEMINI / gemini-3.6-flash |
| 注册 p95 | 902.35 ms | 1,299.09 ms |
| 校准 p95 | 633.50 ms | 809.84 ms |
| Aurora p50 | 7,424.05 ms | 7,076.69 ms |
| Aurora p95 | 19,325.19 ms | 15,712.25 ms |
| Aurora p99 | 27,263.66 ms | 16,481.29 ms |
| 完整轨迹 p95 | 21,042.56 ms | 17,685.68 ms |
| Sandbox 成功且 owner 唯一 | 10/50 | 5/50 |
| 好友发现 / 好友环 | 未开始 | 未开始 |
| 临时普通账号清理 | 2/30 | 2/30 |

两轮失败模式相同：Sandbox 批次触发 `429 Too Many Requests`，之后 social
重新登录以及账户清理也被同一个 login bucket 阻断。报告中的
`http429Count=0` 只统计 30 条 Aurora 结果，不包含 Sandbox/social/cleanup
阶段的 fatal 429。

### run-04 / run-05 的精确根因

`REDIS_RATE_LIMIT_ENABLED=false` 只会选择
`InMemoryRateLimitStore`，并不关闭 `ApiRateLimitFilter`：

- Public compose 同时设置
  `REDIS_RATE_LIMIT_ENABLED=false` 和
  `DEMO_UNLIMITED_USAGE_ENABLED=true`
  （`deploy/compose/public-demo.yml:47-48`）。
- `InMemoryRateLimitStore` 在 Redis 开关为 false 时生效
  （`src/main/java/com/innercosmos/ratelimit/InMemoryRateLimitStore.java:17-19`）。
- `ApiRateLimitFilter` 将 `/api/public/demo/enter/*` 和普通 login 一起按
  client IP 计入 login bucket
  （`src/main/java/com/innercosmos/config/ApiRateLimitFilter.java:63-64,162-166`）。
- login bucket 的默认容量和补充速度都是 `10/min`
  （`src/main/resources/application.yml:370-371`）。
- `DEMO_UNLIMITED_USAGE_ENABLED` 虽映射到配置
  （`application.yml:379`），复验时没有任何限流代码读取它。

本实验全部从同一笔记本公网 IP 发出，因此 run-04 恰好允许前 10 个
Sandbox；约一分钟后的 run-05 仅补回约 5 个 token。这个数字序列直接
吻合 greedy refill，而不是 Cloudflare 或 Gemini Provider 限流。

该 P0 已在源码中修复并通过聚焦测试，但按实验任务约束尚未重建运行时：

- 非 `prod` 且 `DEMO_UNLIMITED_USAGE_ENABLED=true` 时，仅旁路
  `ApiRateLimitFilter` 的人为额度；
- 认证、授权、CSRF、隐私、幂等和危机安全链继续执行；
- 默认 false 仍限流；
- `prod` 即使误配 true 也仍限流；
- `ApiRateLimitFilterTest,LlmEndpointRateLimitTest`：PASS。

因此 run-04/run-05 必须保持 FAIL。只有用修复后的新镜像再次执行两轮，
才能建立 30/50 PASS 证据。

本次共有三份原始报告：

| 报告 | 证据等级 | 结果 |
|---|---|---|
| `run-01-report.json` | 执行环境失败 | 受限沙箱无法使用 Windows TLS 凭据；业务请求未开始 |
| `run-02-report.json` | 执行环境失败 | 更换到当前 Tunnel 后仍在受限沙箱内 TLS 失败；业务请求未开始 |
| `run-03-report.json` | 有效公网业务实验 | 30/30 Aurora 成功、0 个 429，但 Demo Sandbox 隔离、清理、社交发现及 Provider 冻结失败 |

前两次失败没有被覆盖或删除。`run-03` 在沙箱外使用官方 PowerShell
7.6.3 便携运行时执行，HTTPS 证书校验、30/50 并发和通过阈值均未降低。

## 固定命令

```powershell
.\.tools\pwsh-7.6.3\pwsh.exe -NoProfile `
  -File .\scripts\demo\test-30-user-burst.ps1 `
  -Origin https://causing-weighted-gotta-matrix.trycloudflare.com `
  -UserCount 30 `
  -ThrottleLimit 30 `
  -SandboxEntryUsers 50 `
  -SandboxEntryThrottleLimit 50 `
  -ReportPath .\evidence\experiments\PUBLIC-BURST-30X50-001\run-03-report.json
```

## run-03 原始数字

### 已通过的子合同

- 真实注册、校准、Aurora 回复：`30/30`
- HTTP 429：`0`
- Critic fallback：`0`
- 注册延迟：p50 `690.02 ms`，p95 `890.42 ms`
- 校准延迟：p50 `333.09 ms`，p95 `1,182.19 ms`
- Aurora 总延迟：p50 `16,573.94 ms`，p95 `20,698.74 ms`，
  p99 `29,605.58 ms`
- 完整单用户轨迹 p95：`21,993.69 ms`
- 30 个临时普通账号：`30/30` 已删除

这些数字仅证明本轮命中的公网运行时能够同时完成 30 条真实 Aurora
轨迹；它们不能覆盖下面的隔离和社交失败。

### 未通过的子合同

- 报告中的 Provider：`DEEPSEEK/deepseek-v4-flash`
- 预期 Provider：`GEMINI/gemini-3.6-flash`
- 50 个 Demo 入口仅返回 `3` 个不同 owner
- 返回了共享 curated template identity
- Demo Sandbox 清理失败：`50/50`
- 每个普通用户只发现 `15/29` 个本轮同伴
- 好友环没有开始，接受连接：`0/30`

## 三个失败的统一高置信根因

### 1. Tunnel/Origin 命中了旧运行时，而不是刚启动的 Gemini Compose 实例

这是最高置信度根因，不是 Gemini 自动 failover：

1. `run-public-demo.ps1` 的 Demo 信息记录 `provider=gemini`。
2. 新 Compose 容器的非密钥环境变量为
   `LLM_PROVIDER=gemini`，三个 Aurora stage model 均为
   `gemini-3.6-flash`。
3. 新容器启动日志明确记录：
   `Creating LlmClient for provider: gemini`。
4. 但通过发布的公网 Origin 登录后读取 `/api/ai/health`，返回：
   `provider=deepseek, model=deepseek-v4-flash`。
5. 同一个公网 Origin 的 burst 回复也全部报告
   `DEEPSEEK/deepseek-v4-flash`。
6. 新容器的 `HostConfig.PortBindings` 请求了
   `127.0.0.1:8080`，但当时 `NetworkSettings.Ports["8080/tcp"]`
   为空；健康门只证明 Tunnel 后面“有一个 UP 服务”，没有证明它是
   这次刚创建的容器。

因此 `demo-info.txt` 中的 Provider 是启动脚本的**意图值**，不是公网
Origin 的有效运行时证据。最可能的具体机制是 8080/Tunnel 切换期间仍
连接到了先前的 DeepSeek Demo。缺少公开的 image SHA / git SHA /
instance id，暂时无法只靠现有端点进一步区分具体旧进程。

当前启动门只检查 HTTP 健康
（`scripts/demo/run-public-demo.ps1:248`）；验证失败默认被降级为
`WARN` 并继续发布（`:264-272`），最后仍输出
`PUBLIC_DEMO_READY_WITH_WARN`（`:316`）。这正是错误运行时仍被接受的
原因。

`SessionModelRouter` 的用户 profile 主键查询也曾存在错误，但它不能
解释 `/api/ai/health` 的系统 Provider 同样变成 DeepSeek。因此它是
独立风险，不是本次公网 Provider 漂移的充分根因。

### 2. 50→3 不是 burst 复用同一 WebRequestSession

burst 在 `ForEach-Object -Parallel` 的每个 runspace 内部各自创建新的
`WebRequestSession`（`scripts/demo/test-30-user-burst.ps1:170-212`），
没有把 Session 对象跨 runspace 共享。

当前源码合同是在每个 HTTP Session 内按 persona key 缓存该 Session
自己的 Sandbox；第一次访问调用
`createPersonalSandbox`（
`src/main/java/com/innercosmos/controller/DemoExperienceController.java:87-95`）。
当前返回值应为新建 SANDBOX 的真实 user id（`:104`）。

当前删除端点也故意只允许删除 `accountKind=SANDBOX` 的当前用户
（`PublicDemoSandboxLifecycleController.java:30-42`）。因此旧运行时返回
三个共享 DEMO/SHOWCASE 账号后，50 次删除全部被拒绝，行为与报告完全
吻合。

附加数据库证据：目标 Compose 数据库在 run-03 所在的 `20:58 UTC`
没有新增 SANDBOX；已有 59 个 SANDBOX 均来自更早分钟。这再次说明
burst 请求没有命中刚启动的 Compose 应用/数据库。

### 3. `15/29` 是旧社交实现的窗口特征，不是当前源码的 60 行合同

当前 `SocialServiceImpl`：

- 仅返回 `account_kind=HUMAN`
- 无 query 时 `LIMIT 60`
- 精确用户名/昵称 query 时 `LIMIT 10`

对应
`src/main/java/com/innercosmos/service/impl/SocialServiceImpl.java:64-81`。

而 `a5a23600` 之前的实现是 `HUMAN + SHOWCASE`、`LIMIT 30`。本轮每个
actor 只看到 15 个 burst peer，与旧窗口被存量账号占据的行为一致。
burst 脚本中“最多 30 行”的注释也已经落后于当前 `LIMIT 60` 源码
（`scripts/demo/test-30-user-burst.ps1:512-529`）。

因此先修复/证明部署身份，再判断当前 `LIMIT 60` 是否还能重现发现
失败；不能在旧运行时上修改当前社交查询来“修数字”。

## 历史 run-03 的最小修复与重跑门

按优先级：

1. 停止旧 Public Demo、旧 Tunnel 和占用 8080 的运行时；确认新 Compose
   的 published port 确实存在。
2. 实验启动必须使用 `-StrictVerification`；任何 persona 隔离失败都不
   得发布 `demo-info.txt` 或 READY。
3. 在 burst 前增加有效运行时门：
   - `/api/ai/health` 必须为 `gemini/gemini-3.6-flash`
   - 两个独立 WebRequestSession 进入同一 persona 必须得到两个不同且以
     `sandbox-` 开头的账号
   - 两个 Session 的 `DELETE /api/public/demo/sandbox` 都必须成功
4. 给健康/版本端点增加非敏感运行时身份：
   `gitSha`、`imageDigest`、`instanceId`、`effectiveProvider`。Tunnel
   readiness 必须核对预期 identity，而不能只看 `status=UP`。
5. 部署身份通过后，原样连续执行两轮 30/50；不得删除本次失败报告，
   新报告从 `run-04-report.json` 开始。
6. 若当前运行时仍出现 discovery 失败，再修订断言：优先按用户名 exact
   query 验证好友环相邻节点，默认 people 列表只证明产品的有界发现
   窗口。不要以扩大列表掩盖功能不可达。

## 最终主张边界

当前可以陈述：

> 在同一修复后公网 Gemini 运行时上，两次连续 clean run 均完成 30/30
> 个真实注册、校准与 Aurora 回合，Provider 为 Gemini 3.6 Flash，
> HTTP 429、fallback 和 stage failure 均为 0；每轮 50/50 Demo Session
> 获得不同 owner 并全部清理，30/30 位用户完成发现检查和好友环，临时
> 账号 30/30 删除。两轮 Aurora p95 分别为 17.90 秒和 12.85 秒。

边界：

- 这是 Windows 笔记本、Quick Tunnel 和当前 classroom 数据规模下的
  Demo 容量证据，不是商业生产容量或 SLA。
- 本实验输入触发 `single-pass.v1`，不能用于证明 dual-kernel、
  planner/critic 或语义质量优于基线。
- `run-06` 因额外实验重叠，只能作为额外负载通过证据；正式连续复现是
  `run-07` 和 `run-08`。
- `run-09` 是最终 retry 代码的无重叠 smoke；它证明最终代码未回归，
  但不是第三次预注册的连续容量重复。
- 历史 run-01 至 run-05 仍是失败台账的一部分，不得从材料中删除。
