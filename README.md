# Inner Cosmos 内宇宙

Inner Cosmos 是基于 Java Web 的 AI 自我共鸣与慢社交平台原型。当前版本按 `inner_cosmos_工程总纲_v_1_0.md` 搭建第一轮工程骨架，默认使用 Mock LLM，可在无 API Key 的情况下演示核心闭环。

> 本 README 说明当前可运行基线。长期自主产品化工作的唯一入口是 [`goal-objective.md`](goal-objective.md)，完整文档权威关系见 [`对齐文档/README.md`](对齐文档/README.md)。Java 17、Boot 3.3、H2/MySQL 与物理 HTML 等均不是完全体目标架构。

## 技术栈

- Java 21 编译与运行基线
- Spring Boot 3.5.14
- Spring MVC
- MyBatis-Plus
- H2 本地演示数据库；PostgreSQL 16 + pgvector 与 Flyway 为目标生产事实源
- `application-mysql.yml` 仅保留为迁移来源兼容配置，禁止生产双写
- SSE 流式接口
- 多物理 HTML 页面

## 默认账号

- 管理员：`admin / admin123`
- 测试用户：`demo / demo123`

## 运行

```powershell
mvn spring-boot:run
```

本机如果没有全局 Maven，可使用当前工作区临时下载的 Maven：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
```

也可以直接使用脚本自动准备 Maven 并启动：

```powershell
.\scripts\run-dev.ps1
```

访问入口：

```text
http://localhost:8080/pages/index.html
```

## PostgreSQL 模式

默认配置使用 H2，便于本地零配置演示。生产与 PostgreSQL 开发环境使用 Flyway 管理的 60 表基线：

```powershell
docker compose -f poc/postgres-pgvector/compose.yml up -d --wait
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:55432/inner_cosmos_poc"
$env:SPRING_DATASOURCE_USERNAME = "inner_cosmos"
$env:SPRING_DATASOURCE_PASSWORD = "local-poc-only"
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

生产 `prod` profile 强制 PostgreSQL、Flyway、`sslmode=verify-full`/`verify-ca`、外部凭据和禁用 Demo 数据。主 schema 位于：

```text
src/main/resources/db/migration/postgresql/
```

## 已完成的第一轮骨架

- 用户注册、登录、退出、当前用户
- Aurora Mock 对话与 SSE 路由
- DialogSession / DialogMessage 保存
- 对话结束事件与 MemoryCard 生成
- 情感重力计算
- 记忆星空数据接口
- EchoCapsule 创建与星海广场种子数据
- PersonaChat 有限轮次对话
- SlowLetter 状态模式流转
- Safety 边界与支持资源
- AI Interaction Log 入库与查询
- Admin 基础查看接口
- 18 个物理 HTML 页面
