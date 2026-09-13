import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useCallback, useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, type QuotaOverview } from "../api";
import { QuotaPanel, resetTimeLabel } from "./QuotaPanel";

vi.mock("../api", () => ({
  api: { quotas: vi.fn() }
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// Synthetic contract-example values from the CP-46 backend payload shape — not real user data.
const overview: QuotaOverview = {
  quotas: [
    { capability: "ai.deep_daily_budget", basis: "DAILY", used: 12, limit: 400, remaining: 388,
      usedTokens: 3400, limitTokens: 400000, resetsAt: "2026-09-14T00:00:00" }
  ],
  subscriptionWindows: [
    { productId: "pro.monthly", capability: "memory.extended_horizon", basis: "SUBSCRIPTION_PERIOD",
      state: "ACTIVE", resetsAt: "2026-10-13T04:00:00", autoRenew: true, cancelAtPeriodEnd: false }
  ],
  neverPayGated: ["account.cancellation", "data.export", "safety.crisis_interception"]
};

/** Mirrors AuroraApp's real wiring (loadQuotas' view/loading/loaded/error state around
 *  api.quotas()) so the panel under test runs against the data flow it actually ships with. */
function QuotaPanelHarness({ locale = "zh-CN" }: { locale?: "zh-CN" | "en-SG" }) {
  const [view, setView] = useState<QuotaOverview | null>(null);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setView(await api.quotas());
      setLoaded(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "load failed");
    } finally {
      setLoading(false);
    }
  }, []);
  return <QuotaPanel view={view} loading={loading} loaded={loaded} error={error}
    onLoad={() => void load()} locale={locale} />;
}

describe("QuotaPanel (CP-46)", () => {
  it("auto-loads once on mount and renders usage, reset moments and the never-pay promise", async () => {
    vi.mocked(api.quotas).mockResolvedValue(overview);
    render(<QuotaPanelHarness />);

    expect(api.quotas).toHaveBeenCalledOnce();
    // Remaining count and used/limit for the daily budget.
    expect(await screen.findByText("剩余 388")).toBeVisible();
    expect(screen.getByText("已用 12 / 400")).toBeVisible();
    // The zoneless UTC resetsAt is localized onto the user's clock (re-tagged as UTC first).
    expect(screen.getByText(`${new Date("2026-09-14T00:00:00Z").toLocaleString("zh-CN")} 重置`)).toBeVisible();
    // Subscription window: state, product, auto-renew mark and its own reset moment.
    expect(screen.getByText("生效中")).toBeVisible();
    expect(screen.getByText("pro.monthly")).toBeVisible();
    expect(screen.getByText("自动续费")).toBeVisible();
    expect(screen.getByText(`${new Date("2026-10-13T04:00:00Z").toLocaleString("zh-CN")} 重置`)).toBeVisible();
    // The never-pay-gated promise is fixed copy, always present.
    expect(screen.getByText("安全、纠正、导出与删除永不付费解锁。")).toBeVisible();
  });

  it("expands the concrete never-pay-gated capability list behind the promise", async () => {
    vi.mocked(api.quotas).mockResolvedValue(overview);
    render(<QuotaPanelHarness />);
    fireEvent.click(await screen.findByText("3 项能力属于这一承诺"));
    expect(screen.getByText("account.cancellation")).toBeVisible();
    expect(screen.getByText("data.export")).toBeVisible();
    expect(screen.getByText("safety.crisis_interception")).toBeVisible();
  });

  it("degrades gracefully when there is no usage and no subscription window", async () => {
    vi.mocked(api.quotas).mockResolvedValue({
      quotas: [], subscriptionWindows: [], neverPayGated: ["data.export"]
    });
    render(<QuotaPanelHarness />);
    expect(await screen.findByText("当前没有计入配额的用量。")).toBeVisible();
    expect(screen.getByText("当前没有生效的订阅窗口。")).toBeVisible();
    expect(screen.getByText("核心每日额度不依赖订阅，始终适用。")).toBeVisible();
    // The promise survives an empty payload.
    expect(screen.getByText("安全、纠正、导出与删除永不付费解锁。")).toBeVisible();
  });

  it("shows an inline degrade message with retry when the endpoint fails, and recovers", async () => {
    vi.mocked(api.quotas)
      .mockRejectedValueOnce(new Error("HTTP 500"))
      .mockResolvedValueOnce(overview);
    render(<QuotaPanelHarness />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("暂时无法读取配额信息。");
    expect(alert).toHaveTextContent("HTTP 500");
    // No usage numbers render, but the never-pay promise stays visible.
    expect(screen.queryByText("每日用量")).not.toBeInTheDocument();
    expect(screen.getByText("安全、纠正、导出与删除永不付费解锁。")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText("剩余 388")).toBeVisible();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders English (en-SG) copy when that locale is selected", async () => {
    vi.mocked(api.quotas).mockResolvedValue(overview);
    render(<QuotaPanelHarness locale="en-SG" />);
    expect(await screen.findByText("388 remaining")).toBeVisible();
    expect(screen.getByText("12 of 400 used")).toBeVisible();
    expect(screen.getByText("Active")).toBeVisible();
    expect(screen.getByText("Auto-renews")).toBeVisible();
    expect(screen.getByText("Safety, correction, export and deletion are never locked behind payment.")).toBeVisible();
  });
});

describe("resetTimeLabel", () => {
  it("treats a zoneless LocalDateTime as UTC (the backend serializes without a Z)", () => {
    // Timezone-independent pin: the zoneless string must land on the SAME instant as its
    // explicitly-UTC form. A naive local parse diverges on every non-UTC machine.
    expect(resetTimeLabel("2026-09-14T00:00:00", "zh-CN"))
      .toBe(resetTimeLabel("2026-09-14T00:00:00Z", "zh-CN"));
  });

  it("passes an already-zoned timestamp through without double-tagging", () => {
    expect(resetTimeLabel("2026-09-14T00:00:00+08:00", "en-SG"))
      .toBe(new Date("2026-09-14T00:00:00+08:00").toLocaleString("en-SG"));
  });

  it("falls back to the raw string when the value is unparseable", () => {
    expect(resetTimeLabel("not-a-timestamp", "zh-CN")).toBe("not-a-timestamp");
  });
});
