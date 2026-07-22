import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AdminAiLogsTab } from "./AdminAiLogsTab";
import type { AdminAiLog } from "../../api";

afterEach(cleanup);

const log = (over: Partial<AdminAiLog> = {}): AdminAiLog =>
  ({ id: 1, moduleName: "AURORA_CHAT", provider: "glm", modelName: "glm-4", success: true, fallbackUsed: false, errorMessage: null, createdAt: null, ...over });

describe("AdminAiLogsTab", () => {
  it("shows an empty state with no logs", () => {
    render(<AdminAiLogsTab logs={[]} />);
    expect(screen.getByText("没有 AI 日志")).toBeVisible();
  });

  it("lists logs with provider/model/success/fallback pills", () => {
    render(<AdminAiLogsTab logs={[log({ success: false, fallbackUsed: true, errorMessage: "超时" })]} />);
    expect(screen.getByText("AURORA_CHAT")).toBeVisible();
    expect(screen.getByText("glm")).toBeVisible();
    expect(screen.getByText("失败")).toBeVisible();
    expect(screen.getByText("fallback")).toBeVisible();
    expect(screen.getByText("超时")).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminAiLogsTab locale="en-SG" logs={[]} />);
    expect(screen.getByText("No AI logs")).toBeVisible();
  });
});
