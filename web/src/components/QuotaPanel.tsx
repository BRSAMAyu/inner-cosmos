import { useEffect } from "react";
import type { QuotaOverview, QuotaRow, SubscriptionWindowRow } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// CP-46 transparent quotas: the owner-facing "what have I used, what am I allowed, when does it
// reset" surface for GET /api/me/quotas. Presentational only — data is loaded by AuroraApp, the
// same split as DataRightsPanel/ConsentCenterPanel. Bilingual via the shared Locale, following the
// same Record<Locale, dict> convention as those panels.

// Known capability codes get a human label; anything the backend adds later falls back to the raw
// code (honest over pretty — the panel never hides an unknown capability behind a guess).
const CAPABILITY_LABEL: Record<Locale, Record<string, string>> = {
  "zh-CN": {
    "ai.deep_daily_budget": "Aurora 深度对话",
    "memory.extended_horizon": "记忆长程回溯"
  },
  "en-SG": {
    "ai.deep_daily_budget": "Aurora deep conversations",
    "memory.extended_horizon": "extended memory horizon"
  }
};

const COPY: Record<Locale, {
  aria: string; eyebrow: string; heading: string; intro: string;
  dailyHeading: string; subscriptionHeading: string; neverHeading: string; neverNote: string;
  usedOf: (used: number, limit: number) => string; remaining: (n: number) => string;
  resetsAt: (when: string) => string; unknownReset: string;
  quotaEmpty: string; subscriptionEmpty: string; subscriptionEmptyNote: string;
  stateActive: string; stateOther: (state: string) => string;
  autoRenewOn: string; autoRenewOff: string; cancelAtPeriodEnd: string;
  neverItems: (n: number) => string; reload: string;
  errorTitle: string; errorDetail: (message: string) => string; retry: string; loading: string;
}> = {
  "zh-CN": {
    aria: "我的用量与额度（透明配额）",
    eyebrow: "用量 · 透明可见",
    heading: "我的用量与额度",
    intro: "每一项能力的用量、上限与重置时间都如实列出，不藏任何暗门；看到的就是服务器正在执行的。",
    dailyHeading: "每日用量",
    subscriptionHeading: "订阅权益窗口",
    neverHeading: "永不付费解锁的能力",
    neverNote: "安全、纠正、导出与删除永不付费解锁。",
    usedOf: (used, limit) => `已用 ${used} / ${limit}`,
    remaining: n => `剩余 ${n}`,
    resetsAt: when => `${when} 重置`,
    unknownReset: "重置时间未知",
    quotaEmpty: "当前没有计入配额的用量。",
    subscriptionEmpty: "当前没有生效的订阅窗口。",
    subscriptionEmptyNote: "核心每日额度不依赖订阅，始终适用。",
    stateActive: "生效中",
    stateOther: state => state,
    autoRenewOn: "自动续费",
    autoRenewOff: "不自动续费",
    cancelAtPeriodEnd: "本期结束后取消",
    neverItems: n => `${n} 项能力属于这一承诺`,
    reload: "刷新",
    errorTitle: "暂时无法读取配额信息。",
    errorDetail: message => message,
    retry: "重试",
    loading: "…"
  },
  "en-SG": {
    aria: "My usage and allowances (transparent quotas)",
    eyebrow: "USAGE, IN PLAIN SIGHT",
    heading: "My usage & allowances",
    intro: "Every capability's usage, limit and reset time is listed honestly — no hidden doors. What you see here is what the server enforces.",
    dailyHeading: "Daily usage",
    subscriptionHeading: "Subscription windows",
    neverHeading: "Never locked behind payment",
    neverNote: "Safety, correction, export and deletion are never locked behind payment.",
    usedOf: (used, limit) => `${used} of ${limit} used`,
    remaining: n => `${n} remaining`,
    resetsAt: when => `resets ${when}`,
    unknownReset: "reset time unknown",
    quotaEmpty: "Nothing is counting against a quota right now.",
    subscriptionEmpty: "No subscription window is active right now.",
    subscriptionEmptyNote: "The core daily allowances don't depend on a subscription and always apply.",
    stateActive: "Active",
    stateOther: state => state,
    autoRenewOn: "Auto-renews",
    autoRenewOff: "Does not auto-renew",
    cancelAtPeriodEnd: "Cancels at period end",
    neverItems: n => `${n} capabilit${n === 1 ? "y" : "ies"} covered by this promise`,
    reload: "Reload",
    errorTitle: "Usage and allowances are temporarily unavailable.",
    errorDetail: message => message,
    retry: "Retry",
    loading: "…"
  }
};

function capabilityLabel(capability: string, locale: Locale): string {
  return CAPABILITY_LABEL[locale][capability] ?? capability;
}

/** resetsAt arrives as a UTC LocalDateTime WITHOUT a zone marker ("2026-09-14T00:00:00"); a naive
 *  new Date(iso) would read it as LOCAL time and shift the reset moment. Re-tag zoneless strings
 *  as UTC before localizing; an already-zoned or unparseable value passes through untouched. */
export function resetTimeLabel(iso: string, locale: Locale): string {
  const zoneless = !(/[zZ]$/.test(iso) || /[+-]\d{2}:?\d{2}$/.test(iso));
  const parsed = new Date(zoneless ? `${iso}Z` : iso);
  return Number.isNaN(parsed.getTime()) ? iso : parsed.toLocaleString(locale);
}

