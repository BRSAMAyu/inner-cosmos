# ADR-0002：PostgreSQL 16 + pgvector 作为目标生产事实源

- 状态：Accepted for migration；production cutover pending
- 日期：2026-07-15
- 决策范围：G2 / DATA-POSTGRES
- 证据：`evidence/m1/M1-DATA-POC-001/`

## 背景

当前应用以 MySQL/H2 运行，但完整产品目标要求在一个可审计的事务边界内承载对话、记忆、同意与来源、异步任务以及受策略约束的向量检索。不得为了迁移便利形成 MySQL 与 PostgreSQL 双事实源，也不得在数据库检索后才在应用层过滤所有者、同意或保留期。

## 决策

目标生产事实源单选 **PostgreSQL 16+ 与 pgvector**。Flyway 是生产 schema 的唯一迁移机制。MySQL 在切换完成前仍是当前事实源，只作为迁移来源；H2 只允许用于不声称数据库等价性的快速单元测试。禁止生产双写。

PoC 已证明四个数据族可在同一 PostgreSQL 事务与约束模型中表达：conversation、memory、governance、async。Testcontainers 契约覆盖空库安装、版本升级、重复启动、事务失败回滚、MyBatis CRUD/批处理以及 JSONB、数组、枚举、时间与 UUID。10k/100k 合成数据检索将 `user_id`、同意范围、状态、保留期和 embedding 版本全部放入 SQL，并通过查询计划验证数据库内过滤。

## 迁移与核对

后续实施包必须按以下顺序执行，未通过核对不得切换：

1. 将现有 60 表转写为可从空库安装的 Flyway 版本化基线，并为 PostgreSQL 语义差异编写契约测试；
2. 在 Testcontainers 中同时验证空库、逐版本升级和重复启动；
3. 从只读 MySQL 快照迁移到隔离 PostgreSQL，逐表核对行数、主键、外键、状态分布、时间精度、JSON 内容与抽样内容哈希；
4. 对 P0/P1/P2/P3 数据分别核对所有者、同意、来源、保留与派生关系，并验证删除/撤回传播；
5. 在维护窗口停止写入，执行最终增量、再次核对，通过后原子切换连接配置；
6. 保留 MySQL 只读回退窗口，待备份恢复演练和观察期通过后再按审批计划退役。

核对报告、差异清单和签字必须进入 DATA-POSTGRES 证据目录。应用主工程改用 PostgreSQL Testcontainers 前，本 ADR 不构成生产切换完成声明。

## 回滚

- 切换前：删除隔离 PostgreSQL 资源，应用继续只写 MySQL；
- 切换窗口内：若最终核对或健康门禁失败，恢复旧连接配置并重新开放 MySQL 写入；
- 切换后：优先从已验证的 PostgreSQL PITR/备份恢复并前向修复。只有在仍处于明确的只读回退窗口、没有产生无法回放的新写入且负责人批准时，才允许回到 MySQL；
- PoC 目录可删除，但 ADR 与测试证据保留。

## 影响与边界

- 获得 JSONB、数组、全文索引、事务 outbox 和 pgvector 的同库一致性；
- 承担 PostgreSQL 运维、扩展版本、索引调优和迁移核对成本；
- RDS Singapore 的真实创建、费用、备份保留和 KMS key 仍需 `HG-PRODUCTION-ACCOUNTS` 授权；
- 本决策不删除、降级或重构 Aurora 主动性、Self/Constitution/Emergence、人格关系演化、用户建模、共鸣体动态对话、慢信、星空或匹配语义。
