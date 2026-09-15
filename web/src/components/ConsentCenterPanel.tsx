import type { ConsentView } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// CP-07 consent center: the user-facing "what have I agreed to" surface. Every purpose from
// the backend registry renders with its group, current decision and withdrawal effect; only
// user-settable purposes get actions. REQUIRED purposes explain the only exit (account
// deletion) and MANAGED ones point back to the surface that owns them (capsule workbench).

const GROUP_ORDER: ConsentView["group"][] = [
  "REQUIRED", "OPTIONAL_ASK", "OPTIONAL", "SENSITIVE", "MANAGED_ELSEWHERE"
];

const GROUP_LABEL: Record<Locale, Record<ConsentView["group"], string>> = {
  "zh-CN": {
    REQUIRED: "服务必需",
    OPTIONAL_ASK: "按需征求",
    OPTIONAL: "可选",
    SENSITIVE: "敏感信息（单独同意）",
    MANAGED_ELSEWHERE: "由对应功能逐项管理"
  },
  "en-SG": {
    REQUIRED: "Service-necessary",
    OPTIONAL_ASK: "Asked when needed",
    OPTIONAL: "Optional",
    SENSITIVE: "Sensitive (separate consent)",
    MANAGED_ELSEWHERE: "Managed per-use in its own surface"
  }
};

const COPY: Record<Locale, {
  eyebrow: string; heading: string; intro: string; load: string; refresh: string;
  empty: string; granted: string; declined: string; grant: string; revoke: string;
  requiredNote: string; managedNote: string; version: (v: string) => string;
  reConsentBadge: string; reConsentNote: (v: string) => string;
}> = {
  "zh-CN": {
    eyebrow: "数据与同意 · 你说了算",
    heading: "我同意过什么",
    intro: "每一项用途都单独列出：做什么、撤回后发生什么。除了服务必需项，你可以随时改变主意；撤回立即生效。",
    load: "查看我的同意项",
    refresh: "刷新",
    empty: "还没有加载同意信息。",
    granted: "已同意",
    declined: "未同意",
    grant: "同意",
    revoke: "撤回同意",
    requiredNote: "核心功能必需；如不需要，可通过注销账号终止全部处理。",
    managedNote: "在对应功能内逐项授权与撤回（如共鸣体工作台）。",
    version: v => `版本 ${v}`,
    reConsentBadge: "条款已更新，需要重新确认",
    reConsentNote: v => `你上一次的选择记录在旧版条款下，已不再作为当前依据；重新确认后以版本 ${v} 为准。`
  },
  "en-SG": {
    eyebrow: "Data & consent · your call",
    heading: "What I have agreed to",
    intro: "Every purpose is listed on its own: what it does and what withdrawal means. Except for service-necessary items, you can change your mind at any time; withdrawal is effective immediately.",
    load: "View my consents",
    refresh: "Refresh",
    empty: "No consent information loaded yet.",
    granted: "Granted",
    declined: "Not granted",
    grant: "Grant",
    revoke: "Withdraw",
    requiredNote: "Necessary for the core service; the only exit is account deletion.",
    managedNote: "Granted and withdrawn per item inside its own feature (e.g. the capsule workbench).",
    version: v => `version ${v}`,
    reConsentBadge: "Terms updated · re-confirm",
    reConsentNote: v => `Your last choice was recorded under older terms and no longer applies; confirming again records it under version ${v}.`
  }
};

export function ConsentCenterPanel({ views, loading, loaded, onLoad, onDecide, busyPurpose,
  locale = "zh-CN" }: {
  views: ConsentView[];
  loading: boolean;
  loaded: boolean;
  onLoad: () => void;
  onDecide: (purposeCode: string, grant: boolean) => void;
  busyPurpose: string | null;
  locale?: Locale;
}) {
  const copy = COPY[locale];
  const grouped = GROUP_ORDER
    .map(group => ({ group, rows: views.filter(view => view.group === group) }))
    .filter(entry => entry.rows.length > 0);
  return (
    <section className="consent-center" aria-label={copy.heading}>
      <header>
        <p className="eyebrow">{copy.eyebrow}</p>
        <h3>{copy.heading}</h3>
        <p className="intro">{copy.intro}</p>
        <AsyncButton busy={loading} onClick={onLoad}>{loaded ? copy.refresh : copy.load}</AsyncButton>
      </header>
      {loaded && views.length === 0 && <p className="empty">{copy.empty}</p>}
      {loaded && grouped.map(entry => (
        <div key={entry.group} className="consent-group" data-group={entry.group}>
          <h4>{GROUP_LABEL[locale][entry.group]}</h4>
          <ul>
            {entry.rows.map(view => (
              <li key={view.purposeCode} data-purpose={view.purposeCode}
                  data-granted={view.granted}
                  data-re-consent={view.source === "RE_CONSENT_REQUIRED" ? "true" : undefined}>
                <div className="consent-row-head">
                  <strong>{view.purposeCode}</strong>
                  {/* CP-07 versioned re-consent: source === RE_CONSENT_REQUIRED means the
                      recorded decision predates the registry's current version, so a stale
                      grant never authorizes — surface the re-confirm ask on that row only. */}
                  {view.source === "RE_CONSENT_REQUIRED" && (
                    <span className="consent-reconsent-badge">{copy.reConsentBadge}</span>
                  )}
                  <span className={"consent-state " + (view.granted ? "granted" : "declined")}>
                    {view.granted ? copy.granted : copy.declined}
                  </span>
                </div>
                {view.source === "RE_CONSENT_REQUIRED" && (
                  <p className="consent-reconsent-note">{copy.reConsentNote(view.version)}</p>
                )}
                <p className="consent-description">{view.description}</p>
                {view.userSettable && view.granted && (
                  <p className="consent-withdrawal">{view.withdrawalEffect}</p>
                )}
                {entry.group === "REQUIRED" && <p className="consent-note">{copy.requiredNote}</p>}
                {entry.group === "MANAGED_ELSEWHERE" && <p className="consent-note">{copy.managedNote}</p>}
                {view.userSettable && (
                  <div className="consent-actions">
                    <AsyncButton
                      busy={busyPurpose === view.purposeCode}
                      disabled={busyPurpose !== null && busyPurpose !== view.purposeCode}
                      aria-label={`${view.granted ? copy.revoke : copy.grant}: ${view.purposeCode}`}
                      onClick={() => onDecide(view.purposeCode, !view.granted)}>
                      {view.granted ? copy.revoke : copy.grant}
                    </AsyncButton>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      ))}
      {loaded && views.length > 0 && (
        <footer><small>{copy.version(views[0]?.version ?? "")}</small></footer>
      )}
    </section>
  );
}
