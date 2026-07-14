# 内心宇宙 → AWS EKS(Kubernetes)云原生可行性分析

> 本文由 4 个 agent 并行分析后汇总(12-factor 就绪度 / 有状态拆分 / EKS 基础设施 / Day-2 运维)。**只提供信息、现状、选项与权衡,不给执行步骤**——供你掌握全貌、读关键代码后自己定方案。证据附 `file:line`。
> 评估时点:2026-07-14。代码分支 `feat/run006-aurora-self-understanding`。

---

## 0. 核心结论(先读这段)

**技术上可行**:非 root 镜像(`Dockerfile` appuser:1001)、Spring Boot 内嵌 Tomcat、Actuator+Prometheus、MySQL 8.4、SSE、`${VAR:default}` env 化配置,都是标准 EKS 可承载形态。

**但两个现实约束决定成败**,且都与"写代码方式"有关、不是"装什么 AWS 服务"能解决的:

1. **当前架构本质是「单副本设计」**——进程内状态遍布全栈(HttpSession、SSE 推送注册表、对话状态、限流桶、5 个定时任务)。**直接上多副本 = 掉登录、推送丢失/重复、限流翻倍、胶囊能量被过度衰减**。多副本前必须先还一笔应用层债(session 共享 / ShedLock / 限流共享 / SSE 跨副本总线)。

2. **LLM 全部在中国大陆**(智谱 `open.bigmodel.cn`、MiniMax `api.minimaxi.com`、小米MiMo `api.xiaomimimo.com`、DeepSeek `api.deepseek.com`)。海外 AWS 区 → 跨境延迟/连通可靠性/PIPL 合规是真问题;AWS 中国区 → 延迟最低但需 ICP 备案 + 中国实体。**区位决策是整个方案形态的分水岭。**

> **一处事实勘误**:任务描述说音频 ~250MB,实测 `src/main/resources/static/audio/music/` 仅 **~26MB / 6 个 mp3**(无 flac),打进 70MB fat-jar。S3 成本几乎为零。

---

## 1. 12-Factor 就绪度(现状/差距)

| Factor | 现状 | 差距 / 上云影响 |
|---|---|---|
| **I 配置** | prod 数据源全 env 化(`application-prod.yml:12-14` `${SPRING_DATASOURCE_*}`);LLM `${VAR:default}`(`application.yml:111-149`);CORS/cookie/seed/safety 都 env 化;`.env.example` 列 12 变量 | 🔴 **3 个真实 LLM key 以默认值写进 yml 且 commit + 打进镜像**(`application.yml:125,133,141`);`application-mysql.yml:8-9` 硬编码 root/root;**限流阈值是 Java 常量不可调**(`ApiRateLimitFilter.java:30-34`);Tomcat 线程 max200、HikariCP 池大小按 profile 写死,无 `${}` |
| **II 依赖** | pom 显式版本;`eclipse-temurin:17`;无 ffmpeg/原生进程(ASR 走 HTTP) | 🟡 jjwt 依赖 vestigial(`src` 0 引用,误导安全审计);spring-boot-starter-security 偏重 |
| **III 配置/代码分离** | `@ConfigurationProperties(prefix="llm")` 结构化绑定规范(`LlmConfig.java:17`);`@Value` 仅 2 处 | 🔴 secret 与 endpoint 未真正分离(默认值=真 key);**mysql profile 静默切 mock LLM**(`application-mysql.yml:18-23`)→ "在 MySQL 环境测的功能"与 prod 行为系统性偏差 |
| **IV 后端服务** | MySQL 8.4 + Prometheus + Grafana(docker-compose);HikariCP 完整 | 🔴 **无缓存层**(无 Redis/Caffeine)、**无 MQ**(异步全 `@Async` 进程内)、**音频打进 jar**;prod `useSSL=false&allowPublicKeyRetrieval=true`(明文+公钥检索) |
| **VI 进程(无状态)** | 纯请求/响应 REST 无状态 | 🔴 **有状态部分:鉴权、限流、SSE、流式握手、对话状态、全部定时任务** → 见 §2(实质单副本) |
| **VII 端口 / VIII 并发** | 内嵌 Tomcat 8080,`EXPOSE 8080`,LB 可直接打 | 🟡 线程池全硬编码;SSE 长连占异步执行槽;纵向扩容受限 + 横向被状态阻塞 |
| **IX 易处理** | `server.shutdown: graceful` + task `await-termination 30s` 已配 | 🟡 进程内状态在 pod 死亡全丢(掉登录/SSE 断流/token 消失);无 preStop 排空 SSE;`SseEmitter(0L)` 无超时,emitter 可能滞留 |
| **X 环境等价** | 同 jar 多 profile | 🟡 dev=H2+mock,prod=MySQL+真LLM,差异巨大;**本地 green ≠ prod green** |
| **XI 日志** | prod JSON 一行式(`application-prod.yml:43-44`)+ 文件 | 🟡 **非 stdout-only**(双写文件);手搓 JSON(`%msg` 不转义引号/换行,日志带 `"` 即破坏解析);无 traceId |
| **XII 管理进程** | schema 迁移靠应用内 `SchemaM{n}Initializer` ApplicationRunner | 🟡 无独立 `db:migrate` 式管理进程;管理操作要 exec 进 web 容器;SchemaM runner 每副本每启动都跑(幂等守卫) |

