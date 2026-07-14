# M1-DATA-APP-001 — 主应用 PostgreSQL / Flyway 生产基线

```yaml
status: EVALUATED
workstream: backend-data-architecture
depends_on: [M1-DATA-POC-001]
base_sha: f1aad5903b826637ea9768849df30a7f97cd6afa
human_gate: false
evidence_dir: evidence/m1/M1-DATA-APP-001/
```

## 目标

把已通过选型门禁的 PostgreSQL 16 + pgvector 接入主应用：以 Flyway 管理完整 60 表生产基线，禁止生产继续使用 H2/MySQL 或运行时补表，并在真实 PostgreSQL 容器和证书校验连接上验证启动。此包只改变数据与生产工程基线，不改变 Aurora、人格演化、用户画像、共鸣体、匹配、慢信或星空的产品语义。

## Owned paths

- `pom.xml`
- `src/main/resources/application*.yml`
- `src/main/resources/db/migration/postgresql/**`
- `src/main/java/com/innercosmos/config/ProductionStartupGuard.java`
- `src/main/java/com/innercosmos/config/Schema*Initializer.java`
- PostgreSQL / production guard 测试
- 数据转换、备份和生产镜像验证脚本
- 本包文档、证据和验收账本

## 实施结果

1. 从当前 `schema.sql` 确定性生成 PostgreSQL Flyway V1：60 表、21 外键、58 identity、65 个显式索引，并启用 pgvector。
2. `prod` 与新增 `postgres` profile 使用 PostgreSQL JDBC 和 Flyway；默认 H2 开发模式显式关闭 Flyway。
3. 生产启动守卫拒绝非 PostgreSQL、非证书校验 TLS、关闭 Flyway或启用旧 SQL init 的配置。
4. 9 个旧运行时 DDL initializer 在 Flyway 启用时不装配，避免双重 schema owner。
5. Testcontainers 验证空库安装、重复迁移、完整表/索引/外键/identity 对账、唯一约束，以及主应用注册登录和持久化。
6. 生产镜像在 `prod` profile 下通过自签 CA 的 `sslmode=verify-full` 连接 PostgreSQL，Flyway v1 建表并通过健康检查。
7. PostgreSQL 备份脚本改为 `pg_dump` custom format；MySQL profile/驱动仅保留为迁移来源兼容，不再是生产目标。

## 验收结论

- 主应用生产 schema ownership、空库安装、重复启动和容器启动验证已完成。
- `mvn clean verify`：655 tests，0 failures/errors/skips；SpotBugs 0 findings。
- 独立 pgvector PoC：7 contract tests + 1 benchmark test 通过；100k p95 1.286 ms，小于 150 ms 门槛。
- DATA-POSTGRES 仍为 `IN_PROGRESS`：尚未取得并迁移获授权的实际 MySQL 数据快照，也未完成逐表行数/关键字段哈希/业务抽样核对和回滚演练。
- 未删除、降级或重构任何创新产品能力；创新语义保持不变。

## 验证命令

```powershell
.\mvnw.cmd -B clean verify
.\mvnw.cmd -B -f poc\postgres-pgvector\pom.xml -Pbenchmark verify
.\scripts\verify-production-image.ps1 -Image inner-cosmos:postgres-main
.\scripts\assert-test-baseline.ps1 -MinimumTests 613
.\scripts\scan-secrets.ps1
bash -n scripts/backup/backup.sh
git diff --check
```

## 后续工作

在获得经过授权的迁移来源后，执行一次性快照迁移、逐表 reconciliation、关键业务旅程抽样和可恢复回滚演练。禁止通过长期双写替代该步骤。
