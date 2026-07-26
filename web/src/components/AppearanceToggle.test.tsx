import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { AppearanceToggle } from "./AppearanceToggle";
import { setColorScheme, setPreviewHour } from "../theme";

afterEach(() => {
  cleanup();
  setColorScheme(null);
  setPreviewHour(null);
  delete document.documentElement.dataset.theme;
  delete document.documentElement.dataset.time;
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

  it('选择"夜色"显式应用柔和夜间主题', () => {
    document.documentElement.dataset.theme = "day";
    render(<AppearanceToggle />);
    fireEvent.click(screen.getByRole("button", { name: "夜色" }));
    expect(document.documentElement.dataset.theme).toBe("night");
    expect(screen.getByRole("button", { name: "夜色" })).toHaveAttribute("aria-pressed", "true");
  });

  it("renders in English when locale is en-SG", () => {
    render(<AppearanceToggle locale="en-SG" />);
    expect(screen.getByRole("group", { name: "Appearance" })).toBeVisible();
    const lightOptions = screen.getByRole("group", { name: "Light" });
    expect(lightOptions).toHaveTextContent("Follow time");
    expect(lightOptions).toHaveTextContent("Day");
    expect(lightOptions).toHaveTextContent("Night");
    expect(screen.getByRole("slider", { name: "Demo time" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Return to live time" })).toBeDisabled();
  });

  it("滑杆可预览七时段并恢复真实时间", () => {
    render(<AppearanceToggle />);
    const slider = screen.getByRole("slider", { name: "演示时间" });
    fireEvent.change(slider, { target: { value: "21" } });
    expect(document.documentElement.dataset.time).toBe("night");
    expect(document.documentElement.dataset.theme).toBe("night");
    expect(screen.getByText("演示时间 · 21:00")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "恢复实时" }));
    expect(screen.getByText(/实时 ·/)).toBeVisible();
  });

  it("七个预设均可直接选择", () => {
    render(<AppearanceToggle />);
    const presetGroup = screen.getByRole("group", { name: "演示时间" });
    expect(presetGroup.querySelectorAll("button")).toHaveLength(7);
    fireEvent.click(screen.getByRole("button", { name: "黄昏" }));
    expect(document.documentElement.dataset.time).toBe("dusk");
    expect(document.documentElement.dataset.theme).toBe("day");
  });
});
