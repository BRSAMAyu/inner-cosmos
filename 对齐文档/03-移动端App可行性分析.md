# 内心宇宙 → 移动端 App 可行性分析

> 本文由 4 个 agent 并行分析后汇总(前端选型 / 后端 API / 原生能力 / 分发成本)。只提供信息、现状、选项与权衡,不给执行步骤。证据附 `file:line`。评估时点 2026-07-14。

---

## 0. 核心结论(先读这段)

1. **现有前端比预期更适合移动化**:纯静态 HTML/JS/CSS 多页站、已响应式 + 触控感知(24 个 media query、48px 触控目标、暗色/无障碍齐备)、原生 `EventSource`/Web Audio、**全站无 Canvas**(记忆星空是 SVG+DOM,粒子是 DOM div+CSS)。→ **Capacitor 包壳(~95% 代码复用、可上架、补齐 iOS 推送)是性价比断档首选;PWA 是零成本前置**;RN/Flutter/纯原生因"无 Canvas 红利 + UI 全重写换不来体验增量"**不推荐**。

2. **移动化的真正障碍不在前端,在"后台/推送"**:现有主动唤醒(Aurora proactive)100% 依赖前台 SSE 长连接(`ProactiveDeliveryChannel.java:19` 进程内 Map),**App 后台即断、杀进程即丢,且零推送基础设施(无设备 token 表、无离线补发)**。必须重构为"服务端 @Scheduled 决策 + FCM/APNs 推送驱动 + 端上点击拉起"。这是**最大的架构改动**。

3. **危机安全管道在移动端有硬缺口**:无 SOS 按钮(仅小 tel 链接)、无自动运营告警、POST 回退不跳安全港页。心理健康 App 移动化必须**更强更快更深集成**(端上关键词预检 + 一键拨号 + 本地 SOS + HIGH 事件实时推运营)。

4. **分发门槛是产品/行政问题,不是技术**:中国大陆分发=软著(1.5-4 个月)+ APP 备案 + iOS ICP 备案 + 名称三方一致 + 华为/小米/OPPO/vivo/应用宝多商店逐个过审 + 境内主体/服务器。**Web/PWA 完全没有这道墙**。移动 App 唯一高增量价值=**稳定推送**(睡前复盘/慢信送达/主动消息);其余 Web/PWA 基本覆盖。

5. **诚实定位**:对一个中文、低压陪伴、课程体量的产品,**PWA 几乎能拿到移动 App 80% 用户价值,成本不到 5%**。原生 App 是"新产品量级"工程,与课设体量不匹配——除非确认要长期商业化。

---

## 1. 现有前端/后端形态盘点(基线)

**前端**(已读真实代码核实):
- 纯静态多页站,**无构建链、无 SSR、无框架**(无 React/Vue/jQuery),37 个 HTML 页 + 11 个 JS + 2 个 CSS。
- 单体 `const IC = {}` 全局 namespace(`app.js:1`,831 行)+ localStorage 存偏好;36/36 页内联 `<script>`。
- `app.css` **4785 LOC / ~612 规则**,但**结构化设计 token 体系成熟**:莫兰迪色板 `--color-*`、文本/强调色、阴影层级 `--elev-1/2/3`、间距圆角、动效曲线 `--ease-flow/drift/bloom`、模数字号、字体 `--font-hero`(LXGW 文楷)/`--font-body`(Noto Serif SC)/`--font-display`(Cormorant)。
- **关键纠偏:全站无 Canvas**。记忆星空是 `<svg>` 连线 + DOM `<div class="star">` + CSS 关键帧;粒子是 DOM div + CSS float。
- **已具备的移动适配**:24 个 `@media`,含 `(hover:none) and (pointer:coarse)`(关光标、按钮 48px、`:active scale(0.96)` 触摸反馈)、`max-width:479px`(`--tap:48px`、按钮全宽)、`prefers-reduced-motion`、retina;无障碍有 skip-link + `aria-live`。**这不是桌面专属站,是已响应式+触控感知的 Web 应用。**
- 运行时:REST `fetch credentials:include`(GET 重试 1 次,POST 不重试)、**原生 `EventSource` SSE**(对话流 + 主动消息)、`getUserMedia`+自写 WAV 编码器上传 ASR、`navigator.vibrate`、**Web Audio API**(非 `<audio>`)BGM。
- 资产:`static/` 27MB,其中 **26MB 是 6 首古典 BGM mp3**(代码仅 ~1MB)。

