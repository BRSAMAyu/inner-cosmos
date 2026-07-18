import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router-dom";
import { registerSW } from "virtual:pwa-register";
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

// B5-pwa-mobile: registers the service worker vite-plugin-pwa generates at build time
// (web/vite.config.ts's VitePWA() config). In dev (`npm run dev`) this is a no-op unless
// devOptions.enabled is set, so it only takes effect against a real `npm run build` output
// -- exactly the artifact Spring serves under /app/aurora/. registerType is "autoUpdate", so
// no update-available prompt is shown here (see track-b-status.yml for that as a follow-up).
if ("serviceWorker" in navigator) {
  registerSW({ immediate: true });
}

// HashRouter (not BrowserRouter): the app is served as a static bundle under
// /app/aurora/ by Spring Boot, whose WebMvcConfig only forwards the exact "/app/aurora"
// and "/app/aurora/" paths to index.html -- there is no "/app/aurora/**" catch-all
// fallback. A BrowserRouter path like /app/aurora/cosmos would 404 on a hard refresh or a
// pasted deep link. The hash portion of a URL is never sent to the server, so HashRouter
// gives real, shareable, back/forward-correct routes today with zero server-side change.
// Filed as a follow-up in docs/goal/tracks/track-b-integration-requests.yml: once a
// server-side SPA fallback exists, swap HashRouter for BrowserRouter here only -- the rest
// of the app depends solely on react-router's location/navigate API, not on this choice.
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <HashRouter>
      <AuroraApp />
    </HashRouter>
  </StrictMode>
);
