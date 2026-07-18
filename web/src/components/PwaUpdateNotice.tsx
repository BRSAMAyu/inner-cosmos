import { useRegisterSW } from "virtual:pwa-register/react";
import { UpdateBanner } from "./UpdateBanner";

// B5-pwa-mobile: the container that owns vite-plugin-pwa's useRegisterSW() hook (the
// virtual:pwa-register/react module vite-plugin-pwa generates from web/vite.config.ts's
// VitePWA() registerType at build time) and registers the service worker exactly once on
// mount -- this replaces the plain `registerSW({ immediate: true })` call that used to live
// directly in web/src/main.tsx. useRegisterSW's own registerSW() call already no-ops when
// "serviceWorker" is not in navigator (see vite-plugin-pwa's client/build/register.js), so no
// extra feature-detection guard is needed here.
//
// registerType is "prompt" (see vite.config.ts), not "autoUpdate": under vite-plugin-pwa's
// generated register script, "autoUpdate" mode never calls onNeedRefresh at all -- it silently
// reloads the page itself the moment an updated service worker activates, with zero user
// warning. "prompt" mode is the one that surfaces needRefresh/offlineReady state and only
// applies the waiting service worker (via updateServiceWorker(), which sends the
// skip-waiting message and reloads once the new worker takes control) when the user clicks
// "现在刷新" below -- confirmed by reading node_modules/vite-plugin-pwa/dist/client/build/
// register.js directly rather than assuming from the option name.
export function PwaUpdateNotice() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    offlineReady: [offlineReady, setOfflineReady],
    updateServiceWorker,
  } = useRegisterSW({ immediate: true });

  return (
    <UpdateBanner
      needRefresh={needRefresh}
      offlineReady={offlineReady}
      onReload={() => {
        void updateServiceWorker(true);
      }}
      onDismissRefresh={() => setNeedRefresh(false)}
      onDismissOfflineReady={() => setOfflineReady(false)}
    />
  );
}
