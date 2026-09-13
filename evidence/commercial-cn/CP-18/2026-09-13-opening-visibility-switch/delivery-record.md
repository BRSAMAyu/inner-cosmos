# CP-18 连续性可见撤回开关 — 增量

- 日期：2026-09-13；实施：后台实现 agent（continuity + web 前端域），主线程整合
- 愿景：V14（跨会话连续性是用户可见可控的能力——展示可撤回，事实不伪造）

## 交付内容

### 1. 属主开关（V52 + H2 孪生）

`tb_continuity_preference(user_id UNIQUE, opening_visible BOOLEAN DEFAULT TRUE)`——无行=默认开。`SessionContinuityService.visibility()/setOpeningVisible()`；`OpeningContext` 增 `openingVisible` 分量（保留兼容构造器）+ `withdrawn()` 工厂。**事实构建逻辑一字未动**——开关只影响开屏供给，连续性事实照常诚实记录（负测断言事实层与 summary 行原样保留）。

### 2. 供给与 API

`GET /api/dialog/session/continuity` 关状态返回 withdrawn 标记（零先前材料 + openingVisible=false，不冒充新用户）；`GET/PUT /api/dialog/session/continuity/visibility`（仅本人，缺 openingVisible → 400）。

### 3. 前端

`AuroraOpeningContinuity.tsx`：openingVisible===false → 整卡不渲染（含「第一次对话」形态——withdrawn 不冒充新用户）；卡片自带「不再显示开场回顾」撤回按钮（PUT 成功才隐藏，失败如实报错）。

### 4. 顺手修复的既有 bug（如实记录）

`web/src/api.ts` 的 `dialogContinuity` 原请求 `/api/dialog/continuity`，后端实际映射 `/api/dialog/session/continuity`——**原路径下开屏卡片从未真正加载过**。已修正。

## 测试证据

- 新增 `SessionContinuityVisibilityToggleTest` 4/4（默认开带溯源供给/开关持久/关→withdrawn 标记零材料+事实层原样/空 body 400）；continuity 域回归 22/22；
- 前端 AuroraOpeningContinuity 9/9 + 全量 783/783 绿、tsc clean；
- 主线程全量整合回归：backend 1837/1837。

## 诚实边界

- 开关「重新打开」暂无 UI 入口（卡关掉即不渲染，重开走 PUT API；设置面接线登记后续——consent 中心并行改动中）；
- 开关是展示选择不是数据删除——连续性事实的清除走既有数据权利/撤回链路。
