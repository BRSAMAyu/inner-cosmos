import { useEffect, useRef, useState, type FormEvent } from "react";
import { AsyncButton } from "../loading";
import type { Locale } from "../i18n";
import { PcmWavRecorder } from "../audio-recorder";

export type AuroraUiMessage = { key: string; speaker: "USER" | "AURORA"; text: string; partial?: boolean };

/** The two pre-speech beats worth showing inline; `null` while idle or actively streaming tokens. */
export type AuroraThinkingStage = "understanding" | "composing" | null;

const COPY: Record<Locale, {
  convAria: string; empty: string; speakerYou: string; partialHint: string; thinkingAria: string;
  understanding: string; composing: string; placeholderActive: string; placeholderIdle: string;
  writeAria: string; transcribing: string; micStop: string; micStart: string; recStop: string;
  voice: string; stop: string; interruptSend: string; send: string; goodbye: string;
}> = {
  "zh-CN": {
    convAria: "与 Aurora 的对话", empty: "把现在最真实的一句话放在这里。", speakerYou: "你",
    partialHint: "停在这里", thinkingAria: "Aurora 正在思考",
    understanding: "Aurora 正在理解这一刻…", composing: "Aurora 正在组织下一句…",
    placeholderActive: "直接说出新的想法，Aurora 会停下并重新理解…", placeholderIdle: "此刻，你想从哪里说起？",
    writeAria: "写给 Aurora", transcribing: "转写中…", micStop: "停止录音并转写", micStart: "用语音输入",
    recStop: "● 停止录音", voice: "🎤 语音", stop: "停止回应", interruptSend: "打断并发送", send: "发送", goodbye: "沉淀今天"
  },
  "en-SG": {
    convAria: "Conversation with Aurora", empty: "Put the truest thing you feel right now here.", speakerYou: "You",
    partialHint: "Paused here", thinkingAria: "Aurora is thinking",
    understanding: "Aurora is taking in this moment…", composing: "Aurora is composing the next line…",
    placeholderActive: "Just say the new thought — Aurora will pause and re-understand…", placeholderIdle: "Where would you like to begin, right now?",
    writeAria: "Write to Aurora", transcribing: "Transcribing…", micStop: "Stop recording and transcribe", micStart: "Use voice input",
    recStop: "● Stop recording", voice: "🎤 Voice", stop: "Stop responding", interruptSend: "Interrupt & send", send: "Send", goodbye: "Settle today"
  }
};

