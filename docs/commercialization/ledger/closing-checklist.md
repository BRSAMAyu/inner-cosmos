# COMMERCIAL-CN 收尾清单 — next_action 三分类审计快照

- 生成日期：2026-09-13（分类快照，非完成证明——见 §5 诚实边界）
- 审计对象：`docs/commercialization/ledger/commercial-cn-ledger.yml`（64 work_packages + 13 sub_gates + 4 open_questions）
- 审计方法：逐条读取 next_action/note 原文，按依赖词分类；对 CP-03/04/06/19/20/22/23/40/46/47 十个关键包交叉核对 `evidence/commercial-cn/` 交付记录内容；对全部 43 个台账引用的 delivery-record 路径做了存在性核对（全部存在）。
- 本文件是本次审计唯一新增文件；台账本体与其他文件未做任何改动。

---

## 1. 汇总

### 1.1 三分类计数（work_packages 64 个）

| 分类 | 计数 | 包列表 |
|---|---|---|
| 含可编码残余（本会话可写代码/测试/结构化落盘并本地验证） | **26** | 见 §2；其中 13 个为纯可编码（CP-07/10/14/15/17/23/24/26/29/31/33/34/35），13 个为"编码半边 + operator 半边"混合（CP-03/08/09/11/18/21/28/32/38/39/40/43/45） |
| 纯 operator 残余（外部解锁，无可编码残余） | **38** | 见 §3 |
| next_action 分项已被后续增量覆盖（从行动项剔除） | **6 个包含此类分项** | CP-07（2 段）、CP-11（1 段）、CP-15（1 段）、CP-20（1 段）、CP-23（1 段）、CP-28（1 段），明细见 §1.3 |

- sub_gates 13 个：**全部**收口条件为 operator 门禁（60A=双专家签字、60C=真人读屏/弱网真机/独立研究、其余为真实研究/交易/法定程序/官方回执/外部复核）。其中 CP-60A、CP-60C 的首个工程增量已交付并各有一份 delivery-record。
- open_questions 4 个：全部属 operator 决策域（OQ-01 细分←CP-02 访谈、OQ-02 主云 PoC←CP-37、OQ-03 运营主体←CP-05、OQ-04 支付渠道选择←CP-45/46）。
- 16 个包无 next_action 字段（11 个 IN_PROGRESS 仅 note 记明剩余门禁：CP-51/52/55/56/57/58/59/61/62/63/64；5 个 NOT_STARTED：CP-37/42/53/54/60 父包）。

### 1.2 逐包汇总表

分类代号：**[A]**=可编码增量；**[B]**=operator 门禁；**[C]**=已被后续增量覆盖；**[A+B]**=两者皆有（A 为行动项，B 单列 §3）。

