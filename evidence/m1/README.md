# M1 Evidence Protocol

M1 证据按 `evidence/m1/<package-id>/` 保存。只提交脱敏、可复核的小型证据；`target/`、生产数据、Secret、P0 原文和大型原始日志不得入库。

## 状态词

所有 acceptance 条目只能使用：`NOT_STARTED`、`IN_PROGRESS`、`PASS`、`FAIL`、`BLOCKED`。没有可重复证据时不得标记为 `PASS`。

## 每个工作包的最小清单

每个目录必须包含 `manifest.yml`，字段如下：

```yaml
schema_version: 1
package_id: M1-XXX-000
status: IN_PROGRESS
source_sha: 40-character-git-sha
branch: branch-name
recorded_at: 2026-07-14T00:00:00+08:00
environment:
  os: Windows
  java: exact-version
  maven: exact-version
  spring_boot: exact-version
commands:
  - command: exact command without secrets
    result: PASS
    artifact: relative/path/to/redacted-output.txt
    sha256: lowercase-hex
changes:
  - concise summary
human_gates:
  - owner and current status; never include credential values
known_limitations:
  - limitation
review:
  status: PENDING
  reviewer: UNASSIGNED
rollback: concise rollback procedure
```

`source_sha` 表示被验证的实现提交。每个 `artifact` 必须存在并记录 SHA-256；如果只保留 Surefire XML 汇总，应记录汇总文件或可重复的统计脚本输出。人工门禁只能由实际执行者签字，Agent 不得替人标记完成。

## Acceptance ledger

[acceptance-ledger.yml](acceptance-ledger.yml) 是 M1 机器可读验收台账。ID 必须唯一，状态变化必须附 evidence 路径与 reviewer；长期愿景和 H2/H3 工作仍保留在权威文档中，但不计入本轮 PASS。