export function AuroraConversation({ messages, activeTurnId, thinkingStage = null, draft, sessionReady, onDraftChange, onSubmit, onStop, onTranscribe, onGoodbye, locale = "zh-CN" }: {
  messages: AuroraUiMessage[];
  activeTurnId: number | null;
  /** Derived from the session runtime signal; drives an inline "thinking" beat where the user is
   * looking, instead of only a far-away hero badge. */
  thinkingStage?: AuroraThinkingStage;
  draft: string;
  sessionReady: boolean;
  onDraftChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onStop: () => void;
  onTranscribe?: (blob: Blob) => Promise<string>;
  /** Triggers the "沉淀今天/温柔告别" ritual (GoodbyeOrchestrator). Only offered while idle and
   * ready -- a deliberate closing action, never available mid-stream. */
  onGoodbye?: () => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [recording, setRecording] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const [audioLevel, setAudioLevel] = useState(0);
  const [speaking, setSpeaking] = useState(false);
  const [voiceError, setVoiceError] = useState<string | null>(null);
  const recorderRef = useRef<PcmWavRecorder | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const draftRef = useRef(draft);
  draftRef.current = draft;
  // Gemini audit 4.6 (CONFIRMED/P2): mountedRef + a per-attempt generation counter guard the
  // transcription-result *application*, not the recorder itself. Every stop-recording-and-
  // transcribe attempt is its own generation; a still-in-flight onTranscribe() promise from an
  // earlier attempt (superseded by a newer recording, or outlived by an unmount) must never write
  // onDraftChange/setTranscribing on the caller's behalf once it finally resolves.
  const mountedRef = useRef(true);
  const generationRef = useRef(0);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages, thinkingStage]);
  const voiceSupported = typeof navigator !== "undefined"
    && !!navigator.mediaDevices?.getUserMedia && typeof AudioContext !== "undefined";

  const finishRecording = async (cancel = false) => {
    const recorder = recorderRef.current;
    recorderRef.current = null;
    const generation = ++generationRef.current;
    setRecording(false); setAudioLevel(0); setSpeaking(false);
    if (!recorder) return;
    const blob = await recorder.stop(cancel);
    if (!blob || cancel || !onTranscribe) return;
    setTranscribing(true);
    try {
      const text = await onTranscribe(blob);
      if (text && mountedRef.current && generation === generationRef.current) {
        onDraftChange((draftRef.current ? `${draftRef.current} ` : "") + text);
      }
    } catch { /* upstream status keeps the text draft intact */ }
    finally {
      if (mountedRef.current && generation === generationRef.current) setTranscribing(false);
    }
  };

  useEffect(() => () => { mountedRef.current = false; void finishRecording(true); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const startRecording = async () => {
    if (!onTranscribe || !voiceSupported) return;
    setVoiceError(null);
    try {
      const recorder = new PcmWavRecorder();
      recorderRef.current = recorder;
      await recorder.start((level, voice) => { setAudioLevel(level); setSpeaking(voice); }, () => void finishRecording());
      setRecording(true);
    } catch (error) {
      recorderRef.current = null; setRecording(false);
      const reason = error instanceof DOMException || error instanceof Error ? error.name : "RecorderError";
      setVoiceError(reason || "RecorderError");
    }
  };

  return <>
    <section className={`conversation ${messages.length === 0 ? "empty-state" : "has-messages"}`}
      aria-label={t.convAria}>
      {messages.length === 0 && <div className="empty"><span>✦</span><p>{t.empty}</p></div>}
      {messages.map(message => <article className={`message ${message.speaker.toLowerCase()} ${message.partial ? "partial" : ""}`} key={message.key}
        aria-live={message.partial ? "polite" : undefined}>
        <span className="speaker">{message.speaker === "AURORA" ? "Aurora" : t.speakerYou}</span>
        <p>{message.text || "…"}</p>
        {message.partial && message.text && <small>{t.partialHint}</small>}
      </article>)}
      {activeTurnId !== null && thinkingStage && <article className={`message aurora thinking ${thinkingStage}`} aria-label={t.thinkingAria}>
        <span className="speaker">Aurora</span>
        <p><span className="thinking-dots" aria-hidden="true"><i></i><i></i><i></i></span>{thinkingStage === "understanding" ? t.understanding : t.composing}</p>
      </article>}
      <div ref={bottomRef} aria-hidden="true" />
    </section>
    <form className="composer" onSubmit={onSubmit}>
      <textarea value={draft} onChange={event => onDraftChange(event.target.value)}
        placeholder={activeTurnId ? t.placeholderActive : t.placeholderIdle}
        aria-label={t.writeAria} onKeyDown={event => {
          if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); }
        }} />
      <div className="actions">
        {onTranscribe && voiceSupported && <AsyncButton
          className={"voice" + (recording ? " recording" : "")}
          disabled={!sessionReady}
          busy={transcribing}
          busyText={t.transcribing}
          aria-pressed={recording}
          aria-label={recording ? t.micStop : t.micStart}
          onClick={() => recording ? void finishRecording() : void startRecording()}>
          {recording ? t.recStop : t.voice}</AsyncButton>}
        {recording && <>
          <span className={`voice-meter ${speaking ? "speaking" : ""}`} role="meter" aria-label={locale === "zh-CN" ? "录音音量" : "Recording level"}
            aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(audioLevel * 100)}>
            <i style={{ transform: `scaleX(${Math.max(.04, audioLevel)})` }} />
          </span>
          <button type="button" className="voice-cancel" onClick={() => void finishRecording(true)}>
            {locale === "zh-CN" ? "取消录音" : "Cancel recording"}
          </button>
        </>}
        {voiceError && <span className="voice-error" role="status" data-error={voiceError}>
          {locale === "zh-CN" ? "暂时无法启动录音；文字草稿仍然安全保留。" : "Recording could not start; your text draft is still safe."}
        </span>}
        {activeTurnId && <button type="button" className="stop" onClick={onStop}>{t.stop}</button>}
        {onGoodbye && !activeTurnId && sessionReady && <button type="button" className="goodbye-trigger" onClick={onGoodbye}>{t.goodbye}</button>}
        <button type="submit" className="send" disabled={!draft.trim() || !sessionReady}>{activeTurnId ? t.interruptSend : t.send}</button>
      </div>
    </form>
  </>;
}
