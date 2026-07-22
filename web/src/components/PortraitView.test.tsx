import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PortraitView } from "./PortraitView";

afterEach(cleanup);

describe("PortraitView", () => {
  it("shows an empty state when Aurora has no dimensions yet", () => {
    render(<PortraitView dimensions={[]} history={{}} calibrated={{}} busyDim={null}
      onLoadHistory={() => undefined} onCalibrate={() => undefined} />);
    expect(screen.getByText("Aurora 还没有形成对你的理解。多和它聊聊，这里会慢慢长出你的轮廓。")).toBeVisible();
  });

  it("renders dimensions sorted by confidence with a readable value and confidence percentage", () => {
    render(<PortraitView
      dimensions={[
        { dim: "VALUES", valueJson: "\"在意被认真回应\"", confidence: 0.4, updatedAt: null },
        { dim: "INNER_DRIVE", valueJson: "\"想把事情做扎实\"", confidence: 0.8, updatedAt: null }
      ]}
      history={{}} calibrated={{}} busyDim={null}
      onLoadHistory={() => undefined} onCalibrate={() => undefined} />);
    const names = screen.getAllByText(/内驱力|在意的事/).map(el => el.textContent);
    expect(names[0]).toBe("内驱力");
    expect(screen.getByText("想把事情做扎实")).toBeVisible();
    expect(screen.getByText("把握 80%")).toBeVisible();
  });

  it("requests history on expand, hides it on collapse, and shows the loaded rows", () => {
    const onLoadHistory = vi.fn();
    render(<PortraitView
      dimensions={[{ dim: "VALUES", valueJson: "\"在意被认真回应\"", confidence: 0.5, updatedAt: null }]}
      history={{ VALUES: [{ valueJson: "\"旧的理解\"", recordedAt: "2026-01-01T00:00:00" }] }}
      calibrated={{}} busyDim={null} onLoadHistory={onLoadHistory} onCalibrate={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "看它怎么变的" }));
    expect(onLoadHistory).toHaveBeenCalledExactlyOnceWith("VALUES");
    expect(screen.getByText("旧的理解")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "看它怎么变的" }));
    expect(screen.queryByText("旧的理解")).not.toBeInTheDocument();
  });

  it("opens the calibration form, submits the trimmed draft with the current value as oldValue, and closes it", () => {
    const onCalibrate = vi.fn();
    render(<PortraitView
      dimensions={[{ dim: "EMOTION_PATTERN", valueJson: "\"容易担心\"", confidence: 0.6, updatedAt: null }]}
      history={{}} calibrated={{}} busyDim={null} onLoadHistory={() => undefined} onCalibrate={onCalibrate} />);
    fireEvent.click(screen.getByRole("button", { name: "这不太是我" }));
    expect(screen.getByText("Aurora 现在的理解是：容易担心")).toBeVisible();
    const textarea = screen.getByPlaceholderText("比如：我其实不是外向，只是在熟人面前才放得开…");
    fireEvent.change(textarea, { target: { value: "  我只是谨慎，不是担心  " } });
    fireEvent.click(screen.getByRole("button", { name: "告诉 Aurora" }));
    expect(onCalibrate).toHaveBeenCalledExactlyOnceWith("EMOTION_PATTERN", "容易担心", "  我只是谨慎，不是担心  ");
    expect(screen.queryByPlaceholderText("比如：我其实不是外向，只是在熟人面前才放得开…")).not.toBeInTheDocument();
  });

  it("disables the save button while busy for this dimension, and shows the calibrated note", () => {
    const { rerender } = render(<PortraitView
      dimensions={[{ dim: "VALUES", valueJson: "\"在意被认真回应\"", confidence: 0.5, updatedAt: null }]}
      history={{}} calibrated={{}} busyDim={null} onLoadHistory={() => undefined} onCalibrate={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "这不太是我" }));
    fireEvent.change(screen.getByPlaceholderText("比如：我其实不是外向，只是在熟人面前才放得开…"), { target: { value: "更准确的说法" } });
    rerender(<PortraitView
      dimensions={[{ dim: "VALUES", valueJson: "\"在意被认真回应\"", confidence: 0.5, updatedAt: null }]}
      history={{}} calibrated={{}} busyDim="VALUES" onLoadHistory={() => undefined} onCalibrate={() => undefined} />);
    expect(screen.getByRole("button", { name: "告诉 Aurora" })).toBeDisabled();

    rerender(<PortraitView
      dimensions={[{ dim: "VALUES", valueJson: "\"在意被认真回应\"", confidence: 0.5, updatedAt: null }]}
      history={{}} calibrated={{ VALUES: true }} busyDim={null} onLoadHistory={() => undefined} onCalibrate={() => undefined} />);
    expect(screen.getByText("✓ Aurora 会把你的看法和它的观察并在一起。")).toBeVisible();
  });

  it("renders in English when locale is en-SG, including dimension labels", () => {
    render(<PortraitView locale="en-SG"
      dimensions={[{ dim: "INNER_DRIVE", valueJson: "\"wants to do things thoroughly\"", confidence: 0.8, updatedAt: null }]}
      history={{}} calibrated={{}} busyDim={null} onLoadHistory={() => undefined} onCalibrate={() => undefined} />);
    expect(screen.getByRole("heading", { name: "How Aurora sees you" })).toBeVisible();
    expect(screen.getByText("Inner drive")).toBeVisible();
    expect(screen.getByText(/Grasped 80%/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Not quite me" }));
    expect(screen.getByText(/Aurora's current understanding is/)).toBeVisible();
  });
});
