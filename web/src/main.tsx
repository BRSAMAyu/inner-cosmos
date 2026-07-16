import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuroraApp } from "./AuroraApp";
import { startTimeOfDayTheme } from "./theme";
import { startRipples } from "./ripple";
import "./styles.css";

// 七时段时间感知主题：按本地时间设 <html data-time>，每分钟刷新。
startTimeOfDayTheme();
// 点击涟漪：每次在交互元素上按下都发射即时反馈（prefers-reduced-motion 时自动跳过）。
startRipples();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuroraApp />
  </StrictMode>
);
