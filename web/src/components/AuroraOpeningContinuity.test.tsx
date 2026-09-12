import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DialogContinuity } from "../api";
import { AuroraOpeningContinuity } from "./AuroraOpeningContinuity";

afterEach(cleanup);

const returningContinuity: DialogContinuity = {
  hasPrior: true,
  priorSessionId: 7,
  priorActiveAt: "2026-09-10",
  carryForward: [
    { kind: "PRIOR_SUMMARY", text: "用户谈到工作转向的犹豫，区分了'不想做'和'怕做不好'。", provenance: "上次对话（9月10日）的整理" },
    { kind: "PRIOR_TOPICS", text: "职业转换、自我评价", provenance: "上次对话（9月10日）的整理" }
  ],
  openingLine: "你9月10日聊过一次，我带着那次留下的整理在这里。想继续，也可以从新的开始。"
};

const freshContinuity: DialogContinuity = {
  hasPrior: false,
  priorSessionId: null,
  priorActiveAt: null,
  carryForward: [],
  openingLine: "我们从头开始。你想说的那件事，慢慢来。"
};

describe("AuroraOpeningContinuity (CP-18)", () => {
  it("renders nothing without continuity", () => {
    const { container } = render(<AuroraOpeningContinuity
      continuity={null} locale="zh-CN" onDismiss={() => undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a returning user the provenance-labeled carry-forward and the choice to continue", () => {
    render(<AuroraOpeningContinuity
      continuity={returningContinuity} locale="zh-CN" onDismiss={() => undefined} />);

    const card = screen.getByTestId("opening-continuity");
    expect(card).toHaveClass("returning");
    expect(screen.getByText(returningContinuity.openingLine)).toBeInTheDocument();
    const notes = screen.getAllByRole("listitem");
    expect(notes).toHaveLength(2);
    expect(screen.getByText(/不想做/)).toBeInTheDocument();
    // Every carried note is labeled with where it came from — no unprovenanced "memory".
    expect(screen.getAllByText(/来源：上次对话/)).toHaveLength(2);
    expect(screen.getByText(/——由你决定/)).toBeInTheDocument();
  });

  it("shows a brand-new user the first-conversation state and never a fabricated prior", () => {
    render(<AuroraOpeningContinuity
      continuity={freshContinuity} locale="en-SG" onDismiss={() => undefined} />);

    const card = screen.getByTestId("opening-continuity");
    expect(card).toHaveClass("fresh");
    expect(screen.getByText("First conversation")).toBeInTheDocument();
    expect(screen.getByText(freshContinuity.openingLine)).toBeInTheDocument();
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
    expect(within(card).queryByText(/last conversation/i)).not.toBeInTheDocument();
    expect(screen.getByText(/your choice|take your time/i)).toBeInTheDocument();
  });

  it("is dismissible", () => {
    const dismiss = vi.fn();
    render(<AuroraOpeningContinuity
      continuity={returningContinuity} locale="zh-CN" onDismiss={dismiss} />);

    fireEvent.click(screen.getByRole("button", { name: "收起开场上下文" }));
    expect(dismiss).toHaveBeenCalledOnce();
  });

  it("treats a prior with no surviving material as first-conversation-shaped, without claiming it", () => {
    render(<AuroraOpeningContinuity
      continuity={{ ...returningContinuity, carryForward: [] }}
      locale="zh-CN" onDismiss={() => undefined} />);

    const card = screen.getByTestId("opening-continuity");
    expect(card).toHaveClass("fresh");
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
  });
});