function usagePercent(used: number, limit: number): number {
  if (limit <= 0) return 0;
  return Math.min(100, Math.max(0, Math.round((used / limit) * 100)));
}

/**
 * CP-46 web surface. Daily quotas render used/limit + remaining + a localized reset moment;
 * subscription windows render their state, reset moment and auto-renew mark; and the never-pay-
 * gated promise is stated as fixed copy that stays visible even when the numbers fail to load
 * (it is a product principle, not a server coin-flip) — only its concrete capability list needs
 * the API. A failed load degrades to an inline alert with a retry.
 */
export function QuotaPanel({ view, loading, loaded, error, onLoad, locale = "zh-CN" }: {
  view: QuotaOverview | null;
  loading: boolean;
  loaded: boolean;
  error: string | null;
  onLoad: () => void;
  locale?: Locale;
}) {
  const t = COPY[locale];

  // Auto-load once on mount (PortraitClaimsPanel's pattern), but never re-fire while an attempt is
  // failing — error is part of the guard so a persistent failure waits for an explicit retry
  // instead of looping the request.
  useEffect(() => {
    if (!loaded && !loading && error === null) onLoad();
  }, [loaded, loading, error, onLoad]);

  const resetCell = (resetsAt: string | null) => resetsAt
    ? <small className="quota-reset">{t.resetsAt(resetTimeLabel(resetsAt, locale))}</small>
    : <small className="quota-reset muted">{t.unknownReset}</small>;

  const quotaRow = (quota: QuotaRow) => (
    <li className="quota-row" key={`${quota.capability}:${quota.basis}`}>
      <div className="quota-row-head">
        <span className="quota-capability">{capabilityLabel(quota.capability, locale)}</span>
        <span className="quota-count">{t.remaining(quota.remaining)}</span>
      </div>
      <div className="quota-meter" role="progressbar" aria-valuemin={0} aria-valuemax={quota.limit}
        aria-valuenow={quota.used}
        aria-label={`${capabilityLabel(quota.capability, locale)}: ${t.usedOf(quota.used, quota.limit)}`}>
        <i style={{ width: `${usagePercent(quota.used, quota.limit)}%` }} />
      </div>
      <div className="quota-row-foot">
        <small>{t.usedOf(quota.used, quota.limit)}</small>
        {resetCell(quota.resetsAt)}
      </div>
    </li>
  );

  const windowRow = (window: SubscriptionWindowRow) => (
    <li className="quota-window" key={`${window.productId}:${window.capability}`}>
      <div className="quota-row-head">
        <span className="quota-capability">{capabilityLabel(window.capability, locale)}</span>
        <span className={`quota-window-state ${window.state.toLowerCase()}`}>
          {window.state === "ACTIVE" ? t.stateActive : t.stateOther(window.state)}
        </span>
      </div>
      <div className="quota-row-foot">
        <small className="muted">{window.productId}</small>
        <small className="quota-renew">
          {window.cancelAtPeriodEnd ? t.cancelAtPeriodEnd : (window.autoRenew ? t.autoRenewOn : t.autoRenewOff)}
        </small>
        {resetCell(window.resetsAt)}
      </div>
    </li>
  );

  return <section className="quota-panel" aria-label={t.aria}>
    <span className="eyebrow">{t.eyebrow}</span>
    <h3>{t.heading}</h3>
    <p className="quota-intro">{t.intro}</p>

    {error !== null && <div className="quota-error" role="alert">
      <p>{t.errorTitle}</p>
      <p className="quota-error-detail">{t.errorDetail(error)}</p>
      <AsyncButton busy={loading} onClick={onLoad}>{t.retry}</AsyncButton>
    </div>}

    {error === null && !loaded && <p className="muted" role="status">{t.loading}</p>}

    {error === null && loaded && <>
      <h4>{t.dailyHeading}</h4>
      <ul className="quota-list">
        {!(view?.quotas ?? []).length
          ? <li className="muted">{t.quotaEmpty}</li>
          : view!.quotas.map(quotaRow)}
      </ul>

      <h4>{t.subscriptionHeading}</h4>
      <ul className="quota-list">
        {!(view?.subscriptionWindows ?? []).length
          ? <li className="muted">
              {t.subscriptionEmpty}
              <small className="quota-empty-note">{t.subscriptionEmptyNote}</small>
            </li>
          : view!.subscriptionWindows.map(windowRow)}
      </ul>
    </>}

      <div className="quota-never">
        <h4>{t.neverHeading}</h4>
        <p>{t.neverNote}</p>
        {(view?.neverPayGated ?? []).length > 0 && (
          <details className="quota-never-details">
            <summary>{t.neverItems(view!.neverPayGated.length)}</summary>
            <ul>
              {view!.neverPayGated.map(capability => (
                <li key={capability}>{capabilityLabel(capability, locale)}</li>
              ))}
            </ul>
          </details>
        )}
      </div>

      {loaded && <div className="quota-foot">
        <AsyncButton className="quota-reload" busy={loading} busyText="…" onClick={onLoad}>{t.reload}</AsyncButton>
      </div>}
  </section>;
}
