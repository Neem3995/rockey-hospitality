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
      if (active) setState({ key, revision, data, error: null, loading: false });
    }).catch((failure) => {
      if (active) setState({ key, revision, data: null, error: failure instanceof ApiError ? failure : new ApiError(0), loading: false });
    });
    return () => { active = false; controller.abort(); };
  }, [key, revision, load, keepPrevious]);
  const reload = useCallback(() => setRevision((value) => value + 1), []);
  const sameKey = state.key === key;
  const current = sameKey && state.revision === revision;
  const data = sameKey && (current || keepPrevious) ? state.data : null;
  const loading = !current || state.loading;
  return { data, error: current ? state.error : null, loading, refreshing: loading && data !== null, reload };
}
