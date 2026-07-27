import { useEffect, useState } from "react";
import type { Locale } from "../i18n";
import type { AuroraInnerVoice } from "./AuroraConversation";
import { InlineAudioPlayer } from "./shared/InlineAudioPlayer";

const COPY: Record<Locale, {
  eyebrow: string; title: string; hint: string; reveal: string; hide: string; dismiss: string;
}> = {
  "zh-CN": {
    eyebrow: "AURORA · 余响",
    title: "还有一句，她没有说出口",
    hint: "心声只在真正不同于回复时偶尔浮现；你不需要回应。",
    reveal: "让它浮现",
    hide: "轻轻收起",
    dismiss: "让它散去"
  },
  "en-SG": {
    eyebrow: "AURORA · AFTERGLOW",
    title: "There was one thing she did not say aloud",
    hint: "An inner voice appears only when it is truly different from the reply. It asks nothing of you.",
    reveal: "Let it surface",
    hide: "Fold it softly",
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
    aria-label={t.title} aria-live="polite" data-has-audio={Boolean(voice.audio)}>
    <div className="aurora-inner-glow" aria-hidden="true" />
    <div className="aurora-inner-presence" aria-hidden="true">
      <span className="aurora-inner-presence-core" />
      <span className="aurora-inner-presence-trail" />
    </div>
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
