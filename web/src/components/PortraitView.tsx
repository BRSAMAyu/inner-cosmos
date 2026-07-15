import { useState } from "react";
import type { PortraitDimension, PortraitHistoryEntry } from "../api";

const DIM_LABELS: Record<string, string> = {
  INNER_DRIVE: "内驱力", VALUES: "在意的事", SELF_NARRATIVE: "自我叙事",
  COMMUNICATION_STYLE: "表达方式", ABSTRACT_VS_CONCRETE: "抽象与具体",
  EMOTION_PATTERN: "情绪底色", ENERGY_RHYTHM: "精力节律", CURRENT_STATE: "近期状态",
  RELATIONSHIP_CONTEXT: "关系处境", AGENCY_BOUNDARY: "自主边界"
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

export function PortraitView({ dimensions, history, calibrated, busyDim, onLoadHistory, onCalibrate }: {
  dimensions: PortraitDimension[]; history: Record<string, PortraitHistoryEntry[]>;
  calibrated: Record<string, boolean>; busyDim: string | null;
  onLoadHistory: (dim: string) => void; onCalibrate: (dim: string, oldValue: string, newValue: string) => void;
}) {
  const [openHistory, setOpenHistory] = useState<Record<string, boolean>>({});
  const [calibratingDim, setCalibratingDim] = useState<string | null>(null);
  const [draft, setDraft] = useState("");

  const sorted = dimensions.filter(d => d.dim).slice().sort((a, b) => (b.confidence ?? 0) - (a.confidence ?? 0));

  return <section className="portrait-space" aria-label="Aurora 眼中的你">
    <span className="eyebrow">AURORA · SELF MIRROR</span>
    <h2>Aurora 眼中的你</h2>
    <p>这是 Aurora 一点点观察后，对你形成的理解。它不一定都对——你可以在任何一条上告诉它「这不太是我」，补上你自己的看法。Aurora 会把你的声音和它的观察并在一起，慢慢更懂你。</p>
    {!sorted.length ? <p className="portrait-empty">Aurora 还没有形成对你的理解。多和它聊聊，这里会慢慢长出你的轮廓。</p> :
      <div className="portrait-grid">
        {sorted.map(d => {
          const value = readableValue(d.valueJson);
          const conf = Math.max(0, Math.min(1, d.confidence ?? 0));
          const label = DIM_LABELS[d.dim] ?? d.dim;
          const historyOpen = Boolean(openHistory[d.dim]);
          return <article className={`portrait-dim-card${calibrated[d.dim] ? " calibrated" : ""}`} key={d.dim}>
            <div className="portrait-dim-head"><span className="portrait-dim-name">{label}</span><small>把握 {Math.round(conf * 100)}%</small></div>
            <div className="portrait-conf-bar"><div className="portrait-conf-fill" style={{ width: `${Math.round(conf * 100)}%` }} /></div>
            <p className="portrait-dim-value">{value || <span className="muted">还很模糊</span>}</p>
            <div className="portrait-dim-foot">
              <small className="muted">{d.updatedAt ? `更新于 ${new Date(d.updatedAt).toLocaleString()}` : ""}</small>
              <div className="portrait-dim-actions">
                <button type="button" onClick={() => {
                  if (!historyOpen) onLoadHistory(d.dim);
                  setOpenHistory(current => ({ ...current, [d.dim]: !historyOpen }));
                }}>看它怎么变的</button>
                <button type="button" onClick={() => { setCalibratingDim(d.dim); setDraft(""); }}>这不太是我</button>
              </div>
            </div>
            {historyOpen && <div className="portrait-history">
              {!(history[d.dim] ?? []).length ? <span className="muted">这一面还没有变化记录，或正在回看…</span> :
                history[d.dim].map((row, index) => <div className="portrait-history-row" key={index}>
                  <span>{readableValue(row.valueJson) || "—"}</span><small className="muted">{new Date(row.recordedAt).toLocaleString()}</small>
                </div>)}
            </div>}
            {calibratingDim === d.dim && <div className="portrait-calibrate">
              <p className="muted">{value ? `Aurora 现在的理解是：${value}` : "Aurora 对这一面还很模糊。"}</p>
              <textarea maxLength={280} value={draft} onChange={event => setDraft(event.target.value)}
                placeholder="比如：我其实不是外向，只是在熟人面前才放得开…" />
              <div className="portrait-calibrate-actions">
                <button type="button" onClick={() => setCalibratingDim(null)}>先不</button>
                <button type="button" disabled={busyDim === d.dim || !draft.trim()}
                  onClick={() => { onCalibrate(d.dim, value, draft); setCalibratingDim(null); }}>告诉 Aurora</button>
              </div>
            </div>}
            {calibrated[d.dim] && <div className="portrait-note">✓ Aurora 会把你的看法和它的观察并在一起。</div>}
          </article>;
        })}
      </div>}
  </section>;
}