| id | 状态 | next_action 摘要 | 分类 | 证据 / 建议动作 |
|---|---|---|---|---|
| CP-01 | IMPLEMENTED | 独立复核抽查迁移表；外部访谈属 CP-02 | [B] | 独立复核是人工审阅动作；evidence CP-01 存在 |
| CP-02 | IN_PROGRESS | 所有者批准后执行访谈并出细分决策记录 | [B] | 依赖真人访谈/招募/预算（note 明示 BLOCKED_EXTERNAL） |
| CP-03 | IMPLEMENTED | 独立复核；CP-45 接入支付事件 | [A+B] | A：K3 发射器未接线（`MetricCode.java` 仅有枚举，CP-45 两份记录均未提 K3）→ §2-1；B：独立复核 |
| CP-04 | IMPLEMENTED | CP-17 接入后执行 BASE-SINGLE-KERNEL 基线与红队回放 | [B] | 依赖真实 Provider 账户；对拍 harness 已由 CP-23/claim-control 记录交付 |
| CP-05 | IN_PROGRESS | 所有者确认未知资产；法务核定许可矩阵 | [B] | 商标/字体/学校权利属人工确认；docs/commercialization/compliance/cp05-entity-ip-rights-checklist.md 为 DRAFT 工作底稿 |
| CP-06 | IN_PROGRESS | CP-05 确定属地后填受理机关；法务复核判断表 | [B] | 属地=公司注册决策；cp06-regulatory-route-design.md §3 问题单明示依赖 |
| CP-07 | IN_PROGRESS | web 同意中心页随 CP-09/10；VOICE 接线随 CP-27；版本重同意触发器 | [C]+[A] | C：同意中心页已由 CP-09 记录交付、VOICE 门已由 CP-27 记录交付；A：版本重同意触发器 → §2-16 |
| CP-08 | IN_PROGRESS | 心理专家/法务审阅；使用时长提醒随 CP-11/26 | [A+B] | A：使用时长提醒（CP-08 记录明列"未含"，代码无实现）→ §2-17；B：专家审阅 |
| CP-09 | IN_PROGRESS | J01 onboarding 整合；中文文案审校随访谈结果 | [A+B] | A：`web/src/components/OnboardingGuide.tsx` 整合 → §2-14；B：文案审校依赖访谈结果 |
| CP-10 | IN_PROGRESS | J01 权限理解题与 onboarding 整合；补 VERIFIED_ID 显示 | [A] | CP-13 服务端链路 2026-09-06 已交付，前端显示已解锁 → §2-14（与 CP-09 合并做） |
| CP-11 | IN_PROGRESS | 3D 退化列表、键盘焦点与 200% 文本审计、三运营商弱网真机 | [A+B]+[C] | A：3D 退化列表与 200% 自动化合同 → §2-15；C：焦点契约已由 CP-12 第一增量（ConsentRequestDialog）部分覆盖；B：弱网真机 |
| CP-12 | IN_PROGRESS | CP-12A 真人无讲解研究（15 人，人工门） | [B] | 记录明示"200% 真机/对比度实测/真人研究是 CP-12A 人工研究门" |
| CP-13 | IN_PROGRESS | 真实通道合同核验后接入；微信/Apple 登录随商店包 | [B] | 真实身份通道合同与商店账号 |
| CP-14 | IN_PROGRESS | by-id 全量接线；CAPSULE_RUNTIME purpose；缓存键授权版本 | [A] | 全部可编码 → §2-2 |
| CP-15 | IN_PROGRESS | 资产清单（缓存/对象/导出/推送/Provider 副本）+data.retracted.v1 幂等消费者 | [A]+[C] | C：消费者已由 CP-39 记录交付（v1 schema 校验+inbox 回执+dedup 幂等）；A：派生资产清单与清理接线 → §2-11 |
| CP-16 | IN_PROGRESS | 管理员 MFA 随身份通道；独立渗透清单 CP-63A 前 | [B]（含可选 A） | MFA 终态依赖身份通道；渗透**执行**属 CP-63A 外部方。可选 A：渗透范围清单文档/本地 TOTP 骨架 → §2-25 |
| CP-17 | IN_PROGRESS | 合同版本填实；预算/并发/deadline 统一治理；429/流中断负测 | [A] | 全部可编码（429/中断可用模拟 Provider 负测）→ §2-3 |
| CP-18 | IN_PROGRESS | 连续性对用户可见的撤回开关；真机弱网开屏拉取时序验收 | [A+B] | A：撤回开关 → §2-13；B：真机弱网验收 |
| CP-19 | IN_PROGRESS | 真实 provider 上 SINGLE vs DUAL 内容增益评分 | [B] | 依赖真实 Provider；对拍 harness 与预算压力 fixture 已交付（CP-18/CP-23/CP-12 三份记录） |
| CP-20 | IN_PROGRESS | 专业审阅案例冻结+夜班值班演练；Docker 恢复后复跑 PG 基线 | [B]+[C] | C：PG 基线已在 Docker 恢复后由 CP-41/45/46/47/62 多次实机复跑（计数演进至 47/105/98/28）；B：专业审阅与值班演练 |
| CP-21 | IN_PROGRESS | 前端冲突刷新 UI（CP-11 联动）；真实 LLM 摘要下阈值校准 | [A+B] | A：`expectedVersion` 冲突的前端呈现 → §2-12；B：真实 LLM 校准 |
| CP-22 | IN_PROGRESS | 真实 provider 向量质量校准 | [B] | 依赖真实 Provider；五个增量已交付（含标签评测集与时间窗硬约束） |
| CP-23 | IN_PROGRESS | 信念变化时间线视图；非作者评审招募材料（CP-12 联动） | [C]+[A] | C：时间线视图已由 CP-22/labeled-retrieval-bank 记录交付（web 732/732）；A：招募材料 → §2-23 |
| CP-24 | IN_PROGRESS | 周报证据与缺失说明；一条纠正贯穿全部视图的旅程测试 | [A] | `WeeklyReviewV2Service` 既有 → §2-4 |
| CP-25 | IN_PROGRESS | 评测执行与 7/30 日轨迹真人审阅（注册表窗口） | [B] | 评测执行依赖真实 Provider；真人审阅依赖真人 |
| CP-26 | IN_PROGRESS | 静默窗/改期/锁屏脱敏与重复领取负测（J07）随 web 联动 | [A] | `WakeIntentServiceImpl`/`WakeIntentDeliveryJob` 既有 → §2-5 |
| CP-27 | IN_PROGRESS | 真实 ASR/TTS 接入；打断/来电/首音时延窗口 | [B] | 真实供应商合同+真机窗口 |
| CP-28 | IN_PROGRESS | 技能清单理论依据/skill kill switch；专家审阅（外部门） | [C]+[A]+[B] | C：kill switch（停用门）已由 CP-60A 记录交付（SkillDisarmamentTest）；A：原三技能理论依据补全 → §2-24；B：专家审阅 |
| CP-29 | IN_PROGRESS | 编译失败不替换稳定版本负测；授权快照覆盖/冲突解释 | [A] | `CapsuleServiceImpl` 编译路径 → §2-10 |
| CP-30 | IN_PROGRESS | 保真协议执行（来源用户偏好≥60%/50% 接受门） | [B] | "来源用户"评价需真人 |
| CP-31 | IN_PROGRESS | 分享/导出/复制链的标识传播与缓存清理 | [A] | aiGenerated 目前仅 Aurora 回复链路，分享/导出为空白 → §2-9 |
| CP-32 | IN_PROGRESS | 相似/互补/意外模式召回排序+可解释共同点；接受门实验 | [A+B] | A：匹配模式与解释 → §2-8；B：接受门实验需真实用户 |
| CP-33 | IN_PROGRESS | 收件方读回执选择；跨时区/到期未到负测 | [A] | readReceipt 代码全仓无命中，绿地 → §2-6 |
| CP-34 | IN_PROGRESS | 关系互动回顾（替代温度分）；纠错需双方同意流程 | [A] | 可复用 CP-59 RelationQualityReport 设施 → §2-7 |
| CP-35 | IN_PROGRESS | 禁言/移交/解散规则与历史可见范围；主持审核容量门 | [A] | `SocialServiceImpl` 既有邀请/屏蔽底座 → §2-7 |
| CP-36 | IN_PROGRESS | 内容安全供应商分层队列；值班表与响应 SLA 运营化 | [B] | 供应商合同与真实排班（记录明示"外部门+CP-48/50"） |
| CP-37 | NOT_STARTED | （无；主云 PoC 未启动） | [B] | 主云账户/选型；OQ-02 |
| CP-38 | IN_PROGRESS | 真实集群演练（CP-50A 门禁）；KEDA 按队列年龄接入 | [A+B] | A：KEDA ScaledObject 清单 → §2-20；B：集群演练 |
| CP-39 | IN_PROGRESS | 真实 PG 空库/旧库升级与 N-N-1 并行演练（operator 门禁）；未知事件 DLQ 看板 | [A+B] | A：DLQ 看板 → §2-18；B：真实 PG 演练（台账原文括注 operator 门禁） |
| CP-40 | IN_PROGRESS | OTel 贯穿 HTTP/SSE/Provider/检索；多 Pod Redis 预算；账单对账看板 | [A+B] | A：OTel 插桩 → §2-19；B：多 Pod Redis 依赖集群（记录明示 per-pod 下近似）、真实账单 |
| CP-41 | IN_PROGRESS | 厂商 REST 接线与密钥=operator 渠道合同门禁；真机矩阵=operator 门禁 | [B] | 台账原文双"operator 门禁"；传输层与凭据门已就位 |
| CP-42 | NOT_STARTED | （无；iOS 中国区） | [B] | Apple 开发者账户/真机 |
| CP-43 | IN_PROGRESS | Chrome/Edge/Safari 支持矩阵实测；PWA 更新与桌面签名验证 | [A+B] | A：本地多浏览器自动化 smoke 腿 → §2-22（带能力边界）；B：桌面签名验证需证书、真机实测终态 |
| CP-44 | IN_PROGRESS | 身份冻结/渠道合同/HarmonyOS PoC（operator 门禁） | [B] | 台账原文括注 |
| CP-45 | IN_PROGRESS | 真实持牌渠道下单/预创建与证书=operator 门禁；价格版本表与订单过期策略后续批次 | [A+B] | A：价格版本表+过期策略（台账明示"后续批次"）→ §2-21；B：持牌渠道证书 |
| CP-46 | IN_PROGRESS | IAP 渠道服务器通知接线（需真实渠道账号）；真实续费/退款率回填 CP-55 | [B] | 依赖真实渠道账号与真实交易数据；四个增量含续费提醒排程均已交付 |
| CP-47 | IN_PROGRESS | 真实渠道验签适配；完整结算周期抽样对账 | [B] | 验签适配需生产证书体系；"完整结算周期"依赖真实结算周期——必须归 operator |
| CP-48 | IN_PROGRESS | 帮助中心与工单系统真实接线、真实排班与演练回填=operator 门禁 | [B] | 台账原文括注 |
| CP-49 | IN_PROGRESS | 发布流程实际执行（tag 推送）与镜像签名密钥策略=operator 门禁；真实 NVD 扫描需 CI 注入 key | [B] | 台账原文括注 |
| CP-50 | IN_PROGRESS | CP-50A 真实 staging 200 并发 SSE/24h 浸泡/故障注入与 50B 真实交易腿（operator 门禁） | [B] | 台账原文括注；k6 harness 已就位 |
| CP-51 | IN_PROGRESS | （无 next_action；note：receipt/signature operator-only） | [B] | 51A/51B 子门全部 NOT_STARTED，法定程序与官方回执 |
| CP-52 | IN_PROGRESS | （无 next_action；note：模板签字/可用均为 operator 事实） | [B] | 五方签字与发布决策 |
| CP-53 | NOT_STARTED | （无；品牌与增长素材） | [B] | 依赖 CP-02 访谈结果与合规审校 |
| CP-54 | NOT_STARTED | （无；真人队列研究） | [B] | 60–100 人研究 |
| CP-55 | IN_PROGRESS | （无 next_action；note：DRAFT 下 operator 字段一律 null） | [B] | 实验执行需真实预算/渠道许可/用户 |
| CP-56 | IN_PROGRESS | （无 next_action；note：市场输入未取得标待验证） | [B] | 数值类条目依赖市场输入与真实指标（可选低价值 A：从仓库证据回填"失败样本/未关闭事项"条目） |
| CP-57 | IN_PROGRESS | （无 next_action；note：90 日真人队列为 operator 门禁） | [B] | 台账原文括注 |
| CP-58 | IN_PROGRESS | （无 next_action；note：来源用户评价/B2B 为 operator 门禁） | [B] | 台账原文括注 |
| CP-59 | IN_PROGRESS | （无 next_action；note：90 日双方确认为 operator 门禁） | [B] | 台账原文括注 |
| CP-60 | NOT_STARTED | （父包；子门见下） | [B] | 60A 关门=双专家签字；60B 全 operator；60C 关门=真人读屏/弱网真机/独立研究 |
| CP-61 | IN_PROGRESS | （无 next_action；note：独立签字/旅程串联为 S5 operator 门禁） | [B] | 台账原文括注 |
| CP-62 | IN_PROGRESS | （无 next_action；首增量导出/导入已交付） | [B] | 剩余为停服运营程序（真实演练/对外承诺），属运营动作 |
| CP-63 | IN_PROGRESS | （无 next_action；note：独立渗透/红队执行与回执为外部专业方门禁） | [B] | 台账原文括注 |
| CP-64 | IN_PROGRESS | （无 next_action；note：真实留存付费退款数据与创始人决策为门禁） | [B] | 台账原文括注 |

