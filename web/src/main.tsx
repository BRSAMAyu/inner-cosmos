import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { Capacitor } from "@capacitor/core";
import { AuroraApp } from "./AuroraApp";
import { InstallPrompt } from "./components/InstallPrompt";
import { PwaUpdateNotice } from "./components/PwaUpdateNotice";
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

// AuroraSpaController serves extension-free nested paths as index.html, so product
// spaces now use clean shareable URLs while static assets stay on the resource handler.
// InstallPrompt and PwaUpdateNotice (B5-pwa-mobile: install-prompt UX + versioned-update flow)
// are mounted as siblings to <AuroraApp>, not inside its render tree -- neither needs any of
// AuroraApp's internal state, router location, or auth session, and this keeps them out of the
// way of the concurrent AuroraApp.tsx domain-hook decomposition in progress on this branch.
// Both share one .pwa-banner-stack wrapper (web/src/styles.css) so that if both happen to be
// visible at once (live-verification caught this: a genuinely fresh account can see the
// offline-ready notice fire at the same moment install becomes available) they stack instead
// of overlapping at the same fixed viewport position.
const nativeShell = import.meta.env.VITE_NATIVE_SHELL === "true"
  || Capacitor.isNativePlatform()
  || "__TAURI_INTERNALS__" in window;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter basename={nativeShell ? "/" : "/app/aurora"}>
      <AuroraApp />
    </BrowserRouter>
    {!nativeShell && (
      <div className="pwa-banner-stack">
        <InstallPrompt />
        <PwaUpdateNotice />
      </div>
    )}
  </StrictMode>
);
