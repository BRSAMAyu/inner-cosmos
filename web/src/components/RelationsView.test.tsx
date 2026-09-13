import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RelationsView } from "./RelationsView";
import type { RelationMention, RelationReview } from "../api";

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const rel = (over: Partial<RelationMention> = {}): RelationMention => ({
  id: 1, relationLabel: "妈妈", relationType: "家人", emotionTags: "牵挂,愧疚",
  triggerSummary: "很久没有打电话了", boundaryHint: null, ...over
});

const review = (over: Partial<RelationReview> = {}): RelationReview => ({
  relationLabel: "妈妈", windowStart: "2026-08-16T00:00:00", windowEnd: "2026-09-13T00:00:00",
  mentionCount: 3, weeksActive: 2, emotionSpectrum: { 牵挂: 2, 温暖: 1 },
  recentTriggers: ["深夜通话后写下"], ...over
});

describe("RelationsView", () => {
  it("shows an empty state when no relations have surfaced", () => {
    render(<RelationsView relations={[]} selected={null} timeline={[]} review={null} busy={false} onSelect={() => undefined} />);
    expect(screen.getByText(/还没有从对话里浮现的关系/)).toBeVisible();
  });

  it("lists relations with emotion tags and selects one on click", () => {
    const onSelect = vi.fn();
    render(<RelationsView relations={[rel({ id: 7, relationLabel: "阿哲" })]} selected={null}
      timeline={[]} review={null} busy={false} onSelect={onSelect} />);
    expect(screen.getByText("阿哲")).toBeVisible();
    expect(screen.getByText("牵挂")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /阿哲/ }));
    expect(onSelect).toHaveBeenCalledExactlyOnceWith("阿哲");
  });

  it("renders the interaction review with real counts and the non-evaluative note for the selected relation", () => {
    render(<RelationsView relations={[rel()]} selected="妈妈"
      timeline={[{ timestamp: "2026-07-17T10:00:00", emotions: "温暖", summary: "一起吃了饭" }]}
      review={review()} busy={false} onSelect={() => undefined} />);
    // CP-34: counts from real rows, never a temperature verdict.
    expect(screen.getByText(/互动回顾/)).toBeVisible();
    expect(screen.getByText(/近 4 周被提及 3 次/)).toBeVisible();
    expect(screen.getByText(/分布在 2 个不同的周/)).toBeVisible();
    expect(screen.getByText("牵挂 ×2")).toBeVisible();
    expect(screen.getByText(/不是关系好坏的评判/)).toBeVisible();
    expect(screen.queryByText(/关系温度/)).not.toBeInTheDocument();
    expect(screen.getByText(/「妈妈」的时间线/)).toBeVisible();
    expect(screen.getByText("一起吃了饭")).toBeVisible();
  });

  it("an empty window shows the honest empty review, not a fabricated mid-range score", () => {
    render(<RelationsView relations={[rel()]} selected="妈妈" timeline={[]}
      review={review({ mentionCount: 0, weeksActive: 0, emotionSpectrum: {}, recentTriggers: [] })}
      busy={false} onSelect={() => undefined} />);
    expect(screen.getByText(/近 4 周没有提及这段关系的记录/)).toBeVisible();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });

  it("shows a loading state while the timeline is being fetched", () => {
    // Now routed through the shared LoadingText primitive (web/src/loading.tsx), which withholds
    // its text for the first second (the spec's "don't flash a loader under 1s" rule) -- advance
    // past that threshold before asserting, same as loading.test.tsx's own convention.
    render(<RelationsView relations={[rel()]} selected="妈妈" timeline={[]} review={null} busy={true} onSelect={() => undefined} />);
    act(() => vi.advanceTimersByTime(1000));
    expect(screen.getByText(/正在读取「妈妈」的时间线/)).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<RelationsView locale="en-SG" relations={[rel({ id: 7, relationLabel: "Alex" })]} selected="Alex"
      timeline={[{ timestamp: "2026-07-17T10:00:00", emotions: "warm", summary: "had dinner together" }]}
      review={review({ relationLabel: "Alex", emotionSpectrum: { warm: 2, grateful: 1 } })}
      busy={false} onSelect={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Relationship interactions, seen slowly" })).toBeVisible();
    expect(screen.getByText(/mentioned 3 times in the last 4 weeks/)).toBeVisible();
    expect(screen.getByText(/not a verdict on the relationship/)).toBeVisible();
    expect(screen.getByText("Alex's timeline")).toBeVisible();
    expect(screen.getByText("had dinner together")).toBeVisible();
  });
});