**最关键 5 个阻塞点**(上云严重度):① 进程内状态遍布(单副本架构);② 5 个 @Scheduled 无分布式锁每副本全量执行;③ 两步 SSE 流式握手硬绑单 pod;④ 真 LLM key 默认值进镜像;⑤ prod useSSL=false + mysql profile 静默 mock。

---

## 2. 有状态组件识别(多副本阻断清单)

> 这是上 EKS 多副本**最核心的一节**。每个组件:**位置 → 多副本表现 → 处置方向(选项非步骤)**。

| # | 组件 | 位置 | 多副本严重度 | 处置方向 |
|---|---|---|---|---|
| 1 | **HttpSession 鉴权** | `AuthController.java:39-40`、`BaseController.java:16-22`、`JwtAuthenticationFilter.java:41-58`(类名误导,实为 session) | 🔴 **致命** | Spring Session+Redis(首选)/ Sticky ALB / 改真 JWT |
| 2 | **SSE 推送 Map** `Map<userId,Set<SseEmitter>>` | `ProactiveDeliveryChannel.java:19`,`SseEmitter(0L)` 无超时 | 🔴 高 | Redis pub/sub 广播 / Sticky / WebSocket Gateway |
| 3 | **对话状态 3 份 Map**:`streamStage`(token→上下文,60s)、`turnCounter`、`goodbyeConfirmCount` | `AuroraAgentServiceImpl.java:90-99` | 🔴 高(streamStage 跨 pod handoff 必坏) | Sticky session / Redis 外移 |
| 4 | **限流桶** bucket4j 进程内 | `ApiRateLimitFilter.java:36`(代码注释自承"swap to Redis") | 🔴 高(限流 N×、登录爆破防护失效) | bucket4j Redis / 前置 WAF rate rule |
| 5 | **token 用量** `dailyUsageStore` Map | `TokenEstimationServiceImpl.java:22` | 🟠 中(配额/计费不准) | Redis `INCRBY` |
| 6 | **A/B 分组缓存** | `ABTestServiceImpl.java:26` | 🟠 中(分组不一致) | 确定性 hash / Redis |
| 7 | **CapsuleSync 限速器** Guava RateLimiter | `CapsuleSyncService.java:54` | 🟠 中(下游速率 N×) | 分布式限流 |
| 8 | 天气/Geocoding/prompt metrics 缓存 | `WeatherContextService.java:54`、`GeocodingService.java:47`、`PromptVersionServiceImpl.java:18` | 🟡 低(仅 miss/不汇总) | 可选 Redis cache |

