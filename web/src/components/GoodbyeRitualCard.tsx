import type { GoodbyeResult } from "../api";
import type { Locale } from "../i18n";

const COPY: Record<Locale, { heading: string; settled: string; dismiss: string }> = {
  "zh-CN": { heading: "沉淀今天", settled: "重要的部分已经安静地留住了。", dismiss: "好，先到这里" },
  "en-SG": { heading: "Settling today", settled: "The important parts have been quietly kept.", dismiss: "Okay, that's enough for now" }
};

/**
 * A distinct closing-recap treatment for Aurora's "沉淀今天/温柔告别" ritual — deliberately not
 * another chat bubble, since GoodbyeOrchestrator's async SessionCloser steps are a real structural
 * beat (consolidation, not just more conversation).
 */
export function GoodbyeRitualCard({ result, locale, onDismiss }: {
  result: GoodbyeResult | null; locale: Locale; onDismiss: () => void;
}) {
  if (!result) return null;
  const t = COPY[locale];
  return <aside className="goodbye-ritual-card" role="status" aria-live="polite" lang={locale}>
    <div className="goodbye-ritual-glow" aria-hidden="true"><span /></div>
    <div className="goodbye-ritual-body">
      <span className="eyebrow">{t.heading}</span>
      <p className="goodbye-ritual-line">{result.line}</p>
      <small>{t.settled}</small>
      <button type="button" onClick={onDismiss}>{t.dismiss}</button>
    </div>
  </aside>;
}