sub_gates（13）：CP-12A/12B（试点研究/真实交易）、CP-50A/50B（真实压测/交易腿）、CP-51A/51B（法定程序/官方回执）、CP-54A/54B（真人队列）、CP-60A（双专家签字）、CP-60B（真机/渠道）、CP-60C（真人读屏/弱网真机/独立研究）、CP-63A/63B（外部安全隐私复核）——全部 [B]；60A/60C 已交付首增量（同一份记录 evidence/commercial-cn/CP-60/2026-09-13-skills-and-accessibility/）。

### 1.3 "已由后续增量覆盖"明细（从行动项剔除）

| 包 | 过期分项 | 被哪条覆盖（证据） |
|---|---|---|
| CP-07 | "web 同意中心页随 CP-09/10" | CP-09 记录 evidence/commercial-cn/CP-09/2026-09-06-consent-center-web/（同意中心页+角标，web 708/708） |
| CP-07 | "VOICE 接线随 CP-27" | CP-27 记录 evidence/commercial-cn/CP-27/2026-09-06-voice-skill-gateway/（VOICE_PROCESSING 同意门） |
| CP-11 | "键盘焦点"（部分） | CP-12 记录第一增量 ConsentRequestDialog 焦点契约（该记录自认只覆盖可自动化合同面） |
| CP-15 | "data.retracted.v1 幂等消费者" | CP-39 记录 evidence/commercial-cn/CP-39/2026-09-12-outbox-reliability/（v1 schema 校验+inbox 回执+dedup append 幂等） |
| CP-20 | "Docker 恢复后复跑 PG 基线" | Docker 已恢复（当前 28.5.1 在运行）；CP-41/45/46/47/62 记录均含 PG 基线实机计数（最新 47 迁移/105 表/98 身份列/28 链） |
| CP-23 | "信念变化时间线视图" | CP-22 目录 labeled-retrieval-bank 记录之"CP-23 第五增量"（web 732/732 绿） |
| CP-28 | "skill kill switch" | CP-60A 记录之停用门（SkillDisarmamentTest：禁用后新运行拒绝、记忆零变化、审计保留、兄弟技能不受波及） |

