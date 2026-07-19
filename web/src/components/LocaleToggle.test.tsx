import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LocaleToggle } from "./LocaleToggle";

afterEach(cleanup);

describe("LocaleToggle", () => {
  it("marks the active locale pressed and labels itself in that language", () => {
    const { rerender } = render(<LocaleToggle locale="zh-CN" onChange={() => undefined} />);
    expect(screen.getByRole("button", { name: "中文" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "English" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByText("语言")).toBeVisible();

    rerender(<LocaleToggle locale="en-SG" onChange={() => undefined} />);
    expect(screen.getByRole("button", { name: "English" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("Language")).toBeVisible();
  });

  it("emits the chosen locale on click", () => {
    const onChange = vi.fn();
    render(<LocaleToggle locale="zh-CN" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(onChange).toHaveBeenCalledWith("en-SG");
  });
});
