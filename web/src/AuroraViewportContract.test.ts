import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const sourceRoot = path.resolve(import.meta.dirname);
const appSource = readFileSync(path.join(sourceRoot, "AuroraApp.tsx"), "utf8");
const stylesheet = readFileSync(path.join(sourceRoot, "styles.css"), "utf8");

describe("Aurora full-viewport conversation contract", () => {
  it("removes dashboard cards from Today and puts support behind a collapsed disclosure", () => {
    const conversation = appSource.indexOf("<AuroraConversation");
    const support = appSource.indexOf('<details className="aurora-support-drawer"');
    const demo = appSource.indexOf("<DemoPersonaChooser", support);
    const returns = appSource.indexOf('<section className="returns"', support);

    expect(conversation).toBeGreaterThan(0);
    expect(support).toBeGreaterThan(conversation);
    expect(demo).toBeGreaterThan(support);
    expect(returns).toBeGreaterThan(support);
    expect(appSource).not.toContain("<TodayOverview");
    expect(appSource).not.toContain("<QuickHello");
    expect(appSource).not.toContain("<StartHereJourney");
  });

  it("uses an uncapped dynamic viewport with transcript flex and fixed composer", () => {
    expect(stylesheet).toMatch(/\.space-aurora \.aurora-primary\s*\{[^}]*top:\s*auto;[^}]*height:\s*calc\(100dvh - 140px\);[^}]*min-height:\s*420px;[^}]*overflow:\s*hidden;/s);
    expect(stylesheet).toMatch(/\.space-aurora \.aurora-primary \.conversation\s*,[\s\S]*?flex:\s*1 1 0;/);
    expect(stylesheet).toMatch(/\.space-aurora \.aurora-primary \.composer\s*\{[^}]*position:\s*relative;[^}]*inset:\s*auto;[^}]*flex:\s*0 0 auto;/s);
    expect(stylesheet).toMatch(/@media \(max-width:\s*680px\)[\s\S]*?height:\s*calc\(100dvh - 93px\);/);
  });

  it("hides welcome copy after messages and keeps the orb out of layout", () => {
    expect(appSource).toContain('auroraSession.messages.length > 0 ? "has-conversation" : "is-empty"');
    expect(stylesheet).toMatch(/\.space-aurora \.aurora-primary\.has-conversation \.hero\s*\{[^}]*position:\s*absolute;[^}]*height:\s*0;/s);
    expect(stylesheet).toMatch(/\.space-aurora \.aurora-primary\.has-conversation \.hero > div:first-child\s*\{\s*display:\s*none;/);
    expect(stylesheet).toMatch(/\.space-aurora \.aurora-primary \.orb\s*\{[^}]*position:\s*absolute;/s);
  });

  it("merges runtime, modes and history into one toolbar with overlay history", () => {
    const toolbar = appSource.indexOf('<div className="aurora-toolbar">');
    expect(toolbar).toBeGreaterThan(0);
    expect(appSource.indexOf('<nav className="modes"', toolbar)).toBeGreaterThan(toolbar);
    expect(appSource.indexOf("<ConversationHistory compact", toolbar)).toBeGreaterThan(toolbar);
    expect(stylesheet).toMatch(/\.aurora-toolbar\s*\{[^}]*display:\s*flex;[^}]*min-height:\s*44px;/s);
    expect(stylesheet).toMatch(/\.conversation-history\.compact \.conversation-history-list\s*\{[^}]*position:\s*absolute;/s);
    expect(stylesheet).toMatch(/\.aurora-toolbar \.modes button\s*\{[^}]*white-space:\s*nowrap;/s);
  });
});