**一句话**:多副本真正阻断点 = #1(session 不共享)+ #2/#3(SSE 与对话状态进程内);安全控制退化 = #4(限流 N×)。

---

## 3. `@Scheduled` 多副本重复执行(必须先解决再开 HPA)

5 个任务,**每个 pod 都全量执行**(Spring @Scheduled 无分布式锁,`@EnableScheduling` 在 `InnerCosmosApplication.java:8`):

| 任务 | 频率 | 副作用 | 多副本后果 | 幂等性 |
|---|---|---|---|---|
| `AuroraProactiveJob` (`:44`) | 90s | 遍历**所有用户**主动 tick + 私人定时器 | N 份主动推送/重复触发 | 否 |
| `LetterDeliveryJob` (`:33`) | 60s | 信件 SENT→FLYING→DELIVERED + 审计日志 | 有 selectById 复查兜底,审计日志重写 | 基本幂等 |
| `CapsuleSyncRetryJob` (`:33`) | 60s | 重跑 FAILED 同步 | 抢同一批行,浪费下游配额 | 基本幂等 |
| `SessionIdleWatcher` (`:25`) | 5min | 触发告别(调 LLM) | 重复 LLM 调用 | 基本幂等 |
| `NightlyMemorySettlementJob` (`:61`) | cron 0 0 2 * * | 全表重算引力/基线 + **capsule 能量乘式衰减** | N 副本 = 能量被衰减 N 次(0.97^N) | **非幂等,有害** |

**处置方向(选项)**:
- **ShedLock**(改动最小,推荐):`@SchedulerLock` 基于 JDBC/Redis 互斥,复用 MySQL 或即将引入的 Redis。适合 LetterDelivery/CapsuleSync/SessionIdle/AuroraProactive。
- **K8s Leader Election**(spring-cloud-kubernetes 或 K8s Lease):不引外部依赖但绑定 EKS。
- **外移 K8s CronJob 单实例**:`NightlyMemorySettlementJob` 这种重计算/非幂等/每日一次的,最适合剥离成 `kind: CronJob` `completions:1`,既去重又把凌晨全表重算尖峰从业务 pod 隔离。**且衰减算法本身要幂等化**(基于日期判断是否已衰减)。

> **附带坑**:`NightlyMemorySettlementJob.java:70-71` 的分页**实际不生效**——`MybatisPlusConfig` 未注册 `PaginationInnerInterceptor`,`selectPage` 不追加 LIMIT,一次返回全表(`while(batch.size()==batchForTest)` 若全表恰 ==200 会死循环)。这是独立 bug,多副本只是放大。

---

## 4. `@Transactional` 内调 LLM(Day-2 可靠性头号技术债)

已核实 **4 处**事务方法内直接调 LLM,网络往返(可达 60s)期间 Hikari 连接全程占住不释放:

| 位置 | 超时 |
|---|---|
| `ai/capsule/CapsuleContextRegenerator.java:38,55`(`regenerate()` `@Transactional` → `llmClient.chat`) | GLM 60s |
| `ai/capsule/CapsuleSyncService.java:154,200`(`decide → regenerateOne`) | 60s |
| `ai/mode/ModeSwitchService.java:48`(`switchTo()` `@Transactional` → pushModeAcknowledgement → chat) | 60s |
| `safety/SafetyReviewService.java:74,105`(`recheckSync` `@Transactional` → structuredAiService.call) | 受 4s 预算约束(严重度低) |

**影响**:prod HikariPool `maximum-pool-size=20`;并发 20 个胶囊重生成 = 连接池全占满 = 全应用所有 DB 操作阻塞。**K8s 滚动更新放大**:SIGTERM 杀 pod 时在途事务连接被强关 → LLM 已返回但事务未 commit → 状态丢失/部分更新。

