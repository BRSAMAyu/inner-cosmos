import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AdminModelTab } from "./AdminModelTab";
import type { AdminAiHealth, AdminModelConfigRow } from "../../api";

afterEach(cleanup);

const health = (over: Partial<AdminAiHealth> = {}): AdminAiHealth => ({
  mode: "dev", provider: "glm", model: "glm-4", apiKeyConfigured: true, fallbackAllowed: true,
  mockProvider: false, asrProvider: "mock", asrModel: "mock-asr", asrKeyConfigured: false,
  lastSuccess: true, lastError: null, ...over
});

const config = (over: Partial<AdminModelConfigRow> = {}): AdminModelConfigRow =>
  ({ id: 1, configKey: "temperature", configValue: "0.7", description: "创造性程度", ...over });

describe("AdminModelTab", () => {
  it("shows AI health details", () => {
    render(<AdminModelTab health={health()} configs={[]} />);
    expect(screen.getByText(/LLM: glm \/ glm-4/)).toBeVisible();
    expect(screen.getByText(/已配置 key/)).toBeVisible();
  });

  it("lists model config entries", () => {
    render(<AdminModelTab health={health()} configs={[config()]} />);
    expect(screen.getByText("temperature")).toBeVisible();
    expect(screen.getByText("创造性程度")).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminModelTab locale="en-SG" health={health()} configs={[]} />);
    expect(screen.getByText(/key configured/)).toBeVisible();
  });
});
