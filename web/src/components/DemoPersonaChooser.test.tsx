import { cleanup, render, screen } from "@testing-library/react";
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
});
