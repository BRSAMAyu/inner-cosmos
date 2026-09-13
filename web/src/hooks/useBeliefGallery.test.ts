import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { BeliefPattern } from "../api";
import { useBeliefGallery } from "./useBeliefGallery";

vi.mock("../api", () => ({
  api: {
    beliefList: vi.fn(),
    beliefByCategory: vi.fn(),
    beliefStrong: vi.fn(),
    beliefContradictions: vi.fn()
  },
  // Faithful to the real discriminator: only code CONFLICT (the backend's HTTP 409
  // channel) counts as a version conflict; BAD_REQUEST must never trip it.
  isVersionConflictError: (error: unknown) =>
    error instanceof Error && (error as { code?: string }).code === "CONFLICT"
}));

const belief = (overrides: Partial<BeliefPattern> = {}): BeliefPattern => ({
  id: 1, beliefContent: "只要拆出足够小的第一步，我就能推进下去。", beliefType: "SELF",
  beliefCategory: "agency", strengthScore: 0.7, confirmationCount: 4, status: "active", ...overrides
});

function setup() {
  const setStatus = vi.fn();
  const { result } = renderHook(() => useBeliefGallery({ setStatus }));
  return { result, setStatus };
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("useBeliefGallery -- initial state", () => {
  it("starts empty on the 'all' filter", () => {
    const { result } = setup();
    expect(result.current.beliefs).toEqual([]);
    expect(result.current.contradictions).toEqual([]);
    expect(result.current.filter).toBe("all");
    expect(result.current.selectedCategory).toBeNull();
    expect(result.current.busy).toBe(false);
  });
});

describe("useBeliefGallery -- bootstrap loaders", () => {
  it("loadAll and loadContradictions populate their own state", async () => {
    vi.mocked(api.beliefList).mockResolvedValue([belief()]);
    vi.mocked(api.beliefContradictions).mockResolvedValue([
      { beliefA: belief({ id: 1 }), beliefB: belief({ id: 2 }), contradictionReason: "在拉扯你" }
    ]);
    const { result } = setup();
    await act(async () => { await Promise.all([result.current.loadAll(), result.current.loadContradictions()]); });
    expect(result.current.beliefs).toHaveLength(1);
    expect(result.current.contradictions).toHaveLength(1);
  });
});

describe("useBeliefGallery -- filters", () => {
  it("selecting 'strong' fetches strong beliefs with the default threshold", async () => {
    vi.mocked(api.beliefStrong).mockResolvedValue([belief({ strengthScore: 0.8 })]);
    const { result } = setup();
    await act(async () => { await result.current.selectFilter("strong"); });
    expect(api.beliefStrong).toHaveBeenCalledExactlyOnceWith(0.5);
    expect(result.current.filter).toBe("strong");
    expect(result.current.beliefs).toHaveLength(1);
  });

  it("selecting 'all' re-fetches the full list", async () => {
    vi.mocked(api.beliefList).mockResolvedValue([belief(), belief({ id: 2 })]);
    const { result } = setup();
    await act(async () => { await result.current.selectFilter("all"); });
    expect(result.current.beliefs).toHaveLength(2);
  });

  it("derives the category picker from the loaded 'all' beliefs", async () => {
    vi.mocked(api.beliefList).mockResolvedValue([
      belief({ id: 1, beliefCategory: "agency" }), belief({ id: 2, beliefCategory: "self_worth" }),
      belief({ id: 3, beliefCategory: "agency" })
    ]);
    const { result } = setup();
    await act(async () => { await result.current.loadAll(); });
    expect(result.current.categories.sort()).toEqual(["agency", "self_worth"]);
  });

  it("selecting a category fetches its beliefs", async () => {
    vi.mocked(api.beliefByCategory).mockResolvedValue([belief({ beliefCategory: "vision" })]);
    const { result } = setup();
    await act(async () => { await result.current.selectCategory("vision"); });
    expect(api.beliefByCategory).toHaveBeenCalledExactlyOnceWith("vision");
    expect(result.current.selectedCategory).toBe("vision");
    expect(result.current.categoryBeliefs).toHaveLength(1);
  });

  it("reports a friendly error when a filter fetch fails", async () => {
    vi.mocked(api.beliefStrong).mockRejectedValue(new Error("暂时无法加载"));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.selectFilter("strong"); });
    expect(setStatus).toHaveBeenCalledWith("暂时无法加载");
  });
});

describe("useBeliefGallery -- CP-21 conflict refresh", () => {
  const conflictError = () => Object.assign(new Error("version is stale"), { code: "CONFLICT" });
  const plainError = () => Object.assign(new Error("只有当前有效的理解才能搁置"), { code: "BAD_REQUEST" });

  it("a 409/code-CONFLICT failure raises the conflict state instead of a generic status", async () => {
    vi.mocked(api.beliefStrong).mockRejectedValue(conflictError());
    const { result, setStatus } = setup();
    await act(async () => { await result.current.selectFilter("strong"); });
    expect(result.current.conflict).toBe(true);
    expect(setStatus).not.toHaveBeenCalled();
  });

  it("refreshFromConflict clears the state and re-pulls the lists from the server", async () => {
    vi.mocked(api.beliefStrong).mockRejectedValueOnce(conflictError());
    vi.mocked(api.beliefList).mockResolvedValue([belief()]);
    vi.mocked(api.beliefContradictions).mockResolvedValue([]);
    const { result } = setup();
    await act(async () => { await result.current.selectFilter("strong"); });
    expect(result.current.conflict).toBe(true);

    await act(async () => { result.current.refreshFromConflict(); });
    expect(result.current.conflict).toBe(false);
    expect(api.beliefList).toHaveBeenCalled();
    expect(result.current.beliefs).toHaveLength(1);
  });

  it("a non-conflict failure keeps the conflict state off (不误报)", async () => {
    vi.mocked(api.beliefStrong).mockRejectedValue(plainError());
    const { result, setStatus } = setup();
    await act(async () => { await result.current.selectFilter("strong"); });
    expect(result.current.conflict).toBe(false);
    expect(setStatus).toHaveBeenCalledOnce();
  });

  it("dismissConflict closes the banner without refetching", async () => {
    vi.mocked(api.beliefStrong).mockRejectedValueOnce(conflictError());
    const { result } = setup();
    await act(async () => { await result.current.selectFilter("strong"); });
    act(() => { result.current.dismissConflict(); });
    expect(result.current.conflict).toBe(false);
  });
});
