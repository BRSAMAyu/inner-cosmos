// B5-pwa-mobile: a plain, controlled presentational component for the versioned-update flow.
// Owns no service-worker/registration logic itself (see PwaUpdateNotice.tsx for the container
// that feeds it vite-plugin-pwa's useRegisterSW() state) so it is fully unit-testable with
// props alone. Reuses the .connect-error card's warm-theme visual language (see
// web/src/loading.tsx / web/src/styles.css) rather than inventing a new banner style.
export type UpdateBannerProps = {
  /** A new service worker is waiting -- reloading now activates it. Never auto-reloads. */
  needRefresh: boolean;
  /** The app shell was precached for the first time; nothing to reload, just an FYI. */
  offlineReady: boolean;
  onReload: () => void;
  onDismissRefresh: () => void;
  onDismissOfflineReady: () => void;
};

export function UpdateBanner({
  needRefresh,
  offlineReady,
  onReload,
  onDismissRefresh,
  onDismissOfflineReady,
}: UpdateBannerProps) {
  // A pending update always takes priority over the one-time offline-ready FYI -- if both are
  // somehow true at once (e.g. install completed just as an update lands), the actionable
  // banner is the one worth the user's attention.
  if (needRefresh) {
    return (
      <div className="pwa-banner pwa-banner-update" role="status" aria-live="polite">
        <p className="pwa-banner-title">内宇宙有新版本了</p>
        <p className="pwa-banner-detail">刷新后即可使用，不会打断你还没发送的内容。</p>
        <div className="pwa-banner-actions">
          <button type="button" className="pwa-banner-primary" onClick={onReload}>
            现在刷新
          </button>
          <button type="button" className="pwa-banner-dismiss" onClick={onDismissRefresh}>
            稍后
          </button>
        </div>
      </div>
    );
  }
  if (offlineReady) {
    return (
      <div className="pwa-banner pwa-banner-offline-ready" role="status" aria-live="polite">
        <p className="pwa-banner-title">内宇宙现在可以离线打开了</p>
        <div className="pwa-banner-actions">
          <button type="button" className="pwa-banner-dismiss" onClick={onDismissOfflineReady}>
            知道了
          </button>
        </div>
      </div>
    );
  }
  return null;
}