---

## 2. 会话内可编码增量清单（按价值排序）

排序依据：收入正确性 > 安全边界 > 治理深度 > UI/文档补全。每项均可本地以既有测试设施验证（H2 全量 + Docker PG 基线当前可用）。注意：交付后**不得**自改台账状态——台账更新属下一批次人工/主线程动作。

1. **K3 支付事件发射器接线（CP-03 × CP-45 联动，收入指标闭环）**
   现状：`src/main/java/com/innercosmos/service/metric/MetricCode.java` 已定义 `PAYMENT_CAPTURED`/`REFUND_SETTLED`，但主代码零发射点；CP-45 两份 delivery-record 均未提及 K3（交叉核对确认）。
   动作：在 `src/main/java/com/innercosmos/payments/PaymentOrderService.java`（支付成功授予权益处）与 `src/main/java/com/innercosmos/payments/channel/ChannelCallbackIngestService.java`（PAYMENT_SUCCEEDED/REFUND_SUCCEEDED 入账处）挂钩 `service/metric/MetricEventServiceImpl`，锚定业务周（复用 CommercialMetricFunnelTest 已有的跨周断言样式）；扩 `CommercialMetricFunnelTest` 支付/退款合成样本。
2. **CP-14 敏感边界 by-id 全量接线 + CAPSULE_RUNTIME purpose + 缓存键授权版本**
   复用 `src/main/java/com/innercosmos/service/privacy/SensitiveDataBoundaryServiceImpl`（统一守卫已建）；扫 `controller/` 全部按 id 读 P1/P2 资源的端点逐一接线，越权矩阵负测扩列；为画像缓存键加入授权版本维度。
