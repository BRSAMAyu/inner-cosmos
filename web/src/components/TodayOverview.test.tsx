import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TodayOverview } from "./TodayOverview";

afterEach(cleanup);

const props = {
  memoryCount: 0,
  latestMemory: null,
  arrivedLetters: 0,
  latestLetter: null,
  publicCapsules: 0,
  wakeIntents: 0,
  onOpenCosmos: vi.fn(),
  onOpenLetters: vi.fn(),
  onOpenResonance: vi.fn(),
  onWriteLetter: vi.fn(),
  onOpenReturns: vi.fn()
};

describe("TodayOverview locale closure", () => {
  it("does not leak the English eyebrow into the Chinese view", () => {
    render(<TodayOverview {...props} locale="zh-CN" />);
    expect(screen.getByText("今日 · 内宇宙")).toBeVisible();
    expect(screen.queryByText("YOUR COSMOS, TODAY")).not.toBeInTheDocument();
  });

  it("keeps the English eyebrow in the English view", () => {
    render(<TodayOverview {...props} locale="en-SG" />);
    expect(screen.getByText("YOUR COSMOS, TODAY")).toBeVisible();
  });

  it("localises the legacy system-generated memory title in the English view", () => {
    render(<TodayOverview {...props} latestMemory="今日沉淀" locale="en-SG" />);
    expect(screen.getByText("Today's reflection")).toBeVisible();
    expect(screen.queryByText("今日沉淀")).not.toBeInTheDocument();
  });
});