**后端**:Spring Boot 3.3.6,**42 controller / 180 方法级端点 / 37 个 `/api/*` 域**;`ApiResponse<T>` 信封(但错误路径裸 Map、限流裸 JSON = **3 套契约**);**session 鉴权**(`HttpSession` LOGIN_USER_ID,`JwtAuthenticationFilter` 名不副实);SSE 双通道(对话两步握手 + 主动流);ASR `POST /api/asr/transcribe`(25MB `getBytes()` 全量入堆);bucket4j 限流进程内;5 个 `@Scheduled`。

---

## 2. 跨平台技术选型(矩阵)

| 方案 | 代码复用率 | 原生能力(推送/麦克风/后台音频) | 性能(本场景) | 成本 | 可上架 Store/Play | 视觉保真 |
|---|---|---|---|---|---|---|
| **PWA** | ~100% | 弱-中(iOS 推送受限、后台音频受限) | 足够 | **极低**(+2 文件) | ✗(Play 可 TWA,Store 不行) | 100% |
| **Capacitor** ⭐ | ~95% | **强**(APNs/FCM/麦克风/后台音频插件齐) | 足够 | **低** | **✓ 双端** | ~100% |
| React Native | ~30%(逻辑)/<5%(UI) | 强 | 好 | 高 | ✓ | 需重做 |
| Flutter | ~0% | 强 | 过剩(无 Canvas 可发挥) | 极高 | ✓ | 需重做 |
| 纯原生 | 0% | 最强 | 最好 | 极高(×2 代码库) | ✓ | 需重做 |

**推荐:主路线 Capacitor;零成本前置 PWA。** 理由:前端是纯静态+原生 EventSource+Web Audio+DOM/SVG/CSS 动画+已响应式 → 恰是 Capacitor/PWA 最佳输入;最大不确定项"Canvas 星空"经核实**不存在**,故 RN/Flutter 的核心卖点(Skia/可复用组件树)**没有着力点**。Capacitor 用 ~95% 复用换来"可上 Store + iOS 原生推送 + 后台音频",恰好补齐 PWA 在 iOS 的两个硬短板;增量成本只是工程脚手架+少量插件。**明确不推荐 RN/Flutter/纯原生**:用 10× 成本换 0 体验增量。

---

## 3. UI/UX 可移植性

| 资产 | PWA/Capacitor | RN/Flutter |
|---|---|---|
| 莫兰迪色板/设计 token(50+ CSS 变量) | 直接用 | 重定义为 JS/Dart 常量 |
| 字体 Noto Serif SC/LXGW/Cormorant | 直接用(打包本地字体更稳) | 需 link/注册字件 |
| 暗色模式 `body.dark-star`+`prefers-color-scheme` | 直接用(可桥接系统级) | 重做主题切换 |
| CSS transition/keyframes 动画(`star-breathe`/`nebula-drift`) | 直接用 | 用 reanimated/implicit 重表达 |
| 记忆星空 SVG+DOM | 直接用 | `react-native-svg`/`CustomPaint` 重写 |
| 触控适配(48px/按压反馈/触屏滚动) | 已就绪直接用 | 重做 |

**一句话**:PWA/Capacitor 下整个视觉系统(色板/token/字体/暗色/动画/星空/粒子/触控)**几乎 100% 保真、零重做**;RN/Flutter 下几乎全部重新表达。

**关键移动 UI 挑战**:① SSE 打字机效果——PWA/Capacitor WebView 原生支持 EventSource 零改动,RN 需社区库;② 长对话列表虚拟滚动(现状未实现,Web 系成本最低);③ 键盘遮挡(已有 sticky 布局,Capacitor+`safe-area-insets` 基本到位);④ 手势(Capacitor 有 `@capacitor/app` 返回键钩子);⑤ 暗色/无障碍(Capacitor 免费继承)。

---

## 4. 后端 API 与认证适配

### 4.1 API 面
- 180 端点 / 37 域,统一 `ApiResponse<T>` 信封,session-scoped。
- **移动端问题**:① **零分页**(45 处 `ApiResponse<List<>>` 无界列表,慢信件收件箱/对话消息长期积累 → 首屏拉全量是反模式);② **弱类型请求体**(`CapsuleController`/`MemoryController`/`AuroraChatController` 用 `Map<String,Object>` body → Swift/Kotlin 强类型 struct 无法直接建模);③ 状态转换用 POST 非 PATCH(风格问题)。
- **方向**:薄适配(保持端点+移动端自带 cursor)/ 分页切片层(`?cursor=&limit=`,MyBatis-Plus 已在)/ DTO 收口 BFF。