3. **CP-17 网关治理三件套**
   `ai/gateway/GatewayCallLedger.java` 合同/备案版本字段填实（结构化枚举而非自由文本）；预算/并发/deadline 统一治理——以 `StructuredAiService.callObserved` 咽喉为唯一拦截点（CP-40 ProviderSpendGuard 同模式）加 per-call deadline 与并发闸；429/流中断负测（Mock Provider 注入故障响应，fail-closed 断言）。
4. **CP-24 周报证据与缺失说明 + 纠正贯穿旅程测试**
   `src/main/java/com/innercosmos/service/WeeklyReviewV2Service.java` 周报条目补证据引用与"缺失说明"字段（诚实呈现无数据维度）；新增一条"纠正贯穿画像/星空/信念画廊全部视图"的旅程测试（对齐 StateMatrixAndPortraitAndPersonaTest 风格）。
5. **CP-26 J07 静默窗/改期/重复领取负测与锁屏脱敏**
   `service/impl/WakeIntentServiceImpl.java` + `scheduler/WakeIntentDeliveryJob.java` 补静默窗、改期、重复领取幂等负测；锁屏通知脱敏文案接 web 通知组件。
6. **CP-33 收件方读回执选择 + 跨时区/到期未到负测**
   readReceipt 为全仓空白（已验证无命中）：新增收件方回执偏好（实体列+API）与 `web/src/components/LettersInbox.tsx` 开关；寄件回执隐私既有测试同型扩负测。
7. **CP-34 关系互动回顾 + CP-35 群治理**
   CP-34：以 CP-59 `RelationQualityReport`（回轮深度/Wilson CI）为底座做关系互动回顾视图，替代温度分；纠错需双方同意流程（状态机+契约测试）。
   CP-35：`service/impl/SocialServiceImpl.java` 补禁言/移交/解散/历史可见范围规则与主持审核容量门（结构化容量台账+契约测试）。
8. **CP-32 匹配模式召回排序与可解释共同点**
   `service/ResonanceMatchStrategy.java` 扩相似/互补/意外三模式召回与排序，解释字段进 VO（发现硬过滤三层已锁定，本项是其上的一半）。
9. **CP-31 分享/导出/复制链的标识传播与缓存清理**
   aiGenerated 目前仅存在于 Aurora 回复链路（`AuroraChatController`/`AuroraReplyVO`）；为共鸣体分享/导出/复制链补显式标识与缓存失效。
10. **CP-29 编译失败保稳定版本负测 + 授权快照覆盖/冲突解释**
    `service/impl/CapsuleServiceImpl.java` 编译路径：失败不替换稳定版负测；授权快照覆盖语义与冲突解释文案。
11. **CP-15 撤回派生资产清单与清理接线**
    结构化落盘五类派生资产（缓存/对象存储/导出/推送/Provider 副本）清单，把 CP-39 已建的 `data.retracted.v1` 消费者扩展到逐条清理（消费者本体已覆盖，勿重复实现）。
