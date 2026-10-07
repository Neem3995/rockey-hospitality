import { useEffect, useRef } from 'react';

/** Management-view focus return, including an opener removed/disabled by a completed refresh.
 * @param {boolean} open @param {boolean} loading
 */
export default function useDialogFocus(open, loading) {
  const opener = useRef(/** @type {HTMLButtonElement | null} */ (null));
  const fallback = useRef(/** @type {HTMLHeadingElement | null} */ (null));
  const returning = useRef(false);
  useEffect(() => {
    if (open) { returning.current = true; return; }
    if (!returning.current) return;
    const current = document.activeElement;
    // A user who has already moved elsewhere keeps their chosen focus.
    if (current !== document.body && current !== opener.current && current !== fallback.current) {
      returning.current = false;
      return;
    }
    const target = opener.current;
    let restored = false;
    if (target?.isConnected && !target.matches(':disabled') && !target.closest('[hidden], [inert]')
        && getComputedStyle(target).display !== 'none' && getComputedStyle(target).visibility !== 'hidden') {
      target.focus();
      restored = document.activeElement === target;
    }
    if (!restored && fallback.current?.isConnected) fallback.current.focus();
    if (!loading) returning.current = false;
  }, [open, loading]);
  return { opener, fallback };
}
