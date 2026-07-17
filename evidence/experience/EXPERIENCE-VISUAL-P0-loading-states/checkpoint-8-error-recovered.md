# EXPERIENCE §1.1.5 · 入口连接的"错误 + 恢复"态（checkpoint 8）

四态（空/错/等待/恢复）里 checkpoints 6-7 覆盖了"等待"态；本 checkpoint 补上最关键流程的"错误 + 恢复"态。

## 真实缺陷（不是补空白，是修 bug）

`AuroraApp.tsx` 的 `bootstrap()` catch 分支：非鉴权失败（网络/500/非 JSON 响应）时**只 `setStatus(错误)`，
却把 `authenticated` 停在 `null`**。而渲染入口 `if (authenticated === null)` 只显示"正在连接你的内宇宙"加载屏
——于是任何一次 bootstrap 失败都会让用户**永久卡在加载屏**，错误信息根本没被渲染出来。这是一个真实的、
高可感知的体验缺陷（正是操作者反感的"像坏了的工程 demo"）。

## 交付

- `AuroraApp.tsx`：新增 `bootstrapError` 状态；`bootstrap()` 开头 `setBootstrapError(null)`，非鉴权失败分支
  `setBootstrapError(message)`。入口渲染改为：`bootstrapError ? <ConnectError …/> : <LoadingText busy>`。
- `web/src/loading.tsx`：抽出 `ConnectError({ message, onRetry })` 组件（`role="alert"` + 错误详情 +
  "重试"恢复按钮），归入加载/连接四态家族，便于单测。
- `web/src/styles.css`：`.connect-error`（`--surface-raised` 暖面板 + `--hairline-strong` 描边）、
  `.connect-error-title`（`--text-primary`）、`.connect-error-detail`（`--danger` 暖红，非冷科技告警）。

## 验证

- `npm test`：**102/102**（原 101 + 1 个 `ConnectError` 单测：渲染错误详情 + 重试按钮回调 onRetry）。
- `npm run build`：tsc + vite OK，`assets/app.js` 333.23kB。
- **真实浏览器端到端实测**（这是本 checkpoint 最强证据——直接跑通新代码路径）：把生产构建的静态 app
  用 HTTP 单独托管在 `127.0.0.1:8124/app/aurora/`（**无后端**），bootstrap 的 `createSession` fetch
  拿到 404 HTML 而非 JSON → 抛错 → 进入新错误态。getComputedStyle + innerText 取证：
  - `stillLoading: false`（**不再卡加载屏**——修复生效），`hasErrorState: true`。
  - 标题「没能连上你的内宇宙」；详情「Unexpected token '<' … is not valid JSON」，
    色 `rgb(217,138,114)` = `--danger`（暖红，r>g>b 暖侧）；面板 `rgb(42,34,32)` = `--surface-raised`。
  - **恢复路径**：DOM click「重试」→ 触发 1 次新 fetch（重跑 bootstrap），后端仍缺失故正确回到错误态而非
    卡加载（`backInErrorState: true, stuckLoading: false`）。真实后端在线时重试即进入应用。
  - （截图工具仍超时——环境 flakiness，与前序 checkpoint 一致；用 computed-style/DOM 文本取证。）

## 下一步

- 空/错/恢复态审计推进到各数据加载视图（星空/共鸣/信件/画像等）——多数已有空态（AuroraConversation
  `.empty`、ResonanceNetwork `.network-empty`、PeopleDiscovery/PlazaDirectory 空态），重点补错误+恢复。
- 然后 §1.1 视觉主线：剩余 var() token 化 → 亮色/莫兰迪时段带 → 本地字体打包 → 五母题补齐。
