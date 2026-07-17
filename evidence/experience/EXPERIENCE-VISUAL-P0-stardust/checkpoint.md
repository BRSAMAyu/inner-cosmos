# EXPERIENCE §1.1.4 · 星尘母题（checkpoint 9）

五母题（流动/呼吸/星尘/涟漪/透光）盘点：流动(easing, cp2)、呼吸(.orb)、涟漪(cp5)、透光(glass
backdrop-filter, 部分) 均已在，**唯独"星尘"完全缺失**。本 checkpoint 补上——极淡暖色粒子层缓慢上浮 +
横向摇曳 + 呼吸式明灭，营造"安静流动的内在宇宙"纵深（UIUXdesign §14 目标）。

## 交付

- `web/src/stardust.ts`：
  - 纯函数 `particleCount(w,h,coarse)`（视口面积定密度，coarse pointer/移动端显著降级，带上下限）、
    `createParticles(...)`（可注入 rand 便于确定化测试）、`isCoarsePointer()`。
  - `startStardust()`：创建 fixed 全屏 canvas（`aria-hidden`），rAF 循环绘制暖色粒子；
    **prefers-reduced-motion 完全跳过**（返回 no-op、不建 canvas）；**无 2D context（jsdom）安全退化**；
    暖色取自 `--accent-aurora` token（回退暖金 #c79a68），绝不冷蓝；resize 重算粒子数；返回 stop 清理。
- `web/src/styles.css`：`.stardust`（fixed/inset0/z-index 0/pointer-events none）+ `#root{position:relative;z-index:1}`
  确保内容永远在星尘之上、星尘绝不拦截指针。
- `web/src/main.tsx`：`startStardust()` 全局启动（与 ripple/theme 并列）。

## 验证

- `npm test`：**108/108**（原 102 + 6 星尘用例：移动端密度降级、超大视口封顶、零视口=0、粒子数量/坐标在
  视口内/极淡 alpha、reduced-motion 不建 canvas 返回 no-op、无 context 安全退化不抛错）。jsdom 打印
  "getContext not implemented" 恰好走通了安全退化分支。
- `npm run build`：tsc + vite OK；`.stardust` 样式确认进入构建产物。
- **真实浏览器实测**（生产静态 app 托管于 127.0.0.1:8125，getComputedStyle + getImageData 取证）：
  - canvas 正确挂载：`position:fixed, z-index:0, pointer-events:none, canvasW×H=1280×720`，且 `#root` z-index=1
    （内容在星尘之上）。
  - **绘制管线正确**：`--accent-aurora` 解析为 `rgb(199,154,104)`；用该 tint 手绘一颗粒子后 `getImageData`
    读回 `[201,155,103,89]`——暖色（r>g>b 成立）、淡 alpha 89/255≈0.35，符合"极淡暖色"设计。
  - 实时循环 litPixels=0 的原因已定位：**Browser pane 标签页 `document.hidden=true`**，浏览器按规范暂停后台
    标签的 `requestAnimationFrame`（实测 `rafFiredWithin300ms=false`）——这是环境限制，非 bug；真实可见标签页
    rAF 正常，粒子会动（循环本就随可见性恢复）。截图工具在动画页仍超时（已记录的同类 flakiness）。

## 五母题现状（更新）

流动 ✓ | 呼吸 ✓ | **星尘 ✓（本 checkpoint）** | 涟漪 ✓ | 透光 ◐（glass 面板已有 backdrop-filter，
可后续统一强化）。

## 下一步

- §1.1 视觉主线继续：透光母题统一强化；然后**全量 var() token 化**（styles.css 尚有 619 处硬编码暖色 hex，
  需分多个 checkpoint 谨慎推进）→ 亮色/莫兰迪时段带 → 本地字体打包。token 化是亮色时段带的前置。
