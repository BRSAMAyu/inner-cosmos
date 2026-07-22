import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GoodbyeRitualCard } from "./GoodbyeRitualCard";
import type { GoodbyeResult } from "../api";

afterEach(cleanup);

const result: GoodbyeResult = {
  success: true, line: "今天先到这里，我会把重要的部分留住。", stepsCompleted: [],
  confirmed: false, reverted: false, confidence: 0.95, goodbyeStrength: "HIGH"
};

describe("GoodbyeRitualCard", () => {
  it("renders nothing when there is no goodbye result", () => {
    const { container } = render(<GoodbyeRitualCard result={null} locale="zh-CN" onDismiss={() => undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the farewell line as a distinct closing card, not a chat bubble", () => {
    render(<GoodbyeRitualCard result={result} locale="zh-CN" onDismiss={() => undefined} />);
    expect(screen.getByText("今天先到这里，我会把重要的部分留住。")).toBeVisible();
    expect(screen.getByRole("status")).toHaveClass("goodbye-ritual-card");
  });

  it("dismisses on request", () => {
    const onDismiss = vi.fn();
    render(<GoodbyeRitualCard result={result} locale="zh-CN" onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole("button", { name: "好，先到这里" }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it("renders in English when locale is en-SG", () => {
    render(<GoodbyeRitualCard result={result} locale="en-SG" onDismiss={() => undefined} />);
    expect(screen.getByRole("button", { name: "Okay, that's enough for now" })).toBeVisible();
  });
});
