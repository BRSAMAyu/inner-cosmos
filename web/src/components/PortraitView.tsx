import { useState } from "react";
import type { PortraitDimension, PortraitHistoryEntry } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const DIM_LABELS: Record<Locale, Record<string, string>> = {
  "zh-CN": {
    INNER_DRIVE: "内驱力", VALUES: "在意的事", SELF_NARRATIVE: "自我叙事",
    COMMUNICATION_STYLE: "表达方式", ABSTRACT_VS_CONCRETE: "抽象与具体",
    EMOTION_PATTERN: "情绪底色", ENERGY_RHYTHM: "精力节律", CURRENT_STATE: "近期状态",
    RELATIONSHIP_CONTEXT: "关系处境", AGENCY_BOUNDARY: "自主边界"
  },
  "en-SG": {
    INNER_DRIVE: "Inner drive", VALUES: "What matters to you", SELF_NARRATIVE: "Self-narrative",
    COMMUNICATION_STYLE: "Communication style", ABSTRACT_VS_CONCRETE: "Abstract vs. concrete",
    EMOTION_PATTERN: "Emotional undertone", ENERGY_RHYTHM: "Energy rhythm", CURRENT_STATE: "Current state",
    RELATIONSHIP_CONTEXT: "Relationship context", AGENCY_BOUNDARY: "Agency & boundaries"
  }
};

const COPY: Record<Locale, {
  aria: string; heading: string; intro: string; empty: string; grasped: (pct: number) => string;
  vague: string; updatedAt: (date: string) => string; viewHistory: string; notMe: string;
  noHistory: string; currentUnderstanding: (value: string) => string; stillVague: string;
  draftPlaceholder: string; notNow: string; tellAurora: string; calibratedNote: string;
}> = {
  "zh-CN": {
    aria: "Aurora 眼中的你", heading: "Aurora 眼中的你",
    intro: "这是 Aurora 一点点观察后，对你形成的理解。它不一定都对——你可以在任何一条上告诉它「这不太是我」，补上你自己的看法。Aurora 会把你的声音和它的观察并在一起，慢慢更懂你。",
    empty: "Aurora 还没有形成对你的理解。多和它聊聊，这里会慢慢长出你的轮廓。",
    grasped: pct => `把握 ${pct}%`, vague: "还很模糊", updatedAt: date => `更新于 ${date}`,
    viewHistory: "看它怎么变的", notMe: "这不太是我", noHistory: "这一面还没有变化记录，或正在回看…",
    currentUnderstanding: value => `Aurora 现在的理解是：${value}`, stillVague: "Aurora 对这一面还很模糊。",
    draftPlaceholder: "比如：我其实不是外向，只是在熟人面前才放得开…", notNow: "先不", tellAurora: "告诉 Aurora",
    calibratedNote: "✓ Aurora 会把你的看法和它的观察并在一起。"
  },
  "en-SG": {
    aria: "How Aurora sees you", heading: "How Aurora sees you",
    intro: "This is what Aurora has come to understand about you, bit by bit. It isn't always right — on any dimension you can tell it \"that's not quite me\" and add your own view. Aurora folds your voice together with its own observation to slowly understand you better.",
    empty: "Aurora hasn't formed an understanding of you yet. Talk with it more, and your outline will slowly grow here.",
    grasped: pct => `Grasped ${pct}%`, vague: "Still vague", updatedAt: date => `Updated ${date}`,
    viewHistory: "See how it changed", notMe: "Not quite me", noHistory: "No change history for this dimension yet, or it's still loading…",
    currentUnderstanding: value => `Aurora's current understanding is: ${value}`, stillVague: "Aurora is still vague about this dimension.",
    draftPlaceholder: "e.g. I'm not actually extroverted — I only open up around people I know well…", notNow: "Not now", tellAurora: "Tell Aurora",
    calibratedNote: "✓ Aurora folds your view together with its own observation."
  }
};

