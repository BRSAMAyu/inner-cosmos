import { useRef, useState, type FormEvent } from "react";
import { AsyncButton } from "../loading";

export type AuroraUiMessage = { key: string; speaker: "USER" | "AURORA"; text: string; partial?: boolean };

/** The two pre-speech beats worth showing inline; `null` while idle or actively streaming tokens. */
export type AuroraThinkingStage = "understanding" | "composing" | null;

const THINKING_COPY: Record<"understanding" | "composing", string> = {
  understanding: "Aurora 正在理解这一刻…",
  composing: "Aurora 正在组织下一句…"
};

export function AuroraConversation({ messages, activeTurnId, thinkingStage = null, draft, sessionReady, onDraftChange, onSubmit, onStop, onTranscribe }: {
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
}) {
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
    <section className="conversation" aria-live="polite" aria-label="与 Aurora 的对话">
      {messages.length === 0 && <div className="empty"><span>✦</span><p>把现在最真实的一句话放在这里。</p></div>}
      {messages.map(message => <article className={`message ${message.speaker.toLowerCase()} ${message.partial ? "partial" : ""}`} key={message.key}>
        <span className="speaker">{message.speaker === "AURORA" ? "Aurora" : "你"}</span>
        <p>{message.text || "…"}</p>
        {message.partial && message.text && <small>停在这里</small>}
      </article>)}
      {activeTurnId !== null && thinkingStage && <article className={`message aurora thinking ${thinkingStage}`} aria-label="Aurora 正在思考">
        <span className="speaker">Aurora</span>
        <p><span className="thinking-dots" aria-hidden="true"><i></i><i></i><i></i></span>{THINKING_COPY[thinkingStage]}</p>
      </article>}
    </section>
    <form className="composer" onSubmit={onSubmit}>
      <textarea value={draft} onChange={event => onDraftChange(event.target.value)}
        placeholder={activeTurnId ? "直接说出新的想法，Aurora 会停下并重新理解…" : "此刻，你想从哪里说起？"}
        aria-label="写给 Aurora" onKeyDown={event => {
          if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); }
        }} />
      <div className="actions">
        {onTranscribe && voiceSupported && <AsyncButton
          className={"voice" + (recording ? " recording" : "")}
          disabled={!sessionReady}
          busy={transcribing}
          busyText="转写中…"
          aria-pressed={recording}
          aria-label={recording ? "停止录音并转写" : "用语音输入"}
          onClick={() => recording ? stopRecording() : void startRecording()}>
          {recording ? "● 停止录音" : "🎤 语音"}</AsyncButton>}
        {activeTurnId && <button type="button" className="stop" onClick={onStop}>停止回应</button>}
        <button type="submit" className="send" disabled={!draft.trim() || !sessionReady}>{activeTurnId ? "打断并发送" : "发送"}</button>
      </div>
    </form>
  </>;
}
