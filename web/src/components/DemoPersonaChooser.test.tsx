import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import { DemoPersonaChooser } from "./DemoPersonaChooser";

vi.mock("../api", () => ({
  api: {
    demoPersonas: vi.fn(),
    enterDemoPersona: vi.fn()
  }
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("DemoPersonaChooser", () => {
  it("does not render the top story switcher when the server withholds templates for a HUMAN account", async () => {
    vi.mocked(api.demoPersonas).mockResolvedValue([]);

    render(<DemoPersonaChooser compact currentUsername="ordinary-user" onEntered={vi.fn()} />);

    await vi.waitFor(() => expect(api.demoPersonas).toHaveBeenCalledOnce());
    expect(screen.queryByRole("region", { name: "Demo 体验角色" })).not.toBeInTheDocument();
    expect(screen.queryByText("切换体验角色")).not.toBeInTheDocument();
  });

  it("shows all three stories and refreshes the active story without reloading the page", async () => {
    const initial = [
      { key: "lin-che", name: "Lin Che", headline: "把理想变成真实", story: "课程项目", themes: ["创造"], active: false },
      { key: "shen-yan", name: "Shen Yan", headline: "在远方重新找到自己", story: "交换生活", themes: ["归属"], active: false },
      { key: "xia-yu", name: "Xia Yu", headline: "总在照顾别人的人", story: "新的工作", themes: ["边界"], active: false }
    ];
    vi.mocked(api.demoPersonas)
      .mockResolvedValueOnce(initial)
      .mockResolvedValueOnce(initial.map(persona => ({ ...persona, active: persona.key === "shen-yan" })));
    vi.mocked(api.enterDemoPersona).mockResolvedValue({} as never);
    const onEntered = vi.fn().mockResolvedValue(undefined);

    render(<DemoPersonaChooser onEntered={onEntered} />);

    await vi.waitFor(() => expect(screen.getAllByRole("button")).toHaveLength(3));
    fireEvent.click(screen.getByRole("button", { name: /沈砚/ }));

    await vi.waitFor(() => {
      expect(api.enterDemoPersona).toHaveBeenCalledWith("shen-yan");
      expect(onEntered).toHaveBeenCalledOnce();
      expect(api.demoPersonas).toHaveBeenCalledTimes(2);
    });
    expect(screen.getByRole("button", { name: /沈砚/ })).toBeDisabled();
    expect(screen.getByText("当前")).toBeInTheDocument();
  });

  it("collapses the compact demo switcher so Aurora keeps the first viewport", async () => {
    vi.mocked(api.demoPersonas).mockResolvedValue([
      { key: "lin-che", name: "Lin Che", headline: "把理想变成真实", story: "课程项目", themes: ["创造"], active: true },
      { key: "shen-yan", name: "Shen Yan", headline: "在远方重新找到自己", story: "交换生活", themes: ["归属"], active: false },
      { key: "xia-yu", name: "Xia Yu", headline: "总在照顾别人的人", story: "新的工作", themes: ["边界"], active: false }
    ]);

    render(<DemoPersonaChooser compact onEntered={vi.fn()} />);

    const region = await screen.findByRole("region", { name: "Demo 体验角色" });
    expect(region.querySelector("summary")).toHaveTextContent("体验角色 · 林澈切换");
    expect(screen.queryByText("把理想变成真实")).not.toBeInTheDocument();
    expect(region.querySelector("details")).not.toHaveAttribute("open");
  });
});
