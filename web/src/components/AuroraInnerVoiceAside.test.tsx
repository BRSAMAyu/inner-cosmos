import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuroraInnerVoiceAside } from "./AuroraInnerVoiceAside";

const voice = {
  key: "inner-42",
  text: "我也有一点舍不得催你往前走。",
  audio: "data:audio/mpeg;base64,AAA",
  voiceId: "warm-a"
};

afterEach(cleanup);

describe("AuroraInnerVoiceAside", () => {
  it("stays outside the interface when the preference is disabled", () => {
    render(<AuroraInnerVoiceAside voice={voice} enabled={false} mode="AMBIENT" onDismiss={vi.fn()} />);
    expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
  });

  it("shows an ambient heart-voice without autoplaying it", () => {
    render(<AuroraInnerVoiceAside voice={voice} enabled mode="AMBIENT" onDismiss={vi.fn()} />);

    expect(screen.getByRole("complementary", { name: "还有一句，她没有说出口" })).toBeVisible();
    expect(screen.getByText(voice.text)).toBeVisible();
    expect(screen.getByRole("button", { name: "▶ 播放" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /播放中/ })).not.toBeInTheDocument();
  });

  it("keeps on-demand content veiled until invited, then lets it disappear", () => {
    const onDismiss = vi.fn();
    render(<AuroraInnerVoiceAside voice={voice} enabled mode="ON_DEMAND" onDismiss={onDismiss} />);

    expect(screen.queryByText(voice.text)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "让它浮现" }));
    expect(screen.getByText(voice.text)).toBeVisible();
    expect(screen.getByRole("button", { name: "▶ 播放" })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "让它散去" }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it("keeps the textual heart-voice visible when no audio layer is available", () => {
    render(<AuroraInnerVoiceAside voice={{ ...voice, audio: undefined }} enabled
      mode="AMBIENT" onDismiss={vi.fn()} />);

    expect(screen.getByText(voice.text)).toBeVisible();
    expect(screen.queryByRole("button", { name: "▶ 播放" })).not.toBeInTheDocument();
  });
});
