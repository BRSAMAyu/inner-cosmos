import { useEffect, useState } from "react";
import type { Locale } from "../i18n";
import type { AuroraInnerVoice } from "./AuroraConversation";
import { InlineAudioPlayer } from "./shared/InlineAudioPlayer";

const COPY: Record<Locale, {
  eyebrow: string; title: string; hint: string; reveal: string; hide: string; dismiss: string;
}> = {
  "zh-CN": {
    eyebrow: "AURORA · 未说出口",
    title: "后来浮现的一点心声",
    hint: "它不属于这轮回复，也不要求你回应。",
    reveal: "轻轻展开",
    hide: "收起",
    dismiss: "让它散去"
  },
  "en-SG": {
    eyebrow: "AURORA · UNSAID",
    title: "A thought that surfaced later",
    hint: "It is not part of the reply, and asks nothing from you.",
    reveal: "Reveal gently",
    hide: "Fold away",
    dismiss: "Let it pass"
  }
};

export function AuroraInnerVoiceAside({ voice, enabled, mode, locale = "zh-CN", onDismiss }: {
  voice: AuroraInnerVoice | null;
  enabled: boolean;
  mode: "AMBIENT" | "ON_DEMAND";
  locale?: Locale;
  onDismiss: () => void;
}) {
  const t = COPY[locale];
  const [revealed, setRevealed] = useState(mode === "AMBIENT");

  useEffect(() => {
    setRevealed(mode === "AMBIENT");
  }, [voice?.key, mode]);

  if (!enabled || !voice) return null;

  return <aside className={`aurora-inner-channel ${revealed ? "revealed" : "veiled"}`}
    aria-label={t.title}>
    <div className="aurora-inner-glow" aria-hidden="true" />
    <div className="aurora-inner-copy">
      <span className="eyebrow">{t.eyebrow}</span>
      <strong>{t.title}</strong>
      <small>{t.hint}</small>
      {revealed && <p className="ugc-text">{voice.text}</p>}
    </div>
    <div className="aurora-inner-actions">
      <button type="button" className="aurora-inner-reveal" onClick={() => setRevealed(value => !value)}>
        {revealed ? t.hide : t.reveal}
      </button>
      {revealed && voice.audio && <InlineAudioPlayer audio={voice.audio} autoPlay={false} locale={locale} />}
      <button type="button" className="aurora-inner-dismiss" onClick={onDismiss}>{t.dismiss}</button>
    </div>
  </aside>;
}
