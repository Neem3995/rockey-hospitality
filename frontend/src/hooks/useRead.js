/*
 * STUDY NOTE: Pages call this read-only hook with a scope key and stable load function.
 * An effect fetches when key, revision, load or keepPrevious changes; reload increments revision.
 * Cleanup marks the old effect inactive and aborts its request, so it cannot replace current data.
 * Same-key refresh may keep rows mounted; a new key hides them immediately and errors clear them.
 * We return data/loading/refreshing/error and reload. Writes and auth decisions belong elsewhere.
 */
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../services/apiClient.js';

/** Read-only request state. Key changes hide old data immediately; cleanup ignores stale responses.
 * Same-key list refreshes may retain loaded content; errors still clear private data.
 * @template T @param {string} key @param {(signal: AbortSignal) => Promise<T>} load @param {boolean} [keepPrevious]
 */
export default function useRead(key, load, keepPrevious = false) {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState(/** @type {{key: string, revision: number, data: T | null, error: ApiError | null, loading: boolean}} */ ({ key: '', revision: -1, data: null, error: null, loading: true }));
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState((previous) => ({ key, revision, data: keepPrevious && previous.key === key ? previous.data : null, error: null, loading: true }));
    load(controller.signal).then((data) => {
      // This response belongs to this effect. Once cleanup marks it inactive, ignore late completion.
      if (active) setState({ key, revision, data, error: null, loading: false });
    }).catch((failure) => {
      if (active) setState({ key, revision, data: null, error: failure instanceof ApiError ? failure : new ApiError(0), loading: false });
    });
    return () => { active = false; controller.abort(); };
  }, [key, revision, load, keepPrevious]);
  // reload asks React for a new revision; the dependency above starts the read after rendering.
  const reload = useCallback(() => setRevision((value) => value + 1), []);
  const sameKey = state.key === key;
  const current = sameKey && state.revision === revision;
  const data = sameKey && (current || keepPrevious) ? state.data : null;
  const loading = !current || state.loading;
  return { data, error: current ? state.error : null, loading, refreshing: loading && data !== null, reload };
}
