import { useCallback, useMemo, useState } from "react";
import { api, isVersionConflictError, type BeliefContradiction, type BeliefPattern } from "../api";
import type { Locale } from "../i18n";

// Port of the belief-pattern-browsing half of src/main/resources/static/pages/beliefs.html
// (BeliefController: /api/belief/list, /by-category, /strong, /contradictions). See api.ts's
// BeliefPattern doc comment for why the OTHER half of that legacy page (the M6 Aurora Self Panel)
// is deliberately out of scope here and why beliefs.html itself is not retired by this port.

export type BeliefFilter = "all" | "strong" | "byCategory";

export function useBeliefGallery({ setStatus, locale = "zh-CN" }: { setStatus: (status: string) => void; locale?: Locale }) {
  const [beliefs, setBeliefs] = useState<BeliefPattern[]>([]);
  const [contradictions, setContradictions] = useState<BeliefContradiction[]>([]);
  const [filter, setFilter] = useState<BeliefFilter>("all");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [categoryBeliefs, setCategoryBeliefs] = useState<BeliefPattern[]>([]);
  const [busy, setBusy] = useState(false);
  // CP-21: a belief-surface response died on an optimistic-concurrency conflict (409 /
  // code CONFLICT). The gallery never silently keeps stale rows then — it says someone
  // updated the content first and offers a refresh.
  const [conflict, setConflict] = useState(false);

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
      if (isVersionConflictError(error)) setConflict(true);
      else setStatus(error instanceof Error ? error.message
        : locale === "en-SG" ? "Could not load beliefs yet." : "暂时无法加载信念");
    } finally {
      setBusy(false);
    }
  }, [locale, setStatus]);

  const selectCategory = useCallback(async (category: string) => {
    setSelectedCategory(category);
    setBusy(true);
    try {
      setCategoryBeliefs(await api.beliefByCategory(category));
    } catch (error) {
      if (isVersionConflictError(error)) setConflict(true);
      else setStatus(error instanceof Error ? error.message
        : locale === "en-SG" ? "Could not load this category yet." : "暂时无法加载这个分类");
    } finally {
      setBusy(false);
    }
  }, [locale, setStatus]);

  /** CP-21: "查看最新" — drop the conflict state and re-pull the lists from the server. */
  const refreshFromConflict = useCallback(() => {
    setConflict(false);
    void loadAll().catch(() => undefined);
    void loadContradictions();
  }, [loadAll, loadContradictions]);

  /** CP-21: dismiss the banner without refetching (the owner chose to keep reading as-is). */
  const dismissConflict = useCallback(() => setConflict(false), []);

  return {
    beliefs, contradictions, filter, categories, selectedCategory, categoryBeliefs, busy,
    conflict, refreshFromConflict, dismissConflict,
    loadAll, loadContradictions, selectFilter, selectCategory
  };
}
