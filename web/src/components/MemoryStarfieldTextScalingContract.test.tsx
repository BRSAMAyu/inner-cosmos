import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { MemoryStarfield, STARFIELD_VIEW_STORAGE_KEY } from "./MemoryStarfield";
import type { StarfieldScene, StarfieldStar } from "../api";

/**
 * CP-11 200% text-scaling contract for the memory starfield's accessible list path
 * (closing-checklist §2-15; same shape as ContrastTokenAudit / NonAuthorUsabilityContract).
 *
 * What this contract DOES assert:
 * - Static (component source + stylesheet): the list path's text containers — cosmos-space,
 *   cosmos-list rows, legend, more-memories, view toggle — declare no fixed px
 *   width/height/max-* and no px font-size, so a 200% browser text setting reflows rows
 *   instead of clipping them. The component applies no literal px geometry or px font-size
 *   anywhere inline.
 * - The only px sizing in the component is the decorative star-core diameter (a glyph-sized
 *   coloured dot that never contains text, always aria-hidden). Star-label chips — the one
 *   piece of map text that used to sit under a fixed px max-width — now width-track their own
 *   font size (em), verified against the LAST matching rule (the cascade winner).
 * - Rendered (jsdom, production stylesheet injected): in the list view the rows the user
 *   actually gets resolve to rem-based font sizes, and neither the ol nor any li carries an
 *   inline or stylesheet overflow/height clamp.
 *
 * What it deliberately does NOT assert: jsdom performs no layout and no zoom, so "no pixel is
 * ever clipped at 200%" cannot be proven here. The decorative 3D-style map keeps its
 * canvas-like overflow:hidden and is not covered — the list view is the scaled-down path.
 * Real-browser and real-device 200% zoom remain the CP-12A human research gate, exactly like
 * the rest of this contract family.
 */

const css = readFileSync(resolve(__dirname, "../styles.css"), "utf-8");
const componentSource = readFileSync(resolve(__dirname, "./MemoryStarfield.tsx"), "utf-8");

/** All rule bodies whose selector list mentions the given class (e.g. ".cosmos-list"). */
function rulesMentioning(selectorClass: string): string[] {
  const escaped = selectorClass.replace(".", "\\.");
  return [...css.matchAll(new RegExp(`[^{}]*${escaped}[^{}]*\\{[^}]*\\}`, "g"))]
    .map(match => match[0]);
}

const LIST_PATH_CONTAINERS = [".cosmos-space", ".cosmos-list", ".cosmos-legend",
  ".cosmos-more-memories", ".cosmos-view-toggle"];