### 4.2 认证(移动端头号问题)
- 现状:`AuthController` 登录仅 `session.setAttribute`,**不返回任何 token**;CSRF disabled;**无 Spring Session/Redis/OAuth2**;`BaseController.currentUserId(HttpSession)` 42 controller 几乎都注入;限流也 key 在 session。
- **问题**:原生 App 不天然管 cookie;SSE/EventSource 在移动端库对 cookie 透传不一致;session 粘性(多实例必坏)。
- **方向(推荐 B)**:改 **JWT/Bearer**(jjwt 0.12.5 vestigial 已在 pom,直接用)——登录签 access+refresh、filter 解析、`currentUserId` 重构为从 SecurityContext 取;token 吊销需黑名单(Redis/DB);**C 叠加**:微信/QQ/Apple 社交登录(`User` 加 openid/provider 列,复用 B 的 token 签发;Apple Sign-In 是 App Store 上架硬性要求)。

### 4.3 契约统一
- **3 套契约**:成功 `ApiResponse{success,code,message,data}`、错误裸 Map{success,error,message,status,timestamp}、限流 429 裸 JSON 字符串。强类型客户端解构失败,要 3 套 decoder。
- **无版本前缀**(无 `/api/v1`)→ 移动 App 发版后旧版本长尾共存,任何 breaking change 打挂旧版本。
- **方向**:统一信封(GlobalExceptionHandler 也返回 ApiResponse)+ 加 `/api/v1` 前缀(越早越便宜,42 controller 路径一次性结构搜索)+ 引 springdoc-openapi 生成 schema 供移动端代码生成。

---

## 5. 🔴 推送与后台保活(最大架构重构)

> 现有 proactive 模型在移动端必须从"长连接 SSE 推"重构为"服务端 @Scheduled 决策 + FCM/APNs 推送 + 端上点击拉起"。

**现状(Web)**:`ProactiveDeliveryChannel.java:19` 进程内 `Map<userId,Set<SseEmitter>>`;`AuroraProactiveJob` 每 90s 全表扫用户 tick;**用户无活跃 emitter 时 `push()` 静默 return**(`:36-40`),但调用方**永远写 `sentAt=now()`**,离线推送被记为审计行但既不入队也不补发;`SseEmitter(0L)` 永不超时;**零推送基础设施**(grep fcm/apns/firebase/device-token 全 0)。

**移动端根本性变化**:iOS/Android 后台数秒~数分钟即掐 socket;杀进程后 SSE 不存在 → 现有 push **100% 静默丢弃**。长连接保活在移动端不可行(Doze/App Standby/iOS Background 限时)。

**方向(选项)**:
- **设备 token 注册表**(新表 `device_registration`,App 启动拿 FCM/APNs token 后 `POST /api/device/register`,多设备多 token)——当前最缺的基础设施。
- **推送通道**:海外 FCM(Android)+ APNs(iOS);**国内 FCM 不可达,需厂商通道**(华为/小米/OPPO/vivo Push + 个推/极光/友盟统一 SDK)——国内 Android 推送的现实复杂度。
- **投递出口重构**:`ProactiveDeliveryChannel` 改双通道——在线(前台 SSE/WebSocket)走 SseEmitter;离线(无 emitter)→ 写 `push_outbox` → 异步 worker 调推送 SDK。**修复"sentAt=now 但未送达"的审计谎言**,引入 `delivered_at`/`status`。
- **离线 outbox + 补发**:用户重新上线 pull "missed" proactive(弥补 `ic-proactive-client.js` 盲重连无 catch-up);配合 §6 的 `since/updated_at` 同步接口。
- **proactive 预算重新校准**:`IntensityPolicy` 的 ACTIVE=8/COMPANION=12/ALIVE=无限 在"每条=系统通知"语义下太激进(触发通知疲劳、权限被吊销),建议主动推送限 1-3 条/天,其余降级"打开 App 拉 in-app feed"。
- **静默时段强制**:复用 `QuietWindowResolver` 4 层静默窗,夜间一律不推(已支持)。
- **@Scheduled 决策层零改动**:5 个任务继续在服务端跑;变的只有"决策结果如何送达设备"。