**处置方向(选项)**:把 LLM 调用移出事务边界(先调 LLM 拿结果,再开短事务写 DB);failover 链的慢调用绝不进 `@Transactional`。**扩副本前必修。**

---

## 5. 网络 / Ingress / SSE 超时

- **ALB Ingress**(AWS Load Balancer Controller,本项目默认首选,原生挂 WAF/ACM,托管自动扩)。
- 🔴 **SSE 超时必须调**:代码 `SseEmitter(120_000L)` = 120s 长连接(`AuroraAgentServiceImpl.java:355` 等);**ALB idle timeout 默认 60s → 会被提前掐断**。需调到 **≥130s**;target group `deregistration delay` 必须 > 应用 graceful shutdown(30s),否则滚动发布在飞 SSE 被丢。
- 🔴 **HTTPS 下安全 cookie 失效**:HTTPS 终止在 ALB,pod 收到 HTTP。应用须配 `server.forward-headers-strategy: native`(`application.yml` 当前**未设**)才能让 `COOKIE_SECURE`(`:20` 默认 false)和安全重定向生效。
- **WAF**:ALB 前挂 AWS WAF + **rate-based rule**——应用自带 bucket4j 限流是 per-pod 内存型(多副本翻倍),**WAF 全局前置节流正好补这个洞**(在 session 共享改造前尤其有用)。强烈建议上。
- **跨境 SSE 脆弱**:长 idle SSE 跨 GFW 易被中间盒 RST;建议应用层发 SSE keep-alive/comment 帧。

---

## 6. 🔑 LLM 出站到中国大陆(本应用上云的特殊关键点)

目标全部在中国大陆。三层考量:**延迟 / 连通可靠性 / 合规**。

### 6.1 延迟与路径
| 部署位置 → 中国大陆 LLM | 典型 RTT | 路径 |
|---|---|---|
| ap-southeast-1 新加坡 | 100-200ms+ | 公网跨境,经 GFW |
| ap-northeast-1 东京 | 150-300ms | 公网跨境 |
| us-east-1 弗吉尼亚 | 200-350ms 单程 | 长途+跨境 |
| **cn-north-1 北京 / cn-northwest-1 宁夏** | **个位数~十几个 ms** | 国内直连,不跨 GFW |

**对 SSE 流式聊天的影响**:GLM 推理 ~8s 后流式吐 token;跨境加首 token 延迟与每 token 抖动;**最长 120s 的 idle SSE 跨 GFW 本身就脆**。

### 6.2 连通可靠性(无 SLA)
- 国际区 ↔ 中国大陆公网是 best-effort,受 GFW 干扰(尤其长连接/特定域名的 TLS)。**无厂商 SLA**。
- 应用层 `FailoverLlmClient`(glm↔minimax 互备)是好设计,但 4 家都在大陆 → **同时跨境 = 共因失效**,failover 救不了跨境网络本身。
- **高杠杆动作(你来核实)**:智谱有 **z.ai 国际端点**,MiniMax/DeepSeek 部分有海外接入点。**若 provider 提供海外端点,路由"海外 app → 海外 LLM 端点"可完全绕开 GFW**。

### 6.3 合规与数据主权(心理数据 = 敏感个人信息)
- 心理健康应用处理情绪/信念/关系/记忆 → **中国 PIPL/DSL 下的敏感个人信息**。跨境传输有法定门槛(安全评估/标准合同/认证)。中国用户敏感数据存海外 AWS 再发给国内 LLM = 跨境数据流,有合规成本。
- **AWS 中国区**(cn-north-1 北京 / cn-northwest-1 宁夏):由光环新网/西云数据运营,**独立 partition**,**全球 AWS 账号不通用**;需**中国实体营业执照 + ICP 备案**;服务目录滞后全球。但 → **到国内 LLM 延迟最低、无 GFW、合规清晰**。
- **更激进的选项**:若受众就是中国用户 + 调中国 LLM,**中国本土云(阿里/腾讯/华为 ACK/TKE/CCE)可能比 AWS 更自然**——延迟更低、合规顺。AWS EKS 的优势在全球覆盖与生态;纯中国心理数据场景,它不一定是正确的云。