12. **CP-21 前端冲突刷新 UI**
    `expectedVersion` 乐观并发冲突（409）在 `web/src/components/BeliefGallery.tsx`/`PortraitClaimsPanel.tsx` 编辑面的"他人已更新→刷新"呈现。
13. **CP-18 连续性可见撤回开关**
    `service/continuity/SessionContinuityService(+Impl)` 加属主开关；`web/src/components/AuroraOpeningContinuity.tsx` 尊重开关（关=不显示开屏卡片，后端仍诚实记录）。
14. **CP-09/CP-10 J01 onboarding 整合 + 权限理解题 + VERIFIED_ID 显示**
    `web/src/components/OnboardingGuide.tsx` + `web/src/newUserJourney.ts`：整合渐进同意与权限理解题（J01）；me/account 面补 VERIFIED_ID 徽章（消费 CP-13 既有链路，服务端已交付）。
15. **CP-11 3D 退化列表视图 + 200% 文本缩放自动化合同**
    `web/src/components/MemoryStarfield.tsx` 增加"可达列表"降级呈现（与情绪编码开关闭锁语义一致）；200% 缩放自动化合同（ContrastTokenAudit/NonAuthorUsabilityContract 同型静态+渲染断言）。
16. **CP-07 版本重同意触发器**
    `entity/AnalysisConsent.java` 注释已预告 "versioned re-consent … lands with CP-07"；`service/consent/ConsentCenterServiceImpl` 加版本号变更→重同意门（无静默沿用旧授权）。
17. **CP-08 使用时长累计提醒**
    CP-08 记录明列"未含"；会话时长累计与提醒（AuroraConversation 层 + 服务端时长口径），与 CP-26 静默窗共享配置面。
18. **CP-39 未知事件 DLQ 看板**
    `event/reliable/JdbcOutboxRepository` DEAD 行的 admin 端点/页面（CP-40 联动），呈现毒/未知事件与 replayDead 入口。
19. **CP-40 OTel 贯穿**
    以 micrometer-tracing/OTel spans 贯穿 HTTP→SSE→Provider（`StructuredAiService.callObserved` 已是咽喉）→检索（MemoryRetrievalService）；本地以捕获 span 断言验证。
20. **CP-38 KEDA 按队列年龄伸缩**
    `deploy/k8s/extensions/keda/`（目录已存在）补按 outbox 队列年龄的 ScaledObject；本地仅 kustomize build 静态校验——真实缩放验证仍属 §3 集群门禁。
21. **CP-45 价格版本表 + 订单过期策略**
    `PaymentOrderService` 目录价单一来源扩价格版本表（改价不追溯既有订单断言）+ 订单过期状态机（过期单回调拒收负测）。
22. **CP-43 浏览器支持矩阵自动化腿（带能力边界）**
    本地 Playwright 多浏览器 smoke 消费 web-pwa checklist 的可自动化项；Chrome/Edge/Safari 实测覆盖以本机可用浏览器为准——签名验证与真机终态仍属 §3。
23. **CP-23 非作者评审招募材料**
    docs/commercialization/research/ 新增非作者评审招募文案（对齐 cp02-problem-interview-protocol.md 的预注册纪律），供 CP-12A 15 人研究使用。
24. **CP-28 原三技能理论依据补全**
    skill/ 注册表为既有三项技能补 DOI 验证 evidence（CP-60A 已为新三技能建立逐字段范本）。
25. **CP-16 渗透测试范围清单（pre-CP-63A）+ 可选本地 MFA 骨架**
    渗透范围/资产/断言清单文档（CP-63A 前置）；本地 TOTP MFA 骨架（注意：台账把 MFA 终态挂在身份通道上，骨架≠终态）。
26. **CP-60C web 维度自动化单元格推进（谨慎、证据优先）**
    用 CP-12 既有自动化合同（焦点/对比度）作为证据，把 `cp60c-accessibility-matrix.yml` 中 web×（键盘/对比度）单元格推进至 AUTOMATED_PASS——契约要求必须带证据，无证据一律不动；真人维度保持 NOT_STARTED。

---

## 3. operator 人工门禁清单（按解锁主体分组）

每项写明解锁后 agent 能接着做什么。所有"接线/回填"类动作的工程底座均已交付（对应 delivery-record 见 §1.2 证据列）。

### 3.1 法务与组织（公司/主体/监管程序）