---

## 6. 离线与本地存储

**现状**:纯在线架构,零 Service Worker/Cache API/`navigator.onLine`;localStorage 只存偏好;无 ETag/since 增量接口;无分页/无清理(历史单调累积);**音频永不入库**(DB 无 blob),离线缓存天然纯文本体积极小;**`updated_at` 全表自动维护**(`BaseEntity`+`MybatisMetaObjectHandler`)——增量同步的好底座;导出/删除服务端原语已就绪(`GET /api/user/export`、`DELETE /api/user/account` 密码确认,跨 ~25 表级联)。

**方向**:
- **本地库**:Drift/Room(SQL 范式,迁移成本低,可镜像 54 表子集)/WatermelonDB(RN 离线优先)。Realm 已不推荐、Hive 停更。
- **离线消息 outbox**:`pending_message` 表 + 指数退避 flush;**服务端必须新增幂等键**(当前 `AuroraChatController` 无幂等键——弱网重试会重复消息)。
- **增量同步**:复用 `updated_at`,加 `?since=<iso>&limit=` 到记忆/对话/profile;配 `ShallowEtagHeaderFilter` 几乎零成本。
- **冲突**:画像/记忆 last-write-wins + 软冲突提示(用户修正 `UserCorrection` 已有实体);AI 写入字段服务端权威。
- **离线降级清单**:可读=对话/记忆/画像/情绪时间线/慢信件/待办;可写(入队)=发消息/日记/共鸣体/待办;**不可用**=ASR(LLM/ASR 依赖在线)、LLM 新回复、实时天气(有 manual fallback)。

---

## 7. 语音 ASR 移动端策略

**现状**:录音→上传→服务端转写;默认 MiMo(base64 data-URL 塞进 chat-completions JSON,魔数嗅探**只接受 WAV/MP3**);GLM 备选**硬编码 `audio/wav`+`filename=audio.wav`**;25MB `getBytes()` 全量入堆(2× 堆峰值);无 VAD/无声静默裁剪/无时长上限;`/api/asr/transcribe` 与 `/api/diary/transcribe-audio` **重复入口**。

**移动端坑**:移动录音默认 **AAC(M4A)/AMR/Opus**,服务端一律标 `audio/wav` 转发 → 非 WAV 字节大概率报错/乱码(**头号坑**);弱网 25MB 单 POST 几乎必败。

**方向(推荐 C 混合)**:**端上优先**(iOS `SFSpeechRecognizer`/Android `SpeechRecognizer`/Sherpa-ONNX/Whisper.onnx,省流量+低延迟+离线+隐私不出端)→ 失败/低置信/长音频回退服务端;**服务端 `AsrController`/`AsrClient` 接口零改动**(端上成功就不调用);若放宽 `MimoAsrClient` 的 WAV/MP3 硬校验为多格式+服务端转码;**端上必须补 VAD + 60-90s 上限**(避免撞 25MB)。隐私加分:情绪/自伤语音不出端(与 §10 危机管道协同)。

---

## 8. 音频 BGM 播放

**现状**:Web Audio API(`AudioContext`+`decodeAudioData`+`BufferSource loop`),非 `<audio>`;7 时段槽→6 首 mp3,**26MB 全部本地打包**;crossfade 是死代码(实际硬切);每次页面跳转 BGM 重新 fetch+decode+从头播;无 MediaSession(无锁屏控件);**天气音效引用了不存在的文件**(rain/storm/wind/snow 静默 no-op)。

**移动端需求**:后台播放、锁屏控件、音频焦点(让路/duck)、格式兼容、切页不中断(移动端导航天然解决 Web 痛点)。

**方向**:iOS `AVAudioSession(.playback)`+`MPNowPlayingInfoCenter`+`UIBackgroundModes:audio`;Android `AudioFocusRequest`+前台服务(`mediaPlayback` 类型)+MediaSession;Flutter `just_audio`+`audio_service`。**包体积**:26MB 打进包撑大安装包→(a)首启按需下载 (b)转 AAC 64k(古典钢琴听感损失小,体积减半) (c)预取最近 2-3 时段轨。补齐缺失音效文件、接上 crossfade。

---

## 9. 权限管理

