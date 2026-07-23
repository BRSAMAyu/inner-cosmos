import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CapsuleWorkbench } from "./CapsuleWorkbench";
import type { CapsuleBoundary, CapsuleGenomeVersion, EchoCapsule, MemoryCard } from "../api";

afterEach(cleanup);

const memory: MemoryCard = { id: 1, title: "一次和解", summary: null, status: "ACTIVE", versionNo: 1, consentScope: "SHARED", memoryLayer: "EPISODIC", confidence: .8 };
const capsule: EchoCapsule = { id: 9, pseudonym: "雨后的人", intro: "先沉默再表达", authorizedMemoryIds: "[1]", visibilityStatus: "PRIVATE", isPublic: false, activeGenomeVersionId: 3, publicTags: "[]" };
const genomeVersion: CapsuleGenomeVersion = { id: 3, versionNo: 1, parentVersionId: null, compilerVersion: "v1", status: "ACTIVE", evaluationJson: "{}", changeReason: "初始编译", createdAt: "2026-07-15T00:00:00Z" };
const boundary: CapsuleBoundary = { capsuleId: 9, allowTopics: "自我观察, 日常支持", blockedTopics: "真实姓名, 诊断承诺", maxConversationTurns: 30, allowLetterRequest: true, privacyLevel: "STRICT", version: 1 };