### 6.4 区位决策矩阵(你来定——这决定整个方案形态)
| 场景 | 推荐落点 | 关键代价 |
|---|---|---|
| 受众在中国 + 合规敏感 | **AWS 中国区** 或 **中国本土云** | 需 ICP 备案 + 中国实体 |
| 受众全球,但仍用中国 LLM | **ap-southeast-1 新加坡**;优先用各家**海外 LLM 端点** | 跨境延迟/脆弱,接受 |
| 受众全球,可换 LLM | 改**海外托管 LLM**,彻底绕开中国端点 | 失去国内 provider(成本/合规权衡) |

> LLM 是文本流量,**egress 体积小**——NAT 的 GB 费用在 LLM 路径上不是大头(真正吃钱的是 NAT 基础小时费 + 跨境延迟风险)。

---

## 7. 托管服务选型

| 组件 | 选项与权衡 |
|---|---|
| **MySQL** | **RDS for MySQL 8.4**(版本与 compose 一致,稳,Reserved 省 30-40%)vs **Aurora Serverless v2**(弹性缩容,但 MySQL 8.0 兼容**非 8.4**)。课程项目+数据小 → RDS MySQL 8.4 几乎是默认解。Multi-AZ 翻倍,看可用性。**RDS Proxy**:多副本+多 AZ 时显著降低故障切换连接报错,pod 少可选,2-3 副本+多 AZ 推荐。 |
| **对象存储 S3** | ~26MB 音频成本 < $0.01/月。价值不在省钱而在**把媒体从镜像剥离** + CloudFront 缓存。代码侧只改 `audio-system.js:20-36` url 前缀,后端零改动;需访问控制则加 `/api/audio/sign` 预签名 URL 接口。 |
| **Secrets** | 现状:key 明文进 yml/jar/git 历史。**上 EKS 必须移出镜像**:① yml 默认值清空;② 视真 key 已泄露 → 去 GLM/MiniMax/MiMo 后台 **revoke 旧 key 签新 key**;③ 注入用 **External Secrets Operator**(把 Secrets Manager 同步成 K8s Secret,GitOps 友好,推荐)或 **Secrets Store CSI driver**(volume 挂载,key 不进 etcd)。轮换:LLM key 静态 token 手动轮换够;DB 密码用 Secrets Manager 原生 MySQL 轮换。 |
| **ElastiCache Redis(多副本关键依赖)** | pom 现无 Redis/Spring Session/ShedLock。**多副本必需**:① session 共享(Spring Session);② ShedLock 锁 store;③ 限流共享(bucket4j Redis);④ SSE pub/sub 广播。规格 cache.t4g.micro(开发 ~$12/月)/ small+replica(生产 ~$25-50)。 |

---

## 8. Day-2 运维(CI-CD / 弹性 / 可观测 / 可靠性 / 安全)

### 8.1 CI/CD
- **CI 引擎**:GitHub Actions(仓库已在 GitHub,原生集成,免运维)vs CodePipeline(纯 AWS,IAM 统一但配置重)。仓库在 GitHub → GHA 自然起点。
- 🔴 **必须先加 git remote**(`BRSAMAyu/inner-cosmos`,本地 `git remote -v` 为空)GHA 才触发。
- **镜像标签**:`:git-sha`(可追溯可回滚,推荐)❌别用 `:latest`。
- **部署策略**:金丝雀(LLM 调用贵且慢,先放 5% 观察真实延迟/P99 再放大,需 Argo Rollouts / Flux+Flagger)。
- **构建优化**:BuildKit cache mounts 复用 `.m2`;音频外迁 S3 后镜像才真正瘦身(当前 jar 70MB 大头是依赖非音频)。

