# RUN-007 — UIUX 艺术化打磨（全前端 · 艺术品级）

> 用户指令：把整个前端「detail 级」打磨到接近艺术家个人网站的程度；先做完全部，再由用户检视（本轮不设中途审批门）。北极星 = 商业级蓝图第 7 章（暖雾 Morandi 日常层 + 星海深层 + 时间/天气/心情环境层；环境层 JS 已建好）。基线绿线 606，纯前端改动，**不破坏任何后端契约 / 测试**。

## 现状诊断

`css/app.css`（4628 行）已是成熟、有品味的系统（Morandi 令牌、楷体 hero、衬线正文、flow 缓动、呼吸动效、遮罩网格）。问题不在「缺」，而在：

1. **accretion 杂质** — 重复的 `body.dark-star` 块（~748 与 ~1704/1727）、重复 `@keyframes fly-away`（1061 与 1568）、多处分散的 page-specific enhancement 堆叠 → 维护噪音 + 偶发覆盖冲突。
2. **细节工艺未到顶** — elevation/阴影体系是单一 `--shadow`；缺统一的层级（resting/hover/raised）；滚动条、选区、focus ring 工艺不一致；空状态、加载态、分隔符等「呼吸细节」散落。
3. **节奏不统一** — 字号/行高/间距虽有令牌但页面各写各的内联 style；缺一套可复用的版式韵律 + 微交互词汇，导致页面间气质漂移。

「艺术品级」= 在已有高水准上**补足这三层**，并让每页都吸到。

## 艺术级打磨准绳（一致性合同）

每处改动都按此自检：

- **版式韵律**：hero 用楷体、克制字距；正文衬线 1.7 行高；建立模块化字阶（`--fs-*`）与垂直节奏；标题/正文/辅助三档对比清晰；`text-wrap: balance` 用于标题、`pretty` 用于段落。
- **层级与材质**：统一 elevation 体系（`--elev-1/2/3` resting→hover→modal）；卡片用「纸 + 暖雾玻璃」材质，顶部 1px 高光线保留；圆角按语义（卡 16 / 弹层 24 / 控件 8）。
- **色彩与光**：莫兰迪克制；强调色仅用于「真有意义」处（共鸣、当前态）；日常层 vs 星海层对比；暗色（dark-star）与浅色等价完整、无漏网元素。
- **微交互**：hover 抬升 + 柔光、active 轻压、focus-visible 清晰可达；过渡走 `--ease-flow/bloom`；进入用 stagger；**全部尊重 `prefers-reduced-motion`**。
- **细节工艺**：自定义滚动条、选区、占位符、空状态（已有 `IC.empty`，统一气质）、加载骨架、分隔符、链接下划线动画。
- **环境层融合**：时间/天气/心情驱动的背景与各页和谐共生（非装饰）；flow-stage 平面在内容之下不抢戏。
- **可达性**：对比度达标、键盘可达、`:focus-visible`、命中区 ≥44px、reduced-motion 全覆盖。
- **零回归**：不改 DOM 结构所依赖的 id/class 钩子与 JS 行为；`./mvnw test` 仍 606 绿；浏览器 0 console error。

## 执行分期

- **P0 · 基础层（最高杠杆，全页受益）** — 清理 app.css accretion（dedupe dark-star / keyframes）；引入字阶 `--fs-*` + elevation `--elev-*` 令牌；统一滚动条/选区/focus/占位符/链接/分隔符/空状态/骨架工艺；补全 reduced-motion；统一卡片/控件/导航微交互。
- **P1 · 入口与第一印象** — `index.html`、`login.html`、`register.html`（落地气质定调）。
- **P2 · 核心体验面（手工精修，蓝图优先）** — `dashboard`、`aurora-chat`、`portrait`、`memory-starfield`、`echo-plaza`、`slow-letter` + `letter-threads`、`capsule-chat`/`capsule-detail`/`capsule-create`。
- **P3 · 次级体验页** — `daily-record`、`heart-diary`、`emotion-timeline`、`timeline`、`themes`、`relations`、`beliefs`、`todo`、`inbox`、`social`、`safety-harbor`、`thought-shredder`、`weekly-review`、`settings`。
- **P4 · 工具/后台页（随基础层提升 + 轻修，不追特效）** — `admin`、`model-config`、`token-usage`、`abtest-report`、`prompt-versions`、`ai-log`、`ai-dev-history`、`ui-preview`。

## 验收

- `JAVA_HOME=... M2_HOME=... ./mvnw test` → 仍 **≥606 绿**、零回归（纯前端不应动测试，但要确认没误伤 JS 行为契约）。
- 运行应用做真实浏览器验收（蓝图 §7.6）：1440×900 + 1280×720 + 390×844；三主题（白昼/暮色/夜色）+ 天气（晴/雨/雾/暴）切换；**0 console error**；无横向溢出；reduced-motion 下静默优雅。
- 每页对照上面的「准绳」逐条过。

## 不做（YAGNI）

- 不重构后端 / API / DOM 钩子契约。
- 不引第三方 UI 框架或构建工具（保持原生 CSS/JS）。
- 不做与打磨无关的功能改动。
