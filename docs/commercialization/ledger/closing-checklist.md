# COMMERCIAL-CN 收尾清单 — 终局快照（v2）

- 生成日期：2026-09-15（取代 2026-09-13 初版快照，初版归档于 closing-checklist-2026-09-13-archived.md）
- 审计对象：ledger（64 work_packages + 13 sub_gates + 4 open_questions）+ evidence/ 全部交付记录 + git checkpoint-45..51 实际落地的代码
- 方法：只读终局审计（逐项 grep 代码证据核对），叠加在飞批次现场观察；本快照以**代码为准**而非以旧清单文字为准
- 定位：完成度地图，不是完成证明（诚实边界见 §5）

---

## 1. 汇总

- 64 包：3 IMPLEMENTED（CP-01/03/04）；4 NOT_STARTED 且全为 operator 前置（CP-37/42/54/60 父包）；57 IN_PROGRESS 且工程半边基本交付
- 13 子门：全部 operator
- **工程侧不存在任何大块未开工的可编码工作**——codable 残余收敛为一小簇（见 §2）

## 2. 剩余可编码清单（按最短收尾路径排序）

| # | 项 | 状态 |
|---|---|---|
| 1 | 在飞批次落地：CP-21 belief 版本列+乐观锁（α）、CP-26 服务端推送脱敏（β）、CP-31 user-mirror 出处（δ，已交付）、CP-18 设置面重开入口+CP-39/40 DLQ 前端页（γ，已交付） | α/β 在飞，γ/δ 已交付 |
| 2 | 台账清账：evidence_dir 修复、11 条未引用记录补指针、5 处过期 next_action 刷新、本快照重生成 | **已由主线程完成（2026-09-15）** |
| 3 | CP-45 价格版本表 + 订单过期策略（§2-21，支付域最后一块 codable 核心） | 在飞（ζ） |
| 4 | CP-40 OTel 贯穿插桩（§2-19，桥已就位，callObserved/检索咽喉 span） | 在飞（η） |
| 5 | 前端消费收口批：CP-08 时长提醒 UI、CP-07 RE_CONSENT_REQUIRED 统一呈现、CP-31 aiGenerated 前端消费、CP-32 解释字段、CP-34 纠错提案 UI、CP-33 全局回执偏好、CP-35 治理端点接线 | 待做（各小项） |
| 6 | CP-43 浏览器矩阵自动化腿（playwright 多浏览器 projects + 消费 web-pwa checklist） | 待做 |
| 7 | §2-24 两份技能 manifest 补 DOI；§2-26 CP-60C web×键盘/对比度 24 单元格推进 AUTOMATED_PASS | 待做（半天级） |
| 8 | 低优先可选：CP-39 AdminActionLog、CP-32 themeOverlap 处置、CP-15 wake_intent 主体列、CP-16 TOTP 骨架、CP-62 媒体 blob 打包、CP-14 缓存键（休眠至缓存引入）、CP-34 关系标签→平台连接映射、CP-32 UNEXPECTED 节律曲线 | 可选 |

### 已从旧清单剔除的登记错误

- **§2-20 CP-38 KEDA**：按队列年龄的 ScaledObject 早已存在（deploy/k8s/extensions/keda/worker-scaled-object.yaml，ec948b7b 2026-07-23，第二触发器即 inner_cosmos_outbox_oldest_ready_age_seconds）——旧清单"补 ScaledObject"前提不成立；剩余为商业化命名空间变体（低价值）+ 真实集群验证（operator）

### 旧清单 §2 的 26 项处置对照

20 项已消化（K3 支付事件/CP-14 by-id/CP-17 网关/CP-24 周报/CP-26 静默窗/CP-33 回执/CP-34+35/CP-32/CP-31 标识/CP-29/CP-15 清理/CP-21 冲突UI/CP-18 开关/CP-09+10 J01/CP-11 星域/CP-07 重同意/CP-08 后端半/CP-39 API 半/CP-23 招募/CP-16 渗透范围——证据链 checkpoint-44..51，见各包 delivery-record）；1 项登记错误剔除（上述 KEDA）；5 项进本表 #3/#4/#5/#7。

## 3. operator 门禁（不可编码，等外部解锁）

真实 Provider（CP-04/19/22/25）、主体/属地/备案回执（CP-05/06/51A/51B）、持牌支付渠道与结算终态（CP-45/46/47，OQ-04）、主云账户（CP-37，OQ-02）、厂商推送密钥与真机矩阵（CP-41）、Apple 账户（CP-42）、真人研究全线（CP-02/12A/54A/54B，OQ-01）、专家签字（CP-08/20/28/60A）、独立渗透（CP-63A/B，scope 文档已备）、tag 推送/NVD key/镜像签名（CP-49）、staging 压测（CP-50A/B）、Go/No-Go 五方签字（CP-52）、投资人输入（CP-56）、创始人决策（CP-64）、独立验收（CP-61）、90 日队列（CP-57/59）、停服演练（CP-62 半边）、实名发起入口的渠道合同（CP-10）、模型合同备案（CP-17）。

## 4. 台账完整性核查结论（2026-09-15）

- 64 个 delivery_record 路径 100% 存在；evidence_dir 指针已全部修复到真实目录（NOT_STARTED 的 CP-37/42/54 无目录属诚实状态）
- 11 个存在但未被引用的 evidence 目录已补 delivery_recordN 指针；5 处过期 next_action 已刷新

## 5. 诚实边界

- 本快照是完成度地图：IN_PROGRESS ≠ 商业成功（蓝图 §11 原则）；BUSINESS_VALIDATED/PRODUCT_READY/CN_RELEASE_READY 永不由 agent 写入
- §2 各项完成以 delivery-record + 全量回归绿为准，不以本表勾选为准
- operator 门禁解除后如产生新的可编码工作（如真实渠道联调腿），将新增清单而非在本表静默改写
