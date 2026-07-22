import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AdminSafetyTab } from "./AdminSafetyTab";
import type { AdminSafetyEvent } from "../../api";

afterEach(cleanup);

const event = (over: Partial<AdminSafetyEvent> = {}): AdminSafetyEvent =>
  ({ id: 1, userId: 1, riskType: "SELF_HARM", riskLevel: "HIGH", matchedRule: null, handledAction: null, triggerScene: null, createdAt: null, ...over });

describe("AdminSafetyTab", () => {
  it("shows an empty state with no events", () => {
    render(<AdminSafetyTab events={[]} />);
    expect(screen.getByText("没有安全事件")).toBeVisible();
  });

  it("lists safety events with risk type and level", () => {
    render(<AdminSafetyTab events={[event()]} />);
    expect(screen.getByText("安全事件 #1")).toBeVisible();
    expect(screen.getByText(/SELF_HARM/)).toBeVisible();
    expect(screen.getByText(/HIGH/)).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminSafetyTab locale="en-SG" events={[event()]} />);
    expect(screen.getByText("Safety event #1")).toBeVisible();
  });
});
