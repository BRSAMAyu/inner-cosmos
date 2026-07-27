import { useCallback, useState } from "react";
import { api, diaryTranscribeAudio } from "../api";
import type { Locale } from "../i18n";

// Port of src/main/resources/static/pages/heart-diary.html into the AppShell (Phase 3, legacy batch
// B): voice/text diary entry with AI transcription, level-based polish (0=raw, 1=cleaned, 2=organized,
// 3=reshaped) and submit, over DiaryController.
//
// `rawText` is the untouched original (typed, or from ASR) that every polish request and the final
// submit are computed from. `displayText` is what's actually shown in the editable textarea: it
// mirrors rawText at level 0, and becomes the fetched/cached polished text when a non-zero level is
// selected. This intentionally differs from the legacy page's own quirk of wiping every polish cache
// entry on every keystroke (its "input" listener called onRawTextChanged unconditionally) -- here,
// only an explicit onTextChange (typing while at level 0, or a fresh transcription) resets the cache.
export function useHeartDiary({ setStatus, locale = "zh-CN" }: { setStatus: (status: string) => void; locale?: Locale }) {
  const en = locale === "en-SG";
  const [rawText, setRawText] = useState("");
  const [displayText, setDisplayText] = useState("");
  const [transcriptionId, setTranscriptionId] = useState<number | null>(null);
  const [activeLevel, setActiveLevel] = useState(0);
  const [polishedByLevel, setPolishedByLevel] = useState<Record<number, string>>({});
  const [polishBusy, setPolishBusy] = useState(false);
  const [submitBusy, setSubmitBusy] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const onTextChange = useCallback((text: string) => {
    setRawText(text);
    setDisplayText(text);
    setTranscriptionId(null);
    setPolishedByLevel({});
    setActiveLevel(0);
  }, []);

  const transcribeAudio = useCallback(async (blob: Blob) => {
    try {
      const vt = await diaryTranscribeAudio(blob);
      setTranscriptionId(vt.id);
      setRawText(vt.originalText);
      setDisplayText(vt.originalText);
      setPolishedByLevel({});
      setActiveLevel(0);
      setSubmitted(false);
      setStatus(en ? "Voice converted to text. You can refine or edit it." : "语音已转成文字，可以继续润色或修改。");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : en ? "Voice transcription failed. Please try again shortly." : "语音转写失败，请稍后再试。");
    }
  }, [en, setStatus]);

  const switchLevel = useCallback(async (level: number) => {
    setActiveLevel(level);
    if (level === 0) { setDisplayText(rawText); return; }
    const cached = polishedByLevel[level];
    if (cached !== undefined) { setDisplayText(cached); return; }
    setPolishBusy(true);
    try {
      let id = transcriptionId;
      if (!id) {
        const vt = await api.diaryTranscribe(rawText);
        id = vt.id;
        setTranscriptionId(id);
      }
      const result = await api.diaryPolish(rawText, level as 1 | 2 | 3);
      setPolishedByLevel(current => ({ ...current, [level]: result.polishedText }));
      setDisplayText(result.polishedText);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : en ? "Refinement failed, but your original text is still here." : "润色暂时失败了，原文仍然保留。");
    } finally {
      setPolishBusy(false);
    }
  }, [en, rawText, polishedByLevel, transcriptionId, setStatus]);

  const submit = useCallback(async (): Promise<boolean> => {
    const content = displayText.trim();
    if (content.length < 5) {
      setStatus(en ? "This entry is a little too short. Add a few more words." : "心声内容太短，多写几句吧");
      return false;
    }
    setSubmitBusy(true);
    try {
      let id = transcriptionId;
      if (!id) {
        const vt = await api.diaryTranscribe(rawText || content);
        id = vt.id;
        setTranscriptionId(id);
      }
      await api.diarySubmit(id, content);
      setSubmitted(true);
      setStatus(en ? "Your entry was saved and added to the memory starfield." : "记录已保存，并加入记忆星空。");
      return true;
    } catch (error) {
      setStatus(error instanceof Error ? error.message : en ? "Could not save this entry yet." : "暂时无法保存这段心声");
      return false;
    } finally {
      setSubmitBusy(false);
    }
  }, [displayText, en, rawText, transcriptionId, setStatus]);

  return {
    rawText, displayText, transcriptionId, activeLevel, polishBusy, submitBusy, submitted,
    onTextChange, transcribeAudio, switchLevel, submit
  };
}