**现状(Web)**:麦克风(`getUserMedia` 浏览器弹窗)、定位(`geolocation` 天气/日落,拒绝静默回退默认北京)、**无任何 Web Push/Notification**(站内通知是 DB 轮询)、无文件持久化、无后台。

**移动端**:iOS/Android 动态申请差异显著;**情境化申请**(首次点🎙问麦克风);**被拒降级路径**(麦克风→键盘输入,定位→manual 天气模式[已有优雅降级 `weather-system.js:58`],推送→站内轮询);写统一 `PermissionGate`(申请→被拒→降级→引导设置)。iOS `Info.plist` usage description、Android 13+ 运行时权限。

---

## 10. 🔴 危机安全管道移动化(心理健康应用最关键)

> 心理健康类 App,安全管道在移动端必须更强、更快、更深集成,而非平移。

**现状(已相当完整)**:服务端、调 LLM 之前同步预检(`AuroraAgentServiceImpl:152`/`:364`);硬关键词 23 中文+10 英文(`CrisisKeywordRule`,`text.contains` 无归一化);隐性 distress ~30 句(`DistressSignalDetector`,触发同步 LLM 复核);**4s 预算**(`SafetyReviewService`,超时三路 catch 全降级 fallback,硬关键词路径瞬时);真实热线齐全(110/120/010-82951332/400-161-9995/12320/12355);安全港页(呼吸圈+5-4-3-2-1 着陆+热线 `tel:` 链接);审计 `tb_safety_event`(仅 admin 被动拉取)。

**移动端硬缺口**:
- 🔴 **无 SOS 按钮**(`safety-harbor.html:285` 仅小 tel 链接,无悬浮急救钮/一键拨号)。
- 🔴 **无自动运营告警**(HIGH 事件只落一行 DB,靠 admin 主动查,人命关天的延迟不可接受)。
- 🔴 **POST 回退不跳安全港**(`aurora-chat.html:1092-1099` 的 `sendViaPost` 只把安全消息当普通气泡,不读 `featureTarget`——移动端走 POST fallback 看不到一键拨号页)。
- **端上无预检**(`API.safetyCheck` 死码,零调用)。

**方向**:
- **端上硬关键词预检 + 服务端权威双门**:端上内置加密下发/可热更的硬关键词快表(镜像 `CrisisKeywordRule` 子集),输入即触发"即时安全港+一键拨号"不等网络;服务端门控保留为兜底(防逆向绕过)+ distress 层 4s LLM 复核。
- **一键拨号深度集成**:原生 `tel:` 系统拨号接管(iOS `openURL(TELURL)`/Android `ACTION_DIAL`);安全港热线卡改大按钮。
- **本地 SOS(新增,Web 无)**:预设 1-3 紧急联系人(存本地 Keychain/Keystore 不上传);SOS 触发(长按/连按电源/红钮)→ 弹拨号 110/120 + 发短信/位置给联系人 + 进呼吸练习;配合 iOS Emergency SOS/Android Safety Hub。
- **后台/锁屏危机检测**:服务端 distress 判 HIGH 而用户后台 → 发**高优先级推送**(Android HIGH/iOS critical),文案温和("Aurora 想确认你还好吗,需要时 010-82951332"),点开直达安全港。
- **修复 POST 路径缺口**:无论 SSE/POST 都读 `featureTarget=safety-harbor` 强制跳转。
- **审计升级**:HIGH 事件除落库外实时推运营/on-call(webhook/IM 机器人)。
- **随访冷却**:新增轻量 @Scheduled,HIGH 后 24h/72h 温和 follow-up,复用 §5 推送出口。
- **关键词归一化**(移动+Web 共需):现裸 `String.contains` 可被繁体(`自殺`)/插空格(`自 杀`)/零宽字符绕过,建议 NFKC+去空白标点+繁简归一再匹配。

---

## 11. 平台 UX 差异(iOS vs Android)

- **导航重排**:38 页 → 4-5 Tab(对话/记忆/共鸣/我),深交互用模态;iOS 底 Tab+大标题、Android Navigation Bar+Top App Bar。
- **字体(国内关键)**:**Google Fonts CDN 在国内极慢/不稳**,移动端必须**打包子集**(LXGW WenKai 全量 ~10MB→GB2312 子集 1-2MB;Noto Serif SC 同理)。
- **深色模式**:已跟随系统(`prefers-color-scheme`),原生化即可,保留手动锁定(`ic_fixed_theme`)。
- **手势**:iOS 右滑返回、Android 14+ 预测式返回;键盘 `KeyboardAvoidingView`/SafeArea;刘海/灵动岛 `env(safe-area-inset-*)`。
- **无障碍**:VoiceOver/TalkBack 语义标签;呼吸圈动画给屏阅读器等价文本;尊重"减弱动效"系统开关(大量 motion.js 动画需降级)。

