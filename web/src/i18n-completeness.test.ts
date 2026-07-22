/// <reference types="node" />
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

// Guards against the exact regression found in RelationsView.tsx and PortraitView.tsx: a component
// that renders hardcoded Chinese text/JSX with no `locale` prop or i18n import at all, so it never
// honors the user's chosen locale regardless of what COPY table other sibling components use.
//
// Heuristic (deliberately simple, matching DomainBoundaryArchitectureTest's own regex-on-source-text
// style rather than full AST parsing): any non-test .tsx file containing a CJK character must also
// import something from an "i18n" module (either the shared web/src/i18n.ts, or re-export it locally).
// This doesn't guarantee every string is translated, but it does guarantee a component can't be
// entirely CJK-hardcoded with zero locale-awareness ever again without a human noticing in review.

const CJK_PATTERN = /[一-鿿]/;
// Accepts either the shared web/src/i18n.ts Locale type, or the parallel, structurally-identical
// SkillLocale type (PsychologySkillStudio.tsx and its consumers) -- both are established,
// intentional bilingual-parameterization conventions in this codebase.
const I18N_IMPORT_PATTERN = /from\s+["'](?:\.+\/)+i18n["']|\bSkillLocale\b/;

export function containsCjk(source: string): boolean {
  return CJK_PATTERN.test(source);
}

export function importsI18n(source: string): boolean {
  return I18N_IMPORT_PATTERN.test(source);
}

// Files that legitimately contain CJK but are not themselves locale-parameterized components --
// each entry must have a real, reviewed reason; this list must never grow silently.
const ALLOWLIST = new Set<string>([
  // Entry point: its only CJK is in code comments (module-purpose notes), not rendered UI text.
  "main.tsx",
  // Thin container around vite-plugin-pwa's useRegisterSW(); its only CJK is a code comment
  // quoting UpdateBanner.tsx's actual (already-bilingual) button text for a citation, not UI text
  // of its own.
  join("components", "PwaUpdateNotice.tsx")
]);

function findTsxFiles(dir: string): string[] {
  const entries = readdirSync(dir);
  const files: string[] = [];
  for (const entry of entries) {
    const full = join(dir, entry);
    const stats = statSync(full);
    if (stats.isDirectory()) {
      if (entry === "node_modules") continue;
      files.push(...findTsxFiles(full));
    } else if (entry.endsWith(".tsx") && !entry.endsWith(".test.tsx")) {
      files.push(full);
    }
  }
  return files;
}

describe("i18n-completeness detector logic", () => {
  it("detects CJK characters in a string", () => {
    expect(containsCjk("你好，Aurora")).toBe(true);
    expect(containsCjk("hello, Aurora")).toBe(false);
    expect(containsCjk("")).toBe(false);
  });

  it("detects an i18n import regardless of relative depth", () => {
    expect(importsI18n('import type { Locale } from "../i18n";')).toBe(true);
    expect(importsI18n('import type { Locale } from "../../i18n";')).toBe(true);
    expect(importsI18n('import type { Locale } from "./i18n";')).toBe(true);
    expect(importsI18n('import { AsyncButton } from "../loading";')).toBe(false);
  });
});

describe("i18n-completeness guard", () => {
  it("every non-test .tsx component containing CJK text also imports the shared i18n module", () => {
    const componentsDir = join(__dirname, "components");
    const rootTsxFiles = readdirSync(__dirname)
      .filter(name => name.endsWith(".tsx") && !name.endsWith(".test.tsx"))
      .map(name => join(__dirname, name));
    const files = [...findTsxFiles(componentsDir), ...rootTsxFiles];

    const violations = files
      .map(file => ({ file, source: readFileSync(file, "utf-8") }))
      .filter(({ source }) => containsCjk(source) && !importsI18n(source))
      .map(({ file }) => relative(__dirname, file))
      .filter(relPath => !ALLOWLIST.has(relPath));

    expect(violations, `these files render hardcoded CJK text with no i18n import:\n  ${violations.join("\n  ")}`).toEqual([]);
  });
});