| 门禁 | 解锁物 | 解锁后 agent 可做 |
|---|---|---|
| CP-05 | 公司注册、商标/域名、字体与学校权利确认、法务核矩阵 | 权利链清单从 DRAFT 升签字版；CP-06 受理机关列回填；OQ-03 关闭 |
| CP-06 | 属地确定（随 CP-05） | 程序台账 §2 逐行填受理机关；属地问题单回填 |
| CP-51A/51B | 备案/登记/安全评估/算法备案的提交与官方回执 | 回执登记进法定程序台账（RegulatoryLedgerContractTest 结构已就位、无 PASS 态防伪） |
| CP-13 | 真实身份通道合同 | 接入真实核验通道（V39 升级链已 fail-closed 就位） |
| CP-44 | 渠道合同、身份冻结、HarmonyOS PoC 决策 | 合同要素填入 store-submissions checklist；HarmonyOS 立项或否决记录 |
| CP-01/CP-03 独立复核 | 复核人时间 | reviewed_at 落位（工程上无可自证动作） |
| CP-52 | 五方签字与发布决策 | 签字/日期写入 Go/No-Go 台账（契约已强制 NO_GO 优先） |
| CP-64 | 创始人商业决策 | decision ledger 结论落位（BUSINESS_VALIDATED 永不可由 agent 写） |

### 3.2 金融与支付

| 门禁 | 解锁物 | 解锁后 agent 可做 |
|---|---|---|
| CP-45 | 持牌渠道商户号/证书 | 接真实下单/预创建 API 与验签配置（内核+沙箱适配层已就位） |
| CP-46 | IAP 渠道账号（App Store/国内商店） | 服务器通知接线到 V44 权益状态机（七态归一已就位） |
| CP-47 | 生产验签证书体系 + 完整结算周期数据 | 真实渠道验签适配；结算周期抽样对账报告（对账内核已就位） |
| CP-55 | 实验预算与渠道许可 | DRAFT 实验推 EXECUTING，按 CP-03 字典收终点（K2 决策带已钉死） |
| CP-56 | 市场输入/投资人对接材料 | data-room 数值类条目回填（PENDING_VALIDATION 字段结构已就位） |
| OQ-04 | 支付渠道选择决策 | 确定 CP-45 适配优先级与 CP-12B 交易腿范围 |

### 3.3 云与基础设施

| 门禁 | 解锁物 | 解锁后 agent 可做 |
|---|---|---|
| CP-37 | 主云账户（ACK/TKE 选型） | IaC 落地、托管 PG/Redis 迁移验证（DATA-POSTGRES/REDIS 重验项承接）；OQ-02 关闭 |
| CP-38 | 真实集群 | 角色争抢/租约/排空/预算封顶演练（四角色 kustomize 派生已就位）；KEDA 真实缩放验证 |
| CP-39 | 真实 PG 环境窗口 | 空库/旧库升级与 N-N-1 并行演练（outbox 可靠性合同已就位） |
| CP-40 | 多 Pod 集群 + 真实账单 | Redis 共享预算改造；账单对账看板回填真实数据 |
| CP-49 | tag 推送权限、镜像签名密钥、CI 的 NVD key | 执行发布流程实跑；真实 CVE 扫描（命令合同已就位，无 key 路径已验证） |
| CP-50A/B | staging 环境 + 真实交易腿 | k6 200 并发 SSE/24h 浸泡/故障注入与交易腿实跑（两份 k6 harness 已就位，阈值草案待实测冻结） |

### 3.4 渠道、设备与真人研究

| 门禁 | 解锁物 | 解锁后 agent 可做 |
|---|---|---|
| CP-41 | 五厂商密钥/合同 + 真机矩阵窗口 | 接线厂商 REST 网关（凭据门 EXTERNAL_CREDENTIAL_GATE 已 fail-closed 就位，替换占位即通）；真机矩阵证据回填 |
| CP-42 | Apple 开发者账户/真机 | iOS 工程与系统能力接入 |
| CP-43 | 桌面签名证书 | PWA 更新与签名验证实测回填 |
| CP-02 | 所有者批准+招募+预算 | 30 次访谈执行与细分决策记录（工具包已冻结） |
| CP-12A | 15 人无讲解研究窗口 | 研究证据落盘并关闭 CP-12（自动化合同已就位） |
| CP-53 | CP-02 访谈结果 | 品牌与合规增长素材产出 |
| CP-54A/B | 60–100 人队列（≥35 日）与成熟队列 | K1/K2/G-SAFE/G-TRUST 真实数据按字典出报告 |
| OQ-01 | 随 CP-02 | CP-09/10 文案定稿与 CP-55 渠道优先级 |

### 3.5 专家、真实 Provider 与外部验收

