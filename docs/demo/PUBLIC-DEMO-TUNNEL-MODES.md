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

先在 Cloudflare 控制台建立 remotely-managed Tunnel，将一个易读的固定 hostname（例如
`https://aurora.example.com`）路由到 `http://127.0.0.1:8080`。推荐首次只配置一次：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File ".\scripts\demo\set-fixed-public-demo.ps1" `
  -PublicOrigin "https://aurora.example.com"
```

脚本会在控制台安全提示中读取 token。固定域名保存在被 Git 忽略的
`.demo-runtime/fixed-tunnel.json`，token 则由 Windows DPAPI 以当前用户身份加密；文件不能
复制给另一台电脑或另一个 Windows 用户使用。之后每次启动、网络恢复或电脑重启，只需：

```powershell
.\scripts\demo\start-fixed-public-demo.ps1
```

启动脚本解密 token 后只通过子进程环境变量交给 `cloudflared`，不会把 token 放进命令行、
日志或 `demo-info.txt`。若隧道仍健康，它会自动走 `-ReuseTunnel`，因此不会因为重复启动
而换地址。
Named Tunnel 地址固定；默认 watchdog 在 `cloudflared` 进程退出时最多重启六次，并继续
检查公网 `/actuator/health`。Docker Compose 的 app、PostgreSQL 和 Redis 仍采用
`restart: unless-stopped`。

可选安装“当前 Windows 用户登录 60 秒后启动”的任务（Docker Desktop 仍需配置为登录后
启动）：

```powershell
.\scripts\demo\install-fixed-public-demo-autostart.ps1

# 删除自动启动任务
.\scripts\demo\install-fixed-public-demo-autostart.ps1 -Remove
```

这条路径需要操作者在 Cloudflare 控制台提供账号、Tunnel、DNS/hostname 和有效 token。
仓库不会自动修改外部账户或 DNS。固定域名负责“短、易记、不随重启变化”；不要再为课堂
分享 Quick Tunnel 的随机 `trycloudflare.com` 地址，也不要额外依赖会过期的第三方短链。

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
