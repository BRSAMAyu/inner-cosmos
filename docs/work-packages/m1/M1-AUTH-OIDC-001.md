# M1-AUTH-OIDC-001 — 移动端 OIDC Resource Server 与身份绑定基础

```yaml
status: IMPLEMENTED
workstream: platform-security
depends_on: [M1-AUTH-WEB-001, M1-DATA-APP-001]
human_gate: false
evidence_dir: evidence/m1/M1-AUTH-OIDC-001/
```

## 目标

在保留 Web Session/CSRF 和全部创新产品语义的前提下，建立移动端 Authorization Code + PKCE 所需的服务端信任边界：移动端把授权码交给外部 OIDC Provider，Inner Cosmos 仅接收并验证该 Provider 签发的 API access token。

## 实施结果

1. 使用 Spring Security OAuth2 Resource Server 与 Nimbus JOSE 验证 JWT 签名、`iss`、`aud`、`exp` 和 `nbf`；
2. 以 `(issuer, subject)` 作为唯一外部身份键，禁止按邮箱自动合并账户；
3. 首次可信认证可自动创建本地账户，角色固定由服务端数据库赋予，忽略客户端角色 claim；
4. Bearer 请求不使用 Cookie CSRF，Web Session 写请求继续使用同步器 CSRF；无效 Bearer 不能借已有 Cookie Session 绕过 CSRF；
5. 同时存在 Bearer 与 Session 时，以经过验签的 Bearer principal 为当前请求身份；
6. 提供不含 client secret 的公共移动端启动元数据，声明 Authorization Code、PKCE 必须启用且只允许 `S256`；
7. PostgreSQL Flyway V2 与 H2 schema 新增 `tb_user_identity`，账户删除通过外键级联清理绑定；
8. 生产环境缺失 OIDC issuer、JWK、audience、端点、public client ID 或 redirect URI 时拒绝启动。

## 验收

- 本地 JWK 端点 + 真实 RSA 签名验证通过，不受信任私钥被拒绝；
- issuer、audience 与过期 token 的负向测试通过；
- 自动建档、稳定复用、邮箱验证、服务端角色和 Bearer/Session 混合身份测试通过；
- 全量 673 tests、SpotBugs、Secret scan、PostgreSQL Testcontainers 和生产镜像烟测通过。

## 未关闭范围

`AUTH-OIDC` 保持 `IN_PROGRESS`。本包完成服务端 resource-server 与 PKCE bootstrap 契约，但尚未交付 Capacitor 原生授权回调、Keychain/Keystore token 保存、refresh/revoke、设备会话管理 UI，也没有真实外部 IdP 租户联调和独立安全评审。

## 回滚

不得回滚 Web CSRF、JWT 验签、issuer/audience 校验、服务端角色或 `(issuer, subject)` 唯一绑定。兼容问题只能通过修复 IdP/client 配置或迁移身份数据解决。
