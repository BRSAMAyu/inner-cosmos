# 公网 Demo 隧道模式

公共 Demo 现在区分两种运行承诺。

## Quick Tunnel：临时体验

```powershell
.\scripts\demo\run-public-demo.ps1
```

Quick Tunnel 不需要 Cloudflare 账号，但地址会在进程退出、电脑休眠、网络切换或重启后失效。
APK 会绑定本次临时地址，因此 watchdog 只监测故障，不会自动创建一个地址不同的新 Quick
Tunnel。故障后必须重新运行完整启动脚本，并重新分享新 APK。

默认验收用于发现问题但不阻塞 Demo 启动；只有需要严格发布门禁时才使用：

```powershell
.\scripts\demo\run-public-demo.ps1 -StrictVerification
```

## Named Tunnel：固定地址

先在 Cloudflare 控制台建立 remotely-managed Tunnel，将固定 hostname 路由到
`http://127.0.0.1:8080`。token 只注入当前操作者环境，禁止写入仓库、脚本、日志或
`demo-info.txt`：

```powershell
$env:CLOUDFLARED_TUNNEL_TOKEN = "<external secret>"
.\scripts\demo\run-public-demo.ps1 `
  -TunnelMode named `
  -PublicOrigin "https://demo.example.com"
```

启动脚本通过子进程环境变量将 token 交给 `cloudflared`，不会把 token 放进命令行。
Named Tunnel 地址固定；默认 watchdog 在 `cloudflared` 进程退出时最多重启六次，并继续
检查公网 `/actuator/health`。Docker Compose 的 app、PostgreSQL 和 Redis 仍采用
`restart: unless-stopped`。

这条路径需要人工提供 Cloudflare 账号、Tunnel、DNS/hostname 和有效 token。仓库不能
自动创建这些外部资源。

## 实时状态与停止

```powershell
.\scripts\demo\status-public-demo.ps1
.\scripts\demo\stop-public-demo.ps1
```

只有下列条件同时满足，状态才会输出 `demo_state=READY` 和可分享地址：

- PID 属于仓库内预期的 `cloudflared.exe`，且命令与 Quick/Named 模式一致；
- 固定或临时 origin 的公网 `/actuator/health` 返回 `UP`；
- 当前 APK SHA-256 与本轮 `demo-info.txt` 完全一致；
- Docker Engine 可访问。

否则状态为 `STALE_OR_UNAVAILABLE`，旧地址只以 `stale_origin_recorded` 标记，不会被呈现
为当前可用入口。停止 Demo 默认保留 PostgreSQL/Redis 数据卷，但会清除失效 URL、PID
和 watchdog 状态。

脚本级契约检查：

```powershell
.\scripts\demo\test-public-demo-script-contracts.ps1
```
