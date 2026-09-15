# CP-18 开关设置面重开入口 — 第四增量

- 日期：2026-09-15；实施：后台 agent（AccountSettings 域）
- 愿景：V14（撤回可逆——卡上关掉后设置面可重开）

## 交付

AccountSettings 新 `OpeningRecapSetting`（默认直连 `api.continuityVisibility`/`setContinuityVisibility`，可注入）：值只来自 GET 真实返回（加载中不渲染不闪错、失败「暂时无法获取」+重试绝不猜）；切换 optimistic+PUT 确认、失败回滚如实报错；关态文案「不再显示开场回顾，连续性记录不受影响」对齐后端「仅显示选择、连续性事实照记」语义。中英双语。

## 测试

AccountSettings 37/37（新 7 用例：真实值渲染/切换落库回滚/失败态/双语）；tsc 绿。

## 诚实边界

无——登记项闭环。
