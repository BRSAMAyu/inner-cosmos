import type { DiscoverablePerson } from "./api";

// TB-REQ-001 (docs/goal/tracks/track-b-integration-requests.yml): the backend does not yet flag
// synthetic/QA accounts, so People Discovery leaks them to real users. Remove this client-side
// denylist once the backend excludes them at the source -- this is an honest stopgap, not a
// security boundary.
export const TEST_ACCOUNT_PREFIXES = ["csrf", "smoke", "header", "qa-", "test-", "b0-observer"];

export function excludeTestAccounts(people: DiscoverablePerson[]): DiscoverablePerson[] {
  return people.filter(person => {
    const handle = `${person.username} ${person.nickname}`.toLowerCase();
    return !TEST_ACCOUNT_PREFIXES.some(prefix => handle.includes(prefix));
  });
}
