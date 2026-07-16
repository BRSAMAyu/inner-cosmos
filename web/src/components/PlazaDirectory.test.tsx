import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PlazaDirectory } from "./PlazaDirectory";
import type { PublicCapsule } from "../api";

afterEach(cleanup);

const capsule = (over: Partial<PublicCapsule> = {}): PublicCapsule => ({
  id: 1, pseudonym: "雨后的人", intro: "先沉默再表达边界", capsuleType: "USER_CAPSULE",
  publicTags: "[\"自我观察\",\"关系\"]", echoEnergy: 12, freshnessScore: 0.8, conversationLimitPerDay: 30,
  lastActivityAt: "2026-07-15T00:00:00Z", ...over
});

describe("PlazaDirectory", () => {
  it("lists public capsules with their tags and opens a chat with one", () => {
    const onOpenCapsule = vi.fn();
    const c = capsule();
    render(<PlazaDirectory capsules={[c]} activeCapsuleId={null} busy={false} onOpenCapsule={onOpenCapsule} />);
    expect(screen.getByText("雨后的人")).toBeVisible();
    expect(screen.getByText("自我观察", { selector: ".plaza-card-tags span" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "开始对话" }));
    expect(onOpenCapsule).toHaveBeenCalledExactlyOnceWith(c);
  });

  it("filters by a free-text query over pseudonym and intro", () => {
    render(<PlazaDirectory capsules={[capsule({ id: 1, pseudonym: "雨后的人", intro: "先沉默" }),
      capsule({ id: 2, pseudonym: "清晨的人", intro: "喜欢早起", publicTags: "[]" })]}
      activeCapsuleId={null} busy={false} onOpenCapsule={() => undefined} />);
    fireEvent.change(screen.getByPlaceholderText(/搜索/), { target: { value: "清晨" } });
    expect(screen.queryByText("雨后的人")).not.toBeInTheDocument();
    expect(screen.getByText("清晨的人")).toBeVisible();
  });

  it("filters by a chosen tag chip", () => {
    render(<PlazaDirectory capsules={[capsule({ id: 1, pseudonym: "A", publicTags: "[\"关系\"]" }),
      capsule({ id: 2, pseudonym: "B", publicTags: "[\"工作\"]" })]}
      activeCapsuleId={null} busy={false} onOpenCapsule={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "工作" }));
    expect(screen.queryByText("A")).not.toBeInTheDocument();
    expect(screen.getByText("B")).toBeVisible();
  });

  it("shows an empty state when there are no public capsules", () => {
    render(<PlazaDirectory capsules={[]} activeCapsuleId={null} busy={false} onOpenCapsule={() => undefined} />);
    expect(screen.queryByRole("button", { name: "开始对话" })).not.toBeInTheDocument();
    expect(screen.getByText(/还没有/)).toBeVisible();
  });
});