describe("CapsuleWorkbench", () => {
  it("lets the owner start a new capsule from the create form", () => {
    const onCapsuleName = vi.fn();
    const onToggleMemory = vi.fn();
    render(<CapsuleWorkbench capsules={[]} selectedCapsuleId={null} selectedCapsule={null} selectableMemories={[memory]}
      selectedMemoryIds={[]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={onToggleMemory} onCapsuleName={onCapsuleName} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined} />);
    fireEvent.click(screen.getByLabelText(/一次和解/));
    expect(onToggleMemory).toHaveBeenCalledWith(1);
    fireEvent.change(screen.getByPlaceholderText("例如：雨后仍愿意开口的人"), { target: { value: "新名字" } });
    expect(onCapsuleName).toHaveBeenCalledWith("新名字");
  });

  it("lets the owner recompile, publish and archive an existing capsule", () => {
    const onRecompile = vi.fn();
    const onPublish = vi.fn();
    const onArchive = vi.fn();
    render(<CapsuleWorkbench capsules={[capsule]} selectedCapsuleId={capsule.id} selectedCapsule={capsule} selectableMemories={[memory]}
      selectedMemoryIds={[1]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[genomeVersion]} fidelitySummary={[{ genomeVersionId: 3, versionNo: 1, totalRatings: 2, likeMeCount: 1, notMeCount: 1, factWrongCount: 0, tooExposedCount: 0, toneWrongCount: 0, fidelityScore: 0.5 }]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={onRecompile} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={onPublish} onPause={() => undefined} onArchive={onArchive} />);
    expect(screen.getAllByText(/2 次反馈 · 50% 像我/).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("button", { name: "用当前选择生成新版本" }));
    expect(onRecompile).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "确认并发布当前版本" }));
    expect(onPublish).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "撤回这个共鸣体" }));
    expect(onArchive).toHaveBeenCalledOnce();
  });

  it("edits and saves the capsule's conversation boundary", () => {
    const onSaveBoundary = vi.fn();
    render(<CapsuleWorkbench capsules={[capsule]} selectedCapsuleId={capsule.id} selectedCapsule={capsule} selectableMemories={[memory]}
      selectedMemoryIds={[1]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[genomeVersion]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined}
      boundary={boundary} boundaryBusy={false} onSaveBoundary={onSaveBoundary} />);
    const blocked = screen.getByLabelText("明确避开的话题") as HTMLInputElement;
    expect(blocked.value).toBe("真实姓名, 诊断承诺");
    fireEvent.change(blocked, { target: { value: "真实姓名, 诊断承诺, 强迫即时回应" } });
    fireEvent.click(screen.getByRole("button", { name: "保存边界设置" }));
    expect(onSaveBoundary).toHaveBeenCalledOnce();
    expect(onSaveBoundary.mock.calls[0][0]).toMatchObject({
      blockedTopics: "真实姓名, 诊断承诺, 强迫即时回应", allowTopics: "自我观察, 日常支持",
      maxConversationTurns: 30, allowLetterRequest: true, privacyLevel: "STRICT"
    });
  });

  it("disables the boundary save while it is busy", () => {
    render(<CapsuleWorkbench capsules={[capsule]} selectedCapsuleId={capsule.id} selectedCapsule={capsule} selectableMemories={[memory]}
      selectedMemoryIds={[1]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[genomeVersion]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined}
      boundary={boundary} boundaryBusy={true} onSaveBoundary={() => undefined} />);
    // AsyncButton (web/src/loading.tsx) keeps the original label for the first second of a busy
    // state, so a synchronous render/assert checks disabled on the original label.
    expect(screen.getByRole("button", { name: "保存边界设置" })).toBeDisabled();
  });

  it("lets the owner set the owner context note, stand-in permission and contact policy when creating a capsule", () => {
    const onCapsuleOwnerNote = vi.fn();
    const onCapsuleStandIn = vi.fn();
    const onCapsuleContactPolicy = vi.fn();
    render(<CapsuleWorkbench capsules={[]} selectedCapsuleId={null} selectedCapsule={null} selectableMemories={[memory]}
      selectedMemoryIds={[]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined}
      capsuleOwnerNote="" onCapsuleOwnerNote={onCapsuleOwnerNote} capsuleStandIn={false} onCapsuleStandIn={onCapsuleStandIn}
      capsuleContactPolicy="LETTER_ONLY" onCapsuleContactPolicy={onCapsuleContactPolicy} />);
    fireEvent.change(screen.getByLabelText("给它的额外背景说明"), { target: { value: "我在关系里习惯先沉默" } });
    expect(onCapsuleOwnerNote).toHaveBeenCalledWith("我在关系里习惯先沉默");
    fireEvent.click(screen.getByLabelText("允许它先作为回声代你回应"));
    expect(onCapsuleStandIn).toHaveBeenCalledWith(true);
    fireEvent.change(screen.getByLabelText("真人联系方式"), { target: { value: "STAND_IN_FIRST" } });
    expect(onCapsuleContactPolicy).toHaveBeenCalledWith("STAND_IN_FIRST");
  });

  it("lets the owner edit and save an existing capsule's context and contact policy", () => {
    const onSaveContext = vi.fn();
    const withContext: EchoCapsule = { ...capsule, ownerContextNote: "先前的备注", standInEnabled: false, realContactPolicy: "LETTER_ONLY" };
    render(<CapsuleWorkbench capsules={[withContext]} selectedCapsuleId={withContext.id} selectedCapsule={withContext} selectableMemories={[memory]}
      selectedMemoryIds={[1]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[genomeVersion]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined}
      onSaveContext={onSaveContext} />);
    const note = screen.getByLabelText("给它的额外背景说明") as HTMLTextAreaElement;
    expect(note.value).toBe("先前的备注");
    fireEvent.change(note, { target: { value: "更新后的备注" } });
    fireEvent.click(screen.getByRole("button", { name: "保存背景与联系设置" }));
    expect(onSaveContext).toHaveBeenCalledWith({ ownerContextNote: "更新后的备注", standInEnabled: false, realContactPolicy: "LETTER_ONLY" });
  });

  // W2 UIUX audit follow-up: each memory checkbox's <label> wrapped <strong>title</strong>
  // <small>layer · version</small> with no separator, so the wrapped checkbox's accessible name
  // concatenated into a run-on string (live-verified against a real memory: label.textContent ===
  // "今日沉淀EPISODIC · v1"). Same shape as the ProductShellNavigation run-on bug this campaign
  // already fixed. Fixed with an explicit aria-label on the checkbox and aria-hidden on the visual
  // duplicate, in both the create-tab list and the review-tab "compact" list.
  it("gives each memory checkbox a separated accessible name (title + layer/version), not a run-on concatenation", () => {
    render(<CapsuleWorkbench capsules={[]} selectedCapsuleId={null} selectedCapsule={null} selectableMemories={[memory]}
      selectedMemoryIds={[]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined} />);
    expect(screen.getByLabelText("一次和解 · EPISODIC · v1")).toBeInTheDocument();
  });

  it("gives the review-tab compact memory checkbox the same separated accessible name", () => {
    render(<CapsuleWorkbench capsules={[capsule]} selectedCapsuleId={capsule.id} selectedCapsule={capsule} selectableMemories={[memory]}
      selectedMemoryIds={[1]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[genomeVersion]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined} />);
    expect(screen.getByLabelText("一次和解 · v1")).toBeInTheDocument();
  });

  it("renders the workbench and boundary editor in English when locale is en-SG", () => {
    render(<CapsuleWorkbench locale="en-SG" capsules={[capsule]} selectedCapsuleId={capsule.id} selectedCapsule={capsule} selectableMemories={[memory]}
      selectedMemoryIds={[1]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[genomeVersion]} fidelitySummary={[]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={() => undefined} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={() => undefined} onPause={() => undefined} onArchive={() => undefined}
      boundary={boundary} boundaryBusy={false} onSaveBoundary={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Confirm it's like you before others meet it" })).toBeVisible();
    expect(screen.getByText("1 capsule")).toBeVisible();
    expect(screen.getByRole("button", { name: "Generate a new version from the current selection" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Save boundary settings" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Withdraw this capsule" })).toBeVisible();
  });
});
