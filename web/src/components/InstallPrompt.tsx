import { useEffect, useState } from "react";

// B5-pwa-mobile: in-app "install Inner Cosmos" affordance. Not in TS's lib.dom.d.ts (it is a
// Chromium-only extension, never shipped by Safari/Firefox), so it is declared locally rather
// than pulling in a whole third-party types package for one event shape.
type BeforeInstallPromptEvent = Event & {
  readonly platforms: string[];
  readonly userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
  prompt(): Promise<void>;
};

export function InstallPrompt() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    // The browser fires this at most once per page load, and only when it has decided the
    // app is installable. Per the MDN/web.dev contract: call preventDefault() synchronously
    // inside this handler (not after an await or a later tick) or the browser's own automatic
    // mini-infobar/install-UI takes over immediately and this event can never be replayed
    // later via a stored reference -- stash the event itself so a later click on our own
    // affordance can call .prompt() on demand.
    function onBeforeInstallPrompt(event: Event) {
      event.preventDefault();
      setDeferredPrompt(event as BeforeInstallPromptEvent);
    }
    // Fires once the app is actually installed (via our prompt, the browser's own UI, or an
    // OS app store) -- there is nothing left to offer, and a stale deferredPrompt cannot be
    // replayed a second time regardless.
    function onAppInstalled() {
      setDeferredPrompt(null);
      setDismissed(true);
    }
    window.addEventListener("beforeinstallprompt", onBeforeInstallPrompt);
    window.addEventListener("appinstalled", onAppInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstallPrompt);
      window.removeEventListener("appinstalled", onAppInstalled);
    };
  }, []);

  // Nothing to show: the event never fired (already installed, or a browser that doesn't
  // support it at all, e.g. iOS Safari -- no broken/always-visible button in either case), or
  // the user already dismissed this session's affordance.
  if (!deferredPrompt || dismissed) return null;

  async function handleInstall() {
    if (!deferredPrompt) return;
    // A BeforeInstallPromptEvent can only be prompted once; consume it either way so the
    // affordance disappears once the user has answered, rather than lingering on a dead event.
    await deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    setDeferredPrompt(null);
  }

  return (
    <div className="pwa-banner pwa-banner-install" role="status" aria-live="polite">
      <p className="pwa-banner-title">把内宇宙装到桌面/主屏幕</p>
      <p className="pwa-banner-detail">像原生应用一样打开，离线也能进入。</p>
      <div className="pwa-banner-actions">
        <button type="button" className="pwa-banner-primary" onClick={handleInstall}>
          安装内宇宙
        </button>
        <button type="button" className="pwa-banner-dismiss" onClick={() => setDismissed(true)}>
          不用了
        </button>
      </div>
    </div>
  );
}
