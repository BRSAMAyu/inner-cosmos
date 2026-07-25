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
// Normal web builds use the explicit update prompt below. Public classroom builds use
// autoUpdate instead, so a returning tutor cannot remain pinned to a stale Demo shell; in
// that mode needRefresh stays false and this component only owns the offline-ready notice.
export function PwaUpdateNotice() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    offlineReady: [offlineReady, setOfflineReady],
    updateServiceWorker,
  } = useRegisterSW({
    immediate: true,
    // Browser-managed service-worker checks may be throttled for many hours. Ask for one
    // explicit check whenever the app shell starts; normal builds still show their prompt,
    // while classroom builds activate and reload immediately.
    onRegisteredSW: (_serviceWorkerUrl, registration) => {
      void registration?.update();
    },
  });

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
