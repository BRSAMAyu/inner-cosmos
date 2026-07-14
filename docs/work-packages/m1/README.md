# M1 Work Package Queue

本目录把 `对齐文档/07-H0H1决策表与M1执行包.md` 转为可被 Agent 单独领取、实现和独立验证的工作单元。

## 当前队列

| Package | 状态 | 依赖 | 默认工作流 |
|---|---|---|---|
| [M1-GOV-001](M1-GOV-001.md) | READY | 无 | 产品/架构 |
| [M1-SEC-001](M1-SEC-001.md) | READY | 无；外部密钥轮换需人类 | 平台/安全 |
| [M1-SEC-002](M1-SEC-002.md) | READY | 无 | 后端/安全 |
| [M1-SEC-003](M1-SEC-003.md) | READY | 无 | 后端/安全 |
| [M1-BASE-001](M1-BASE-001.md) | EVALUATED_REVIEW_PENDING | GOV、SEC-001/2/3 | 验证/集成 |
| [M1-DATA-POC-001](M1-DATA-POC-001.md) | BLOCKED_BY_BASE | BASE | 数据/架构 |

同一时间最多四个包处于 `CLAIMED`。领取时必须填写 owner、worktree/branch、base SHA、开始时间；完成时填写证据路径、验证者和集成 SHA。

## 通用完成规则

- 只修改 `Owned paths`；需要扩大范围时先更新包并检查冲突；
- Builder 写测试，但不能独立批准自己的包；
- 包级命令和全量 `mvn test` 都必须成功；
- 原始证据写入 `evidence/m1/<package-id>/`；
- 不提交真实密钥、生产数据、生成的构建目录；
- 失败不是完成：保留日志，将状态置为 `FAILED`，写清下一步；
- 集成前将包分支更新到指定 base SHA，重新验证。

## 领取记录模板

```yaml
package: M1-XXX-000
status: CLAIMED
owner: <agent-or-human>
reviewer: <different-agent-or-human>
branch: codex/<package-id>
worktree: <absolute-path>
base_sha: <sha>
started_at: <iso-8601>
evidence: evidence/m1/<package-id>/
integrated_sha: null
```
