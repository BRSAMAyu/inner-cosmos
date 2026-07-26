import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { StartHereJourney } from "./StartHereJourney";

afterEach(cleanup);

describe("StartHereJourney", () => {
  it("shows an expanded, actionable five-step journey for an ordinary new user", () => {
    const onStep = vi.fn();
    render(<StartHereJourney locale="zh-CN" onStep={onStep} />);

    expect(screen.getByRole("region", { name: "从这里开始：完整旅程" })).toBeVisible();
    expect(screen.getAllByRole("listitem")).toHaveLength(5);
    expect(screen.getByText("和 Aurora 说")).toBeVisible();
    expect(screen.getByText("留下记忆")).toBeVisible();
    expect(screen.getByText("编织共鸣体")).toBeVisible();
    expect(screen.getByText("遇见共鸣")).toBeVisible();
    expect(screen.getByText("写一封慢信")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: /塑造侧影/ }));
    expect(onStep).toHaveBeenCalledExactlyOnceWith("capsule");
  });

  it("is compact and collapsed by default in the demo sandbox, but can be opened", () => {
    render(<StartHereJourney isDemoSandbox onStep={() => undefined} />);
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
    const expand = screen.getByRole("button", { name: "展开五步旅程" });
    expect(expand).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(expand);
    expect(screen.getAllByRole("listitem")).toHaveLength(5);
  });

  it("can be completely hidden in a demo presentation", () => {
    const { container } = render(<StartHereJourney isDemoSandbox demoPresentation="hidden"
      onStep={() => undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the complete journey in English without Chinese action labels", () => {
    render(<StartHereJourney locale="en-SG" onStep={() => undefined} />);
    expect(screen.getByText("START HERE")).toBeVisible();
    expect(screen.getByRole("button", { name: /Start talking/ })).toBeVisible();
    expect(screen.getByRole("button", { name: /See memories/ })).toBeVisible();
    expect(screen.getByRole("button", { name: /Shape a facet/ })).toBeVisible();
    expect(screen.getByRole("button", { name: /Find resonance/ })).toBeVisible();
    expect(screen.getByRole("button", { name: /Write a slow letter/ })).toBeVisible();
    expect(screen.queryByText("开始倾诉")).not.toBeInTheDocument();
  });
});
