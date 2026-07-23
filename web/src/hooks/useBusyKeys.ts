import { useCallback, useState } from "react";

// Gemini audit 4.8 (CONFIRMED/P1): "多个 social action 使用普通 button，无 per-resource busy guard."
// Several lists of social actions (accept/decline a connection request, leave a connection,
// invite/respond-to/leave a group, act on a letter, send a draft) each need THEIR OWN button
// disabled while its own request is in flight -- without also disabling every sibling button for
// an unrelated resource. A single shared boolean (the pre-existing `groupBusy`/`peopleBusy`/
// `draftBusy` fields) is exactly the wrong fix the audit calls out: clicking "accept" on one group
// invite must not grey out "leave" on a completely different group, and sending draft A must not
// disable sending draft B.
//
// This hook tracks a Set of "currently in flight" resource keys. `run` adds the key before
// starting, and always removes it afterwards (success or failure) -- callers that need custom
// error handling should catch inside their own async function; `run` does not swallow rejections,
// it only guarantees the key is cleared.
export function useBusyKeys<K extends string | number>() {
  const [keys, setKeys] = useState<ReadonlySet<K>>(() => new Set());

  const isBusy = useCallback((key: K) => keys.has(key), [keys]);

  const run = useCallback(async <T,>(key: K, fn: () => Promise<T>): Promise<T> => {
    setKeys(current => {
      if (current.has(key)) return current;
      const next = new Set(current);
      next.add(key);
      return next;
    });
    try {
      return await fn();
    } finally {
      setKeys(current => {
        if (!current.has(key)) return current;
        const next = new Set(current);
        next.delete(key);
        return next;
      });
    }
  }, []);

  return { isBusy, run };
}
