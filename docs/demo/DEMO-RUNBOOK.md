# Inner Cosmos 本机公网 Demo 运行手册

## 目标与边界

本轮演示不依赖云服务器。演示者的 Windows 笔记本同时承担应用服务器、PostgreSQL、
Redis 和 AI Provider 网关；Cloudflare Quick Tunnel 提供临时公网 HTTPS 入口。老师和
同学可以：

- 用手机或电脑浏览器访问完整 Web 产品；
- 从同一入口下载 Android APK，安装后连接同一台笔记本；
- 自助注册真实账号；
- 与 Aurora 对话并生成记忆；
- 创建、发布和访问共鸣体，进行共鸣体对话和慢信；
- 发现现场其他用户、添加好友、创建和加入群组。

这里的“完成”以现场核心轨迹可用为准，不以 AWS、商店发布、长期生产运维或新加坡
合规签字为前置条件。

## 一次性准备

Windows 需要：

- Docker Desktop 已启动；
- JDK 21、Node.js 22、Android SDK（只有构建/安装 APK 时需要）；
- 仓库根目录存在被 `.gitignore` 排除的 `API*.txt`，包含 DeepSeek 与 Qwen 凭据；
- 电脑不能休眠，网络需允许访问 Cloudflare 和模型 Provider。

脚本在缺少 `cloudflared.exe` 时，会从 Cloudflare 官方 GitHub Release 下载 Windows
AMD64 版本到被 Git 忽略的 `scripts/demo/bin/`。密钥只注入当前 Docker Compose
进程，不会写入镜像、Git、Markdown 或运行证据。

## 启动：一条命令

在仓库根目录打开 PowerShell：

```powershell
.\scripts\demo\run-public-demo.ps1
```

脚本以单并发 Gradle 和单并发 Compose 构建为默认，依次完成：

1. 生成临时公网 HTTPS 地址；
2. 将该地址编译进本次 Debug APK；
3. 构建 Capacitor Android 应用；
4. 启动 PostgreSQL 16 + pgvector、Redis 和 Spring Boot；
5. 启用 Redis Session、限流、幂等、Aurora 流和 JDBC Outbox；
6. 使用真实 DeepSeek 对话、Qwen embedding 和 Qwen TTS，禁用 Mock fallback；
7. 自动验证公网健康、首页、APK 下载、双用户注册、好友、群组、Aurora、记忆沉淀、
   共鸣体发布/发现/对话以及慢信发送；
8. 打印三个可分享地址和 APK SHA-256。

成功标志为：

```text
PUBLIC_DEMO_READY
Landing: https://...trycloudflare.com/
Web App: https://...trycloudflare.com/app/aurora/
Android: https://...trycloudflare.com/downloads/inner-cosmos-demo.apk
```

不要把仓库内旧的 APK 另行发给同学。必须分享本次 `Android:` 地址，因为 Quick
Tunnel 地址每次重新启动都会变化，APK 也会随之重新绑定。

## 演示前 5 分钟检查

```powershell
.\scripts\demo\status-public-demo.ps1
```

确认：

- `postgres`、`redis`、`app` 均为 healthy；
- `tunnel_running=True`；
- 手机蜂窝网络能打开 `Landing`（这能排除仅在本机可见的假成功）；
- 浏览器可以注册一个新账号；
- APK 可以下载；
- 演示笔记本已接电并关闭自动睡眠。

如需人工重跑核心验收：

```powershell
.\scripts\demo\verify-public-demo.ps1 -Origin "https://你的地址.trycloudflare.com"
```

只复核网络、注册、好友与群组而不再次调用 AI：

```powershell
.\scripts\demo\verify-public-demo.ps1 `
  -Origin "https://你的地址.trycloudflare.com" -SkipAurora
```

## Android 安装与使用

老师或同学在 Android 手机浏览器打开 `Landing`，点击“下载 Android App”。首次
旁加载可能需要允许当前浏览器安装未知应用；这是课堂 Debug APK 的正常流程。安装后：

1. 打开 Inner Cosmos；
2. 选择注册，创建自己的账号；
3. App 直接连接本次公网 HTTPS 地址；
4. 进入 Today 与 Aurora 对话；
5. 在 Cosmos 查看沉淀的记忆与画像；
6. 在 Resonance 创建/发现共鸣体；
7. 在 Connect 添加同学、接受邀请、创建群组与查看慢信。

Debug APK 只为本轮课堂演示开启跨 HTTPS Origin 的 WebView Session Cookie。Release
资源保持关闭，不会把这一策略带入正式包。App 不使用演示账号或内置密码。

## 推荐 8–12 分钟演示轨迹

1. 两名同学分别用网页或 App 注册。
2. 用户 A 与 Aurora 对话，展示多气泡、打断/停止/继续和 Inner Voice。
3. 结束对话，进入 Cosmos 查看新记忆、来源和可纠正画像。
4. 用户 A 用授权记忆编译私有共鸣体，预览、沙盒验证后发布。
5. 用户 B 在 Resonance 发现它，查看匹配解释并进行共鸣体对话。
6. 用户 B 写一封慢信；用户 A 在 Connect 看到关系轨迹。
7. A/B 互相添加好友，创建课堂群组并接受邀请。
8. 最后展示同一产品在浏览器和 Android App 中共享相同数据与会话语义。

## 停止与恢复

保留数据库数据并停止：

```powershell
.\scripts\demo\stop-public-demo.ps1
```

同时删除本次 PostgreSQL/Redis 数据：

```powershell
.\scripts\demo\stop-public-demo.ps1 -DeleteData
```

Quick Tunnel 进程停止、电脑休眠、网络切换或重启后，旧公网地址和旧 APK 都可能失效。
重新运行 `run-public-demo.ps1` 会生成新地址和新 APK。需要跨重启的固定地址时，应另行
配置命名 Cloudflare Tunnel；这不是现场单次演示的阻塞项。

## 常见故障

- `Docker Desktop is not reachable`：启动 Docker Desktop，等待 Engine 就绪后重试。
- `A public demo tunnel is already running`：先运行状态脚本；确需重建时先停止旧 Demo。
- 找不到 Provider Key：确认根目录的 `API*.txt` 未被移动，且没有提交到 Git。
- 网页可用但 App 登录失败：不要手动构建普通 mobile APK；重新运行完整启动脚本。
- Aurora 无回复：先确认公网健康，再检查 Provider 网络；脚本默认禁止 Mock fallback，
  因此不会把假回复伪装成真实成功。
- APK 安装失败：卸载旧的 `sg.innercosmos.app.dev` 后重装；正式签名和商店发布不属于
  本次课堂旁加载路径。

## 已验证事实与诚实边界

2026-07-24 已在 Windows 主机、Android API 36.1 模拟器和公网 Quick Tunnel 上证明：

- APK 安装、冷启动、注册、Session 恢复与真实 Aurora 多气泡回复；
- 公网新用户相互发现、好友请求/接受、私有群组邀请/接受；
- Aurora 对话到记忆沉淀；
- 私有共鸣体编译、发布、访客发现、访客对话与慢信发送；
- 公网下载 APK 与模拟器安装 APK 的 SHA-256 完全一致；
- Android 进程无 Crash/ANR。

仍需在正式课堂前用至少一台真实 Android 手机做一次旁加载和蜂窝网络冒烟。iOS
签名、应用商店、固定域名、云端部署和长期生产监管不是本轮 Demo 的完成条件。
