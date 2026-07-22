import { useCallback, useState } from "react";
import { api, type AiHealth, type ShredderHistoryEntry, type ShredderResult } from "../api";

// Phase 3 legacy-page port: src/main/resources/static/pages/thought-shredder.html.
export type UseThoughtShredderOptions = {
  setStatus: (status: string) => void;
};

export function useThoughtShredder({ setStatus }: UseThoughtShredderOptions) {
  const [shredderAiHealth, setShredderAiHealth] = useState<AiHealth | null>(null);
  const [shredderHistory, setShredderHistory] = useState<ShredderHistoryEntry[]>([]);
  const [shredderResult, setShredderResult] = useState<ShredderResult | null>(null);
  const [shredderBusy, setShredderBusy] = useState(false);

  const loadShredderAiHealth = useCallback(() => api.aiHealth().then(setShredderAiHealth).catch(() => undefined), []);
  const loadShredderHistory = useCallback(() => api.shredderHistory().then(setShredderHistory).catch(() => undefined), []);

  const processShred = useCallback(async (text: string, saveMode: "KEEP_RAW" | "KEEP_ONLY_RESULT" | "DISPLAY_ONCE") => {
    const trimmed = text.trim();
    if (!trimmed) { setStatus("请先写下你的想法"); return; }
    setShredderBusy(true);
    setShredderResult(null);
    try {
      const result = await api.shredderProcess(trimmed, saveMode);
      setShredderResult(result);
      await loadShredderHistory();
    } catch (error) { setStatus(error instanceof Error ? error.message : "粉碎未能完成，请重试"); }
    finally { setShredderBusy(false); }
  }, [loadShredderHistory, setStatus]);

  const settleShred = useCallback(async (id: number) => {
    try {
      await api.shredderSettle(id);
      setStatus("已沉淀到记忆");
      await loadShredderHistory();
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法沉淀这条记录"); }
  }, [loadShredderHistory, setStatus]);

  const deleteShred = useCallback(async (id: number) => {
    try {
      await api.shredderDelete(id);
      setStatus("记录已删除");
      await loadShredderHistory();
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法删除这条记录"); }
  }, [loadShredderHistory, setStatus]);

  return {
    shredderAiHealth, shredderHistory, shredderResult, shredderBusy,
    loadShredderAiHealth, loadShredderHistory, processShred, settleShred, deleteShred
  };
}
