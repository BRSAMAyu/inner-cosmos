import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { initialProductSpace, productSpaceFromPath, ProductShellNavigation, spacePath } from "./ProductShell";

afterEach(cleanup);

describe("ProductShell", () => {
  // Characterization: pins the pre-router `?space=` query-param behavior. The value is
  // still read once, at boot, to redirect old bookmarks/links to their real-route
  // equivalent (see AuroraApp.tsx's legacy-link redirect effect) -- it must keep meaning
  // exactly this even though it is no longer the live navigation mechanism.
  it("restores only a known space from the URL", () => {
    expect(initialProductSpace("?space=letters")).toBe("letters");
    expect(initialProductSpace("?space=unknown")).toBe("aurora");
    expect(initialProductSpace("?space=https://attacker.invalid")).toBe("aurora");
  });

  it("maps every space to a real, stable path", () => {
    expect(spacePath("aurora")).toBe("/aurora");
    expect(spacePath("cosmos")).toBe("/cosmos");
    expect(spacePath("resonance")).toBe("/resonance");
    expect(spacePath("letters")).toBe("/connections/letters");
    expect(spacePath("me")).toBe("/me");
  });

  it("resolves the active space from an exact route path", () => {
    expect(productSpaceFromPath("/aurora")).toBe("aurora");
    expect(productSpaceFromPath("/cosmos")).toBe("cosmos");
    expect(productSpaceFromPath("/resonance")).toBe("resonance");
    expect(productSpaceFromPath("/connections/letters")).toBe("letters");
    expect(productSpaceFromPath("/me")).toBe("me");
  });

  it("resolves the active space from a nested sub-route (stretch goal hook for later per-space routes)", () => {
    expect(productSpaceFromPath("/cosmos/starfield")).toBe("cosmos");
    expect(productSpaceFromPath("/connections/letters/42")).toBe("letters");
  });

  it("falls back to aurora for the root path and any unrecognized path", () => {
    expect(productSpaceFromPath("/")).toBe("aurora");
    expect(productSpaceFromPath("/unknown")).toBe("aurora");
    expect(productSpaceFromPath("")).toBe("aurora");
  });

  it("exposes all five spaces and marks the current one", () => {
    render(<ProductShellNavigation active="cosmos" onNavigate={() => undefined} />);
    expect(screen.getAllByRole("button")).toHaveLength(5);
    expect(screen.getByRole("button", { name: /^内宇宙/ })).toHaveAttribute("aria-current", "page");
  });

  it("delegates an explicit navigation choice", () => {
    const onNavigate = vi.fn();
    render(<ProductShellNavigation active="aurora" onNavigate={onNavigate} />);
    fireEvent.click(screen.getByRole("button", { name: /^共鸣/ }));
    expect(onNavigate).toHaveBeenCalledWith("resonance");
  });
});
