import { useCallback, useState } from "react";
import { api, type DailyRecordDetail, type DailyRecordEntry } from "../api";

// Phase 3 legacy-page port: src/main/resources/static/pages/daily-record.html.
//
// A real, confirmed quirk of the backend this hook works around (see api.ts's DailyRecordDetail
// doc comment): GET /api/daily-record/latest returns a DailyRecordVO built from the user's latest
// MemoryCard, which has NO `id` of its own -- so "accept" / "edit" cannot target it directly. This
// hook instead treats `dailyRecords()[0]` (the tb_daily_record list, ordered by record_date desc)
// as the real, editable "today" record, and merges the richer VO's fragments/emotions/todos/
// mainMemory onto it purely for display when index 0 is selected. Older entries (index > 0) only
// ever show the fields that actually exist on the plain DailyRecord entity.
export type UseDailyRecordOptions = {
  setStatus: (status: string) => void;
};

export function useDailyRecord({ setStatus }: UseDailyRecordOptions) {
  const [dailyRecords, setDailyRecords] = useState<DailyRecordEntry[]>([]);
  const [dailyRecordDetail, setDailyRecordDetail] = useState<DailyRecordDetail | null>(null);
  const [dailyRecordIndex, setDailyRecordIndex] = useState(0);
  const [dailyRecordAcceptBusy, setDailyRecordAcceptBusy] = useState(false);
  const [dailyRecordEditBusy, setDailyRecordEditBusy] = useState<"theme" | "event" | null>(null);

  const loadDailyRecords = useCallback(() => api.dailyRecords().then(setDailyRecords), []);
  const loadLatestDailyRecord = useCallback(() => api.latestDailyRecord().then(setDailyRecordDetail).catch(() => undefined), []);

  // Regression (Gemini audit 2.2.1): clamp against the loaded list's actual bounds, not against
  // the *current* index -- the old `Math.min(index, current)` could never increase past `current`,
  // permanently disabling the "前一天" (previous day) button, which calls onSelectIndex(index + 1).
  const selectDailyRecordIndex = useCallback((index: number) => {
    setDailyRecordIndex(() => Math.max(0, Math.min(index, dailyRecords.length - 1)));
  }, [dailyRecords.length]);

  const acceptDailyRecord = useCallback(async () => {
    const record = dailyRecords[dailyRecordIndex];
    if (!record) { setStatus("没有可保存的记录"); return; }
    setDailyRecordAcceptBusy(true);
    try {
      await api.acceptDailyRecordEntry(record.id);
      setDailyRecords(rows => rows.map(row => row.id === record.id ? { ...row, userAccepted: true } : row));
      setStatus("记录已接受并保存");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法保存这条记录"); }
    finally { setDailyRecordAcceptBusy(false); }
  }, [dailyRecords, dailyRecordIndex, setStatus]);

  const editDailyRecordField = useCallback(async (field: "theme" | "event", value: string) => {
    const record = dailyRecords[dailyRecordIndex];
    if (!record) { setStatus("没有可编辑的记录"); return; }
    setDailyRecordEditBusy(field);
    try {
      const patch = field === "theme" ? { theme: value } : { cognitiveSummary: value };
      const updated = await api.editDailyRecord(record.id, patch);
      setDailyRecords(rows => rows.map(row => row.id === record.id ? updated : row));
      if (dailyRecordIndex === 0) await loadLatestDailyRecord();
      setStatus("已保存");
    } catch (error) { setStatus(error instanceof Error ? error.message : "保存失败"); }
    finally { setDailyRecordEditBusy(null); }
  }, [dailyRecords, dailyRecordIndex, loadLatestDailyRecord, setStatus]);

  return {
    dailyRecords, dailyRecordDetail, dailyRecordIndex, dailyRecordAcceptBusy, dailyRecordEditBusy,
    loadDailyRecords, loadLatestDailyRecord, selectDailyRecordIndex, acceptDailyRecord, editDailyRecordField
  };
}
