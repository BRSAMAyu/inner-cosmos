import { useRef, useState, type FormEvent } from "react";
import { AsyncButton } from "../loading";
import type { Locale } from "../i18n";

export type AuroraUiMessage = { key: string; speaker: "USER" | "AURORA"; text: string; partial?: boolean };

/** The two pre-speech beats worth showing inline; `null` while idle or actively streaming tokens. */
export type AuroraThinkingStage = "understanding" | "composing" | null;

const COPY: Record<Locale, {
  convAria: string; empty: string; speakerYou: string; partialHint: string; thinkingAria: string;
  understanding: string; composing: string; placeholderActive: string; placeholderIdle: string;
  writeAria: string; transcribing: string; micStop: string; micStart: string; recStop: string;
  voice: string; stop: string; interruptSend: string; send: string;
}> = {
  "zh-CN": {
    convAria: "与 Aurora 的对话", empty: "把现在最真实的一句话放在这里。", speakerYou: "你",
    partialHint: "停在这里", thinkingAria: "Aurora 正在思考",
    understanding: "Aurora 正在理解这一刻…", composing: "Aurora 正在组织下一句…",
    placeholderActive: "直接说出新的想法，Aurora 会停下并重新理解…", placeholderIdle: "此刻，你想从哪里说起？",
    writeAria: "写给 Aurora", transcribing: "转写中…", micStop: "停止录音并转写", micStart: "用语音输入",
    recStop: "● 停止录音", voice: "🎤 语音", stop: "停止回应", interruptSend: "打断并发送", send: "发送"
  },
  "en-SG": {
    convAria: "Conversation with Aurora", empty: "Put the truest thing you feel right now here.", speakerYou: "You",
    partialHint: "Paused here", thinkingAria: "Aurora is thinking",
    understanding: "Aurora is taking in this moment…", composing: "Aurora is composing the next line…",
    placeholderActive: "Just say the new thought — Aurora will pause and re-understand…", placeholderIdle: "Where would you like to begin, right now?",
    writeAria: "Write to Aurora", transcribing: "Transcribing…", micStop: "Stop recording and transcribe", micStart: "Use voice input",
    recStop: "● Stop recording", voice: "🎤 Voice", stop: "Stop responding", interruptSend: "Interrupt & send", send: "Send"
  }
};

export function AuroraConversation({ messages, activeTurnId, thinkingStage = null, draft, sessionReady, onDraftChange, onSubmit, onStop, onTranscribe, locale = "zh-CN" }: {
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
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [recording, setRecording] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const voiceSupported = typeof navigator !== "undefined"
    && !!navigator.mediaDevices?.getUserMedia && typeof MediaRecorder !== "undefined";

  const startRecording = async () => {
    if (!onTranscribe || !voiceSupported) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const rec = new MediaRecorder(stream);
      chunksRef.current = [];
      rec.ondataavailable = event => { if (event.data.size > 0) chunksRef.current.push(event.data); };
      rec.onstop = async () => {
        stream.getTracks().forEach(track => track.stop());
        const blob = new Blob(chunksRef.current, { type: rec.mimeType || "audio/webm" });
        setTranscribing(true);
        try { const text = await onTranscribe(blob); if (text) onDraftChange((draft ? draft + " " : "") + text); }
        catch { /* surfaced upstream via status; keep the composer usable */ }
        finally { setTranscribing(false); }
      };
      recorderRef.current = rec;
      rec.start();
      setRecording(true);
    } catch { setRecording(false); }
  };
  const stopRecording = () => { recorderRef.current?.stop(); setRecording(false); };

  return <>
    <section className={`conversation ${messages.length === 0 ? "empty-state" : "has-messages"}`}
      aria-live="polite" aria-label={t.convAria}>
      {messages.length === 0 && <div className="empty"><span>✦</span><p>{t.empty}</p></div>}
      {messages.map(message => <article className={`message ${message.speaker.toLowerCase()} ${message.partial ? "partial" : ""}`} key={message.key}>
        <span className="speaker">{message.speaker === "AURORA" ? "Aurora" : t.speakerYou}</span>
        <p>{message.text || "…"}</p>
        {message.partial && message.text && <small>{t.partialHint}</small>}
      </article>)}
      {activeTurnId !== null && thinkingStage && <article className={`message aurora thinking ${thinkingStage}`} aria-label={t.thinkingAria}>
        <span className="speaker">Aurora</span>
        <p><span className="thinking-dots" aria-hidden="true"><i></i><i></i><i></i></span>{thinkingStage === "understanding" ? t.understanding : t.composing}</p>
      </article>}
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
          onClick={() => recording ? stopRecording() : void startRecording()}>
          {recording ? t.recStop : t.voice}</AsyncButton>}
        {activeTurnId && <button type="button" className="stop" onClick={onStop}>{t.stop}</button>}
        <button type="submit" className="send" disabled={!draft.trim() || !sessionReady}>{activeTurnId ? t.interruptSend : t.send}</button>
      </div>
    </form>
  </>;
}
