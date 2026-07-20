import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AuroraSelfSpace } from "./AuroraSelfSpace";
import type { SelfEvolution } from "../api";

afterEach(cleanup);

const evolution: SelfEvolution = {
  candidates: [{ id: 1, dimension: "care", proposedBelief: "更早察觉你的疲惫", confidence: .8, evidenceRefs: "[]", createdAt: "2026-07-16T00:00:00" }],
  proposals: [{
    id: 9, sourceReflectionId: 2, dimension: "care", currentBelief: null, proposedBelief: "在你安静时先不追问",
    evidenceRefs: "[]", counterEvidence: "", expectedImpact: "", changesConstitution: false, rollbackTargetVersionId: 0,
    policyVersion: "v1", status: "EVALUATED",
    evaluation: { decision: "PASS", reasons: "", sandboxBefore: "before", sandboxAfter: "after", fidelityScore: .9, qualityScore: .8, continuityScore: .85 },
    createdAt: "2026-07-16T00:00:00"
  }],
  versions: [{ id: 3, versionNo: 2, parentVersionId: 1, rollbackTargetVersionId: null, sourceProposalId: 9,
    constitutionHash: "h", publicNarrative: "她学会了留白", status: "ACTIVE", activatedAt: "2026-07-16T00:00:00" }]
};

describe("AuroraSelfSpace", () => {
  it("renders the continuous-self surface in Chinese by default", () => {
    render(<AuroraSelfSpace evolution={evolution} busy={false} onPropose={() => undefined}
      onEvaluate={() => undefined} onActivate={() => undefined} onRollback={() => undefined} />);
    expect(screen.getByRole("heading", { name: "她最近学会了什么" })).toBeVisible();
    expect(screen.getByText("评测通过，等你确认")).toBeVisible();
    expect(screen.getByRole("button", { name: "允许她记住这次成长" })).toBeEnabled();
  });

  it("renders the same surface in English when locale is en-SG", () => {
    render(<AuroraSelfSpace locale="en-SG" evolution={evolution} busy={false} onPropose={() => undefined}
      onEvaluate={() => undefined} onActivate={() => undefined} onRollback={() => undefined} />);
    expect(screen.getByRole("heading", { name: "What she's recently learned" })).toBeVisible();
    expect(screen.getByText("Passed evaluation — awaiting your confirmation")).toBeVisible();
    expect(screen.getByRole("button", { name: "Let her remember this growth" })).toBeEnabled();
    expect(screen.getByText("An understanding taking shape · 80%")).toBeVisible();
  });
});
