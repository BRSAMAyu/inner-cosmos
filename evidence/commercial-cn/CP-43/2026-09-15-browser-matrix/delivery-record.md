# CP-43 浏览器矩阵自动化腿 — 增量（§2-22）

- 日期：2026-09-15；实施：后台 agent（web e2e 域）

## 交付

- `web/playwright.config.ts` 多浏览器 projects：chromium 默认基线（53 用例行为不变）；firefox/webkit 经 `IC_BROWSER_MATRIX=full|firefox,webkit` 显式启用（未装引擎机器不默认挂）；**本机三引擎实装实跑**：`IC_BROWSER_MATRIX=full` → PWA 提交冒烟 **6 passed（chromium/firefox/webkit × 2）**，full 矩阵 159 用例列出验证；
- `web/e2e/pwa-submission-smoke.spec.ts`：消费 `web-pwa.checklist.yml` 的可自动化项——manifest+全部声明图标可达、SW register→install→activate→reload 控制 app scope；ICP/HTTPS/下载 SHA/账号流程等 operator 回执项不在自动声明范围（注释明示）；
- `WebPwaChecklistContractTest` 3/3：checklist 结构契约（status 枚举/WAIVED 必带 approved_by/PENDING 无 evidence/禁伪完成态用语/7 骨架项齐全）。

## 诚实边界

真机 Chrome/Edge/Safari 矩阵、商店提交回执属 operator 门禁；axe e2e 重型审计未在本批跑。

## 测试

三引擎 6/6 真跑 + 契约 3/3；默认 chromium 53 用例行为不变。
