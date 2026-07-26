import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PlazaDirectory } from "./PlazaDirectory";
import type { PublicCapsule } from "../api";

afterEach(cleanup);

const capsule = (over: Partial<PublicCapsule> = {}): PublicCapsule => ({
  id: 1, pseudonym: "雨后的人", intro: "先沉默再表达边界", capsuleType: "USER_CAPSULE",
  publicTags: "[\"自我观察\",\"关系\"]", echoEnergy: 0.72, freshnessScore: 0.8, conversationLimitPerDay: 30,
  lastActivityAt: "2026-07-15T00:00:00Z", ...over
});

describe("PlazaDirectory", () => {
  it("lists public capsules with their tags and opens a chat with one", () => {
    const onOpenCapsule = vi.fn();
    const c = capsule();
    render(<PlazaDirectory capsules={[c]} activeCapsuleId={null} busy={false} onOpenCapsule={onOpenCapsule} />);
    expect(screen.getByText("雨后的人")).toBeVisible();
    expect(screen.getByText("自我观察", { selector: ".plaza-card-tags span" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "和这个侧影聊聊" }));
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

  it("sorts by the selected key instead of forcing real-person capsules to the front", () => {
    const capsules = [
      ...Array.from({ length: 6 }, (_, index) => capsule({
        id: index + 1,
        pseudonym: `官方 ${index + 1}`,
        capsuleType: "SEED_CAPSULE",
        echoEnergy: .99 - index / 100,
        freshnessScore: .2 + index / 100,
        lastActivityAt: `2026-07-${10 + index}T00:00:00Z`
      })),
      capsule({ id: 20, pseudonym: "真实侧影", capsuleType: "USER_CAPSULE", echoEnergy: .3, freshnessScore: 1, lastActivityAt: "2026-07-20T00:00:00Z" })
    ];
    render(<PlazaDirectory capsules={capsules} activeCapsuleId={null} busy={false} onOpenCapsule={() => undefined} />);

    expect(screen.getAllByRole("listitem")[0]).toHaveTextContent("官方 1");
    expect(screen.getAllByTitle("回声能量")[0]).toHaveTextContent("99%");
    fireEvent.click(screen.getByRole("button", { name: "新鲜度" }));
    expect(screen.getAllByRole("listitem")[0]).toHaveTextContent("真实侧影");
    fireEvent.click(screen.getByRole("button", { name: "最近活跃" }));
    expect(screen.getAllByRole("listitem")[0]).toHaveTextContent("真实侧影");
    expect(screen.getByText("可以写信给本人")).toBeVisible();
    expect(screen.getAllByRole("listitem")).toHaveLength(6);
    fireEvent.click(screen.getByRole("button", { name: "继续浏览另外 1 个侧影" }));
    expect(screen.getAllByRole("listitem")).toHaveLength(7);
    expect(screen.getByText("官方 6")).toBeVisible();
  });

  it("keeps large theme vocabularies progressively disclosed on small screens", () => {
    const tags = Array.from({ length: 15 }, (_, index) => `theme-${String.fromCharCode(65 + index)}`);
    render(<PlazaDirectory capsules={[capsule({ publicTags: JSON.stringify(tags) })]}
      activeCapsuleId={null} busy={false} onOpenCapsule={() => undefined} />);

    expect(screen.queryByRole("button", { name: "theme-O" })).not.toBeInTheDocument();
    const disclosure = screen.getByRole("button", { name: "展开另外 3 个主题" });
    expect(disclosure).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(disclosure);
    expect(screen.getByRole("button", { name: "theme-O" })).toBeVisible();
    expect(screen.getByRole("button", { name: "收起主题" })).toHaveAttribute("aria-expanded", "true");
  });

  it("shows an empty state when there are no public capsules", () => {
    render(<PlazaDirectory capsules={[]} activeCapsuleId={null} busy={false} onOpenCapsule={() => undefined} />);
    expect(screen.queryByRole("button", { name: "开始对话" })).not.toBeInTheDocument();
    expect(screen.getByText(/还没有/)).toBeVisible();
  });

  it("renders headings, sort controls and cards in English when locale is en-SG", () => {
    render(<PlazaDirectory locale="en-SG" capsules={[capsule()]} activeCapsuleId={null} busy={false} onOpenCapsule={() => undefined} />);
    expect(screen.getByRole("heading", { name: /Meet a facet first/ })).toBeVisible();
    expect(screen.getByText("1 public capsule")).toBeVisible();
    expect(screen.getByRole("button", { name: "Echo energy" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Talk to this facet" })).toBeVisible();
  });
});
