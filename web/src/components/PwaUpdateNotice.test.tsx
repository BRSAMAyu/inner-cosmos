import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PwaUpdateNotice } from "./PwaUpdateNotice";

// B5-pwa-mobile: PwaUpdateNotice is the thin container that owns vite-plugin-pwa's
// useRegisterSW() hook (from the virtual:pwa-register/react module vite-plugin-pwa generates
// at build time) and feeds its state into the plain, already-unit-tested UpdateBanner. Mocking
// the virtual module here is the honest boundary -- jsdom cannot run a real service worker --
// but this still pins that PwaUpdateNotice wires the hook's needRefresh/offlineReady/
// updateServiceWorker through to the UI correctly, and never reloads except via the user's
// own explicit click (see UpdateBanner.test.tsx for the reload-only-on-click contract).
const setNeedRefresh = vi.fn();
const setOfflineReady = vi.fn();
const updateServiceWorker = vi.fn().mockResolvedValue(undefined);
let needRefreshValue = false;
let offlineReadyValue = false;

vi.mock("virtual:pwa-register/react", () => ({
  useRegisterSW: () => ({
    needRefresh: [needRefreshValue, setNeedRefresh],
    offlineReady: [offlineReadyValue, setOfflineReady],
    updateServiceWorker,
  }),
}));

// jsdom's default navigator.language (en-US) would otherwise make loadLocale() fall back to
// en-SG for every test with no stored preference -- pin zh-CN explicitly (see UpdateBanner.tsx).
beforeEach(() => localStorage.setItem("ic.locale", "zh-CN"));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  needRefreshValue = false;
  offlineReadyValue = false;
});

describe("PwaUpdateNotice", () => {
  it("renders nothing when the registered service worker reports no pending state", () => {
    const { container } = render(<PwaUpdateNotice />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the update banner when useRegisterSW reports needRefresh, and reloading calls updateServiceWorker", () => {
    needRefreshValue = true;
    render(<PwaUpdateNotice />);
    expect(screen.getByText("内宇宙有新版本了")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "现在刷新" }));
    expect(updateServiceWorker).toHaveBeenCalledOnce();
  });

  it("shows the offline-ready notice when useRegisterSW reports offlineReady, and dismissing calls setOfflineReady(false)", () => {
    offlineReadyValue = true;
    render(<PwaUpdateNotice />);
    expect(screen.getByText("内宇宙现在可以离线打开了")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "知道了" }));
    expect(setOfflineReady).toHaveBeenCalledExactlyOnceWith(false);
  });

  it("dismissing the refresh banner calls setNeedRefresh(false), not updateServiceWorker", () => {
    needRefreshValue = true;
    render(<PwaUpdateNotice />);
    fireEvent.click(screen.getByRole("button", { name: "稍后" }));
    expect(setNeedRefresh).toHaveBeenCalledExactlyOnceWith(false);
    expect(updateServiceWorker).not.toHaveBeenCalled();
  });
});