describe("CP-11 200% text-scaling contract — static source and stylesheet", () => {
  it("applies no literal fixed px geometry or px font-size through inline styles", () => {
    // Star-core diameters are computed (`${...}px`), never literal digits, so a literal-px
    // match here would mean someone reintroduced a hard-pinned text container.
    expect(componentSource).not.toMatch(
      /(?:width|height|maxWidth|maxHeight|fontSize)\s*:\s*["'][ "'\d]*\d+(?:\.\d+)?px/i);
    expect(componentSource).not.toMatch(/(?:width|height)\s*:\s*\d+(?:\.\d+)?px/i);
  });

  it("keeps the only px-sized element decorative: star cores are aria-hidden dots", () => {
    const coreSpans = componentSource.match(/<span className="cosmos-star-core"[^>]*>/g) ?? [];
    expect(coreSpans.length).toBeGreaterThan(0);
    for (const span of coreSpans) {
      expect(span).toMatch(/aria-hidden="true"/); // never announced, never a text container
    }
  });

  it("pins no fixed px width/height or px font-size on any list-path container rule", () => {
    for (const container of LIST_PATH_CONTAINERS) {
      const rules = rulesMentioning(container);
      expect(rules.length).toBeGreaterThan(0); // selector renamed ⇒ contract must fail loudly
      for (const rule of rules) {
        expect(rule, rule).not.toMatch(/(?:^|[;{\s])(?:width|height|max-width|max-height)\s*:\s*[^;}]*\d+px/i);
        expect(rule, rule).not.toMatch(/font-size\s*:\s*\d+(?:\.\d+)?px/i);
      }
    }
  });

  it("expresses list text sizes in rem so they follow the user's font setting", () => {
    const listRules = rulesMentioning(".cosmos-list").join("\n");
    expect(listRules).toMatch(/font-size\s*:\s*\.?[\d.]+rem/i);
    const toggleRules = rulesMentioning(".cosmos-view-toggle").join("\n");
    expect(toggleRules).toMatch(/font-size\s*:\s*\.?[\d.]+rem/i);
  });

  it("lets the map's star-label chip width follow its font size, not a fixed px cap", () => {
    const labelRules = rulesMentioning(".cosmos-star-label");
    expect(labelRules.length).toBeGreaterThan(1); // historical px rules still present…
    const winner = labelRules[labelRules.length - 1]; // …but the last rule wins the cascade
    const maxWidth = winner.match(/max-width\s*:\s*([^;}]*)/)?.[1] ?? "";
    expect(maxWidth).toMatch(/[\d.]+em/i);
    expect(maxWidth).not.toMatch(/\d+px/i);
  });

  it("does not clip the accessible list itself (only the decorative map scrolls internally)", () => {
    const listRules = rulesMentioning(".cosmos-list").join("\n");
    expect(listRules).not.toMatch(/overflow\s*:\s*hidden/i);
    // The map is a canvas-like decorative surface, explicitly out of the scaling contract.
    expect(rulesMentioning(".cosmos-map").join("\n")).toMatch(/overflow\s*:\s*hidden/i);
  });
});

const baseStar: StarfieldStar = {
  id: 1, title: "星1", summary: "一段足够长的真实摘要，用来代表 200% 文本缩放下仍需完整换行阅读的记忆内容。",
  theme: "工作", color: "#fff", gravity: 1, glow: .8, freshness: 1, x: 0, y: 0,
  memoryLayer: "EPISODIC", confidence: .9, versionNo: 2, peopleTags: null,
  status: "ACTIVE", occurredAt: null, ariaLabel: "星1", connectedMemoryIds: []
};

const scene: StarfieldScene = {
  mode: "TIME", modeExplanation: "按时间排列",
  stars: Array.from({ length: 6 }, (_, index) => ({ ...baseStar, id: index + 1, title: `星${index + 1}` })),
  accessibleList: Array.from({ length: 6 }, (_, index) => ({ ...baseStar, id: index + 1, title: `星${index + 1}` })),
  legend: { 尺寸: "情感重力与长期重要性" }, generatedAt: "2026-07-15T00:00:00Z"
};

describe("CP-11 200% text-scaling contract — rendered list view under the production stylesheet", () => {
  let productionStyle: HTMLStyleElement;

  beforeEach(() => {
    // jsdom does not load the app stylesheet on its own; inject the real file so the
    // computed values below reflect what a browser would cascade onto these elements.
    productionStyle = document.createElement("style");
    productionStyle.textContent = css;
    document.head.appendChild(productionStyle);
  });
  afterEach(() => {
    productionStyle.remove();
    cleanup();
    localStorage.clear();
  });

  const renderListView = () => {
    const view = render(<MemoryStarfield starfield={scene} starfieldBusy={false}
      onChangeMode={() => undefined} starfieldDetail={null} detailBusy={null}
      onRevealStar={() => undefined} onCloseDetail={() => undefined} memoryOperations={[]}
      rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "列表视图" }));
    return view;
  };

  it("flows every memory as an unclamped li — no inline geometry, full content present", () => {
    const { container } = renderListView();
    const rows = [...container.querySelectorAll<HTMLElement>(".cosmos-list > li")];
    expect(rows).toHaveLength(scene.accessibleList.length); // content grows the list, never truncates
    for (const row of rows) {
      expect(row.getAttribute("style")).toBeNull(); // no inline size/overflow override
      const computed = getComputedStyle(row);
      expect(propertyOrNone(computed, "maxHeight")).not.toMatch(/\d+px/i);
      expect(propertyOrNone(computed, "overflow")).not.toBe("hidden");
    }
    const list = container.querySelector<HTMLElement>(".cosmos-list")!;
    expect(getComputedStyle(list).overflow).not.toBe("hidden");
  });

  it("resolves list text to rem units from the production stylesheet, not fixed px", () => {
    const { container } = renderListView();
    const summary = container.querySelector<HTMLElement>(".cosmos-list li p")!;
    const meta = container.querySelector<HTMLElement>(".cosmos-list li small")!;
    expect(getComputedStyle(summary).fontSize).toMatch(/rem$/i);
    expect(getComputedStyle(meta).fontSize).toMatch(/rem$/i);
    expect(getComputedStyle(summary).fontSize).not.toMatch(/\d+px$/i);
  });

  it("keeps the presentation toggle itself scalable (rem type, no fixed px box)", () => {
    renderListView();
    const toggle = screen.getByRole("button", { name: "星图视图" });
    expect(getComputedStyle(toggle).fontSize).toMatch(/rem$/i);
    expect(toggle.getAttribute("style")).toBeNull();
    expect(localStorage.getItem(STARFIELD_VIEW_STORAGE_KEY)).toBe("list");
  });
});

/** jsdom leaves unset computed properties as "" or "none" — normalize so `.not.toMatch` is honest. */
function propertyOrNone(computed: CSSStyleDeclaration, property: string): string {
  return computed.getPropertyValue(property) || "none";
}
