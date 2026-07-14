# M1-DATA-POC-001 — PostgreSQL + pgvector 48 小时 PoC

```yaml
status: EVALUATED
workstream: backend-data-architecture
depends_on: [M1-BASE-001]
timebox: 48h
base_sha: 2c6e78fbd2134e816e17f40de8ef52a30c11354b
human_gate: not-required-current-L1-authority-selects-postgresql
evidence_dir: evidence/m1/M1-DATA-POC-001/
```

## 目标

用隔离、可删除的 PoC 回答 PostgreSQL + pgvector 是否应成为 M1 的单一事实源；不在结论前迁移 55 张生产表，不建立双写。

## Owned paths

- `poc/postgres-pgvector/**`（新增、隔离）
- `docs/adr/ADR-*-database-of-record.md`
- `evidence/m1/M1-DATA-POC-001/**`
- 不得修改现有生产 schema 和业务 Mapper，除非先扩大包并获批

## 数据切片

- conversation：session、message、support plan、feedback；
- memory：item、evidence、revision、embedding；
- governance：consent、retrieval audit、retention；
- async：outbox event、AI job、job attempt。

## 实施任务

1. 使用 Flyway 建立 PoC schema；
2. 验证 MyBatis CRUD、事务、JSONB、时间、枚举和批处理；
3. 生成 10k 与 100k memory_item 合成数据；
4. 对带 `user_id + consent + retention` 过滤的向量检索测 p50/p95/p99；
5. 证明 filter 在数据库查询中生效，禁止应用层事后过滤；
6. 估算 RDS Singapore 最小 staging 成本、备份、加密和扩展；
7. 写单选 ADR：采用 PostgreSQL，或 M1 保留 MySQL；不得结论为双事实源。

## 验收

- D-03 通过且所有原始 SQL、计划、数据规模、硬件/实例信息可复现；
- Flyway 从空库重复成功；
- 事务失败无部分写入；
- ADR 明确迁移、核对、回滚、删除 PoC 的方案；
- 48 小时到点必须给出选择，不能以“继续研究”无限延期。

## 验证命令

具体命令由 PoC README 固化，至少包含：

```powershell
docker compose -f poc/postgres-pgvector/compose.yml up -d
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -f poc/postgres-pgvector/pom.xml verify
docker compose -f poc/postgres-pgvector/compose.yml down -v
```

## 回滚

PoC 可整体删除。若不采用 PostgreSQL，保留 ADR 与性能证据，删除运行资源；现有 MySQL 继续作为唯一事实源。

## 执行结论（2026-07-15）

- 隔离 PoC 已完成，未修改现有生产 schema、Mapper 或产品语义；
- 空库、V1 到 V2 升级、重复启动、事务回滚、MyBatis 类型与批处理契约均通过；
- 10k 与 100k 数据集的所有者、同意范围、状态与保留期过滤均在 PostgreSQL 内执行，100k p95 低于 150 ms 门禁；
- `docs/adr/0002-postgresql-system-of-record.md` 单选 PostgreSQL 16 + pgvector 为目标生产事实源，禁止双写；
- 该结论只关闭选型 PoC。现有 60 表的 Flyway 基线、全量迁移、核对和生产切换仍是 DATA-POSTGRES 的后续工作，因此总验收保持 `IN_PROGRESS`；
- 原包的人工选型门禁已被当前 L1 权威目标中明确的 PostgreSQL 目标取代；外部 RDS 创建和费用批准仍受 `HG-PRODUCTION-ACCOUNTS` 约束。
