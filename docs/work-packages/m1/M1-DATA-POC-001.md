# M1-DATA-POC-001 — PostgreSQL + pgvector 48 小时 PoC

```yaml
status: BLOCKED_BY_BASE
workstream: backend-data-architecture
depends_on: [M1-BASE-001]
timebox: 48h
human_gate: choose-single-source-database
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
