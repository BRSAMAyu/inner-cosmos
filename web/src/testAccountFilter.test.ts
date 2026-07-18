import { describe, expect, it } from "vitest";
import { excludeTestAccounts } from "./testAccountFilter";
import type { DiscoverablePerson } from "./api";

function person(overrides: Partial<DiscoverablePerson>): DiscoverablePerson {
  return { id: 1, username: "someone", nickname: "Someone", relationStatus: "NONE", ...overrides };
}

describe("excludeTestAccounts", () => {
  it("keeps ordinary accounts", () => {
    const people = [person({ id: 1, username: "mira", nickname: "Mira" })];
    expect(excludeTestAccounts(people)).toEqual(people);
  });

  it("drops known QA/service account patterns by username or nickname", () => {
    const people = [
      person({ id: 1, username: "mira", nickname: "Mira" }),
      person({ id: 2, username: "csrf-runtime-1", nickname: "CSRF Runtime" }),
      person({ id: 3, username: "smoke-test", nickname: "Smoke" }),
      person({ id: 4, username: "header-check", nickname: "Header" }),
      person({ id: 5, username: "qa-alpha", nickname: "QA Alpha" }),
      person({ id: 6, username: "test-observer-9", nickname: "Observer" }),
      person({ id: 7, username: "obs1", nickname: "b0-observer-run" })
    ];
    expect(excludeTestAccounts(people).map(p => p.id)).toEqual([1]);
  });

  it("is case-insensitive", () => {
    const people = [person({ id: 9, username: "CSRF-Runtime", nickname: "whatever" })];
    expect(excludeTestAccounts(people)).toEqual([]);
  });
});
