import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CapsuleWorkbench } from "./CapsuleWorkbench";
import type { CapsuleGenomeVersion, EchoCapsule, MemoryCard } from "../api";

afterEach(cleanup);

const memory: MemoryCard = { id: 1, title: "一次和解", summary: null, status: "ACTIVE", versionNo: 1, consentScope: "SHARED", memoryLayer: "EPISODIC", confidence: .8 };
const capsule: EchoCapsule = { id: 9, pseudonym: "雨后的人", intro: "先沉默再表达", authorizedMemoryIds: "[1]", visibilityStatus: "PRIVATE", isPublic: false, activeGenomeVersionId: 3, publicTags: "[]" };
const genomeVersion: CapsuleGenomeVersion = { id: 3, versionNo: 1, parentVersionId: null, compilerVersion: "v1", status: "ACTIVE", evaluationJson: "{}", changeReason: "初始编译", createdAt: "2026-07-15T00:00:00Z" };

describe("CapsuleWorkbench", () => {
  it("lets the owner start a new capsule from the create form", () => {
    const onCapsuleName = vi.fn();
    const onToggleMemory = vi.fn();
    render(<CapsuleWorkbench capsules={[]} selectedCapsuleId={null} selectedCapsule={null} selectableMemories={[memory]}
      selectedMemoryIds={[]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[]}
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
      selectedMemoryIds={[1]} capsuleName="" capsuleIntro="" capsulePreview={null} capsuleBusy={false} genomeHistory={[genomeVersion]}
      sandboxQuestion="" sandboxResult={null} sandboxFeedback={null} onSelectCapsule={() => undefined}
      onToggleMemory={() => undefined} onCapsuleName={() => undefined} onCapsuleIntro={() => undefined}
      onPreviewNewCapsule={() => undefined} onCancelPreview={() => undefined} onCreateCapsule={() => undefined}
      onRecompile={onRecompile} onSandboxQuestion={() => undefined} onRunSandbox={() => undefined}
      onRateSandbox={() => undefined} onPublish={onPublish} onPause={() => undefined} onArchive={onArchive} />);
    fireEvent.click(screen.getByRole("button", { name: "用当前选择生成新版本" }));
    expect(onRecompile).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "确认并发布当前版本" }));
    expect(onPublish).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "撤回这个共鸣体" }));
    expect(onArchive).toHaveBeenCalledOnce();
  });
});
