import { useCallback, useMemo, useState } from "react";
import { api, type BeliefContradiction, type BeliefPattern } from "../api";

// Port of the belief-pattern-browsing half of src/main/resources/static/pages/beliefs.html
// (BeliefController: /api/belief/list, /by-category, /strong, /contradictions). See api.ts's
// BeliefPattern doc comment for why the OTHER half of that legacy page (the M6 Aurora Self Panel)
// is deliberately out of scope here and why beliefs.html itself is not retired by this port.

export type BeliefFilter = "all" | "strong" | "byCategory";

export function useBeliefGallery({ setStatus }: { setStatus: (status: string) => void }) {
  const [beliefs, setBeliefs] = useState<BeliefPattern[]>([]);
  const [contradictions, setContradictions] = useState<BeliefContradiction[]>([]);
  const [filter, setFilter] = useState<BeliefFilter>("all");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [categoryBeliefs, setCategoryBeliefs] = useState<BeliefPattern[]>([]);
  const [busy, setBusy] = useState(false);

  const loadAll = useCallback(() => api.beliefList().then(setBeliefs), []);
  const loadContradictions = useCallback(() => api.beliefContradictions().then(setContradictions).catch(() => undefined), []);

  const categories = useMemo(
    () => Array.from(new Set(beliefs.map(b => b.beliefCategory).filter((c): c is string => Boolean(c)))),
    [beliefs]
  );

  const selectFilter = useCallback(async (next: BeliefFilter) => {
    setFilter(next);
    if (next === "byCategory") return; // Reuses the already-loaded "all" list to build the category picker.
    setBusy(true);
    try {
      setBeliefs(next === "strong" ? await api.beliefStrong(0.5) : await api.beliefList());
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "暂时无法加载信念");
    } finally {
      setBusy(false);
    }
  }, [setStatus]);

  const selectCategory = useCallback(async (category: string) => {
    setSelectedCategory(category);
    setBusy(true);
    try {
      setCategoryBeliefs(await api.beliefByCategory(category));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "暂时无法加载这个分类");
    } finally {
      setBusy(false);
    }
  }, [setStatus]);

  return {
    beliefs, contradictions, filter, categories, selectedCategory, categoryBeliefs, busy,
    loadAll, loadContradictions, selectFilter, selectCategory
  };
}
