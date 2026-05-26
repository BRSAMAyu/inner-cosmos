# Inner Cosmos 内宇宙

Inner Cosmos 是基于 Java Web 的 AI 自我共鸣与慢社交平台原型。当前版本按 `inner_cosmos_工程总纲_v_1_0.md` 搭建第一轮工程骨架，默认使用 Mock LLM，可在无 API Key 的情况下演示核心闭环。

## 技术栈

- Java 17 target，当前机器 Java 21 可运行
- Spring Boot 3.3.6
- Spring MVC
- MyBatis-Plus
- H2 默认演示数据库，`application-mysql.yml` 保留 MySQL 8 配置
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

## MySQL 模式

默认配置使用 H2，便于老师本地无数据库时先演示。切换 MySQL：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

请先创建数据库：

```sql
CREATE DATABASE inner_cosmos DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
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
