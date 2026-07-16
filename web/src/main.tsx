import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuroraApp } from "./AuroraApp";
import { startTimeOfDayTheme } from "./theme";
import "./styles.css";

// 七时段时间感知主题：按本地时间设 <html data-time>，每分钟刷新。
startTimeOfDayTheme();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuroraApp />
  </StrictMode>
);
