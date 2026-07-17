import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RelationsView } from "./RelationsView";
import type { RelationMention } from "../api";

afterEach(cleanup);

const rel = (over: Partial<RelationMention> = {}): RelationMention => ({
  id: 1, relationLabel: "妈妈", relationType: "家人", emotionTags: "牵挂,愧疚",
  triggerSummary: "很久没有打电话了", boundaryHint: null, ...over
});

describe("RelationsView", () => {
  it("shows an empty state when no relations have surfaced", () => {
    render(<RelationsView relations={[]} selected={null} timeline={[]} health={null} busy={false} onSelect={() => undefined} />);
    expect(screen.getByText(/还没有从对话里浮现的关系/)).toBeVisible();
  });

  it("lists relations with emotion tags and selects one on click", () => {
    const onSelect = vi.fn();
    render(<RelationsView relations={[rel({ id: 7, relationLabel: "阿哲" })]} selected={null}
      timeline={[]} health={null} busy={false} onSelect={onSelect} />);
    expect(screen.getByText("阿哲")).toBeVisible();
    expect(screen.getByText("牵挂")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /阿哲/ }));
    expect(onSelect).toHaveBeenCalledExactlyOnceWith("阿哲");
  });

  it("renders the relationship temperature and timeline for the selected relation", () => {
    render(<RelationsView relations={[rel()]} selected="妈妈"
      timeline={[{ timestamp: "2026-07-17T10:00:00", emotions: "温暖", summary: "一起吃了饭" }]}
      health={{ relationLabel: "妈妈", healthScore: 0.82 }} busy={false} onSelect={() => undefined} />);
    expect(screen.getByText(/关系温度/)).toBeVisible();
    expect(screen.getByText(/温暖 · 82%/)).toBeVisible();
    expect(screen.getByText(/「妈妈」的时间线/)).toBeVisible();
    expect(screen.getByText("一起吃了饭")).toBeVisible();
  });

  it("shows a loading state while the timeline is being fetched", () => {
    render(<RelationsView relations={[rel()]} selected="妈妈" timeline={[]} health={null} busy={true} onSelect={() => undefined} />);
    expect(screen.getByText(/正在读取「妈妈」的时间线/)).toBeVisible();
  });
});
