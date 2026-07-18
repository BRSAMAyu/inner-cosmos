import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ClaimCandidate } from "../api";
import { ClaimCandidateReview } from "./ClaimCandidateReview";

afterEach(cleanup);

const candidate = (over: Partial<ClaimCandidate> = {}): ClaimCandidate => ({
  id: 1, claimType: "PREFERENCE", value: "在下雨天读书", authorityLevel: "SINGLE_EXPLICIT",
  confidence: 0.55, provenanceMessageIds: [101], evidenceText: "喜欢在下雨天读书",
  uncertain: false, alreadyActive: false, createdAt: "2026-07-16T09:30:00", ...over
});

describe("ClaimCandidateReview", () => {
  it("renders nothing when there are no candidates", () => {
    const { container } = render(<ClaimCandidateReview candidates={[]} onConfirm={() => undefined} onDismiss={() => undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the friendly type, value, evidence, confidence and provenance count", () => {
    render(<ClaimCandidateReview candidates={[candidate()]} onConfirm={() => undefined} onDismiss={() => undefined} />);
    expect(screen.getByText("偏好")).toBeVisible();
    expect(screen.getByText("在下雨天读书")).toBeVisible();
    expect(screen.getByText(/喜欢在下雨天读书/)).toBeVisible();
    expect(screen.getByText(/55% 把握 · 来自 1 处对话/)).toBeVisible();
  });

  it("confirming a candidate calls onConfirm with its id", () => {
    const onConfirm = vi.fn();
    render(<ClaimCandidateReview candidates={[candidate({ id: 42 })]} onConfirm={onConfirm} onDismiss={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "对，就是我" }));
    expect(onConfirm).toHaveBeenCalledExactlyOnceWith(42);
  });

  it("dismissing requires an inline confirmation before calling onDismiss", () => {
    const onDismiss = vi.fn();
    render(<ClaimCandidateReview candidates={[candidate({ id: 42 })]} onConfirm={() => undefined} onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole("button", { name: "不太是我" }));
    expect(onDismiss).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "确认忽略" }));
    expect(onDismiss).toHaveBeenCalledExactlyOnceWith(42);
  });

  it("marks a busy candidate's actions as in-progress and disabled", () => {
    // AsyncButton (web/src/loading.tsx) keeps the original label for the first second of a busy
    // state, so a synchronous render/assert checks disabled on the original label -- matching the
    // convention already used by every other AsyncButton-adopting component's tests in this repo.
    render(<ClaimCandidateReview candidates={[candidate({ id: 7 })]} busyId={7} onConfirm={() => undefined} onDismiss={() => undefined} />);
    expect(screen.getByRole("button", { name: "对，就是我" })).toBeDisabled();
  });

  it("surfaces uncertain and already-known badges", () => {
    render(<ClaimCandidateReview candidates={[candidate({ uncertain: true, alreadyActive: true })]}
      onConfirm={() => undefined} onDismiss={() => undefined} />);
    expect(screen.getByText("你也还在确认")).toBeVisible();
    expect(screen.getByText("已在你的理解中")).toBeVisible();
  });

  it("renders English (en-SG) copy and type labels when that locale is selected", () => {
    const onConfirm = vi.fn();
    render(<ClaimCandidateReview candidates={[candidate()]} locale="en-SG"
      onConfirm={onConfirm} onDismiss={() => undefined} />);
    expect(screen.getByText("Preference")).toBeVisible();
    expect(screen.getByText(/from 1 moments/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Yes, that's me" }));
    expect(onConfirm).toHaveBeenCalledExactlyOnceWith(1);
  });
});