| 门禁 | 解锁物 | 解锁后 agent 可做 |
|---|---|---|
| CP-04/19/22/25 | 大陆真实 Provider 账户（合同+备案核实） | 跑 BASE-SINGLE-KERNEL 基线与红队回放（CP-04）；SINGLE vs DUAL 内容增益评分（CP-19，对拍 harness 已就位）；向量质量校准（CP-22）；人格评测执行（CP-25） |
| CP-08/20/28/60A | 心理专家与法务审阅/双专家签字 | 签字写入 skill-expert-review.ledger（12 行 PENDING 结构已就位）；案例冻结与政策剧本定稿 |
| CP-27 | ASR/TTS 供应商合同+真机 | 真实语音链路接入（VOICE_PROCESSING 同意门已就位）；打断/来电/首音时延窗口实测 |
| CP-30/58 | 来源用户保真评价/B2B 用途合同 | 保真协议接受门执行（≥60%/50% 区间判据已预注册） |
| CP-36 | 内容安全供应商合同 | 分层审核队列接线（V42 案件后台已就位） |
| CP-48 | 工单系统、真实排班、演练窗口 | 值班/演练数据回填运行手册（骨架契约已锁定无伪就位态） |
| CP-57/59 | 90 日真人队列/双方确认 | 长程评测与社区健康快照回填（评测集与指标已冻结） |
| CP-60B/60C | HarmonyOS 真机/渠道；真人读屏/弱网真机/独立研究 | 矩阵单元格带证据推进（60 格全 NOT_STARTED 结构已就位） |
| CP-61 | 独立验收组 | 签收记录落位（愿景 77 节点闭包护栏已就位） |
| CP-62 | 停服演练窗口 | 迁移/停服演练数据回填（导出/导入 roundtrip 已实机验证） |
| CP-63A/B | 外部渗透/红队与独立复核方 | 报告与回执登记 review-calendar（EXPIRED 阻断契约已就位）；整改验证 |
| CP-12B | J12 真实交易与全端体验 | 首发验收证据落位 |

---

## 4. 审计发现：台账与 evidence 的不一致（仅记录，不改台账）

1. **3 份 delivery-record 落盘但未被台账引用**（note 描述了这些增量，指针却停在旧记录）：
   - `evidence/commercial-cn/CP-22/2026-09-12-labeled-retrieval-bank/`——CP-22 条目只引用 CP-18 的记录，但其 note 的 ③标签评测集 ④否定/时间约束 ⑤时间窗硬约束实际落盘于此（该记录同时含 CP-23 第五增量）；
   - `evidence/commercial-cn/CP-23/2026-09-12-claim-control/`——CP-23 的属主控制/前端面板两增量落盘于此，条目未引用；
   - `evidence/commercial-cn/CP-45/2026-09-13-channel-sandbox-adapters/`——CP-45 第一增量（渠道沙箱适配层，test_command 里 ChannelCallbackSandboxContractTest 的来源）未被条目引用（delivery_record 被 order-catalog 覆盖而非追加，与 CP-46 的 delivery_record2/4 追加风格不一致）。
   内容均真实存在，属指针滞后，不构成虚报。
2. **23 个包声明 evidence_dir 但目录不存在**：其中 4 个 NOT_STARTED（CP-37/42/53/54）属预期；19 个 IN_PROGRESS 包的证据实际借用其他包目录或 docs/commercialization/compliance/（CP-05/06）。属记账模式差异，非证据缺失。
3. **CP-03 "CP-45 接入支付事件" 联动断链**：CP-45 已交付两个增量，但两份记录均未接 K3 发射器，台账也未再提醒该联动（见 §2-1，本清单将其列为最高价值可编码项）。
4. **台账引用的 43 个 delivery-record 路径（41 evidence + 2 docs）逐一核对全部存在**；抽查的 10 个关键包（CP-03/04/06/19/20/22/23/40/46/47）记录内容与 note 声称的增量逐项对上，未发现"note 自称覆盖但证据文件不存在"的情形。

---

## 5. 诚实边界声明

- 本清单是 **2026-09-13 对台账 next_action 的分类快照**，不是任何包的完成证明；"可编码"表示存在明确、可本地验证的工程残余，不表示该残余已做完或做完即可关门。
- 所有包的关门条件以台账与蓝图为准：大量 [A] 项做完后其包仍是 IN_PROGRESS（如 CP-60C 自动化单元格推进后仍需真人维度；CP-40 OTel 落地后仍需集群侧）。
- [B] 分类依据是 next_action/note 原文中的外部依赖词（真实/真人/真机/证书/账号/合同/回执/签字/演练/结算周期等），分类不做任何替代性推断（例如不以"本地 TOTP 也能做 MFA"改写 CP-16 的台账语义，仅以可选项注明）。
- 本清单不修改台账与任何证据文件；§4 的不一致仅作记录，处置权在台账维护者。