function readableValue(valueJson: string | null): string {
  if (!valueJson) return "";
  const raw = valueJson.trim();
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed === "string") return parsed;
    if (Array.isArray(parsed)) {
      return parsed.map(item => (item && typeof item === "object" ? (item.value ?? item.label ?? JSON.stringify(item)) : String(item))).join("、");
    }
    if (parsed && typeof parsed === "object") {
      return parsed.value ?? parsed.summary ?? parsed.label ?? Object.values(parsed).join("、");
    }
    return String(parsed);
  } catch { return raw; }
}

export function PortraitView({ dimensions, history, calibrated, busyDim, onLoadHistory, onCalibrate, locale = "zh-CN" }: {
  dimensions: PortraitDimension[]; history: Record<string, PortraitHistoryEntry[]>;
  calibrated: Record<string, boolean>; busyDim: string | null;
  onLoadHistory: (dim: string) => void; onCalibrate: (dim: string, oldValue: string, newValue: string) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const dimLabels = DIM_LABELS[locale];
  const [openHistory, setOpenHistory] = useState<Record<string, boolean>>({});
  const [calibratingDim, setCalibratingDim] = useState<string | null>(null);
  const [draft, setDraft] = useState("");

  const sorted = dimensions.filter(d => d.dim).slice().sort((a, b) => (b.confidence ?? 0) - (a.confidence ?? 0));

  return <section className="portrait-space" aria-label={t.aria}>
    <span className="eyebrow">AURORA · SELF MIRROR</span>
    <h2>{t.heading}</h2>
    <p>{t.intro}</p>
    {!sorted.length ? <p className="portrait-empty">{t.empty}</p> :
      <div className="portrait-grid">
        {sorted.map(d => {
          const value = readableValue(d.valueJson);
          const conf = Math.max(0, Math.min(1, d.confidence ?? 0));
          const label = dimLabels[d.dim] ?? d.dim;
          const historyOpen = Boolean(openHistory[d.dim]);
          return <article className={`portrait-dim-card${calibrated[d.dim] ? " calibrated" : ""}`} key={d.dim}>
            <div className="portrait-dim-head"><span className="portrait-dim-name">{label}</span><small>{t.grasped(Math.round(conf * 100))}</small></div>
            <div className="portrait-conf-bar"><div className="portrait-conf-fill" style={{ width: `${Math.round(conf * 100)}%` }} /></div>
            <p className="portrait-dim-value">{value || <span className="muted">{t.vague}</span>}</p>
            <div className="portrait-dim-foot">
              <small className="muted">{d.updatedAt ? t.updatedAt(new Date(d.updatedAt).toLocaleString(locale)) : ""}</small>
              <div className="portrait-dim-actions">
                <button type="button" onClick={() => {
                  if (!historyOpen) onLoadHistory(d.dim);
                  setOpenHistory(current => ({ ...current, [d.dim]: !historyOpen }));
                }}>{t.viewHistory}</button>
                <button type="button" onClick={() => { setCalibratingDim(d.dim); setDraft(""); }}>{t.notMe}</button>
              </div>
            </div>
            {historyOpen && <div className="portrait-history">
              {!(history[d.dim] ?? []).length ? <span className="muted">{t.noHistory}</span> :
                history[d.dim].map((row, index) => <div className="portrait-history-row" key={index}>
                  <span>{readableValue(row.valueJson) || "—"}</span><small className="muted">{new Date(row.recordedAt).toLocaleString(locale)}</small>
                </div>)}
            </div>}
            {calibratingDim === d.dim && <div className="portrait-calibrate">
              <p className="muted">{value ? t.currentUnderstanding(value) : t.stillVague}</p>
              <textarea maxLength={280} value={draft} onChange={event => setDraft(event.target.value)}
                placeholder={t.draftPlaceholder} />
              <div className="portrait-calibrate-actions">
                <button type="button" onClick={() => setCalibratingDim(null)}>{t.notNow}</button>
                <AsyncButton busy={busyDim === d.dim} disabled={!draft.trim()}
                  onClick={() => { onCalibrate(d.dim, value, draft); setCalibratingDim(null); }}>{t.tellAurora}</AsyncButton>
              </div>
            </div>}
            {calibrated[d.dim] && <div className="portrait-note">{t.calibratedNote}</div>}
          </article>;
        })}
      </div>}
  </section>;
}
