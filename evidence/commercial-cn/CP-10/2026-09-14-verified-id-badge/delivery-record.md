# CP-10 VERIFIED_ID 徽章 — 增量

- 日期：2026-09-14；实施：后台实现 agent（AccountSettings 前端域）
- 愿景：V13（实名状态如实可见）

## 交付（agent W）

AccountSettings 账户面顶部 `IdentityVerificationBadge`：仅当后端 `ageGateMethod === "VERIFIED_ID"`（IdentitySecurityController 既有端点，CP-13 链路）显示「实名已验证」；未验证中性「未完成实名」；PENDING「核验进行中」；REJECTED/EXPIRED 显示后端自己的 latestFailureReason。**不伪造**：未成年人拦截路径（history 有 VERIFIED 但账户未升级）仍显示未完成（专项测试）；加载失败「暂时无法获取」+刷新绝不猜；加载中不渲染任何态（避免「未完成」提前闪烁）。

## 诚实边界

- 前端无验证发起入口（后端 initiate 需通道合同，非 sandbox fail-closed）——只读展示，未验证态不做假链接；发起入口属渠道合同 operator 门禁后接线。

## 测试

AccountSettings 30/30（徽章 7 新用例含 minor-intercept 不升级）；全量 web 828/828。