### 8.2 清单管理
- 当前只有 docker-compose,**无任何 K8s 清单**。
- **Kustomize overlay**(base + dev/staging/prod 三层)上手最快;若频繁参数化 → Helm。二选一别混用。环境差异主要是 LLM_MODE/DB 端点/SEED_ENABLED/COOKIE_SECURE/SAFETY_RECHECK,Kustomize configMapGenerator+patch 即可覆盖。

### 8.3 弹性伸缩
- **HPA**:CPU-based 对本应用(瓶颈在等 LLM,CPU 常年低)**基本不触发**,需**自定义指标**。
- **金矿指标**:① `llmTaskExecutor` 队列深度/活跃线程(用户排队等 AI)——但 micrometer 默认不自动注册 `ExecutorServiceMetrics` binder,需手动加;② 活跃 SSE 连接数(gauge)。经 **Prometheus Adapter** 或 **KEDA** 暴露。
- **KEDA**:比 HPA+Adapter 省心,可 scale-to-zero(夜间省节点)。
- **节点伸缩**:**Karpenter**(AWS 原生,按 pending pod 即时开机,自动选最便宜实例)优于 Cluster Autoscaler。
- 🔴 **关键权衡——扩 pod 能否提升 LLM 吞吐?部分能,但被两因素钳制**:① **LLM provider 限流**(GLM/MiniMax QPM/TPM 上限,扩 10 pod 各发 → 触发 429 → failover 级联到 mock 降级;**吞吐天花板在 provider 配额不在 pod 数**);② **NAT 出站连接**(高并发长 SSE 撞 NAT 端口耗尽)。真正受益于扩 pod 的是 SSE 并发会话数和非 LLM 的 DB/业务请求。

### 8.4 健康检查与探针
- 🔴 **`CustomHealthIndicator` 把 DB 健康度并进 overall `/actuator/health`**——若直接接到 K8s **liveness** → DB 闪断触发 pod 重启风暴。**正确映射**:开启 `management.endpoint.health.probes.enabled=true` 自动暴露 `liveness`(只 ping+内存,不含 DB)/`readiness`(含 DB)两组。
- 🟡 内存判定算法偏弱(分母用 `runtime.totalMemory()` 非 `maxMemory()`,接近 Xmx 也不触发 WARNING)。
- 🔴 **安全口**:`/actuator/metrics/**` 和 `/prometheus` 是 `permitAll`(`SecurityConfig.java:40-43`)——含 JVM/DB 池/吞吐敏感信息,**EKS 上绝不能经公网 Ingress 暴露**,只集群内 Prometheus 经 NetworkPolicy 访问。
- **startup probe 必要性高**:70MB jar + Spring Security + 55 表 schema initializer 首启动慢,Java JIT 预热 → failureThreshold×periodSeconds 给足(如 30×10s=5min)。

### 8.5 可观测性
- **Metrics**:自建 Prometheus+Grafana(compose 已有)vs **Amazon Managed Prometheus(AMP)+ Managed Grafana(AMG)**(无运维,按摄入收费,IRSA 原生)vs CloudWatch Container Insights(省事但表达力弱)。
- **Logs**:**Fluent Bit DaemonSet** → CloudWatch Logs / Loki / OpenSearch。JSON 已就绪 parse 零成本。**推荐 stdout 唯一真相源**,关掉文件输出避免双份。
- 🔴 **Tracing 全缺**(pom 无 OTel/brave/zipkin)。**本项目 tracing 价值极高**:LLM 多 provider failover 链一个请求可能串 3 个 provider,无 trace 看不出卡在哪一跳/哪次降级。加 `opentelemetry-spring-boot-starter` + `micrometer-tracing-bridge-otel` → Jaeger/Tempo/AWS X-Ray。务必把 traceId 注入 MDC+log pattern 实现"一条 trace→一串 log"。
- 🔴 **日志含 PII 风险**:`log.info("LLM attempt provider=...")` 可能打进 prompt/对话文本;心理咨询类应用日志进 CloudWatch/Sentry 前必须脱敏。