---

## 12. 隐私与合规

**现状**:BCrypt(12) 合格;cookie http-only+SameSite=Lax+`secure` 由 env 控制(默认 false);CSRF disabled(靠 SameSite);**仅 session cookie**(无 JWT);**DB 明文存 PII**(情绪/对话/画像/记忆);导出/删除已就绪(个保法被遗忘权/可携带权服务端原语满足)。

**移动端需求/方向**:
- **端上 DB 加密**:SQLCipher(iOS/Android/Flutter Drift),密钥存 Keychain/Keystore 绑生物识别。
- **生物识别锁屏**:FaceID/TouchID/BiometricPrompt(`local_auth`),切后台超时锁、启动解锁。
- **凭证**:session-cookie → 推荐真 JWT/refresh,token 存 Keychain/Keystore(非明文)。
- **导出/删除 UI**:移动端"设置"接服务端原语。
- **LLM 上游隐私披露**:对话经第三方 LLM 处理须在隐私政策披露;端上 ASR 是隐私加分。
- **合规清单**:个保法(导出/删除/最小化已有基础)、Store/Play 隐私营养标签、未成年人保护(12355 青少年热线已内置,年龄分级可加)。

---

## 13. 分发门槛(🔴 中国大陆 = 系统性门槛)

> Web 版完全没有、移动 App 绕不过去的门槛。分三层:

1. **工信部 APP 备案**(所有 App 强制,2023-09-01 起):审核 7-25 工作日;**个人备案可申请但不得商业用途**(锁死商业化);服务器须中国境内。
2. **软件著作权**(国内各大商店前置):加急 ~1.5 月 / 普件 ~4 月;**名称三方一致硬要求**(APP 备案名=商店展示名=软著名,逐字一致,否则驳回)→ 名字必须在备案/软著前定死。
3. **多商店分发**(国内安卓现实):无 Google Play,要覆盖须分别上架**华为/小米/OPPO/vivo/应用宝**(至少 5 家),每家独立账号+独立审核+各自政策;一次改动重传 5 家;部分对"社交/AIGC/心理"有额外内容安全要求。
4. **iOS 中国区**:除软著外还需 **ICP 备案号**填入 App Store Connect,元数据与备案一致。

**对比 Web/PWA**:网页部署即改即上线、零审核、无软著/APP 备案(仅网站本身 ICP 备案)、无版本长尾、无 API 兼容负担。**这是 Web 最大的结构性优势。**

**心理类审核政策(偏利好)**:现有"不提供心理诊断、不替代医生/热线"免责措辞 + 危机词拦截 + 热线资源 = 审核想看到的安全叙事雏形,**比从零开始的同类 App 更易过审**。但:① **"疗效"措辞是红线**(诊断/治疗/有效率/缓解抑郁 → 触发医疗资质要求);② AIGC 内容治理需系统化(过滤/举报/审核机制);③ 未成年人保护(建议直接定级 Teen+,放弃儿童市场);④ 心理数据 PIPL 敏感个人信息(单独同意/最小化/境内存储/跨境受限)。

---

## 14. 维护/成本/团队技能

**维护负担**:
| 维度 | Web/PWA | 原生 App |
|---|---|---|
| 改动同步 | 一处改全平台立即生效 | Web+iOS+Android 各写 |
| 发版 | 即改即上线 | 审核 1-7 天 + 用户主动更新 |
| 版本长尾 | 无 | 旧版本长尾共存,**API 必须向后兼容** |
| 热修复 | 改 HTML 即时 | 需 OTA 或重新审核(苹果对热修复严) |
| 回滚 | 秒级 | 需重新提审,旧版已流出无法召回 |

> 心理类应用的"即改即上线"尤其关键——安全策略/危机词/热线需**即时生效**,App 旧版本危机逻辑可能滞后数月(伦理风险)。

