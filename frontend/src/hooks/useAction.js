import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../services/apiClient.js';

/** Mutation state only: no automatic write retry. Lock synchronously before awaiting.
 * @param {() => void} onSuccess
 */
export default function useAction(onSuccess) {
  const lock = useRef(false);
  const alive = useRef(false);
  const controller = useRef(new AbortController());
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(/** @type {ApiError | null} */ (null));
  const [uncertain, setUncertain] = useState(false);
  useEffect(() => {
    alive.current = true;
    controller.current = new AbortController();
    return () => { alive.current = false; controller.current.abort(); };
  }, []);
  /** @param {(signal: AbortSignal) => Promise<unknown>} operation */
  async function run(operation) {
    if (lock.current || uncertain) return;
    lock.current = true;
    setPending(true);
    setError(null);
    try {
      await operation(controller.current.signal);
      if (alive.current) onSuccess();
    } catch (failure) {
      if (alive.current) {
        const safeError = failure instanceof ApiError ? failure : new ApiError(0);
        setError(safeError);
        setUncertain(safeError.status === 0 || safeError.status >= 500);
      }
    } finally { lock.current = false; if (alive.current) setPending(false); }
  }
  return { pending, error, uncertain, run };
}
