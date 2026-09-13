# CP-52 候选 manifest 与逐渠道 Go/No-Go 结构化落盘（检查点 40）

## 交付物（`docs/commercialization/launch/`）

### 1. `candidate-manifest.template.yml` 发布候选模板

- 候选身份三要素（candidate_id / version_tag / git_sha）**绑定检查点 36 的
  release-manifest.json**（同 SHA 构建的可追溯链）
- **五方签字**（工程/法律/体验/运营/支付与恢复）一律 null——签字是真人事实
- **J01–J12 十二条跨端验收旅程**（蓝图 §3.2 逐条具名）：verdict PENDING、evidence
  null——release 设备 + 真实模型 + 国内生产镜像重走后才可回填
- **六个发布开关**（公告/状态页/客服/应用更新/回滚/暂停注册）全部 available=false
  且无证据——全部可用才允许任何渠道 GO
- `open_p0_defects: null`——零 P0 是缺陷追踪事实，不是模板默认值；abort 阈值与
  权益保留/合法回退规则写明

### 2. `go-no-go.yml` 逐渠道决策台账

六渠道 decision ∈ {GO, NO_GO}；NO_GO 带理由（当前全部指向 CP-51B PENDING）；
GO 须 decided_by/decided_at。

### 3. 契约 `LaunchGoNoGoContractTest` 2/2 —— 跨文件一致性

- **Go/No-Go 永远跑不赢提审台账**：交叉读取 `regulatory/submissions.ledger.yml`，
  任何渠道 review_outcome ≠ PASSED 或 blocks_launch=true 时 decision 强制 NO_GO
  （未通过渠道保持关闭的结构化落地）
- 渠道集合精确等于提审台账六渠道；GO 必须有决策人与日期
- 模板诚实性：五方签字空、十二旅程全 PENDING 无证据、六开关全 unavailable、
  P0 计数 null——coding agent 建骨架，一切"通过/签字/可用"来自真实演练证据

## 回归

本包 +2 测试（后端全量在本检查点集成时统一跑）；Web 未触及。

## 诚实边界（operator 门禁）

真实候选复制与演练、五方签字、J01-J12 真机重走、开关演练、灰度放量决策——
全部 operator/独立验收组事实；本结构保证的是"没有这些事实时不存在 GO"。
