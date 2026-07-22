import type { AuroraSafetyAlert } from "../hooks/useAuroraSession";
import type { Locale } from "../i18n";
import { SafetyResourceList } from "./SafetyResourceList";

// Phase 0 (safety-critical, jumps the queue ahead of feature polish): the backend safety pipeline
// (CrisisKeywordRule/SafetyReviewService) has been independently audited and is solid, but until this
// component existed its "safety" SSE event only reached the user as one line in the shared, low-visual-
// weight global status banner -- easily overwritten by the very next status update. This card is
// deliberately persistent (stays mounted until the user explicitly dismisses it) and visually distinct
// (role="alert", warm-but-urgent accent, not just more banner text).
//
// `resources` is the real, already-vetted list from GET /api/safety/resources (SafetyServiceImpl.resources()
// -- see its own M-002 comment), the same content the legacy safety-harbor.html rendered -- never
// invented here. Only the generic "contact local emergency services" line below is hardcoded, because
// it needs no locale-specific verification. Singapore-specific resources are a separate, tracked gap
// (ledger item SG-PRODUCT) pending local/legal verification -- do not add specific SG hotline numbers
// here until that sign-off exists.
const COPY = {
  "zh-CN": {
    heading: "先照顾好自己",
    genericSafety: "如果你现在有生命危险，请立即拨打当地紧急电话，或前往最近的急诊室。",
    dial: "拨打 ",
    openHarbor: "打开安全避风港（呼吸练习与着陆练习）",
    dismiss: "我看到了，先关闭"
  },
  "en-SG": {
    heading: "Take care of yourself first",
    genericSafety: "If you are in immediate danger, please call your local emergency number now, or go to the nearest emergency department.",
    dial: "Call ",
    openHarbor: "Open the safety harbor (breathing & grounding exercises)",
    dismiss: "I've seen this, close for now"
  }
} as const;

export function SafetyResourceCard({ alert, resources, locale, onDismiss, onOpenHarbor }: {
  alert: AuroraSafetyAlert | null;
  resources: string[];
  locale: Locale;
  onDismiss: () => void;
  onOpenHarbor?: () => void;
}) {
  if (!alert) return null;
  const t = COPY[locale];
  return (
    <aside className="safety-resource-card" role="alert" aria-live="assertive" lang={locale}>
      <strong>{t.heading}</strong>
      {alert.safeMessage && <p>{alert.safeMessage}</p>}
      <p>{t.genericSafety}</p>
      <SafetyResourceList resources={resources} dialLabel={t.dial} />
      <div className="safety-resource-card-actions">
        {onOpenHarbor && <button type="button" className="quiet" onClick={onOpenHarbor}>{t.openHarbor}</button>}
        <button type="button" onClick={onDismiss}>{t.dismiss}</button>
      </div>
    </aside>
  );
}
