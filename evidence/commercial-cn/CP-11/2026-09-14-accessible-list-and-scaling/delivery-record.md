# CP-11 星域可达列表退化视图 + 200% 缩放合同 — 增量

- 日期：2026-09-14；实施：后台实现 agent（MemoryStarfield 前端域），主线程整合（修后端闭锁根因）
- 愿景：V02/V11（星域可离开 3D 呈现；情绪编码开关在所有呈现通道一致闭锁）

## 交付（agent U）

1. **视图切换**：「星图/列表」切换组（aria-pressed）；`localStorage["ic-starfield-view"]` 持久化（显式选择优先）；`prefers-reduced-motion` 无存储偏好时默认列表；列表视图下 cosmos-map 不渲染（星体按钮退出 Tab 序），全部记忆单 ol 平铺不折叠。
2. **闭锁语义一致**：`emotionEncoding===false`（防御性可选读取）时地图三通道全中性（统一 13px 直径 = 服务端拍平值、中性色、中性不透明度）；图例「尺寸」条目改中性说明、「亮度」条目删除（zh/en）；`data-emotion-encoding` 属性；列表行本就不渲染重力/颜色（不变量注释+测试钉死）。
3. **200% 缩放合同**（`MemoryStarfieldTextScalingContract.test.tsx` 9 用例，ContrastTokenAudit 同型）：静态断言组件与 styles.css 相关规则无固定 px 宽高/px 字号（`.cosmos-star-label` 级联末条 max-width 用 em）；渲染断言列表项逐条增长不截断、无 inline 覆盖、rem 字号；选择器改名零匹配时响亮失败。
4. styles.css append-only（切换组/星标宽度/中性光晕）。

## 主线程整合：后端闭锁根因修复

agent 审计发现 CP-24 的服务端闭锁只拍平 gravity（尺寸），**color 与 glow（=0.34+gravity×0.22）仍泄露情绪重力**——en 图例文案也泄露。前端防御性闭锁已封 UI 层；根因在后端 `StarfieldExplorerServiceImpl` 修复：编码关闭时三通道一起中性化（gravity=0.5、color="#8a8a97"、glow=0.7），StarfieldEncodingAndLetterReceiptPrivacyTest 2/2 回归绿。

## 诚实边界

- jsdom 无布局：「200% 下零像素裁剪」不可在此证明——真机 200% 缩放仍是 CP-12A 人工研究门（合同只断言可静态/渲染验证的事实，装饰性 cosmos-map 明确排除）；
- api.ts 未传 emotionEncoding 参数（默认 true 请求，闭锁分支由后端标志触发，语义闭环）；
- reduced-motion 仅初始默认无实时监听（显式选择优先哲学，styles.css 已有全局动效熔断）。

## 测试

MemoryStarfield 29/29（+11）+ 缩放合同 9/9 + 后端编码回归 2/2；全量 backend 1844/1844 + web 828/828 + tsc clean。
