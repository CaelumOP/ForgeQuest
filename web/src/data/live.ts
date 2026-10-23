// A Preact hook over Dexie liveQuery: re-renders whenever the tables a query read change.
import { liveQuery } from 'dexie';
import { useEffect, useState } from 'preact/hooks';

/** Runs `query` now and again after every change to what it read. Shows `initial` until the first result. */
export function useLive<T>(query: () => Promise<T>, deps: unknown[], initial: T): T {
  const [value, setValue] = useState<T>(initial);
  useEffect(() => {
    const sub = liveQuery(query).subscribe({
      next: (v) => setValue(() => v),
      error: (e) => console.error('useLive query failed', e),
    });
    return () => sub.unsubscribe();
  }, deps);
  return value;
}
