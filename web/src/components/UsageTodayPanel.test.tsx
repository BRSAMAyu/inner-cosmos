import { cleanup, render, screen } from "@testing-library/react";
import { useCallback, useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, type UsageToday } from "../api";
import { UsageTodayPanel } from "./UsageTodayPanel";

vi.mock("../api", () => ({
  api: { usageToday: vi.fn() }
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// Contract-example values from UsageTimeController's GET /api/me/usage/today shape —
// not real user data.
const quietDay: UsageToday = {
  date: "2026-09-15", activeSeconds: 7_320, turnCount: 19,
  reminderAfterMinutes: 120, reminderDue: false, reminderNote: "",
  basis: "COMPLETED_TURN_DURATION"
};
const longDay: UsageToday = {
  ...quietDay, activeSeconds: 9_060, turnCount: 41, reminderDue: true,
  reminderNote: "你今天已经和 Aurora 聊了很久，也许该歇一歇了。"
};

/** Mirrors AuroraApp's real wiring (loadUsageToday's view/loading/loaded/error state around
 *  api.usageToday()) so the panel runs against the data flow it actually ships with. */
function UsageTodayHarness({ locale = "zh-CN" }: { locale?: "zh-CN" | "en-SG" }) {
  const [view, setView] = useState<UsageToday | null>(null);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setView(await api.usageToday()); setLoaded(true); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "load failed"); }
    finally { setLoading(false); }
  }, []);
  return <UsageTodayPanel view={view} loading={loading} loaded={loaded} error={error}
    onLoad={() => void load()} locale={locale} />;
}

describe("UsageTodayPanel (CP-08)", () => {
  it("auto-loads once and shows today's approx minutes plus the record-not-verdict basis note", async () => {
    vi.mocked(api.usageToday).mockResolvedValue(quietDay);
    render(<UsageTodayHarness />);

    expect(api.usageToday).toHaveBeenCalledOnce();
    expect(await screen.findByText("今天约 122 分钟")).toBeVisible(); // 7320s rounds to 122min
    expect(screen.getByText("已完成 19 轮对话")).toBeVisible();
    expect(screen.getByText("按已完成对话轮次的时长累计，只是记录，不做评判。")).toBeVisible();
  });

  it("shows the backend's reminderNote verbatim when reminderDue — no client rewording, no alert tone", async () => {
    vi.mocked(api.usageToday).mockResolvedValue(longDay);
    render(<UsageTodayHarness />);

    expect(await screen.findByText(longDay.reminderNote)).toBeVisible();
    expect(screen.getByText("一条平静的提醒")).toBeVisible();
    // Calm presentation: status, not an alarm.
    expect(screen.getByRole("status")).toBeVisible();
  });

  it("degrades honestly on load failure — inline error with retry, no fabricated zero", async () => {
    vi.mocked(api.usageToday).mockRejectedValue(new Error("HTTP 503"));
    render(<UsageTodayHarness />);

    expect(await screen.findByRole("alert")).toHaveTextContent("暂时无法读取今日时长。");
    expect(screen.queryByText(/今天约/)).toBeNull();
  });
});
