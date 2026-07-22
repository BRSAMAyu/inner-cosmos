import { useState } from "react";
import type { AiHealth, ShredderHistoryEntry, ShredderResult } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// Phase 3 legacy-page port: src/main/resources/static/pages/thought-shredder.html.
type SaveMode = "KEEP_RAW" | "KEEP_ONLY_RESULT" | "DISPLAY_ONCE";

const COPY: Record<Locale, {
  heading: string; intro: string; inputLabel: string; placeholder: string; saveModeLabel: string;
  keepRaw: string; keepResult: string; displayOnce: string; submit: string; submitBusy: string;
  resultHeading: string; resultPlaceholder: string; coreFeeling: string; hiddenNeed: string;
  noise: string; keepSentence: string; settle: string; delete: string; historyHeading: string;
  historyEmpty: string; gravity: string; notConfigured: string; configured: string;
  fallbackOn: string; fallbackOff: string; failed: string;
}> = {
  "zh-CN": {
    heading: "思维碎纸机", intro: "把混乱、愤怒、焦虑、碎碎念一次性倒进来。它会帮你把噪音和真正的需求分开。",
    inputLabel: "把想法倒进来", placeholder: "把混乱、愤怒、焦虑、碎碎念一次性倒进来...", saveModeLabel: "保存模式",
    keepRaw: "保留原始", keepResult: "仅保留结果", displayOnce: "仅看一次", submit: "粉碎并沉淀", submitBusy: "正在粉碎...",
    resultHeading: "粉碎结果", resultPlaceholder: "粉碎后，结果会出现在这里。", coreFeeling: "核心感受",
    hiddenNeed: "隐藏需求", noise: "可以放下的噪音", keepSentence: "值得保留的一句话", settle: "沉淀到记忆",
    delete: "删除", historyHeading: "历史粉碎记录", historyEmpty: "还没有粉碎记录。", gravity: "重力",
    notConfigured: "未配置真实模型密钥", configured: "真实模型已配置", fallbackOn: "开发 fallback 可用",
    fallbackOff: "fallback 关闭", failed: "粉碎未能完成，请重试。"
  },
  "en-SG": {
    heading: "Thought Shredder", intro: "Pour in the chaos, anger, anxiety, or rambling all at once. It helps separate the noise from the real need.",
    inputLabel: "Pour your thoughts in", placeholder: "Pour in the chaos, anger, anxiety, or rambling all at once...", saveModeLabel: "Save mode",
    keepRaw: "Keep raw text", keepResult: "Keep result only", displayOnce: "View once", submit: "Shred and settle", submitBusy: "Shredding...",
    resultHeading: "Shredded result", resultPlaceholder: "After shredding, the result appears here.", coreFeeling: "Core feeling",
    hiddenNeed: "Hidden need", noise: "Noise you can drop", keepSentence: "A sentence worth keeping", settle: "Settle into memory",
    delete: "Delete", historyHeading: "Shredding history", historyEmpty: "No shredding records yet.", gravity: "gravity",
    notConfigured: "no real model key configured", configured: "real model configured", fallbackOn: "dev fallback available",
    fallbackOff: "fallback off", failed: "Shredding didn't complete. Please try again."
  }
};

export function ThoughtShredderSection({ aiHealth, history, result, busy, onShred, onSettle, onDelete, locale = "zh-CN" }: {
  aiHealth: AiHealth | null; history: ShredderHistoryEntry[]; result: ShredderResult | null; busy: boolean;
  onShred: (text: string, saveMode: SaveMode) => void; onSettle: (id: number) => void; onDelete: (id: number) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [text, setText] = useState("");
  const [saveMode, setSaveMode] = useState<SaveMode>("KEEP_ONLY_RESULT");

  return <section className="thought-shredder-section" aria-label={t.heading}>
    <h2>{t.heading}</h2>
    <p className="muted mb-2">{t.intro}</p>

    {aiHealth && <p className="muted mb-2" style={{ fontSize: ".82rem" }}>
      {aiHealth.provider ?? "-"} / {aiHealth.model ?? "-"} · {aiHealth.apiKeyConfigured ? t.configured : t.notConfigured} · {aiHealth.fallbackAllowed ? t.fallbackOn : t.fallbackOff}
    </p>}

    <div className="grid">
      <article className="shred-paper" style={{ padding: 20 }}>
        <div className="field-group mb-2">
          <label htmlFor="shredText">{t.inputLabel}</label>
          <textarea id="shredText" placeholder={t.placeholder} value={text} onChange={event => setText(event.target.value)}
            style={{ minHeight: 200, width: "100%" }} />
        </div>
        <div className="field-group mb-2">
          <label htmlFor="shredSaveMode">{t.saveModeLabel}</label>
          <select id="shredSaveMode" aria-label={t.saveModeLabel} value={saveMode} onChange={event => setSaveMode(event.target.value as SaveMode)}>
            <option value="KEEP_RAW">{t.keepRaw}</option>
            <option value="KEEP_ONLY_RESULT">{t.keepResult}</option>
            <option value="DISPLAY_ONCE">{t.displayOnce}</option>
          </select>
        </div>
        <AsyncButton busy={busy} busyText={t.submitBusy} className="shred-btn"
          onClick={() => onShred(text, saveMode)}>{t.submit}</AsyncButton>
      </article>

      <article className="panel" style={{ position: "relative", overflow: "hidden" }}>
        <h3>{t.resultHeading}</h3>
        {!result
          ? <p className="muted">{t.resultPlaceholder}</p>
          : <div className="result-section">
              <h4>{t.coreFeeling}</h4>
              <p className="gold">{result.coreFeeling}</p>
              <h4 className="mt-2">{t.hiddenNeed}</h4>
              <p className="muted">{result.hiddenNeed}</p>
              <h4 className="mt-2">{t.noise}</h4>
              <p className="muted">{result.noiseToDrop.join("；")}</p>
              <h4 className="mt-2">{t.keepSentence}</h4>
              <p className="teal">{result.sentenceToKeep}</p>
              <div className="row gap-sm mt-2">
                <button type="button" onClick={() => onSettle(result.memoryCard.id)}>{t.settle}</button>
                <button type="button" onClick={() => onDelete(result.memoryCard.id)}>{t.delete}</button>
              </div>
            </div>}
      </article>
    </div>

    <div className="spacer" />

    <section className="panel">
      <h3>{t.historyHeading}</h3>
      {history.length === 0
        ? <p className="empty">{t.historyEmpty}</p>
        : <div className="grid">
            {history.slice(0, 6).map(h => <article className="card" key={h.id} style={{ minHeight: 80 }}>
              <strong>{h.title}</strong>
              <p className="muted">{(h.summary ?? "").slice(0, 80)}...</p>
              <div className="pill-row mt-1">
                <span className="pill">{h.memoryType ?? "碎纸"}</span>
                <span className="pill">{t.gravity} {(h.emotionalGravity ?? 0).toFixed(2)}</span>
              </div>
            </article>)}
          </div>}
    </section>
  </section>;
}
