import { useCallback, useState } from "react";
import { api, type WeeklyReviewV2 } from "../api";

// Phase 3 legacy-page port: src/main/resources/static/pages/weekly-review.html.
// Uses /api/daily-record/weekly/v2/* (WeeklyReviewV2VO), not the plain V1 weekly/latest+generate
// the legacy page's own api.js called -- see api.ts's WeeklyReviewV2 doc comment for why.
export type UseWeeklyReviewOptions = {
  setStatus: (status: string) => void;
};

export function useWeeklyReview({ setStatus }: UseWeeklyReviewOptions) {
  const [weeklyReview, setWeeklyReview] = useState<WeeklyReviewV2 | null>(null);
  const [weeklyReviewBusy, setWeeklyReviewBusy] = useState(false);

  const loadWeeklyReview = useCallback(() => api.weeklyReviewV2Latest().then(setWeeklyReview).catch(() => undefined), []);

  const generateWeeklyReview = useCallback(async () => {
    setWeeklyReviewBusy(true);
    try {
      const review = await api.generateWeeklyReviewV2();
      setWeeklyReview(review);
      setStatus("这周的成长周报已经生成。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法生成这周的周报"); }
    finally { setWeeklyReviewBusy(false); }
  }, [setStatus]);

  return { weeklyReview, weeklyReviewBusy, loadWeeklyReview, generateWeeklyReview };
}
