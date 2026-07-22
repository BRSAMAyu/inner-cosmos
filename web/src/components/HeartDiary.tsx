import { useRef, useState } from "react";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";
import { PcmWavRecorder } from "../audio-recorder";

// Port of src/main/resources/static/pages/heart-diary.html into the AppShell (Phase 3, legacy batch
// B). Mic capture follows the same PcmWavRecorder lifecycle as AuroraConversation.tsx (start/stop,
// level meter, auto-stop, visibility-based auto-stop) -- see audio-recorder.ts.

const LEVELS = [0, 1, 2, 3] as const;
type PolishLevel = typeof LEVELS[number];

const COPY: Record<Locale, {
  aria: string; heading: string; intro: string; textareaAria: string; placeholder: string;
  startRecording: string; stopRecording: string; recording: string; transcribing: string;
  voiceUnavailable: string; submit: string; polishHeading: string; polishIntro: string;
  polishTabsAria: string; levelLabel: Record<PolishLevel, string>; levelDescription: Record<PolishLevel, string>;
  polishing: string;
}> = {
  "zh-CN": {
    aria: "心声日记", heading: "心声日记", intro: "不必有逻辑地去写，甚至可以直接用麦克风倾诉。我们为你提取意义。",
    textareaAria: "心声日记正文", placeholder: "在这里倾泻下今天的混乱、碎片或者平静...",
    startRecording: "🎤 开始倾诉录音", stopRecording: "■ 结束录音", recording: "正在录音…", transcribing: "转写中…",
    voiceUnavailable: "暂时无法启动录音；文字草稿仍然安全保留。", submit: "将心声化作记忆星宿",
    polishHeading: "心声整理与润色",
    polishIntro: "语音输入或散乱拼写的原文通常带有很多冗余。你可以选择不同的档位让 Aurora 协助梳理，但我们始终会保留你的初心。",
    polishTabsAria: "润色档位",
    levelLabel: { 0: "原文", 1: "净化", 2: "梳理", 3: "重塑" },
    levelDescription: {
      0: "没有经过任何修饰的倾诉记录，可能会显得有些凌乱，但这最真实。",
      1: "过滤了你表达中的口头词和拼写语法失误，完全保留原话顺序和你的口吻。",
      2: "在不改变你主观表达意图的前提下，修正乱序表达，梳理逻辑，提升阅读的流畅度。",
      3: "由 Aurora 对你的倾诉进行文学化的提炼与段落排版重构，让它成为一篇文字优美的反思日记。"
    },
    polishing: "Aurora 正在凝神，帮你修饰字句..."
  },
  "en-SG": {
    aria: "Heart diary", heading: "Heart diary",
    intro: "Write without needing logic — or just speak into the microphone. We surface the meaning for you.",
    textareaAria: "Heart diary entry", placeholder: "Pour out today's chaos, fragments, or calm here...",
    startRecording: "🎤 Start speaking", stopRecording: "■ Stop recording", recording: "Recording…", transcribing: "Transcribing…",
    voiceUnavailable: "Recording could not start; your text draft is still safe.", submit: "Turn this into a memory star",
    polishHeading: "Tidying and polishing",
    polishIntro: "Voice input or scattered typing usually carries a lot of redundancy. Pick a level for Aurora to help tidy it up — we always keep your original intent.",
    polishTabsAria: "Polish levels",
    levelLabel: { 0: "Original", 1: "Cleaned", 2: "Organized", 3: "Reshaped" },
    levelDescription: {
      0: "The unedited record of what you said — it may look a bit messy, but it's the most real.",
      1: "Filler words and spelling/grammar slips are filtered out, keeping your order and tone exactly.",
      2: "Without changing your intent, out-of-order phrasing is fixed and the logic tidied for easier reading.",
      3: "Aurora literarily reworks your words and paragraphing, turning it into a well-written reflective entry."
    },
    polishing: "Aurora is focusing, polishing your words..."
  }
};

export function HeartDiary({ rawText, displayText, activeLevel, polishBusy, submitBusy, onTextChange, onSwitchLevel,
  onTranscribeAudio, onSubmit, locale = "zh-CN" }: {
  rawText: string; displayText: string; activeLevel: number; polishBusy: boolean; submitBusy: boolean;
  onTextChange: (text: string) => void; onSwitchLevel: (level: number) => void;
  onTranscribeAudio: (blob: Blob) => Promise<void>; onSubmit: () => void; locale?: Locale;
}) {
  const t = COPY[locale];
  const [recording, setRecording] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const [voiceError, setVoiceError] = useState<string | null>(null);
  const recorderRef = useRef<PcmWavRecorder | null>(null);
  const voiceSupported = typeof navigator !== "undefined"
    && !!navigator.mediaDevices?.getUserMedia && typeof AudioContext !== "undefined";

  const finishRecording = async (cancel = false) => {
    const recorder = recorderRef.current;
    recorderRef.current = null;
    setRecording(false);
    if (!recorder) return;
    const blob = await recorder.stop(cancel);
    if (!blob || cancel) return;
    setTranscribing(true);
    try { await onTranscribeAudio(blob); } finally { setTranscribing(false); }
  };

  const startRecording = async () => {
    if (!voiceSupported) return;
    setVoiceError(null);
    try {
      const recorder = new PcmWavRecorder();
      recorderRef.current = recorder;
      await recorder.start(undefined, () => void finishRecording());
      setRecording(true);
    } catch (error) {
      recorderRef.current = null;
      setRecording(false);
      setVoiceError(error instanceof Error ? error.name : "RecorderError");
    }
  };

  const showPolish = rawText.trim().length > 5;

  return <section className="heart-diary-space" aria-label={t.aria}>
    <span className="eyebrow">HEART DIARY</span>
    <h2>{t.heading}</h2>
    <p>{t.intro}</p>

    <div className="diary-paper">
      <textarea className="diary-textarea" aria-label={t.textareaAria} placeholder={t.placeholder}
        value={displayText} onChange={event => onTextChange(event.target.value)} />
    </div>

    <div className="diary-actions">
      {voiceSupported && <AsyncButton className={"voice-pill" + (recording ? " recording" : "")}
        busy={recording || transcribing} busyText={recording ? t.recording : t.transcribing}
        aria-pressed={recording} onClick={() => (recording ? void finishRecording() : void startRecording())}>
        {recording ? t.stopRecording : t.startRecording}
      </AsyncButton>}
      {voiceError && <span className="voice-error" role="status">{t.voiceUnavailable}</span>}
      <AsyncButton className="diary-submit" busy={submitBusy} onClick={onSubmit} disabled={displayText.trim().length < 5}>
        {t.submit}
      </AsyncButton>
    </div>

    {showPolish && <div className="diary-polish-panel">
      <h3>{t.polishHeading}</h3>
      <p className="muted">{t.polishIntro}</p>
      <div className="polish-tabs" role="tablist" aria-label={t.polishTabsAria}>
        {LEVELS.map(level => <button key={level} type="button" role="tab" aria-selected={activeLevel === level}
          className={activeLevel === level ? "active" : ""} onClick={() => onSwitchLevel(level)}>{t.levelLabel[level]}</button>)}
      </div>
      <p className="polish-note">{t.levelDescription[(activeLevel in t.levelDescription ? activeLevel : 0) as PolishLevel]}</p>
      {polishBusy && <p className="polish-loading">{t.polishing}</p>}
    </div>}
  </section>;
}
