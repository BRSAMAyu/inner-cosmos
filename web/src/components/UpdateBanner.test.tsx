import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { UpdateBanner } from "./UpdateBanner";

// B5-pwa-mobile: pins the versioned-update affordance's visibility logic given the
// (needRefresh, offlineReady) state pair vite-plugin-pwa's useRegisterSW() hook produces
// -- see PwaUpdateNotice.tsx for the container that actually owns that hook. This component
// stays a plain, controlled presentational component so it is testable with no service worker
// or virtual-module mocking at all.
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("UpdateBanner", () => {
  it("renders nothing when neither a refresh nor offline-ready state is active", () => {
    const { container } = render(
      <UpdateBanner
        needRefresh={false}
        offlineReady={false}
        onReload={vi.fn()}
        onDismissRefresh={vi.fn()}
        onDismissOfflineReady={vi.fn()}
      />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a new-version affordance with on-brand copy when needRefresh is true, and never auto-reloads", () => {
    const onReload = vi.fn();
    render(
      <UpdateBanner
        needRefresh
        offlineReady={false}
        onReload={onReload}
        onDismissRefresh={vi.fn()}
        onDismissOfflineReady={vi.fn()}
      />
    );
    expect(screen.getByRole("status")).toHaveTextContent("内宇宙有新版本了");
    expect(screen.getByRole("button", { name: "现在刷新" })).toBeInTheDocument();
    expect(onReload).not.toHaveBeenCalled();
  });

  it("clicking the reload button calls onReload -- reload only happens on explicit user choice", () => {
    const onReload = vi.fn();
    render(
      <UpdateBanner
        needRefresh
        offlineReady={false}
        onReload={onReload}
        onDismissRefresh={vi.fn()}
        onDismissOfflineReady={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "现在刷新" }));
    expect(onReload).toHaveBeenCalledOnce();
  });

  it("clicking dismiss on the refresh banner calls onDismissRefresh and does not call onReload", () => {
    const onReload = vi.fn();
    const onDismissRefresh = vi.fn();
    render(
      <UpdateBanner
        needRefresh
        offlineReady={false}
        onReload={onReload}
        onDismissRefresh={onDismissRefresh}
        onDismissOfflineReady={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "稍后" }));
    expect(onDismissRefresh).toHaveBeenCalledOnce();
    expect(onReload).not.toHaveBeenCalled();
  });

  it("shows a distinct offline-ready notice when offlineReady is true and needRefresh is false", () => {
    render(
      <UpdateBanner
        needRefresh={false}
        offlineReady
        onReload={vi.fn()}
        onDismissRefresh={vi.fn()}
        onDismissOfflineReady={vi.fn()}
      />
    );
    expect(screen.getByRole("status")).toHaveTextContent("内宇宙现在可以离线打开了");
    // No reload affordance for a first-install offline-ready notice -- there is nothing to reload.
    expect(screen.queryByRole("button", { name: "现在刷新" })).not.toBeInTheDocument();
  });

  it("clicking dismiss on the offline-ready notice calls onDismissOfflineReady", () => {
    const onDismissOfflineReady = vi.fn();
    render(
      <UpdateBanner
        needRefresh={false}
        offlineReady
        onReload={vi.fn()}
        onDismissRefresh={vi.fn()}
        onDismissOfflineReady={onDismissOfflineReady}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "知道了" }));
    expect(onDismissOfflineReady).toHaveBeenCalledOnce();
  });

  it("prefers the needRefresh banner over the offlineReady notice when both are somehow true", () => {
    render(
      <UpdateBanner
        needRefresh
        offlineReady
        onReload={vi.fn()}
        onDismissRefresh={vi.fn()}
        onDismissOfflineReady={vi.fn()}
      />
    );
    expect(screen.getByText("内宇宙有新版本了")).toBeInTheDocument();
    expect(screen.queryByText("内宇宙现在可以离线打开了")).not.toBeInTheDocument();
  });
});
