# FINAL-P0-CLOSURE-2026-07-22

## 裁决

本检查点完成四项机器可关闭的 P0：对话结束后的耐久派生投影、Aurora 任务感知记忆取证、Academy EKS 精确 Flyway schema 门禁，以及可自检的一键教师演示。它不关闭真实 Provider、真实设备、法务或生产账户等人工门禁。

实现提交：`3f75c0dee46e199b7a057e33243068def6edc8d2`。

## 实现事实

1. `DialogFinishedProjectionHandler` 取代仅写审计日志的 handler。生产 outbox 路径在一次 `JdbcOutboxRepository.complete` 事务中执行记忆抽取、画像候选与情绪时间线聚合；异常会回滚 receipt/effect 并由 outbox 重试。
2. 当 `inner-cosmos.events.outbox.enabled=true` 时，六个旧的进程内异步派生 listener 不注册，避免同一会话双重投影；缺省和专注单元测试仍可使用旧 listener 路径。
3. Aurora 上下文通过 `MemoryRetrievalService` 生成任务感知 `MemoryEvidencePack`，并让 legacy memory context 与相同 evidence IDs 对齐；检索失败时不偷偷退回重力 Top-N，避免不透明语义漂移。
4. API、worker、scheduler 三个 Academy 工作负载在启动前要求 Flyway 恰好到仓库最高版本 V20，且任何失败 migration 都会 fail closed。
5. `scripts/run-teacher-demo.ps1` 使用每次运行独享的 H2 文件、Mock Provider 与显式 Demo seed，验证 health、CSRF、登录、当前用户和 React shell；退出后清理数据库。
6. CI/Surefire 最低测试门槛从历史 613 提升到当前完整基线 923。
7. 签名 OCI 发布工作流在任何 tag 或手动发布前重新执行 Java 923 基线、前端契约/测试/构建、Secret 历史扫描和 Academy schema 门禁；`build-sign` 明确依赖 `verify`，不能从发布入口绕过普通 CI。

## 2026-07-22 机器验证

| Gate | Result |
|---|---|
| `mvnw clean test` with Java 21 + Docker Desktop | **923 tests, 0 failures, 0 errors, 0 skipped** |
| PostgreSQL 16 / pgvector / Redis Testcontainers | PASS（包含在上述全量回归） |
| `scripts/academy/validate-schema-version.ps1` | PASS：highest migration V20，3/3 workloads，fail closed |
| `scripts/academy/validate-manifests.ps1` | PASS：20 resources，0 forbidden，0 missing，3 schema gates |
| `scripts/run-teacher-demo.ps1 -Port 18080` | PASS：health、demo login/session、React shell；停止后临时数据库清理 |
| 新增定向回归 | 7/7 PASS：3 task-aware retrieval + 4 durable projection/routing |

## 仍开放的诚实边界

- 真实 Provider 的密钥轮换签字、盲评、长期质量、延迟与成本。
- Android/iOS 真机、APNs/FCM、签名、商店账户与审核。
- 新加坡 PDPA 跨境、HSA/心理专业与法务复核。
- commercial-sg 真实 AWS/DNS/支付/备份恢复与非作者运维演练。

因此本证据支持 `LOCAL_TEACHER_DEMO_READY`，不支持 `COMMERCIAL_RELEASE_PASS`。
