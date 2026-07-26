import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LocaleToggle } from "./LocaleToggle";

afterEach(cleanup);

describe("LocaleToggle", () => {
  it("shows the active locale in one compact select and labels itself in that language", () => {
    const { rerender } = render(<LocaleToggle locale="zh-CN" onChange={() => undefined} />);
    expect(screen.getByRole("combobox", { name: "界面语言 / Language" })).toHaveValue("zh-CN");
    expect(screen.getByText("语言")).toBeVisible();

    rerender(<LocaleToggle locale="en-SG" onChange={() => undefined} />);
    expect(screen.getByRole("combobox", { name: "界面语言 / Language" })).toHaveValue("en-SG");
    expect(screen.getByText("Language")).toBeVisible();
  });

  it("emits the chosen locale on change", () => {
    const onChange = vi.fn();
    render(<LocaleToggle locale="zh-CN" onChange={onChange} />);
    fireEvent.change(screen.getByRole("combobox", { name: "界面语言 / Language" }), { target: { value: "en-SG" } });
    expect(onChange).toHaveBeenCalledWith("en-SG");
  });
});
