# M1-AUTH-WEB-001 — Web Session 与 CSRF 安全基线

```yaml
status: IMPLEMENTED
workstream: platform-security
depends_on: [M1-SEC-001, M1-BASE-001]
human_gate: false
evidence_dir: evidence/m1/M1-AUTH-WEB-001/
```

## 目标

在不改变 Aurora 主动性、Self/Constitution/Emergence、画像、共鸣体、慢信、星空或匹配语义的前提下，为现有 Web 体验建立可信的服务端会话、会话固定攻击防护和同步器 CSRF 防护。

## Owned paths

- `src/main/java/com/innercosmos/config/SecurityConfig.java`
- `src/main/java/com/innercosmos/config/SessionAuthenticationFilter.java`
- `src/main/java/com/innercosmos/controller/AuthController.java`
- Web 静态客户端的统一请求传输层
- 对应安全、生产守卫与浏览器契约测试

## 实施结果

1. `LOGIN_USER_ID` 服务端会话成为 Spring Security 的唯一 Web 身份来源，客户端身份头不被信任；
2. 登录和注册成功时轮换 `JSESSIONID`，阻断会话固定；
3. 默认启用 session-bound synchronizer CSRF，生产环境禁止关闭；
4. 写请求统一由前端 `IC.secureFetch` 获取并附加动态 CSRF header；
5. 401、403 与 CSRF 错误返回稳定 JSON envelope；
6. 危机帮助资源保持匿名可访问；
7. CORS 仅允许明确列出的请求头；
8. 未删除、降级或收缩任何创新产品能力。

## 验收

- 缺失或无效 CSRF token 的写请求返回 403；
- 未认证访问返回 401，伪造 `X-User-Id` 不产生认证；
- 登录、注册均轮换会话 ID，登出使会话失效；
- 真实 HTTP 请求和静态浏览器契约均通过；
- Java 21 / Spring Boot 3.5 全量测试、SpotBugs、Secret scan 与生产镜像烟测通过。

## 未关闭范围

`AUTH-OIDC` 仍为 `IN_PROGRESS`：本包只关闭安全 Web session/CSRF 基线；移动端 OIDC Authorization Code + PKCE 尚未实现和验证。

## 回滚

不得回滚 CSRF、会话轮换或服务端身份边界。若前端兼容出现问题，只能修复统一传输层或明确的公开端点清单，不得重新全局关闭 CSRF。