### 8.6 可靠性 / 灾难恢复
- **多 AZ**:EKS 跨 2-3 AZ 节点 + `topologySpreadConstraints`;RDS **Multi-AZ + 自动备份 + PITR**(心理数据 PITR 是刚需)。Aurora(failover 秒级+backtrack,贵但值)vs RDS MySQL 多 AZ。
- **优雅停机三件套对齐**:Spring graceful + K8s `preStop`(sleep 摘流量)+ `terminationGracePeriodSeconds`(> SSE 最长 120s + shutdown 30s ≈ 150s+)。
- 🔴 **`@Transactional`+LLM 在滚动更新时的风险**(见 §4)。

### 8.7 安全合规
- **IRSA**(pod 经 ServiceAccount+OIDC assume IAM 角色,免长期 key)/ 较新的 **EKS Pod Identity**(更简单)。
- **NetworkPolicy**(Calico/Cilium):禁止 web pod 直连彼此、DB pod 只开给 web SA。
- **镜像扫描**:Trivy(CI)+ ECR enhanced scanning。
- **Pod Security Standards**:`restricted`(non-root 已满足,补 `readOnlyRootFilesystem:true`,需日志改 stdout)。
- 🔴 **prod DB `useSSL=false`** → EKS→RDS 必须 `useSSL=true`+RDS CA。`mysql_native_password` 在 8.4 已 deprecated,Aurora 默认 `caching_sha2_password` 需适配。
- **数据合规(最重)**:PII/情绪数据落 RDS 必须 KMS 静态加密+TLS;RDS/CloudTrail 审计;数据最小化+保留期+注销级联删除(配合个保法被遗忘权);日志/错误上报脱敏。
- 🟡 **危机检测路径不能因扩容/故障静默失效**:对 `SafetyReviewService` 降级路径加显式告警,failover 到 mock 时要 visible。

---

## 9. 成本模型(定性 + 量级感,全球区)

| 项 | 性质 | 月费量级 |
|---|---|---|
| EKS 控制面 | 固定 | $73/集群(超期版本 +$0.06/h extended support) |
| EC2 节点 / Fargate | 半固定 | $50-150(EC2 2-3 节点,Spot 省 60-90%) / $60-200(Fargate,贵 20-30%) |
| **NAT Gateway** | 固定+小变动 | **$32/个 + $0.045/GB**(基础小时费是大头,LLM 文本 GB 可忽略) |
| RDS MySQL | 半固定 | $15(单 AZ t4g.micro)~ $200+(Multi-AZ small) |
| ElastiCache Redis | 半固定 | $12(微)~ $50(副本),**多副本必需** |
| ALB | 固定+LCU | $20-40(SSE 长连占 LCU) |
| CloudFront / WAF / Secrets / S3 | 变动 | $5-30 / $10-30 / ~$2 / <$0.1 |

**粗略月费量级感(全球区,不含人力)**:
- **迷你(MVP/POC)**:单 AZ、2× t3.medium、单 NAT、RDS t4g.micro 单 AZ、**无 Redis(单副本)**、无 WAF → **~$150-250/月**(⚠️ 单副本=没高可用但能跑)。
- **小(prod-light)**:2 AZ、2-3 副本、RDS small、单 NAT、ALB、Secrets、Redis 微、CloudFront → **~$300-500/月**。
- **中(prod HA)**:全 Multi-AZ、3 副本 m6i、RDS Multi-AZ、Redis 副本、WAF、CloudFront → **~$700-1200/月**。

> 最贵固定项 = EKS 控制面($73)+ NAT 基础费($32)+ RDS。对等 LLM 的轻计算应用,节点可很小。**值得问"EKS 是否过重"**——单实例 ECS Fargate 或 Lightsail 可能更便宜(部署形态决策)。

