import { useEffect, useRef, useState } from "react";
import type { Locale } from "../../i18n";

// W2 voice feature: ONE shared audio-playing implementation, reused by AccountSettings' voice
// preview button, the Aurora conversation's AMBIENT inner-voice bubble (auto-plays on mount) and
// its ON_DEMAND variant (plays on tap). Takes a base64 `data:audio/...` URI -- never a remote URL
// -- matching the fixed API contract (GET/PATCH tts/preferences and POST tts/preview all return
// inline base64 audio, not a fetchable link).
//
// Autoplay-policy reality: an unmuted <audio>.play() call not preceded by a user gesture in the
// current page CAN be rejected by the browser (a real, common case for the AMBIENT mode, which
// auto-plays without any click). That rejection is caught here and turned into a visible "play"
// affordance -- never a silent, unexplained absence of sound. The same button also serves the
// ON_DEMAND / manual case (autoPlay=false): it simply waits for the first click instead of
// attempting playback on mount.

export type InlineAudioPlayerStatus = "idle" | "playing" | "blocked" | "error";

type AudioPlayerCopy = { play: string; playing: string; blocked: string; error: string };

const COPY: Record<Locale, AudioPlayerCopy> = {
  "zh-CN": { play: "▶ 播放", playing: "🔊 播放中…", blocked: "▶ 播放（自动播放被拦截，点击播放）", error: "暂时无法播放，点击重试" },
  "en-SG": { play: "▶ Play", playing: "🔊 Playing…", blocked: "▶ Play (autoplay was blocked -- tap to play)", error: "Couldn't play this -- tap to retry" }
};

export function InlineAudioPlayer({
  audio, autoPlay = false, locale = "zh-CN", ariaLabel, className, onPlayAttempt
}: {
  /** A `data:audio/...;base64,...` URI (or any src `HTMLAudioElement` accepts). */
  audio: string;
  /** AMBIENT mode / a settings preview: attempt playback immediately on mount. ON_DEMAND mode:
   *  render the button but wait for a click. */
  autoPlay?: boolean;
  locale?: Locale;
  /** Overrides the default bilingual aria-label (e.g. a caller-specific "preview this voice"). */
  ariaLabel?: string;
  className?: string;
  /** Fires the moment a play attempt is made (autoplay OR a click) -- before the promise settles.
   *  Lets a caller (e.g. the ON_DEMAND inner-voice bubble) reveal hidden content in the same beat
   *  the audio starts trying to play, without this component needing to know about that content. */
  onPlayAttempt?: () => void;
}) {
  const t = COPY[locale];
  const elementRef = useRef<HTMLAudioElement | null>(null);
  const [status, setStatus] = useState<InlineAudioPlayerStatus>("idle");

  const attemptPlay = () => {
    const element = elementRef.current;
    if (!element) return;
    onPlayAttempt?.();
    let result: unknown;
    try { result = element.play(); }
    catch { setStatus("blocked"); return; }
    if (result && typeof (result as Promise<void>).then === "function") {
      (result as Promise<void>).then(
        () => setStatus("playing"),
        // NotAllowedError (autoplay policy) and any other rejection both fall back to the same
        // visible, retryable affordance -- the user must never be left with silent audio and no
        // indication anything was supposed to play.
        () => setStatus("blocked")
      );
    } else {
      // jsdom's HTMLMediaElement.play() returns undefined rather than a Promise; treat that as an
      // immediate (test-environment) success rather than leaving status stuck at "idle".
      setStatus("playing");
    }
  };

  useEffect(() => {
    const element = new Audio(audio);
    elementRef.current = element;
    setStatus("idle");
    const handleEnded = () => setStatus(current => current === "playing" ? "idle" : current);
    const handleError = () => setStatus("error");
    element.addEventListener("ended", handleEnded);
    element.addEventListener("error", handleError);
    if (autoPlay) attemptPlay();
    return () => {
      element.removeEventListener("ended", handleEnded);
      element.removeEventListener("error", handleError);
      element.pause();
      // Drop the source explicitly (rather than just dropping the reference) so an in-flight
      // network fetch for the audio data is released too -- avoids a dangling element continuing
      // to hold onto (or decode) media after the component that owns it has unmounted.
      element.src = "";
      elementRef.current = null;
    };
    // Only the audio source identifies "which clip" -- autoPlay/onPlayAttempt intentionally are
    // not deps: this effect owns exactly one mount/unmount cycle per distinct `audio` value.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [audio]);

  const label = status === "error" ? t.error : status === "blocked" ? t.blocked : status === "playing" ? t.playing : t.play;
  return (
    <button type="button" className={`inline-audio-player ${status} ${className ?? ""}`.trim()}
      aria-label={ariaLabel ?? label} onClick={attemptPlay}
      // Disabled only while actively playing (to avoid re-triggering play() mid-playback); every
      // other state (idle/blocked/error) must stay clickable so the fallback affordance actually
      // works.
      disabled={status === "playing"}>
      {label}
    </button>
  );
}
