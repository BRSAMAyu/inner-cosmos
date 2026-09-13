# CP-48 值班/升级运行手册骨架（检查点 32）

蓝图 CP-48（客服、退款与用户沟通）的可编码部分：值班台账与升级路径**结构化落盘**，
并像商店提审清单一样用契约测试锁定结构诚实性。

## 交付物

### 1. 结构化运行手册 `docs/commercialization/operations/`

- **`oncall-rotation.yml` 值班台账**：7 个可排班角色登记（duty_crisis_responder /
  crisis_lead / duty_support / support_lead / payments_engineer / privacy_officer /
  founder_on_duty）+ 5 个值班 slot——**危机 3 班（0-8/8-16/16-24）与一般客服 2 时段
  （工作日 10-19 / 周末 14-18）分成两套 kind，互不顶替**（蓝图"必要危机值班与一般
  客服时段分开"）；GENERAL 必须公示真实服务时间且**不得承诺 24 小时人工**；CRISIS
  必须挂安全资源入口（与 /api/safety/resources/catalog 对齐）；每 slot 有交接工件路径
- **`escalation-paths.yml` 升级路径**：四级严重度（SEV1-safety 危机 / SEV2-payment
  支付资金 / SEV3-rights 数据权利 / SEV4-general 一般）各带 ack_minutes、
  update_minutes、**严格递增的升级阶梯**（首级 after_minutes=0）；on_timeout 写明
  超时自动升级；privacy_rule 写明各级数据可见边界；SEV2 内置"DISPUTED 订单先暂停
  该渠道收款"（CP-47 恢复规则）、SEV3 内置"删除卡住先核对 CP-15 独立撤回日志水位"
  （CP-50A 规则）
- **`ticket-categories.yml` 工单分类**：9 类覆盖蓝图全部家族（错误收费/扣款未解锁/
  退款/退订误解/数据权利/删除卡住/封禁申诉/骚扰举报/安全危机）；**默认
  MINIMAL_METADATA（客服只看最小账户/订单元数据，不看倾诉）**；仅骚扰举报与安全
  危机是 AUTHORIZED_SENSITIVE 且必须给出授权程序（按 CP-36/CP-20 程序授权）；每类
  有 SLA 反馈时限与用户侧进展可见承诺（"用户能查询进展"）
- **`drill-scenarios.yml` 演练矩阵**：6 个演练覆盖蓝图验收清单（错误收费+扣款未
  解锁 / 退订误解+退款 / 删除卡住+权利请求 / 误封+骚扰 / 月度夜班危机演练(CP-20
  联动) / 停服演练(CP-50B 联动)）；`last_drilled` 只能由 operator 真实演练后回填日期
- **`README.md`**：谁能填什么的诚实边界（coding agent 只能推进结构到 IN_PROGRESS；
  排班就位/演练通过只能以 evidence 外部凭证落位）、过载预案（限量获客→人工补偿，
  绝不虚构 24 小时人工）

### 2. 结构契约 `OnCallRunbookContractTest` 1/1（随全量测试套件强制）

- CRISIS 与 GENERAL 两 kind 必须并存；值班/阶梯/演练 owner 的每个角色必须在 duty_roles
  登记表内（不存在引用幽灵角色）
- 阶梯 after_minutes 严格递增且首级为 0；四级严重度齐全
- 工单分类挂接已定义严重级；AUTHORIZED_SENSITIVE 必须带授权程序；蓝图 9 分类齐全
- 演练 covers 只能引用真实分类或保留字 platform_outage；蓝图 8 个演练家族全覆盖
- status ∈ {PENDING, IN_PROGRESS}——**不存在 FILLED/PASS 伪就位态**，与商店提审清单
  同一纪律：就位/通过只能来自外部真实凭证

## 诚实边界（operator 人工门禁）

- 真实姓名/电话/排班落位、帮助中心上线、真实演练执行与 last_drilled 回填、真实
  工单系统接线——均需 operator 外部完成；本骨架是证据落盘位置与结构约束，不是
  "已有值班"的证明

## 回归

- 后端 **1612/1612 绿**（+1 契约测试），2 个 Docker 门控跳过；Web 742/742 绿，tsc 干净
