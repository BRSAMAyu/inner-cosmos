# Original User Request

## 2026-07-22T23:56:53Z

对 Inner Cosmos 项目进行全方位的深度审查与隐患扫描，挖掘隐藏极深、需深刻思考才能发现的系统级缺陷、逻辑漏洞、性能/并发隐患、安全与隐私漏洞，以及与产品目标/对齐文档之间的深层断层。

Working directory: d:\code\inner cosmos
Integrity mode: development

## Requirements

### R1. 全链路架构与对齐审查 (Architectural & Spec Alignment Audit)
深度对比 `对齐文档/`、`goal-objective.md` 与实际后端（`src/`）及前端（`web/`）实现，找出表面上看似完成但实际缺少关键边界校验、状态机转换遗漏或与极速/慢社交设计初衷冲突的深层逻辑缺陷。

### R2. 后端并发、事务与存储隐患扫描 (Backend Vulnerability & Concurrency Audit)
针对 Spring Boot 3.5.x + PostgreSQL/pgvector + Redis + Flyway 架构，审查高并发场景下的竞态条件、数据库事务隔离/死锁风险、pgvector 向量检索性能与退化问题、内存泄漏以及分布式缓存与数据库双写一致性漏洞。

### R3. AI 交互、数据隐私与安全边界审查 (AI Safety, P0-P3 Privacy & Prompt Injection)
审查 AI 代理（Aurora、Capsule、ThoughtShredder 等）的 Prompt 注入风险、脱敏逻辑（DataMaskingService）是否能在复杂多变场景下严格保障 P0 隐私不泄露到 P2/P3 社交层、安全过滤器（SafetyBoundaryFilter）边界失效可能。

### R4. 前后端交互与端到端异常处理扫描 (Frontend & E2E Fault Tolerance Audit)
审查 Vite/React 19 前端与后端 REST/SSE 接口交互中的长连接断连重连、SSE 内存泄漏、未捕获异常、状态不同步以及极致慢社交 UI 交互中的隐性 UX 坑点。

## Acceptance Criteria

### 深度报告与验证证据
- [ ] 产出一份无伪造、具名指明代码具体文件与行号的深度缺陷分析报告（含缺陷场景复现推演）。
- [ ] 每一个发现的漏洞均包含 Root Cause、危害评估（Impact Level）及精准的修复/重构建议。
- [ ] 验证报告中无泛泛而谈的模板化建言，聚焦项目实际代码与业务逻辑。
