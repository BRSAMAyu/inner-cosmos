import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuroraApp } from "./AuroraApp";
import { startTimeOfDayTheme } from "./theme";
import { startRipples } from "./ripple";
import { startStardust } from "./stardust";
import "./styles.css";

// 七时段时间感知主题：按本地时间设 <html data-time>，每分钟刷新。
startTimeOfDayTheme();
// 点击涟漪：每次在交互元素上按下都发射即时反馈（prefers-reduced-motion 时自动跳过）。
startRipples();
// 星尘背景：极淡暖色粒子缓慢流动（prefers-reduced-motion 跳过，移动端降级）。
startStardust();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuroraApp />
  </StrictMode>
);
