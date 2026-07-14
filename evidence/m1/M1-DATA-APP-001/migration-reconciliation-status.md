# 实际数据迁移与对账状态

状态：`PENDING_AUTHORIZED_SOURCE_EXECUTION`

本执行包已经完成目标 PostgreSQL 生产基线、Flyway schema owner、空库/重复迁移、主应用持久化和证书校验生产启动验证，但这不等同于完成现有数据迁移。

尚未执行：

- 从获授权的实际 MySQL 环境生成一致性快照；
- 将真实数据一次性加载至目标 PostgreSQL；
- 比较全部 60 表的行数、主外键完整性和关键字段哈希；
- 对 Aurora 对话/记忆、Self、用户画像、共鸣体、匹配、慢信和星空做业务抽样；
- 演练失败回滚和恢复时间。

当前仓库没有获授权生产快照或外部生产数据库访问，因此证据不得宣称 migration reconciliation 已关闭。`DATA-POSTGRES`、`D-01`、`D-02` 均继续保持 `IN_PROGRESS`。获得合法来源后应以一次性迁移与可审计对账完成收口，不启用长期双写。
