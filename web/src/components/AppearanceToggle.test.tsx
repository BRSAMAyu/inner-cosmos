import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { AppearanceToggle } from "./AppearanceToggle";
import { setColorScheme } from "../theme";

afterEach(() => {
  cleanup();
  setColorScheme(null);
  delete document.documentElement.dataset.theme;
});

describe("AppearanceToggle", () => {
  it("渲染三个外观选项，默认跟随时间为选中", () => {
    render(<AppearanceToggle />);
    expect(screen.getByRole("button", { name: "跟随时间" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "白昼" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "夜色" })).toHaveAttribute("aria-pressed", "false");
  });

  it('选择"白昼"给 <html> 写 data-theme="day" 并更新选中态', () => {
    render(<AppearanceToggle />);
    fireEvent.click(screen.getByRole("button", { name: "白昼" }));
    expect(document.documentElement.dataset.theme).toBe("day");
    expect(screen.getByRole("button", { name: "白昼" })).toHaveAttribute("aria-pressed", "true");
  });

  it('选择"夜色"移除 data-theme（暗色为默认）', () => {
    document.documentElement.dataset.theme = "day";
    render(<AppearanceToggle />);
    fireEvent.click(screen.getByRole("button", { name: "夜色" }));
    expect(document.documentElement.dataset.theme).toBeUndefined();
    expect(screen.getByRole("button", { name: "夜色" })).toHaveAttribute("aria-pressed", "true");
  });
});