---

## 10. 需你拍板的关键决策(汇总,非步骤)

1. 🔑 **区位**(§6.4 矩阵):受众在哪?是否接受跨境到中国 LLM?——**决定整个方案形态**(全球区 vs 中国区 vs 换云)。
2. **节点形态**:EC2 MNG(省钱稳态)vs Fargate(省运维贵)。
3. **MySQL**:RDS MySQL 8.4(版本一致稳)vs Aurora Serverless v2(弹性非 8.4)。
4. 🔑 **多副本前置改造**(§11):session 共享 / ShedLock / 限流共享 / SSE 总线 —— **不做就别上多副本**。
5. **入站**:ALB idle timeout 调 ≥130s + WAF rate rule(补多副本限流洞)+ 应用补 `forward-headers-strategy`。
6. **NAT 高可用**:单 NAT(省钱)vs 多 AZ NAT(高可用)。
7. **密钥**:内置明文 LLM key 移出镜像 → Secrets Manager(+ revoke 旧 key)。
8. **可观测后端**:AMP/AMG vs 自建 Prometheus vs CloudWatch;tracing → X-Ray vs Tempo/Jaeger。
9. **部署形态**:EKS vs ECS Fargate vs Lightsail(成本敏感时)。

---

## 11. 多副本前置改造清单(应用层债,按依赖顺序)

> 这些是"写代码"层面的事,不是装 AWS 服务能解决的。基础设施能承载,但这笔债要先还。

| 优先级 | 改造项 | 依赖 |
|---|---|---|
| 🔴 P0 | **session 共享**:Spring Session + Redis(`@EnableRedisHttpSession`),或先 ALB sticky 兜底 | 引入 Redis |
| 🔴 P0 | **@Scheduled 去重**:ShedLock(JDBC 复用 MySQL 或 Redis),或 NightlyJob 剥离为 K8s CronJob | Redis 或 MySQL |
| 🔴 P0 | **`@Transactional`+LLM 解耦**:4 处移出事务边界(§4) | 无 |
| 🔴 P0 | **secret 移出镜像**:yml 默认值清空 + Secrets Manager 注入 + revoke 旧 key | ESO/CSI |
| 🟠 P1 | **限流共享**:bucket4j Redis 或前置 WAF rate rule | Redis/WAF |
| 🟠 P1 | **SSE 跨副本总线**:Redis pub/sub 广播主动推送;streamStage/turnCounter/goodbyeConfirmCount 外移或 sticky | Redis |
| 🟠 P1 | **prod useSSL=true** + `forward-headers-strategy: native` + 探针分组(liveness 不含 DB) | 配置 |
| 🟠 P1 | **PaginationInnerInterceptor 注册**(修夜间任务分页失效) | 无 |
| 🟡 P2 | **@Async 拆池**(AI 长任务 vs 事件抽取)+ queue 满改 CallerRunsPolicy;事件外移 MQ | 无/MQ |
| 🟡 P2 | **tracing**(OTel)+ `llmTaskExecutor` 指标 binder 接 KEDA | OTel |
| 🟡 P2 | token 用量/A/B 分组/限速器 Redis 化汇总 | Redis |
| 🟢 P3 | 音频外迁 S3+CloudFront;日志改 stdout 唯一;readOnlyRootFilesystem | S3 |

---

## 12. 一句话总评
**上 EKS 技术可行,但本应用的两个"出身"决定路径**:① **LLM 在中国大陆**——区位决策(全球区跨境 vs 中国区备案 vs 换本土云)是分水岭,先把这件事想清楚;② **当前代码是单副本设计**——多副本前必须先还 session/ShedLock/限流/SSE 总线这笔应用层债。基础设施层(EKS/RDS/Redis/ALB)都是标准件,真正的难点在应用层的无状态化改造和 LLM 区位权衡。
