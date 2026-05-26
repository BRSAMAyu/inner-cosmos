# API 冒烟测试记录

当前环境：

- Windows PowerShell
- Java 21
- 本地 Maven：`.tools/apache-maven-3.9.9`
- 默认 H2 数据库
- `llm.mode=mock`

已执行：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -DskipTests compile
```

结果：编译通过。

启动：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
```

验证：

- `GET /pages/index.html` 返回 200。
- `POST /api/auth/login` 使用 `demo / demo123` 成功。
- `POST /api/dialog/session/create` 成功创建会话。
- `POST /api/aurora/message` 返回 Mock Aurora 回复并写入日志。
- `POST /api/dialog/session/{id}/finish` 触发记忆沉淀。
- `GET /api/memory/starfield` 返回记忆星体数据。
- `GET /api/plaza/capsules` 返回 5 个种子共鸣体。
