import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BeliefGallery } from "./BeliefGallery";
import type { BeliefPattern } from "../api";

afterEach(cleanup);

const belief = (overrides: Partial<BeliefPattern> = {}): BeliefPattern => ({
  id: 1, beliefContent: "只要拆出足够小的第一步，我就能推进下去。", beliefType: "SELF",
  beliefCategory: "agency", strengthScore: 0.7, confirmationCount: 4, status: "active", ...overrides
});

function baseProps() {
  return {
    beliefs: [] as BeliefPattern[], contradictions: [], filter: "all" as const, categories: [] as string[],
    selectedCategory: null, categoryBeliefs: [] as BeliefPattern[], busy: false,
    onSelectFilter: vi.fn(), onSelectCategory: vi.fn()
  };
}

describe("BeliefGallery", () => {
  it("shows an empty state when there are no beliefs yet", () => {
    render(<BeliefGallery {...baseProps()} />);
    expect(screen.getByText(/还没有识别出明显的信念模式/)).toBeVisible();
  });

  it("renders belief cards with strength and category", () => {
    render(<BeliefGallery {...baseProps()} beliefs={[belief()]} />);
    expect(screen.getByText(/只要拆出足够小的第一步/)).toBeVisible();
    expect(screen.getByText(/70%/)).toBeVisible();
    expect(screen.getByText("agency")).toBeVisible();
  });

  it("renders a contradiction pair with its reason", () => {
    render(<BeliefGallery {...baseProps()} contradictions={[{
      beliefA: belief({ id: 1, beliefContent: "我必须一直坚强" }),
      beliefB: belief({ id: 2, beliefContent: "我可以求助" }),
      contradictionReason: "这两个信念在拉扯你"
    }]} />);
    expect(screen.getByText(/我必须一直坚强/)).toBeVisible();
    expect(screen.getByText(/我可以求助/)).toBeVisible();
    expect(screen.getByText("这两个信念在拉扯你")).toBeVisible();
  });

  it("switches to the 'strong' filter tab", () => {
    const onSelectFilter = vi.fn();
    render(<BeliefGallery {...baseProps()} onSelectFilter={onSelectFilter} />);
    fireEvent.click(screen.getByRole("tab", { name: /强信念/ }));
    expect(onSelectFilter).toHaveBeenCalledExactlyOnceWith("strong");
  });

  it("shows a category picker and loads a category's beliefs on selection", () => {
    const onSelectCategory = vi.fn();
    render(<BeliefGallery {...baseProps()} filter="byCategory" categories={["agency", "self_worth"]}
      selectedCategory="agency" categoryBeliefs={[belief({ beliefCategory: "agency" })]} onSelectCategory={onSelectCategory} />);
    fireEvent.click(screen.getByRole("button", { name: "self_worth" }));
    expect(onSelectCategory).toHaveBeenCalledExactlyOnceWith("self_worth");
    expect(screen.getByText(/只要拆出足够小的第一步/)).toBeVisible();
  });

  it("shows a loading state while busy", () => {
    render(<BeliefGallery {...baseProps()} busy={true} />);
    expect(screen.getByText(/正在加载|Loading/)).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<BeliefGallery {...baseProps()} locale="en-SG" beliefs={[belief()]} />);
    expect(screen.getByRole("heading", { name: /belief/i })).toBeVisible();
  });
});
