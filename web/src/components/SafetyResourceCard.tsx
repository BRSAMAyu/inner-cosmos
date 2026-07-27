import { useEffect, useRef } from "react";
import type { AuroraSafetyAlert } from "../hooks/useAuroraSession";
import type { SafetyResource } from "../api";
import type { Locale } from "../i18n";
import { SafetyResourceList } from "./SafetyResourceList";

// Phase 0 (safety-critical, jumps the queue ahead of feature polish): the backend safety pipeline
// (CrisisKeywordRule/SafetyReviewService) has been independently audited and is solid, but until this
// component existed its "safety" SSE event only reached the user as one line in the shared, low-visual-
// weight global status banner -- easily overwritten by the very next status update. This card is
// deliberately persistent (stays mounted until the user explicitly dismisses it) and visually distinct
// (role="alert", warm-but-urgent accent, not just more banner text).
//
// `resources` comes from the region-bound catalog and carries its own verified authority metadata.
const COPY = {
  "zh-CN": {
    heading: "先照顾好自己",
    gentleHeading: "先确认一下此刻的安全",
    gentleResources: "需要时展开支持资源",
    genericSafety: "如果你现在有生命危险，请立即拨打当地紧急电话，或前往最近的急诊室。",
    dial: "拨打 ",
    openHarbor: "打开安全避风港（呼吸练习与着陆练习）",
    dismiss: "我看到了，先关闭"
  },
  "en-SG": {
    heading: "Take care of yourself first",
    gentleHeading: "A gentle safety check-in",
    gentleResources: "Expand local support when you want it",
    genericSafety: "If you are in immediate danger, please call your local emergency number now, or go to the nearest emergency department.",
    dial: "Call ",
    openHarbor: "Open the safety harbor (breathing & grounding exercises)",
    dismiss: "I've seen this, close for now"
  }
} as const;

export function SafetyResourceCard({ alert, resources, locale, onDismiss, onOpenHarbor }: {
  alert: AuroraSafetyAlert | null;
  resources: SafetyResource[];
  locale: Locale;
  onDismiss: () => void;
  onOpenHarbor?: () => void;
}) {
  const urgentRef = useRef<HTMLElement>(null);
  const high = alert?.riskLevel === "HIGH";
  const gentle = alert?.safetyState === "GENTLE_CHECK_IN";
  useEffect(() => {
    if (high) urgentRef.current?.focus();
  }, [high]);
  if (!alert || (!high && !gentle)) return null;
  const t = COPY[locale];
  if (gentle) {
    return (
      <aside className="safety-resource-card is-gentle" role="status" aria-live="polite" lang={locale}>
        <strong>{t.gentleHeading}</strong>
        {alert.safeMessage && <p>{alert.safeMessage}</p>}
        <details>
          <summary>{t.gentleResources}</summary>
          <SafetyResourceList resources={resources} dialLabel={t.dial} />
          {onOpenHarbor && <button type="button" className="quiet" onClick={onOpenHarbor}>{t.openHarbor}</button>}
        </details>
        <button type="button" className="quiet" onClick={onDismiss}>{t.dismiss}</button>
      </aside>
    );
  }
  return (
    <aside ref={urgentRef} tabIndex={-1} className="safety-resource-card" role="alert"
      aria-live="assertive" aria-labelledby="urgent-safety-heading" lang={locale}>
      <strong id="urgent-safety-heading">{t.heading}</strong>
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
