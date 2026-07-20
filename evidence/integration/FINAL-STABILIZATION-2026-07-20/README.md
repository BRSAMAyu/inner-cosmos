# FINAL-STABILIZATION-2026-07-20

状态：`PASS_FOR_LOCAL_TEACHER_DEMO`。这不是“商业发布已完成”的声明；真实 Provider、设备、法务与生产账户仍属于人工/外部门禁。

## 本轮闭环

- 将 Track A 与 Track B 的已合并能力收拢到同一条可演示产品路径。
- 增加持久化账号来源字段，数据库边界排除 `SYNTHETIC`/`DEMO`/`SYSTEM`，不再用用户名猜测测试账号。
- 把用户确认的理解 claim 安全、限量、可纠正地送入 Aurora 上下文。
- 修正 Mock 语义分析的单字误判，让课程展示焦虑场景得到有依据、可行动的双气泡回应。
- 完成 ALIVE 严格 JSON 决策、清洁 SPA 路由、标签渐进披露与短屏/移动端输入区打磨。
- 修复并行写请求的 CSRF single-flight/refresh 竞争，以及 PWA 提示遮挡移动端底部导航的问题。
- 将 Playwright 固定为中文、单 worker、独立新鲜 H2 seed；避免共享账号状态竞争并保留断线回放测试。

## 可复现门禁

| 门禁 | 结果 |
|---|---|
| `mvnw clean test` | 913 tests，0 failures，0 errors，0 skipped |
| PostgreSQL/pgvector/Flyway | PostgreSQL 16，Flyway V1–V20 PASS |
| Redis integration | Testcontainers PASS |
| `npm test -- --run` | 33 files，226 tests PASS |
| `npm run build` | TypeScript + Vite/PWA production build PASS |
| `npx playwright test --reporter=list` | 14/14 PASS |
| Browser 真实体验 | clean-route hard refresh、短桌面/投影、390×844、标签披露、账号过滤、双气泡回答 PASS |

Playwright 覆盖：多气泡、打断/停止/重规划、SSE 断线耐久恢复、WakeIntent、Aurora Self、理解校准、星空模式、定点记忆修正、共鸣体 needs-review、回滚/永久忘记、共鸣体跨用户慢信、心理 Skill、移动窄屏与离线恢复。

## 证据边界

- Mock 体验已适合无密钥课堂演示，但不代表真实模型质量优于竞品。
- dead-button 诊断对“内宇宙/我的”执行硬门禁；共鸣/连接中的上下文依赖按钮只输出诊断清单，由专门纵向旅程验证。
- `local-complete`、Academy EKS 和 commercial-sg 是三种不同证据，禁止互相替代。
- 商业完成仍需：密钥人工轮换签字、真实 Provider 盲评、真实设备/APNs/FCM/签名、PDPA/跨境/心理专业复核，以及生产 AWS/DNS/支付/商店账户操作。