**成本**:钱不是主要矛盾(几百~几千 RMB);**时间+主体+持续行政义务**才是。隐性成本=**国内 Android 厂商推送碎片化**(集成 5 家 SDK,远超 Web 零推送);macOS runner/iOS 签名需 Mac + 证书管理。

**团队技能**:当前栈 Java/静态前端,无移动经验。最现实渐进路径=**Capacitor 包壳**(几乎零学习);RN 虽用 JS/TS 但项目是 vanilla JS 仍需学 React 组件模型;Java→Kotlin 学习曲线最低但不覆盖 iOS。招聘 RN/Flutter 工程师国内月成本不菲,对课设预算不现实。

---

## 15. 产品定位与 MVP 路径

**移动 App 相对 Web 的增量价值**(逐项审视):

| 能力 | Web/PWA | 移动 App 价值 | 对本项目是否关键 |
|---|---|---|---|
| 推送保活/提醒 | Android Web Push 可,iOS 弱 | 原生强稳定 | **中高**(睡前复盘/慢信/主动消息=核心场景) |
| 随身/零打开 | PWA 可装桌面 | App 更顺 | 中 |
| 麦克风/ASR | 浏览器可 | 原生更好 | 低(已可用) |
| 传感器/健康 | 基本不能 | 可访问 | 低(不依赖) |
| 离线 | PWA SW 可缓存 | 原生更强 | 低(核心实时对话) |
| 商店货架 | 靠 SEO/分享 | 应用商店 | 低(国内啃备案)+高(品牌可信) |

**结论**:移动唯一高增量=**稳定推送**。其余 Web/PWA 基本覆盖。

**三档定位(供决策)**:
- **A. Web + PWA(推荐起点)** ⭐:零审核、即时更新、SEO 可达、零安装、当前栈直接支持;放弃原生推送/商店货架。**性价比断档式最高**。
- **B. PWA + Capacitor 包壳试水**:最低成本试商店,接受套壳过审不确定性与国内备案门槛。
- **C. RN/Flutter 重写 + 全套国内资质**:真正"做 App",但这是**新产品量级工程**(需团队/预算/时间/主体持续投入),与课设体量不匹配。

---

## 16. 最小可行移动适配路径(优先级,非步骤)

若决定上移动(Capacitor 路线),按依赖顺序:
1. **认证 → JWT/Bearer**(§4.2):jjwt 已在 pom;其余一切的前提(SSE 鉴权/限流 key/推送 token 绑定)。
2. **契约统一 + `/api/v1`**(§4.3):3 套→1 套,加版本号(移动端代码生成基础)。
3. **推送网关 + device-token 注册表 + push_outbox**(§5):解决"杀进程丢主动消息",复用 `ProactiveEventLog`+`/aurora/proactive/check`+`quietHours`。
4. **危机管道移动化**(§10):端上预检+一键拨号+本地 SOS+HIGH 实时告警+POST 路径修复。
5. **分页 + 增量同步**(§4.1/§6):`IPage`+cursor+`since`+ETag。
6. **ASR 混合 + 格式修复**(§7):端上优先,放宽 `MimoAsrClient` WAV/MP3 硬校验,补 VAD。
7. **前端 Capacitor 化 + 字体子集 + 音频按需**(§2/§8/§11):包壳、字体打包、BGM 转码/按需下载。
8. **端上加密 + 生物识别**(§12):SQLCipher+Keychain/Keystore。

**关键改造集中在**:`JwtAuthenticationFilter`/`BaseController.currentUserId`/`ApiResponse`+`GlobalExceptionHandler`+`ApiRateLimitFilter`(契约)/`ProactiveDeliveryChannel.push`+`AuroraProactiveJob`(推送)/`GlmAsrClient`+`AsrController`(ASR)/`safety-harbor`+危机路径(安全)。**无 redis/spring-session/oauth/FCM —— 全部绿地新建,依赖与改造面清晰。**

---

## 17. 一句话总评
**移动化的最优解不是"重写一个 App",而是"PWA 立即拿 80% 价值 + Capacitor 在需要时低成本补齐 iOS 推送/上架"**。前端已具备优秀的移动起点(响应式+触控+设计 token+无 Canvas 负担),真正的工程量在后端三件事——**推送/后台保活重构、认证 JWT 化、危机管道移动化**;真正的障碍在中国大陆分发的行政门槛(软著+备案+多商店)。原生 App 是"确认要长期商业化"那天才该启动的新产品工程,当前阶段 PWA 是性价比断档的选择。
