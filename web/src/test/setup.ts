import "@testing-library/jest-dom/vitest";
import { beforeEach } from "vitest";

// Node 26 exposes an experimental process-level `localStorage` global that is undefined unless
// the process is started with --localstorage-file. It can also shadow jsdom's storage accessor,
// so use a deterministic browser-compatible implementation instead of reading window.localStorage.
function createMemoryStorage(): Storage {
  const values = new Map<string, string>();
  return {
    get length() {
      return values.size;
    },
    clear() {
      values.clear();
    },
    getItem(key: string) {
      return values.get(String(key)) ?? null;
    },
    key(index: number) {
      return [...values.keys()][index] ?? null;
    },
    removeItem(key: string) {
      values.delete(String(key));
    },
    setItem(key: string, value: string) {
      values.set(String(key), String(value));
    }
  };
}

const testLocalStorage = createMemoryStorage();
const testSessionStorage = createMemoryStorage();

Object.defineProperty(globalThis, "localStorage", {
  configurable: true,
  value: testLocalStorage
});
Object.defineProperty(globalThis, "sessionStorage", {
  configurable: true,
  value: testSessionStorage
});
Object.defineProperty(window, "localStorage", {
  configurable: true,
  value: testLocalStorage
});
Object.defineProperty(window, "sessionStorage", {
  configurable: true,
  value: testSessionStorage
});

beforeEach(() => {
  testLocalStorage.clear();
  testSessionStorage.clear();
});

// jsdom does not implement scrollIntoView; components that auto-scroll (e.g. AuroraConversation)
// would otherwise throw "scrollIntoView is not a function" in every test that renders them.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}
